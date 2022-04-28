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

import static java.util.concurrent.TimeUnit.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;

import lombok.extern.slf4j.Slf4j;

/**
 * Helper for debouncing operations and eventually running them on an executor.
 */
@Slf4j
public class Debouncer {

    private final Executor executor;
    /** Debounding interval [ns]. */
    private final long interval;

    private final Semaphore semaphore = new Semaphore(1);
    private Semaphore scheduleSemaphore = new Semaphore(1);

    private Runnable operation;
    private CompletableFuture<Boolean> promise;
    private long lastExecution = 0;
    private boolean executionScheduled = false;

    /**
     * @param interval debouncing interval [ms]
     */
    public Debouncer(Executor executor, long interval) {
        this.executor = executor;
        this.interval = NANOSECONDS.convert(interval, MILLISECONDS);
    }

    public CompletableFuture<Boolean> debounce(Runnable runnable) {
        try {
            scheduleSemaphore.acquire();
        } catch (InterruptedException e) {
            throw new RuntimeException("Error trying to acquire semaphore for debounce request scheduling");
        }

        final CompletableFuture<Boolean> newPromise = new CompletableFuture<>();

        // put into a different thread because we don't want to block while another operation is running
        new Thread(() -> request(runnable, newPromise)).start();
        return newPromise;
    }

    private void request(Runnable runnable, CompletableFuture<Boolean> newPromise) {
        try {
            semaphore.acquire();
            scheduleSemaphore.release();
        } catch (InterruptedException e) {
            log.error("Error trying to acquire semaphore for NN storage debouncing", e);
        }

        if (executionScheduled) {
            // previous schedule one will be dropped, hence we notify listeners with false
            promise.complete(false);
        }

        this.promise = newPromise;
        this.operation = runnable;

        if (!executionScheduled) {
            long millisToWait = MILLISECONDS.convert(interval - (System.nanoTime() - lastExecution), NANOSECONDS);
            if (millisToWait <= 0) {
                execute();
            } else {
                scheduleExecution(millisToWait);
                executionScheduled = true;
                semaphore.release();
            }
        } else {
            semaphore.release();
        }
    }

    private void scheduleExecution(long millis) {
        new Thread(() -> {
            try {
                Thread.sleep(millis);

                semaphore.acquire();
                execute();
            } catch (InterruptedException e) {
                log.error("Error while sleeping for debouncing", e);
            }
        }).start();
    }

    /**
     * Execute operation on executor, reset lock when done.
     */
    private void execute() {
        executor.execute(() -> {
            operation.run();
            promise.complete(true);

            lastExecution = System.nanoTime();
            executionScheduled = false;

            semaphore.release();
        });
    }
}
