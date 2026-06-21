# Search Typeahead System

A backend-focused search typeahead system built with Java (Spring Boot). Supports prefix suggestions, distributed caching with consistent hashing, trending searches, and batch writes.

## Architecture

```
Browser (HTML/JS)
      │  debounced GET /suggest
      ▼
Spring Boot (port 8080)
      │
  SuggestionService
      ├── DistributedCache (3 logical nodes, consistent hashing, TTL 5 min)
      │         └── on miss ↓
      └── QueryStore (ConcurrentHashMap, loaded from 100k CSV dataset)
                ▲
         BatchWriteService (in-memory queue, flushes every 30s or at 100 items)
                ▲
         TrendingService (1-hour sliding window, score = totalCount + recentCount×10)
```

### Key Design Choices

| Concern | Choice | Why |
|---------|--------|-----|
| Primary store | In-memory `ConcurrentHashMap` | Simple, fast, sufficient for demo |
| Cache distribution | Consistent hashing (MD5, 150 virtual nodes/node) | Minimizes remapping when nodes are added/removed |
| Trending ranking | `score = totalCount + recentCount × 10` | Boosts recently surging queries without permanently over-ranking them |
| Write reduction | In-memory queue + periodic flush | Avoids a DB write per search; one write per 30s or 100 searches |

## Requirements

- Java 17+
- Maven 3.8+

## Quick Start

```bash
cd search-typeahead
mvn spring-boot:run
```

Open [http://localhost:8080](http://localhost:8080)

## APIs

### `GET /suggest?q=<prefix>`
Returns up to 10 prefix-matching suggestions sorted by trending score.

```json
[
  { "query": "iphone 15 pro", "count": 100000 },
  { "query": "iphone 15", "count": 98000 }
]
```

### `POST /search`
Submits a search query. Adds to batch buffer, does NOT write to DB synchronously.

```json
// Request
{ "query": "iphone" }

// Response
{ "message": "Searched", "query": "iphone", "bufferSize": 1 }
```

### `GET /cache/debug?prefix=<prefix>`
Shows which cache node owns the prefix and whether it's currently a cache hit.

```json
{
  "prefix": "iph",
  "node": "cache-node-2",
  "hit": true,
  "ttlRemainingMs": 287432,
  "hashPosition": -3821748291034
}
```

### `GET /batch/stats`
Returns batch write performance metrics.

### `GET /cache/stats`
Returns per-node cache hit/miss counts.

## Dataset

The system loads `src/main/resources/dataset/queries.csv` at startup (~100,000 queries).

Format:
```
query,count
iphone,120000
python tutorial,95000
...
```

## Consistent Hashing

The cache uses a `ConsistentHashRing` with 3 physical nodes × 150 virtual nodes = 450 ring positions.

- Prefix is hashed using MD5 (first 8 bytes → long).
- `TreeMap.ceilingEntry(hash)` finds the responsible node clockwise.
- This ensures ~uniform distribution and minimal remapping if nodes change.

To see it in action: `GET /cache/debug?prefix=iph` shows which node owns that prefix.

## Trending Searches

Trending score formula: `score = totalCount + recentCount × 10`

- `totalCount` = all-time search count from dataset + submitted searches.
- `recentCount` = searches for this query in the last 60 minutes.
- Recency boost of 10× makes recently surging queries surface above dormant popular ones.
- Cache is invalidated after each batch flush so rankings stay fresh.

## Batch Writes

- Submitted searches go into a `ConcurrentLinkedQueue` buffer — no DB write yet.
- Buffer flushes every **30 seconds** (scheduled) or when size reaches **100**.
- Flush aggregates counts: 50 "iphone" searches → 1 DB increment of 50, not 50 writes.
- **Failure trade-off**: if the app crashes before a flush, buffered searches are lost. Acceptable for search-count data (approximate counts are fine). For stronger guarantees, buffer could be persisted to a WAL.

## Performance

- `GET /suggest` with cache hit: ~1ms (in-memory lookup)
- `GET /suggest` on cache miss: ~5-15ms (prefix scan over 100k entries)
- Cache TTL: 5 minutes; cache hit rate improves with repeated queries
- Batch writes reduce DB writes by up to 100× compared to synchronous writes

Check live stats at `GET /cache/stats` and `GET /batch/stats`.
