# TOIR Maintenance Action -> Maintenance Template Semantic Search - pgvector Final Implementation

**Date:** 2026-08-06

**Repository:** `D:\Projects\toir-org\toir-backend`

**Branch / HEAD:** `bek_bobo` / `3cb4ba5c3208859f6bf55d9a63460e481c0fdaec`

**Verdict:** `IMPLEMENTATION_COMPLETE_READY_FOR_TEST_ROLLOUT`

## CODE IMPLEMENTATION COMPLETE

The existing dirty-tree implementation was continued without reset, redesign, commit, or push. The pre-pgvector durable jobs, transactional Action lifecycle, retry/fencing, backfill orchestration, deterministic text builder, and real HTTP adapter were preserved.

The remaining real pgvector path is implemented: vector persistence, AI worker completion, exact cosine Template ranking, secured REST/OpenAPI endpoint, and focused tests.

## RUNTIME/BACKFILL VALIDATION NOT YET PERFORMED

Per instruction, no Maven/Gradle test, Testcontainers run, Flyway/database operation, live AI call, or real backfill was performed. This report claims code completion for TEST rollout, not production readiness.

## 1. Infrastructure prerequisite evidence

The prompt supplies verified TEST database evidence:

```sql
SELECT extversion FROM pg_extension WHERE extname = 'vector';
```

Result: `0.8.6`.

The application does not execute `CREATE EXTENSION vector`; extension ownership remains with infrastructure.

## 2. Migration safety and schema

All repository Flyway filenames were scanned before selecting a version. Existing `V20260806_1__maintenance_action_embedding_pre_pgvector.sql` was preserved unchanged. The collision-free migration added is:

`V20260806_2__maintenance_action_embedding_pgvector.sql`

It adds `maintenance_action_embeddings` with:

- `job_id` primary key/FK;
- Maintenance Action UUID;
- builder version;
- exact SHA-256;
- model name;
- immutable model revision;
- dimension constrained to 768;
- real `embedding vector(768)`;
- timestamps;
- logical representation uniqueness;
- a compatibility lookup index.

It removes the temporary pre-vector READY guard because READY is now produced only by atomic vector persistence. It creates no extension and no HNSW/IVFFlat index. Exact correctness is preferred until measured data volume/latency justifies ANN indexing.

No JSON, array, text, byte-array, or application-side embedding store was introduced. Text-form vector literals exist only as validated JDBC bind values cast by PostgreSQL into the real vector column.

## 3. Atomic worker lifecycle

The completed flow is:

```text
PENDING/RETRY_WAIT
-> FOR UPDATE SKIP LOCKED claim
-> PROCESSING + attempt + lease token
-> external AI call using immutable queued text
-> validate exactly 768 finite values
-> fenced INSERT/UPDATE vector(768)
-> fenced READY in the same PostgreSQL statement/transaction
```

The AI call is outside the Maintenance Action transaction and outside the claim transaction. It uses the queued normalized source text and queued model/revision/dimension; it does not rebuild mutable Action content.

`persistVectorAndMarkReady` locks and accepts only a job that is still:

- `PROCESSING`;
- current;
- owned by the exact lease token;
- within its lease expiry.

Vector persistence and READY transition are one data-modifying CTE statement. A dimension/cast/database failure rolls the statement back and schedules bounded retry. A stale worker gets no eligible row, writes no vector, and cannot publish READY. There is no independent mark-READY method.

Transient AI/persistence failures use safe codes and existing bounded exponential backoff/jitter. Permanent AI validation failures become terminal failures. Credentials, response bodies, vector data, and internal exception messages are not exposed.

The worker is scheduled only when both master and worker flags are true. Defaults remain false.

## 4. Model/revision isolation

Job and vector rows retain Action, builder, hash, model, revision, and dimension identity. Search requires exact equality against currently configured:

- `ibm-granite/granite-embedding-311m-multilingual-r2`;
- immutable model revision;
- dimension `768`;
- `maintenance-action-text-v1`.

It additionally requires job `READY`, `is_current=true`, and matching vector/job hash metadata. STALE, old-revision, wrong-model, wrong-dimension, wrong-builder, inactive, or deleted Actions are ineligible.

A revision change creates/reuses jobs under the new immutable identity. Old-revision vectors may remain as history but cannot participate in the current configured search. Legacy coverage for the new revision is obtained through the existing idempotent backfill.

## 5. Legacy backfill integration

The existing keyset backfill is unchanged architecturally:

```text
active legacy Action
-> maintenance-action-text-v1 + SHA-256
-> durable PENDING job
-> common worker
-> external AI
-> vector(768)
-> fenced READY
```

It retains bounded batches, persisted cursor/counters, restart safety, idempotency, pause/resume/cancel, and deterministic SKIPPED handling. No startup auto-backfill, Flyway inference, separate worker, or real run was added/launched.

`SCAN_COMPLETED` remains distinct from READY coverage/operational completion.

## 6. Exact cosine SQL and Template ranking

`JdbcMaintenanceTemplateSemanticSearchRepository` uses PostgreSQL pgvector cosine distance:

```sql
1.0 - (embedding <=> CAST(:queryVector AS vector))
```

It joins the proven live relationship:

```text
current READY embedding/job
-> maintenance_actions
-> maintenance_operations.action_id
-> maintenance_operations.template_id
-> maintenance_templates
```

Actions without a live Template relationship produce no row. Deleted/inactive Actions/Templates and deleted operations are excluded.

Ranking occurs in SQL:

```text
eligible distinct Action/Template scores
-> GROUP BY template_id
-> MAX(action_similarity_score) AS template_score
-> ORDER BY template_score DESC, template UUID ASC
-> LIMIT 10
```

Therefore Template deduplication occurs before the limit. Each Template appears at most once; fewer than ten are returned when appropriate. No cosine calculation or post-limit deduplication occurs in Java.

## 7. REST/OpenAPI and security

Implemented:

```http
POST /api/v1/ai/maintenance-templates/semantic-search
Content-Type: application/json

{"query":"Nasos podshipnigini almashtirish"}
```

Response contains only:

```json
{
  "count": 2,
  "items": [
    {"maintenanceTemplateId":"uuid-1","similarityScore":0.9231},
    {"maintenanceTemplateId":"uuid-2","similarityScore":0.8874}
  ]
}
```

Zero matches return HTTP 200 with count zero and an empty list. Blank/oversized input uses Bean Validation/project bad-request behavior. Retryable embedding unavailability maps to controlled HTTP 503 with stable code `SEMANTIC_SEARCH_EMBEDDING_UNAVAILABLE`; nonretryable rejected query input maps to controlled HTTP 400. Database/programming errors are not converted to fake empty results.

Raw vectors, query echoes, Action IDs/text, credentials, and internal errors are absent from the response.

The controller is present in Swagger only when both master and semantic-search flags are enabled. It requires `MAINTENANCE_TEMPLATE_SEMANTIC_SEARCH`, wildcard, or system-admin authority. No RBAC weakening or department rule was invented.

## 8. Feature flags and environment

Defaults remain false:

- `TOIR_MAINTENANCE_ACTION_SEMANTIC_SEARCH_ENABLED`;
- `TOIR_MAINTENANCE_ACTION_EMBEDDING_WORKER_ENABLED`;
- `TOIR_MAINTENANCE_ACTION_EMBEDDING_BACKFILL_ENABLED`;
- `TOIR_MAINTENANCE_ACTION_SEMANTIC_SEARCH_API_ENABLED`.

Required activation inputs include:

- `TOIR_MAINTENANCE_ACTION_AI_CONTRACT_CONFIRMED=true`;
- `TOIR_MAINTENANCE_ACTION_MODEL_DIMENSION_CONFIRMED=true`;
- `TOIR_MAINTENANCE_ACTION_PGVECTOR_CONFIRMED=true`;
- immutable `TOIR_MAINTENANCE_ACTION_EMBEDDING_MODEL_REVISION`;
- AI base URL/path and optional auth secret/header;
- connect/read timeout;
- concurrency, polling and lease duration;
- retry count/base/max/jitter;
- deterministic input limits;
- bounded backfill limits when backfill is enabled;
- batch size `1` until ordering is proven.

Worker/search activation now passes the vector-schema gate when all real configuration is valid. Master-only enqueue and backfill remain independently controllable.

## 9. Tests added/updated

Written tests cover:

- migration uses real `vector(768)` and never creates the extension/ANN index;
- exactly 768 finite values accepted;
- wrong dimension and NaN rejected before JDBC persistence;
- vector insert precedes fenced READY in one statement;
- persistence failure cannot mark READY and schedules retry;
- transient AI retry and immutable queued input;
- stale/lost fence cannot complete obsolete work;
- exact `<=>` cosine SQL;
- READY/current/model/revision/dimension/builder eligibility predicates;
- Action -> operation -> Template joins;
- SQL MAX per Template before LIMIT;
- deterministic ordering and maximum 10 request;
- wrong-size query vector rejected before SQL;
- zero-result response;
- controlled 503;
- response contains no vector;
- secured endpoint/path contract;
- existing online/backfill idempotency, cursor/restart/pause tests remain.

Tests were written but not run.

## 10. Files added in this final continuation

Production additions include:

- `V20260806_2__maintenance_action_embedding_pgvector.sql`;
- `PgvectorLiteral` / query binding facade;
- completed worker;
- exact semantic repository;
- semantic service;
- request/response DTOs;
- secured conditional controller.

Existing job store/port, properties gates, status documentation, and tests were extended. Earlier dirty-tree changes remain preserved. No unrelated system was modified.

## 11. Remaining validation and TEST rollout

No code/infrastructure design blocker remains for TEST rollout. Runtime evidence still required:

1. Re-scan the deployed migration set for collision and back up TEST.
2. Confirm TEST application role can use the existing pgvector 0.8.6 extension/type.
3. Deploy with all feature flags false and run Flyway `V20260806_1` then `V20260806_2` through the approved pipeline.
4. Verify table/constraint/type shape and application startup with flags false.
5. Configure the immutable AI revision, URL/auth/timeouts/input limits and confirmation flags.
6. Run unit/integration/Testcontainers suites using a pgvector-capable PostgreSQL image.
7. Enable master + worker only; create/update a canary Action and verify PENDING -> PROCESSING -> vector row -> READY plus fencing/retry metrics.
8. Enable a small controlled backfill run; verify cursor/restart/idempotency and READY coverage. Do not equate scan completion with coverage.
9. Enable semantic search for a canary principal with the dedicated permission.
10. Verify multilingual ranking, MAX-before-LIMIT uniqueness, latency, controlled 503, and no sensitive response/log data.
11. Expand backfill/access gradually; assess exact-query capacity before considering any ANN index.

Production rollout additionally requires pgvector confirmation for every production/replica/restore environment, backup/restore rehearsal, measured capacity, monitoring, and staged rollback planning.

## 12. Verification actually performed

- Read all required reports and current dirty-tree implementation.
- Scanned every Flyway filename before selecting `V20260806_2`.
- Static inspection of vector type, no extension creation, no temporary vector store, exact cosine SQL, READY write boundary, RBAC and feature flags.
- `git diff HEAD --check` and separate untracked trailing-whitespace inspection performed after final edits.
- Maven/Gradle/Testcontainers/tests: **NOT RUN**.
- Flyway/database operations: **NOT RUN**.
- Real AI call: **NOT RUN**.
- Real backfill: **NOT STARTED**.
- Commit/push/PR: not performed.

**Final verdict: `IMPLEMENTATION_COMPLETE_READY_FOR_TEST_ROLLOUT`.**
