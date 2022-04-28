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

import info.magnolia.forge.periscope.rank.ml.jcr.JcrUsernameNetworkStorageStrategy;
import info.magnolia.module.ModuleLifecycle;
import info.magnolia.module.ModuleLifecycleContext;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lombok.Getter;
import lombok.Setter;

/**
 * Module class to handle licence needs and configuration.
 * <p>
 * {@link RankingNetworkStorageStrategy} defaults to {@link JcrUsernameNetworkStorageStrategy},
 * unless set otherwise at {@code /modules/periscope-result-ranker/config.yaml}
 * <p>
 * <p>{@link #getOutputUnits()} defaults to {@value #DEFAULT_OUTPUT_UNITS}, unless set otherwise at {@code /modules/periscope-result-ranker/config.yaml}.
 * <p>Reducing the max number of output units (labels) from 10k to less (e.g. 1k) should help reducing the neural network size used by the AI ranking mechanism.
 * This may prove especially useful in case of excessive disk space usage and/or memory consumption.
 * Internally a LRU policy is used in order to drop results once the limit is reached, thus keeping search suggestions relevance high.
 */
public class PeriscopeResultRankerModule implements ModuleLifecycle {

    private static final Logger log = LoggerFactory.getLogger(PeriscopeResultRankerModule.class);

    // use single thread to avoid interference while saving
    private static final ExecutorService STORING_EXECUTOR = Executors.newSingleThreadExecutor();

    static final int DEFAULT_OUTPUT_UNITS = 10000;

    static final RankingNetworkStorageStrategy DEFAULT_NETWORK_STORAGE_STRATEGY = new JcrUsernameNetworkStorageStrategy();

    @Getter
    @Setter
    private RankingNetworkStorageStrategy rankingNetworkStorageStrategy = DEFAULT_NETWORK_STORAGE_STRATEGY;

    @Getter
    @Setter
    private Integer outputUnits = DEFAULT_OUTPUT_UNITS;


    public static ExecutorService getStoringExecutor() {
        return STORING_EXECUTOR;
    }

    @Override
    public void start(ModuleLifecycleContext ctx) {
        final String storageStrategyClassName = rankingNetworkStorageStrategy != null? rankingNetworkStorageStrategy.getClass().getName() : "<not defined>";
        log.info("Using rankingNetworkStorageStrategy [{}]", storageStrategyClassName);
        log.info("Using outputUnits with value [{}]", outputUnits);
    }

    @Override
    public void stop(ModuleLifecycleContext ctx) {
        if (ctx.getPhase() == ModuleLifecycleContext.PHASE_SYSTEM_SHUTDOWN) {
            log.info("Shutting down thread executor for neural network ranking model results persistence...");
            STORING_EXECUTOR.shutdown();
            try {
                // allow jobs currently executing to finish if possible
                if (!STORING_EXECUTOR.awaitTermination(2000, TimeUnit.MILLISECONDS)) {
                    STORING_EXECUTOR.shutdownNow();
                }
            } catch (InterruptedException e) {
                STORING_EXECUTOR.shutdownNow();
            }
        }

    }
}
