package com.typeahead.model;

public class SearchEvent {
    private final String query;
    private final long timestamp;

    public SearchEvent(String query, long timestamp) {
        this.query = query;
        this.timestamp = timestamp;
    }

    public String getQuery() { return query; }
    public long getTimestamp() { return timestamp; }
}
