package com.typeahead.store;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Primary data store: query -> total search count.
 * Backed by a ConcurrentHashMap for thread-safe in-memory storage.
 */
@Component
public class QueryStore {

    private final ConcurrentHashMap<String, Long> store = new ConcurrentHashMap<>();

    private long dbReadCount = 0;
    private long dbWriteCount = 0;

    public void put(String query, long count) {
        store.put(query.toLowerCase(), count);
    }

    public void increment(String query) {
        dbWriteCount++;
        store.merge(query.toLowerCase(), 1L, Long::sum);
    }

    public void incrementAll(Map<String, Long> counts) {
        dbWriteCount++;
        counts.forEach((query, delta) ->
            store.merge(query.toLowerCase(), delta, Long::sum)
        );
    }

    public long getCount(String query) {
        return store.getOrDefault(query.toLowerCase(), 0L);
    }

    /**
     * Scans all keys for those starting with the given prefix.
     * Returns (query, count) pairs. This is the database read path.
     */
    public List<Map.Entry<String, Long>> getPrefixMatches(String prefix) {
        dbReadCount++;
        String lowerPrefix = prefix.toLowerCase();
        return store.entrySet().stream()
                .filter(e -> e.getKey().startsWith(lowerPrefix))
                .collect(Collectors.toList());
    }

    public int size() { return store.size(); }

    public long getDbReadCount() { return dbReadCount; }
    public long getDbWriteCount() { return dbWriteCount; }
}
