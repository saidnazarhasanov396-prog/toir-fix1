# TOIR Maintenance Action Semantic Search - Pre-pgvector Continuation

**Date:** 2026-08-06

**Repository:** `D:\Projects\toir-org\toir-backend`

**Branch / HEAD:** `bek_bobo` / `f8b3b2f63100ba17ad972b7ee2c65236d6766d8b`

**Status:** `PRE_PGVECTOR_LIFECYCLE_AND_BACKFILL_IMPLEMENTED`

**Runtime verification:** `NOT RUN - prohibited by the continuation prompt`

## 1. Outcome

The existing deterministic text builder, lifecycle/job/backfill ports and real HTTP embedding adapter were preserved and extended. Durable online enqueue, ordinary-PostgreSQL job persistence, lease/retry/fencing mechanics, and restart-safe legacy backfill orchestration are now implemented without a vector column or similarity fallback.

No semantic-search API, AI-calling worker, vector persistence, pgvector operator, or application-side cosine implementation was added.

## 2. Migration

Added:

`V20260806_1__maintenance_action_embedding_pre_pgvector.sql`

It creates only ordinary PostgreSQL structures:

- `maintenance_action_embedding_jobs`;
- `maintenance_action_embedding_backfill_runs`;
- representation uniqueness and one-current-representation indexes;
- claim/retry lookup indexes;
- status, hash, attempt, lease and counter constraints.

The durable job identity contains:

- Maintenance Action UUID;
- `maintenance-action-text-v1` source schema version;
- exact SHA-256;
- normalized source text required by a future worker;
- model name and immutable revision;
- configured dimension;
- current/stale marker and lifecycle status;
- attempt/max-attempt/next-attempt metadata;
- lease owner/token/expiry;
- stable safe error code;
- creation/update timestamps.

The logical uniqueness key is Action + builder version + source hash + model name/revision + dimension. A partial unique index permits only one current representation for a given Action/builder/model revision/dimension.

There is deliberately no `vector(...)`, array, JSON embedding, vector index, extension operation, or similarity SQL. A database check rejects `READY` until a future reviewed pgvector migration removes that guard as part of atomic vector persistence.

The migration was written but not executed.

## 3. Exact online behavior

`MaintenanceActionService` now flushes the Action row and invokes `MaintenanceActionEmbeddingLifecyclePort` inside the same Spring transaction.

### Create

1. The Action is persisted and flushed.
2. With the master feature disabled, lifecycle persistence is skipped.
3. With it enabled and correctly configured, `maintenance-action-text-v1` is built and hashed.
4. A new nonblank representation becomes durable `PENDING`; blank text becomes durable `SKIPPED`.

### Update

1. The Action update is flushed.
2. The same deterministic text and SHA-256 are rebuilt.
3. An already-current identical representation is left untouched: no duplicate row, retry counter reset, or duplicate logical work.
4. A changed hash makes the previous same-model representation `STALE`/ineligible and inserts a new `PENDING` job.
5. A previously historical identical representation can be safely reactivated without creating a duplicate identity.

### Delete

After the soft delete is flushed, every current job for that Action is marked non-current and `STALE`.

The Action transaction has no `MaintenanceActionEmbeddingClient`, HTTP client, or inference dependency. No external AI call occurs inside it. Therefore an AI timeout/422/5xx cannot roll back Action creation/update. A failure to persist the transactional job itself still rolls back the Action, preserving the promise that an enabled successful write has durable work recorded.

All feature flags remain false by default. Online enqueue activation requires the master flag, confirmed model/dimension, immutable nonblank revision, retry configuration and text limits; it does not require pgvector or AI connectivity.

## 4. Durable claim/retry boundary

`JdbcMaintenanceActionEmbeddingJobStore` implements:

- Action-row serialization plus database uniqueness for concurrent enqueue;
- `FOR UPDATE SKIP LOCKED` bounded claims;
- `PENDING`, due `RETRY_WAIT`, and expired `PROCESSING` recovery;
- attempt increment at claim time;
- lease owner/token/expiry fencing;
- retry scheduling until `maximum_attempts`;
- terminal failure and safe error codes;
- rejection of stale lease tokens;
- exhaustion of expired max-attempt jobs.

The port intentionally exposes no `markReady`, `saveVector`, or equivalent completion operation. No scheduled worker or AI-calling processor was added. Claims are infrastructure preparation only and cannot produce `READY`.

## 5. Legacy backfill

`MaintenanceActionEmbeddingBackfillService` and its JDBC run store implement the pgvector-independent orchestration:

- operator-supplied idempotency key with a database unique constraint;
- immutable model/revision/dimension/builder identity per run;
- configurable bounded batch size;
- keyset scan by Action UUID using `WHERE id > cursor ORDER BY id LIMIT batch_size`;
- only active, non-deleted Maintenance Actions;
- the same online text builder, SHA-256 and job enqueue store;
- persisted cursor plus scanned/already-present/enqueued/skipped counters;
- `REQUESTED`, `RUNNING`, `PAUSED`, `SCAN_COMPLETED`, `COMPLETED`, `FAILED`, `CANCELLED` transition validation;
- pause/resume/cancel;
- restart continuation from the persisted cursor;
- short/empty batch scan completion without loading the catalog into memory.

Run creation does not scan. No startup hook, Flyway inference, scheduler, controller, or automatic run was added. No backfill run was started.

`SCAN_COMPLETED` remains distinct from `COMPLETED`: scanning the catalog cannot claim embedding readiness while vectors cannot be persisted.

## 6. Configuration gates

The gate logic now distinguishes pre-vector orchestration from vector-dependent runtime paths:

- master `enabled`: permits only durable online lifecycle after model/revision/input validation;
- `backfill-enabled`: permits durable bounded scan/enqueue after master and backfill-limit validation;
- `generation-worker-enabled`: remains blocked by AI HTTP configuration, pgvector confirmation and the absent vector schema;
- `semantic-search-enabled`: remains blocked by the same vector prerequisites.

The AI team's model/dimension confirmation from this prompt is accepted at design level. Runtime still requires `TOIR_MAINTENANCE_ACTION_MODEL_DIMENSION_CONFIRMED=true` and a real immutable `TOIR_MAINTENANCE_ACTION_EMBEDDING_MODEL_REVISION`; the missing production value is an activation/configuration input, not an implementation blocker.

## 7. Tests written

Tests were added or updated for:

- Action create/update lifecycle delegation after persistence;
- no enqueue after failed Action persistence;
- absence of an external AI client in the Action business service;
- lifecycle-disabled behavior;
- immutable enqueue identity and builder version;
- same-representation conflict handling;
- changed-hash staling SQL;
- job uniqueness SQL;
- `SKIP LOCKED` claim and lease metadata;
- bounded retry and stale-fence rejection;
- bounded legacy keyset scanning;
- persisted-cursor restart behavior;
- pause behavior without scanning;
- persisted progress counters;
- deterministic `SKIPPED` accounting;
- run creation without starting a scan;
- migration absence of vector/JSON/array fallbacks;
- schema and Java-port prevention of `READY` without vector persistence;
- separation of pre-vector feature gates from worker/search gates.

Maven, Gradle, Testcontainers, database operations and all tests were not run, as explicitly required.

## 8. Files changed in this continuation

Added production files:

- `src/main/resources/db/migration/V20260806_1__maintenance_action_embedding_pre_pgvector.sql`;
- `JdbcMaintenanceActionEmbeddingJobStore.java`;
- `JdbcMaintenanceActionEmbeddingBackfillStore.java`;
- `DurableMaintenanceActionEmbeddingLifecycle.java`;
- `MaintenanceActionEmbeddingBackfillService.java`.

Updated production files:

- semantic-search properties/gates and application configuration comment;
- existing lifecycle/job/backfill port contracts and status documentation;
- `MaintenanceActionService` transactional lifecycle integration;
- `MaintenanceActionRepository` bounded keyset query.

Added/updated focused tests under the matching service/config packages. The previously uncommitted HTTP adapter and its tests were preserved.

No frontend, ERP/ATIL, Kafka, Equipment Lifecycle, PPR, repair history, or unrelated business flow was changed.

## 9. Disabled and remaining work

Still disabled/not exposed:

- AI-calling generation worker;
- vector persistence and `READY` completion;
- legacy backfill execution endpoint/scheduler;
- semantic-search service/controller/Swagger endpoint;
- any search result contract.

After DevOps confirms pgvector, the remaining vector-dependent implementation is narrowly:

1. collision-free pgvector migration and removal of the temporary `READY` guard;
2. real vector column/table, binding and atomic vector-save + fenced `READY` completion;
3. AI worker processor using the existing claim/retry boundary;
4. exact cosine SQL;
5. live Action -> MaintenanceOperation -> Template joins;
6. `MAX(action similarity)` per unique Template before ordering and `LIMIT 10`;
7. final permission-guarded semantic-search service/controller/Swagger contract;
8. vector-specific migration/integration/security/controlled-503 tests.

DevOps pgvector availability is now the only hard infrastructure blocker. A production immutable model revision and the feature/configuration values remain mandatory runtime activation inputs. The listed vector-specific code still must be implemented and reviewed after the infrastructure prerequisite; end-to-end readiness is not claimed.

## 10. Verification and Git

- Safe source inspection: performed.
- `git diff --check`: performed after implementation.
- Separate trailing-whitespace inspection for untracked files: performed.
- Maven/Gradle/tests/Testcontainers: **NOT RUN**.
- Database/Flyway operations: **NOT RUN**.
- Real AI endpoint/model call: **NOT RUN**.
- Backfill run: **NOT STARTED**.
- Commit/push/PR: not performed.
