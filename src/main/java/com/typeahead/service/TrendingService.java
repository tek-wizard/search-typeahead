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
 * Score formula: totalCount + sum of decay(event) for each recent event.
 * decay(event) = (WINDOW_MINUTES - ageInMinutes), so a search 1 min ago
 * contributes 59 pts and one from 59 min ago contributes 1 pt.
 *
 * This means two queries each searched once are NOT equal — the more recently
 * searched query always ranks higher, regardless of historical count.
 */
@Service
public class TrendingService {

    private static final long WINDOW_MS = 60 * 60 * 1000; // 1 hour
    private static final long WINDOW_MINUTES = 60;

    private final ConcurrentLinkedQueue<SearchEvent> recentEvents = new ConcurrentLinkedQueue<>();

    public void recordSearch(String query) {
        recentEvents.add(new SearchEvent(query.toLowerCase(), System.currentTimeMillis()));
    }

    /**
     * Computes trending score for a query using time-decay.
     * Each recent search contributes (60 - ageInMinutes) points instead of a flat boost,
     * so a search from 1 minute ago outranks one from 30 minutes ago even at the same count.
     */
    public long getScore(String query, long totalCount) {
        long now = System.currentTimeMillis();
        long cutoff = now - WINDOW_MS;
        pruneOldEvents(cutoff);

        long decayedBoost = recentEvents.stream()
                .filter(e -> e.getQuery().equals(query.toLowerCase()) && e.getTimestamp() >= cutoff)
                .mapToLong(e -> {
                    long ageMinutes = (now - e.getTimestamp()) / 60_000;
                    return Math.max(1, WINDOW_MINUTES - ageMinutes);
                })
                .sum();

        return totalCount + decayedBoost;
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
