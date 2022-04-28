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

import static info.magnolia.forge.periscope.rank.ml.RankingNetworkStorageStrategy.RankingInfo;
import static java.util.stream.Collectors.toList;
import static org.deeplearning4j.nn.api.OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.nd4j.linalg.activations.Activation.TANH;

import info.magnolia.cms.security.User;
import info.magnolia.forge.periscope.rank.ml.jcr.JcrUsernameNetworkStorageStrategy;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.inputs.InputType;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.junit.Before;
import org.junit.Test;
import org.nd4j.linalg.cpu.nativecpu.NDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.learning.config.Nesterovs;

public class RankingNetworkStorageTest {

    private MultiLayerNetwork network;
    private User user;
    private PeriscopeResultRankerModule module;

    @Before
    public void setUp() {
        network = createMockMultiLayerNetwork();

        user = mock(User.class);
        when(user.getName()).thenReturn("foobar");

        module = new PeriscopeResultRankerModule();
        module.setRankingNetworkStorageStrategy(new NeuralNetworkResultRankerTest.InMemoryRankingNetworkStorageStrategy());
    }

    @Test
    public void repeatedPersistingShouldCauseNoCollisions() throws Exception {
        // GIVEN
        float[] paramsInitial = Nd4j.toFlattened(network.params()).toFloatVector();

        float[] in = {1, 0, 1, 0, 1};
        float[] out = {0, 1, 0, 1, 0};

        final RankingInfo rankingInfo = new RankingInfo(network, new IndexedBuffer<>(5), user);
        final RankingNetworkStorage storage = new RankingNetworkStorage(module);

        // WHEN
        List<CompletableFuture<Boolean>> futures = IntStream.range(0, 5).mapToObj(i -> {
            network.fit(new NDArray(in), new NDArray(out));
            return storage.persist(rankingInfo);

        }).collect(toList());
        // block until all are done
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[]{})).get();

        // THEN
        float[] paramsAfterFit = Nd4j.toFlattened(network.params()).toFloatVector();
        assertThat(paramsAfterFit.length, is(paramsInitial.length));
        assertFalse(arraysSimilar(paramsAfterFit, paramsInitial));

        MultiLayerNetwork loadedNetwork = storage.load(user).get().getNetwork();

        float[] paramsAfterLoad = Nd4j.toFlattened(loadedNetwork.params()).toFloatVector();
        assertFalse(arraysSimilar(paramsAfterLoad, paramsInitial));
        assertTrue(arraysSimilar(paramsAfterLoad, paramsAfterFit));
    }

    @Test
    public void unsetRankingNetworkStorageShouldUseDefaultOne() throws Exception {
        // GIVEN
        final PeriscopeResultRankerModule borked = new PeriscopeResultRankerModule();
        borked.setRankingNetworkStorageStrategy(null);

        // WHEN THEN
        assertThat(new RankingNetworkStorage(borked).networkStorageStrategy, instanceOf(JcrUsernameNetworkStorageStrategy.class));
    }

    public static boolean arraysSimilar(float[] a, float[] b) {
        return IntStream.range(0, b.length)
                .mapToDouble(i -> b[i] - a[i])
                .map(Math::abs)
                .allMatch(diff -> diff <= 0.01f);
    }

    public static MultiLayerNetwork createMockMultiLayerNetwork() {
        MultiLayerNetwork network;
        MultiLayerConfiguration configuration = new NeuralNetConfiguration.Builder()
                .seed(123)
                .l2(1e-5)
                .weightInit(WeightInit.RELU)
                .optimizationAlgo(STOCHASTIC_GRADIENT_DESCENT)
                .activation(TANH)
                .updater(new Nesterovs(0.1, 0.4))
                .list()
                .layer(0, new DenseLayer.Builder().nIn(5).nOut(20)
                        .build())
                .layer(1, new OutputLayer.Builder().nOut(5)
                        .build())
                .setInputType(InputType.feedForward(10))
                .build();
        network = new MultiLayerNetwork(configuration);
        network.init();
        return network;
    }
}
