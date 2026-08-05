# TOIR Maintenance Action -> Top-10 Unique Maintenance Template Semantic Search — Backend Deep Audit

**Audit date:** 2026-08-05  
**Scope:** backend audit only; no implementation  
**Runtime/build/test verification:** **NOT RUN — skipped by user instruction**

## 1. Executive summary

The canonical reusable action is `com.toir.entity.maintenance.MaintenanceAction`, persisted in `maintenance_actions` with a UUID primary key. It is not itself owned by one template. The canonical route to a template is:

```text
maintenance_actions.id
        1
        |
        | maintenance_operations.action_id (nullable)
        *
maintenance_operations
        *
        |
        | maintenance_operations.template_id (not null)
        1
maintenance_templates.id
```

Therefore one action can be linked to zero, one, or many templates; one template can contain zero or many operations, and any operation may have no action. This is a many-to-many association at the Action/Template level, implemented by `maintenance_operations`, not a direct Action -> Template foreign key. Evidence: `MaintenanceOperation.template` is mandatory while `MaintenanceOperation.action` is optional (`src/main/java/com/toir/entity/maintenance/MaintenanceOperation.java:18-24`); the database adds nullable `action_id` and its FK in `V20260526_4__maintenance_actions_and_equipment_rules.sql:30-49`, while the baseline supplies mandatory `template_id` and its FK (`B20260523_7__schema_baseline.sql:1211-1226,3474-3478`).

Option A (one embedding per catalog action, grouped by template at query time) is structurally feasible. The query must join current action embeddings through active, non-deleted operations and templates, calculate action similarity, group by template with `MAX`, and only then order and limit. It must not fetch ten actions and deduplicate in Java.

However, implementation is not unconditional. The main explicit decisions/blockers are:

1. A template operation stores its own name, description, instructions/resources and can override or outlive the linked catalog action values. The business owner must confirm that catalog `MaintenanceAction` meaning—not operation snapshot text—is the intended search corpus (`MaintenanceTemplateService.applyOperationFields`, lines 209-232).
2. Actions/templates are global dictionaries with no department/tenant column. Existing department-scoped authorization cannot be applied relationally. A global dedicated permission is feasible; department-scoped search requires a new proven ownership rule/schema.
3. No pgvector usage, extension, vector mapping, embedding job, client, or model contract exists. DevOps and AI-team prerequisites are required.
4. Runtime data was not queried. Counts of unlinked actions, multi-template actions, and operation-only templates are `UNKNOWN` until the supplied read-only SQL is run by an operator.

**Overall verdict: `READY_WITH_EXPLICIT_DECISIONS`.** The schema can implement correct unique-template grouping, but W0 decisions and pgvector/model-service prerequisites must be closed before production implementation.

## 2. Repository snapshot

| Item | Evidence/result |
|---|---|
| Repository | `D:\Projects\toir-org\toir-backend` |
| Branch | `bek_bobo` |
| HEAD | `00435f3f90cbe257f4bf0e49f94394c1f8eb644e` |
| Upstream | `UNKNOWN` — `git rev-parse --abbrev-ref --symbolic-full-name @{upstream}` returned no configured upstream |
| Initial worktree | Three pre-existing untracked reports: `WEBSOCKET_METER_AUTHENTICATION_DEEP_AUDIT_2026-08-04.md`, `WEBSOCKET_METER_AUTHENTICATION_IMPLEMENTATION_2026-08-04.md`, `WORK_ORDER_CREATE_AND_MATERIAL_USAGE_DEEP_AUDIT_2026-08-05.md` |
| Framework | Spring Boot `3.3.5` (`pom.xml:7-11`), Java `21` (`pom.xml:21`) |
| Persistence | Spring Data JPA/Hibernate through `spring-boot-starter-data-jpa` (`pom.xml:48-51`); effective Hibernate patch version was not resolved because builds were forbidden |
| Database | PostgreSQL; local Compose image `postgres:16-alpine` (`docker-compose.yml:2-4`) |
| Migration | Flyway `11.19.1` with PostgreSQL module (`pom.xml:24,31-38`); enabled, baseline-on-migrate, validate-on-migrate (`application.yml:9-14`) |
| Hibernate schema mode | `ddl-auto: validate`, open-in-view false (`application-dev.yml:10-18`, `application-prod.yml:11-20`) |
| Repository instructions | No `AGENTS.md` found under the repository |
| Git operations | No fetch, branch mutation, stash, reset, clean, commit, or push performed |

The application unconditionally calls `flyway.repair()` before `migrate()` (`src/main/java/com/toir/config/FlywayConfig.java:11-17`). That is existing behavior, not changed in this audit, and deserves independent operational review because automatic checksum repair can hide drift.

## 3. Proven domain model

### 3.1 Relevant concepts and their boundaries

| Concept | Entity/table and UUID key | Write/read path | Why it is or is not the embedding target |
|---|---|---|---|
| Reusable Maintenance Action catalog | `MaintenanceAction` / `maintenance_actions`; UUID inherited from `BaseEntity` | `MaintenanceActionController` -> `MaintenanceActionService` -> `MaintenanceActionRepository`; DTOs `MaintenanceActionRequest`/`MaintenanceActionDto` | **Canonical approved target.** It is reusable business text and is linked to templates through operations. Entity evidence: `MaintenanceAction.java:13-49`; key/soft-delete: `BaseEntity.java:15-26`; migration: `V20260526_4...sql:1-28`. |
| Maintenance Template | `MaintenanceTemplate` / `maintenance_templates`; UUID | `MaintenanceTemplateController` -> `MaintenanceTemplateService` -> `MaintenanceTemplateRepository`; request/response under `dto/maintenancetemplate` | Returned object ID, not embedded metadata. Holds template name/description/type and owns operations (`MaintenanceTemplate.java:14-56`). |
| Template/regulation operation | `MaintenanceOperation` / `maintenance_operations`; UUID | `POST /api/v1/maintenance-templates/{id}/operations` and `DELETE /api/v1/maintenance-templates/operations/{operationId}` -> `MaintenanceTemplateService.addOperation/removeOperation` -> `MaintenanceOperationRepository` | Intermediate relationship and independent operation snapshot. Not the approved action entity. It has its own name/description/instructions (`MaintenanceOperation.java:29-68`). |
| Maintenance Regulation | `MaintenanceRegulation` / `maintenance_regulations`; UUID | `MaintenanceRegulationController`/`MaintenanceRegulationService`/repository and regulation DTOs | Points to a template by nullable UUID (`MaintenanceRegulation.java:31-53`); not an Action. |
| Work Order task/operation | `maintenance.WorkOrderTask` / `work_order_tasks`; UUID | Work-order APIs and `WorkOrderService` | Execution-time task with title/description/status and source template/operation IDs (`WorkOrderTask.java:20-53`); not reusable catalog Action. |
| PPR task operation | `PprTask` / `ppr_tasks`; UUID | PPR APIs/services/repository | Planned work item with regulation/rule/equipment and volatile schedule/status (`PprTask.java:23-92`); not catalog Action. |
| Performed work | `WorkExecution` / `work_executions`; UUID | `WorkExecutionController`/`WorkExecutionService`/repository, `WorkExecutionDto` | Contains actual notes/result (`WorkExecution.java:18-34`); historical execution, not reusable Action. |
| Maintenance history anchor | `MaintenanceCompletionAnchor` / `maintenance_completion_anchors`; UUID | Completion flows in work order/repair services | Equipment/work-order/PPR linkage and performed time/note (`MaintenanceCompletionAnchor.java:30-73`); history, not catalog Action. |
| Repair-request action selection | `RepairRequestTemplateAction` / `repair_request_template_actions`; UUID | `RepairRequestService` and repair request DTOs/repository | Snapshot of a chosen template/operation/action for a repair request. Its FKs are created in `V20260616_5...sql:78-86`; it is not canonical template membership. |
| AI lifecycle representation | `EquipmentLifecycleContextV1.MaintenanceEvent` and export DTOs | Equipment lifecycle assembler/export services | DTO/export representation only; no reusable Action ownership and expressly out of scope. |

There is no separate mapper class for Maintenance Action: mapping is the static `MaintenanceActionDto.from` factory (`src/main/java/com/toir/dto/maintenanceaction/MaintenanceActionDto.java:20-34`). Template mapping is private service code (`MaintenanceTemplateService.templateDto/operationDto`, lines 397-441) plus DTO factories (`MaintenanceTemplateDto.java:39-52`, `MaintenanceOperationDto.java:57-70`).

### 3.2 Canonical conclusion

Embed `maintenance_actions`, keyed by `maintenance_actions.id`. Resolve result templates only at search time through active `maintenance_operations.action_id -> maintenance_operations.template_id -> maintenance_templates.id`. Do not embed a template UUID, operation UUID, repair-request snapshot, PPR task, work execution, completion anchor, timestamp, or audit field.

The conclusion is subject to W0 confirmation because operation text can differ from catalog action text. `addOperation` accepts an optional `actionId`, but chooses request values first and falls back to action values (`MaintenanceTemplateService.java:149-158,209-232`). Thus catalog-action semantic similarity is not guaranteed to describe the exact text currently displayed in every template operation.

## 4. Action -> Template relationship map

| Question | Evidence-based answer |
|---|---|
| Direct FK on action? | No. `MaintenanceAction` has no template property (`MaintenanceAction.java:20-49`). |
| Intermediate entity | `MaintenanceOperation`. |
| Cardinality | Action 1 -> 0..N operations; Template 1 -> 0..N operations. Effective Action <-> Template is many-to-many. |
| Can one action belong to several templates? | **Structurally yes.** No uniqueness constraint exists on `maintenance_operations.action_id`; only `(template_id, sequence)` is unique (`MaintenanceOperation.java:9-10`; baseline lines 2818-2822). Actual occurrence count is `UNKNOWN`. |
| Can an action exist without a template? | **Structurally yes.** Action creation never creates an operation (`MaintenanceActionService.java:41-60`). Actual count is `UNKNOWN`. |
| Can a template operation exist without an action? | Yes; `action_id` is nullable and `addOperation` permits null (`MaintenanceOperation.java:22-24`; `MaintenanceTemplateService.java:149-156`). Actual count is `UNKNOWN`. |
| Is template link mutable? | An existing operation is updated when request `id` is supplied; `op.setTemplate(t)` and `op.setAction(action)` can alter the association within the guarded template (`MaintenanceTemplateService.java:141-158,176-182,209-210`). Moving an existing operation to another template is rejected by `getOperationForTemplate`; action replacement/removal is allowed. |
| Template archive/delete | `active` can change on update (`MaintenanceTemplateService.java:255-267`); delete is soft delete (`lines 124-138`). No restore API was found. |
| Action archive/delete | `active` can change on full update (`MaintenanceActionService.java:82-93`); delete is soft delete (`lines 70-75`). No restore API was found. |
| Physical cascade | Template has JPA `cascade = ALL, orphanRemoval = true` (`MaintenanceTemplate.java:53-56`), but production delete only sets `is_deleted`; it does not physically remove operations. Database `template_id` FK has no shown `ON DELETE CASCADE`; action FK likewise has no cascade (`V20260526_4...sql:45-49`). |
| FK ownership | `maintenance_operations` owns both FKs. |
| Stable/exposable template UUID | UUID is the entity PK inherited from `BaseEntity` and already returned in `MaintenanceTemplateDto.id` (`MaintenanceTemplateDto.java:10-21`). It is technically stable and already API-visible, subject to authorization. |
| Orphans/legacy unresolved | Hard FK orphans should be prevented, but soft-deleted/inactive parent/action and null links are possible. Runtime prevalence is `UNKNOWN`. |
| Versioned/duplicate templates | No explicit template version column or clone path was found. `code` is unique in entity/schema; soft-delete-aware uniqueness behavior needs runtime schema confirmation. Business duplicates by text remain possible. |
| Legacy/new relationship difference | `action_id` was added later by `V20260526_4`; pre-existing operations can remain null. No migration backfilled it. This creates a plausible legacy split; counts are `UNKNOWN`. |

### Operator-only read-only measurement SQL (do not run in Flyway/startup)

```sql
-- Overall and eligibility counts
SELECT count(*) AS total_actions,
       count(*) FILTER (WHERE NOT is_deleted) AS non_deleted_actions,
       count(*) FILTER (WHERE NOT is_deleted AND is_active) AS active_actions
FROM maintenance_actions;

-- Actions linked/unlinked through live operations and live templates
SELECT count(*) FILTER (WHERE linked) AS actions_with_template,
       count(*) FILTER (WHERE NOT linked) AS actions_without_template
FROM (
  SELECT a.id, EXISTS (
    SELECT 1
    FROM maintenance_operations o
    JOIN maintenance_templates t ON t.id = o.template_id
    WHERE o.action_id = a.id AND NOT o.is_deleted AND NOT t.is_deleted
  ) AS linked
  FROM maintenance_actions a
  WHERE NOT a.is_deleted
) x;

-- Physical or soft/inactive orphan conditions
SELECT o.id, o.action_id, o.template_id,
       a.id IS NULL AS missing_action,
       coalesce(a.is_deleted, false) AS deleted_action,
       t.id IS NULL AS missing_template,
       coalesce(t.is_deleted, false) AS deleted_template
FROM maintenance_operations o
LEFT JOIN maintenance_actions a ON a.id = o.action_id
LEFT JOIN maintenance_templates t ON t.id = o.template_id
WHERE NOT o.is_deleted
  AND (o.action_id IS NULL OR a.id IS NULL OR a.is_deleted OR t.id IS NULL OR t.is_deleted);

-- Blank recommended source text
SELECT count(*) AS blank_source_actions
FROM maintenance_actions a
WHERE NOT a.is_deleted
  AND btrim(concat_ws(E'\n', nullif(btrim(a.name), ''), nullif(btrim(a.category), ''),
      nullif(btrim(a.required_skill), ''), nullif(btrim(a.safety_notes), ''),
      nullif(btrim(a.tools_required), ''), nullif(btrim(a.spare_parts_required), ''),
      nullif(btrim(a.consumables_required), ''))) = '';

-- Templates/actions with multiplicity
SELECT template_id, count(DISTINCT action_id) AS distinct_actions
FROM maintenance_operations
WHERE NOT is_deleted AND action_id IS NOT NULL
GROUP BY template_id HAVING count(DISTINCT action_id) > 1
ORDER BY distinct_actions DESC;

SELECT action_id, count(DISTINCT template_id) AS distinct_templates
FROM maintenance_operations
WHERE NOT is_deleted AND action_id IS NOT NULL
GROUP BY action_id HAVING count(DISTINCT template_id) > 1
ORDER BY distinct_templates DESC;

-- Operation-only templates
SELECT t.id, count(o.id) AS live_operations,
       count(o.id) FILTER (WHERE o.action_id IS NOT NULL) AS linked_actions
FROM maintenance_templates t
LEFT JOIN maintenance_operations o ON o.template_id = t.id AND NOT o.is_deleted
WHERE NOT t.is_deleted
GROUP BY t.id
HAVING count(o.id) FILTER (WHERE o.action_id IS NOT NULL) = 0;
```

## 5. CRUD and lifecycle path matrix

Complete production search found no bulk Action create, Action import, Action clone, Action PATCH, Action restore, or Action hard-delete endpoint. Direct Action construction occurs only in `MaintenanceActionService.create` (`rg` evidence at line 45). No action seed inserts were found; the schema migration creates only the table. Repository `save/delete` remains callable by future/internal code, so coverage tests should guard new bypasses.

| Operation/path | Transaction | Search text? | Template link? | Events/after commit | Future behavior |
|---|---|---:|---:|---|---|
| `POST /api/v1/maintenance-actions` -> `MaintenanceActionService.create` (`Controller:65-68`; `Service:41-60`) | `@Transactional` | Creates all catalog fields | No | No domain event or audit call | Save Action, insert durable `PENDING` embedding row in same transaction; return without inference. |
| `PUT /api/v1/maintenance-actions/{id}` -> `update` (`Controller:70-76`; `Service:63-68`) | `@Transactional` | Yes: all fields replaced; active may change | No | None | Rebuild normalized text. Same hash: no job. Changed hash: old current embedding `STALE`, insert new `PENDING`. Active-only change should alter eligibility but not vector version. |
| `DELETE /api/v1/maintenance-actions/{id}` -> `delete` (`Controller:78-82`; `Service:70-75`) | `@Transactional` | No | Links remain physically present | None | Exclude through action `is_deleted`; optionally mark current embeddings stale for housekeeping. |
| Action deactivate/reactivate via PUT (`Service:91-93`) | Same update transaction | No if other text unchanged | No | None | Exclude/include by live action state. On reactivation reuse compatible current READY embedding if hash/revision/schema match; otherwise enqueue. |
| `POST /maintenance-templates/{id}/operations` -> `addOperation` (`TemplateController:94-98`; service lines 140-174) | `@Transactional` | Does not change catalog Action; changes operation snapshot | Adds/replaces/removes Action link | None for operation | Search membership changes immediately via relational join. Do not re-embed an unchanged action. Confirm whether operation snapshot text should instead drive embeddings. |
| `DELETE /maintenance-templates/operations/{id}` -> `removeOperation` (`TemplateController:100-105`; service lines 235-241) | `@Transactional` | No | Soft-removes link | None | Exclude that link at query time; do not delete reusable action embedding. |
| Template update/deactivate (`TemplateService:104-122,255-268`) | `@Transactional` | Template text changes only | No | Audit scheduled | Query eligibility follows `is_active/is_deleted`; no action re-embedding if template text is excluded. |
| Template soft delete (`TemplateService:124-138`) | `@Transactional` | No action text change | Operations remain | Audit scheduled | Exclude template before scoring/grouping. |
| Regulation create/update/delete | Transactional service paths | Regulation text only | `template_id` may change, but not canonical Action membership | Existing audit | No Action embedding change; regulation is not the required result grouping path. |
| Repair request selections (`RepairRequestService:410-615`) | Part of repair request transaction | Snapshot/custom operation text | Copies template/action IDs into request-specific rows | Existing repair flow effects | Not a canonical membership hook and must not enqueue Action embeddings. |
| Migration `V20260526_4` | Flyway | Creates catalog schema only | Adds nullable operation link | N/A | Legacy Actions require explicit backfill; never infer embeddings in Flyway. |

**Bypass warning:** integrating only `MaintenanceActionService.create` misses `update`, activation changes, soft delete, legacy rows, and any future direct repository/import path. Search correctness also depends on operation link changes and template state even though those do not require vector regeneration.

## 6. Recommended deterministic source text

### Field audit

| Field | DB/type/constraint | Language and mutability | Recommendation |
|---|---|---|---|
| `name` | varchar(255), non-null; request `@NotBlank @Size(255)` (`MaintenanceActionRequest.java:12`) | Free original text; mutable | Include first. Primary business meaning. |
| `category` | varchar(128), nullable; request size 128 (`Request:13-14`) | Free text example “MECHANICAL”; mutable | Include. Useful vocabulary. |
| `requiredSkill` | varchar(255), nullable; request size 255 (`Request:16`) | Free text; mutable | Include. Useful skill terminology. |
| `safetyNotes` | text nullable, no request max (`Entity:36-37`; `Request:17`) | Free original text; mutable | Include with an implementation max/document size guard. |
| `toolsRequired` | text nullable, no request max (`Entity:39-40`; `Request:18`) | Free original text; mutable | Include. |
| `sparePartsRequired` | text nullable, no request max (`Entity:42-43`; `Request:19`) | Free original text; mutable | Include. |
| `consumablesRequired` | text nullable, no request max (`Entity:45-46`; `Request:20`) | Free original text; mutable | Include. |
| `code` | varchar(128), generated | Identifier, not natural meaning | Exclude. |
| `defaultDurationHours` | double nullable | Numeric planning metadata | Exclude. |
| `active`, `is_deleted` | boolean | Volatile eligibility | Exclude from text; filter relationally. |

Recommended source schema `maintenance-action-text-v1`:

```text
name
category
requiredSkill
safetyNotes
toolsRequired
sparePartsRequired
consumablesRequired
```

Use a fixed field order; trim each field; convert every Unicode whitespace run to one ASCII space within fields; omit null/blank fields; join nonblank fields with exactly `\n`; preserve original Uzbek/Russian/English characters without forced translation or case folding; encode the exact normalized string as UTF-8 and hash it with SHA-256. UUIDs, `maintenanceTemplateId`, codes, timestamps, audit metadata, duration, and volatile states are excluded. A blank normalized source should be explicitly `SKIPPED`/ineligible, not sent to the model. In practice `name` validation should prevent blank new rows, but legacy/runtime data remains unmeasured.

Do **not** add template name/description to every action embedding in v1. Evidence shows an action can serve several templates, so there is no single canonical template context; duplicating template context would either require one vector per Action/Template link or conflate unrelated templates. Template context can be a later separate corpus (Option C) after measured need. Conversely, operation-specific descriptions are real business text and may justify operation-level embeddings if W0 determines that users mean the template checklist rather than the global Action catalog.

## 7. Current async/job capabilities

| Capability | Existing evidence | Readiness |
|---|---|---|
| Durable domain-specific jobs | Equipment lifecycle export persists jobs and idempotency (`EquipmentLifecycleExportJobRepository.java:19-27`); ERP/ATIL have transport outboxes | Pattern exists, but no embedding job. Do not reuse ERP/ATIL outboxes. |
| After-commit dispatch | `EquipmentLifecycleExportDispatcher.dispatchAfterCommit` registers synchronization (`lines 30-47`) | Reusable concept, but durable row must be inserted before commit. Dispatcher alone is not durability. |
| Scheduled work | `@EnableScheduling` is present in `ToirApplication`; several scheduled jobs exist | Proven platform support. |
| Multi-instance claiming | Export cleanup uses `FOR UPDATE SKIP LOCKED` (`EquipmentLifecycleExportJobRepository.java:47-64`); export leases use pessimistic locks | Proven pattern. Embedding claim should atomically transition bounded eligible rows. |
| Bounded executor | Export properties include concurrency and queue capacity (`EquipmentLifecycleExportProperties.java:16-28`) | Useful convention. |
| Idempotency | Export `(creator,idempotencyKey)` lookup and lease fencing patterns exist | Action embedding requires a domain key, not request idempotency. |
| Retry/backoff | ERP/ATIL rows have attempts/`next_attempt_at`; no generic reusable bounded exponential retry abstraction was proven | Partial. Build embedding-specific retry. |
| Diagnostics | Export stores status/failure/lease metadata | Pattern exists. Sanitize model failures. |
| Backfill | No generic Action embedding backfill mechanism found | Missing. |
| Circuit breaker | No Resilience4j/Bucket4j/circuit breaker dependency or rate limiter found | Missing. |

Strategy verdicts:

- **Option A — recommended, conditional:** one vector per `maintenance_actions` row; join/group at query time. Supports reused actions and the locked `MAX` semantics.
- **Option B — not compliant as default:** aggregation loses the highest individual action match, changes whenever any operation changes, risks model input limits, and cannot meet approved action lifecycle semantics.
- **Option C — defer:** separate template/operation vectors could address template prose but doubles lifecycle and calibration complexity. Introduce only for a proven second retrieval use case.

Suggested uniqueness key is confirmed as necessary and sufficient for immutable representations: `(maintenance_action_id, model_revision, source_schema_version, source_text_hash)`. Include model name and dimension as checked metadata; if revisions could collide across model names, include `model_name` in the unique key as defense-in-depth. Multiple Action/Template links must not create duplicate embedding jobs.

## 8. Model-service integration boundary

Granite must remain outside the Spring JVM. Existing code uses Spring `RestClient` for ERP/ATIL (`AtilEquipmentStatusOutboxDispatcher.java:19,28-39`) and JDK `HttpClient` with connect timeout for Faktura/MES, but those clients are integration-specific and do not define a safe AI contract.

| Item | State |
|---|---|
| Feature flag/config properties | Missing for semantic search |
| Base URL/internal auth header or mTLS | Missing/contract `UNKNOWN` |
| Connect/read timeout | Missing for this service; conventions exist elsewhere |
| Request/response DTO | Missing; must not be invented |
| Model name | Locked: `ibm-granite/granite-embedding-311m-multilingual-r2` |
| Immutable exact revision/digest | `UNKNOWN`; must be supplied by AI team |
| Dimension | Locked at 768 by requirement; must be response-validated |
| Document/query input mode or prefix | `UNKNOWN`; AI team must state whether model service requires asymmetric modes/prefixes |
| Batch shape/order/error semantics | `UNKNOWN` |
| Numeric validation | Missing; future client must reject null/empty, wrong length, NaN and +/-Infinity |
| Error mapping | Missing; synchronous query timeout/unavailability must become controlled `503`, not stack trace |
| Credential redaction | No dedicated embedding client. Existing config contains credentials directly in YAML (`application.yml:91-99`, prod datasource/JWT settings), so the new secret must be environment/secret-manager only and never included in DTO `toString`, logs, health output, or exception messages. This report intentionally does not reproduce existing secret values. |

AI-team contract decisions: endpoint path; JSON request/response shape; single and batch support; exact immutable model digest; query/document modes; normalization ownership; maximum UTF-8 bytes/tokens; authentication mechanism/header; timeout/SLA; error codes; response ordering; whether vectors are already normalized; and revision compatibility rollout.

## 9. pgvector and Flyway readiness

- PostgreSQL 16 is proven only for local Compose (`docker-compose.yml:2-4`). Production server version is `UNKNOWN`; configuration shows a PostgreSQL URL but no server introspection was performed.
- Repository-wide search found no `pgvector`, `vector(...)`, cosine operator, HNSW/IVFFlat, or vector Java dependency. Extension availability is `UNKNOWN`.
- Spring Boot 3.3.5/JPA is present, but the effective Hibernate patch/version and native vector mapping support were not resolved without a build. The lowest-friction repository fit is native SQL/JDBC binding with an explicitly reviewed pgvector JDBC strategy, not an assumed JPA vector annotation.
- Flyway has 243 migration files. Latest occupied names are `V20260803_1__equipment_lifecycle_export_audit_module.sql` and `V20260803_2__equipment_fleet_lifecycle_read_indexes.sql`. A future migration must re-list immediately before naming; do not reserve a version from this audit.
- The scan reports both `B20260523_7__schema_baseline.sql` and `V20260523_7__ppr_plan_date_range_and_optional_task_equipment.sql`. This is an existing baseline/version overlap, not a new change; Flyway currently operates with it. There were no duplicate `V` names discovered by the filename scan.
- App migrations should not assume permission to `CREATE EXTENSION`. DevOps must preinstall/enable pgvector (or explicitly grant and approve extension creation) before an embedding-table migration requiring `vector(768)` runs. A migration that blindly creates/uses an unavailable extension can stop deployment.

`vector(768)` is feasible only after confirming pgvector availability on every environment and backup/replica compatibility. Start with exact cosine `<=>`, because data volume and latency are `UNKNOWN`. Measure action/link counts and p95 query time. Consider HNSW only after an agreed threshold from load tests (not an arbitrary row count); use `vector_cosine_ops`, document approximation, filter eligibility/auth correctly, and compare recall@10 against exact results before enabling it by default.

## 10. Exact top-10 unique-template search semantics

Repository-specific review SQL (future table/column names illustrative, not production code):

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
      /* REQUIRED: authorization predicate must be applied here if scope metadata is added */
),
action_scores AS (
    SELECT e.maintenance_action_id,
           1.0 - (e.embedding <=> CAST(:query_vector AS vector)) AS similarity_score
    FROM maintenance_action_embeddings e
    WHERE e.status = 'READY'
      AND e.model_name = :model_name
      AND e.model_revision = :model_revision
      AND e.dimension = 768
      AND e.source_schema_version = :source_schema_version
      AND e.is_current = true
),
template_scores AS (
    SELECT l.template_id,
           MAX(s.similarity_score) AS similarity_score,
           (array_agg(s.maintenance_action_id
              ORDER BY s.similarity_score DESC, s.maintenance_action_id ASC))[1] AS best_action_id
    FROM eligible_links l
    JOIN action_scores s ON s.maintenance_action_id = l.action_id
    GROUP BY l.template_id
)
SELECT template_id AS maintenance_template_id, similarity_score
FROM template_scores
ORDER BY similarity_score DESC, template_id ASC
LIMIT 10;
```

Properties proven by this strategy:

- grouping and `MAX` happen before `LIMIT 10`;
- duplicate operations linking the same Action/Template are neutralized by `DISTINCT`;
- pending/processing/failed/stale and incompatible revision/schema rows are excluded;
- deleted/inactive actions/templates and deleted operations are excluded before scoring;
- templates without eligible linked actions disappear naturally;
- no arbitrary threshold is applied;
- similarity is `1 - cosineDistance`;
- UUID ascending provides a deterministic score tie-breaker;
- empty results map to HTTP 200 with `count: 0, items: []`;
- `best_action_id` should be retained internally for diagnostics/calibration but not returned by the locked minimal API.

Authorization must filter links before their scores contribute. With the current schema there is no valid department predicate: template `equipment_type_id(s)` describes compatibility, not ownership; it must not be misused as department scope.

## 11. API, authentication, authorization, and scope

The future path `/api/v1/ai/maintenance-templates/semantic-search` fits the sole current AI namespace convention, `/api/v1/ai/equipment-lifecycle/dataset-exports` (`EquipmentLifecycleDatasetExportController.java:36-40`). Use POST because plain query text is request content and must not appear in URL/access logs.

- Authentication is stateless JWT for every non-public path (`SecurityConfig.java:52-68`). No special AI service-account type/flow was found; whether a service account receives JWTs is `UNKNOWN`.
- Existing AI export uses class-level `@PreAuthorize` with SYSTEM_ADMIN, wildcard, or dedicated permission (`EquipmentLifecycleDatasetExportController.java:36-40`). Recommend `MAINTENANCE_TEMPLATE_SEMANTIC_SEARCH` as a new dedicated permission following this convention. Backfill should use a separate stronger `MAINTENANCE_ACTION_EMBEDDING_BACKFILL` permission or operations-only control.
- Existing Maintenance Template endpoints reuse `MAINTENANCE_REGULATION_*` permissions (`MaintenanceTemplateController.java:35-101`). Maintenance Action endpoints have no method permission at all; they rely only on global authentication (`MaintenanceActionController.java:30-82`). This is insufficient precedent for the new AI endpoint.
- Neither Action nor Template contains tenant/department ownership. Existing `ScopeAccessService` can evaluate explicit departments/equipment (`lines 43-49,84-119`), but cannot scope these dictionaries. Therefore recommend **deny-by-default dedicated global permission** for v1. If business requires department-scoped results, implementation is blocked until ownership/visibility is defined and stored. Cross-department AI search is a required business decision.
- Request: `{ "query": string }`, required/not blank, configurable maximum characters and UTF-8 bytes. A concrete max is a W0/model-contract decision; do not guess it.
- Response remains `{count, items[{maintenanceTemplateId, similarityScore}]}`; do not expose query vector, action text, best action ID, credentials, stack traces, or deleted IDs.
- Current global exception handling supports controlled `RestException` status responses and hides unexpected errors (`GlobalExceptionHandler.java:41-47,158-163`). Add a stable 503 error code for model unavailability/timeout.
- No application rate limiter/circuit breaker was found. Add per-principal rate control and bounded client concurrency before exposing synchronous inference.
- Use OpenAPI annotations consistent with existing controllers (`@Tag`, validation); never log body/query text at INFO. Audit metadata should record actor, outcome, count, latency, revision and correlation ID—not raw query by default.

## 12. Backfill and version transitions

Backfill must be an explicit protected command/API, never Flyway or startup. It should keyset-scan live Actions (`id > :cursor ORDER BY id LIMIT :batch`), normalize/hash current text, and insert missing immutable job rows with `ON CONFLICT DO NOTHING`. Multiple instances claim rows with short `FOR UPDATE SKIP LOCKED` transactions and perform HTTP outside the claim transaction.

State/version behavior:

| Event | Required behavior |
|---|---|
| Model revision changes | New jobs for all eligible Actions; old READY rows coexist but query uses exactly the configured revision. Promote configuration only after coverage/readiness criteria. |
| Dimension changes | New schema/migration or rigorously versioned column strategy; never mix dimensions/operators. Treat as incompatible deployment. |
| Source schema changes | New `source_schema_version`; recalculate text/hash and backfill. Old rows remain excluded from current search. |
| Action changes during backfill | Job stores exact normalized source/hash. Before READY commit, compare current hash or rely on current-version selection; obsolete work becomes STALE and cannot win. |
| Link/template deleted during work | Vector may finish, but search-time live joins exclude the link/template. Do not couple vector generation to template membership. |
| Partial failure/retry | Atomically update attempts, sanitized code/message and `next_attempt_at`; bounded exponential backoff with jitter; terminal FAILED after configured attempts; manual retry is idempotent. |
| Old/new coexist | Unique immutable representations coexist; only configured model+revision+schema and current hash are searchable. |

Observe pending age, claim duration, success/failure/retry counts, terminal failures, READY coverage, stale count, model latency, query latency, and result counts. Apply retention to stale/failed versions so growth is bounded.

## 13. Audit-log regression analysis

Current `AuditModule` contains `EQUIPMENT_LIFECYCLE_DATASET_EXPORT` but no semantic-search/embedding module (`src/main/java/com/toir/enums/AuditModule.java:3-89`). The current DB constraint was rebuilt to include the export module (`V20260803_1...sql:1-31`). A future module must be added in the Java enum **and** in a new migration that reconstructs `audit_logs_module_check`; migration contract tests must assert both remain aligned. If a new audit action such as `SEARCH` is introduced, `audit_logs_action_check` also needs expansion; today actions are limited to LOGIN/CREATE/UPDATE/APPROVE/CLOSE/CANCEL/EXPORT/DELETE (`B20260523_7...sql:132`). Prefer an explicit reviewed action instead of misclassifying search as EXPORT.

For audit calls made inside an active transaction, `AuditLogWriteScheduler` registers `afterCommit`, catches `RuntimeException`, and only logs it (`lines 16-30`). `AuditLogWriter` writes in `REQUIRES_NEW` (`lines 19-39`). Therefore an after-commit audit constraint failure **cannot currently propagate into the already completed HTTP business method through that callback**. The previous misleading-500 path is fixed for this scheduled path.

But `schedule` calls `writer.persist` directly when no transaction/synchronization is active (`AuditLogWriteScheduler.java:31-34`); failures then propagate. Semantic search is primarily read/inference and may run without a transaction, so naïvely auditing it through this method could still turn a successful search computation into HTTP 500. Resolution: register the module/constraint before use and define best-effort/non-propagating audit behavior for read/search requests (or explicitly wrap that call), with tests for both transactional and non-transactional paths.

Maintenance Action create/update/delete currently do not call `AuditBuilderService` at all (`MaintenanceActionService.java:20-99`), unlike Maintenance Template create/update/delete (`MaintenanceTemplateService.java:87-138`). Future lifecycle audit scope must be decided; high-volume worker attempt logs should be metrics/structured operational events, not one audit row per poll.

## 14. Findings by severity

### P0 — 0

No proven flaw makes the approved design fundamentally impossible.

### P1 — 4

1. **Catalog Action vs operation snapshot semantic ambiguity.** Evidence: operation owns independent text and request-first fallback (`MaintenanceOperation.java:29-68`; `MaintenanceTemplateService.java:209-232`). Impact: a template may rank from generic/outdated catalog Action text while its actual operation wording differs, and operation-only templates never match. Resolution: business owner explicitly chooses catalog-action Option A or revises corpus to operation/link embeddings. **Blocks implementation: yes (W0 correctness decision).**
2. **Department/tenant scope is not representable.** Evidence: Action/Template entities have no ownership department; `ScopeAccessService` requires department/equipment identifiers (`ScopeAccessService.java:84-119`). Impact: accidental cross-department exposure if a caller is merely authenticated. Resolution: approve dedicated global permission for v1 or add a proven visibility relation. **Blocks scoped implementation: yes.**
3. **External model-service contract is absent.** Evidence: no embedding DTO/client/config; exact digest, modes, auth and shape are unknown. Impact: incompatible query/document vectors, credential leakage, or unstable rollout. Resolution: signed/versioned AI-team contract and fixtures. **Blocks implementation: yes.**
4. **pgvector infrastructure is absent/unknown.** Evidence: no vector repository usage/dependency/migration; Compose uses plain `postgres:16-alpine`. Impact: migration/startup failure and no vector persistence/query. Resolution: DevOps confirms/install/enables pgvector in every environment before schema rollout. **Blocks deployment: yes; code design may proceed behind disabled flag.**

### P2 — 9

1. **No durable embedding lifecycle.** No job/status/backfill exists. Impact: lost or duplicate embeddings. Add versioned job table, unique key, retry/claim state machine. **Blocks implementation: yes, addressed by W1-W4.**
2. **Action endpoints lack dedicated RBAC.** Evidence: no `@PreAuthorize` in `MaintenanceActionController.java:30-82`. Impact: any authenticated principal can mutate the future corpus. Add Action permissions or explicitly secure mutation hooks. **Blocks secure rollout: yes.**
3. **Runtime relationship/data quality is unknown.** No database reads were allowed. Impact: coverage/latency and operation-only gap cannot be quantified. Run supplied read-only SQL in an approved environment. **Blocks capacity plan, not code skeleton.**
4. **Unbounded text columns.** Four resource/note request fields have no `@Size` (`MaintenanceActionRequest.java:17-20`). Impact: oversized model requests/cost/latency. Add validated per-field and composed UTF-8 byte/token limits aligned with model contract. **Blocks safe exposure: yes.**
5. **No rate limiter/circuit breaker/bulkhead.** Repository search found none. Impact: synchronous search can overload model service/backend. Add per-principal limits, bounded concurrency and failure policy. **Blocks production rollout: yes.**
6. **Non-transactional audit failures can propagate.** Evidence: direct persistence branch (`AuditLogWriteScheduler.java:31-34`). Impact: computed search could return misleading 500. Use registered module plus non-propagating search audit path. **Blocks rollout: yes.**
7. **Template/action activation and link changes need query-time correctness.** Evidence: mutable action link and independent active/delete flags. Impact: stale IDs/scores if eligibility is materialized only at embedding time. Always join/filter current domain state. **Blocks incorrect query designs.**
8. **Automatic Flyway repair is enabled.** Evidence: `FlywayConfig.java:11-17`. Impact: schema checksum drift can be masked during a high-risk extension migration. Independently review deployment governance; do not rely on repair for vector rollout. **Does not uniquely block this feature.**
9. **Secrets/config redaction posture is weak.** Evidence: sensitive settings are present directly in application YAML. Impact: adding another credential similarly could leak through source/config/errors. New credential must be external secret only; rotate existing exposed secrets under a separate security task. **Blocks adding credentials in files.**

### P3 — 3

1. **Exact-search performance threshold unknown.** Start exact and benchmark; HNSW only with recall@10 evidence. Not a blocker.
2. **Best matching Action diagnostics are absent from locked response.** Retain internally for support/calibration without exposing it. Not a blocker.
3. **No explicit `SEARCH` audit action.** Add only if audit taxonomy owners approve and update DB constraint; otherwise define a semantically accurate existing policy. Not a search-correctness blocker.

## 15. Final verdicts

| Area | Verdict | Basis |
|---|---|---|
| Canonical Maintenance Action entity | `PROVEN` | `MaintenanceAction` / `maintenance_actions` |
| Canonical Action -> Template relationship | `PROVEN` | Nullable `maintenance_operations.action_id`, mandatory `template_id`; effective many-to-many |
| Source-text readiness | `PARTIAL` | Proven fields; corpus meaning and max lengths need decisions |
| Lifecycle-hook readiness | `PARTIAL` | Central create/update/delete service exists; no hook/job and relation/state paths must be included |
| Async worker/outbox readiness | `PARTIAL` | Patterns exist; embedding-specific durable state/retry absent |
| pgvector readiness | `MISSING` | No extension evidence, usage, dependency, mapping or schema |
| Flyway migration readiness | `PARTIAL` | Tooling/config proven; extension privilege and final version require checks |
| Search-query feasibility | `PROVEN` | Real join path supports pre-limit grouping and exact cosine SQL |
| Unique-template grouping correctness | `PROVEN` | `GROUP BY template_id` + `MAX` before `LIMIT` is implementable |
| Authorization readiness | `MISSING` | Dedicated permission absent; department scope cannot be represented |
| Audit-log readiness | `PARTIAL` | Enum/constraint pattern and safe after-commit path exist; non-tx propagation risk remains |
| Legacy backfill readiness | `MISSING` | No mechanism/data measurements |
| External embedding-service contract | `MISSING` | Exact revision/modes/auth/shape/SLA unknown |
| Overall implementation readiness | `PARTIAL` | `READY_WITH_EXPLICIT_DECISIONS` |

## 16. Future work packages

| WP | Likely repository files | Dependencies | Acceptance criteria | Rollback concern / unresolved decision |
|---|---|---|---|---|
| W0 — contracts/decisions | New design/contract docs only | Business, AI, Security, DevOps | Corpus chosen; global/scope rule; model digest/modes/DTO/SLA/max query; audit taxonomy agreed | Do not code past unresolved catalog-vs-operation or scope decision |
| W1 — vector/schema foundation | New Flyway migrations; new embedding entity/repository/status enum; `pom.xml` only if approved driver needed | pgvector enabled; migration version re-scan | `vector(768)`, immutable metadata/status/unique key, indexes, enum/check constraints, permission/audit constraint compatibility | Feature disabled rollback; schema must tolerate old/new revisions; extension failure must not surprise prod |
| W2 — text/lifecycle | New `MaintenanceActionEmbeddingTextBuilder`; `MaintenanceActionService`; possibly domain listener coverage tests | W1, W0 corpus | deterministic v1 text/hash; create job same tx; unchanged no duplicate; changed stale/new; delete/active rules | Action save must remain successful without inference; decide Action audit/RBAC |
| W3 — client/worker | New AI config/properties/client DTOs, worker, claim/retry services/repositories; `application.yml` env placeholders | W0/W1 | external process only; exact 768 finite values; SKIP LOCKED/fencing; bounded backoff/jitter; sanitized diagnostics; multi-instance safety | Kill switch; credential rotation; model partial outage behavior |
| W4 — backfill | New protected operational controller/service or admin command, repository queries, observability | W2/W3 | explicit, bounded, resumable, idempotent, multi-instance safe; coverage/status | Cancel/pause and stale-job retention; actual batch size after measurements |
| W5 — semantic API | New `/controller/ai`, request/response DTO, search service/repository native SQL, OpenAPI | W0-W4 | plain query only; controlled 503; exact revision; eligible join; MAX/group then limit; stable tie; max 10; no vectors | Feature flag rollback; decide best-action diagnostics and timeout |
| W6 — auth/audit/ops | `PermissionConstants`, role defaults/migration, `AuditModule`, audit constraint migration, metrics/log config, possibly rate limiter | W0/W5 | dedicated search/backfill permissions; approved global/scope rule; no raw query/secret logs; audit failures cannot alter response; dashboards/alerts | Permission grant rollout; audit volume/taxonomy; department schema if required |
| W7 — tests/deploy | Existing/new JUnit tests, migration contract tests, Testcontainers pgvector image/config, deployment docs/compose | All prior | full matrix passes in CI; exact-vs-approx evidence if HNSW; staged backfill/model rollout/rollback rehearsed | Tests were not run in this audit; production rollout gated on DevOps validation |

## 17. Exact unresolved decisions

1. Does “Maintenance Action” mean global `maintenance_actions` text, or the template-specific `maintenance_operations` snapshot/override?
2. Should operation-only templates be intentionally absent from results?
3. Is v1 search globally authorized by a dedicated permission, or must it be department-scoped? If scoped, what is the canonical template/action ownership relation?
4. Can the AI client use normal JWT authentication, and which role/service account receives the permission?
5. Exact immutable Granite revision/container digest and whether document/query modes/prefixes differ.
6. Embedding-service endpoint, request/response/batch format, auth mechanism, SLA, maximum bytes/tokens, and error taxonomy.
7. Approved query maximum length, rate limit, concurrency, timeout, retry counts/backoff, and data retention.
8. Whether search attempts require a new `SEARCH` audit action, and whether raw query retention is prohibited (recommended: prohibit by default).
9. pgvector version, installation owner, extension privilege, production PostgreSQL version, replica/backup support, and approved JDBC mapping.
10. Actual data counts/quality and exact-search performance target.

## 18. Implementation change map

No code was changed. Future implementation would likely touch:

- existing: `MaintenanceActionService`, `MaintenanceActionController` (RBAC only if approved), `PermissionConstants`, `RolePermissionDefaults`, `AuditModule`, `application.yml`, and migration/test contracts;
- new: semantic-search controller/request/response, text builder, embedding config/client, job/entity/repositories, worker/claim/retry/backfill/search services, vector migration(s), and tests;
- read-only domain joins: `maintenance_actions`, `maintenance_operations`, `maintenance_templates`;
- explicitly untouched: frontend, ERP/ATIL outboxes, Kafka, equipment lifecycle ingestion/export contracts.

## 19. Future test plan

Repository uses JUnit 5, Mockito, Spring MockMvc, AssertJ via Spring Boot test, and PostgreSQL Testcontainers (`pom.xml:124-160`). Existing action HTTP contracts are in `src/test/java/com/toir/controller/MaintenanceActionControllerContractTest.java:24-110`; migration contracts exist under `src/test/java/com/toir/migration`; AI export security tests provide permission patterns.

| Test area | Proposed repository-specific class/type |
|---|---|
| Text normalization/hash/vector validation | `MaintenanceActionEmbeddingTextBuilderTest`, `EmbeddingVectorValidatorTest` (plain JUnit/AssertJ) |
| Create/update/delete transactional enqueue | `MaintenanceActionEmbeddingLifecycleServiceTest` plus PostgreSQL integration test; verify exactly one, unchanged hash no duplicate, changed text new version, active/delete eligibility, model never called in create tx |
| Link mutation | `MaintenanceTemplateSemanticMembershipTest`; replace/remove `actionId`, template delete/deactivate, action linked to multiple templates, operation-only link |
| Worker/retry/concurrency | `MaintenanceActionEmbeddingWorkerTest` (Mockito client) and pgvector/Testcontainers claim integration test; SKIP LOCKED, retry exhaustion, partial failure, stale race, multiple instances |
| Invalid vectors | client/validator tests for 0/767/769 values, nulls, NaN and infinities |
| SQL semantics | PostgreSQL pgvector repository integration test: MAX action score, one row/template, grouping before limit, exactly 10 distinct templates when >=10, fewer/empty, deterministic UUID tie, incompatible revision/status exclusion |
| Authorization | MockMvc/Spring Security tests patterned after `RbacEquipmentLifecycleDatasetExportSecurityTest`; unauthenticated 401, missing permission 403, wildcard/admin, approved department/global behavior |
| API/client | MockMvc contract for request max/blank, response shape/no vectors, timeout/unavailable controlled 503, Uzbek/Russian/English fixtures |
| Backfill | service/integration tests for batching, resume cursor, conflict idempotency, action changes during batch, old/new coexistence |
| Migration/audit | migration text/real PostgreSQL tests modeled on `EquipmentLifecycleDatasetExportAuditMigrationTest`; enum/check alignment, pgvector prerequisite behavior, no collision, committed operation not changed to 500 by audit failure, non-tx search audit failure contained |

Required explicit cases: create exactly one durable job; unchanged/changed text; changed link; model failure not rollback; concurrent claim; retry exhaustion; invalid length/non-finite; deleted action/template and unauthorized exclusion; max-per-template; group-before-limit; 10/fewer/tie; multilingual queries; empty; 503; revision mismatch; backfill idempotency; audit compatibility. **No tests were written or run in this audit.**

## 20. DevOps prerequisites and verification status

DevOps prerequisites:

1. Confirm production PostgreSQL version and install/enable a compatible pgvector extension on dev/test/stage/prod, replicas and restore environments before the schema migration.
2. Provide a pgvector-enabled Testcontainers image for CI; plain `postgres:16-alpine` does not prove extension availability.
3. Deploy a separately managed Granite embedding container pinned to exact immutable revision/digest, with health/readiness, CPU/GPU/RAM sizing, restart policy and network isolation.
4. Supply base URL and internal secret/mTLS through environment/secret manager; never source-control them. Rotate currently source-exposed credentials in a separate security operation.
5. Define model SLOs, backend connect/read timeouts, concurrency/rate limits, retry/backoff, alerts and dashboards.
6. Run the read-only measurement SQL, benchmark exact cosine p50/p95/p99 at projected scale, and establish rollout/backfill windows.
7. Stage rollout: schema -> disabled backend -> model service -> limited backfill -> coverage check -> search canary -> broader permission grant. Keep a feature kill switch and compatible old revision until rollback window closes.

Verification:

- Repository/source/static inspection: completed.
- Production database queries/writes: **NOT RUN — skipped by user instruction**.
- Maven/Gradle/build/tests/Testcontainers: **NOT RUN — skipped by user instruction**.
- Granite download/inference: **NOT RUN — skipped by user instruction**.
- Branch/ref/commit/push operations: none.
- Production/test/config/dependency/migration code modifications: none.
- Only permitted write: this audit report.
