# Repair Request Detail Insights Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add close-readiness, costs-summary, and timeline APIs for repair-request detail pages, verify their contracts and security, and push the completed implementation to `origin/Codex_org`.

**Architecture:** `RepairRequestController` keeps request RBAC/PBAC enforcement and delegates read-only projections to a new `RepairRequestInsightsService`. The service builds DTO records from existing actual-cost, audit, work-order, defect, warranty, meter, and user data using batched repository reads and no schema migration.

**Tech Stack:** Java 21, Spring Boot 3.3.5, Spring MVC/Security, Spring Data JPA, Jackson, JUnit 5, Mockito, AssertJ, MockMvc, Maven.

## Global Constraints

- Implement exactly `GET /api/v1/repair-requests/{id}/costs-summary`, `/close-readiness`, and `/timeline`.
- Match the `RepairRequestCostsSummary`, `RepairRequestCloseReadiness`, and `RepairRequestTimelineEvent` frontend contracts.
- Reuse the existing `REPAIR_REQUEST_READ` authority and request-level department/reporter/assignee scope checks.
- Do not change the frontend and do not add a database migration.
- Return non-null collections and preserve null for optional scalar fields.
- Avoid per-row repository lookups; all enrichment must use batch queries.
- Keep the branch `Codex_org` and push only after the relevant and full verification commands pass.

## File Map

- Create `src/main/java/com/toir/service/repair/RepairRequestInsightsService.java`: all three read-only projections and shared batch-enrichment helpers.
- Create `src/main/java/com/toir/dto/repairrequest/RepairRequestCostKind.java`: three finance kinds.
- Create `src/main/java/com/toir/dto/repairrequest/RepairRequestCostRowDto.java`: finance row contract.
- Create `src/main/java/com/toir/dto/repairrequest/RepairRequestCostsSummaryDto.java`: finance summary contract.
- Create `src/main/java/com/toir/dto/repairrequest/RepairRequestCloseReadinessItemDto.java`: blocker/warning contract.
- Create `src/main/java/com/toir/dto/repairrequest/RepairRequestCloseReadinessDto.java`: readiness response contract.
- Create `src/main/java/com/toir/dto/repairrequest/RepairRequestTimelineEventType.java`: timeline event enum.
- Create `src/main/java/com/toir/dto/repairrequest/RepairRequestTimelineEventDto.java`: timeline event contract.
- Modify `src/main/java/com/toir/repository/actualCost/ActualCostRepository.java`: one repair-request cost-scope query.
- Modify `src/main/java/com/toir/repository/AuditLogRepository.java`: chronological entity audit query.
- Modify `src/main/java/com/toir/controller/repair/RepairRequestController.java`: three secured routes and service dependency.
- Create `src/test/java/com/toir/service/repair/RepairRequestInsightsServiceTest.java`: projection unit tests.
- Modify `src/test/java/com/toir/controller/RepairRequestControllerContractTest.java`: JSON contracts.
- Modify `src/test/java/com/toir/security/RbacRepairRequestSecurityTest.java`: route authorities.
- Modify `src/test/java/com/toir/security/RepairRequestPbacScopeTest.java`: updated controller construction and scope delegation.

---

### Task 1: Costs Summary Projection

**Files:**
- Create: `src/main/java/com/toir/dto/repairrequest/RepairRequestCostKind.java`
- Create: `src/main/java/com/toir/dto/repairrequest/RepairRequestCostRowDto.java`
- Create: `src/main/java/com/toir/dto/repairrequest/RepairRequestCostsSummaryDto.java`
- Create: `src/main/java/com/toir/service/repair/RepairRequestInsightsService.java`
- Modify: `src/main/java/com/toir/repository/actualCost/ActualCostRepository.java`
- Test: `src/test/java/com/toir/service/repair/RepairRequestInsightsServiceTest.java`

**Interfaces:**
- Consumes: `ActualCost`, `ActualCostSourceType`, `ActualCostStatus`, `CostCategory`, `LaborEntry`, `RepairMaterialUsage`, `ContractorWork`, `WorkOrder`, `User`, and `SparePart`.
- Produces: `RepairRequestCostsSummaryDto getCostsSummary(RepairRequest request)`.

- [ ] **Step 1: Write failing finance projection tests**

Create the Mockito test fixture with this core aggregation test:

```java
@ExtendWith(MockitoExtension.class)
class RepairRequestInsightsServiceTest {
    @Mock ActualCostRepository actualCostRepository;
    @Mock WorkOrderRepository workOrderRepository;
    @Mock CostCategoryRepository costCategoryRepository;
    @Mock LaborEntryRepository laborEntryRepository;
    @Mock RepairMaterialUsageRepository materialUsageRepository;
    @Mock SparePartRepository sparePartRepository;
    @Mock ContractorWorkRepository contractorWorkRepository;
    @Mock DefectRepository defectRepository;
    @Mock EquipmentMeterRepository equipmentMeterRepository;
    @Mock MeterReadingRepository meterReadingRepository;
    @Mock AuditLogRepository auditLogRepository;
    @Mock UserRepository userRepository;

    RepairRequestInsightsService service;

    @BeforeEach
    void setUp() {
        service = new RepairRequestInsightsService(
                actualCostRepository, workOrderRepository, costCategoryRepository,
                laborEntryRepository, materialUsageRepository, sparePartRepository,
                contractorWorkRepository, defectRepository, equipmentMeterRepository,
                meterReadingRepository, auditLogRepository, userRepository, new ObjectMapper());
    }

    @Test
    void costsSummaryAggregatesLinkedAndDirectCostsWithoutCountingRejectedTotals() {
        UUID requestId = UUID.randomUUID();
        RepairRequest request = repairRequest(requestId, RequestStatus.COMPLETED);
        ActualCost labor = cost(ActualCostSourceType.LABOR_ENTRY, ActualCostStatus.APPROVED, 100_000);
        ActualCost material = cost(ActualCostSourceType.MATERIAL_ISSUE, ActualCostStatus.PENDING, 50_000);
        ActualCost rejected = cost(ActualCostSourceType.CONTRACTOR_WORK, ActualCostStatus.REJECTED, 70_000);
        when(actualCostRepository.findAllForRepairRequest(requestId)).thenReturn(List.of(labor, material, rejected));

        RepairRequestCostsSummaryDto result = service.getCostsSummary(request);

        assertThat(result.requestId()).isEqualTo(requestId);
        assertThat(result.currency()).isEqualTo("UZS");
        assertThat(result.laborCost()).isEqualTo(100_000);
        assertThat(result.materialCost()).isEqualTo(50_000);
        assertThat(result.contractorCost()).isZero();
        assertThat(result.totalCost()).isEqualTo(150_000);
        assertThat(result.rows()).hasSize(3);
    }
}
```

Also add tests for category-code fallback, unclassifiable-cost exclusion, label enrichment, duplicate-safe repository results, and `costDate` ordering.

- [ ] **Step 2: Run the finance tests and verify failure**

Run:

```bash
./mvnw -Dtest=RepairRequestInsightsServiceTest test
```

Expected: compilation fails because the DTOs, service, and `findAllForRepairRequest` do not exist.

- [ ] **Step 3: Add finance DTOs and repository query**

Implement these exact public contracts:

```java
public enum RepairRequestCostKind { LABOR, CONTRACTOR, MATERIAL }

public record RepairRequestCostRowDto(
        UUID id,
        RepairRequestCostKind kind,
        String sourceLabel,
        UUID workOrderId,
        String workOrderNumber,
        ActualCostStatus status,
        double amount,
        Instant costDate
) {}

public record RepairRequestCostsSummaryDto(
        UUID requestId,
        String currency,
        double totalCost,
        double laborCost,
        double contractorCost,
        double materialCost,
        List<RepairRequestCostRowDto> rows
) {
    public RepairRequestCostsSummaryDto {
        rows = rows == null ? List.of() : List.copyOf(rows);
    }
}
```

Add this repository interface method with a native query that selects distinct, non-deleted actual costs when a non-deleted linked work order has the request ID, `repair_request_id` has the request ID, or `source_type = 'REPAIR_REQUEST'` and `source_id` has the request ID:

```java
List<ActualCost> findAllForRepairRequest(@Param("repairRequestId") UUID repairRequestId);
```

- [ ] **Step 4: Implement minimal batched finance aggregation**

Implement:

```java
@Transactional(readOnly = true)
public RepairRequestCostsSummaryDto getCostsSummary(RepairRequest request)
```

Use source type before category code for kind classification. Load all referenced categories, work orders, labor entries, material usages, spare parts, contractor works, and users with `findAllByIdInAndIsDeletedFalse`. Return all mapped rows, sort by cost date descending then ID, and sum only rows whose status is not `REJECTED`.

- [ ] **Step 5: Run finance tests and verify pass**

Run:

```bash
./mvnw -Dtest=RepairRequestInsightsServiceTest test
```

Expected: all finance projection tests pass.

- [ ] **Step 6: Commit finance projection**

```bash
git add src/main/java/com/toir/dto/repairrequest/RepairRequestCostKind.java \
  src/main/java/com/toir/dto/repairrequest/RepairRequestCostRowDto.java \
  src/main/java/com/toir/dto/repairrequest/RepairRequestCostsSummaryDto.java \
  src/main/java/com/toir/service/repair/RepairRequestInsightsService.java \
  src/main/java/com/toir/repository/actualCost/ActualCostRepository.java \
  src/test/java/com/toir/service/repair/RepairRequestInsightsServiceTest.java
git commit -m "feat: aggregate repair request costs"
```

---

### Task 2: Close Readiness Projection

**Files:**
- Create: `src/main/java/com/toir/dto/repairrequest/RepairRequestCloseReadinessItemDto.java`
- Create: `src/main/java/com/toir/dto/repairrequest/RepairRequestCloseReadinessDto.java`
- Modify: `src/main/java/com/toir/service/repair/RepairRequestInsightsService.java`
- Test: `src/test/java/com/toir/service/repair/RepairRequestInsightsServiceTest.java`

**Interfaces:**
- Consumes: linked work orders, linked defects, active equipment meters, and request meter readings.
- Produces: `RepairRequestCloseReadinessDto getCloseReadiness(RepairRequest request)`.

- [ ] **Step 1: Write failing readiness tests**

Add tests that assert the exact codes and groups:

```java
@Test
void closeReadinessBlocksOpenRequestAndOpenLinkedRecordsButOnlyWarnsForWarrantySlaAndMeters() {
    RepairRequest request = repairRequest(UUID.randomUUID(), RequestStatus.IN_PROGRESS);
    request.setWarrantyActiveAtCreation(true);
    request.setTargetCompletionAt(Instant.now().minusSeconds(60));
    when(workOrderRepository.findAllByRepairRequestIdAndIsDeletedFalseOrderByUpdatedAtDesc(request.getId()))
            .thenReturn(List.of(workOrder(WorkOrderStatus.IN_PROGRESS)));
    when(defectRepository.findAllByRepairRequestIdAndIsDeletedFalseOrderByUpdatedAtDesc(request.getId()))
            .thenReturn(List.of(defect(DefectStatus.OPEN)));
    when(equipmentMeterRepository.findAllByEquipmentIdAndActiveTrueAndIsDeletedFalse(request.getEquipmentId()))
            .thenReturn(List.of(equipmentMeter()));
    when(meterReadingRepository.findAllByRepairRequestIdAndIsDeletedFalseOrderByReadAtDesc(request.getId()))
            .thenReturn(List.of());

    RepairRequestCloseReadinessDto result = service.getCloseReadiness(request);

    assertThat(result.ready()).isFalse();
    assertThat(result.blockers()).extracting(RepairRequestCloseReadinessItemDto::code)
            .containsExactly("REQUEST_NOT_COMPLETED", "OPEN_WORK_ORDERS", "OPEN_DEFECTS");
    assertThat(result.warnings()).extracting(RepairRequestCloseReadinessItemDto::code)
            .containsExactly("WARRANTY_DECISION_PENDING", "METER_READINGS_MISSING", "TARGET_COMPLETION_OVERDUE");
    assertThat(result.groups()).containsEntry("workOrders", CloseReadinessGroupStatus.BLOCKED)
            .containsEntry("defects", CloseReadinessGroupStatus.BLOCKED)
            .containsEntry("warranty", CloseReadinessGroupStatus.WARNING)
            .containsEntry("meterReadings", CloseReadinessGroupStatus.WARNING)
            .containsEntry("sla", CloseReadinessGroupStatus.WARNING);
}
```

Add tests for missing work orders, fully ready state, warning-not-overwriting-blocked precedence, actual completion suppressing overdue, and `reactionOverdue == false` with the current SLA model.

- [ ] **Step 2: Run readiness tests and verify failure**

Run:

```bash
./mvnw -Dtest=RepairRequestInsightsServiceTest test
```

Expected: compilation fails because readiness DTOs and method do not exist.

- [ ] **Step 3: Add readiness DTOs**

```java
public record RepairRequestCloseReadinessItemDto(
        String code,
        String message,
        CloseReadinessSeverity severity,
        String group,
        String targetTab
) {}

public record RepairRequestCloseReadinessDto(
        UUID repairRequestId,
        RequestStatus status,
        boolean ready,
        Instant checkedAt,
        boolean isOverdue,
        boolean reactionOverdue,
        List<RepairRequestCloseReadinessItemDto> blockers,
        List<RepairRequestCloseReadinessItemDto> warnings,
        Map<String, CloseReadinessGroupStatus> groups
) {
    public RepairRequestCloseReadinessDto {
        blockers = blockers == null ? List.of() : List.copyOf(blockers);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        groups = groups == null ? Map.of() : Map.copyOf(groups);
    }
}
```

- [ ] **Step 4: Implement deterministic readiness rules**

Implement the blocking and warning codes from Step 1. Use terminal status sets from the design, initialize a `LinkedHashMap` in frontend group order, ensure blockers win over warnings, compute `ready` strictly from `blockers.isEmpty()`, and use one captured `Instant checkedAt` for all time comparisons and the response.

- [ ] **Step 5: Run readiness tests and verify pass**

Run:

```bash
./mvnw -Dtest=RepairRequestInsightsServiceTest test
```

Expected: finance and readiness tests pass.

- [ ] **Step 6: Commit readiness projection**

```bash
git add src/main/java/com/toir/dto/repairrequest/RepairRequestCloseReadinessItemDto.java \
  src/main/java/com/toir/dto/repairrequest/RepairRequestCloseReadinessDto.java \
  src/main/java/com/toir/service/repair/RepairRequestInsightsService.java \
  src/test/java/com/toir/service/repair/RepairRequestInsightsServiceTest.java
git commit -m "feat: report repair request close readiness"
```

---

### Task 3: Curated Timeline Projection

**Files:**
- Create: `src/main/java/com/toir/dto/repairrequest/RepairRequestTimelineEventType.java`
- Create: `src/main/java/com/toir/dto/repairrequest/RepairRequestTimelineEventDto.java`
- Modify: `src/main/java/com/toir/repository/AuditLogRepository.java`
- Modify: `src/main/java/com/toir/service/repair/RepairRequestInsightsService.java`
- Test: `src/test/java/com/toir/service/repair/RepairRequestInsightsServiceTest.java`

**Interfaces:**
- Consumes: chronological repair-request audits, linked defects/work orders, request meter readings, equipment meters, and users.
- Produces: `List<RepairRequestTimelineEventDto> getTimeline(RepairRequest request)`.

- [ ] **Step 1: Write failing timeline tests**

Add this core timeline-ordering test:

```java
@Test
void timelineCuratesAuditLinksMetersActorsAndStatusTransitionsOldestFirst() throws Exception {
    RepairRequest request = repairRequest(UUID.randomUUID(), RequestStatus.CLOSED);
    request.setCreatedAt(Instant.parse("2026-07-01T09:00:00Z"));
    AuditLog assigned = audit("Заявка назначена исполнителю", AuditAction.UPDATE,
            "{\"status\":\"ASSIGNED\"}", Instant.parse("2026-07-01T10:00:00Z"));
    AuditLog closed = audit("Закрыта заявка", AuditAction.CLOSE,
            "{\"status\":\"CLOSED\"}", Instant.parse("2026-07-02T10:00:00Z"));
    when(auditLogRepository.findRepairRequestTimelineAudits(request.getId().toString()))
            .thenReturn(List.of(assigned, closed));

    List<RepairRequestTimelineEventDto> result = service.getTimeline(request);

    assertThat(result).extracting(RepairRequestTimelineEventDto::type)
            .containsSubsequence(RepairRequestTimelineEventType.CREATED,
                    RepairRequestTimelineEventType.ASSIGNED,
                    RepairRequestTimelineEventType.CLOSED);
    assertThat(result).isSortedAccordingTo(Comparator
            .comparing(RepairRequestTimelineEventDto::occurredAt)
            .thenComparing(event -> event.id().toString()));
}
```

Add cases for clarification, warranty decision, rejection, generic admin status change with derived `fromStatus`, defect/work-order target links, meter actor attribution, user-name batch enrichment, malformed snapshots, and creation de-duplication.

- [ ] **Step 2: Run timeline tests and verify failure**

Run:

```bash
./mvnw -Dtest=RepairRequestInsightsServiceTest test
```

Expected: compilation fails because timeline DTOs, method, and audit query do not exist.

- [ ] **Step 3: Add timeline contracts and audit query**

```java
public enum RepairRequestTimelineEventType {
    CREATED, STATUS_CHANGE, ASSIGNED, CLARIFICATION_REQUESTED,
    WARRANTY_DECISION, DEFECT_LINKED, WORK_ORDER_LINKED,
    METER_READING, REJECTED, CLOSED
}

public record RepairRequestTimelineEventDto(
        UUID id,
        RepairRequestTimelineEventType type,
        Instant occurredAt,
        UUID actorId,
        String actorName,
        String fromStatus,
        String toStatus,
        String message,
        String targetType,
        UUID targetId
) {}
```

Add:

```java
List<AuditLog> findRepairRequestTimelineAudits(@Param("entityId") String entityId);
```

Its query must match non-deleted rows with case-insensitive `entity_type = 'repair_request'`, exact entity ID, and order by `created_at ASC, id ASC`.

- [ ] **Step 4: Implement curated timeline merge**

Parse `currentSnapshot` with Jackson `JsonNode`; malformed/null snapshots yield no status rather than an exception. Classify specialized audit messages/actions before generic status changes. Add deterministic synthetic UUIDs with `UUID.nameUUIDFromBytes` for request creation and current linked/meter records. Resolve all actor names in one `UserRepository.findAllByIdInAndIsDeletedFalse` call, remove duplicate semantic events, and sort by occurrence then ID.

- [ ] **Step 5: Run timeline tests and verify pass**

Run:

```bash
./mvnw -Dtest=RepairRequestInsightsServiceTest test
```

Expected: all insights service tests pass.

- [ ] **Step 6: Commit timeline projection**

```bash
git add src/main/java/com/toir/dto/repairrequest/RepairRequestTimelineEventType.java \
  src/main/java/com/toir/dto/repairrequest/RepairRequestTimelineEventDto.java \
  src/main/java/com/toir/repository/AuditLogRepository.java \
  src/main/java/com/toir/service/repair/RepairRequestInsightsService.java \
  src/test/java/com/toir/service/repair/RepairRequestInsightsServiceTest.java
git commit -m "feat: add repair request timeline"
```

---

### Task 4: Secured Controller Contracts

**Files:**
- Modify: `src/main/java/com/toir/controller/repair/RepairRequestController.java`
- Modify: `src/test/java/com/toir/controller/RepairRequestControllerContractTest.java`
- Modify: `src/test/java/com/toir/security/RbacRepairRequestSecurityTest.java`
- Modify: `src/test/java/com/toir/security/RepairRequestPbacScopeTest.java`

**Interfaces:**
- Consumes: all three `RepairRequestInsightsService` methods.
- Produces: the three HTTP GET contracts under `/api/v1/repair-requests/{id}`.

- [ ] **Step 1: Write failing MockMvc contract and security tests**

Add JSON assertions for every required field, including enum strings and null optional values. Add one parameterized RBAC test covering each path:

```java
@ParameterizedTest
@ValueSource(strings = {"close-readiness", "costs-summary", "timeline"})
@WithMockUser(authorities = "REPAIR_REQUEST_READ")
void repairRequestReaderCanReadInsights(String suffix) throws Exception {
    mockMvc.perform(get("/api/v1/repair-requests/{id}/" + suffix, requestId))
            .andExpect(status().isOk());
}
```

Add unauthenticated and unrelated-authority cases. Update standalone/controller constructors to supply the mocked insights service. Add a PBAC case proving a denied request never delegates to the insights service.

- [ ] **Step 2: Run controller tests and verify failure**

Run:

```bash
./mvnw -Dtest=RepairRequestControllerContractTest,RbacRepairRequestSecurityTest,RepairRequestPbacScopeTest test
```

Expected: tests fail with 404 or compilation errors because the routes and constructor dependency do not exist.

- [ ] **Step 3: Add controller routes**

Inject `RepairRequestInsightsService` and implement each route using this shape:

```java
@GetMapping("/{id}/costs-summary")
@PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('REPAIR_REQUEST_READ')")
public ResponseEntity<RepairRequestCostsSummaryDto> costsSummary(@PathVariable UUID id) {
    RepairRequest request = requestOrThrow(id);
    assertCanReadRequest(request);
    return ResponseEntity.ok(insightsService.getCostsSummary(request));
}
```

Repeat for `close-readiness` and `timeline` with their exact response types. Do not add broader finance or audit authorities because the frontend enables these calls from repair-request read access.

- [ ] **Step 4: Run controller tests and verify pass**

Run:

```bash
./mvnw -Dtest=RepairRequestControllerContractTest,RbacRepairRequestSecurityTest,RepairRequestPbacScopeTest test
```

Expected: all selected controller/security tests pass.

- [ ] **Step 5: Commit API routes**

```bash
git add src/main/java/com/toir/controller/repair/RepairRequestController.java \
  src/test/java/com/toir/controller/RepairRequestControllerContractTest.java \
  src/test/java/com/toir/security/RbacRepairRequestSecurityTest.java \
  src/test/java/com/toir/security/RepairRequestPbacScopeTest.java
git commit -m "feat: expose repair request detail insights"
```

---

### Task 5: Regression Verification and Push

**Files:**
- Modify only files required by failures directly caused by Tasks 1-4.

**Interfaces:**
- Consumes: committed endpoint implementation.
- Produces: verified `Codex_org` branch on `origin`.

- [ ] **Step 1: Run formatting and diff checks**

```bash
git diff --check
git status --short
```

Expected: no whitespace errors; only the implementation plan may remain uncommitted if it was not committed earlier.

- [ ] **Step 2: Run targeted insight tests together**

```bash
./mvnw -Dtest=RepairRequestInsightsServiceTest,RepairRequestControllerContractTest,RbacRepairRequestSecurityTest,RepairRequestPbacScopeTest test
```

Expected: `BUILD SUCCESS` with zero failures and errors.

- [ ] **Step 3: Run the full Maven test suite**

```bash
./mvnw test
```

Expected: `BUILD SUCCESS`. If an unrelated environment-dependent test fails, record the exact test and failure, then still run `./mvnw -DskipTests package` to prove production compilation before deciding whether pushing is safe.

- [ ] **Step 4: Verify frontend contract names against the active frontend**

```bash
rg -n "RepairRequestCloseReadiness|RepairRequestCostsSummary|RepairRequestTimelineEvent|getRepairRequestCloseReadiness|getRepairRequestCostsSummary|getRepairRequestTimeline" \
  ../toir-front/src/types/api.ts ../toir-front/src/lib/api.ts
```

Expected: paths and JSON property names match the implemented controller and DTOs.

- [ ] **Step 5: Commit the implementation plan and any verified follow-up**

```bash
git add -f docs/superpowers/plans/2026-07-10-repair-request-detail-insights.md
git commit -m "docs: add repair request insights implementation plan"
```

If there are no follow-up source changes, commit only the plan. Confirm `git status --short` is empty afterward.

- [ ] **Step 6: Push the approved branch**

```bash
git push origin Codex_org
```

Expected: the remote `Codex_org` branch advances to the final local commit without force-push.
