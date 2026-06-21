package com.typeahead.store;

import com.typeahead.model.QueryEntity;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Primary data store backed by PostgreSQL.
 *
 * On cache miss: queries DB with a prefix scan (LIKE 'prefix%').
 * On batch flush: upserts counts using PostgreSQL's ON CONFLICT DO UPDATE.
 *
 * An index on the 'query' column makes prefix scans fast even at 100k rows.
 */
@Component
public class QueryStore {

    private final QueryRepository repository;
    private final AtomicLong dbReadCount = new AtomicLong();
    private final AtomicLong dbWriteCount = new AtomicLong();

    public QueryStore(QueryRepository repository) {
        this.repository = repository;
    }

    /** Prefix scan — called on cache miss. */
    public List<Map.Entry<String, Long>> getPrefixMatches(String prefix) {
        dbReadCount.incrementAndGet();
        List<QueryEntity> results = prefix.isEmpty()
            ? repository.findAll()
            : repository.findByQueryStartingWith(prefix.toLowerCase());

        return results.stream()
            .map(e -> new AbstractMap.SimpleEntry<>(e.getQuery(), e.getCount()))
            .collect(Collectors.toList());
    }

    /** Bulk upsert — called by BatchWriteService on flush. */
    @Transactional
    public void incrementAll(Map<String, Long> counts) {
        dbWriteCount.incrementAndGet();
        counts.forEach((query, delta) ->
            repository.upsertCount(query.toLowerCase(), delta)
        );
    }

    public long getCount(String query) {
        return repository.findById(1L)
            .map(QueryEntity::getCount)
            .orElse(0L);
    }

    public int size() {
        return (int) repository.count();
    }

    public long getDbReadCount() { return dbReadCount.get(); }
    public long getDbWriteCount() { return dbWriteCount.get(); }
}
