package com.typeahead.cache;

import com.typeahead.model.SuggestionDto;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Distributed cache facade.
 * Uses a ConsistentHashRing to route each prefix key to one of 3 logical CacheNodes.
 * Each node is independently synchronized.
 */
@Component
public class DistributedCache {

    private final ConsistentHashRing ring;
    private final Map<String, CacheNode> nodes = new ConcurrentHashMap<>();

    public DistributedCache() {
        List<String> nodeNames = List.of("cache-node-1", "cache-node-2", "cache-node-3");
        this.ring = new ConsistentHashRing(nodeNames);
        for (String name : nodeNames) {
            nodes.put(name, new CacheNode(name));
        }
    }

    public List<SuggestionDto> get(String prefix) {
        String nodeName = ring.getNode(prefix);
        CacheNode node = nodes.get(nodeName);
        synchronized (node) {
            return node.get(prefix);
        }
    }

    public void put(String prefix, List<SuggestionDto> suggestions) {
        String nodeName = ring.getNode(prefix);
        CacheNode node = nodes.get(nodeName);
        synchronized (node) {
            node.put(prefix, suggestions);
        }
    }

    public void invalidate(String prefix) {
        String nodeName = ring.getNode(prefix);
        CacheNode node = nodes.get(nodeName);
        synchronized (node) {
            node.invalidate(prefix);
        }
    }

    public CacheDebugInfo getDebugInfo(String prefix) {
        String nodeName = ring.getNode(prefix);
        CacheNode node = nodes.get(nodeName);
        synchronized (node) {
            boolean hit = node.containsKey(prefix);
            long ttlRemaining = node.getTtlRemaining(prefix);
            return new CacheDebugInfo(nodeName, hit, ttlRemaining, ring.getHashPosition(prefix));
        }
    }

    public Map<String, long[]> getStats() {
        Map<String, long[]> stats = new ConcurrentHashMap<>();
        for (CacheNode node : nodes.values()) {
            stats.put(node.getName(), new long[]{node.getHitCount(), node.getMissCount()});
        }
        return stats;
    }

    public static class CacheDebugInfo {
        public final String node;
        public final boolean hit;
        public final long ttlRemainingMs;
        public final long hashPosition;

        public CacheDebugInfo(String node, boolean hit, long ttlRemainingMs, long hashPosition) {
            this.node = node;
            this.hit = hit;
            this.ttlRemainingMs = ttlRemainingMs;
            this.hashPosition = hashPosition;
        }
    }
}
