package com.typeahead.controller;

import com.typeahead.service.BatchWriteService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@CrossOrigin
public class SearchController {

    private final BatchWriteService batchWriteService;

    public SearchController(BatchWriteService batchWriteService) {
        this.batchWriteService = batchWriteService;
    }

    /**
     * Submits a search query.
     * Adds to batch buffer (does NOT write to DB synchronously).
     * Returns a dummy confirmation message.
     */
    @PostMapping("/search")
    public Map<String, Object> search(@RequestBody Map<String, String> body) {
        String query = body.getOrDefault("query", "").trim();
        if (query.isEmpty()) {
            return Map.of("message", "Query cannot be empty");
        }
        batchWriteService.add(query);
        return Map.of(
            "message", "Searched",
            "query", query,
            "bufferSize", batchWriteService.getBufferSize()
        );
    }

    /** Returns batch write stats for the performance report. */
    @GetMapping("/batch/stats")
    public Map<String, Object> batchStats() {
        return Map.of(
            "currentBufferSize", batchWriteService.getBufferSize(),
            "totalSearchesFlushed", batchWriteService.getTotalFlushed(),
            "totalFlushBatches", batchWriteService.getTotalBatches()
        );
    }
}
