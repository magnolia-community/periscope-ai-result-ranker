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

import static java.util.stream.Collectors.toList;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.number.OrderingComparison.greaterThanOrEqualTo;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.*;

import info.magnolia.cms.security.User;
import info.magnolia.forge.periscope.rank.ml.RankingNetworkStorageStrategy.RankingInfo;
import info.magnolia.periscope.search.SearchResult;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

import org.apache.commons.collections4.CollectionUtils;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.Lists;

public class NeuralNetworkResultRankerTest {

    private static final int OUTPUT_UNITS = 50;

    private Collection<SearchResult> results;

    private NeuralNetworkResultRanker network;

    private User user;

    private static PeriscopeResultRankerModule module;

    @Before
    public void setUp() {
        user = mock(User.class);
        when(user.getName()).thenReturn("foobar");

        module = new PeriscopeResultRankerModule();
        module.setRankingNetworkStorageStrategy(new InMemoryRankingNetworkStorageStrategy());

        network = new NeuralNetworkResultRanker(new NoopNetworkStorage(module), 123, OUTPUT_UNITS, user);

        results = new ArrayList<>();
        results.add(SearchResult.builder().title("CarGold").build());
        results.add(SearchResult.builder().title("SBB Cargo").build());
        results.add(SearchResult.builder().title("CarGames Inc.").build());
        results.add(SearchResult.builder().title("CarGurus").build());
        results.add(SearchResult.builder().title("Cargo Bar").build());
        results.add(SearchResult.builder().title("Carglass").build());
        network.addResults(results);
    }

    @Test
    public void shouldKeepResultTextsUnique() {
        // GIVEN
        network.addResults(results);
        assertThat(network.getResultTexts().size(), is(6));

        List<SearchResult> clonedResults = results.stream()
                .map(r -> SearchResult.builder().title(r.getTitle() + "").operationRequest(r.getOperationRequest()).build())
                .collect(toList());
        Collections.reverse(clonedResults);

        // WHEN
        network.addResults(clonedResults);

        // THEN
        assertThat(network.getResultTexts().size(), is(6));
        assertThat(network.getResultTexts().indexOf(results.iterator().next().getTitle()), is(0));
    }

    @Test
    public void shouldMoveUpPreviouslyChosenResult() {
        // GIVEN
        List<SearchResult> sortedBefore = new ArrayList<>(network.rank("car", results));
        assertThat(sortedBefore.size(), is(6));

        SearchResult fifth = sortedBefore.get(4);

        // WHEN
        // simulate selecting the chosen result twice
        IntStream.range(0, 2).forEach(i -> network.trainRanking("car", fifth));

        // THEN
        List<SearchResult> sortedAfter = new ArrayList<>(network.rank("car", results));
        assertThat(sortedAfter.indexOf(fifth), is(lessThan(4)));
    }

    @Test
    public void shouldMoveUpForSimilarQueries() {
        // GIVEN
        List<SearchResult> sortedBefore = new ArrayList<>(network.rank("carg", results));
        assertThat(sortedBefore.size(), is(6));

        SearchResult fifth = sortedBefore.get(4);

        // WHEN
        // simulate selecting the chosen result twice
        IntStream.range(0, 2).forEach(i -> network.trainRanking("car", fifth));

        // THEN
        List<SearchResult> sortedAfter = new ArrayList<>(network.rank("carg", results));
        assertThat(sortedAfter.indexOf(fifth), is(lessThan(4)));
    }

    @Test
    public void shouldMoveUpSelectivelyPerQuery() {
        // GIVEN
        List<SearchResult> sortedBefore = new ArrayList<>(network.rank("carg", results));
        assertThat(sortedBefore.size(), is(6));

        SearchResult caSelection = sortedBefore.get(4);
        SearchResult cargSelection = sortedBefore.get(5);

        // WHEN
        IntStream.range(0, 10).forEach(i -> {
            network.trainRanking("ca", caSelection);
            network.trainRanking("carg", cargSelection);
        });

        // THEN
        // when querying "ca", our ca result should be higher than our carg one
        List<SearchResult> sortedCaAfter = new ArrayList<>(network.rank("ca", results));
        assertThat(sortedCaAfter.indexOf(caSelection), is(lessThan(sortedCaAfter.indexOf(cargSelection))));

        // vice versa for "carg"
        List<SearchResult> sortedCargAfter = new ArrayList<>(network.rank("carg", results));
        assertThat(sortedCargAfter.indexOf(cargSelection), is(lessThan(sortedCargAfter.indexOf(caSelection))));
    }

    @Test
    public void shouldRankBasedOnTitleNotObject() {
        // GIVEN
        List<SearchResult> sortedBefore = new ArrayList<>(network.rank("car", results));
        assertThat(sortedBefore.size(), is(6));

        SearchResult fifth = sortedBefore.get(4);

        // WHEN
        // simulate selecting the chosen result twice
        IntStream.range(0, 2).forEach(i -> network.trainRanking("car", fifth));

        // THEN
        List<SearchResult> clonedResults = results.stream().map(r -> SearchResult.builder().title(r.getTitle() + "").operationRequest(r.getOperationRequest()).build()).collect(toList());
        List<SearchResult> sortedAfter = new ArrayList<>(network.rank("car", clonedResults));
        assertThat(sortedAfter.indexOf(fifth), is(lessThan(4)));
    }

    @Test
    public void shouldMergeResultsBasedOnText() {
        // GIVEN
        List<SearchResult> sortedBefore = new ArrayList<>(network.rank("car", results));
        assertThat(sortedBefore.size(), is(6));

        SearchResult second = sortedBefore.get(1);

        List<SearchResult> clonedResults = results.stream().map(r -> SearchResult.builder().title(r.getTitle() + "").operationRequest(r.getOperationRequest()).build()).collect(toList());

        // WHEN
        // simulate selecting the chosen result twice
        IntStream.range(0, 2).forEach(i -> network.trainRanking("car", second));
        network.addResults(clonedResults);

        // THEN
        List<SearchResult> sortedAfter = new ArrayList<>(network.rank("car", clonedResults));
        assertThat(sortedAfter.size(), is(6));
        assertThat(sortedAfter.get(0).getTitle(), is(second.getTitle()));
    }

    @Test
    public void resultAddOrderShouldNotMatter() {
        // GIVEN
        final InMemNetworkStorage storage = new InMemNetworkStorage();
        NeuralNetworkResultRanker ranker = new NeuralNetworkResultRanker(storage, 1234, OUTPUT_UNITS, user);
        ranker.addResults(results);

        List<SearchResult> sortedBefore = new ArrayList<>(ranker.rank("car", results));
        SearchResult fifth = sortedBefore.get(4);

        ranker.trainRanking("car", fifth);
        List<SearchResult> sortedAfterTrain = new ArrayList<>(ranker.rank("car", results));
        assertThat(sortedAfterTrain.indexOf(fifth), is(lessThan(4)));

        // WHEN
        NeuralNetworkResultRanker reloaded = new NeuralNetworkResultRanker(storage, 1234, OUTPUT_UNITS, user);
        final List<SearchResult> reverse = Lists.reverse(new ArrayList<>(results));
        reloaded.addResults(reverse);

        // THEN
        assertThat(reloaded.getResultTexts().size(), is(6));

        List<SearchResult> sortedAfterReload = new ArrayList<>(reloaded.rank("car", results));
        assertThat(sortedAfterReload, is(sortedAfterTrain));
    }

    @Test
    public void shouldBeRobustAgainstUnseenResults() {
        // GIVEN
        SearchResult unseenResult = SearchResult.builder().title("You don't know me.").build();
        SearchResult unseenResult2 = SearchResult.builder().title("No one knows me.").build();

        // WHEN
        network.trainRanking("sample query", unseenResult);
        Collection<SearchResult> ranked = network.rank("another sample query", Collections.singletonList(unseenResult2));

        // THEN
        assertThat(ranked.size(), is(1));
    }

    @Test
    public void moreResultsThanOutputUnitsShouldForgetOldOnes() {
        // GIVEN
        network = new NeuralNetworkResultRanker(new NoopNetworkStorage(module), 123, OUTPUT_UNITS, user);

        List<SearchResult> fillers = IntStream.range(0, OUTPUT_UNITS)
                .mapToObj(i -> SearchResult.builder().title("Sample result " + i).build())
                .collect(toList());
        SearchResult firstFiller = fillers.get(0);
        network.addResults(fillers);
        network.trainRanking("bar", firstFiller);
        fillers.forEach(r -> network.trainRanking("foo", r));

        // WHEN
        network.addResults(results);
        results.forEach(r -> network.trainRanking("bar", r));

        Collection<SearchResult> union = CollectionUtils.union(fillers, results);
        List<SearchResult> ranked = new ArrayList<>(network.rank("bar", union));

        // THEN
        // first one should have been "pushed out" of known results, so defaulting to a low ranking
        assertThat(ranked.indexOf(firstFiller), is(greaterThanOrEqualTo(fillers.size())));
        // recently added ones should be in the known range, so ranked high according to training
        results.forEach(r ->
                assertThat(ranked.indexOf(r), is(lessThan(ranked.size()))));
    }

    @Test
    public void pickingResultShouldDelayDropping() {
        // GIVEN
        network = new NeuralNetworkResultRanker(new NoopNetworkStorage(module), 123, OUTPUT_UNITS, user);

        List<SearchResult> fillers = IntStream.range(0, OUTPUT_UNITS)
                .mapToObj(i -> SearchResult.builder().title("Sample result " + i).build())
                .collect(toList());
        SearchResult firstFiller = fillers.get(0);
        network.addResults(fillers);
        fillers.forEach(r -> network.trainRanking("foo", r));
        // touch again to push it at the end of LRU queue
        network.trainRanking("bar", firstFiller);

        // WHEN
        network.addResults(results);
        results.forEach(r -> network.trainRanking("bar", r));

        Collection<SearchResult> union = CollectionUtils.union(fillers, results);
        List<SearchResult> ranked = new ArrayList<>(network.rank("bar", union));

        // THEN
        // should not have been pushed out, since it was touched late and we expect LRU policy
        assertThat(ranked.indexOf(firstFiller), is(lessThanOrEqualTo(results.size())));
        // recently added ones should be in the known range, so ranked high according to training
        results.forEach(r ->
                assertThat(ranked.indexOf(r), is(lessThanOrEqualTo(ranked.size()))));
    }

    @Test
    public void newResultsShouldNotInheritRankingsOfDroppedOnes() {
        // GIVEN
        network = new NeuralNetworkResultRanker(new NoopNetworkStorage(module), 123, OUTPUT_UNITS, user);

        List<SearchResult> fillers = IntStream.range(0, OUTPUT_UNITS)
                .mapToObj(i -> SearchResult.builder().title("Sample result " + i).build())
                .collect(toList());
        SearchResult firstFiller = fillers.get(0);

        network.addResults(fillers);
        fillers.forEach(r -> network.trainRanking("foo", r));

        // train first one stronger
        network.trainRanking("foo", firstFiller);
        network.trainRanking("foo", firstFiller);
        // make sure it's on top for now
        assertThat(network.rank("foo", fillers).iterator().next(), is(firstFiller));

        SearchResult additionalResult = SearchResult.builder().title("the new one").build();

        // WHEN
        network.addResults(Collections.singletonList(additionalResult));

        List<SearchResult> union = new ArrayList<>(fillers);
        union.add(additionalResult);
        List<SearchResult> ranked = new ArrayList<>(network.rank("foo", union));

        // THEN
        // additional one should have no proper ranking yet, thus be in a random position
        int untrainedIndex = ranked.indexOf(additionalResult);
        assertThat(untrainedIndex, is(greaterThan(0)));

        // (WHEN)
        network.trainRanking("foo", additionalResult);
        ranked = new ArrayList<>(network.rank("foo", union));

        // (THEN)
        // after training for this one now, it will end up ranked higher again
        assertThat(ranked.indexOf(additionalResult), is(lessThan(untrainedIndex)));
    }

    @Test
    public void unsetOutputLabelsShouldUseDefaultValue() throws Exception {
        // GIVEN
        module.setOutputUnits(null);

        // WHEN
        network = new NeuralNetworkResultRanker(new NoopNetworkStorage(module), module, user);

        // THEN
        assertThat(network.getOutputUnits(), is(PeriscopeResultRankerModule.DEFAULT_OUTPUT_UNITS));
    }

    static class NoopNetworkStorage extends RankingNetworkStorage {

        public NoopNetworkStorage(PeriscopeResultRankerModule module) {
            super(module);
        }

        @Override
        CompletableFuture<Boolean> persist(RankingInfo rankingInfo) {
            return CompletableFuture.completedFuture(true);
        }
    }

    static class InMemNetworkStorage extends RankingNetworkStorage {

        public InMemNetworkStorage() {
            super(module);
        }

        @Override
        CompletableFuture<Boolean> persist(RankingInfo rankingInfo) {
            try {
                networkStorageStrategy.store(rankingInfo);
            } catch (RankingNetworkStorageException e) {
                throw new RuntimeException(e);
            }
            return CompletableFuture.completedFuture(true);
        }


        @Override
        Optional<RankingInfo> load(User user) {
            try {
                return networkStorageStrategy.load(user);
            } catch (RankingNetworkStorageException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    static class InMemoryRankingNetworkStorageStrategy implements RankingNetworkStorageStrategy {
        private RankingInfo rankingInfo;

        @Override
        public void store(RankingInfo rankingInfo) throws RankingNetworkStorageException {
            this.rankingInfo = rankingInfo;
        }

        @Override
        public Optional<RankingInfo> load(User user) throws RankingNetworkStorageException {
            return Optional.ofNullable(this.rankingInfo);
        }
    }
}
