/**
 * This file Copyright (c) 2018 Magnolia International
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
package info.magnolia.forge.periscope.rank.ml;

import static java.util.Comparator.comparingInt;

import info.magnolia.cms.security.User;
import info.magnolia.periscope.rank.ResultRanker;
import info.magnolia.periscope.search.SearchResult;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.inject.Inject;

import org.apache.commons.lang3.StringUtils;
import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.WorkspaceMode;
import org.deeplearning4j.nn.conf.inputs.InputType;
import org.deeplearning4j.nn.conf.layers.ConvolutionLayer;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.Layer;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.jsoup.Jsoup;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.cpu.nativecpu.NDArray;
import org.nd4j.linalg.learning.config.Nesterovs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;

/**
 * Generates a Neural Network in order to rank results provided by Periscope.
 */
class NeuralNetworkResultRanker implements ResultRanker {

    private static final Logger log = LoggerFactory.getLogger(NeuralNetworkResultRanker.class);

    private static final int ASCII_CHARS = 128;
    private static final int INPUT_DIGITS = 20;
    private static final int INPUT_CHANNELS = INPUT_DIGITS * ASCII_CHARS;
    private static final int LAST_HIDDEN_UNITS = 100;
    private final RankingNetworkStorage storage;

    private final MultiLayerNetwork network;
    private final IndexedBuffer<String> resultTexts;
    private final int outputUnits;
    private final User user;

    @Inject
    NeuralNetworkResultRanker(RankingNetworkStorage storage, PeriscopeResultRankerModule module, User user) {
        this(storage, null, Optional.ofNullable(module.getOutputUnits()).orElse(module.DEFAULT_OUTPUT_UNITS), user);
    }

    /**
     * @param rngSeed Random number generation seed for reproducibility (e.g. during tests)
     */
    NeuralNetworkResultRanker(RankingNetworkStorage storage, Integer rngSeed, int outputUnits, User user) {
        this.storage = storage;
        this.outputUnits = outputUnits;
        this.user = user;
        RankingNetworkStorageStrategy.RankingInfo rankingInfo = loadOrCreateState(rngSeed, user);
        this.network = rankingInfo.getNetwork();
        this.resultTexts = rankingInfo.getLabels();
    }

    /**
     * Add {@link SearchResult results} to the network.
     */
    @Override
    public void addResults(Collection<SearchResult> results) {
        results.stream()
                .map(this::idFromResult)
                .filter(o -> !resultTexts.contains(o))
                .forEach(item -> {
                    Optional<Integer> droppedIndex = resultTexts.add(item);
                    droppedIndex.ifPresent(this::resetForOutputUnit);
                });
    }

    /**
     * Reset all weights in front of a particular output unit.
     */
    private void resetForOutputUnit(int unitIndex) {
        INDArray params = network.getOutputLayer().params();
        IntStream.range(LAST_HIDDEN_UNITS * unitIndex, LAST_HIDDEN_UNITS * (unitIndex + 1))
                .forEach(i -> params.put(0, i, 0));
        network.getOutputLayer().setParams(params);
    }

    /**
     * Injects users' selection to the Neural Network.
     *
     * @param query which query was used to generate the result.
     * @param result is what user had selected with the given query.
     */
    @Override
    public void trainRanking(String query, SearchResult result) {
        try {
            this.network.fit(inputToArray(query), outputToArray(idFromResult(result)));
            this.resultTexts.touch(idFromResult(result));
            storage.persist(new RankingNetworkStorageStrategy.RankingInfo(this.network, this.resultTexts, this.user));
        } catch (IllegalArgumentException e) {
            log.error("Failed to train ranking neural network", e);
        }
    }

    private String idFromResult(SearchResult result) {
        // remove potential highlighting and such
        return Jsoup.parse(result.getTitle()).text();
    }

    /**
     * Sorts the results based on the query of the user.
     * Takes into account what neural network is suggesting and does ordering according to.
     */
    @Override
    public Collection<SearchResult> rank(String query, Collection<SearchResult> results) {
        List<SearchResult> tempResults = Lists.newArrayList(results);
        INDArray resultArray = this.network.output(inputToArray(query));
        List<String> sortedIds = outputArrayToResults(resultArray);
        tempResults.sort(comparingInt(r -> {
            final String id = idFromResult(r);
            final int index = sortedIds.indexOf(id);
            // order "known" results based on NN ranking, put others at end of list
            return index >= 0 ? index : results.size();
        }));
        return tempResults;
    }

    IndexedBuffer<String> getResultTexts() {
        return resultTexts;
    }

    int getOutputUnits() {
        return outputUnits;
    }

    /**
     * Encode a string into a float array. Each character is represented by a 128-length subarray where one entry at its
     * corresponding ascii code position is 1 and everything else 0.
     */
    private INDArray inputToArray(String query) {
        String asciiQuery = StringUtils.stripAccents(query);

        float[] chars = new float[INPUT_CHANNELS];
        IntStream.range(0, INPUT_DIGITS).forEach(i -> {
            if (asciiQuery.length() <= i) {
                return;
            }

            int asciiCode = asciiQuery.charAt(i) % ASCII_CHARS;
            chars[i * ASCII_CHARS + asciiCode] = 1;
        });
        return new NDArray(chars);
    }

    private INDArray outputToArray(String resultId) {
        float[] nodes = new float[outputUnits];

        int resultIndex = resultTexts.indexOf(resultId);
        if (resultIndex < 0) {
            // in case resultTexts reach maximum outputUnits config, NN can not learn this case
            log.warn("Can not find result index in resultTexts, probably resultTexts reaches maximum outputUnits configuration");
        } else {
            nodes[resultIndex] = 1;
        }

        return new NDArray(nodes);
    }

    private List<String> outputArrayToResults(INDArray resultArray) {
        return resultTexts.asList().stream()
                .sorted((a, b) -> Math.round(Math.signum(resultArray.getFloat(resultTexts.indexOf(b)) - resultArray.getFloat(resultTexts.indexOf(a)))))
                .collect(Collectors.toList());
    }

    private RankingNetworkStorageStrategy.RankingInfo loadOrCreateState(Integer rngSeed, User user) {
        Optional<RankingNetworkStorageStrategy.RankingInfo> rankingInfo = storage.load(user);
        if (rankingInfo.isPresent()) {
            RankingNetworkStorageStrategy.RankingInfo info = rankingInfo.get();
            if (info.getLabels().getLimit() != this.outputUnits) {
                return new RankingNetworkStorageStrategy.RankingInfo(createNetwork(rngSeed), new IndexedBuffer<>(this.outputUnits), user);
            }
            return info;
        }
        return new RankingNetworkStorageStrategy.RankingInfo(createNetwork(rngSeed), new IndexedBuffer<>(this.outputUnits), user);
    }

    private MultiLayerNetwork createNetwork(Integer rngSeed) {
        Layer outputLayer = new OutputLayer.Builder()
                .nOut(outputUnits)
                .activation(Activation.SOFTMAX)
                .build();

        NeuralNetConfiguration.Builder builder = new NeuralNetConfiguration.Builder();
        // Workspaces set to none to consume less memory
        builder.setInferenceWorkspaceMode(WorkspaceMode.NONE);
        builder.setTrainingWorkspaceMode(WorkspaceMode.NONE);
        if (rngSeed != null) {
            builder.seed(rngSeed);
        }
        MultiLayerConfiguration configuration = builder.l2(1e-5)
                .weightInit(WeightInit.RELU)
                .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
                .activation(Activation.TANH)
                .updater(new Nesterovs(0.01, 0.4))
                .list()
                .layer(0, new ConvolutionLayer.Builder(ASCII_CHARS, 3).nIn(1)
                        .nOut(200)
                        // Workspace set to none to consume less memory
                        .cudnnAlgoMode(ConvolutionLayer.AlgoMode.NO_WORKSPACE)
                        .activation(Activation.IDENTITY)
                        .build())
                .layer(1, new DenseLayer.Builder().nOut(LAST_HIDDEN_UNITS)
                        .build())
                .layer(2, outputLayer)
                .setInputType(InputType.convolutionalFlat(ASCII_CHARS, INPUT_DIGITS, 1))
                .build();

        MultiLayerNetwork network = new MultiLayerNetwork(configuration);
        network.init();
        return network;
    }
}
