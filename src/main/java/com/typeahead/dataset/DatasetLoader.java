package com.typeahead.dataset;

import com.typeahead.store.QueryStore;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Loads the CSV dataset into QueryStore at application startup.
 * CSV format: query,count (header row skipped).
 */
@Component
public class DatasetLoader {

    private static final Logger log = LoggerFactory.getLogger(DatasetLoader.class);

    private final QueryStore queryStore;

    public DatasetLoader(QueryStore queryStore) {
        this.queryStore = queryStore;
    }

    @PostConstruct
    public void load() {
        int loaded = 0;
        int errors = 0;
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
                    queryStore.put(query, count);
                    loaded++;
                } catch (NumberFormatException e) {
                    errors++;
                }
            }
        } catch (Exception e) {
            log.error("Failed to load dataset", e);
        }
        log.info("[Dataset] Loaded {} queries into QueryStore ({} errors)", loaded, errors);
    }
}
