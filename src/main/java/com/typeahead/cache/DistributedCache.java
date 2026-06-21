package com.typeahead.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typeahead.model.SuggestionDto;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Distributed cache facade.
 *
 * Uses ConsistentHashRing to route each prefix to one of 3 logical CacheNodes.
 * Each CacheNode stores its data in Redis under its own namespace:
 *   cache-node-1:iph, cache-node-2:java, cache-node-3:pyt, etc.
 *
 * The ring ensures the same prefix always goes to the same node,
 * and that rebalancing is minimal if nodes are added/removed.
 */
@Component
public class DistributedCache {

    private final ConsistentHashRing ring;
    private final Map<String, CacheNode> nodes = new ConcurrentHashMap<>();

    public DistributedCache(RedisTemplate<String, String> redisTemplate, ObjectMapper objectMapper) {
        List<String> nodeNames = List.of("cache-node-1", "cache-node-2", "cache-node-3");
        this.ring = new ConsistentHashRing(nodeNames);
        for (String name : nodeNames) {
            nodes.put(name, new CacheNode(name, redisTemplate, objectMapper));
        }
    }

    public List<SuggestionDto> get(String prefix) {
        return nodeFor(prefix).get(prefix);
    }

    public void put(String prefix, List<SuggestionDto> suggestions) {
        nodeFor(prefix).put(prefix, suggestions);
    }

    public void invalidate(String prefix) {
        nodeFor(prefix).invalidate(prefix);
    }

    public CacheDebugInfo getDebugInfo(String prefix) {
        String nodeName = ring.getNode(prefix);
        CacheNode node = nodes.get(nodeName);
        boolean hit = node.containsKey(prefix);
        long ttlRemaining = node.getTtlRemaining(prefix);
        return new CacheDebugInfo(nodeName, hit, ttlRemaining, ring.getHashPosition(prefix));
    }

    public Map<String, long[]> getStats() {
        Map<String, long[]> stats = new ConcurrentHashMap<>();
        for (CacheNode node : nodes.values()) {
            stats.put(node.getName(), new long[]{node.getHitCount(), node.getMissCount()});
        }
        return stats;
    }

    private CacheNode nodeFor(String prefix) {
        return nodes.get(ring.getNode(prefix));
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
