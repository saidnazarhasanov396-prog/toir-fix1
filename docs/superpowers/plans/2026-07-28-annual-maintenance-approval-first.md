# Annual Maintenance Schedule Approval-First Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Persist deterministic Annual Maintenance Schedule calculation revisions, bind approval to the exact calculated scope, and atomically create `APPROVED` PPR tasks only after final approval.

**Architecture:** New maintenance calculations store immutable relational snapshot items and remain task-free in `CALCULATED` state until approval completes. Approval binding, review-amend, return-for-rework, and final materialization use one advisory-lock discipline, versioned hashes, command idempotency, and backend-derived capabilities. Existing manual and legacy-materialized PPR flows stay on their current paths.

**Tech Stack:** Java 21, Spring Boot, Spring Data JPA, PostgreSQL/Flyway, JUnit 5, Mockito, Testcontainers test sources, React, TypeScript, TanStack Query, Vitest/Testing Library.

## Global Constraints

- Implement the approved design in `docs/superpowers/specs/2026-07-28-annual-maintenance-approval-first-design.md`.
- Do not modify, rename, stage, or merge `V20260728_3__add_mixed_trigger_handling_to_ppr_plans.sql`.
- Use exact new Flyway versions `V20260728_4`, `V20260728_5`, and `V20260728_6`.
- Existing calculations are `LEGACY_MATERIALIZED`; do not delete, regenerate, rematerialize, or change existing tasks.
- New Annual Maintenance Schedule calculations are explicitly `APPROVAL_FIRST`; manual PPR plans retain the legacy-safe database default.
- `PlanStatus.CALCULATED` is new; do not change `GENERATED` semantics or backfill existing rows to `CALCULATED`.
- V1 materialization statuses are only `NOT_APPLICABLE`, `NOT_MATERIALIZED`, and `MATERIALIZED`.
- Reuse existing `ApprovalRequest.templateId/templateVersion`, `failureReason`, and `lastReturnedAt/By/Comment`; do not create duplicate template or return-resolution columns.
- `approvedRevision` is derived from `materializedRevision`; do not add a separate database field.
- The approval-first feature flag is `toir.maintenance-schedule.approval-first.enabled=false` by default.
- When the feature flag is false, do not create a new approval-first or fallback legacy Annual Maintenance Schedule calculation.
- External success notifications/events run only after transaction commit through the canonical event/outbox mechanism.
- Do not fix F-01, F-04, F-05, F-08, F-09, or F-10 in this implementation.
- Write and update tests before production code, but do not execute Maven, backend tests, Testcontainers, Vitest, npm test/build, runtime, Flyway, or database commands.
- Verification wording is exactly: `not run / skipped by user instruction`.

---

## File Structure

### Backend files to create

- `src/main/resources/db/migration/V20260728_4__maintenance_schedule_approval_first_metadata.sql` — expand/backfill/constrain plan and approval metadata.
- `src/main/resources/db/migration/V20260728_5__maintenance_schedule_calculation_snapshot_items.sql` — immutable revision items and PPR task traceability.
- `src/main/resources/db/migration/V20260728_6__maintenance_schedule_command_idempotency.sql` — command results and revision-bound active-request uniqueness.
- `src/main/java/com/toir/config/MaintenanceScheduleApprovalFirstProperties.java` — default-off server feature flag.
- `src/main/java/com/toir/enums/MaintenanceScheduleMaterializationMode.java`
- `src/main/java/com/toir/enums/TaskMaterializationStatus.java`
- `src/main/java/com/toir/enums/MaintenanceScheduleLifecycleStatus.java`
- `src/main/java/com/toir/enums/ApprovalResolutionCode.java`
- `src/main/java/com/toir/enums/MaintenanceScheduleCommandType.java`
- `src/main/java/com/toir/entity/MaintenanceScheduleCalculationItem.java`
- `src/main/java/com/toir/entity/MaintenanceScheduleCommandResult.java`
- `src/main/java/com/toir/repository/MaintenanceScheduleCalculationItemRepository.java`
- `src/main/java/com/toir/repository/MaintenanceScheduleCommandResultRepository.java`
- `src/main/java/com/toir/service/maintanance/MaintenanceScheduleSnapshotService.java`
- `src/main/java/com/toir/service/maintanance/MaintenanceScheduleContentHasher.java`
- `src/main/java/com/toir/service/maintanance/MaintenanceScheduleApprovalBindingService.java`
- `src/main/java/com/toir/service/maintanance/MaintenanceScheduleMaterializationService.java`
- `src/main/java/com/toir/service/maintanance/MaintenanceScheduleLifecycleService.java`
- `src/main/java/com/toir/service/maintanance/MaintenanceScheduleCapabilityService.java`
- `src/main/java/com/toir/service/maintanance/MaintenanceScheduleCommandIdempotencyService.java`
- `src/main/java/com/toir/service/maintanance/MaintenanceScheduleCommandRetentionService.java`
- `src/main/java/com/toir/service/maintanance/MaintenanceScheduleDomainEvent.java`
- `src/main/java/com/toir/service/maintanance/MaintenanceScheduleAfterCommitListener.java`
- focused request/response records under `src/main/java/com/toir/dto/maintenanceschedule/`.

### Backend files to modify

- `PprPlan`, `PprTask`, and `ApprovalRequest` entity mappings.
- `PlanStatus` and `ApprovalStatus`.
- `MaintenanceSchedulePreviewItem`, `MaintenanceScheduleService`, calculation services/controllers/repositories.
- `PprPlanService`, `ApprovalService`, `ApprovalRequestRepository`, `PprPlanApprovalHandler`, and approval execution contracts.
- application configuration defaults and audit/notification wiring.

### Frontend files to create

- `src/modules/repairs/libs/maintenance-schedule-builder/query-keys.ts`
- `src/modules/repairs/libs/maintenance-schedule-builder/command-idempotency.ts`
- `src/modules/repairs/libs/maintenance-schedule-builder/stale-approval.ts`
- `src/modules/repairs/components/maintenance-schedule-builder/maintenance-schedule-approval-panel.tsx`
- `src/modules/repairs/components/maintenance-schedule-builder/maintenance-schedule-review-amend-dialog.tsx`
- `src/modules/repairs/components/maintenance-schedule-builder/maintenance-schedule-return-dialog.tsx`

### Frontend files to modify

- `src/types/api.ts`, `src/lib/api.ts`, calculation lifecycle helpers/tests.
- Calculation registry, preview page, and PPR plan detail page.
- Approval history/status rendering and i18n locale resources.

---

### Task 1: Add migration contracts and additive schema

**Files:**
- Create: `src/test/java/com/toir/migration/MaintenanceScheduleApprovalFirstMigrationContractTest.java`
- Create: `src/test/java/com/toir/migration/MaintenanceScheduleApprovalFirstMigrationPostgresTest.java`
- Create: `src/main/resources/db/migration/V20260728_4__maintenance_schedule_approval_first_metadata.sql`
- Create: `src/main/resources/db/migration/V20260728_5__maintenance_schedule_calculation_snapshot_items.sql`
- Create: `src/main/resources/db/migration/V20260728_6__maintenance_schedule_command_idempotency.sql`
- Modify: `src/test/java/com/toir/migration/FlywayMigrationVersionContractTest.java`

**Interfaces:**
- Produces the columns, tables, foreign keys, checks, and indexes consumed by every later backend task.
- Does not create application-level Java mappings.

- [ ] **Step 1: Write migration source-contract tests first**

```java
@Test
void approvalFirstMigrationsAreLegacySafeAndRevisionBound() throws Exception {
    String v4 = migration("V20260728_4__maintenance_schedule_approval_first_metadata.sql");
    assertThat(v4).contains(
            "materialization_mode",
            "DEFAULT 'LEGACY_MATERIALIZED'",
            "task_materialization_status",
            "DEFAULT 'NOT_APPLICABLE'",
            "'CALCULATED'",
            "'SUPERSEDED'");
    assertThat(v4).doesNotContain("approved_revision");

    String v5 = migration("V20260728_5__maintenance_schedule_calculation_snapshot_items.sql");
    assertThat(v5).contains(
            "maintenance_schedule_calculation_items",
            "source_item_key_version",
            "UNIQUE (plan_id, calculation_revision, source_item_key)",
            "source_calculation_item_id");

    String v6 = migration("V20260728_6__maintenance_schedule_command_idempotency.sql");
    assertThat(v6).contains(
            "maintenance_schedule_command_results",
            "UNIQUE (client_command_id)",
            "UNIQUE (target_type, target_id, action_type)",
            "status = 'PENDING'",
            "calculation_revision IS NOT NULL");
}
```

- [ ] **Step 2: Write deferred PostgreSQL migration tests**

```java
@Test
void legacyRowsAndTasksRemainUnchanged() {
    UUID planId = insertLegacyGeneratedCalculationWithTask();
    migrateToLatest();

    assertThat(plan(planId).materializationMode()).isEqualTo("LEGACY_MATERIALIZED");
    assertThat(plan(planId).taskMaterializationStatus()).isEqualTo("NOT_APPLICABLE");
    assertThat(taskIds(planId)).containsExactly(originalTaskId);
}
```

The Testcontainers test source is written now but marked/documented for a later authorized run.

- [ ] **Step 3: Implement V4 with expand → backfill → constrain**

```sql
ALTER TABLE ppr_plans
    ADD COLUMN materialization_mode varchar(32),
    ADD COLUMN task_materialization_status varchar(32),
    ADD COLUMN calculation_revision bigint,
    ADD COLUMN calculation_content_hash varchar(64),
    ADD COLUMN calculation_content_hash_version integer,
    ADD COLUMN materialized_revision bigint,
    ADD COLUMN materialized_task_count integer;

UPDATE ppr_plans
SET materialization_mode = 'LEGACY_MATERIALIZED',
    task_materialization_status = 'NOT_APPLICABLE'
WHERE materialization_mode IS NULL;

ALTER TABLE ppr_plans
    ALTER COLUMN materialization_mode SET DEFAULT 'LEGACY_MATERIALIZED',
    ALTER COLUMN materialization_mode SET NOT NULL,
    ALTER COLUMN task_materialization_status SET DEFAULT 'NOT_APPLICABLE',
    ALTER COLUMN task_materialization_status SET NOT NULL;
```

V4 also extends the real plan/approval status checks, reuses existing `template_id`, `template_version`, `failure_reason`, and `last_returned_*`, and adds only:

```sql
calculation_revision,
calculation_content_hash,
calculation_content_hash_version,
resolved_route_fingerprint,
requester_context_fingerprint,
resolution_code,
superseded_by_request_id
```

- [ ] **Step 4: Implement V5 snapshot and task-traceability constraints**

```sql
CREATE TABLE maintenance_schedule_calculation_items (
    id uuid PRIMARY KEY,
    plan_id uuid NOT NULL REFERENCES ppr_plans(id) ON DELETE RESTRICT,
    calculation_revision bigint NOT NULL,
    source_item_key varchar(64) NOT NULL,
    source_item_key_version integer NOT NULL,
    equipment_id uuid NOT NULL REFERENCES equipment(id) ON DELETE RESTRICT,
    regulation_id uuid REFERENCES maintenance_regulations(id) ON DELETE RESTRICT,
    maintenance_rule_id uuid REFERENCES equipment_maintenance_rules(id) ON DELETE RESTRICT,
    template_id uuid REFERENCES maintenance_templates(id) ON DELETE RESTRICT,
    maintenance_type varchar(64) NOT NULL,
    trigger_type varchar(64) NOT NULL,
    trigger_discriminator varchar(128),
    cycle_ordinal bigint NOT NULL,
    planned_date date NOT NULL,
    scheduled_start timestamp NOT NULL,
    scheduled_end timestamp NOT NULL,
    due_date timestamp NOT NULL,
    normative_labor_hours numeric(12,2) NOT NULL,
    priority varchar(32) NOT NULL,
    department_id uuid,
    equipment_code_snapshot varchar(255) NOT NULL,
    equipment_name_snapshot varchar(255) NOT NULL,
    source_name_snapshot varchar(255) NOT NULL,
    task_title_snapshot varchar(500) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_maintenance_schedule_item_revision
        UNIQUE (plan_id, calculation_revision, source_item_key)
);
```

Add nullable `ppr_tasks.source_calculation_item_id`, a restrictive FK, and a partial unique index where it is non-null. Add a `BEFORE UPDATE OR DELETE` trigger on `maintenance_schedule_calculation_items` that raises an immutable-snapshot exception; no application path disables this trigger.

- [ ] **Step 5: Implement V6 command and active-request invariants**

```sql
CREATE UNIQUE INDEX uq_approval_first_active_request
ON approval_requests(target_type, target_id, action_type)
WHERE status = 'PENDING'
  AND calculation_revision IS NOT NULL
  AND is_deleted = false;
```

Create `maintenance_schedule_command_results` with globally unique `client_command_id`, versioned request fingerprint, actor/calculation identity, stable result columns, and timestamps.

- [ ] **Step 6: Record deferred verification**

Do not run:

```bash
./mvnw -Dtest=MaintenanceScheduleApprovalFirstMigrationContractTest,MaintenanceScheduleApprovalFirstMigrationPostgresTest test
```

Record: `not run / skipped by user instruction`.

- [ ] **Step 7: Commit Task 1**

```bash
git add src/main/resources/db/migration/V20260728_{4,5,6}__*.sql src/test/java/com/toir/migration/
git commit -m "feat: add approval-first maintenance schema"
```

---

### Task 2: Map new domain fields without changing legacy defaults

**Files:**
- Create: enum and entity files listed in “Backend files to create”.
- Create: `src/test/java/com/toir/entity/MaintenanceScheduleApprovalFirstMappingTest.java`
- Modify: `src/main/java/com/toir/entity/PprPlan.java`
- Modify: `src/main/java/com/toir/entity/PprTask.java`
- Modify: `src/main/java/com/toir/entity/ApprovalRequest.java`
- Modify: `src/main/java/com/toir/enums/PlanStatus.java`
- Modify: `src/main/java/com/toir/enums/ApprovalStatus.java`

**Interfaces:**
- Produces `PprPlan.isApprovalFirstMaintenanceSchedule()`.
- Produces immutable snapshot/command entities and Spring Data repositories.
- Reuses `ApprovalRequest.templateId`, `templateVersion`, `failureReason`, and `lastReturned*`.

- [ ] **Step 1: Write mapping/default tests**

```java
@Test
void manualPlanKeepsLegacySafeDefaults() {
    PprPlan plan = new PprPlan();
    assertThat(plan.getMaterializationMode())
            .isEqualTo(MaintenanceScheduleMaterializationMode.LEGACY_MATERIALIZED);
    assertThat(plan.getTaskMaterializationStatus())
            .isEqualTo(TaskMaterializationStatus.NOT_APPLICABLE);
}
```

- [ ] **Step 2: Add exact enums**

```java
public enum MaintenanceScheduleMaterializationMode {
    LEGACY_MATERIALIZED,
    APPROVAL_FIRST
}

public enum TaskMaterializationStatus {
    NOT_APPLICABLE,
    NOT_MATERIALIZED,
    MATERIALIZED
}
```

Add `CALCULATED` to `PlanStatus` and `SUPERSEDED` to `ApprovalStatus`.

- [ ] **Step 3: Map plan/task/request fields**

```java
public boolean isApprovalFirstMaintenanceSchedule() {
    return origin == PprPlanOrigin.MAINTENANCE_SCHEDULE
            && materializationMode == MaintenanceScheduleMaterializationMode.APPROVAL_FIRST;
}
```

Map `sourceCalculationItem` on `PprTask`. Add only the approved request-binding fields; do not add duplicate approval template/return columns or `approvedRevision`.

- [ ] **Step 4: Map immutable snapshot and command result entities**

```java
@Entity
@Table(name = "maintenance_schedule_calculation_items")
@org.hibernate.annotations.Immutable
public class MaintenanceScheduleCalculationItem {
    // fields map one exact plan revision and expose getters only
}
```

Repositories expose `saveAll` plus exact-revision reads; they do not expose domain update/delete service methods.

- [ ] **Step 5: Record deferred verification and commit**

Do not run:

```bash
./mvnw -Dtest=MaintenanceScheduleApprovalFirstMappingTest test
```

```bash
git add src/main/java/com/toir/entity src/main/java/com/toir/enums src/main/java/com/toir/repository src/test/java/com/toir/entity
git commit -m "feat: map maintenance calculation revisions"
```

---

### Task 3: Build deterministic snapshot keys and versioned content hashing

**Files:**
- Create: `MaintenanceScheduleSnapshotService.java`
- Create: `MaintenanceScheduleContentHasher.java`
- Create: `src/test/java/com/toir/service/maintanance/MaintenanceScheduleSnapshotServiceTest.java`
- Create: `src/test/java/com/toir/service/maintanance/MaintenanceScheduleContentHasherTest.java`
- Modify: `MaintenanceSchedulePreviewItem.java`
- Modify: `MaintenanceScheduleService.java`
- Modify: matching frontend preview type later in Task 10.

**Interfaces:**
- Produces:

```java
SnapshotRevision insertRevision(PprPlan plan, long revision, MaintenanceSchedulePreviewResponse preview);
List<MaintenanceScheduleCalculationItem> loadRevision(UUID planId, long revision);
ContentHash hash(PprPlan plan, List<PprPlanTarget> targets,
                 List<MaintenanceScheduleCalculationItem> items, int version);
```

- `ContentHash` contains `version`, full internal `sha256`, and a safe 12-character fingerprint.

- [ ] **Step 1: Write source-key stability tests**

```java
@Test
void sourceKeysIgnoreInputOrderAndIndependentOccurrences() {
    var first = snapshotService.toItems(plan(), List.of(itemB(), itemA()));
    var reordered = snapshotService.toItems(plan(), List.of(itemA(), itemB()));
    assertThat(keys(first)).containsExactlyElementsOf(keys(reordered));
}
```

Also assert that insertion of an unrelated earlier occurrence does not change existing keys.

- [ ] **Step 2: Write hash-version tests**

```java
@Test
void v1HashIsStableAcrossTargetAndItemOrdering() {
    ContentHash left = hasher.hash(plan(), targetsBThenA(), itemsBThenA(), 1);
    ContentHash right = hasher.hash(plan(), targetsAThenB(), itemsAThenB(), 1);
    assertThat(left.sha256()).isEqualTo(right.sha256());
}
```

Add a test proving localized/decorative labels are excluded while task-title snapshot, dates, labor, priority, and source identity change the hash.

- [ ] **Step 3: Extend preview with deterministic occurrence coordinates**

```java
public record MaintenanceSchedulePreviewItem(
        // existing fields,
        UUID templateId,
        MaintenanceTriggerPolicy triggerType,
        long cycleOrdinal,
        PriorityLevel priority,
        UUID departmentId
) {}
```

Calculate `cycleOrdinal` from rule anchor/periodicity, never list index. Continue using the current date formula; do not fix F-01 here.

- [ ] **Step 4: Implement pure canonical serializers**

```java
return switch (version) {
    case 1 -> sha256(serializeV1(header, sortedTargets, sortedItems));
    default -> throw RestException.conflict("PPR_CALCULATION_HASH_VERSION_UNSUPPORTED");
};
```

Use stable UTF-8 field framing rather than localized JSON serialization defaults.

- [ ] **Step 5: Implement insert-only snapshot persistence**

Generate version-1 source keys from canonical coordinates, insert all rows for the new revision, and read with:

```java
findAllByPlanIdAndCalculationRevisionOrderBySourceItemKeyAsc(planId, revision)
```

- [ ] **Step 6: Record deferred verification and commit**

```bash
# Do not run in this task:
./mvnw -Dtest=MaintenanceScheduleSnapshotServiceTest,MaintenanceScheduleContentHasherTest test
```

```bash
git add src/main/java/com/toir/dto/maintenanceschedule src/main/java/com/toir/service/maintanance src/test/java/com/toir/service/maintanance
git commit -m "feat: snapshot maintenance calculation content"
```

---

### Task 4: Create task-free calculations behind a default-off feature flag

**Files:**
- Create: `MaintenanceScheduleApprovalFirstProperties.java`
- Create: `src/test/java/com/toir/service/maintanance/MaintenanceScheduleApprovalFirstCalculationTest.java`
- Modify: `application.yml` and test configuration.
- Modify: `MaintenanceScheduleCalculationService.java`
- Modify: `PprPlanService.java`
- Modify: `MaintenanceScheduleCalculationController.java`

**Interfaces:**
- Produces `createApprovalFirstCalculation`, `updateApprovalFirstCalculation`, and `requireApprovalFirstEnabled`.
- Legacy calculations continue to call existing `PprPlanService` generation methods.

- [ ] **Step 1: Write feature-disabled and zero-task tests**

```java
@Test
void disabledFlagDoesNotCreateFallbackLegacyCalculation() {
    assertThatThrownBy(() -> service.create(request()))
            .hasMessageContaining("PPR_CALCULATION_APPROVAL_FIRST_DISABLED");
    verifyNoInteractions(snapshotService);
    verify(pprPlanService, never()).createScheduleCalculation(any());
}

@Test
void newCalculationIsCalculatedWithNoTasks() {
    MaintenanceScheduleCalculationDto result = enabledService.create(request());
    assertThat(result.plan().status()).isEqualTo(PlanStatus.CALCULATED);
    assertThat(result.plan().tasks()).isEmpty();
}
```

- [ ] **Step 2: Add default-false configuration**

```java
@ConfigurationProperties(prefix = "toir.maintenance-schedule.approval-first")
public record MaintenanceScheduleApprovalFirstProperties(boolean enabled) {}
```

Set `enabled: false` in committed configuration.

- [ ] **Step 3: Split PprPlanService legacy and approval-first creation**

Approval-first create saves plan/header/targets without calling `PprGeneratorService.generateForPlan`. It explicitly sets mode, revision 1, hash version 1, `CALCULATED`, and `NOT_MATERIALIZED`.

- [ ] **Step 4: Implement revisioned update**

Acquire the canonical advisory lock, lock plan, recheck active request, require expected revision, insert revision `n + 1`, and update the current hash. Do not touch `PprTask`. Apply the same lock and pending-request recheck to delete: allow only an unmaterialized editable calculation, block pending approval, and block approved/materialized records. Preserve the current legacy delete path for legacy calculations.

- [ ] **Step 5: Expose server capability**

Add:

```text
GET /api/v1/maintenance-schedule/calculations/capabilities
```

returning `approvalFirstEnabled` and actor-scoped `canCreate`. Create/update return a typed disabled conflict rather than falling back to legacy.

- [ ] **Step 6: Record deferred verification and commit**

```bash
# Do not run:
./mvnw -Dtest=MaintenanceScheduleApprovalFirstCalculationTest,MaintenanceScheduleCalculationServiceTest test
```

```bash
git add src/main/java/com/toir/config src/main/java/com/toir/service src/main/java/com/toir/controller src/main/resources src/test
git commit -m "feat: save task-free maintenance calculations"
```

---

### Task 5: Bind approval to revision, route, and requester context

**Files:**
- Create: `MaintenanceScheduleApprovalBindingService.java`
- Create: `src/test/java/com/toir/service/maintanance/MaintenanceScheduleApprovalBindingServiceTest.java`
- Modify: `ApprovalService.java`
- Modify: `ApprovalRequestRepository.java`
- Modify: `PprPlanRepository.java`
- Modify: `ApprovalScopeService.java` only for exposing canonical enforced scope inputs.
- Modify: approval repository query contract tests.

**Interfaces:**
- Produces:

```java
ApprovalBinding bind(UUID planId, UUID authenticatedRequesterId, ApprovalActionType action);
boolean matches(ApprovalRequest request, ApprovalBinding binding);
void lockTargetAction(UUID planId, ApprovalActionType action);
```

`ApprovalBinding` carries revision, hash/version, existing template ID/version, route fingerprint, and requester-context fingerprint.

- [ ] **Step 1: Write exact-reuse tests**

Test reuse only when every binding field and `status=PENDING` match. Each changed template, route fingerprint, requester context, revision, hash, or action must produce a new round.

- [ ] **Step 2: Write active-request and superseded-query tests**

```java
@Test
void supersededRequestsAreNotPendingActionableOrEscalated() {
    assertThat(repository.findPendingWithoutEscalation())
            .extracting(ApprovalRequest::getStatus)
            .doesNotContain(ApprovalStatus.SUPERSEDED);
}
```

- [ ] **Step 3: Implement canonical advisory lock and entity order**

Use the same transaction-scoped key for all maintenance calculation mutations. For an existing request: request lock → plan lock → snapshot/task state. For a new request: advisory lock → plan lock → pending recheck → insert.

- [ ] **Step 4: Integrate generic POST /approvals**

Detect `PPR_PLAN + MAINTENANCE_SCHEDULE + APPROVAL_FIRST`, require `action_type=APPROVE`, compute binding, and persist it on the request while reusing existing `templateId/templateVersion`.

- [ ] **Step 5: Record deferred verification and commit**

```bash
# Do not run:
./mvnw -Dtest=MaintenanceScheduleApprovalBindingServiceTest,ApprovalServiceTest,ApprovalRequestRepositoryQueryContractTest test
```

```bash
git add src/main/java/com/toir/service src/main/java/com/toir/repository src/test
git commit -m "feat: bind maintenance approval scope"
```

---

### Task 6: Derive lifecycle, capabilities, and bounded list metadata

**Files:**
- Create: `MaintenanceScheduleLifecycleService.java`
- Create: `MaintenanceScheduleCapabilityService.java`
- Create: DTO records for lifecycle, capabilities, and block reasons.
- Create: `src/test/java/com/toir/service/maintanance/MaintenanceScheduleLifecycleServiceTest.java`
- Create: `src/test/java/com/toir/service/maintanance/MaintenanceScheduleCapabilityServiceTest.java`
- Modify: calculation DTO, calculation/dashboard services, lifecycle repository, stats projection.

**Interfaces:**
- Produces:

```java
MaintenanceScheduleLifecycleStatus lifecycle(PprPlan plan, ApprovalMetadata approval);
MaintenanceScheduleCapabilities capabilities(PprPlan plan, ApprovalMetadata approval,
                                              AuthenticatedUser actor);
Map<UUID, ApprovalMetadata> loadApprovalMetadata(Collection<UUID> planIds);
```

- [ ] **Step 1: Write precedence tests**

```java
assertThat(lifecycle(approvedPlan(), pendingApproval())).isEqualTo(APPROVED);
assertThat(lifecycle(calculatedPlan(), matchingPending())).isEqualTo(PENDING_APPROVAL);
assertThat(lifecycle(calculatedPlan(), returned())).isEqualTo(RETURNED);
assertThat(lifecycle(newRevisionPlan(), oldRejectedRevision())).isEqualTo(CALCULATED);
```

- [ ] **Step 2: Write actor capability tests**

Cover creator pending lock, current approver review-amend/return, PBAC denial, materialized lock, and legacy `canReviewAmend=false / LEGACY_CALCULATION`.

- [ ] **Step 3: Replace per-row approval queries with one aggregate query**

Return active/latest request, current step, round, resolution, pending time, task count, and Work Order count keyed by plan ID. Update dashboard lifecycle SQL for `CALCULATED`, matching revision-bound pending requests, and `SUPERSEDED` exclusion.

- [ ] **Step 4: Expand additive DTOs**

Return revision, safe hash fingerprint, hash version, separate lifecycle/materialization dimensions, derived approved revision, approval metadata, counts, capabilities, and machine-readable blocked reasons.

- [ ] **Step 5: Record deferred verification and commit**

```bash
# Do not run:
./mvnw -Dtest=MaintenanceScheduleLifecycleServiceTest,MaintenanceScheduleCapabilityServiceTest,MaintenanceScheduleCalculationStatsServiceTest test
```

```bash
git add src/main/java/com/toir/dto/maintenanceschedule src/main/java/com/toir/service/maintanance src/main/java/com/toir/repository src/test
git commit -m "feat: expose maintenance calculation lifecycle"
```

---

### Task 7: Implement idempotent review-amend

**Files:**
- Create: `MaintenanceScheduleCommandIdempotencyService.java`
- Create: `MaintenanceScheduleReviewAmendRequest.java`
- Create: `MaintenanceScheduleReviewAmendResponse.java`
- Create: `src/test/java/com/toir/service/maintanance/MaintenanceScheduleReviewAmendServiceTest.java`
- Modify: calculation service/controller and audit wiring.

**Interfaces:**
- Consumes snapshot, hashing, approval binding, lifecycle, and command-result repositories.
- Produces:

```java
MaintenanceScheduleReviewAmendResponse reviewAmend(
        UUID calculationId,
        UUID clientCommandId,
        long expectedRevision,
        UUID expectedApprovalRequestId,
        String amendmentReason,
        MaintenanceScheduleCalculationRequest configuration,
        AuthenticatedUser actor);
```

- [ ] **Step 1: Write authorization, reason, and revision-conflict tests**

Cover authenticated principal authority, current-step assignment, approve+update permissions, PBAC, mandatory reason, and `PPR_CALCULATION_REVISION_CONFLICT`.

- [ ] **Step 2: Write idempotency tests**

Same command/fingerprint/actor returns one committed revision and round. Same key with changed payload returns `PPR_CALCULATION_IDEMPOTENCY_KEY_REUSED`. Different actor receives denial without stored result disclosure. A retention test deletes only completed command results older than 365 days and preserves newer/incomplete rows plus all approval and snapshot history.

- [ ] **Step 3: Implement command reservation/fingerprint**

Reserve `clientCommandId`, lock an existing row on conflict, compare versioned canonical fingerprint and actor/calculation identity, and commit stable result metadata with the domain mutation. Add `toir.maintenance-schedule.command-result-retention-days=365` and a focused retention service that deletes only completed command-result rows older than the configured cutoff; approval requests, snapshots, audit logs, and domain records are never deleted by this cleanup.

- [ ] **Step 4: Implement review-amend transaction**

Lock request then plan, preserve old decisions, mark old request `SUPERSEDED/REVIEW_AMEND`, create new immutable revision/hash, create a fresh request/round from route start, link `supersededByRequestId`, and audit old/new fingerprints.

- [ ] **Step 5: Return the complete command response**

Return previous/current revision, old/new request IDs, round/status, lifecycle/materialization, hash fingerprint, reset decision count, and `APPROVAL_DECISIONS_RESET_AFTER_AMENDMENT`.

- [ ] **Step 6: Record deferred verification and commit**

```bash
# Do not run:
./mvnw -Dtest=MaintenanceScheduleReviewAmendServiceTest test
```

```bash
git add src/main/java/com/toir/service/maintanance src/main/java/com/toir/dto/maintenanceschedule src/main/java/com/toir/controller/maintenance src/test
git commit -m "feat: restart approval after schedule amendment"
```

---

### Task 8: Implement terminal return-for-rework

**Files:**
- Create: return request/response DTOs.
- Create: `src/test/java/com/toir/service/maintanance/MaintenanceScheduleReturnForReworkTest.java`
- Create: `MaintenanceScheduleDomainEvent.java`
- Create: `MaintenanceScheduleAfterCommitListener.java`
- Modify: calculation service/controller and notification service integration.

**Interfaces:**
- Produces:

```java
MaintenanceScheduleCalculationDto returnForRework(
        UUID calculationId,
        UUID clientCommandId,
        UUID expectedApprovalRequestId,
        int expectedApprovalRound,
        String reason,
        AuthenticatedUser actor);
```

- [ ] **Step 1: Write return security/idempotency tests**

Test current approver, PBAC, mandatory reason, expected request/round, same-command retry, and no task creation.

- [ ] **Step 2: Implement dedicated endpoint**

```text
POST /api/v1/maintenance-schedule/calculations/{id}/return-for-rework
```

Do not call generic approval return.

- [ ] **Step 3: Persist canonical existing return metadata**

Set `status=CANCELLED`, `resolutionCode=RETURNED_FOR_REWORK`, and reuse `lastReturnedAt`, `lastReturnedBy`, and `lastReturnComment`. Keep revision/snapshot unchanged and plan `CALCULATED/NOT_MATERIALIZED`.

- [ ] **Step 4: Publish requester notification after commit**

Publish a domain event inside the transaction and handle it with:

```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void onReturnedForRework(MaintenanceScheduleReturnedForRework event) {
    notificationService.notifyReturnedForRework(event.requesterId(), event.calculationId());
}
```

- [ ] **Step 5: Record deferred verification and commit**

```bash
# Do not run:
./mvnw -Dtest=MaintenanceScheduleReturnForReworkTest test
```

```bash
git add src/main/java/com/toir/service/maintanance src/main/java/com/toir/controller/maintenance src/main/java/com/toir/dto/maintenanceschedule src/test
git commit -m "feat: return maintenance schedules for rework"
```

---

### Task 9: Materialize approved tasks exactly once and map committed conflicts

**Files:**
- Create: `MaintenanceScheduleMaterializationService.java`
- Create: materialization outcome/error DTOs.
- Create: `src/test/java/com/toir/service/maintanance/MaintenanceScheduleMaterializationServiceTest.java`
- Create: `src/test/java/com/toir/integration/MaintenanceScheduleMaterializationConcurrencyTest.java`
- Modify: `PprPlanApprovalHandler.java`
- Modify: `ApprovalService.java`
- Modify: `ApprovalController.java`
- Modify: approval action execution result contract and handler implementations.
- Modify: `PprTaskRepository.java`, `PprPlanService.java`, audit/event wiring.

**Interfaces:**
- Produces:

```java
MaintenanceScheduleFinalizationOutcome finalizeApproval(ApprovalRequest request);
```

Outcome codes:

```java
APPROVED,
IDEMPOTENT_SUCCESS,
SUPERSEDED_STALE,
MATERIALIZATION_INCONSISTENT,
ALREADY_MATERIALIZED_DIFFERENT_REVISION
```

- [ ] **Step 1: Write stale, success, and integrity tests**

Assert stale mismatch creates no task/approval notification/event and commits `SUPERSEDED + STALE_SCOPE + failureReason`. Assert success creates one `APPROVED` task per exact approved item. Assert metadata/task corruption is never silent success.

- [ ] **Step 2: Write deferred concurrency/rollback integration tests**

Two finalizers create one task set; source-item uniqueness holds; injected persistence failure rolls back tasks, plan metadata, and command state.

- [ ] **Step 3: Implement legacy delegation and approval-first lock/validation**

Legacy calls the current finalizer without snapshot requirements. Approval-first locks request → plan → item/task state, then validates request revision/hash/version before success state or side effects.

- [ ] **Step 4: Implement exact task creation**

Copy task fields from snapshot items, set `APPROVED`, bind `sourceCalculationItem`, set plan `APPROVED`, `MATERIALIZED`, materialized revision/count, and validate all idempotent-success invariants.

- [ ] **Step 5: Make generic approval execution outcome-aware**

Wrap existing handler JSON success results in an `ApprovalActionExecution` value. Map the maintenance outcome so `SUPERSEDED_STALE` does not continue approved notifications/audits, while existing handlers retain their current success behavior.

- [ ] **Step 6: Commit stale/conflict state before HTTP mapping**

The transactional service returns a typed decision result. After proxy commit, `ApprovalController` maps stale and integrity outcomes to typed 409 bodies with safe fingerprints and retry actions. Do not use `REQUIRES_NEW` or `noRollbackFor`.

- [ ] **Step 7: Publish successful external effects after commit**

Persist audit/outbox state transactionally, publish the maintenance materialized event, and perform notification/downstream delivery only in `AFTER_COMMIT`.

- [ ] **Step 8: Record deferred verification and commit**

```bash
# Do not run:
./mvnw -Dtest=MaintenanceScheduleMaterializationServiceTest,MaintenanceScheduleMaterializationConcurrencyTest,ApprovalServiceTest test
```

```bash
git add src/main/java/com/toir/service src/main/java/com/toir/controller src/main/java/com/toir/dto src/main/java/com/toir/repository src/test
git commit -m "feat: materialize approved maintenance tasks"
```

---

### Task 10: Add frontend contracts, exact query keys, and typed errors

**Repository:** `toir-frontend`

**Files:**
- Modify: `src/types/api.ts`
- Modify: `src/lib/api.ts`
- Create: query-key, idempotency, and stale helper files.
- Create: corresponding Vitest source files.

**Interfaces:**
- Produces `MaintenanceScheduleCalculationRecord` with backend lifecycle/capabilities.
- Produces dedicated review-amend/return API methods.
- Produces `maintenanceScheduleKeys`, `approvalKeys`, and `pprTaskKeys`.

- [ ] **Step 1: Write API contract tests**

```ts
expect(api.updateMaintenanceScheduleCalculation).toBeDefined();
expect(api.reviewAmendMaintenanceScheduleCalculation).toBeDefined();
expect(api.returnMaintenanceScheduleForRework).toBeDefined();
```

Assert review-amend never calls ordinary update and no calculation path calls generic `POST /ppr-plans`.

- [ ] **Step 2: Add lifecycle/materialization/capability types**

```ts
type MaintenanceScheduleLifecycleStatus =
  | "DRAFT" | "CALCULATED" | "PENDING_APPROVAL"
  | "RETURNED" | "REJECTED" | "APPROVED";
```

Keep lifecycle and materialization separate. Add safe unknown-string fallbacks at rendering boundaries.

- [ ] **Step 3: Add command API methods and headers**

Send `Idempotency-Key`, expected revision, and expected request/round. Extend `ApiError` with stale metadata and typed retry action.

- [ ] **Step 4: Add exact query-key factories**

```ts
export const maintenanceScheduleKeys = {
  all: ["maintenance-schedule-calculations"] as const,
  list: (filters: Query) => [...maintenanceScheduleKeys.all, "list", filters] as const,
  detail: (id: string) => [...maintenanceScheduleKeys.all, "detail", id] as const,
};
```

- [ ] **Step 5: Record deferred verification and commit**

```bash
# Do not run:
npx vitest run src/modules/repairs/libs/maintenance-schedule-builder
```

```bash
git add src/types/api.ts src/lib/api.ts src/modules/repairs/libs/maintenance-schedule-builder
git commit -m "feat: add maintenance approval-first contracts"
```

---

### Task 11: Render backend-derived lifecycle and capabilities

**Repository:** `toir-frontend`

**Files:**
- Modify: calculation lifecycle helper/tests.
- Modify: `maintenance-schedule-calculations-page.tsx`
- Modify: `ppr-plan-detail-page.tsx`
- Create: `maintenance-schedule-approval-panel.tsx`
- Modify: approval history/status helpers.
- Modify: locale resources.

**Interfaces:**
- Consumes backend booleans and block-reason codes without frontend RBAC re-derivation.
- Produces registry/detail actions and separate approval/materialization panels.

- [ ] **Step 1: Write lifecycle/action tests**

Cover zero-task `CALCULATED`, pending creator locked state, current approver actions, returned/rejected edit/resubmit, approved/materialized counts, legacy block reasons, and unknown status fallback.

- [ ] **Step 2: Replace frontend lifecycle inference**

Use `record.lifecycleStatus`, `record.materializationStatus`, `record.can*`, and blocked reasons. Do not derive pending status from `plan.status`.

- [ ] **Step 3: Render approval-first detail panel**

Show current/bound/materialized revision, round, approval status, lifecycle, materialization, task count, return/superseded reason, and only real task/Work Order links.

- [ ] **Step 4: Respect default-off server capability**

Hide/disable new create and review-amend UI when backend `approvalFirstEnabled=false`; retain readable legacy UI and do not trigger a legacy create fallback.

- [ ] **Step 5: Record deferred verification and commit**

```bash
# Do not run:
npx vitest run src/modules/repairs/libs/maintenance-schedule-builder/tests/calculation-lifecycle.test.ts
```

```bash
git add src/modules/repairs src/components/ui src/i18n src/types
git commit -m "feat: show maintenance approval lifecycle"
```

---

### Task 12: Add review-amend, return-for-rework, and stale recovery UX

**Repository:** `toir-frontend`

**Files:**
- Create: review-amend and return dialogs/tests.
- Modify: calculation dialog/preview page to support explicit review mode.
- Modify: registry/detail mutations.
- Modify: approval action handling and typed error UI.

**Interfaces:**
- Consumes dedicated API commands and stable per-user-action command IDs.
- Produces exact cache invalidation and typed stale recovery without automatic mutation retry.

- [ ] **Step 1: Write dedicated-command UI tests**

Assert mandatory reasons, explicit “save and restart approval” copy, old-decision reset message, dedicated endpoint only, and same command ID on transport retry.

- [ ] **Step 2: Implement command-ID lifecycle**

Create one UUID when the user confirms a command, keep it through retry/timeouts, and generate a new UUID only for a new user action.

- [ ] **Step 3: Implement review-amend**

Reuse the calculation form/preview, display revision/round and reset warning, require reason, call only review-amend, then reload current calculation/request.

- [ ] **Step 4: Implement return-for-rework**

Show only when `canReturnForRework`, require reason, call the domain endpoint, render `RETURNED` from backend lifecycle, and reopen creator edit/resubmit.

- [ ] **Step 5: Implement typed stale/conflict handling**

For `PPR_CALCULATION_APPROVAL_SCOPE_STALE`, disable old actions, set mutation retry false, invalidate exact calculation/approval/task keys, reload current revision, and show `retryAction`-specific navigation.

- [ ] **Step 6: Record deferred verification and commit**

```bash
# Do not run:
npx vitest run src/modules/repairs/components/maintenance-schedule-builder
```

```bash
git add src/modules/repairs src/components/ui src/lib src/i18n
git commit -m "feat: amend maintenance schedules in approval"
```

---

### Task 13: Complete static review and implementation report

**Files:**
- Create: `docs/maintenance-schedule-approval-first-implementation-report-2026-07-28.md`
- Modify only files needed to resolve static review findings within approved scope.

**Interfaces:**
- Produces the handoff report required by the user.

- [ ] **Step 1: Review scope and repository diffs**

```bash
git diff --check
git diff --stat
git status --short
```

Run these static Git commands only; they are not build/test/runtime verification.

- [ ] **Step 2: Perform source-level contract scans**

```bash
rg -n "APPROVAL_FIRST|CALCULATED|SUPERSEDED|source_calculation_item_id" src/main src/test
rg -n "review-amend|return-for-rework|PPR_CALCULATION_APPROVAL_SCOPE_STALE" ../toir-frontend/src
rg -n "POST /ppr-plans|createPprPlan" ../toir-frontend/src/modules/repairs
```

Confirm the last scan has no active routed calculation fallback.

- [ ] **Step 3: Inspect migration safety statically**

Confirm `_4/_5/_6`, legacy defaults, no update/delete of existing PPR tasks, no global approval-first default, and no modification of `V20260728_3`.

- [ ] **Step 4: Write the final implementation report**

Include:

- migrations;
- backend domain changes;
- approval revision/hash behavior;
- chief-engineer review-amend;
- terminal return;
- final task materialization;
- frontend lifecycle/actions;
- backward compatibility;
- written/updated tests;
- unresolved F-01 and rollout risk;
- verification: `not run / skipped by user instruction`.

State explicitly that static source/diff review does not prove compilation, test success, migration execution, or runtime correctness.

- [ ] **Step 5: Commit the report and any scoped static-review fixes**

```bash
git add docs/maintenance-schedule-approval-first-implementation-report-2026-07-28.md
git commit -m "docs: report approval-first maintenance implementation"
```

---

## Deferred Verification Commands

These commands are documented for a later explicitly authorized verification phase and must not be run during this implementation:

```bash
cd toir-backend
./mvnw test

cd ../toir-frontend
npm test
npm run build
```

Current required status: `not run / skipped by user instruction`.
