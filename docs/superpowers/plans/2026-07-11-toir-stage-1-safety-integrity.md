# TOiR Stage 1 Safety and Integrity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Establish the fail-closed safety, operation-specific authorization, idempotent Work Order generation, optimistic concurrency, and exact monetary foundations required by later Planned Shutdown and Repair Campaign stages.

**Architecture:** Add forward-only PostgreSQL constraints and JPA fields first, then enforce them through narrow policy methods and controller permissions. Repair Campaign generation uses a deterministic database-unique key, while money is represented as decimal strings at the frontend boundary and `BigDecimal` in Java. Stage 1 does not invent shutdown safety evidence; it introduces flags and blockers that Stage 2 will connect to the full Planned Shutdown aggregate.

**Tech Stack:** Java 21, Spring Boot 3.3.5, Spring Security, Spring Data JPA, PostgreSQL/Flyway, JUnit 5/Mockito/MockMvc, React 19, TypeScript 6, Vitest 4, Vite 8.

## Global Constraints

- Work directly on the existing `Codex_org` branches; pre-existing dirty work is already isolated in baseline commits.
- Use forward-only Flyway migrations and preserve historical data.
- Missing safety configuration must block only Work Orders explicitly marked `requiresShutdown` or `requiresIsolation`; existing ordinary Work Orders remain compatible.
- Database uniqueness and optimistic locking are authoritative for concurrent requests.
- Money uses `numeric(19,4)`, Java `BigDecimal`, and ISO-4217 `currencyCode`.
- Every behavior change starts with a failing automated test.
- Do not push until all five remediation stages and the final completion report pass verification.

---

### Task 1: Stage 1 database and entity foundations

**Files:**
- Create: `src/main/resources/db/migration/V20260711_1__toir_stage1_integrity_foundation.sql`
- Create: `src/test/java/com/toir/migration/ToirStage1IntegrityMigrationContractTest.java`
- Modify: `src/main/java/com/toir/entity/PlannedShutdown.java`
- Modify: `src/main/java/com/toir/entity/repair/RepairCampaign.java`
- Modify: `src/main/java/com/toir/entity/repair/RepairCampaignStage.java`
- Modify: `src/main/java/com/toir/entity/repair/RepairCampaignDepartment.java`
- Modify: `src/main/java/com/toir/entity/maintenance/WorkOrder.java`

**Interfaces:**
- Produces: optimistic `version` on Planned Shutdown and Repair Campaign; Work Order `requiresShutdown`, `requiresIsolation`, and `generationKey`; Repair Campaign `currencyCode`; decimal money columns.
- Consumes: existing UUID primary keys and soft-delete convention.

- [ ] **Step 1: Write the failing migration contract test**

```java
class ToirStage1IntegrityMigrationContractTest {
    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V20260711_1__toir_stage1_integrity_foundation.sql");

    @Test
    void addsConcurrencySafetyIdempotencyAndExactMoney() throws Exception {
        String sql = Files.readString(MIGRATION).toLowerCase();
        assertThat(sql).contains("ADD COLUMN IF NOT EXISTS version bigint NOT NULL DEFAULT 0");
        assertThat(sql).contains("requires_shutdown boolean NOT NULL DEFAULT false");
        assertThat(sql).contains("requires_isolation boolean NOT NULL DEFAULT false");
        assertThat(sql).contains("generation_key varchar(512)");
        assertThat(sql).contains("uq_work_orders_active_generation_key");
        assertThat(sql).contains("numeric(19,4)");
        assertThat(sql).contains("currency_code varchar(3) NOT NULL DEFAULT 'UZS'");
    }
}
```

- [ ] **Step 2: Run the migration contract test and confirm RED**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 24) ./mvnw -Dtest=ToirStage1IntegrityMigrationContractTest test`

Expected: FAIL because the migration file does not exist.

- [ ] **Step 3: Add the forward-only migration**

```sql
ALTER TABLE planned_shutdowns
    ADD COLUMN IF NOT EXISTS version bigint NOT NULL DEFAULT 0;

ALTER TABLE repair_campaigns
    ADD COLUMN IF NOT EXISTS version bigint NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS currency_code varchar(3) NOT NULL DEFAULT 'UZS';

ALTER TABLE work_orders
    ADD COLUMN IF NOT EXISTS requires_shutdown boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS requires_isolation boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS generation_key varchar(512);

ALTER TABLE repair_campaigns
    ALTER COLUMN total_budget TYPE numeric(19,4) USING round(total_budget::numeric, 4),
    ALTER COLUMN total_actual TYPE numeric(19,4) USING round(total_actual::numeric, 4);
ALTER TABLE repair_campaign_stages
    ALTER COLUMN planned_cost TYPE numeric(19,4) USING round(planned_cost::numeric, 4),
    ALTER COLUMN actual_cost TYPE numeric(19,4) USING round(actual_cost::numeric, 4);
ALTER TABLE repair_campaign_departments
    ALTER COLUMN planned_budget TYPE numeric(19,4) USING round(planned_budget::numeric, 4);

CREATE UNIQUE INDEX IF NOT EXISTS uq_work_orders_active_generation_key
    ON work_orders (generation_key)
    WHERE generation_key IS NOT NULL AND is_deleted = false;
```

- [ ] **Step 4: Map the new columns in JPA**

```java
@Version
@Column(name = "version", nullable = false)
private Long version;
```

Add the version field to `PlannedShutdown` and `RepairCampaign`. Add to `WorkOrder`:

```java
@Column(name = "requires_shutdown", nullable = false)
private boolean requiresShutdown;

@Column(name = "requires_isolation", nullable = false)
private boolean requiresIsolation;

@Column(name = "generation_key", length = 512)
private String generationKey;
```

Add `currencyCode` to `RepairCampaign`. Task 5 converts the Java money types after the schema migration is independently verified.

- [ ] **Step 5: Run the migration contract and compile**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 24) ./mvnw -DskipTests compile`

Expected: BUILD SUCCESS. Hibernate may map the existing primitive money fields to the new numeric columns until Task 5 completes the Java conversion.

- [ ] **Step 6: Commit the schema foundation**

Run: `git add src/main/resources/db/migration/V20260711_1__toir_stage1_integrity_foundation.sql src/test/java/com/toir/migration/ToirStage1IntegrityMigrationContractTest.java src/main/java/com/toir/entity && git commit -m "feat: add TOiR stage 1 integrity schema"`

---

### Task 2: Operation-specific Planned Shutdown and Repair Campaign authorization

**Files:**
- Create: `src/test/java/com/toir/security/RbacToirBusinessFlowSecurityTest.java`
- Create: `src/main/resources/db/migration/V20260711_2__toir_business_flow_permissions.sql`
- Modify: `src/main/java/com/toir/security/PermissionConstants.java`
- Modify: `src/main/java/com/toir/security/ApprovalDomainPermissions.java`
- Modify: `src/main/java/com/toir/security/ApprovalSecurityExpressions.java`
- Modify: `src/main/java/com/toir/controller/PlannedShutdownController.java`
- Modify: `src/main/java/com/toir/controller/repair/RepairCampaignController.java`
- Modify: `toir-front/src/lib/permissions.ts`
- Test: `toir-front/src/lib/access-control.test.ts`

**Interfaces:**
- Produces: exact permission codes `PLANNED_SHUTDOWN_*` and `REPAIR_CAMPAIGN_*` used by backend annotations, approval fallback, role seed, and frontend action visibility.
- Consumes: existing `SYSTEM_ADMIN` and wildcard bypass convention.

- [ ] **Step 1: Write failing MockMvc authorization tests**

```java
@Test
@WithMockUser(authorities = PermissionConstants.USER_READ)
void unrelatedUserCannotStartOrCloseCampaign() throws Exception {
    mockMvc.perform(post("/api/v1/repair-campaigns/{id}/start", UUID.randomUUID()))
            .andExpect(status().isForbidden());
    mockMvc.perform(post("/api/v1/repair-campaigns/{id}/close", UUID.randomUUID()))
            .andExpect(status().isForbidden());
}

@Test
@WithMockUser(authorities = PermissionConstants.REPAIR_CAMPAIGN_START)
void campaignStarterCanStartCampaign() throws Exception {
    when(service.start(any())).thenReturn(campaignDto());
    mockMvc.perform(post("/api/v1/repair-campaigns/{id}/start", UUID.randomUUID()))
            .andExpect(status().isOk());
}
```

Add equivalent create/read checks for Planned Shutdown and create/update/generate/cancel checks for Repair Campaign.

- [ ] **Step 2: Run security tests and confirm RED**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 24) ./mvnw -Dtest=RbacToirBusinessFlowSecurityTest test`

Expected: unauthorized requests are not 403 because annotations/constants are missing.

- [ ] **Step 3: Add permission constants and controller annotations**

Add Planned Shutdown permissions: `READ`, `CREATE`, `UPDATE`, `APPROVE`, `PREPARE`, `CONFIRM_SAFE_STATE`, `START_REPAIR`, `TEST`, `STARTUP`, `CLOSE`, `CANCEL`, `RESCHEDULE`, `EXTEND`.

Add Repair Campaign permissions: `READ`, `CREATE`, `UPDATE`, `APPROVE`, `START`, `SUSPEND`, `COMPLETE`, `CLOSE`, `CANCEL`, `GENERATE_WORK_ORDERS`.

Use compile-time expressions such as:

```java
@PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('REPAIR_CAMPAIGN_START')")
```

- [ ] **Step 4: Replace generic approval fallback**

```java
case PLANNED_SHUTDOWN -> optional(PermissionConstants.PLANNED_SHUTDOWN_APPROVE);
case REPAIR_CAMPAIGN -> optional(PermissionConstants.REPAIR_CAMPAIGN_APPROVE);
```

Add both permissions to `ApprovalSecurityExpressions.CAN_CREATE`, `CAN_APPROVE`, and `CAN_REJECT`.

- [ ] **Step 5: Seed role permissions**

Use the existing `append_role_permissions` migration pattern. Grant all new permissions to `SYSTEM_ADMIN`, operational management permissions to `MAINTENANCE_MANAGER`, read/update operational subsets to `MAINTENANCE_ENGINEER`, and approval-specific permissions only to the corresponding manager roles already present in seed data.

- [ ] **Step 6: Add frontend constants and tests**

```ts
expect(can(userWith("REPAIR_CAMPAIGN_START"), REPAIR_CAMPAIGN_START)).toBe(true);
expect(can(userWith("WORK_ORDER_START"), REPAIR_CAMPAIGN_START)).toBe(false);
```

- [ ] **Step 7: Run backend and frontend authorization tests**

Run backend: `JAVA_HOME=$(/usr/libexec/java_home -v 24) ./mvnw -Dtest=RbacToirBusinessFlowSecurityTest,ApprovalServiceTest test`

Run frontend: `yarn test --run src/lib/access-control.test.ts`

Expected: all pass.

- [ ] **Step 8: Commit authorization in each repository**

Backend commit: `feat: enforce TOiR business flow permissions`

Frontend commit: `feat: add TOiR business flow permissions`

---

### Task 3: Fail-closed safety flags for Work Order start

**Files:**
- Modify: `src/main/java/com/toir/dto/workorder/WorkOrderRequest.java`
- Modify: `src/main/java/com/toir/dto/workorder/WorkOrderDto.java`
- Modify: `src/main/java/com/toir/service/WorkOrderService.java`
- Modify: `src/main/java/com/toir/service/SafetyChecklistService.java`
- Test: `src/test/java/com/toir/service/WorkOrderServiceTest.java`
- Test: `src/test/java/com/toir/service/SafetyChecklistServiceTest.java`

**Interfaces:**
- Produces: `requiresShutdown` and `requiresIsolation` on Work Order create/detail; `SafetyChecklistService.assertCanStart()` blocks flagged Work Orders when no checklist exists.
- Consumes: Task 1 Work Order columns.

- [ ] **Step 1: Add failing safety tests**

```java
@Test
void startShutdownRequiredWorkOrderWithoutChecklistFailsClosed() {
    WorkOrder workOrder = approvedWorkOrder();
    workOrder.setRequiresShutdown(true);
    when(checklistRepository.findFirstByWorkOrderIdAndIsDeletedFalseAndStatusNotInOrderByUpdatedAtDesc(
            eq(workOrder.getId()), any())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.assertCanStart(workOrder))
            .hasMessageContaining("Safety checklist is required");
}

@Test
void ordinaryWorkOrderWithoutChecklistRemainsStartable() {
    WorkOrder workOrder = approvedWorkOrder();
    service.assertCanStart(workOrder);
}
```

- [ ] **Step 2: Run tests and confirm RED**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 24) ./mvnw -Dtest=SafetyChecklistServiceTest,WorkOrderServiceTest test`

Expected: shutdown-required Work Order is incorrectly allowed.

- [ ] **Step 3: Implement the conditional fail-closed policy**

```java
if (checklist.isEmpty()) {
    if (workOrder.isRequiresShutdown() || workOrder.isRequiresIsolation()) {
        throw RestException.badRequest("Safety checklist is required for shutdown/isolation work");
    }
    return;
}
```

Map request flags during Work Order creation and expose them in DTOs. Default absent JSON values to false.

- [ ] **Step 4: Run targeted safety and Work Order tests**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 24) ./mvnw -Dtest=SafetyChecklistServiceTest,WorkOrderServiceTest test`

Expected: pass with ordinary compatibility and fail-closed flagged behavior.

- [ ] **Step 5: Commit**

Commit: `feat: fail closed for shutdown safety checklists`

---

### Task 4: Approved-only idempotent Repair Campaign Work Order generation

**Files:**
- Modify: `src/main/java/com/toir/repository/WorkOrderRepository.java`
- Modify: `src/main/java/com/toir/service/repair/RepairCampaignService.java`
- Modify: `src/main/java/com/toir/dto/repaircampaign/RepairCampaignGenerateWorkOrdersRequest.java`
- Test: `src/test/java/com/toir/service/RepairCampaignServiceTest.java`
- Test: `src/test/java/com/toir/migration/ToirStage1IntegrityMigrationContractTest.java`
- Modify: `toir-front/src/lib/api.ts`
- Test: `toir-front/src/lib/api.test.ts`

**Interfaces:**
- Produces: deterministic `RC:<campaignId>:<stageId>:<equipmentId>` generation key; repeated generation returns the existing canonical Work Order; only `APPROVED`, `PREPARATION`, or `IN_PROGRESS` campaigns may generate.
- Consumes: Task 1 unique index and `generationKey` entity field.

- [ ] **Step 1: Write failing campaign tests**

```java
@Test
void generateWorkOrdersRejectsDraftCampaign() {
    RepairCampaign campaign = campaign(RepairCampaignStatus.DRAFT);
    when(repository.findByIdAndIsDeletedFalse(campaign.getId())).thenReturn(Optional.of(campaign));

    assertThatThrownBy(() -> service.generateWorkOrders(campaign.getId(), request()))
            .hasMessageContaining("approved");
    verify(workOrderService, never()).create(any());
}

@Test
void generateWorkOrdersReturnsExistingCanonicalOrderOnReplay() {
    WorkOrder existing = generatedOrder(generationKey(campaignId, stageId, equipmentId));
    when(workOrderRepository.findByGenerationKeyAndIsDeletedFalse(existing.getGenerationKey()))
            .thenReturn(Optional.of(existing));

    List<WorkOrderDto> result = service.generateWorkOrders(campaignId, request());

    assertThat(result).hasSize(1);
    verify(workOrderService, never()).create(any());
}
```

- [ ] **Step 2: Run tests and confirm RED**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 24) ./mvnw -Dtest=RepairCampaignServiceTest test`

Expected: DRAFT generation succeeds and replay creates another Work Order.

- [ ] **Step 3: Add repository lookup and generation policy**

```java
Optional<WorkOrder> findByGenerationKeyAndIsDeletedFalse(String generationKey);
```

```java
private void assertCampaignCanGenerateWorkOrders(RepairCampaign campaign) {
    if (!EnumSet.of(APPROVED, PREPARATION, IN_PROGRESS).contains(campaign.getStatus())) {
        throw RestException.badRequest("Repair campaign must be approved before generating work orders");
    }
}
```

Stage 1 accepts current enum statuses `APPROVED` and `IN_PROGRESS`; Stage 3 adds `PREPARATION` to this policy. Generate a deterministic key, query it first for replay UX, set it on `WorkOrderRequest`, and rely on the unique index for races. Translate a unique-constraint race into lookup-and-return of the canonical row.

- [ ] **Step 4: Ensure bulk rollback semantics**

Keep the entire method `@Transactional`. Add a test that throws on the second create and verifies no success result is returned and the transaction is marked for rollback in an integration test when PostgreSQL is available.

- [ ] **Step 5: Send an idempotency header from frontend API**

```ts
headers: { "Idempotency-Key": idempotencyKey },
```

The controller requires the header and passes it to `generateWorkOrders(UUID, RepairCampaignGenerateWorkOrdersRequest, String)`. The service validates a nonblank key and writes it into the audit correlation payload. Canonical result replay is controlled by deterministic per-equipment generation keys; the request key prevents accidental mutation retries from using a new client identity.

- [ ] **Step 6: Run targeted backend/frontend tests**

Run backend: `JAVA_HOME=$(/usr/libexec/java_home -v 24) ./mvnw -Dtest=RepairCampaignServiceTest,RepairCampaignControllerContractTest test`

Run frontend: `yarn test --run src/lib/api.test.ts`

Expected: pass.

- [ ] **Step 7: Commit in both repositories**

Backend commit: `feat: make campaign work order generation idempotent`

Frontend commit: `feat: send campaign generation idempotency keys`

---

### Task 5: Exact Repair Campaign monetary contracts

**Files:**
- Create: `src/main/java/com/toir/dto/common/MoneyDecimalStringDeserializer.java`
- Modify: all DTOs in `src/main/java/com/toir/dto/repaircampaign/`
- Modify: `src/main/java/com/toir/service/repair/RepairCampaignService.java`
- Modify: `src/test/java/com/toir/service/RepairCampaignServiceTest.java`
- Modify: `src/test/java/com/toir/controller/RepairCampaignControllerContractTest.java`
- Modify: `toir-front/src/types/api.ts`
- Modify: `toir-front/src/modules/repairs/libs/repair-campaigns/types.ts`
- Modify: `toir-front/src/modules/repairs/libs/repair-campaigns/form-contract.ts`
- Modify: `toir-front/src/modules/repairs/pages/repair-campaign-detail-page.tsx`
- Test: `toir-front/src/modules/repairs/libs/repair-campaigns/tests/repair-campaign-form-contract.test.ts`

**Interfaces:**
- Produces: Java `BigDecimal` for campaign-owned money; JSON decimal strings; frontend string-form state and decimal-string payloads; `currencyCode` throughout.
- Consumes: Task 1 numeric columns and campaign currency field.

- [ ] **Step 1: Write failing decimal precision tests**

```java
@Test
void createPreservesFourDecimalBudgetAndCurrency() {
    RepairCampaignRequest request = request(new BigDecimal("123456789.1234"), "UZS");
    when(repository.save(any())).thenAnswer(invocation -> withId(invocation.getArgument(0)));

    RepairCampaignDto result = service.create(request);

    assertThat(result.totalBudget()).isEqualByComparingTo("123456789.1234");
    assertThat(result.currencyCode()).isEqualTo("UZS");
}
```

Frontend:

```ts
expect(buildCreateCampaignPayload(formWith({ totalBudget: "123456789.1234" }))).toMatchObject({
  totalBudget: "123456789.1234",
  currencyCode: "UZS",
});
```

- [ ] **Step 2: Run backend and frontend tests and confirm RED**

Expected: Java constructors require `double` and frontend emits a number.

- [ ] **Step 3: Convert campaign-owned money to BigDecimal**

Use `BigDecimal.ZERO`, `add`, `subtract`, `compareTo`, and `divide(..., 4, RoundingMode.HALF_UP)`. Convert `ActualCost.getAmount()` and linked budget doubles at the boundary with `BigDecimal.valueOf()` until their owning finance module is migrated in a later finance-specific task.

Remove `mapToDouble` and primitive arithmetic from `RepairCampaignService`. The deprecated complete-stage overload accepts `BigDecimal ignoredActualCost`.

- [ ] **Step 4: Emit decimal strings to frontend**

Use the existing `com.toir.dto.sparepartlifecycle.DecimalStringSerializer` for output. Add `MoneyDecimalStringDeserializer`, which accepts only canonical JSON strings and rejects scale greater than four. Annotate Repair Campaign request money fields with both serializer/deserializer as appropriate. Change frontend Repair Campaign API monetary fields to `string`, parse only for chart/display using a shared finite decimal formatter, and send unchanged normalized decimal strings.

- [ ] **Step 5: Run complete targeted campaign suites and build**

Backend: `JAVA_HOME=$(/usr/libexec/java_home -v 24) ./mvnw -Dtest=RepairCampaignServiceTest,RepairCampaignControllerContractTest,RepairCampaignBudgetLinksMigrationContractTest,ToirStage1IntegrityMigrationContractTest test`

Frontend: `yarn test --run src/modules/repairs/libs/repair-campaigns/tests/repair-campaign-form-contract.test.ts src/lib/api.test.ts && yarn build`

Expected: pass and exact four-decimal values preserved.

- [ ] **Step 6: Commit backend and frontend monetary changes**

Backend commit: `feat: use exact repair campaign monetary values`

Frontend commit: `feat: preserve repair campaign decimal values`

---

### Task 6: Stage 1 verification and evidence report

**Files:**
- Create: `docs/audits/2026-07-11-toir-stage-1-verification.md`
- Create: `toir-front/docs/audits/2026-07-11-toir-stage-1-verification.md`

**Interfaces:**
- Produces: reproducible Stage 1 evidence and the contract consumed by the Stage 2 plan.
- Consumes: all Tasks 1-5 commits and test outputs.

- [ ] **Step 1: Run backend targeted tests**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 24) ./mvnw -Dtest=ToirStage1IntegrityMigrationContractTest,RbacToirBusinessFlowSecurityTest,SafetyChecklistServiceTest,WorkOrderServiceTest,RepairCampaignServiceTest,RepairCampaignControllerContractTest,ApprovalServiceTest test`

Expected: zero failures and zero errors.

- [ ] **Step 2: Run frontend targeted tests and build**

Run: `yarn test --run src/lib/access-control.test.ts src/lib/api.test.ts src/modules/repairs/libs/repair-campaigns/tests/repair-campaign-form-contract.test.ts && yarn build`

Expected: zero test failures and successful production build.

- [ ] **Step 3: Validate migrations**

Run contract tests unconditionally. If Docker is available, run `FlywayEmptyDbSmokeTest`; otherwise record the exact skip reason and do not call it passed.

- [ ] **Step 4: Review diffs and worktrees**

Run: `git diff --check`, `git status --short --branch`, and `git log --oneline` in both repositories.

Expected: no whitespace errors and only expected report/plan changes uncommitted.

- [ ] **Step 5: Write the Stage 1 verification report**

Record migrations, permissions, API behavior, negative tests, exact commands/results, known limitations deferred to Stage 2, and commit IDs. Do not claim repository-wide lint or full regression success unless those commands were run and passed.

- [ ] **Step 6: Commit the verification report in both repositories**

Commit: `docs: report TOiR stage 1 verification`

- [ ] **Step 7: Begin Stage 2 planning automatically**

Invoke `superpowers:writing-plans` for the Planned Shutdown core plan using only the verified Stage 1 interfaces.

---

### Task 7: Close final Stage 1 review gaps

**Files:**
- Modify: `src/main/java/com/toir/repository/repair/RepairCampaignRepository.java`
- Modify: `src/main/java/com/toir/service/repair/RepairCampaignService.java`
- Modify: `src/main/java/com/toir/exception/GlobalExceptionHandler.java`
- Modify: Repair Campaign request DTOs and validation helpers
- Test: Repair Campaign concurrency, validation, and exception-handler tests
- Modify: `toir-front/src/modules/repairs/pages/repair-campaign-detail-page.tsx`
- Modify: Repair Campaign action and form-contract helpers/tests

**Interfaces:**
- Produces: campaign-row serialization for generation/status transitions, stable optimistic-lock HTTP 409 responses, permission-aware frontend actions, and `numeric(19,4)`/ISO-4217 request validation.
- Consumes: the verified Tasks 1-6 Stage 1 contracts.

- [ ] **Step 1: Write failing campaign-generation/status-transition locking tests**

Require generation to load and validate the campaign under a database write lock before acquiring deterministic generation-key locks. Add repository lock-contract coverage and a PostgreSQL concurrency test when PostgreSQL is available; otherwise keep the deterministic service/repository contract test and disclose the live-test limitation.

- [ ] **Step 2: Implement campaign-row serialization**

Add a `PESSIMISTIC_WRITE`/`SELECT ... FOR UPDATE` repository method and use the locked campaign read for the eligibility decision in generation. Keep deterministic equipment ordering and per-generation-key advisory locks.

- [ ] **Step 3: Write failing optimistic-lock HTTP contract tests**

Verify `ObjectOptimisticLockingFailureException` and JPA `OptimisticLockException` produce a stable HTTP 409 response rather than the catch-all 500.

- [ ] **Step 4: Implement optimistic-lock conflict mapping**

Add the narrow global exception handler and preserve existing domain-conflict behavior.

- [ ] **Step 5: Write failing permission-visibility tests**

Cover start, complete, close, cancel, edit, add-stage, attach, and manual-create actions for users with the exact Repair Campaign permission and users with unrelated authorities.

- [ ] **Step 6: Gate every Repair Campaign detail action by exact permission**

Combine lifecycle/status conditions with the corresponding `REPAIR_CAMPAIGN_*` permission, and hide empty action menus.

- [ ] **Step 7: Write failing monetary and currency boundary tests**

Reject more than 15 integer digits or 4 fractional digits for every campaign-owned input money value. Reject invalid/non-uppercase ISO-4217 currency codes and mirror the money limit in frontend validation.

- [ ] **Step 8: Implement exact boundary validation**

Use `@Digits(integer = 15, fraction = 4)` at backend request boundaries, validate and normalize currency against `java.util.Currency`, and keep canonical decimal strings on the frontend.

- [ ] **Step 9: Run corrective verification and re-review**

Run focused backend/frontend tests, the Stage 1 aggregate suites, and the frontend production build. Request independent code review of the corrective commits before beginning Stage 2.
