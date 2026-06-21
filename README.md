# Search Typeahead System

A backend-focused search typeahead system built with Java (Spring Boot). Supports prefix suggestions, distributed caching with consistent hashing, trending searches, and batch writes.

---

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                     Browser (UI)                        │
│  - Debounced input (300ms) → GET /suggest               │
│  - Enter / button click    → POST /search               │
│  - Trending section        → GET /suggest?q=            │
└──────────────────────────┬──────────────────────────────┘
                           │ HTTP
┌──────────────────────────▼──────────────────────────────┐
│              Spring Boot (port 8080)                    │
│  SuggestController   SearchController   StatsController │
└──────┬───────────────────────┬──────────────────────────┘
       │                       │
┌──────▼──────────┐   ┌────────▼────────────────────────┐
│ SuggestionService│   │       BatchWriteService          │
│                  │   │  ConcurrentLinkedQueue buffer    │
│  1. Check cache  │   │  Flush every 30s OR at 100 items │
│  2. On miss:     │   │  One DB write per flush          │
│     scan store   │   └────────────┬────────────────────┘
│  3. Score+sort   │                │ bulk increment
│  4. Cache result │   ┌────────────▼────────────────────┐
└───┬──────────────┘   │         QueryStore               │
    │                  │  ConcurrentHashMap<String,Long>  │
    │      ┌───────────│  100k queries loaded from CSV   │
    │      │           └─────────────────────────────────┘
┌───▼──────▼──────────────────────────────────────────┐
│              DistributedCache                        │
│                                                      │
│   ConsistentHashRing (MD5, 150 virtual nodes each)   │
│        ┌──────────┬──────────┬──────────┐            │
│        │  Node 1  │  Node 2  │  Node 3  │            │
│        │ HashMap  │ HashMap  │ HashMap  │            │
│        │ TTL 5min │ TTL 5min │ TTL 5min │            │
│        └──────────┴──────────┴──────────┘            │
└──────────────────────────────────────────────────────┘
       │
┌──────▼──────────────────────────────────────────────┐
│              TrendingService                         │
│  score = totalCount + recentCount × 10              │
│  Sliding 1-hour window of SearchEvents              │
└──────────────────────────────────────────────────────┘
```

### Key Design Choices

| Concern | Choice | Why |
|---------|--------|-----|
| Primary store | In-memory `ConcurrentHashMap` | Simple, fast, sufficient for demo |
| Cache distribution | Consistent hashing (MD5, 150 virtual nodes/node) | Minimizes remapping when nodes are added/removed |
| Trending ranking | `score = totalCount + recentCount × 10` | Boosts recently surging queries without permanently over-ranking dormant ones |
| Write reduction | In-memory queue + periodic flush | Avoids a DB write per search; one write per 30s or 100 searches |

---

## Requirements

- Java 17+
- Maven 3.8+

## Quick Start

```bash
cd search-typeahead
mvn spring-boot:run
```

Open [http://localhost:8080](http://localhost:8080)

---

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
Submits a search. Adds to batch buffer — no synchronous DB write.

```json
// Request
{ "query": "iphone" }

// Response
{ "message": "Searched", "query": "iphone", "bufferSize": 3 }
```

### `GET /cache/debug?prefix=<prefix>`
Shows which cache node owns the prefix and whether it's a hit.

```json
{
  "prefix": "iph",
  "node": "cache-node-2",
  "hit": true,
  "ttlRemainingMs": 287432,
  "hashPosition": -3821748291034
}
```

### `GET /stats`
Combined performance report — latency, cache hit rate, batch write reduction, DB stats.

```json
{
  "latency": {
    "totalRequests": 120,
    "avgMs": 2,
    "p50Ms": 1,
    "p95Ms": 8,
    "p99Ms": 14
  },
  "cache": {
    "totalHits": 95,
    "totalMisses": 25,
    "hitRatePercent": "79.2"
  },
  "batchWrites": {
    "searchesSubmitted": 200,
    "actualDbWrites": 3,
    "writeReductionFactor": "66.7x",
    "currentBufferSize": 12
  },
  "database": {
    "totalReads": 25,
    "totalWrites": 3,
    "queryCount": 100000
  }
}
```

### `GET /cache/stats`
Per-node hit/miss counts.

### `GET /batch/stats`
Batch write metrics only.

---

## Dataset

**Source:** Synthetic dataset generated from common tech, product, and lifestyle search queries.

Inspired by real-world search patterns across categories like programming languages, cloud tools, e-commerce products, and entertainment — similar in structure to the [AOL Query Log dataset](https://jeffhuang.com/search_query_logs.html) and the [TREC Web Track queries](https://trec.nist.gov/).

**Format:** `query,count` — 100,000 rows  
**Location:** `src/main/resources/dataset/queries.csv`  
**Loading:** `DatasetLoader.java` reads the CSV at startup via `@PostConstruct` into a `ConcurrentHashMap`.

Sample entries:
```
query,count
iphone,120000
youtube,150000
python tutorial,95000
java spring boot tutorial,70000
docker tutorial,65000
```

---

## Consistent Hashing

The cache uses a `ConsistentHashRing` with **3 physical nodes × 150 virtual nodes = 450 ring positions**.

- Prefix key is hashed using MD5 (first 8 bytes → `long`).
- `TreeMap.ceilingEntry(hash)` finds the responsible node clockwise on the ring.
- Wraps around to the first node if hash exceeds the maximum ring position.
- Ensures ~uniform key distribution and minimal remapping when nodes are added/removed.

**Demo:** `GET /cache/debug?prefix=iph` → shows node assignment and TTL in real time.

---

## Trending Searches

**Score formula:** `score = totalCount + recentCount × 10`

- `totalCount` = all-time search count (from dataset + submitted searches).
- `recentCount` = number of times this query was searched in the **last 60 minutes**.
- The **10× recency boost** makes recently surging queries surface above dormant popular ones.
- The boost naturally decays as the 1-hour window slides — no permanent over-ranking.
- After each batch flush, cache entries for affected prefixes are **invalidated** so updated rankings appear immediately.

**How to see the difference:**
- Submit "java" 5 times via the UI or `POST /search`.
- `GET /suggest?q=j` — "java" will rank higher than before due to recency boost.

---

## Batch Writes

- `POST /search` adds the query to a `ConcurrentLinkedQueue` — **no DB write yet**.
- A `@Scheduled` task flushes every **30 seconds**, or immediately when buffer hits **100 items**.
- Flush aggregates: 50 "iphone" searches → 1 `store.merge("iphone", 50)` call, not 50 writes.
- After flush, cache entries for affected prefixes are invalidated.

**Write reduction example** (visible at `GET /stats`):
```
searchesSubmitted: 500
actualDbWrites:    6
writeReductionFactor: 83.3x
```

**Failure trade-off:** If the app crashes before a flush, buffered searches in the queue are lost. This is acceptable for search-count data where approximate counts are fine. For stronger guarantees, the buffer could be persisted to a write-ahead log (WAL) on disk.

---

## Performance

All metrics are live at `GET /stats`.

| Metric | Typical value |
|--------|--------------|
| Cache hit latency | ~1ms |
| Cache miss latency (prefix scan 100k entries) | ~5–15ms |
| p95 latency | < 10ms |
| Cache TTL | 5 minutes |
| Batch write reduction | 50–100× |

---

## Non-Functional Properties

- **Easy to run:** single `mvn spring-boot:run` command, no external DB or cache server needed.
- **Modular:** each concern (cache, store, trending, batch) is a separate class.
- **Observable:** `/stats`, `/cache/debug`, `/cache/stats`, `/batch/stats` endpoints for full visibility.
- **Thread-safe:** `ConcurrentHashMap`, `ConcurrentLinkedQueue`, and per-node synchronization throughout.
