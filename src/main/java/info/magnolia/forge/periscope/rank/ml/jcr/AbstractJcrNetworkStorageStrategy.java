/**
 * This file Copyright (c) 2019 Magnolia International
 * Ltd.  (http://www.magnolia-cms.com). All rights reserved.
 *
 *
 * This program and the accompanying materials are made
 * available under the terms of the Magnolia Network Agreement
 * which accompanies this distribution, and is available at
 * http://www.magnolia-cms.com/mna.html
 *
 * Any modifications to this file must keep this entire header
 * intact.
 *
 */
package info.magnolia.forge.periscope.rank.ml.jcr;

import static java.util.stream.Collectors.toList;

import info.magnolia.cms.security.User;
import info.magnolia.context.MgnlContext;
import info.magnolia.forge.periscope.rank.ml.Debouncer;
import info.magnolia.forge.periscope.rank.ml.IndexedBuffer;
import info.magnolia.forge.periscope.rank.ml.RankingNetworkStorageException;
import info.magnolia.forge.periscope.rank.ml.RankingNetworkStorageStrategy;
import info.magnolia.jcr.util.NodeTypes;
import info.magnolia.jcr.util.PropertyUtil;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.RepositoryException;

import org.apache.commons.io.IOUtils;
import org.apache.jackrabbit.commons.JcrUtils;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.util.ModelSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract network storage strategy based on JCR.
 */
public abstract class AbstractJcrNetworkStorageStrategy implements RankingNetworkStorageStrategy {

    protected static final Logger log = LoggerFactory.getLogger(AbstractJcrNetworkStorageStrategy.class);

    static final String PARENT_PATH = "/";
    static final String FILENAME = "ranking-neural-network";
    static final String LABELS_NODE_NAME = "labels";
    static final String EVICTION_ORDER_PROPERTY = "evictionOrder";
    static final String BUFFER_LIMIT_PROPERTY = "bufferLimit";
    static final String WORKSPACE = "rankings";
    static final String RANKING_USERROLE = "ranker";

    protected abstract Optional<Node> getNetworkNode(User user);

    protected abstract Node getOrCreateNetworkNode(User user) throws RepositoryException;

    @Override
    public void store(RankingInfo rankingInfo) throws RankingNetworkStorageException {
        log.debug("Storing RankingInfo for user {}...", rankingInfo.getUser().getName());

        try {
            MgnlContext.doInSystemContext(() -> {
                storeToJcr(rankingInfo);
                return null;
            }, true);
        } catch (Exception e) {
            throw new RankingNetworkStorageException(e);
        }
    }

    @Override
    public Optional<RankingInfo> load(User user) throws RankingNetworkStorageException {
        final Optional<Node> networkNode = getNetworkNode(user);

        if (!networkNode.isPresent()) {
            return Optional.empty();
        }

        try {
            final MultiLayerNetwork network = ModelSerializer.restoreMultiLayerNetwork(JcrUtils.readFile(networkNode.get()), true);
            log.debug("Loading RankingInfo for user {}...", user.getName());
            return Optional.of(getOrCreateRankingInfo(network, user));
        } catch (RepositoryException | IOException e) {
            throw new RankingNetworkStorageException(e);
        }
    }

    private IndexedBuffer<String> getLabels(Node labelsNode) throws RepositoryException {
        PropertyIterator propIterator = labelsNode.getProperties();
        Map<String, String> properties = new HashMap<>();
        while (propIterator.hasNext()) {
            final Property property = propIterator.nextProperty();
            properties.put(property.getName(), PropertyUtil.getValueString(property));
        }

        List<String> labels = properties.entrySet().stream()
                .filter(entry -> {
                    try {
                        Integer.parseInt(entry.getKey());
                        return true;
                    } catch (NumberFormatException e) {
                        // ignore non-number properties
                        return false;
                    }
                })
                .sorted((a, b) -> {
                    try {
                        int keyA = Integer.parseInt(a.getKey());
                        int keyB = Integer.parseInt(b.getKey());
                        return keyA - keyB;
                    } catch (NumberFormatException e) {
                        throw new IllegalStateException(e);
                    }
                }).map(Map.Entry::getValue)
                .collect(toList());

        List<String> evictionOrder = labelsNode.hasProperty(EVICTION_ORDER_PROPERTY) ?
                PropertyUtil.getValuesStringList(labelsNode.getProperty(EVICTION_ORDER_PROPERTY).getValues()) :
                Collections.emptyList();
        int bufferLimit = (int) labelsNode.getProperty(BUFFER_LIMIT_PROPERTY).getLong();

        return new IndexedBuffer<>(bufferLimit, labels, evictionOrder);
    }

    private RankingInfo getOrCreateRankingInfo(MultiLayerNetwork network, User user) throws RepositoryException {
        final Node parentNode = getOrCreateNetworkNode(user);
        final IndexedBuffer<String> labels = getLabels(parentNode.getNode(LABELS_NODE_NAME));

        return new RankingInfo(network, labels, user);
    }

    /**
     * This method is synchronized in order to prevent multiple quickly successive calls to interfere with each other.
     *
     * <p>Thanks to the {@link Debouncer}, this <i>shouldn't</i> be happening anymore in a standard scenario.
     * But still better to keep it as a safeguard: if things are debounced, it shouldn't mean a performance hit anyway.
     */
    private synchronized void storeToJcr(RankingInfo rankingInfo) throws RepositoryException, IOException {
        Node parentNode = getOrCreateNetworkNode(rankingInfo.getUser());

        PipedInputStream in = new PipedInputStream();
        OutputStream out = new PipedOutputStream(in);

        new Thread(() -> {
            try {
                ModelSerializer.writeModel(rankingInfo.getNetwork(), out, true);
            } catch (IOException e) {
                log.error("Failed to write neural network to stream", e);
            } finally {
                IOUtils.closeQuietly(out);
            }
        }, "WriteNeuralNetworkModelToStream").start();

        JcrUtils.putFile(parentNode, FILENAME, "application/octet-stream", in);

        IndexedBuffer<String> labels = rankingInfo.getLabels();

        Node labelsNode = JcrUtils.getOrAddNode(parentNode, LABELS_NODE_NAME, NodeTypes.Content.NAME);
        for (String label : labels.asList()) {
            labelsNode.setProperty(Integer.toString(labels.indexOf(label)), label);
        }
        // delete old subsequent labels
        for (int i = labels.size(); labelsNode.hasProperty(Integer.toString(i)); i++) {
            labelsNode.getProperty(Integer.toString(i)).remove();
        }

        String[] evictionOrderVals = labels.evictionOrder().toArray(new String[]{});
        labelsNode.setProperty(EVICTION_ORDER_PROPERTY, evictionOrderVals);

        labelsNode.setProperty(BUFFER_LIMIT_PROPERTY, labels.getLimit());

        parentNode.getSession().save();
    }
}
