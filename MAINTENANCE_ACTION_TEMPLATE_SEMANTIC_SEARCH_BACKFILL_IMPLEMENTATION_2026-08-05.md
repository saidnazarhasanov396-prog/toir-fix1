# Maintenance Action Semantic Search + Backfill — Gated Foundation Implementation Report

**Date:** 2026-08-05

**Repository:** `D:\Projects\toir-org\toir-backend`

**Implementation status:** `PARTIAL — EXTERNALLY BLOCKED`
**Verification:** **NOT RUN — skipped by user instruction**

## 1. Outcome

This change implements only the contract-independent, safely disabled foundation permitted by the prompt's no-guessing gates:

- deterministic `maintenance-action-text-v1` source composition and SHA-256 hashing;
- configured per-field/composed character and UTF-8 byte limits, intentionally unset until the AI contract is frozen;
- strict finite vector/dimension validation utility;
- narrow internal embedding client boundary with DOCUMENT/QUERY modes, but no guessed HTTP adapter or wire DTO;
- embedding, lifecycle, job-store, and durable backfill-run state/port contracts;
- independent disabled feature/configuration switches and explicit fail-closed external-gate errors;
- dedicated search and backfill permission constants, without broad role grants;
- focused unit tests for text, multilingual normalization/hash, limits, vector validation, and fail-closed flags.

The following are intentionally not implemented because required external evidence is absent:

- pgvector Flyway schema and JDBC store: `BLOCKED_BY_PGVECTOR_PREREQUISITE`;
- live Granite HTTP adapter, worker inference, and synchronous query inference: `BLOCKED_BY_AI_SERVICE_CONTRACT`;
- online Action lifecycle enqueue, durable backfill endpoints, and semantic search API: blocked by the two foundations above;
- audit enum/constraint migrations: no live search/backfill event is exposed yet, so registering unused values would not cure the primary blockers.

No no-op lifecycle hook was attached to `MaintenanceActionService`. Doing so would let core Action writes appear integrated while no durable row can exist, violating the same-transaction durability requirement. Enabling any runtime flag fails startup rather than silently losing work.

## 2. Preflight snapshot

| Item | Value |
|---|---|
| Branch | `bek_bobo` |
| HEAD | `00435f3f90cbe257f4bf0e49f94394c1f8eb644e` |
| Upstream | `UNKNOWN` — none reported by read-only Git inspection |
| Audit source | `MAINTENANCE_ACTION_TEMPLATE_SEMANTIC_SEARCH_BACKEND_DEEP_AUDIT_2026-08-05.md`, SHA-256 `CE7E34408728566BBA307BF343D25DAAAFF7B596A62D05142D21B7E372D0AD43` |
| Initial worktree | Four pre-existing untracked reports, including the authoritative semantic-search audit |
| Repository instructions | No `AGENTS.md` found |
| Database operations | None |
| Git mutation | No branch/ref change, commit, push, merge, reset, clean, or stash |

The current checkout matches the audited branch and HEAD. The canonical domain relationship remains:

```text
maintenance_actions.id
  -> maintenance_operations.action_id (nullable)
  -> maintenance_operations.template_id (required)
  -> maintenance_templates.id
```

Locked decisions applied: catalog Action corpus; one representation per Action text version; many-to-many links resolved live; operation-only templates absent; unlinked Actions may be embedded later; global dictionary permission; exact cosine baseline; MAX/group before final limit; no threshold; all runtime flags disabled.

## 3. Runtime measurements and external gates

No approved database connection or measured values were supplied. Per the prompt, no automatic or production query was attempted.

| Measurement | Value |
|---|---|
| Total/non-deleted/active Actions | `UNKNOWN` |
| Actions with/without live Template links | `UNKNOWN` |
| Null/soft-orphan links | `UNKNOWN` |
| Blank v1 source texts | `UNKNOWN` |
| Multi-template Actions/templates with multiple Actions | `UNKNOWN` |
| Operation-only templates | `UNKNOWN` |
| Projected storage and exact-search load | `UNKNOWN` |

### AI service contract

Repository-wide search found no exact immutable revision/digest, endpoint, authentication contract, JSON shape, batch ordering, document/query prefix rules, service normalization ownership, model input maxima, error taxonomy, SLA, or returned-vector normalization guarantee.

- Model family/name: `ibm-granite/granite-embedding-311m-multilingual-r2` (locked).
- Exact immutable revision/container digest: `UNKNOWN`.
- Contract status: `BLOCKED_BY_AI_SERVICE_CONTRACT`.
- No HTTP request/response DTO or adapter was guessed.

### pgvector

Repository search still shows no pgvector extension evidence, dependency, mapping, migration, or pgvector CI image. Local Compose is plain `postgres:16-alpine`; this does not prove extension availability.

- Dev/test/stage/prod/replica/restore/CI extension status: `UNKNOWN`.
- Prerequisite status: `BLOCKED_BY_PGVECTOR_PREREQUISITE`.
- No `vector(768)` migration and no unsafe fallback column were created.
- Flyway was not asked to install an extension.

## 4. Implemented architecture boundaries

```text
MaintenanceAction
      |
      v
MaintenanceActionEmbeddingTextBuilder
  normalized UTF-8 + SHA-256
      |
      v
LifecyclePort ----> JobStorePort ----> [BLOCKED: pgvector/JDBC schema]
                                      |
                                      v
EmbeddingClient interface ----> [BLOCKED: frozen HTTP contract/adapter]

BackfillStorePort -------------> [BLOCKED: durable run schema/runner/API]
Semantic search API -----------> [BLOCKED: query adapter + vector schema]
```

The port contracts deliberately have no fake/no-op production implementation. Once gates are cleared:

1. Action save and immutable job insert must share the existing `MaintenanceActionService` transaction.
2. Worker claim must use a short `FOR UPDATE SKIP LOCKED`/lease transaction.
3. HTTP inference must occur after claim commit and outside the database transaction.
4. READY finalization must validate lease/fence and current Action hash atomically.
5. Search must use a parameterized exact cosine native/JDBC query.

## 5. Source text and hashing

Class: `src/main/java/com/toir/service/maintenanceembedding/MaintenanceActionEmbeddingTextBuilder.java`.

Schema version: `maintenance-action-text-v1`.

Exact field order:

1. `name`
2. `category`
3. `requiredSkill`
4. `safetyNotes`
5. `toolsRequired`
6. `sparePartsRequired`
7. `consumablesRequired`

Behavior:

- Unicode whitespace (`Character.isWhitespace` or `isSpaceChar`) collapses to one ASCII space inside each field;
- leading/trailing whitespace disappears;
- null/blank fields are omitted;
- fields join with exactly `\n`;
- original Unicode/case is preserved; no translation or case folding;
- character limits count Unicode code points;
- composed UTF-8 bytes are measured directly;
- SHA-256 is calculated from the exact composed UTF-8 byte sequence;
- blank legacy text is represented by `SourceText.blank() == true`, allowing the future store to persist `SKIPPED` without inference.

Excluded: all UUIDs, Action code, Template/Operation text, duration, timestamps, audit metadata, active/deleted state and execution/scheduling status.

Limits default to zero/unconfigured. This is deliberate: the AI team's actual model/service character, byte, or token maxima were not supplied. The builder fails with `AI-team input limits are not configured` rather than inventing production limits.

## 6. Vector/client boundary

`EmbeddingVectorValidator.requireFiniteDimension` rejects:

- null/empty response;
- any dimension other than the configured exact dimension;
- null values;
- NaN and positive/negative Infinity.

It accepts exactly 768 finite numeric values when called with the locked dimension.

`MaintenanceActionEmbeddingClient` is an internal Java boundary only. Its input captures text, DOCUMENT/QUERY mode, model name, revision and expected dimension; its result can report values/model/revision/mode for compatibility validation. It is not a statement about HTTP JSON. No secrets, headers, URLs, or query text logging are implemented.

## 7. Lifecycle, worker, and retry status

Contract-independent enums/ports were added:

- embedding states: `PENDING`, `PROCESSING`, `RETRY_WAIT`, `READY`, `FAILED`, `STALE`, `SKIPPED`;
- lifecycle methods: Action created/updated/deleted;
- job enqueue outcomes: `ENQUEUED`, `ALREADY_PRESENT`, `SKIPPED_BLANK`;
- claimed job identity includes job/action IDs, exact source snapshot/hash, and lease token.

The durable implementation, Action service hook, claim SQL, lease expiry recovery, retry/backoff/jitter, stale race handling, READY finalization, retention, and metrics are **not implemented**. They require the pgvector-backed schema and exact model error contract. Consequently acceptance criterion 1 (durable online enqueue) is not claimed complete.

Model failure cannot affect Action writes in this change because there is no model call or enabled hook. Future integration must preserve that property while inserting the durable PENDING row in the Action transaction.

## 8. Legacy backfill status

The contract-independent backfill states and persistence boundary are defined:

- `REQUESTED`, `RUNNING`, `PAUSED`, `SCAN_COMPLETED`, `COMPLETED`, `FAILED`, `CANCELLED`;
- immutable target model/revision/dimension/source schema and idempotency key;
- persisted UUID cursor;
- scanned/already-present/enqueued/skipped/ready/retrying/terminal-failed counters;
- create/update timestamps.

No durable run table, keyset runner, lease/fence, endpoints, controller request/response, or permission migration was created because no pgvector/job schema can safely be deployed. Therefore legacy backfill is not operational and acceptance criteria 2-3 are externally blocked.

Future endpoints remain as designed in the prompt: start/status/pause/resume/cancel/manual retry, protected by `MAINTENANCE_ACTION_EMBEDDING_BACKFILL`, with repository-consistent idempotency. Scan completion must remain distinct from READY completion.

## 9. Semantic API and exact grouping status

`POST /api/v1/ai/maintenance-templates/semantic-search` was not exposed. Exposing it while query inference and vector persistence are unavailable would create a permanently failing API and incorrectly imply completion.

The required future parameterized SQL semantics remain:

```sql
WITH eligible_links AS (
  SELECT DISTINCT o.action_id, o.template_id
  FROM maintenance_operations o
  JOIN maintenance_actions a ON a.id = o.action_id
  JOIN maintenance_templates t ON t.id = o.template_id
  WHERE o.action_id IS NOT NULL
    AND NOT o.is_deleted
    AND NOT a.is_deleted AND a.is_active
    AND NOT t.is_deleted AND t.is_active
), action_scores AS (
  SELECT e.maintenance_action_id,
         1.0 - (e.embedding <=> CAST(:query_vector AS vector)) AS score
  FROM maintenance_action_embeddings e
  WHERE e.status = 'READY' AND e.is_current
    AND e.model_name = :model_name
    AND e.model_revision = :model_revision
    AND e.dimension = 768
    AND e.source_schema_version = :source_schema_version
), template_scores AS (
  SELECT l.template_id, MAX(s.score) AS score
  FROM eligible_links l
  JOIN action_scores s ON s.maintenance_action_id = l.action_id
  GROUP BY l.template_id
)
SELECT template_id, score
FROM template_scores
ORDER BY score DESC, template_id ASC
LIMIT 10;
```

`MAX`/grouping precedes `LIMIT 10`; duplicate operation links are neutralized; live Action/Operation/Template predicates apply; operation-only templates are absent; there is no threshold. This SQL is documentation only and was not added to a repository/query implementation because its referenced vector table is gated.

## 10. Permissions and audit

Added constants:

- `MAINTENANCE_TEMPLATE_SEMANTIC_SEARCH`;
- `MAINTENANCE_ACTION_EMBEDDING_BACKFILL`.

They were not granted to any default/business role. SYSTEM_ADMIN/wildcard conventions can be applied when controllers exist. No department predicate was fabricated.

No audit enum/action or DB check migration was added because no live search/backfill operation currently emits an audit event. A follow-up implementation must atomically align Java values with `audit_logs_module_check`; if `SEARCH` is introduced it must also update `audit_logs_action_check`. Search audit must catch non-transactional failures so a successful result cannot become HTTP 500. Meaningful aggregate backfill transitions should be audited; worker polls/rows should use metrics instead.

## 11. Configuration and kill switches

Prefix: `toir.ai.maintenance-action-semantic-search`.

Independent flags, all default false:

- `TOIR_MAINTENANCE_ACTION_SEMANTIC_SEARCH_ENABLED`;
- `TOIR_MAINTENANCE_ACTION_EMBEDDING_WORKER_ENABLED`;
- `TOIR_MAINTENANCE_ACTION_SEMANTIC_SEARCH_API_ENABLED`;
- `TOIR_MAINTENANCE_ACTION_EMBEDDING_BACKFILL_ENABLED`.

External acknowledgements, default false:

- `TOIR_MAINTENANCE_ACTION_AI_CONTRACT_CONFIRMED`;
- `TOIR_MAINTENANCE_ACTION_PGVECTOR_CONFIRMED`.

Other placeholders cover base URL, endpoint path, auth header/secret, exact revision, document/query modes, timeouts, concurrency, batch/poll/lease, retry/backoff/jitter, limits, retention and backfill bounds. Secrets have no committed default value.

Fail-closed order when any runtime feature is enabled:

1. `BLOCKED_BY_AI_SERVICE_CONTRACT`;
2. `BLOCKED_BY_PGVECTOR_PREREQUISITE`;
3. `BLOCKED_BY_LIVE_ADAPTER_AND_VECTOR_SCHEMA`.

Thus toggling a flag cannot silently activate incomplete behavior.

## 12. Migrations and collision scan

Immediately before implementation, the latest occupied migrations were:

- `V20260803_1__equipment_lifecycle_export_audit_module.sql`;
- `V20260803_2__equipment_fleet_lifecycle_read_indexes.sql`.

No new migration version was selected or created. This avoids both collision and an unsafe `vector(768)` deployment before DevOps confirmation. Future work must re-list versions again; this report does not reserve a number.

## 13. Files changed

Production/config foundation:

- `src/main/java/com/toir/config/MaintenanceActionSemanticSearchProperties.java`
- `src/main/java/com/toir/config/MaintenanceActionSemanticSearchConfiguration.java`
- `src/main/java/com/toir/service/maintenanceembedding/MaintenanceActionEmbeddingTextBuilder.java`
- `src/main/java/com/toir/service/maintenanceembedding/EmbeddingVectorValidator.java`
- `src/main/java/com/toir/service/maintenanceembedding/MaintenanceActionEmbeddingClient.java`
- `src/main/java/com/toir/service/maintenanceembedding/MaintenanceActionEmbeddingStatus.java`
- `src/main/java/com/toir/service/maintenanceembedding/MaintenanceActionEmbeddingLifecyclePort.java`
- `src/main/java/com/toir/service/maintenanceembedding/MaintenanceActionEmbeddingJobStore.java`
- `src/main/java/com/toir/service/maintenanceembedding/MaintenanceActionBackfillStatus.java`
- `src/main/java/com/toir/service/maintenanceembedding/MaintenanceActionEmbeddingBackfillStore.java`
- `src/main/java/com/toir/security/PermissionConstants.java`
- `src/main/resources/application.yml`

Tests:

- `src/test/java/com/toir/service/maintenanceembedding/MaintenanceActionEmbeddingTextBuilderTest.java`
- `src/test/java/com/toir/service/maintenanceembedding/EmbeddingVectorValidatorTest.java`
- `src/test/java/com/toir/config/MaintenanceActionSemanticSearchPropertiesTest.java`

Documentation:

- this report.

Untouched: frontend, ERP/ATIL integrations and outboxes, Kafka, Equipment Lifecycle contracts/result ingestion, repair-request snapshots, PPR tasks, work-execution/history, MaintenanceOperation snapshot corpus, Flyway repair behavior, and unrelated credentials/security cleanup.

## 14. Tests added

Tests were written but not run:

- exact seven-field order and `maintenance-action-text-v1`;
- whitespace collapse including non-breaking space;
- deterministic UTF-8 SHA-256 fixture;
- Uzbek/Russian/English case/text preservation;
- null/blank omission and blank legacy marker;
- per-field and composed UTF-8 limits;
- refusal when AI-team limits are unset;
- exactly 768 finite values accepted;
- 0/767/769, null, NaN, and infinities rejected;
- all flags disabled by default;
- each external gate and subordinate-flag bypass fails closed.

Worker, lifecycle integration, backfill, search SQL/API, security, audit and migration tests were not fabricated against nonexistent implementations. They remain required in the follow-up described by the prompt.

## 15. Acceptance matrix

| Criterion | Status |
|---|---|
| New/changed Action durable enqueue | `BLOCKED_BY_PGVECTOR_PREREQUISITE` |
| Safe legacy backfill | State/port contract added; operational path blocked |
| Restart/bounded/multi-instance backfill | Contract captures cursor/counters; durable behavior blocked |
| Exact 768 finite validation/revision/schema/hash | Validator and text/hash foundation added; persistence blocked |
| Live eligibility search | Documented; query implementation blocked |
| MAX/group before LIMIT 10 | Proven/documented; API blocked |
| Minimal unique-template response | Not exposed; blocked |
| Failures cannot roll back Action writes | No model call/hook exists; full durability behavior not yet implemented |
| Permissions/audit constraints aligned | Constants added, no live event/schema; follow-up required |
| Prerequisites/staged enablement | Documented |
| Tests written/not run | Foundation tests added; gated feature tests remain |
| Out-of-scope untouched | Complete |

End-to-end completion is **not claimed**.

## 16. Required staged rollout

1. AI team freezes the exact wire contract and immutable model/container digest.
2. DevOps enables compatible pgvector everywhere and supplies a pgvector CI/Testcontainers image.
3. Implement/review vector schema, JDBC store, adapter, worker, backfill, API, audit constraints and full tests.
4. Deploy schema/backend with all four flags disabled.
5. Deploy and verify the external Granite service.
6. Enable generation worker at limited concurrency.
7. Start a small bounded legacy backfill.
8. Validate failures, expired-lease recovery, current-hash fencing and READY coverage.
9. Expand/resume backfill during a controlled window.
10. Confirm scan completion separately from agreed READY coverage.
11. Enable search only for a canary service account with the dedicated permission.
12. Validate multilingual quality and calibrate scores without a hard threshold.
13. Broaden access after latency/error/capacity review.

Worker, backfill and search retain independent kill switches. Preserve the last compatible revision through the rollback window.

## 17. Remaining blockers and next exact inputs

Required from AI team: immutable revision/digest, path, auth/mTLS, single/batch JSON fixtures, DOCUMENT/QUERY modes/prefixes, normalization responsibility, input maxima, ordering, errors, SLA/timeouts, and normalization of output vectors.

Required from DevOps: PostgreSQL/pgvector versions and extension confirmation for every environment/replica/restore/CI, application-role privileges, backup/restore validation, and an approved vector binding approach/image.

Required operational data: the audit's read-only counts plus exact-query latency/capacity target.

After those arrive, the next change can safely add the collision-free vector/job/backfill migrations, transactional lifecycle implementation, adapter/worker, admin backfill API, semantic API, audit migrations/metrics and complete test matrix.

## 18. Verification and Git summary

- Maven/Gradle/Testcontainers/test suites: **NOT RUN — skipped by user instruction**.
- Model download/inference: **NOT RUN — skipped by user instruction**.
- Database reads/writes/migrations: **NOT RUN — skipped by user instruction**.
- Static source inspection and deterministic hash fixture calculation: performed.
- `git diff --check`: passed (tracked diff); new untracked files were checked separately for trailing whitespace.
- Commit/push/PR: not performed.
- Existing user-owned untracked reports were preserved.
