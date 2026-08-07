# TOIR Maintenance Action Semantic Search — Continuation Implementation Report

**Date:** 2026-08-06

**Repository:** `D:\Projects\toir-org\toir-backend`

**Status:** `HTTP_ADAPTER_IMPLEMENTED — VECTOR_FLOW_EXTERNALLY_BLOCKED`

**Verification:** **NOT RUN — skipped by user instruction**

## 1. Continuation scope and preflight

This work continued the existing foundation without restarting or redesigning it. Both prior reports and all committed foundation files were read first.

| Item | Value |
|---|---|
| Branch | `bek_bobo` |
| Starting/current HEAD | `f8b3b2f63100ba17ad972b7ee2c65236d6766d8b` |
| Upstream | `UNKNOWN` / not configured by read-only inspection |
| Initial worktree | Clean |
| Existing foundation | Present and preserved |
| Repository instructions | No `AGENTS.md` found |

The locked catalog-Action corpus, many-to-many live link, global-dictionary permission model, exact cosine baseline, group-before-limit rule and disabled-by-default rollout were preserved.

## 2. External AI contract implemented

The real wire contract is implemented by `HttpMaintenanceActionEmbeddingClient`:

```http
POST ${TOIR_MAINTENANCE_ACTION_EMBEDDING_BASE_URL}
     ${TOIR_MAINTENANCE_ACTION_EMBEDDING_ENDPOINT_PATH}
Content-Type: application/json

{"texts":["plain text"]}
```

Response mapping:

```json
{"embeddings":[[0.123,-0.456]]}
```

Production URL and endpoint are not hardcoded. Expected operational values are:

- base URL: `https://toir-ai.tenzorsoft.uz`;
- endpoint: `/ai/recurrent_failure_analysis/recurrent-failure-analysis/embed`.

They must be supplied through environment/configuration.

The adapter:

- uses Spring `RestClient` backed by JDK `HttpClient`;
- applies configured connect and read timeouts;
- optionally applies a configured authentication header and secret as an inseparable pair;
- sends one text per request;
- rejects blank input and any configured batch size other than 1;
- verifies response embedding count equals submitted count (exactly one);
- validates the vector against the configured exact dimension;
- rejects null, empty, wrong-dimension, NaN and infinity through the existing validator;
- verifies the internal request model name, revision and dimension match configuration;
- returns only the internal vector result; it never exposes the HTTP payload to clients.

Batch size remains 1 because response ordering has not been explicitly confirmed. No Action can be paired with another Action's vector.

## 3. Error behavior

| Condition | Safe code | Retryable |
|---|---|---:|
| Connect/read/resource timeout | `EMBEDDING_SERVICE_TIMEOUT` | yes |
| HTTP 422 | `EMBEDDING_SERVICE_VALIDATION_ERROR` | no |
| HTTP 5xx | `EMBEDDING_SERVICE_UNAVAILABLE` | yes |
| Other HTTP failure | `EMBEDDING_SERVICE_HTTP_ERROR` | no |
| Malformed JSON/conversion | `EMBEDDING_SERVICE_MALFORMED_RESPONSE` | yes |
| Output count mismatch | `EMBEDDING_COUNT_MISMATCH` | no |
| Invalid vector | `INVALID_EMBEDDING_VECTOR` | no |
| Model/revision/dimension mismatch | `EMBEDDING_CONTRACT_MISMATCH` | no |

`EmbeddingServiceException` stores only a stable code, safe message and retryable flag. External response bodies, query text, credentials and nested exception messages are not copied into it.

## 4. Model/version safety and remaining AI confirmation

The adapter wire format is now proven by the continuation prompt. However, no immutable deployed model revision/container digest was supplied, and no external confirmation proves that the service currently uses `ibm-granite/granite-embedding-311m-multilingual-r2` with exactly 768 outputs.

New gate:

```text
TOIR_MAINTENANCE_ACTION_MODEL_DIMENSION_CONFIRMED=false
```

Runtime activation requires:

1. AI service contract confirmation;
2. positive URL/path/timeouts and batch size 1;
3. exact nonblank model revision;
4. locked model name and dimension 768;
5. explicit model/dimension confirmation;
6. pgvector prerequisite confirmation;
7. a follow-up vector schema implementation.

Therefore stored/query vectors cannot be mixed across revisions, and the application cannot be enabled using an unconfirmed model.

## 5. PostgreSQL/pgvector status

`BLOCKED_BY_PGVECTOR_PREREQUISITE` remains active.

No approved confirmation was supplied for pgvector in development, test, CI/Testcontainers, staging, production, replicas, or backup/restore environments. Repository inspection still finds no existing pgvector extension/mapping/schema. Per the prompt:

- no `vector(768)` Flyway migration was created;
- no JSON/float-array similarity fallback was introduced;
- no application-side cosine search was added;
- no migration attempts to install an extension;
- no database operation was run.

Latest occupied migrations remained `V20260803_1` and `V20260803_2` at inspection time. No version was reserved. The collision scan must be repeated after DevOps confirmation.

## 6. Online lifecycle status

The existing deterministic `maintenance-action-text-v1` builder and lifecycle/job ports remain unchanged.

The durable Action create/update integration is **not connected** because a transactional job row cannot be persisted before its required pgvector-backed schema is approved. A no-op or in-memory hook was intentionally not introduced.

Consequently:

- Action create/update still does not call the AI service synchronously;
- no model failure can roll back an Action;
- but durable `PENDING → PROCESSING → READY`, unchanged-hash deduplication and changed-hash STALE behavior are not yet operational.

Status: `BLOCKED_BY_PGVECTOR_PREREQUISITE` and `BLOCKED_BY_VECTOR_SCHEMA`.

## 7. Legacy backfill status

Existing durable-run state and store boundaries were preserved. No run table, runner or endpoint can be safely implemented without the embedding/job schema.

The required future flow remains:

1. keyset scan active/non-deleted Actions in bounded transactions;
2. build the same v1 normalized text/hash;
3. `ON CONFLICT DO NOTHING` on immutable representation uniqueness;
4. persist blank legacy input as `SKIPPED`;
5. persist cursor, lease/fence, statuses and counters;
6. call this same HTTP adapter outside scan/claim transactions;
7. separate scan completion from READY coverage;
8. support start/status/pause/resume/cancel/manual retry.

Status: interfaces present; operational backfill `BLOCKED_BY_VECTOR_SCHEMA`.

## 8. Semantic-search API status

`POST /api/v1/ai/maintenance-templates/semantic-search` is not exposed yet. Without a vector table/current READY rows, exposing it would create a permanently unavailable Swagger contract and falsely imply end-to-end readiness.

The required final query semantics remain unchanged:

```text
live DISTINCT(action_id, template_id)
→ READY/current/exact-revision Action similarity
→ GROUP BY template_id
→ templateScore = MAX(actionSimilarityScore)
→ ORDER BY templateScore DESC, template UUID ASC
→ LIMIT 10
```

No `LIMIT 10 Actions → Java distinct` implementation exists. No raw vector, Action text/ID or query echo is exposed.

Swagger endpoint status: `BLOCKED_BY_PGVECTOR_PREREQUISITE`.

Once the schema is approved, synchronous query failures from this adapter must be converted by the search service to a controlled stable HTTP 503.

## 9. Security and feature flags

Existing dedicated permissions are preserved:

- `MAINTENANCE_TEMPLATE_SEMANTIC_SEARCH`;
- `MAINTENANCE_ACTION_EMBEDDING_BACKFILL`.

No broad/default role grant or fabricated department predicate was added.

Worker/search/backfill/master flags remain false by default. Adapter construction is conditional on AI contract confirmation and then validates all wire/model configuration. Runtime gates fail closed with:

- `BLOCKED_BY_AI_SERVICE_CONTRACT`;
- `BLOCKED_BY_MODEL_DIMENSION_CONTRACT`;
- `BLOCKED_BY_PGVECTOR_PREREQUISITE`;
- `BLOCKED_BY_VECTOR_SCHEMA`.

## 10. Environment variables

Existing variables continue to cover flags, limits, retries, leases and retention. Relevant adapter variables:

```text
TOIR_MAINTENANCE_ACTION_AI_CONTRACT_CONFIRMED
TOIR_MAINTENANCE_ACTION_MODEL_DIMENSION_CONFIRMED
TOIR_MAINTENANCE_ACTION_PGVECTOR_CONFIRMED
TOIR_MAINTENANCE_ACTION_EMBEDDING_BASE_URL
TOIR_MAINTENANCE_ACTION_EMBEDDING_ENDPOINT_PATH
TOIR_MAINTENANCE_ACTION_EMBEDDING_AUTH_HEADER
TOIR_MAINTENANCE_ACTION_EMBEDDING_AUTH_SECRET
TOIR_MAINTENANCE_ACTION_EMBEDDING_MODEL_REVISION
TOIR_MAINTENANCE_ACTION_EMBEDDING_CONNECT_TIMEOUT
TOIR_MAINTENANCE_ACTION_EMBEDDING_READ_TIMEOUT
TOIR_MAINTENANCE_ACTION_EMBEDDING_BATCH_SIZE
```

Batch size defaults to 1. Base URL, endpoint, secret, revision and timeouts have no production defaults. Credentials must come from secret management and must not be logged.

## 11. Files changed

Updated:

- `src/main/java/com/toir/config/MaintenanceActionSemanticSearchProperties.java`
- `src/main/java/com/toir/config/MaintenanceActionSemanticSearchConfiguration.java`
- `src/main/java/com/toir/service/maintenanceembedding/MaintenanceActionEmbeddingClient.java`
- `src/main/resources/application.yml`
- `src/test/java/com/toir/config/MaintenanceActionSemanticSearchPropertiesTest.java`

Added:

- `src/main/java/com/toir/service/maintenanceembedding/EmbeddingServiceException.java`
- `src/main/java/com/toir/service/maintenanceembedding/HttpMaintenanceActionEmbeddingClient.java`
- `src/test/java/com/toir/service/maintenanceembedding/HttpMaintenanceActionEmbeddingClientTest.java`
- this continuation report.

Migrations changed: none.

Out of scope and untouched: frontend, ERP/ATIL/outboxes, Kafka, Equipment Lifecycle contracts, PPR, repair snapshots, work execution/history and unrelated flows.

## 12. Tests added/updated

Adapter tests cover:

- exact `texts` request and `embeddings` response mapping;
- exactly 768 values;
- output count mismatch;
- wrong dimension;
- malformed JSON response;
- HTTP 422 permanent mapping;
- HTTP 5xx retryable mapping;
- timeout retryable mapping;
- response-body/exception secret non-propagation;
- batch size greater than 1 rejection;
- configured model revision/mode propagation.

Properties tests now cover:

- explicit model/dimension confirmation gate;
- adapter URL/path/timeout setup;
- batch size 1 enforcement;
- pgvector and vector-schema gates after HTTP/model confirmation.

Existing vector tests still cover empty/767/769, null, NaN and infinities. Existing multilingual source-text tests remain.

Tests for transactional enqueue, worker/retry/restart, backfill, cosine SQL, many-to-many grouping, pre-limit deduplication, maximum 10, security/audit and controlled 503 remain blocked by the absent vector schema/search service and were not fabricated.

## 13. Deployment sequence

1. AI/DevOps supplies immutable model/container revision and confirms locked Granite model plus 768 dimension.
2. Configure and connectivity-test the real endpoint outside this prohibited test run.
3. DevOps enables compatible pgvector everywhere and prepares pgvector CI/Testcontainers.
4. Add collision-free vector/job/backfill schema and audit constraint migration.
5. Add transactional lifecycle store/hook, worker and retry/fencing.
6. Add durable keyset backfill runner/endpoints and readiness coverage.
7. Add exact cosine query repository and Swagger API with controlled 503.
8. Complete integration/security/migration tests.
9. Deploy with flags disabled, then enable worker, limited backfill and canary search in stages.

Independent worker/backfill/search kill switches remain mandatory. Preserve the prior compatible model revision through rollback.

## 14. Remaining blockers

1. Exact immutable AI model/container revision: `UNKNOWN`.
2. External confirmation that deployed service is locked Granite model and exactly 768-dimensional: `UNKNOWN`.
3. Batch response ordering: `UNKNOWN`; safely constrained to 1.
4. Authentication requirement/header value: operational configuration `UNKNOWN`.
5. pgvector across every required environment: `UNKNOWN`.
6. PostgreSQL vector binding/schema approval: pending.
7. Runtime data counts and exact cosine capacity: `UNKNOWN`.

End-to-end readiness is not claimed.

## 15. Verification and Git

- Maven/Gradle/tests/Testcontainers: **NOT RUN — skipped by user instruction**.
- Database operations/migrations: **NOT RUN — skipped by user instruction**.
- Frontend build: **NOT RUN — skipped by user instruction**.
- Model download/inference/live endpoint call: **NOT RUN — skipped by user instruction**.
- Safe static inspection and `git diff --check`: performed after report finalization.
- Commit/push/PR: not performed.
