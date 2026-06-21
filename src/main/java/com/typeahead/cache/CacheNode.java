package com.typeahead.cache;

import com.typeahead.model.SuggestionDto;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A single logical cache node.
 * Stores prefix -> (suggestions, expiry time).
 * All access is externally synchronized via DistributedCache.
 */
public class CacheNode {

    private static final long TTL_MS = 5 * 60 * 1000; // 5 minutes

    private final String name;
    private final Map<String, CacheEntry> store = new HashMap<>();

    private long hitCount = 0;
    private long missCount = 0;

    public CacheNode(String name) {
        this.name = name;
    }

    public List<SuggestionDto> get(String prefix) {
        CacheEntry entry = store.get(prefix);
        if (entry == null || entry.isExpired()) {
            store.remove(prefix);
            missCount++;
            return null;
        }
        hitCount++;
        return entry.suggestions;
    }

    public void put(String prefix, List<SuggestionDto> suggestions) {
        store.put(prefix, new CacheEntry(suggestions, System.currentTimeMillis() + TTL_MS));
    }

    public void invalidate(String prefix) {
        store.remove(prefix);
    }

    public boolean containsKey(String prefix) {
        CacheEntry entry = store.get(prefix);
        if (entry == null || entry.isExpired()) {
            store.remove(prefix);
            return false;
        }
        return true;
    }

    public long getTtlRemaining(String prefix) {
        CacheEntry entry = store.get(prefix);
        if (entry == null || entry.isExpired()) return 0;
        return entry.expiryMs - System.currentTimeMillis();
    }

    public String getName() { return name; }
    public long getHitCount() { return hitCount; }
    public long getMissCount() { return missCount; }

    private static class CacheEntry {
        final List<SuggestionDto> suggestions;
        final long expiryMs;

        CacheEntry(List<SuggestionDto> suggestions, long expiryMs) {
            this.suggestions = suggestions;
            this.expiryMs = expiryMs;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expiryMs;
        }
    }
}
