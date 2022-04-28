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


import static java.util.Collections.emptyList;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.commons.collections4.map.LRUMap;

import com.google.common.collect.Lists;

/**
 * Circular/ring buffer with stable indices and LRU policy. That is, new items will be injected at the position of the
 * previously least recently used one, so all others stay in the same position.
 * <p>
 * Indices start at 0 and go up to limit - 1, incrementally until the buffer is full, then by LRU policy.
 * @param <T> type of items stored in this circular buffer
 */
public class IndexedBuffer<T> {

    private final int limit;
    private final LRUMap<T, Integer> map;

    public IndexedBuffer(int limit) {
        this(limit, emptyList(), emptyList());
    }

    public IndexedBuffer(int limit, List<T> labels, List<T> evictionOrder) {
        this.limit = limit;
        this.map = new LRUMap<>(limit);
        for (int i = 0; i < labels.size(); i++) {
            map.put(labels.get(i), i);
        }
        // touch all in eviction order to line them up correctly
        evictionOrder.forEach(map::get);
    }

    /**
     * Add an item to the buffer, potentially replacing another one.
     *
     * @return evicted index, if limit was already reached.
     */
    synchronized Optional<Integer> add(T item) {
        if (map.size() < limit) {
            map.put(item, map.size());
            return Optional.empty();
        } else {
            Object key = map.firstKey();
            int evictIndex = map.remove(key);
            map.put(item, evictIndex);
            return Optional.of(evictIndex);
        }
    }

    void addAll(Iterable<T> items) {
        for (T item : items) {
            this.add(item);
        }
    }

    boolean contains(T item) {
        return map.containsKey(item);
    }

    /**
     * Signal an item has been used (for LRU policy).
     */
    void touch(T item) {
        map.get(item, true);
    }

    public synchronized int indexOf(T item) {
        return Optional.ofNullable(map.get(item, false))
                .orElse(-1);
    }

    public int size() {
        return map.size();
    }

    public synchronized List<T> asList() {
        return new ArrayList<>(map.keySet());
    }

    public int getLimit() {
        return this.limit;
    }

    public List<T> evictionOrder() {
        return Lists.newArrayList(map.mapIterator());
    }
}