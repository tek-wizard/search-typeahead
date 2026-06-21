package com.typeahead.model;

public class SuggestionDto {
    private final String query;
    private final long count;

    public SuggestionDto(String query, long count) {
        this.query = query;
        this.count = count;
    }

    public String getQuery() { return query; }
    public long getCount() { return count; }
}
