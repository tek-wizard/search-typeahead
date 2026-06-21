package com.typeahead.service;

import com.typeahead.model.SearchEvent;
import org.springframework.stereotype.Service;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Tracks recent search activity to compute a trending score.
 *
 * Score formula: totalCount + recentCount * RECENCY_BOOST
 *
 * recentCount = number of times a query was searched in the last WINDOW_MS.
 * This makes recently surging queries appear higher than historically popular but dormant ones.
 */
@Service
public class TrendingService {

    private static final long WINDOW_MS = 60 * 60 * 1000; // 1 hour
    private static final long RECENCY_BOOST = 10;

    private final ConcurrentLinkedQueue<SearchEvent> recentEvents = new ConcurrentLinkedQueue<>();

    public void recordSearch(String query) {
        recentEvents.add(new SearchEvent(query.toLowerCase(), System.currentTimeMillis()));
    }

    /**
     * Computes trending score for a query.
     * Also prunes stale events from the window.
     */
    public long getScore(String query, long totalCount) {
        long cutoff = System.currentTimeMillis() - WINDOW_MS;
        pruneOldEvents(cutoff);

        long recentCount = recentEvents.stream()
                .filter(e -> e.getQuery().equals(query.toLowerCase()) && e.getTimestamp() >= cutoff)
                .count();

        return totalCount + recentCount * RECENCY_BOOST;
    }

    /**
     * Returns a map of query -> recent count for the trending section.
     */
    public Map<String, Long> getRecentCounts() {
        long cutoff = System.currentTimeMillis() - WINDOW_MS;
        pruneOldEvents(cutoff);

        Map<String, Long> counts = new ConcurrentHashMap<>();
        for (SearchEvent event : recentEvents) {
            if (event.getTimestamp() >= cutoff) {
                counts.merge(event.getQuery(), 1L, Long::sum);
            }
        }
        return counts;
    }

    private void pruneOldEvents(long cutoff) {
        Iterator<SearchEvent> it = recentEvents.iterator();
        while (it.hasNext()) {
            if (it.next().getTimestamp() < cutoff) it.remove();
            else break; // queue is roughly time-ordered
        }
    }
}
