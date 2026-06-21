package com.typeahead.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.typeahead.model.SuggestionDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A logical cache node backed by Redis.
 *
 * Keys are namespaced by node name: "{nodeName}:{prefix}"
 * e.g. "cache-node-2:iph" stores the suggestion list for prefix "iph" on node 2.
 *
 * TTL is delegated to Redis (SETEX), so entries automatically expire.
 * The consistent hashing ring decides which node handles each prefix —
 * this class just handles storage/retrieval for its own namespace.
 */
public class CacheNode {

    private static final Logger log = LoggerFactory.getLogger(CacheNode.class);
    private static final Duration TTL = Duration.ofMinutes(5);

    private final String name;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    private final AtomicLong hitCount = new AtomicLong();
    private final AtomicLong missCount = new AtomicLong();

    public CacheNode(String name, RedisTemplate<String, String> redisTemplate, ObjectMapper objectMapper) {
        this.name = name;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public List<SuggestionDto> get(String prefix) {
        String redisKey = redisKey(prefix);
        String json = redisTemplate.opsForValue().get(redisKey);
        if (json == null) {
            missCount.incrementAndGet();
            log.debug("[{}] MISS for prefix '{}'", name, prefix);
            return null;
        }
        try {
            hitCount.incrementAndGet();
            log.debug("[{}] HIT for prefix '{}'", name, prefix);
            return objectMapper.readValue(json, new TypeReference<List<SuggestionDto>>() {});
        } catch (Exception e) {
            missCount.incrementAndGet();
            return null;
        }
    }

    public void put(String prefix, List<SuggestionDto> suggestions) {
        try {
            String json = objectMapper.writeValueAsString(suggestions);
            redisTemplate.opsForValue().set(redisKey(prefix), json, TTL);
        } catch (Exception e) {
            log.warn("[{}] Failed to cache prefix '{}'", name, prefix);
        }
    }

    public void invalidate(String prefix) {
        redisTemplate.delete(redisKey(prefix));
    }

    public boolean containsKey(String prefix) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(redisKey(prefix)));
    }

    public long getTtlRemaining(String prefix) {
        Long ttl = redisTemplate.getExpire(redisKey(prefix));
        return (ttl != null && ttl > 0) ? ttl * 1000 : 0;
    }

    /** Redis key format: "cache-node-1:iph" */
    private String redisKey(String prefix) {
        return name + ":" + prefix;
    }

    public String getName() { return name; }
    public long getHitCount() { return hitCount.get(); }
    public long getMissCount() { return missCount.get(); }
}
