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
package info.magnolia.forge.periscope.rank.ml;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

import info.magnolia.forge.periscope.rank.ml.IndexedBuffer;

import java.util.Arrays;

import org.junit.Test;

public class IndexedBufferTest {

    @Test
    public void behaveLikeAListForFewItems() {
        // GIVEN
        IndexedBuffer<String> buffer = new IndexedBuffer<>(5);

        // WHEN
        buffer.add("foo");
        buffer.add("bar");
        buffer.add("baz");

        // THEN
        assertThat(buffer.indexOf("foo"), is(0));
        assertThat(buffer.indexOf("bar"), is(1));
        assertThat(buffer.indexOf("baz"), is(2));

        assertTrue(buffer.contains("foo"));

        // assert buffer is not full.
        // IndexedBuffer#add returns the evicted item index, if any.
        assertFalse(buffer.add("newitem").isPresent());
    }

    @Test
    public void replaceOldestOnesOnOverflow() {
        // GIVEN
        IndexedBuffer<String> buffer = new IndexedBuffer<>(5);

        // WHEN
        buffer.addAll(Arrays.asList("alpha", "bravo", "charlie", "delta", "echo", "foxtrot", "golf"));

        // THEN
        assertThat(buffer.indexOf("foxtrot"), is(0));
        assertThat(buffer.indexOf("golf"), is(1));
        assertThat(buffer.indexOf("charlie"), is(2));

        assertFalse(buffer.contains("alpha"));
        assertFalse(buffer.contains("bravo"));

        assertThat(buffer.indexOf("delta"), is(3));

        // assert buffer is full
        assertTrue(buffer.add("newitem").isPresent());

        // oldest item, by order of insertion, was "charlie"
        assertThat(buffer.indexOf("newitem"), is(2));
    }

    @Test
    public void replacesLeastRecentlyUsedOnOverflow() {
        // GIVEN
        IndexedBuffer<String> buffer = new IndexedBuffer<>(5);

        // WHEN
        buffer.addAll(Arrays.asList("alpha", "bravo", "charlie", "delta", "echo"));
        buffer.touch("alpha");
        buffer.touch("charlie");
        buffer.addAll(Arrays.asList("foxtrot", "golf"));

        // THEN "bravo" and "delta" aren't touched and thus are the oldest items.
        // As such, they're replaced with the new items on overflow
        assertThat(buffer.indexOf("alpha"), is(0));
        assertThat(buffer.indexOf("foxtrot"), is(1));
        assertThat(buffer.indexOf("charlie"), is(2));
        assertThat(buffer.indexOf("golf"), is(3));
    }
}