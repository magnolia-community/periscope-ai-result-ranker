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

import static info.magnolia.forge.periscope.rank.ml.RankingNetworkStorageStrategy.RankingInfo;
import static info.magnolia.forge.periscope.rank.ml.jcr.AbstractJcrNetworkStorageStrategy.*;
import static info.magnolia.forge.periscope.rank.ml.jcr.JcrUserRoleNetworkStorageStrategy.DEFAULT_RANKING_NODE_NAME;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import info.magnolia.cms.security.User;
import info.magnolia.context.MgnlContext;
import info.magnolia.forge.periscope.rank.ml.RankingNetworkStorageTest;
import info.magnolia.jcr.util.NodeTypes;
import info.magnolia.jcr.util.NodeUtil;
import info.magnolia.forge.periscope.rank.ml.IndexedBuffer;
import info.magnolia.test.RepositoryTestCase;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.junit.Before;
import org.junit.Test;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.cpu.nativecpu.NDArray;
import org.nd4j.linalg.factory.Nd4j;

public class JcrNetworkStorageStrategyTest extends RepositoryTestCase {

    private MultiLayerNetwork network;
    private AbstractJcrNetworkStorageStrategy jcrStorageStrategy;
    private Session session;
    private User user;

    @Before
    public void setUp() throws Exception {
        super.setUp();

        network = RankingNetworkStorageTest.createMockMultiLayerNetwork();

        jcrStorageStrategy = new JcrUsernameNetworkStorageStrategy();

        session = MgnlContext.getJCRSession(WORKSPACE);

        user = mock(User.class);
        when(user.getName()).thenReturn("foobar");

        NodeUtil.createPath(session.getRootNode(), "/" + user.getName(), NodeTypes.Content.NAME);
    }

    @Override
    protected String getRepositoryConfigFileName() {
        return "test-rankings-repositories.xml";
    }

    @Test
    public void networkShouldNotExistInitially() throws Exception {
        // GIVEN WHEN THEN
        assertFalse(jcrStorageStrategy.load(user).isPresent());
    }

    @Test
    public void networkShouldBePersistedToJCR() throws Exception {
        // GIVEN default storage strategy is assumed to be per username

        // WHEN
        jcrStorageStrategy.store(new RankingInfo(network, bufferOf(3, Arrays.asList("a", "b", "c")), user));

        // THEN
        assertNeuralNetworkNodesExist(session, user.getName());
    }

    @Test
    public void networkForUserWithGrantedRoleShouldBePersistedToJCR() throws Exception {
        // GIVEN storage strategy changed to user role
        jcrStorageStrategy = new JcrUserRoleNetworkStorageStrategy();
        when(user.hasRole(eq(RANKING_USERROLE))).thenReturn(true);

        // WHEN
        jcrStorageStrategy.store(new RankingInfo(network, bufferOf(3, Arrays.asList("a", "b", "c")), user));

        // THEN
        assertNeuralNetworkNodesExist(session, user.getName());
    }

    @Test
    public void networkForUserWithoutGrantedRoleShouldBePersistedToDefaultNode() throws Exception {
        // GIVEN storage strategy changed to user role
        jcrStorageStrategy = new JcrUserRoleNetworkStorageStrategy();

        // WHEN
        jcrStorageStrategy.store(new RankingInfo(network, bufferOf(3, Arrays.asList("a", "b", "c")), user));

        // THEN
        assertNeuralNetworkNodesExist(session, DEFAULT_RANKING_NODE_NAME);
    }

    @Test
    public void labelsShouldPersist() throws Exception {
        // GIVEN
        IndexedBuffer<String> labels = bufferOf(11, Arrays.asList("a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k"));

        // WHEN
        jcrStorageStrategy.store(new RankingInfo(network, labels, user));
        RankingInfo loaded = jcrStorageStrategy.load(user).get(); // block until done

        // THEN
        assertThat(loaded.getLabels().asList(), is(labels.asList()));
        assertThat(loaded.getLabels().evictionOrder(), is(labels.evictionOrder()));
        assertThat(loaded.getLabels().getLimit(), is(labels.getLimit()));
    }

    @Test
    public void overflownLabelsShouldPersist() throws Exception {
        // GIVEN
        IndexedBuffer<String> labels = bufferOf(4, Arrays.asList("a", "b", "c", "d"), Arrays.asList("c", "d", "a", "b"));

        // WHEN
        jcrStorageStrategy.store(new RankingInfo(network, labels, user));
        RankingInfo loaded = jcrStorageStrategy.load(user).get(); // block until done

        // THEN
        assertThat(loaded.getLabels().asList(), is(labels.asList()));
        assertThat(loaded.getLabels().evictionOrder(), is(labels.evictionOrder()));
        assertThat(loaded.getLabels().getLimit(), is(labels.getLimit()));
    }

    @Test
    public void persistingAndLoadingShouldRetainWeights() throws Exception {
        // GIVEN
        float[] paramsInitial = Nd4j.toFlattened(network.params()).toFloatVector();

        float[] in = {1, 0, 1, 0, 1};
        float[] out = {0, 1, 0, 1, 0};
        network.fit(new NDArray(in), new NDArray(out));

        float[] paramsAfterFit = Nd4j.toFlattened(network.params()).toFloatVector();

        assertThat(paramsAfterFit.length, is(paramsInitial.length));
        assertFalse(RankingNetworkStorageTest.arraysSimilar(paramsAfterFit, paramsInitial));

        // WHEN
        jcrStorageStrategy.store(new RankingInfo(network, new IndexedBuffer<>(5), user));
        MultiLayerNetwork loadedNetwork = jcrStorageStrategy.load(user).get().getNetwork(); // block until done

        // THEN
        float[] paramsAfterLoad = Nd4j.toFlattened(loadedNetwork.params()).toFloatVector();
        assertFalse(RankingNetworkStorageTest.arraysSimilar(paramsAfterLoad, paramsInitial));
        assertTrue(RankingNetworkStorageTest.arraysSimilar(paramsAfterLoad, paramsAfterFit));
    }

    @Test
    public void persistingAndLoadingShouldRetainMapping() throws Exception {
        // GIVEN
        final NDArray sampleInput = new NDArray(new float[]{1, 0, 1, 0, 1});
        final INDArray outputInitial = network.output(sampleInput);

        float[] out = {0, 1, 0, 1, 0};
        network.fit(sampleInput, new NDArray(out));

        final INDArray outputAfterTrain = network.output(sampleInput);
        assertFalse(RankingNetworkStorageTest.arraysSimilar(outputAfterTrain.toFloatVector(), outputInitial.toFloatVector()));

        // WHEN
        jcrStorageStrategy.store(new RankingInfo(network, new IndexedBuffer<>(5), user));

        RankingInfo loaded = jcrStorageStrategy.load(user).get(); // block until done

        // THEN
        final INDArray outputAfterLoad = loaded.getNetwork().output(sampleInput);
        assertTrue(RankingNetworkStorageTest.arraysSimilar(outputAfterLoad.toFloatVector(), outputAfterTrain.toFloatVector()));
    }

    private void assertNeuralNetworkNodesExist(Session session, String nodeName) throws RepositoryException {
        assertTrue(session.nodeExists("/" + nodeName + "/" + FILENAME));
        assertTrue(session.nodeExists("/" + nodeName + "/" + LABELS_NODE_NAME));
    }

    public static IndexedBuffer<String> bufferOf(int limit, List<String> labels) {
        return new IndexedBuffer<>(limit, labels, Collections.emptyList());
    }

    public static IndexedBuffer<String> bufferOf(int limit, List<String> labels, List<String> evictionOrder) {
        return new IndexedBuffer<>(limit, labels, evictionOrder);
    }
}
