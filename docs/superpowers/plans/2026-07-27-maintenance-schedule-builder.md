# Maintenance Schedule Builder Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a stateless annual maintenance schedule preview, generate identical occurrence-based PPR tasks from an explicitly anchored plan, and hide pre-approval plans/tasks from non-mechanics.

**Architecture:** A new `MaintenanceScheduleService` is the only component that selects equipment/effective rules and expands date occurrences. The preview controller maps its result to JSON, while `PprGeneratorService` maps the same result to persisted tasks when a plan has an explicit `anchorMode`; legacy plans keep the current generator path. A separate visibility policy supplies allowed parent-plan statuses to read queries.

**Tech Stack:** Java 21, Spring Boot 3.3.5, Spring MVC, Spring Security, Spring Data JPA, PostgreSQL/Flyway, JUnit 5, Mockito, MockMvc, AssertJ.

## Global Constraints

- Do not modify `MaintenanceDueEvent` processing or lifecycle transitions.
- Do not add a preview-run entity or any `/maintenance-schedule/preview-runs` endpoint.
- Reuse `PPR_PLAN_CREATE`, `PPR_PLAN_GENERATE`, and existing PPR permissions.
- A mechanic has `PPR_PLAN_GENERATE` or `PPR_PLAN_APPROVE`; `SYSTEM_ADMIN` and `*` are unrestricted.
- Existing PPR clients that omit `anchorMode` must keep legacy generation behavior.
- Preview and explicit-anchor PPR generation must call the same occurrence calculation method.
- All schedule date windows are inclusive.

---

### Task 1: Anchor-mode persistence and API contracts

**Files:**
- Create: `src/main/java/com/toir/enums/MaintenanceScheduleAnchorMode.java`
- Create: `src/main/java/com/toir/enums/MaintenanceScheduleScopeType.java`
- Create: `src/main/java/com/toir/enums/MaintenanceScheduleAnchorSource.java`
- Create: `src/main/resources/db/migration/V20260727_1__ppr_plan_maintenance_schedule_anchor.sql`
- Create: `src/test/java/com/toir/migration/PprPlanMaintenanceScheduleAnchorMigrationContractTest.java`
- Modify: `src/main/java/com/toir/entity/PprPlan.java`
- Modify: `src/main/java/com/toir/dto/pprplanning/PprPlanRequest.java`
- Modify: `src/main/java/com/toir/dto/pprplanning/PprPlanDto.java`
- Modify: `src/main/java/com/toir/service/PprPlanService.java`
- Modify: `src/main/java/com/toir/controller/PprPlanController.java`
- Modify: affected PPR DTO/controller/service tests.

**Interfaces:**
- Produces: `MaintenanceScheduleAnchorMode { CURRENT, RESET_TO_PLAN_START }`.
- Produces: nullable `PprPlan.anchorMode`.
- Produces: nullable `PprPlanRequest.anchorMode()` and `PprPlanDto.anchorMode()`.
- Existing request constructors remain source-compatible by passing `null`.

- [ ] **Step 1: Write the migration contract test**

```java
class PprPlanMaintenanceScheduleAnchorMigrationContractTest {
    @Test
    void migrationAddsNullableConstrainedAnchorMode() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V20260727_1__ppr_plan_maintenance_schedule_anchor.sql"
        )).toLowerCase(Locale.ROOT);

        assertThat(sql).contains("alter table ppr_plans");
        assertThat(sql).contains("add column if not exists anchor_mode");
        assertThat(sql).contains("'current'");
        assertThat(sql).contains("'reset_to_plan_start'");
        assertThat(sql).doesNotContain("anchor_mode varchar(32) not null");
    }
}
```

- [ ] **Step 2: Run the migration test and verify failure**

Run:

```bash
./mvnw -Dtest=PprPlanMaintenanceScheduleAnchorMigrationContractTest test
```

Expected: FAIL because the migration does not exist.

- [ ] **Step 3: Add the enum and Flyway migration**

```java
public enum MaintenanceScheduleAnchorMode {
    CURRENT,
    RESET_TO_PLAN_START
}
```

```sql
ALTER TABLE ppr_plans
    ADD COLUMN IF NOT EXISTS anchor_mode varchar(32);

ALTER TABLE ppr_plans
    DROP CONSTRAINT IF EXISTS ck_ppr_plans_anchor_mode;

ALTER TABLE ppr_plans
    ADD CONSTRAINT ck_ppr_plans_anchor_mode
    CHECK (anchor_mode IS NULL OR anchor_mode IN ('CURRENT', 'RESET_TO_PLAN_START'));
```

- [ ] **Step 4: Add the entity/request/response field**

Entity field:

```java
@Enumerated(EnumType.STRING)
@Column(name = "anchor_mode")
private MaintenanceScheduleAnchorMode anchorMode;
```

Append `MaintenanceScheduleAnchorMode anchorMode` to the canonical `PprPlanRequest` and `PprPlanDto` records. Update convenience constructors to pass `null`, map it in `PprPlanDto.from`, copy it in `withGenerationMessage`/`withGenerationDiagnostics`, and preserve it in `requestWithScopedDepartment`.

- [ ] **Step 5: Preserve legacy defaults and support mixed builder plans**

In `applyPlanContractFields`:

```java
boolean scheduleBuilder = request.anchorMode() != null;
PprType pprType = scheduleBuilder && request.pprType() == null
        ? null
        : request.pprType() != null ? request.pprType() : PprType.PREVENTIVE_MAINTENANCE;
PprScheduleType scheduleType = scheduleBuilder && request.scheduleType() == null
        ? PprScheduleType.CALENDAR
        : request.scheduleType() != null ? request.scheduleType() : PprScheduleType.CALENDAR;

plan.setAnchorMode(request.anchorMode());
plan.setPprType(pprType);
plan.setScheduleType(scheduleType);
plan.setFrequency(scheduleBuilder && request.frequency() == null
        ? null
        : scheduleType == PprScheduleType.CALENDAR ? request.frequency() : null);
```

Retain all current validation for requests without an explicit anchor mode.

- [ ] **Step 6: Run contract and PPR request/DTO tests**

Run:

```bash
./mvnw -Dtest=PprPlanMaintenanceScheduleAnchorMigrationContractTest,PprPlanControllerContractTest,PprPlanServiceLifecycleTest test
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/toir/enums/MaintenanceScheduleAnchorMode.java \
  src/main/java/com/toir/entity/PprPlan.java \
  src/main/java/com/toir/dto/pprplanning/PprPlanRequest.java \
  src/main/java/com/toir/dto/pprplanning/PprPlanDto.java \
  src/main/java/com/toir/service/PprPlanService.java \
  src/main/java/com/toir/controller/PprPlanController.java \
  src/main/resources/db/migration/V20260727_1__ppr_plan_maintenance_schedule_anchor.sql \
  src/test/java/com/toir/migration/PprPlanMaintenanceScheduleAnchorMigrationContractTest.java
git commit -m "feat: persist maintenance schedule anchor mode"
```

---

### Task 2: Shared occurrence calculator and stateless preview

**Files:**
- Create: `src/main/java/com/toir/dto/maintenanceschedule/MaintenanceSchedulePreviewRequest.java`
- Create: `src/main/java/com/toir/dto/maintenanceschedule/MaintenanceSchedulePreviewItem.java`
- Create: `src/main/java/com/toir/dto/maintenanceschedule/MaintenanceSchedulePreviewSummary.java`
- Create: `src/main/java/com/toir/dto/maintenanceschedule/MaintenanceSchedulePreviewResponse.java`
- Create: `src/main/java/com/toir/service/maintanance/MaintenanceScheduleCalculationRequest.java`
- Create: `src/main/java/com/toir/service/maintanance/MaintenanceScheduleOccurrence.java`
- Create: `src/main/java/com/toir/service/maintanance/MaintenanceScheduleCalculationResult.java`
- Create: `src/main/java/com/toir/service/maintanance/MaintenanceScheduleService.java`
- Create: `src/main/java/com/toir/controller/maintenance/MaintenanceScheduleController.java`
- Create: `src/test/java/com/toir/service/maintanance/MaintenanceScheduleServiceTest.java`
- Create: `src/test/java/com/toir/controller/maintenance/MaintenanceScheduleControllerContractTest.java`

**Interfaces:**
- Consumes: `EquipmentMaintenanceEffectiveRuleResolver.resolveApplicable(UUID)`.
- Consumes: `MaintenanceDueCalculationService.calculate(EquipmentMaintenanceEffectiveRule)`.
- Produces:

```java
MaintenanceScheduleCalculationResult calculate(MaintenanceScheduleCalculationRequest request)
```

- `MaintenanceScheduleOccurrence` carries equipment identity, regulation/rule identity, rule metadata, `plannedDate`, and `anchorSource`.

- [ ] **Step 1: Write failing interval and anchor tests**

```java
@Test
void resetMonthlyScheduleStartsAfterOneIntervalAndIncludesEndBoundary() {
    var request = request(
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 3, 1),
            MaintenanceScheduleAnchorMode.RESET_TO_PLAN_START
    );
    stubOneEquipmentWithRule(PeriodicityUnit.MONTH, 1, null);

    var result = service.calculate(request);

    assertThat(result.occurrences())
            .extracting(MaintenanceScheduleOccurrence::plannedDate)
            .containsExactly(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 3, 1));
}

@Test
void currentScheduleAdvancesPastOverdueOccurrences() {
    stubOneEquipmentWithRule(
            PeriodicityUnit.MONTH,
            1,
            dueAt("2025-11-15T00:00:00Z")
    );

    var result = service.calculate(request(
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 3, 31),
            MaintenanceScheduleAnchorMode.CURRENT
    ));

    assertThat(result.occurrences())
            .extracting(MaintenanceScheduleOccurrence::plannedDate)
            .containsExactly(
                    LocalDate.of(2026, 1, 15),
                    LocalDate.of(2026, 2, 15),
                    LocalDate.of(2026, 3, 15)
            );
}
```

- [ ] **Step 2: Run the service test and verify failure**

Run:

```bash
./mvnw -Dtest=MaintenanceScheduleServiceTest test
```

Expected: FAIL because the calculator types do not exist.

- [ ] **Step 3: Implement request validation and date arithmetic**

Core progression:

```java
private LocalDate addPeriod(LocalDate date, PeriodicityUnit unit, int value) {
    return switch (unit) {
        case DAY -> date.plusDays(value);
        case WEEK -> date.plusWeeks(value);
        case MONTH -> date.plusMonths(value);
        case QUARTER -> date.plusMonths(value * 3L);
        case YEAR -> date.plusYears(value);
        case HOUR -> throw RestException.badRequest(
                "HOUR periodicity cannot be expanded into a date-only maintenance schedule"
        );
    };
}

private List<LocalDate> expand(LocalDate first,
                               LocalDate from,
                               LocalDate to,
                               PeriodicityUnit unit,
                               int value) {
    LocalDate cursor = first;
    while (cursor.isBefore(from)) {
        cursor = addPeriod(cursor, unit, value);
    }
    List<LocalDate> dates = new ArrayList<>();
    while (!cursor.isAfter(to)) {
        dates.add(cursor);
        cursor = addPeriod(cursor, unit, value);
    }
    return dates;
}
```

Validate positive periodicity values and skip `HOUR`/`MANUAL` rules as unmatched, rather than exposing their internal exception through preview.

- [ ] **Step 4: Resolve equipment and effective rules**

Use `equipmentRepository.findAllByIdInAndIsDeletedFalse` for equipment scope and `findAllByEquipmentTypeIdInAndIsDeletedFalse` or the existing active equipment list for type scope. Apply department and `OperationalEquipmentPolicy`.

For every selected equipment:

```java
for (EquipmentMaintenanceEffectiveRule rule : effectiveRuleResolver.resolveApplicable(equipment.getId())) {
    MaintenanceDueCalculationDto due = dueCalculationService.calculate(rule);
    boolean missingMeter = due.structuredExplanation() != null
            && "MISSING_ACTIVE_METER".equals(due.structuredExplanation().blockingCode());
    // count the pair once, calculate calendar dates, and emit immutable occurrences
}
```

- [ ] **Step 5: Add missing-meter, exclusions, scope, and deterministic-order tests**

```java
@Test
void missingMeterIsCountedWithoutSuppressingCalendarOccurrences() {
    stubRuleWithCalendarDueAndMissingMeter();

    var result = service.calculate(currentYearRequest());

    assertThat(result.missingMetersCount()).isEqualTo(1);
    assertThat(result.occurrences()).isNotEmpty();
}

@Test
void manualAndHourRulesDoNotCreateDateOccurrences() {
    stubManualAndHourRules();

    var result = service.calculate(currentYearRequest());

    assertThat(result.occurrences()).isEmpty();
    assertThat(result.unmatchedCount()).isEqualTo(1);
}
```

- [ ] **Step 6: Write failing preview controller tests**

```java
@Test
void previewReturnsStatelessOccurrenceResponse() throws Exception {
    when(scheduleService.calculate(any())).thenReturn(calculationResult());

    mockMvc.perform(post("/api/v1/maintenance-schedule/preview")
            .contentType(MediaType.APPLICATION_JSON)
            .content(validRequestJson()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].plannedDate").value("2026-02-01"))
        .andExpect(jsonPath("$.summary.totalOccurrences").value(1));
}

@Test
void previewRejectsEquipmentScopeWithoutEquipmentIds() throws Exception {
    mockMvc.perform(post("/api/v1/maintenance-schedule/preview")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"fromDate":"2026-01-01","toDate":"2026-12-31",
                 "scopeType":"EQUIPMENT","anchorMode":"CURRENT"}
                """))
        .andExpect(status().isBadRequest());
}
```

- [ ] **Step 7: Implement DTO mapping, scope enforcement, and authorization**

Controller signature:

```java
@PostMapping("/preview")
@PreAuthorize("hasAnyAuthority('PPR_PLAN_GENERATE','PPR_PLAN_CREATE','SYSTEM_ADMIN','*')")
public ResponseEntity<MaintenanceSchedulePreviewResponse> preview(
        @Valid @RequestBody MaintenanceSchedulePreviewRequest request) {
    UUID departmentId = scopeAccessService.enforceDepartmentScope(request.departmentId());
    var result = scheduleService.calculate(request.toCalculationRequest(departmentId));
    return ResponseEntity.ok(MaintenanceSchedulePreviewResponse.from(result));
}
```

The service method is `@Transactional(readOnly = true)` and must not call a save method.

- [ ] **Step 8: Run preview and calculator tests**

Run:

```bash
./mvnw -Dtest=MaintenanceScheduleServiceTest,MaintenanceScheduleControllerContractTest test
```

Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/com/toir/dto/maintenanceschedule \
  src/main/java/com/toir/service/maintanance/MaintenanceSchedule* \
  src/main/java/com/toir/controller/maintenance/MaintenanceScheduleController.java \
  src/test/java/com/toir/service/maintanance/MaintenanceScheduleServiceTest.java \
  src/test/java/com/toir/controller/maintenance/MaintenanceScheduleControllerContractTest.java
git commit -m "feat: preview annual maintenance occurrences"
```

---

### Task 3: Generate occurrence-based PPR tasks from builder plans

**Files:**
- Modify: `src/main/java/com/toir/service/PprGeneratorService.java`
- Modify: `src/test/java/com/toir/service/PprGeneratorServiceLifecycleTest.java`
- Modify: `src/test/java/com/toir/service/PprGeneratorDynamicConditionTest.java`
- Modify: `src/main/java/com/toir/dto/pprplanning/PprTaskDto.java` only if existing fields do not expose the generated dates.

**Interfaces:**
- Consumes: `MaintenanceScheduleService.calculate(MaintenanceScheduleCalculationRequest)`.
- Consumes: explicit `PprPlan.anchorMode`.
- Produces: one `PprTask` per `MaintenanceScheduleOccurrence`.

- [ ] **Step 1: Write a failing multiple-occurrence generation test**

```java
@Test
void explicitBuilderPlanGeneratesEveryPreviewOccurrence() {
    PprPlan plan = explicitBuilderPlan(
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 3, 31),
            MaintenanceScheduleAnchorMode.RESET_TO_PLAN_START
    );
    when(scheduleService.calculate(any())).thenReturn(resultWithDates(
            LocalDate.of(2026, 2, 1),
            LocalDate.of(2026, 3, 1)
    ));
    stubNoExistingTasks(plan);

    var result = service.generateForPlan(plan.getId());

    assertThat(result.created()).isEqualTo(2);
    ArgumentCaptor<PprTask> tasks = ArgumentCaptor.forClass(PprTask.class);
    verify(taskRepository, times(2)).save(tasks.capture());
    assertThat(tasks.getAllValues())
            .extracting(task -> task.getScheduledStart().toLocalDate())
            .containsExactly(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 3, 1));
}
```

- [ ] **Step 2: Run the generator test and verify failure**

Run:

```bash
./mvnw -Dtest=PprGeneratorServiceLifecycleTest#explicitBuilderPlanGeneratesEveryPreviewOccurrence test
```

Expected: FAIL because explicit-anchor plans still use legacy generation.

- [ ] **Step 3: Add the builder branch**

At the start of valid generation:

```java
if (plan.getAnchorMode() != null) {
    return generateScheduleBuilderPlan(plan, planStart, planEnd);
}
return generateForPlanFixed(plan, planStart, planEnd);
```

Build calculation scope from `PprPlanTarget` equipment/equipment-type targets and the stored department. Reject a builder plan with neither equipment nor equipment-type targets.

- [ ] **Step 4: Map occurrences to tasks**

```java
private PprTask saveGeneratedOccurrence(PprPlan plan,
                                        MaintenanceScheduleOccurrence occurrence,
                                        String code) {
    LocalDate plannedDate = occurrence.plannedDate();
    PprTask task = new PprTask();
    task.setCode(code);
    task.setPlan(plan);
    task.setRegulationId(occurrence.regulationId());
    task.setEquipmentMaintenanceRuleId(occurrence.equipmentMaintenanceRuleId());
    task.setEquipmentId(occurrence.equipmentId());
    task.setTitle(occurrence.regulationName() + " — " + occurrence.equipmentCode());
    task.setScheduledStart(plannedDate.atTime(9, 0));
    task.setScheduledEnd(resolveOccurrenceEnd(plannedDate, occurrence.normativeLaborHours(), plan.getEndDate()));
    task.setDueDate(plannedDate.atTime(18, 0));
    task.setPlannedLaborHours(occurrence.normativeLaborHours());
    task.setPriority(PriorityLevel.MEDIUM);
    task.setStatus(PprTaskStatus.PLANNED);
    plan.getTasks().add(task);
    return taskRepository.save(task);
}
```

- [ ] **Step 5: Make duplicate signatures occurrence-aware**

```java
private record TaskSignature(
        UUID planId,
        UUID regulationId,
        UUID equipmentMaintenanceRuleId,
        UUID equipmentId,
        LocalDate plannedDate
) {}
```

Use `task.getScheduledStart().toLocalDate()` when reading existing tasks and `occurrence.plannedDate()` for candidates.

- [ ] **Step 6: Test repeat generation and legacy preservation**

```java
@Test
void repeatedBuilderGenerationSkipsOnlyExistingOccurrenceDates() {
    // existing February occurrence; calculator returns February and March
    // expect one March task and one duplicate skip
}

@Test
void planWithoutAnchorModeStillUsesLegacySingleTaskGeneration() {
    // existing monthly regulation and three-month plan
    // expect current one-task behavior
}
```

Run:

```bash
./mvnw -Dtest=PprGeneratorServiceLifecycleTest,PprGeneratorDynamicConditionTest test
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/toir/service/PprGeneratorService.java \
  src/test/java/com/toir/service/PprGeneratorServiceLifecycleTest.java \
  src/test/java/com/toir/service/PprGeneratorDynamicConditionTest.java
git commit -m "feat: generate PPR tasks from schedule occurrences"
```

---

### Task 4: Enforce PPR plan visibility by caller authority

**Files:**
- Create: `src/main/java/com/toir/security/PprPlanVisibilityPolicy.java`
- Create: `src/test/java/com/toir/security/PprPlanVisibilityPolicyTest.java`
- Modify: `src/main/java/com/toir/controller/PprPlanController.java`
- Modify: `src/main/java/com/toir/service/PprPlanService.java`
- Modify: `src/main/java/com/toir/service/PprTaskQueryService.java`
- Modify: `src/main/java/com/toir/repository/PprPlanRepository.java`
- Modify: `src/main/java/com/toir/repository/PprTaskRepository.java`
- Modify: `src/test/java/com/toir/controller/PprPlanControllerContractTest.java`
- Modify: `src/test/java/com/toir/service/PprPlanServiceListFilterTest.java`
- Modify: `src/test/java/com/toir/security/PprPlanEndpointSecurityTest.java`

**Interfaces:**
- Produces:

```java
Set<PlanStatus> allowedStatuses();
Set<PlanStatus> visibleStatuses(PlanStatus status, Collection<PlanStatus> statuses);
boolean canView(PlanStatus status);
```

- Plan and task repository searches consume a non-empty `Collection<PlanStatus> visiblePlanStatuses`.

- [ ] **Step 1: Write failing policy tests**

```java
@Test
void ordinaryReaderCannotViewDraftOrGenerated() {
    authenticate("PPR_PLAN_READ");

    assertThat(policy.allowedStatuses()).containsExactlyInAnyOrder(
            PlanStatus.APPROVED,
            PlanStatus.IN_PROGRESS,
            PlanStatus.CLOSED,
            PlanStatus.CANCELLED
    );
}

@Test
void generatorAndApproverCanViewEveryStatus() {
    authenticate("PPR_PLAN_GENERATE");
    assertThat(policy.allowedStatuses()).containsExactlyInAnyOrder(PlanStatus.values());
}
```

- [ ] **Step 2: Run policy tests and verify failure**

Run:

```bash
./mvnw -Dtest=PprPlanVisibilityPolicyTest test
```

Expected: FAIL because the policy does not exist.

- [ ] **Step 3: Implement the authority policy**

```java
private static final Set<PlanStatus> WORKER_STATUSES = EnumSet.of(
        PlanStatus.APPROVED,
        PlanStatus.IN_PROGRESS,
        PlanStatus.CLOSED,
        PlanStatus.CANCELLED
);

public Set<PlanStatus> allowedStatuses() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    boolean mechanic = authentication != null && authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .anyMatch(Set.of("PPR_PLAN_GENERATE", "PPR_PLAN_APPROVE", "SYSTEM_ADMIN", "*")::contains);
    return mechanic ? EnumSet.allOf(PlanStatus.class) : EnumSet.copyOf(WORKER_STATUSES);
}
```

Intersect any requested singular/repeated statuses with the allowed set.

- [ ] **Step 4: Add repository-enforced plan status filters**

Add:

```sql
AND p.status IN (:visiblePlanStatuses)
```

to `searchPlans`, count queries, task searches, task counts, and PPR stats. Always pass a non-empty set from the policy; return an empty page/list before querying if a requested set has no visible status.

- [ ] **Step 5: Wire list query parameters and parent-plan task filtering**

Controller parameters:

```java
@RequestParam(required = false) PlanStatus status,
@RequestParam(required = false) List<PlanStatus> statuses
```

Pass `visibilityPolicy.visibleStatuses(status, statuses)` to plan service overloads. Keep existing task `PprTaskStatus status` and pass `visibilityPolicy.allowedStatuses()` as a separate parent-plan filter.

- [ ] **Step 6: Protect detail, child collection, and stats**

Before returning a plan detail or `/{id}/tasks`:

```java
if (!visibilityPolicy.canView(plan.getStatus())) {
    throw RestException.notFound("PPR plan not found: " + plan.getId());
}
```

Use the same visible status set in `/stats`, `/tasks/stats`, and overdue counts.

- [ ] **Step 7: Add controller/service regression tests**

```java
@Test
void workerRequestedDraftStatusReturnsNoPlans() {
    authenticate("PPR_PLAN_READ");
    var statuses = policy.visibleStatuses(PlanStatus.DRAFT, List.of());
    assertThat(statuses).isEmpty();
}

@Test
void workerTaskQueryPassesOnlyPublicParentPlanStatuses() {
    service.findTasks(null, null, null, false, WORKER_STATUSES, 0, 20);
    verify(pprTaskQueryService).findTasks(
            null, null, null, false, WORKER_STATUSES, PageRequest.of(0, 20)
    );
}
```

- [ ] **Step 8: Run visibility tests**

Run:

```bash
./mvnw -Dtest=PprPlanVisibilityPolicyTest,PprPlanControllerContractTest,PprPlanServiceListFilterTest,PprPlanEndpointSecurityTest test
```

Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/com/toir/security/PprPlanVisibilityPolicy.java \
  src/main/java/com/toir/controller/PprPlanController.java \
  src/main/java/com/toir/service/PprPlanService.java \
  src/main/java/com/toir/service/PprTaskQueryService.java \
  src/main/java/com/toir/repository/PprPlanRepository.java \
  src/main/java/com/toir/repository/PprTaskRepository.java \
  src/test/java/com/toir/security/PprPlanVisibilityPolicyTest.java \
  src/test/java/com/toir/controller/PprPlanControllerContractTest.java \
  src/test/java/com/toir/service/PprPlanServiceListFilterTest.java \
  src/test/java/com/toir/security/PprPlanEndpointSecurityTest.java
git commit -m "fix: hide unapproved PPR plans from workers"
```

---

### Task 5: Cross-feature parity and full verification

**Files:**
- Modify: tests from Tasks 1–4 if verification exposes contract gaps.
- Modify: `docs/superpowers/specs/2026-07-27-maintenance-schedule-builder-design.md` only if an implementation constraint must be documented.

**Interfaces:**
- Preview and builder generation consume identical `MaintenanceScheduleCalculationRequest` values for dates, scope, department, and anchor mode.
- No preview persistence APIs exist.

- [ ] **Step 1: Add a parity test**

```java
@Test
void previewDatesEqualGeneratedTaskDatesForSameInputs() {
    MaintenanceScheduleCalculationResult calculation = scheduleService.calculate(request);
    PprGeneratorService.GenerationResult generation = generator.generateForPlan(plan.getId());

    assertThat(savedTasks)
            .extracting(task -> task.getScheduledStart().toLocalDate())
            .containsExactlyElementsOf(
                    calculation.occurrences().stream()
                            .map(MaintenanceScheduleOccurrence::plannedDate)
                            .toList()
            );
    assertThat(generation.created()).isEqualTo(calculation.occurrences().size());
}
```

- [ ] **Step 2: Run the focused suite**

Run:

```bash
./mvnw -Dtest=PprPlanMaintenanceScheduleAnchorMigrationContractTest,MaintenanceScheduleServiceTest,MaintenanceScheduleControllerContractTest,PprGeneratorServiceLifecycleTest,PprGeneratorDynamicConditionTest,PprPlanVisibilityPolicyTest,PprPlanControllerContractTest,PprPlanServiceListFilterTest,PprPlanEndpointSecurityTest test
```

Expected: PASS with zero failures and errors.

- [ ] **Step 3: Run all PPR and maintenance due regressions**

Run:

```bash
./mvnw -Dtest='*Ppr*,MaintenanceDueCalculationServiceTest,EquipmentMaintenanceEffectiveRuleResolverTest,MaintenanceRegulationControllerContractTest' test
```

Expected: PASS.

- [ ] **Step 4: Run the full test suite**

Run:

```bash
./mvnw test
```

Expected: PASS, or only explicitly reported Testcontainers/Docker environment failures after all pure unit and contract suites pass.

- [ ] **Step 5: Inspect final changes**

Run:

```bash
git diff --check
git status --short
git log -5 --oneline
```

Expected: no whitespace errors; only intended files are changed; implementation commits are present.

- [ ] **Step 6: Commit any final test-only corrections**

```bash
git add src/test docs/superpowers/specs/2026-07-27-maintenance-schedule-builder-design.md
git commit -m "test: verify maintenance schedule builder parity"
```

Skip this commit if no final corrections are needed.

