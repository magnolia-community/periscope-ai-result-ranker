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

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import info.magnolia.forge.periscope.rank.ml.Debouncer;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class DebouncerTest {

    private ExecutorService executor;

    @Before
    public void setUp() throws Exception {
        executor = Executors.newSingleThreadExecutor();
    }

    @Test
    public void executeSimpleCall() throws Exception {
        // GIVEN
        Debouncer debouncer = new Debouncer(executor, 500);
        AtomicBoolean canaryDead = new AtomicBoolean(false);

        // WHEN
        CompletableFuture<Boolean> promise = debouncer.debounce(() -> canaryDead.set(true));

        // THEN
        boolean executed = promise.get();
        assertThat(executed, is(true));
        assertThat(canaryDead.get(), is(true));
    }

    @Test
    public void skipIntermediateCall() throws Exception {
        // GIVEN
        Debouncer debouncer = new Debouncer(executor, 50);

        AtomicBoolean alphaDone = new AtomicBoolean(false);
        AtomicBoolean bravoDone = new AtomicBoolean(false);
        AtomicBoolean charlieDone = new AtomicBoolean(false);

        // WHEN
        CompletableFuture<Boolean> promiseAlpha = debouncer.debounce(() -> alphaDone.set(true));
        CompletableFuture<Boolean> promiseBravo = debouncer.debounce(() -> bravoDone.set(true));
        CompletableFuture<Boolean> promiseCharlie = debouncer.debounce(() -> charlieDone.set(true));

        // THEN
        assertThat(promiseAlpha.get(), is(true));
        assertThat(promiseBravo.get(), is(false));
        assertThat(promiseCharlie.get(), is(true));

        assertThat(alphaDone.get(), is(true));
        assertThat(bravoDone.get(), is(false));
        assertThat(charlieDone.get(), is(true));
    }

    @After
    public void tearDown() {
        executor.shutdownNow();
    }
}