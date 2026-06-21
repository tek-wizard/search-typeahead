package com.typeahead.controller;

import com.typeahead.cache.DistributedCache;
import com.typeahead.model.SuggestionDto;
import com.typeahead.service.BatchWriteService;
import com.typeahead.service.LatencyTracker;
import com.typeahead.service.SuggestionService;
import com.typeahead.store.QueryStore;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@CrossOrigin
public class SuggestController {

    private final SuggestionService suggestionService;
    private final DistributedCache distributedCache;
    private final LatencyTracker latencyTracker;
    private final BatchWriteService batchWriteService;
    private final QueryStore queryStore;

    public SuggestController(SuggestionService suggestionService, DistributedCache distributedCache,
                              LatencyTracker latencyTracker, BatchWriteService batchWriteService,
                              QueryStore queryStore) {
        this.suggestionService = suggestionService;
        this.distributedCache = distributedCache;
        this.latencyTracker = latencyTracker;
        this.batchWriteService = batchWriteService;
        this.queryStore = queryStore;
    }

    /** Returns up to 10 prefix-matching suggestions sorted by trending score. */
    @GetMapping("/suggest")
    public List<SuggestionDto> suggest(@RequestParam(defaultValue = "") String q) {
        long start = System.currentTimeMillis();
        List<SuggestionDto> result = suggestionService.getSuggestions(q);
        latencyTracker.record(System.currentTimeMillis() - start);
        return result;
    }

    /**
     * Side-by-side comparison of basic vs trending ranking for the same prefix.
     * Basic = sorted by total count only.
     * Trending = sorted by totalCount + recentCount × 10.
     * Use this to demonstrate the recency boost in your viva.
     */
    @GetMapping("/suggest/compare")
    public Map<String, Object> compare(@RequestParam(defaultValue = "") String q) {
        List<SuggestionDto> basic = suggestionService.getBasicSuggestions(q);
        List<SuggestionDto> trending = suggestionService.getSuggestions(q);

        return Map.of(
            "prefix", q,
            "basic_ranking", basic,
            "trending_ranking", trending,
            "note", "Search a query a few times via POST /search, then compare — recently searched queries rank higher in trending_ranking"
        );
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
            Collectors.toMap(Map.Entry::getKey,
                e -> Map.of("hits", e.getValue()[0], "misses", e.getValue()[1]))
        ));
    }

    /**
     * Combined performance report:
     * - Suggest latency (avg, p50, p95, p99)
     * - Cache hit rate per node
     * - Batch write reduction stats
     * - DB read/write counts
     */
    @GetMapping("/stats")
    public Map<String, Object> stats() {
        LatencyTracker.LatencyStats lat = latencyTracker.getStats();

        Map<String, long[]> cacheRaw = distributedCache.getStats();
        long totalHits = cacheRaw.values().stream().mapToLong(a -> a[0]).sum();
        long totalMisses = cacheRaw.values().stream().mapToLong(a -> a[1]).sum();
        double hitRate = (totalHits + totalMisses == 0) ? 0.0
                : (100.0 * totalHits / (totalHits + totalMisses));

        long buffered = batchWriteService.getBufferSize();
        long flushed = batchWriteService.getTotalFlushed();
        long batches = batchWriteService.getTotalBatches();
        long writesWithoutBatching = flushed + buffered;
        long actualWrites = batches + (buffered > 0 ? 1 : 0);

        return Map.of(
            "latency", Map.of(
                "totalRequests", lat.totalRequests,
                "avgMs", lat.avgMs,
                "p50Ms", lat.p50Ms,
                "p95Ms", lat.p95Ms,
                "p99Ms", lat.p99Ms
            ),
            "cache", Map.of(
                "totalHits", totalHits,
                "totalMisses", totalMisses,
                "hitRatePercent", String.format("%.1f", hitRate)
            ),
            "batchWrites", Map.of(
                "searchesSubmitted", writesWithoutBatching,
                "actualDbWrites", actualWrites,
                "writeReductionFactor", actualWrites == 0 ? "N/A"
                        : String.format("%.1fx", (double) writesWithoutBatching / actualWrites),
                "currentBufferSize", buffered
            ),
            "database", Map.of(
                "totalReads", queryStore.getDbReadCount(),
                "totalWrites", queryStore.getDbWriteCount(),
                "queryCount", queryStore.size()
            )
        );
    }
}
