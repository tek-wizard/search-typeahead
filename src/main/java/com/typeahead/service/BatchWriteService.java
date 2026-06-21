package com.typeahead.service;

import com.typeahead.cache.DistributedCache;
import com.typeahead.store.QueryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Batch write service to avoid synchronous DB writes on every search request.
 *
 * How it works:
 * 1. Each POST /search adds the query to an in-memory buffer (queue).
 * 2. A background task flushes the buffer every 30 seconds, or immediately
 *    if the buffer reaches BATCH_SIZE_THRESHOLD.
 * 3. On flush: aggregate counts -> single bulk write to QueryStore -> invalidate cache.
 *
 * Trade-off: if the app crashes before a flush, buffered searches are lost.
 * This is acceptable for a search-count system (approximate counts are fine).
 */
@Service
public class BatchWriteService {

    private static final Logger log = LoggerFactory.getLogger(BatchWriteService.class);
    private static final int BATCH_SIZE_THRESHOLD = 100;

    private final ConcurrentLinkedQueue<String> buffer = new ConcurrentLinkedQueue<>();
    private final QueryStore queryStore;
    private final DistributedCache distributedCache;
    private final TrendingService trendingService;

    private final AtomicLong totalFlushed = new AtomicLong(0);
    private final AtomicLong totalBatches = new AtomicLong(0);

    public BatchWriteService(QueryStore queryStore, DistributedCache distributedCache, TrendingService trendingService) {
        this.queryStore = queryStore;
        this.distributedCache = distributedCache;
        this.trendingService = trendingService;
    }

    public void add(String query) {
        buffer.add(query.toLowerCase());
        trendingService.recordSearch(query);
        if (buffer.size() >= BATCH_SIZE_THRESHOLD) {
            flush();
        }
    }

    @Scheduled(fixedDelay = 30000)
    public void scheduledFlush() {
        flush();
    }

    public synchronized void flush() {
        if (buffer.isEmpty()) return;

        Map<String, Long> aggregated = new HashMap<>();
        String query;
        while ((query = buffer.poll()) != null) {
            aggregated.merge(query, 1L, Long::sum);
        }

        queryStore.incrementAll(aggregated);

        // Invalidate cache for all affected prefixes
        for (String q : aggregated.keySet()) {
            for (int len = 1; len <= q.length(); len++) {
                distributedCache.invalidate(q.substring(0, len));
            }
        }

        long count = aggregated.values().stream().mapToLong(Long::longValue).sum();
        totalFlushed.addAndGet(count);
        totalBatches.incrementAndGet();
        log.info("[BatchWrite] Flushed {} searches ({} distinct queries) in 1 DB write. Total flushed: {}",
                count, aggregated.size(), totalFlushed.get());
    }

    public int getBufferSize() { return buffer.size(); }
    public long getTotalFlushed() { return totalFlushed.get(); }
    public long getTotalBatches() { return totalBatches.get(); }
}
