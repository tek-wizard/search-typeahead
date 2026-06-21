package com.typeahead.store;

import com.typeahead.model.QueryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QueryRepository extends JpaRepository<QueryEntity, Long> {

    /** Prefix scan — used on cache miss. Index on 'query' column makes this fast. */
    List<QueryEntity> findByQueryStartingWith(String prefix);

    /** Upsert: insert new query or add delta to existing count. */
    @Modifying
    @Query(value = """
        INSERT INTO queries (query, count)
        VALUES (:query, :delta)
        ON CONFLICT (query)
        DO UPDATE SET count = queries.count + :delta
        """, nativeQuery = true)
    void upsertCount(@Param("query") String query, @Param("delta") long delta);
}
