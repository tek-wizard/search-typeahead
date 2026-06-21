package com.typeahead.model;

import jakarta.persistence.*;

@Entity
@Table(name = "queries", indexes = {
    @Index(name = "idx_query_text", columnList = "query")
})
public class QueryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "query", unique = true, nullable = false, length = 500)
    private String query;

    @Column(name = "count", nullable = false)
    private Long count;

    public QueryEntity() {}

    public QueryEntity(String query, Long count) {
        this.query = query;
        this.count = count;
    }

    public Long getId() { return id; }
    public String getQuery() { return query; }
    public Long getCount() { return count; }
    public void setCount(Long count) { this.count = count; }
}
