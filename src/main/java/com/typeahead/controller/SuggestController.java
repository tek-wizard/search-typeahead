package com.typeahead.controller;

import com.typeahead.cache.DistributedCache;
import com.typeahead.model.SuggestionDto;
import com.typeahead.service.SuggestionService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@CrossOrigin
public class SuggestController {

    private final SuggestionService suggestionService;
    private final DistributedCache distributedCache;

    public SuggestController(SuggestionService suggestionService, DistributedCache distributedCache) {
        this.suggestionService = suggestionService;
        this.distributedCache = distributedCache;
    }

    /** Returns up to 10 prefix-matching suggestions sorted by trending score. */
    @GetMapping("/suggest")
    public List<SuggestionDto> suggest(@RequestParam(defaultValue = "") String q) {
        return suggestionService.getSuggestions(q);
    }

    /** Debug endpoint showing which cache node owns the prefix and whether it's a hit. */
    @GetMapping("/cache/debug")
    public Map<String, Object> cacheDebug(@RequestParam String prefix) {
        DistributedCache.CacheDebugInfo info = distributedCache.getDebugInfo(prefix.toLowerCase());
        return Map.of(
            "prefix", prefix,
            "node", info.node,
            "hit", info.hit,
            "ttlRemainingMs", info.ttlRemainingMs,
            "hashPosition", info.hashPosition
        );
    }

    /** Returns per-node cache hit/miss stats. */
    @GetMapping("/cache/stats")
    public Map<String, Object> cacheStats() {
        Map<String, long[]> raw = distributedCache.getStats();
        return Map.copyOf(raw.entrySet().stream().collect(
            java.util.stream.Collectors.toMap(
                Map.Entry::getKey,
                e -> Map.of("hits", e.getValue()[0], "misses", e.getValue()[1])
            )
        ));
    }
}
