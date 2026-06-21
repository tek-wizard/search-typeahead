package com.typeahead.service;

import com.typeahead.cache.DistributedCache;
import com.typeahead.model.SuggestionDto;
import com.typeahead.store.QueryStore;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Core suggestion logic.
 * Flow: normalize prefix -> check distributed cache -> on miss, scan QueryStore
 *       -> sort by trending score -> store in cache -> return top 10.
 */
@Service
public class SuggestionService {

    private static final int MAX_SUGGESTIONS = 10;

    private final QueryStore queryStore;
    private final DistributedCache distributedCache;
    private final TrendingService trendingService;

    public SuggestionService(QueryStore queryStore, DistributedCache distributedCache, TrendingService trendingService) {
        this.queryStore = queryStore;
        this.distributedCache = distributedCache;
        this.trendingService = trendingService;
    }

    public List<SuggestionDto> getSuggestions(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return getTopGlobal();
        }

        String normalizedPrefix = prefix.trim().toLowerCase();

        // 1. Try cache
        List<SuggestionDto> cached = distributedCache.get(normalizedPrefix);
        if (cached != null) {
            return cached;
        }

        // 2. Cache miss: scan primary store
        List<Map.Entry<String, Long>> matches = queryStore.getPrefixMatches(normalizedPrefix);
        if (matches.isEmpty()) {
            return Collections.emptyList();
        }

        // 3. Sort by trending score (recency-aware)
        List<SuggestionDto> results = matches.stream()
                .map(e -> new SuggestionDto(e.getKey(), trendingService.getScore(e.getKey(), e.getValue())))
                .sorted((a, b) -> Long.compare(b.getCount(), a.getCount()))
                .limit(MAX_SUGGESTIONS)
                .collect(Collectors.toList());

        // 4. Store in cache
        distributedCache.put(normalizedPrefix, results);

        return results;
    }

    /** Returns top 10 queries globally, used for trending section. */
    private List<SuggestionDto> getTopGlobal() {
        List<Map.Entry<String, Long>> all = queryStore.getPrefixMatches("");
        return all.stream()
                .map(e -> new SuggestionDto(e.getKey(), trendingService.getScore(e.getKey(), e.getValue())))
                .sorted((a, b) -> Long.compare(b.getCount(), a.getCount()))
                .limit(MAX_SUGGESTIONS)
                .collect(Collectors.toList());
    }
}
