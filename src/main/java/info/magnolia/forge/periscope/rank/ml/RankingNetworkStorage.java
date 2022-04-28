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

import info.magnolia.cms.security.User;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Persistence utility for {@link NeuralNetworkResultRanker}.
 * Different {@link RankingNetworkStorageStrategy storage strategies} can be plugged in.
 *
 * @see PeriscopeResultRankerModule#setRankingNetworkStorageStrategy(RankingNetworkStorageStrategy)
 */
class RankingNetworkStorage {

    private static final Logger log = LoggerFactory.getLogger(RankingNetworkStorage.class);

    private static final long DEFAULT_DEBOUNCE_TIME = 30 * 1000;

    private final Debouncer debouncer;

    // package visibility for testing purposes
    final RankingNetworkStorageStrategy networkStorageStrategy;

    @Inject
    public RankingNetworkStorage(PeriscopeResultRankerModule periscopeResultRankerModule) {
        this(DEFAULT_DEBOUNCE_TIME, periscopeResultRankerModule);
    }

    RankingNetworkStorage(long debounceTime, PeriscopeResultRankerModule periscopeResultRankerModule) {
        if (periscopeResultRankerModule.getRankingNetworkStorageStrategy() == null) {
            log.warn("No rankingNetworkStorageStrategy was set in periscope-result-ranker module. Setting to default value {}", periscopeResultRankerModule.DEFAULT_NETWORK_STORAGE_STRATEGY);
            networkStorageStrategy = periscopeResultRankerModule.DEFAULT_NETWORK_STORAGE_STRATEGY;
        } else {
            this.networkStorageStrategy = periscopeResultRankerModule.getRankingNetworkStorageStrategy();
        }
        this.debouncer = new Debouncer(periscopeResultRankerModule.getStoringExecutor(), debounceTime);
    }

    /**
     * Persist some ranking information.
     * @return CompletableFuture serving a boolean whether this version was actually stored or obsoleted by a more
     * recent version (due to debouncing).
     */
    CompletableFuture<Boolean> persist(RankingNetworkStorageStrategy.RankingInfo rankingInfo) {
        return debouncer.debounce(() -> {
            try {
                networkStorageStrategy.store(rankingInfo);
            } catch (RankingNetworkStorageException e) {
                log.error("Failed to persist ranking neural network", e);
            }
        });
    }

    Optional<RankingNetworkStorageStrategy.RankingInfo> load(User user) {
        try {
            return networkStorageStrategy.load(user);
        } catch (RankingNetworkStorageException e) {
            throw new IllegalStateException("Failed to load ranking neural network for user " + user.getName(), e);
        }
    }
}
