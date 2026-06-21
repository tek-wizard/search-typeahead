package com.typeahead.service;

import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Tracks response latency for the /suggest endpoint.
 * Uses a fixed-size circular buffer to keep the last 10,000 samples.
 * Computes p50, p95, p99 on demand by sorting a snapshot.
 */
@Service
public class LatencyTracker {

    private static final int BUFFER_SIZE = 10_000;

    private final long[] buffer = new long[BUFFER_SIZE];
    private final AtomicLong index = new AtomicLong(0);
    private final LongAdder totalRequests = new LongAdder();
    private final LongAdder totalLatencyMs = new LongAdder();

    public void record(long latencyMs) {
        int slot = (int) (index.getAndIncrement() % BUFFER_SIZE);
        buffer[slot] = latencyMs;
        totalRequests.increment();
        totalLatencyMs.add(latencyMs);
    }

    public LatencyStats getStats() {
        long total = totalRequests.sum();
        if (total == 0) return new LatencyStats(0, 0, 0, 0, 0);

        int filled = (int) Math.min(total, BUFFER_SIZE);
        long[] snapshot = Arrays.copyOf(buffer, filled);
        Arrays.sort(snapshot);

        long avg = totalLatencyMs.sum() / total;
        long p50 = snapshot[(int) (filled * 0.50)];
        long p95 = snapshot[(int) (filled * 0.95)];
        long p99 = snapshot[(int) (filled * 0.99)];

        return new LatencyStats(total, avg, p50, p95, p99);
    }

    public static class LatencyStats {
        public final long totalRequests;
        public final long avgMs;
        public final long p50Ms;
        public final long p95Ms;
        public final long p99Ms;

        public LatencyStats(long totalRequests, long avgMs, long p50Ms, long p95Ms, long p99Ms) {
            this.totalRequests = totalRequests;
            this.avgMs = avgMs;
            this.p50Ms = p50Ms;
            this.p95Ms = p95Ms;
            this.p99Ms = p99Ms;
        }
    }
}
