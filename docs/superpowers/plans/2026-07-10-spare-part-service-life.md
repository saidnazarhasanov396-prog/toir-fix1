# Spare-Part Service-Life Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Subagent dispatch is not authorized for this task.

**Goal:** Add an installation-centric spare-part service-life capability with trustworthy meter baselines, atomic/idempotent lifecycle commands, due/readiness projections, granular RBAC, and minimum production frontend workflows while preserving BOM and parent asset lifetime semantics.

**Architecture:** Keep `EquipmentSparePart` unchanged as BOM data. Add independent rule, limit, installation, baseline, material-allocation, command, and due-event aggregates under a focused spare-part-lifecycle package. Route install/remove/replace through one transactional service; use `EquipmentMeter` as the only usage authority; expose derived readiness without modifying `Equipment.status` or parent lifetime fields.

**Tech Stack:** Java 21 target (JDK 24 compiler), Spring Boot 3.3.5, Spring Data JPA, PostgreSQL, Flyway, JUnit 5/Mockito/Testcontainers, React 19, TypeScript 6, Vite 8, TanStack Query, React Hook Form/Zod, Yarn 4.

## Global Constraints

- Preserve all pre-existing backend and frontend changes; no reset, clean, broad checkout, commit, push, or force operation.
- `equipment_spare_parts` remains BOM/applicability data and its APIs/UI stay backward compatible.
- All installations reference `equipment_id`; no asset polymorphism and no separate Vehicle aggregate.
- Part life starts only from an explicit installation and never writes Equipment/Vehicle lifetime fields.
- `EquipmentMeter` is authoritative; missing or ambiguous meters are typed errors.
- New auditable resource values use `BigDecimal`/PostgreSQL `numeric`, not `double`.
- One active installation per `(equipment_id, position_key)` is enforced by a PostgreSQL partial unique index and application locking.
- Issued material is never implicitly installed; work-order completion requires explicit lifecycle operations.
- Refurbishment, cross-asset cumulative usage, automatic historical backfill, and generic maintenance rewrites remain out of scope.
- New remote notifications/webhooks are published after commit.
- Existing clients omitting lifecycle operations retain current behavior.

---

## Planned file structure

Backend additions live under:

- `src/main/java/com/toir/entity/sparepartlifecycle/` — rule, limit, installation, baseline, allocation, command, due-event entities.
- `src/main/java/com/toir/enums/sparepartlifecycle/` — typed lifecycle enums.
- `src/main/java/com/toir/repository/sparepartlifecycle/` — scoped queries, locks, and event lookup.
- `src/main/java/com/toir/service/sparepartlifecycle/` — normalization, meter resolution, rule resolution, pure evaluation, event upsert, lifecycle orchestration, readiness and next-action services.
- `src/main/java/com/toir/dto/sparepartlifecycle/` — additive API commands/responses.
- `src/main/java/com/toir/controller/sparepartlifecycle/` — rule, installation, due/readiness endpoints.
- `src/test/java/com/toir/.../sparepartlifecycle/` — unit, security, migration and PostgreSQL concurrency coverage.

Frontend additions live under:

- `src/modules/equipment/libs/spare-part-lifecycle/` — types, normalization and view models.
- `src/modules/equipment/components/equipment-detail/maintenance/` — current installations, history, due/readiness and action dialogs.
- existing API/types/query files receive additive methods and types.

### Task 1: Canonical meter resolution and reading safety

**Files:**
- Modify: `src/main/java/com/toir/entity/equipment/EquipmentMeter.java`
- Modify: `src/main/java/com/toir/repository/equipment/EquipmentMeterRepository.java`
- Create: `src/main/java/com/toir/service/sparepartlifecycle/CanonicalEquipmentMeterService.java`
- Modify: `src/main/java/com/toir/service/MeterService.java`
- Modify: `src/main/java/com/toir/service/VehicleService.java`
- Test: `src/test/java/com/toir/service/sparepartlifecycle/CanonicalEquipmentMeterServiceTest.java`
- Test: `src/test/java/com/toir/service/MeterServiceLifecycleSafetyTest.java`
- Migration: `src/main/resources/db/migration/V20260710_1__canonical_equipment_meter.sql`

**Interfaces:**
- Produces `CanonicalEquipmentMeterService.resolve(UUID equipmentId, MeterType type, UUID explicitMeterId)` returning an active `EquipmentMeter` or typed `RestException` codes `METER_REQUIRED`, `METER_AMBIGUOUS`, `METER_INACTIVE`, or `METER_MISMATCH`.
- Produces `CanonicalEquipmentMeterService.currentValue(...)` as `BigDecimal`.

- [ ] Write resolver tests for explicit ownership/type/active validation, zero/one/multiple matches, and a unique active primary meter.
- [ ] Run the tests and confirm they fail because the resolver and `isPrimary` field do not exist.
- [ ] Add nullable-safe `is_primary boolean NOT NULL DEFAULT false`, an active-primary partial unique index, repository lock/query methods, and the minimal resolver implementation.
- [ ] Run resolver tests and confirm they pass.
- [ ] Write failing MeterService tests for old timestamp rejection and current-reading deletion recomputing current value plus VehicleDetails projection.
- [ ] Implement latest-reading lookup/rebuild, preserving rollover logic; stop direct Vehicle update from independently changing odometer/hour projections except through meter reading flow.
- [ ] Run meter and vehicle focused suites.

### Task 2: Additive lifecycle schema and entities

**Files:**
- Create: `src/main/resources/db/migration/V20260710_2__spare_part_service_life_foundation.sql`
- Create: entities/enums/repositories under `sparepartlifecycle` packages.
- Test: `src/test/java/com/toir/migration/SparePartServiceLifeMigrationContractTest.java`
- Test: `src/test/java/com/toir/migration/SparePartServiceLifePostgresTest.java`

**Interfaces:**
- Produces entities `SparePartLifeRule`, `SparePartLifeLimit`, `SparePartInstallation`, `SparePartInstallationMeterBaseline`, `SparePartInstallationMaterialAllocation`, `SparePartLifecycleCommand`, and `SparePartDueEvent`.
- Produces typed enums for rule scope, combination, limit kind/unit, due action, installation status/evaluation, disposition, command type/status, due state and readiness.

- [ ] Write migration contract tests asserting additive tables, numeric precision, FKs, checks, partial unique active-position index, idempotency uniqueness, allocation uniqueness, due cycle uniqueness, indexes, and no alteration of `equipment_spare_parts`.
- [ ] Run contract tests and confirm failure because the migration is absent.
- [ ] Add the migration with no historical installation rows and no automatic primary-meter backfill.
- [ ] Add JPA entities with `@Version`, enum strings, JSONB snapshots and immutable lifecycle identifiers.
- [ ] Run schema contract and JPA compile tests.
- [ ] Run PostgreSQL migration validation when Docker/Testcontainers is available.

### Task 3: Rule CRUD, validation and deterministic resolution

**Files:**
- Create: `SparePartLifeRuleService`, `SparePartLifeRuleResolver`, DTOs, mapper and repository queries.
- Create: `SparePartLifeRuleController`.
- Test: rule service/resolver/controller tests.

**Interfaces:**
- Produces `resolve(sparePartId, equipmentId, nodeId, normalizedSlot, Instant at)` returning zero or exactly one effective revision.
- Produces immutable `AppliedLifeRuleSnapshot` JSON including ordered limits.

- [ ] Write failing tests for catalog default, equipment, node and node+slot precedence; effective boundaries; ambiguity; overlap rejection; calendar/meter validation; warning bounds; revision immutability; and snapshot stability.
- [ ] Implement slot normalization and scope derivation centrally.
- [ ] Implement create/revise/deactivate and deterministic resolver without newest-row fallback.
- [ ] Add paginated CRUD/effective-rule endpoints and validation/error mapping.
- [ ] Run rule unit/controller tests.

### Task 4: Pure installation lifecycle evaluator and due events

**Files:**
- Create: `SparePartLifecycleEvaluator.java`
- Create: `SparePartDueEventService.java`
- Create: evaluation DTO/value objects.
- Test: evaluator and event-service tests.

**Interfaces:**
- Consumes installation snapshot, structured baselines, current canonical meter values and evaluation time.
- Produces `SparePartLifecycleEvaluation` with aggregate state, per-limit consumption/remaining values, next calendar due and structured reasons.

- [ ] Write failing pure tests for calendar arithmetic, exact warning/due thresholds, meter baseline consumption, rollover/negative delta, missing/inactive meter, `ANY`, `ALL`, and `MANUAL`.
- [ ] Implement the pure evaluator using installation snapshots and `BigDecimal`.
- [ ] Write failing event tests for stable cycle key, repeated upsert, unique-race reload and monotonic resolution.
- [ ] Implement due-event upsert/resolution and after-commit application events.
- [ ] Run evaluator/event tests.

### Task 5: Idempotent transactional install/remove/replace

**Files:**
- Create: lifecycle command DTOs/result DTOs and `SparePartLifecycleService`.
- Add repository pessimistic-lock queries for Equipment, installation, material usage and command.
- Test: service unit tests and PostgreSQL concurrency integration tests.

**Interfaces:**
- Produces `install`, `remove`, and `replace` methods accepting equipment/installation IDs, `Idempotency-Key`, actor and canonical command DTOs.
- Returns stable lifecycle results on same-key/same-request retry; different request conflicts.

- [ ] Write failing tests for slot normalization, node ownership, occupied position, serial quantity/reuse, BOM compatibility/ad-hoc authorization, rule snapshot, meter baselines, parent-lifetime non-mutation, removal disposition and replacement bidirectional links.
- [ ] Implement request canonicalization/hash and command acquisition/reload semantics.
- [ ] Implement install transaction and verify no installation is inferred from BOM/material issue.
- [ ] Implement remove transaction and due-event resolution without stock return.
- [ ] Implement replace transaction with locks, old/new correlation, allocation and event transition.
- [ ] Write PostgreSQL concurrent install/replacement and idempotency tests; map losing races to 409 domain conflicts.
- [ ] Run lifecycle and concurrency suites.

### Task 6: Work-order and WMS/material reconciliation

**Files:**
- Modify: `src/main/java/com/toir/dto/workorder/CompleteWorkOrderRequest.java`
- Modify: `src/main/java/com/toir/service/WorkOrderService.java`
- Modify: `src/main/java/com/toir/repository/repair/RepairMaterialUsageRepository.java`
- Create: lifecycle-operation DTOs.
- Test: `WorkOrderServiceTest` and lifecycle material integration tests.

**Interfaces:**
- Adds optional `sparePartLifecycleOperations` to work-order completion.
- Lifecycle operations reference exact existing material usage or stable completion material line key.

- [ ] Write failing regression test proving omitted operations preserve current completion behavior and issued material alone creates no installation.
- [ ] Write failing tests for explicit install/remove/replace, exact material ownership/part/quantity/serial/lot validation, already allocated usage and rollback.
- [ ] Extend completion additively and invoke the same lifecycle service inside its existing transaction.
- [ ] Integrate `RETURN_TO_STOCK` only through an existing safe transactional return path; otherwise return a typed unsupported-disposition error.
- [ ] Run work-order, material, reservation and lifecycle tests.

### Task 7: Readiness and next required actions

**Files:**
- Create: `SparePartOperationalReadinessService.java`
- Create: `EquipmentNextRequiredActionService.java`
- Create: response DTOs.
- Test: readiness/aggregation tests.

**Interfaces:**
- Produces derived readiness without writing `Equipment.status`.
- Produces separate parent-life, maintenance and part candidates plus deterministic primary action.

- [ ] Write failing tests for evaluation error, blocker, maintenance required, warning, ready, multiple-part precedence and protected status non-mutation.
- [ ] Implement readiness aggregation from active installations/events.
- [ ] Write failing next-action tests proving incompatible units are not converted into one scalar.
- [ ] Implement candidate ordering: blocked/overdue, due, warning, earliest calendar, lowest remaining ratio.
- [ ] Run readiness and status regression tests.

### Task 8: API, RBAC, auditing, scheduling and diagnostics

**Files:**
- Modify: `PermissionConstants.java`, `RolePermissionDefaults.java` and frontend permission constants.
- Create: rule/installation/due/readiness controllers.
- Modify/create Flyway permission seed migration.
- Add scheduled lifecycle scan and meter-reading trigger adapter.
- Add diagnostic SQL/documentation.
- Test: controller contract and negative security tests.

- [ ] Write failing negative authorization tests for every new write path and read-filter tests.
- [ ] Add permission constants/role seeds and explicit `@PreAuthorize` expressions.
- [ ] Add paginated rule, installation and due endpoints, acknowledgement, reevaluation, readiness and next-action endpoints.
- [ ] Add audit events with equipment/slot/installations/work-order/material/idempotency correlation.
- [ ] Trigger re-evaluation after relevant readings, install/replace and nightly calendar scan.
- [ ] Add read-only diagnostics for meter ambiguity, vehicle projection divergence, BOM anomalies, missing ledger linkage and serial reuse indicators.
- [ ] Run security/controller/scheduler tests.

### Task 9: Minimum frontend lifecycle operations

**Files:**
- Modify: `src/lib/api.ts`, `src/types/api.ts`, `src/lib/permissions.ts`.
- Modify: equipment detail queries and maintenance tab.
- Create: current-installations, history, due/readiness and lifecycle action components/dialogs.
- Modify: work-order completion dialog for explicit operations.
- Add locale strings to `uz`, `ru`, and `en` JSON.
- Test: focused view-model/component/RBAC tests.

- [ ] Write failing tests for current/history/due/readiness rendering, separate parent life, permission-gated actions, explicit work-order operations, API validation errors and BOM regression.
- [ ] Add typed API methods/query keys and normalized view models.
- [ ] Add current installations, history, due/readiness and next-action sections while leaving the BOM table unchanged.
- [ ] Add install/remove/replace dialogs with idempotency keys and exact material selection.
- [ ] Extend work-order completion with explicit lifecycle operations; never infer from material lines.
- [ ] Add all three locale variants and loading/empty/error/permission states.
- [ ] Run focused tests, i18n check, type-check and production build.

### Task 10: Documentation and final verification

**Files:**
- Create: `docs/spare-part-service-life.md`
- Create: `docs/sql/spare-part-service-life-diagnostics.sql` if repository convention permits.

- [ ] Document domain separation, rule precedence, meter authority, lifecycle transactions/idempotency, readiness vs Equipment status, APIs, diagnostics, migration/backfill policy and deferred scope.
- [ ] Run focused backend tests and extract exact pass/fail/skip totals.
- [ ] Run PostgreSQL/Testcontainers migration and concurrency tests or record the precise environment blocker.
- [ ] Run the broadest practical backend suite.
- [ ] Run frontend tests, `yarn lint`, `yarn i18n:check`, and `yarn build`.
- [ ] Compare final Git status with the recorded pre-existing changes and verify they remain intact.
- [ ] Report only verified invariants and any genuine remaining blocker; do not commit or push.

## Plan self-review

- Spec coverage: phases A–K and acceptance criteria are represented by Tasks 1–10.
- Scope: physical-instance refurbishment, cumulative reuse and historical reconstruction remain excluded.
- Type consistency: all lifecycle entry points use Equipment UUID identity, normalized non-null position keys, immutable rule snapshots and `BigDecimal` resource values.
- Placeholder scan: no deferred implementation placeholders remain; conditional return-to-stock behavior is explicitly defined as integrate safely or reject.
- Execution choice: inline execution is required because subagent dispatch was not requested and implementation must continue in this session.
