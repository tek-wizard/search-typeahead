package com.typeahead.dataset;

import com.typeahead.model.QueryEntity;
import com.typeahead.store.QueryRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads the CSV dataset into PostgreSQL at startup.
 * Skips loading if data already exists (so restarts are fast).
 * Uses batch inserts of 1000 rows at a time for efficiency.
 */
@Component
public class DatasetLoader {

    private static final Logger log = LoggerFactory.getLogger(DatasetLoader.class);
    private static final int BATCH_SIZE = 1000;

    private final QueryRepository repository;

    public DatasetLoader(QueryRepository repository) {
        this.repository = repository;
    }

    @PostConstruct
    @Transactional
    public void load() {
        if (repository.count() > 0) {
            log.info("[Dataset] Already loaded ({} rows), skipping.", repository.count());
            return;
        }

        int loaded = 0, errors = 0;
        List<QueryEntity> batch = new ArrayList<>(BATCH_SIZE);

        try (InputStream is = getClass().getResourceAsStream("/dataset/queries.csv");
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {

            String line = reader.readLine(); // skip header
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                int comma = line.lastIndexOf(',');
                if (comma < 0) { errors++; continue; }
                try {
                    String query = line.substring(0, comma).trim().toLowerCase();
                    long count = Long.parseLong(line.substring(comma + 1).trim());
                    batch.add(new QueryEntity(query, count));
                    loaded++;

                    if (batch.size() >= BATCH_SIZE) {
                        repository.saveAll(batch);
                        batch.clear();
                        log.info("[Dataset] Inserted {} rows...", loaded);
                    }
                } catch (NumberFormatException e) {
                    errors++;
                }
            }
            if (!batch.isEmpty()) repository.saveAll(batch);

        } catch (Exception e) {
            log.error("Failed to load dataset", e);
        }

        log.info("[Dataset] Loaded {} queries into PostgreSQL ({} errors)", loaded, errors);
    }
}
