# TOiR Stage 3 Repair Campaign Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn Repair Campaign into a versioned operational aggregate integrated with canonical Planned Shutdown, Work Order, Defect, PPR, Approval, WMS material, Actual Cost, and Budget facts without duplicating their source-of-truth data.

**Architecture:** Repair Campaign owns prioritization, stages, dependencies, resources, budget intent, progress policy, and closure narrative. Planned Shutdown continues to own windows, safe-state, isolation, actual downtime, and production return; Work Order owns execution; WMS owns reservations; Approval owns decisions; Actual Cost/Budget owns money. Integration uses explicit join rows, canonical work-item identities, pessimistic aggregate locks plus optimistic versions, durable idempotency commands, and a transactional outbox whose consumers suspend/block affected future work without rewriting historical actual facts.

**Tech Stack:** Java 24 runtime / Java 21 source, Spring Boot 3.3.5, Spring Data JPA, PostgreSQL 17/Flyway, JUnit 5/Mockito/MockMvc, React 19, TypeScript 6, TanStack Query/Table, Vitest 4, Vite 8.

## Global Constraints

- Work directly on `Codex_org`; preserve unrelated changes and stage only named files.
- Every behavior change starts RED, receives the minimum GREEN implementation, and is committed before the next task.
- Use forward-only migrations `V20260712_1` through `V20260712_11`; after a task commits a migration, later tasks add a new migration and never modify it.
- Never fabricate historical approval, reservation, safety, cost, source, schedule, or actual-time evidence. Legacy rows remain non-startable or require explicit remediation.
- Every campaign mutation loads `RepairCampaign` with a pessimistic write lock and checks request `version`; database constraints remain final concurrency authority.
- `PlannedShutdown` owns shutdown windows, actual downtime, safe-state, isolation, startup, and production return. Campaign views reference those facts.
- Campaign approval never substitutes for Planned Shutdown production/HSE approval; shutdown approval never substitutes for campaign approval.
- Campaign material demand becomes executable only through canonical Work Order requirements. Campaign views never create a second WMS reservation.
- Monetary and quantity boundaries use `BigDecimal` / `numeric(19,4)` and decimal-string JSON; no new `double`, floating-point aggregation, or JavaScript number coercion.
- Every transition, approval-invalidating mutation, link/unlink, generation, override, defect proposal, outbox application, and closure writes actor, timestamp, version, correlation key, and before/after evidence.
- Stable blocker codes are sorted by code/entity identity and returned as typed 400/409 payloads; unauthorized access remains 403.
- The supplied audit defines RC-01 through RC-15, not RC-16 through RC-18. Do not invent audit IDs. Security/audit/archive requirements are mapped to the Security section and X-09; cross-module behavior is mapped to X-01 through X-10.
- Diagnostics are accepted through canonical PPR or Work Order sources and inspections through `InspectionRound`. Modernization and HSE-prescription source types are not accepted as free UUIDs because no canonical aggregate exists; their aggregate creation is retained as a Stage 5 decision.

## Retained Deferrals

- **Stage 4:** polished cross-module reporting read models, full Materials/Resources/Schedule/Risks/Files/Closure/Dashboard UX, Playwright end-to-end flows, broad analytics, and remediation of the repository-wide frontend test/lint baseline.
- **Stage 5:** PS-11 configurable derived risk, PS-12 overrun detector/reason taxonomy/escalation notifications, campaign risk engine, critical-path calculation, lessons-learned/repeated-defect analytics, advanced dashboards, and any new Modernization or HSE Prescription source aggregate.
- Stage 3 still persists canonical IDs and immutable facts needed by these later stages; deferral does not permit duplicate data or placeholder fields.

---

### Task 1: Campaign lifecycle metadata and forward-only foundation

**Files:**
- Create: `src/main/resources/db/migration/V20260712_1__repair_campaign_core.sql`
- Create: `src/main/java/com/toir/enums/RepairCampaignPriority.java`
- Create: `src/main/java/com/toir/entity/repair/RepairCampaignStatusHistory.java`
- Create: `src/main/java/com/toir/repository/repair/RepairCampaignStatusHistoryRepository.java`
- Modify: `src/main/java/com/toir/entity/repair/RepairCampaign.java`
- Modify: `src/main/java/com/toir/enums/RepairCampaignStatus.java`
- Modify: `src/main/java/com/toir/dto/repaircampaign/RepairCampaignRequest.java`
- Modify: `src/main/java/com/toir/dto/repaircampaign/RepairCampaignDto.java`
- Test: `src/test/java/com/toir/migration/RepairCampaignCoreMigrationContractTest.java`
- Test: `src/test/java/com/toir/service/RepairCampaignServiceTest.java`

**Interfaces:**
- Produces statuses `DRAFT, SCOPE_FORMATION, RESOURCE_CHECK, PENDING_APPROVAL, APPROVED, PREPARATION, IN_PROGRESS, SUSPENDED, COMPLETED, CLOSING, CLOSED, CANCELLED`.
- Produces root fields `campaignType`, `responsibleEmployeeId`, `priority`, `objective`, `approvalScopeVersion`, `approvalScopeHash`, lifecycle timestamps, `suspendedFromStatus`, `closureVersion`, and optimistic `version`.
- Produces `RepairCampaignStatusHistory(fromStatus,toStatus,actorId,reason,scopeVersion,windowVersion,correlationKey,occurredAt)`.

- [ ] **Step 1: Write the RED migration and service contracts**

```java
@Test
void migrationAddsDedicatedLifecycleMetadataAndConservativeBackfill() {
    assertThat(sql).contains("RESOURCE_CHECK", "PENDING_APPROVAL", "SUSPENDED", "CLOSING");
    assertThat(sql).contains("responsible_employee_id", "approval_scope_hash", "repair_campaign_status_history");
    assertThat(sql).contains("LEGACY_REMEDIATION_REQUIRED");
}

@Test
void createRequiresCurrentDepartmentResponsibleEmployeeAndServerCode() {
    assertThatThrownBy(() -> service.create(requestWithForeignOwner()))
        .isInstanceOf(AccessDeniedException.class);
}
```

- [ ] **Step 2: Run RED**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 24) ./mvnw -Dtest=RepairCampaignCoreMigrationContractTest,RepairCampaignServiceTest test`

Expected: FAIL because the migration, statuses, metadata, and history mapping do not exist.

- [ ] **Step 3: Implement the migration and mappings**

Use `numeric(19,4)`, named constraints, `@Version`, nullable evidence fields for legacy rows, and a non-startable `SCOPE_FORMATION` backfill. Validate active unique code and `endDate >= startDate`; resolve owner through active `hr_employees` in the campaign department.

- [ ] **Step 4: Run GREEN and compile**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 24) ./mvnw -Dtest=RepairCampaignCoreMigrationContractTest,RepairCampaignServiceTest test && ./mvnw -DskipTests compile`

Expected: PASS with zero skipped tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/db/migration/V20260712_1__repair_campaign_core.sql src/main/java/com/toir/enums/RepairCampaignPriority.java src/main/java/com/toir/enums/RepairCampaignStatus.java src/main/java/com/toir/entity/repair/RepairCampaign.java src/main/java/com/toir/entity/repair/RepairCampaignStatusHistory.java src/main/java/com/toir/repository/repair/RepairCampaignStatusHistoryRepository.java src/main/java/com/toir/dto/repaircampaign/RepairCampaignRequest.java src/main/java/com/toir/dto/repaircampaign/RepairCampaignDto.java src/test/java/com/toir/migration/RepairCampaignCoreMigrationContractTest.java src/test/java/com/toir/service/RepairCampaignServiceTest.java
git commit -m "feat: add repair campaign core lifecycle schema"
```

---

### Task 2: Canonical campaign work items and source-breadth carry-forward

**Files:**
- Create: `src/main/resources/db/migration/V20260712_2__repair_campaign_work_sources.sql`
- Create: `src/main/java/com/toir/enums/RepairCampaignWorkItemSourceType.java`
- Create: `src/main/java/com/toir/enums/RepairCampaignWorkItemStatus.java`
- Create: `src/main/java/com/toir/entity/repair/RepairCampaignWorkItem.java`
- Create: `src/main/java/com/toir/repository/repair/RepairCampaignWorkItemRepository.java`
- Create: `src/main/java/com/toir/dto/repaircampaign/RepairCampaignWorkItemRequest.java`
- Create: `src/main/java/com/toir/dto/repaircampaign/RepairCampaignWorkItemResponse.java`
- Create: `src/main/java/com/toir/service/repair/CanonicalWorkSourceResolver.java`
- Create: `src/main/java/com/toir/service/repair/RepairCampaignWorkItemService.java`
- Modify: `src/main/java/com/toir/enums/PlannedShutdownWorkItemSourceType.java`
- Modify: `src/main/java/com/toir/service/PlannedShutdownService.java`
- Modify: `src/main/java/com/toir/controller/repair/RepairCampaignController.java`
- Test: `src/test/java/com/toir/migration/RepairCampaignWorkSourceMigrationContractTest.java`
- Test: `src/test/java/com/toir/service/repair/CanonicalWorkSourceResolverTest.java`
- Test: `src/test/java/com/toir/service/repair/RepairCampaignWorkItemServiceTest.java`
- Test: `src/test/java/com/toir/service/PlannedShutdownServiceTest.java`

**Interfaces:**
- `RepairCampaignWorkItemSourceType`: `MANUAL, DEFECT, PPR, REPAIR_REQUEST, INSPECTION_ROUND, WORK_ORDER`.
- `CanonicalWorkSourceResolver.resolve(type, sourceId)` returns `CanonicalWorkSource(UUID sourceId, UUID equipmentId, String title)` and rejects deleted, foreign-department, mismatched-equipment, and unsupported sources.
- REST: `GET/POST /repair-campaigns/{id}/work-items`, `PUT/DELETE /repair-campaigns/{id}/work-items/{itemId}`, and `PUT /repair-campaigns/{id}/work-items/order`.

- [ ] **Step 1: Write RED source identity tests**

```java
@ParameterizedTest
@EnumSource(value = RepairCampaignWorkItemSourceType.class, names = {"DEFECT","PPR","REPAIR_REQUEST","INSPECTION_ROUND","WORK_ORDER"})
void canonicalSourcesMustExistAndResolveToCampaignScope(RepairCampaignWorkItemSourceType type) {
    assertThatThrownBy(() -> service.add(campaignId, request(type, missingId)))
        .isInstanceOf(RestException.class);
}

@Test
void activeSourceIdentityAndOrderAreUniqueAndFrozenAfterApproval() {
    service.add(campaignId, canonicalDefectRequest(sourceId, 1));
    assertThatThrownBy(() -> service.add(campaignId, canonicalDefectRequest(sourceId, 2)))
        .isInstanceOf(RestException.class)
        .hasMessageContaining("CAMPAIGN_WORK_SOURCE_DUPLICATE");
    campaign.setStatus(RepairCampaignStatus.APPROVED);
    assertThatThrownBy(() -> service.reorder(campaignId, List.of(itemId), version))
        .isInstanceOf(RestException.class)
        .hasMessageContaining("CAMPAIGN_SCOPE_FROZEN");
}
```

- [ ] **Step 2: Run RED**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 24) ./mvnw -Dtest=RepairCampaignWorkSourceMigrationContractTest,CanonicalWorkSourceResolverTest,RepairCampaignWorkItemServiceTest,PlannedShutdownServiceTest test`

Expected: FAIL because work-item schema/resolver/endpoints and new Planned Shutdown sources are absent.

- [ ] **Step 3: Implement canonical identity**

Require `sourceId == null` only for `MANUAL`; use named partial unique indexes for source and order. Extend Planned Shutdown only with sources that have real repositories. Represent diagnostic work through its canonical PPR or Work Order; reject unknown enum values at the API boundary.

- [ ] **Step 4: Run GREEN**

Run the Step 2 command; expected PASS with zero skipped tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/db/migration/V20260712_2__repair_campaign_work_sources.sql src/main/java/com/toir/enums/RepairCampaignWorkItemSourceType.java src/main/java/com/toir/enums/RepairCampaignWorkItemStatus.java src/main/java/com/toir/entity/repair/RepairCampaignWorkItem.java src/main/java/com/toir/repository/repair/RepairCampaignWorkItemRepository.java src/main/java/com/toir/dto/repaircampaign/RepairCampaignWorkItemRequest.java src/main/java/com/toir/dto/repaircampaign/RepairCampaignWorkItemResponse.java src/main/java/com/toir/service/repair/CanonicalWorkSourceResolver.java src/main/java/com/toir/service/repair/RepairCampaignWorkItemService.java src/main/java/com/toir/enums/PlannedShutdownWorkItemSourceType.java src/main/java/com/toir/service/PlannedShutdownService.java src/main/java/com/toir/controller/repair/RepairCampaignController.java src/test/java/com/toir/migration/RepairCampaignWorkSourceMigrationContractTest.java src/test/java/com/toir/service/repair/CanonicalWorkSourceResolverTest.java src/test/java/com/toir/service/repair/RepairCampaignWorkItemServiceTest.java src/test/java/com/toir/service/PlannedShutdownServiceTest.java
git commit -m "feat: add canonical repair campaign work items"
```

---

### Task 3: Planned Shutdown relationship and canonical window identity

**Files:**
- Create: `src/main/resources/db/migration/V20260712_3__repair_campaign_shutdown_relationship.sql`
- Create: `src/main/java/com/toir/entity/plannedshutdown/PlannedShutdownCampaignLink.java`
- Create: `src/main/java/com/toir/entity/repair/RepairCampaignWorkItemWindow.java`
- Create: `src/main/java/com/toir/repository/plannedshutdown/PlannedShutdownCampaignLinkRepository.java`
- Create: `src/main/java/com/toir/repository/repair/RepairCampaignWorkItemWindowRepository.java`
- Create: `src/main/java/com/toir/dto/repaircampaign/RepairCampaignShutdownLinkRequest.java`
- Create: `src/main/java/com/toir/dto/repaircampaign/RepairCampaignShutdownLinkResponse.java`
- Create: `src/main/java/com/toir/dto/repaircampaign/RepairCampaignWorkItemWindowRequest.java`
- Create: `src/main/java/com/toir/dto/repaircampaign/RepairCampaignWorkItemWindowResponse.java`
- Create: `src/main/java/com/toir/service/repair/RepairCampaignShutdownLinkService.java`
- Modify: `src/main/java/com/toir/controller/repair/RepairCampaignController.java`
- Modify: `src/main/java/com/toir/controller/PlannedShutdownController.java`
- Test: `src/test/java/com/toir/migration/RepairCampaignShutdownRelationshipMigrationContractTest.java`
- Test: `src/test/java/com/toir/service/repair/RepairCampaignShutdownLinkServiceTest.java`
- Test: `src/test/java/com/toir/controller/RepairCampaignControllerContractTest.java`

**Interfaces:**
- `planned_shutdown_campaigns` permits zero-to-many in both directions with active uniqueness `(planned_shutdown_id, repair_campaign_id)`.
- `repair_campaign_work_item_windows` links one campaign item to one or more `(planned_shutdown_id, shutdown_work_item_id)` windows; both IDs must belong to the same canonical source/equipment.
- REST link/unlink/list commands require both aggregate versions and return both current versions.

- [ ] Write RED tests for independent aggregates, duplicate links, cross-department PBAC, mismatched source/equipment, deleted links, and post-start unlink rejection.
- [ ] Run: `JAVA_HOME=$(/usr/libexec/java_home -v 24) ./mvnw -Dtest=RepairCampaignShutdownRelationshipMigrationContractTest,RepairCampaignShutdownLinkServiceTest,RepairCampaignControllerContractTest test`; expect FAIL.
- [ ] Implement row-lock ordering by sorted aggregate UUID to avoid deadlocks, active uniqueness, and audit evidence on both aggregate IDs.
- [ ] Rerun the command; expect PASS.
- [ ] Commit named files with `feat: link campaigns to planned shutdown windows`.

---

### Task 4: Dependency DAG and resource plan

**Files:**
- Create: `src/main/resources/db/migration/V20260712_4__repair_campaign_planning.sql`
- Create: `src/main/java/com/toir/entity/repair/RepairCampaignWorkDependency.java`
- Create: `src/main/java/com/toir/entity/repair/RepairCampaignResourceAssignment.java`
- Create: `src/main/java/com/toir/repository/repair/RepairCampaignWorkDependencyRepository.java`
- Create: `src/main/java/com/toir/repository/repair/RepairCampaignResourceAssignmentRepository.java`
- Create: `src/main/java/com/toir/dto/repaircampaign/RepairCampaignDependencyRequest.java`
- Create: `src/main/java/com/toir/dto/repaircampaign/RepairCampaignDependencyResponse.java`
- Create: `src/main/java/com/toir/dto/repaircampaign/RepairCampaignResourceRequest.java`
- Create: `src/main/java/com/toir/dto/repaircampaign/RepairCampaignResourceResponse.java`
- Create: `src/main/java/com/toir/service/repair/RepairCampaignDependencyPolicy.java`
- Create: `src/main/java/com/toir/service/repair/RepairCampaignResourcePolicy.java`
- Modify: `src/main/java/com/toir/controller/repair/RepairCampaignController.java`
- Modify: `src/main/java/com/toir/service/repair/RepairCampaignWorkItemService.java`
- Test: `src/test/java/com/toir/migration/RepairCampaignPlanningMigrationContractTest.java`
- Test: `src/test/java/com/toir/service/repair/RepairCampaignDependencyPolicyTest.java`
- Test: `src/test/java/com/toir/service/repair/RepairCampaignResourcePolicyTest.java`
- Test: `src/test/java/com/toir/controller/RepairCampaignControllerContractTest.java`

**Interfaces:**
- Dependencies are unique directed edges `(campaign_id, predecessor_id, successor_id)` with no self-edge or cycle.
- Resource assignment has exactly one of `employeeId`, `brigadeId`, or `counteragentId`, plus `shiftCode`, `plannedStartAt`, `plannedEndAt`, and competency requirement.
- `RepairCampaignPlanningAssessment` returns deterministic blockers including `DEPENDENCY_CYCLE`, `PREDECESSOR_INCOMPLETE`, `RESOURCE_CONFLICT`, `COMPETENCY_MISSING`, and `CRITICAL_WORK_UNASSIGNED`.

- [ ] Write RED tests for self/circular dependency, foreign item, overlap conflict, inactive resource, missing competency, and unassigned critical work.
- [ ] Run: `JAVA_HOME=$(/usr/libexec/java_home -v 24) ./mvnw -Dtest=RepairCampaignPlanningMigrationContractTest,RepairCampaignDependencyPolicyTest,RepairCampaignResourcePolicyTest,RepairCampaignControllerContractTest test`; expect FAIL.
- [ ] Implement deterministic DFS cycle detection and interval-overlap checks; do not implement critical-path calculation.
- [ ] Rerun and expect PASS.
- [ ] Commit with `feat: add campaign dependencies and resource planning`.

---

### Task 5: Exact material demand and one canonical reservation

**Files:**
- Create: `src/main/resources/db/migration/V20260712_5__campaign_material_reservation_integrity.sql`
- Create: `src/main/java/com/toir/entity/repair/RepairCampaignMaterialRequirement.java`
- Create: `src/main/java/com/toir/repository/repair/RepairCampaignMaterialRequirementRepository.java`
- Create: `src/main/java/com/toir/dto/repaircampaign/RepairCampaignMaterialRequirementRequest.java`
- Create: `src/main/java/com/toir/dto/repaircampaign/RepairCampaignMaterialRequirementResponse.java`
- Create: `src/main/java/com/toir/service/repair/RepairCampaignMaterialService.java`
- Modify: `src/main/java/com/toir/entity/Reservation.java`
- Modify: `src/main/java/com/toir/dto/reservation/ReservationRequest.java`
- Modify: `src/main/java/com/toir/dto/reservation/ReservationDto.java`
- Modify: `src/main/java/com/toir/service/ReservationService.java`
- Modify: `src/main/java/com/toir/repository/ReservationRepository.java`
- Modify: `src/main/java/com/toir/entity/maintenance/WorkOrderSparePartRequirement.java`
- Modify: `src/main/java/com/toir/dto/workorder/WorkOrderSparePartRequirementRequest.java`
- Modify: `src/main/java/com/toir/dto/workorder/WorkOrderSparePartRequirementDto.java`
- Modify: `src/main/java/com/toir/repository/maintenance/WorkOrderSparePartRequirementRepository.java`
- Modify: `src/main/java/com/toir/service/maintanance/WorkOrderSparePartRequirementService.java`
- Modify: `src/main/java/com/toir/enums/WorkOrderSparePartRequirementSourceType.java`
- Test: `src/test/java/com/toir/migration/RepairCampaignMaterialMigrationContractTest.java`
- Test: `src/test/java/com/toir/service/repair/RepairCampaignMaterialServiceTest.java`
- Test: `src/test/java/com/toir/service/ReservationServiceTest.java`
- Test: `src/test/java/com/toir/service/maintanance/WorkOrderSparePartRequirementServiceTest.java`

**Interfaces:**
- Campaign demand uses `requiredQuantity: BigDecimal`, `critical`, and `procurementRequired`; it does not reserve stock directly.
- Generated Work Order requirements use source type `REPAIR_CAMPAIGN_WORK_ITEM` and `campaignRequirementId`.
- Active reservation uniqueness is `(work_order_id, requirement_id, spare_part_id)`; quantities are `numeric(19,4)` and decimal-string JSON.
- Assessment blockers: `MATERIAL_REQUIREMENT_MISSING`, `CRITICAL_MATERIAL_DEFICIT`, `RESERVATION_DUPLICATE`, `PROCUREMENT_REQUIRED`.

- [ ] Write RED precision, duplicate-reservation, deficit, over-reservation, rollback, and replay tests.
- [ ] Run: `JAVA_HOME=$(/usr/libexec/java_home -v 24) ./mvnw -Dtest=RepairCampaignMaterialMigrationContractTest,RepairCampaignMaterialServiceTest,ReservationServiceTest,WorkOrderSparePartRequirementServiceTest test`; expect FAIL.
- [ ] Migrate only finite nonnegative legacy quantities; fail migration with a named remediation code for invalid values. Convert DTOs/entities/calculations to `BigDecimal` without binary conversion.
- [ ] Rerun and expect PASS; also run `WorkOrderServiceTest` as regression.
- [ ] Commit with `feat: enforce canonical campaign material reservations`.

---

### Task 6: Campaign approval snapshot, seven-discipline route, PBAC, and separation of duty

**Files:**
- Create: `src/main/resources/db/migration/V20260712_6__repair_campaign_approval_route.sql`
- Create: `src/main/java/com/toir/service/repair/RepairCampaignApprovalScopeHasher.java`
- Create: `src/main/java/com/toir/service/repair/RepairCampaignApprovalPolicy.java`
- Create: `src/main/java/com/toir/enums/RepairCampaignMutationType.java`
- Create: `src/main/java/com/toir/dto/repaircampaign/CampaignMutationImpact.java`
- Create: `src/main/java/com/toir/service/repair/RepairCampaignMutationImpactService.java`
- Modify: `src/main/java/com/toir/security/PermissionConstants.java`
- Modify: `src/main/java/com/toir/security/RolePermissionDefaults.java`
- Modify: `src/main/java/com/toir/security/ApprovalDomainPermissions.java`
- Modify: `src/main/java/com/toir/service/approval/RepairCampaignApprovalHandler.java`
- Modify: `src/main/java/com/toir/service/repair/RepairCampaignService.java`
- Modify: `src/main/java/com/toir/service/ApprovalService.java`
- Modify: `src/main/java/com/toir/controller/repair/RepairCampaignController.java`
- Test: `src/test/java/com/toir/migration/RepairCampaignApprovalMigrationContractTest.java`
- Test: `src/test/java/com/toir/service/repair/RepairCampaignApprovalScopeHasherTest.java`
- Test: `src/test/java/com/toir/service/repair/RepairCampaignMutationImpactServiceTest.java`
- Test: `src/test/java/com/toir/service/RepairCampaignServiceTest.java`
- Test: `src/test/java/com/toir/security/ApprovalPbacScopeTest.java`
- Test: `src/test/java/com/toir/security/RbacToirBusinessFlowSecurityTest.java`
- Test: `src/test/java/com/toir/security/ToirBusinessFlowRolePermissionsTest.java`

**Interfaces:**
- Add exact permissions: `REPAIR_CAMPAIGN_MANAGE_WORK`, `REPAIR_CAMPAIGN_MANAGE_SCOPE`, `REPAIR_CAMPAIGN_MANAGE_SHUTDOWN_LINKS`, `REPAIR_CAMPAIGN_MANAGE_RESOURCES`, `REPAIR_CAMPAIGN_MANAGE_MATERIALS`, `REPAIR_CAMPAIGN_MANAGE_DEPENDENCIES`, `REPAIR_CAMPAIGN_MANAGE_FINANCE`, `REPAIR_CAMPAIGN_REQUEST_APPROVAL`, `REPAIR_CAMPAIGN_RESUME`, `REPAIR_CAMPAIGN_BEGIN_CLOSING`, `REPAIR_CAMPAIGN_ARCHIVE`, `REPAIR_CAMPAIGN_CONFIRM_DEFECT`, `REPAIR_CAMPAIGN_APPROVE_FX`, `REPAIR_CAMPAIGN_APPROVE_BUDGET_OVERRUN`, `REPAIR_CAMPAIGN_APPROVE_CLOSURE`, `REPAIR_CAMPAIGN_OUTBOX_READ`, and `REPAIR_CAMPAIGN_OUTBOX_RETRY`. Existing create/read/start/suspend/complete/close/cancel/generate permissions remain operation-specific.
- V6 seeds `SYSTEM_ADMIN` and wildcard semantics with the full set; `TECHNICAL_DIRECTOR` and `CHIEF_MECHANIC` receive all campaign lifecycle/manage/approval permissions except outbox operations; `MAINTENANCE_MANAGER` receives read/create/manage work/scope/links/resources/materials/dependencies/request-approval/generate only; `WAREHOUSE_MANAGER` receives read/manage-materials; `FINANCE_MANAGER` receives read/manage-finance/approve-FX/approve-budget-overrun, with requester/approver SoD still enforced; outbox read/retry is seeded only to `SYSTEM_ADMIN`.
- Template `REPAIR_CAMPAIGN_APPROVAL` has ordered role steps for chief mechanic, production, warehouse, procurement, finance, HSE, and chief engineer.
- Hash input includes metadata, dates, work/order, dependencies, resources, materials, shutdown links/window versions, budget/currency, and `scopeVersion`.
- Requester cannot approve any step; one actor cannot approve two discipline steps; department PBAC applies to read/decide/delegate.
- `RepairCampaignMutationType` is `METADATA, DATES, WORK_ITEMS, SHUTDOWN_LINKS, DEPENDENCIES, RESOURCES, MATERIALS, BUDGET, FX`. `GET /repair-campaigns/{id}/mutation-impact?mutationType=...&version=...&scopeVersion=...` returns `CampaignMutationImpact(boolean invalidatesApproval, RepairCampaignStatus currentStatus, long currentScopeVersion, long currentVersion, RepairCampaignStatus nextStatus, String reason, List<RepairCampaignBlocker> blockers)` and requires the permission of the mutation being previewed.
- Every mutating endpoint calls the same impact service before persistence; the frontend preview cannot diverge from the committed next status/reason.

| Endpoint/action | Required authority |
|---|---|
| work-item create/update/remove/reorder | `REPAIR_CAMPAIGN_MANAGE_WORK` |
| campaign metadata/scope update | `REPAIR_CAMPAIGN_MANAGE_SCOPE` |
| shutdown/campaign/window link or unlink | `REPAIR_CAMPAIGN_MANAGE_SHUTDOWN_LINKS` |
| resource assignment mutation | `REPAIR_CAMPAIGN_MANAGE_RESOURCES` |
| material requirement mutation | `REPAIR_CAMPAIGN_MANAGE_MATERIALS` |
| dependency mutation | `REPAIR_CAMPAIGN_MANAGE_DEPENDENCIES` |
| FX draft / financial plan mutation | `REPAIR_CAMPAIGN_MANAGE_FINANCE` |
| request approval | `REPAIR_CAMPAIGN_REQUEST_APPROVAL` |
| resume / begin closing / archive | `REPAIR_CAMPAIGN_RESUME` / `REPAIR_CAMPAIGN_BEGIN_CLOSING` / `REPAIR_CAMPAIGN_ARCHIVE` |
| defect proposal confirmation | `REPAIR_CAMPAIGN_CONFIRM_DEFECT` |
| FX / budget-overrun / final closure approval decision | `REPAIR_CAMPAIGN_APPROVE_FX` / `REPAIR_CAMPAIGN_APPROVE_BUDGET_OVERRUN` / `REPAIR_CAMPAIGN_APPROVE_CLOSURE` |
| outbox dead-letter read / retry | `REPAIR_CAMPAIGN_OUTBOX_READ` / `REPAIR_CAMPAIGN_OUTBOX_RETRY` |

- [ ] Write RED tests for stale hash/version, self approval, repeated actor, substring role, cross-department decision, bypass permission, post-approval mutation, every mutation impact type, stale preview version, exact controller permission expression, and conservative role seeds.
- [ ] Run: `JAVA_HOME=$(/usr/libexec/java_home -v 24) ./mvnw -Dtest=RepairCampaignApprovalMigrationContractTest,RepairCampaignApprovalScopeHasherTest,RepairCampaignMutationImpactServiceTest,RepairCampaignServiceTest,RepairCampaignControllerContractTest,ApprovalPbacScopeTest,RbacToirBusinessFlowSecurityTest,ToirBusinessFlowRolePermissionsTest test`; expect FAIL.
- [ ] Implement snapshot/reapproval and exact route seeds. Source/scope/window/link mutation clears the hash, retires the stale pending request, and returns to `SCOPE_FORMATION`; dependency/resource/material/budget-plan mutation returns to `RESOURCE_CHECK`. No invalidation path returns to `PENDING_APPROVAL`: only a successful explicit request-approval command creates a current request and enters that status. Non-invalidating operational evidence may remain in `PREPARATION` only after a fresh approval.
- [ ] Rerun and expect PASS.
- [ ] Commit with `feat: harden repair campaign approval scope`.

---

### Task 7: Locked lifecycle, readiness, progress states, and archive policy

**Files:**
- Create: `src/main/java/com/toir/service/repair/RepairCampaignTransitionPolicy.java`
- Create: `src/main/java/com/toir/service/repair/RepairCampaignReadinessPolicy.java`
- Create: `src/main/java/com/toir/dto/repaircampaign/RepairCampaignBlocker.java`
- Create: `src/main/java/com/toir/dto/repaircampaign/RepairCampaignTransitionRequest.java`
- Create: `src/main/java/com/toir/dto/repaircampaign/RepairCampaignAssessment.java`
- Modify: `src/main/java/com/toir/service/repair/RepairCampaignService.java`
- Modify: `src/main/java/com/toir/controller/repair/RepairCampaignController.java`
- Modify: `src/main/java/com/toir/repository/repair/RepairCampaignRepository.java`
- Test: `src/test/java/com/toir/service/repair/RepairCampaignTransitionPolicyTest.java`
- Test: `src/test/java/com/toir/service/repair/RepairCampaignReadinessPolicyTest.java`
- Test: `src/test/java/com/toir/service/RepairCampaignServiceTest.java`
- Test: `src/test/java/com/toir/controller/RepairCampaignControllerContractTest.java`
- Test: `src/test/java/com/toir/security/RbacToirBusinessFlowSecurityTest.java`

**Interfaces:**
- One transition executor accepts `expectedStatus`, `version`, `reason`, and `correlationKey`.
- `start` requires current campaign approval, shutdown facts for shutdown-required items, materials, resources, dependencies, and budget readiness.
- `suspend`, `resume`, `complete`, `begin-closing`, `close`, `cancel`, and `archive` have separate permissions and blocker sets. Suspend records only `PREPARATION` or `IN_PROGRESS` in `suspendedFromStatus`; resume restores exactly that status and clears the field.
- Approved/started campaigns cannot hard-delete; archive is soft, state-aware, and preserves history/closure.

- [ ] Write table-driven RED coverage for every legal edge and representative skipped/backward/terminal edge.
- [ ] Add RED tests for critical unresolved work, open contractor work, unallocated cost, active WO, missing approval, missing shutdown, and archive restrictions.
- [ ] Run: `JAVA_HOME=$(/usr/libexec/java_home -v 24) ./mvnw -Dtest=RepairCampaignTransitionPolicyTest,RepairCampaignReadinessPolicyTest,RepairCampaignServiceTest,RepairCampaignControllerContractTest,RbacToirBusinessFlowSecurityTest test`; expect FAIL.
- [ ] Implement the minimal locked transition executor and history/audit writes.
- [ ] Rerun and expect PASS.
- [ ] Commit with `feat: enforce repair campaign lifecycle`.

---

### Task 8: Transactional outbox and cross-module status/date propagation

**Files:**
- Create: `src/main/resources/db/migration/V20260712_7__toir_integration_outbox.sql`
- Create: `src/main/java/com/toir/entity/integration/IntegrationOutboxEvent.java`
- Create: `src/main/java/com/toir/repository/integration/IntegrationOutboxEventRepository.java`
- Create: `src/main/java/com/toir/enums/IntegrationOutboxStatus.java`
- Create: `src/main/java/com/toir/dto/integration/IntegrationOutboxEventDto.java`
- Create: `src/main/java/com/toir/service/repair/RepairCampaignOutboxService.java`
- Create: `src/main/java/com/toir/service/repair/RepairCampaignIntegrationProjector.java`
- Create: `src/main/java/com/toir/config/RepairCampaignOutboxProperties.java`
- Create: `src/main/java/com/toir/service/repair/RepairCampaignOutboxDispatcher.java`
- Create: `src/main/java/com/toir/service/repair/RepairCampaignOutboxScheduler.java`
- Create: `src/main/java/com/toir/controller/repair/RepairCampaignOutboxAdminController.java`
- Modify: `src/main/java/com/toir/service/PlannedShutdownService.java`
- Modify: `src/main/java/com/toir/service/repair/RepairCampaignShutdownLinkService.java`
- Modify: `src/main/java/com/toir/service/repair/RepairCampaignService.java`
- Test: `src/test/java/com/toir/migration/IntegrationOutboxMigrationContractTest.java`
- Test: `src/test/java/com/toir/service/repair/RepairCampaignOutboxServiceTest.java`
- Test: `src/test/java/com/toir/service/repair/RepairCampaignIntegrationProjectorTest.java`
- Test: `src/test/java/com/toir/service/repair/RepairCampaignOutboxDispatcherIntegrationTest.java`
- Test: `src/test/java/com/toir/controller/RepairCampaignOutboxAdminControllerContractTest.java`
- Test: `src/test/java/com/toir/service/PlannedShutdownLifecycleServiceTest.java`

**Interfaces:**
- Event types: `SHUTDOWN_RESCHEDULED`, `SHUTDOWN_EXTENDED`, `SHUTDOWN_CANCELLED`, `CAMPAIGN_SUSPENDED`, `CAMPAIGN_CANCELLED`, `CAMPAIGN_RESUMED`, `CAMPAIGN_SCOPE_CHANGED`.
- Unique processing identity: `(aggregate_type, aggregate_id, aggregate_version, event_type)`.
- Statuses are `PENDING, PROCESSING, PUBLISHED, DEAD_LETTER`; rows persist `attempts`, `next_attempt_at`, `locked_at`, `locked_by`, and `last_error`. The repository claims at most `batchSize` due rows ordered by `(next_attempt_at, created_at, id)` with `FOR UPDATE SKIP LOCKED`.
- V7 also adds nullable `work_orders.integration_block_code` and `work_orders.integration_previous_status`; only the projector/service matrix writes or clears them, and terminal Work Orders never receive either value.
- Configuration is `toir.integration.outbox.enabled=true`, `fixed-delay-ms=5000`, `batch-size=50`, `max-attempts=10`, `base-backoff-seconds=30`, and `max-backoff-seconds=3600`. Backoff is bounded exponential; attempt 10 transitions to `DEAD_LETTER`. A stale `PROCESSING` lease is reclaimable only after the configured max backoff.
- `RepairCampaignOutboxScheduler` invokes one bounded dispatcher batch per tick; it contains no unbounded loop. Admin endpoints `GET /repair-campaigns/outbox/dead-letters` and `POST /repair-campaigns/outbox/{eventId}/retry` use the exact outbox authorities and audit every retry.
- Every projector update uses Work Order/Campaign services so status history and audit remain authoritative. No event changes Planned Shutdown status, Planned Shutdown history, or any actual timestamp.

| Incoming event | Unstarted Work Orders (`DRAFT/PLANNED/APPROVED`) | Active Work Orders (`IN_PROGRESS`) | Terminal Work Orders | Campaign effect |
|---|---|---|---|---|
| `SHUTDOWN_RESCHEDULED` / `SHUTDOWN_EXTENDED` | status unchanged; set `integrationBlockCode=REPLAN_REQUIRED` | remain `IN_PROGRESS`; record `ACTIVE_WORK_WINDOW_CHANGED` blocker/event | unchanged | set `SUSPENDED`, work-item window `REPLAN_REQUIRED`; historical actuals unchanged |
| `SHUTDOWN_CANCELLED` | status unchanged; set `integrationBlockCode=SHUTDOWN_CANCELLED` | transition to `SUSPENDED` through Work Order service and record prior status | unchanged | set `SUSPENDED`; Planned Shutdown remains source of cancellation |
| `CAMPAIGN_SUSPENDED` | status unchanged; set `integrationBlockCode=CAMPAIGN_SUSPENDED` | transition to `SUSPENDED` through Work Order service | unchanged | campaign already `SUSPENDED`; Planned Shutdown unchanged |
| `CAMPAIGN_CANCELLED` | transition to `CANCELLED` only when no execution evidence exists | transition to `SUSPENDED`, never blind-cancel | unchanged | campaign remains `CANCELLED`; Planned Shutdown unchanged |
| `CAMPAIGN_RESUMED` | clear campaign block only after current Campaign and Shutdown policies pass | remain `SUSPENDED` until explicit authorized Work Order resume revalidates policies | unchanged | restore `suspendedFromStatus`; no Planned Shutdown mutation |

- [ ] Write RED commit/rollback, duplicate-delivery, SKIP LOCKED two-dispatcher, batch bound, retry/backoff/dead-letter, stale lease, out-of-order version, every matrix row, partial-completion, admin PBAC, and historical-actual preservation tests.
- [ ] Run: `JAVA_HOME=$(/usr/libexec/java_home -v 24) ./mvnw -Dtest=IntegrationOutboxMigrationContractTest,RepairCampaignOutboxServiceTest,RepairCampaignIntegrationProjectorTest,RepairCampaignOutboxDispatcherIntegrationTest,RepairCampaignOutboxAdminControllerContractTest,PlannedShutdownLifecycleServiceTest test`; expect FAIL.
- [ ] Implement same-transaction outbox writes, bounded scheduled dispatch, idempotent projector behavior, and audited dead-letter retry.
- [ ] Rerun and expect PASS.
- [ ] Commit with `feat: propagate shutdown campaign integration events`.

---

### Task 9: Canonical Work Order generation and independent start invariants

**Files:**
- Create: `src/main/resources/db/migration/V20260712_8__repair_campaign_generation_commands.sql`
- Create: `src/main/java/com/toir/service/repair/RepairCampaignWorkOrderGenerationService.java`
- Create: `src/main/java/com/toir/service/repair/RepairCampaignWorkOrderStartPolicy.java`
- Create: `src/main/java/com/toir/dto/repaircampaign/RepairCampaignGenerationRequest.java`
- Create: `src/main/java/com/toir/dto/repaircampaign/RepairCampaignGenerationResult.java`
- Modify: `src/main/java/com/toir/service/repair/RepairCampaignService.java`
- Modify: `src/main/java/com/toir/controller/repair/RepairCampaignController.java`
- Modify: `src/main/java/com/toir/service/WorkOrderService.java`
- Modify: `src/main/java/com/toir/entity/maintenance/WorkOrder.java`
- Modify: `src/main/java/com/toir/dto/workorder/WorkOrderDto.java`
- Modify: `src/main/java/com/toir/repository/WorkOrderRepository.java`
- Test: `src/test/java/com/toir/service/repair/RepairCampaignWorkOrderGenerationServiceTest.java`
- Test: `src/test/java/com/toir/controller/RepairCampaignGenerationControllerContractTest.java`
- Test: `src/test/java/com/toir/service/repair/RepairCampaignWorkOrderStartPolicyTest.java`
- Test: `src/test/java/com/toir/repository/repair/RepairCampaignGenerationPostgresTest.java`

**Interfaces:**
- Required `Idempotency-Key`; fingerprint contains campaign version, selected work-item IDs, shutdown/window versions, and generation options.
- Generation key is `RC:{campaignWorkItemId}:PS:{shutdownId}:WV:{windowVersion}` for shutdown work and `RC:{campaignWorkItemId}:NOWINDOW` otherwise.
- Replay returns ordered canonical IDs; same header/different fingerprint is 409; any item failure records neither command success nor partial result.
- Work Order start composes campaign approval/readiness and Planned Shutdown safe-state policy. Both approvals are independently current.

- [ ] Write RED draft/stale approval, absent shutdown, outside window, campaign suspended, duplicate source/window, replay poisoning, two-thread race, and Nth-create rollback tests.
- [ ] Run: `JAVA_HOME=$(/usr/libexec/java_home -v 24) ./mvnw -Dtest=RepairCampaignWorkOrderGenerationServiceTest,RepairCampaignGenerationControllerContractTest,RepairCampaignWorkOrderStartPolicyTest,WorkOrderServiceTest test`; expect FAIL.
- [ ] Implement durable command/fingerprint/result rows, deterministic ordering, lock ordering, exact constraint classification, and composed start policy.
- [ ] Rerun and expect PASS; run live PostgreSQL 17 concurrency when executable is available.
- [ ] Commit with `feat: generate canonical campaign work orders`.

---

### Task 10: Defect closure proposal workflow

**Files:**
- Create: `src/main/resources/db/migration/V20260712_9__repair_campaign_defect_closure.sql`
- Create: `src/main/java/com/toir/enums/RepairCampaignDefectClosureStatus.java`
- Create: `src/main/java/com/toir/entity/repair/RepairCampaignDefectClosureProposal.java`
- Create: `src/main/java/com/toir/repository/repair/RepairCampaignDefectClosureProposalRepository.java`
- Create: `src/main/java/com/toir/dto/repaircampaign/RepairCampaignDefectClosureRequest.java`
- Create: `src/main/java/com/toir/dto/repaircampaign/RepairCampaignDefectClosureResponse.java`
- Create: `src/main/java/com/toir/service/repair/RepairCampaignDefectClosureService.java`
- Modify: `src/main/java/com/toir/service/WorkOrderService.java`
- Modify: `src/main/java/com/toir/service/defects/DefectService.java`
- Modify: `src/main/java/com/toir/controller/defects/DefectController.java`
- Modify: `src/main/java/com/toir/controller/repair/RepairCampaignController.java`
- Test: `src/test/java/com/toir/migration/RepairCampaignDefectClosureMigrationContractTest.java`
- Test: `src/test/java/com/toir/service/repair/RepairCampaignDefectClosureServiceTest.java`
- Test: `src/test/java/com/toir/service/WorkOrderServiceTest.java`
- Test: `src/test/java/com/toir/service/defects/DefectServiceTest.java`
- Test: `src/test/java/com/toir/security/RbacToirBusinessFlowSecurityTest.java`

**Interfaces:**
- Work Order completion may create one `PENDING` proposal per `(campaignWorkItemId, defectId, workOrderId)`; it never closes the defect.
- Responsible confirmation records `ACCEPTED`, `RESIDUAL`, or `REJECTED`, evidence, actor, timestamp, and optimistic version.
- Campaign completion blocks unresolved critical proposals; residual/rejected defects remain open.

- [ ] Write RED blind-auto-close, duplicate proposal, unauthorized confirmation, residual, rejected, missing evidence, and stale-version tests.
- [ ] Run: `JAVA_HOME=$(/usr/libexec/java_home -v 24) ./mvnw -Dtest=RepairCampaignDefectClosureMigrationContractTest,RepairCampaignDefectClosureServiceTest,WorkOrderServiceTest,DefectServiceTest,RbacToirBusinessFlowSecurityTest test`; expect FAIL.
- [ ] Implement proposal-only completion integration and explicit confirmation.
- [ ] Rerun and expect PASS.
- [ ] Commit with `feat: add campaign defect closure proposals`.

---

### Task 11: Exact Actual Cost currency, FX snapshot, and budget-overrun approval

**Files:**
- Create: `src/main/resources/db/migration/V20260712_10__campaign_financial_integrity.sql`
- Create: `src/main/java/com/toir/entity/repair/RepairCampaignFxSnapshot.java`
- Create: `src/main/java/com/toir/repository/repair/RepairCampaignFxSnapshotRepository.java`
- Create: `src/main/java/com/toir/dto/repaircampaign/RepairCampaignFxSnapshotRequest.java`
- Create: `src/main/java/com/toir/dto/repaircampaign/RepairCampaignFxSnapshotResponse.java`
- Create: `src/main/java/com/toir/service/repair/RepairCampaignFxSnapshotService.java`
- Create: `src/main/java/com/toir/service/repair/RepairCampaignFinancialPolicy.java`
- Create: `src/main/java/com/toir/dto/repaircampaign/RepairCampaignFinancialApprovalPayload.java`
- Create: `src/main/java/com/toir/service/approval/RepairCampaignFinancialApprovalHandler.java`
- Modify: `src/main/java/com/toir/enums/ApprovalActionType.java`
- Modify: `src/main/java/com/toir/service/approval/ApprovalHandlerRegistryVerifier.java`
- Modify: `src/main/java/com/toir/entity/projects/ActualCost.java`
- Modify: `src/main/java/com/toir/dto/actualcost/ActualCostDto.java`
- Modify: `src/main/java/com/toir/service/ActualCostService.java`
- Modify: `src/main/java/com/toir/repository/actualCost/ActualCostRepository.java`
- Modify: `src/main/java/com/toir/dto/repaircampaign/RepairCampaignCostSummaryDto.java`
- Modify: `src/main/java/com/toir/dto/repaircampaign/RepairCampaignBudgetSummaryDto.java`
- Modify: `src/main/java/com/toir/service/repair/RepairCampaignService.java`
- Modify: `src/main/java/com/toir/service/approval/RepairCampaignApprovalHandler.java`
- Modify: `src/main/java/com/toir/controller/repair/RepairCampaignController.java`
- Test: `src/test/java/com/toir/migration/RepairCampaignFinancialMigrationContractTest.java`
- Test: `src/test/java/com/toir/migration/RepairCampaignFinancialMigrationPostgresTest.java`
- Test: `src/test/java/com/toir/service/ActualCostServiceTest.java`
- Test: `src/test/java/com/toir/controller/ActualCostControllerContractTest.java`
- Test: `src/test/java/com/toir/service/repair/RepairCampaignFinancialPolicyTest.java`
- Test: `src/test/java/com/toir/service/RepairCampaignServiceTest.java`
- Test: `src/test/java/com/toir/security/ApprovalPbacScopeTest.java`
- Test: `src/test/java/com/toir/service/approval/ApprovalActionHandlerTest.java`

**Interfaces:**
- Add typed `ApprovalActionType.APPROVE_CAMPAIGN_FX` and `APPROVE_BUDGET_OVERRUN`. `ApprovalHandlerRegistryVerifier` requires exactly one handler for each financial `(REPAIR_CAMPAIGN, typed action)` pair and keeps lifecycle `APPROVE/REJECT` handling separate. Task 12 adds the closure action only when its handler and payload exist in the same commit.
- Actual Cost `amount` becomes `BigDecimal numeric(19,4)` with ISO `currencyCode`. A legacy row inherits currency only when its Work Order resolves to exactly one campaign with a non-null currency; unlinked or ambiguous rows remain nullable, are excluded from campaign totals, and block campaign closure with `ACTUAL_COST_CURRENCY_REMEDIATION_REQUIRED`. New writes require currency.
- Cross-currency campaign aggregation requires an immutable approved FX snapshot containing currencies, rate, source, approval request, actor, and timestamp.
- V10 seeds separate `REPAIR_CAMPAIGN_FX_APPROVAL` and `REPAIR_CAMPAIGN_BUDGET_OVERRUN_APPROVAL` templates. FX approval payload is `(campaignId, campaignVersion, scopeVersion, budgetId, budgetVersion, fxDraftId, fromCurrency, toCurrency, rate, factsHash)`. Overrun payload is `(campaignId, campaignVersion, scopeVersion, budgetId, budgetVersion, currencyCode, approvedBudget, approvedActual, distinctActualCostIds, distinctBudgetLineIds, factsHash)`.
- `RepairCampaignFinancialApprovalHandler` supports only `APPROVE_CAMPAIGN_FX` and `APPROVE_BUDGET_OVERRUN`. It marks the exact FX draft or overrun fact approved after recomputing payload hash; it never calls campaign lifecycle approval/finalization or changes campaign status.
- Any campaign scope/version, budget version, FX draft, Actual Cost ID set, Budget Line ID set, amount, or currency change makes the approval stale and blocks use. Scope-invalidating mutations retire pending financial approvals through the Task 6 invalidation path.
- Overrun (`approvedActual > approvedBudget`) creates/reuses only the typed overrun approval; unresolved/stale overrun blocks `CLOSING` and final closure approval.
- Before summing, campaign aggregation constructs `LinkedHashMap<UUID, ActualCost>` and `LinkedHashMap<UUID, BudgetLine>` across direct campaign Work Orders, Campaign-to-Shutdown windows, and shared canonical Work Orders. Totals are computed once per distinct primary key.

- [ ] Write RED binary-rounding, excessive scale, null currency, mismatch without FX, unapproved FX, snapshot mutation, concurrent cost post, overrun bypass, stale FX/overrun approval, registry ambiguity/missing handler, financial-handler lifecycle non-transition, and multi-path duplicate Actual Cost/Budget Line fixtures with mixed currencies.
- [ ] Run: `JAVA_HOME=$(/usr/libexec/java_home -v 24) ./mvnw -Dtest=RepairCampaignFinancialMigrationContractTest,RepairCampaignFinancialMigrationPostgresTest,ActualCostServiceTest,ActualCostControllerContractTest,RepairCampaignFinancialPolicyTest,RepairCampaignServiceTest,ApprovalPbacScopeTest,ApprovalActionHandlerTest test`; expect FAIL.
- [ ] Implement decimal-string boundaries, immutable FX trigger, ledger-derived totals, and versioned overrun approval.
- [ ] Rerun and expect PASS.
- [ ] Commit with `feat: enforce campaign financial integrity`.

---

### Task 12: Deterministic progress and immutable closure reconciliation facts

**Files:**
- Create: `src/main/resources/db/migration/V20260712_11__repair_campaign_closure_snapshot.sql`
- Create: `src/main/java/com/toir/entity/repair/RepairCampaignClosureSnapshot.java`
- Create: `src/main/java/com/toir/entity/repair/RepairCampaignClosureDraft.java`
- Create: `src/main/java/com/toir/repository/repair/RepairCampaignClosureSnapshotRepository.java`
- Create: `src/main/java/com/toir/repository/repair/RepairCampaignClosureDraftRepository.java`
- Create: `src/main/java/com/toir/dto/repaircampaign/RepairCampaignClosureReport.java`
- Create: `src/main/java/com/toir/dto/repaircampaign/RepairCampaignClosureDraftRequest.java`
- Create: `src/main/java/com/toir/dto/repaircampaign/RepairCampaignClosureDraftResponse.java`
- Create: `src/main/java/com/toir/dto/repaircampaign/RepairCampaignClosureApprovalPayload.java`
- Create: `src/main/java/com/toir/service/repair/RepairCampaignClosureService.java`
- Create: `src/main/java/com/toir/service/repair/RepairCampaignProgressPolicy.java`
- Create: `src/main/java/com/toir/service/approval/RepairCampaignClosureApprovalHandler.java`
- Modify: `src/main/java/com/toir/enums/ApprovalActionType.java`
- Modify: `src/main/java/com/toir/service/approval/ApprovalHandlerRegistryVerifier.java`
- Modify: `src/main/java/com/toir/service/repair/RepairCampaignService.java`
- Modify: `src/main/java/com/toir/controller/repair/RepairCampaignController.java`
- Test: `src/test/java/com/toir/migration/RepairCampaignClosureMigrationContractTest.java`
- Test: `src/test/java/com/toir/service/repair/RepairCampaignProgressPolicyTest.java`
- Test: `src/test/java/com/toir/service/repair/RepairCampaignClosureServiceTest.java`
- Test: `src/test/java/com/toir/service/repair/RepairCampaignReconciliationTest.java`
- Test: `src/test/java/com/toir/service/approval/ApprovalActionHandlerTest.java`

**Interfaces:**
- Progress is derived by work-item weights (`critical=4, high=3, medium=2, low=1`) and canonical status; no writable percentage.
- Summary exposes total/completed/in-progress/overdue/cancelled/blocked and numerator/denominator.
- While status is `CLOSING`, a versioned mutable closure draft stores `closureNarrative`, required `deviationReasons`, `residualWorkEvidence`, canonical `attachmentFileIds`, canonical `completionActIds`, and `lessonsLearnedSummary`. The draft is not the final report and cannot be read as approved evidence.
- V11 adds `ApprovalActionType.APPROVE_CAMPAIGN_CLOSURE`, registers exactly one closure handler, and seeds `REPAIR_CAMPAIGN_CLOSURE_APPROVAL` in the same task. Request payload is `(campaignId, campaignVersion, scopeVersion, closureDraftId, closureDraftVersion, distinctWorkItemIds, distinctShutdownIds, distinctWorkOrderIds, distinctReservationIds, distinctActualCostIds, distinctBudgetLineIds, distinctDefectProposalIds, attachmentFileIds, completionActIds, factsHash)` and action `APPROVE_CAMPAIGN_CLOSURE`.
- `RepairCampaignClosureApprovalHandler` locks the campaign/draft, recomputes every canonical fact and hash, rejects stale scope/version/cost/evidence, records final Approval Request ID plus ordered approval-step signatures `(stepId, roleCode, actorId, decidedAt, comment)`, creates exactly one immutable snapshot, and then invokes the allowed `CLOSING -> CLOSED` lifecycle transition. Unlike financial handlers, this typed final action is the only approval handler allowed to close the campaign.
- Immutable snapshot contains canonical campaign/work-item/window/WO/reservation/cost/budget/defect-proposal/file/attachment/completion-act IDs, closure narrative, deviations, residual evidence, lessons summary, campaign scope/version, planned/actual dates, final approval identity/signatures, and shutdown downtime references. It never copies downtime.
- Blockers include `CLOSURE_NARRATIVE_MISSING`, `DEVIATION_REASON_MISSING`, `RESIDUAL_EVIDENCE_MISSING`, `CLOSURE_ATTACHMENT_MISSING`, `COMPLETION_ACT_MISSING`, `CLOSURE_APPROVAL_MISSING`, `CLOSURE_APPROVAL_STALE`, plus existing unresolved work/defect/cost/currency blockers.
- Deduplication is by canonical primary key before hashing; snapshot has absolute one-per-campaign uniqueness and UPDATE/DELETE rejection trigger.

- [ ] Write RED manual-progress, duplicate multi-path fact, downtime-copy, missing narrative/deviation/residual/attachment/act, unresolved work/defect/cost, stale final approval, incomplete signature, repeated snapshot, mutation, hash corruption, and exact `CLOSING -> CLOSED` handler tests.
- [ ] Run: `JAVA_HOME=$(/usr/libexec/java_home -v 24) ./mvnw -Dtest=RepairCampaignClosureMigrationContractTest,RepairCampaignProgressPolicyTest,RepairCampaignClosureServiceTest,RepairCampaignReconciliationTest,ApprovalActionHandlerTest test`; expect FAIL.
- [ ] Implement the versioned draft, typed closure approval, immutable canonical snapshot, signature capture, lifecycle finalization, and hash verification. Defer polished report projections and analytics to Stage 4.
- [ ] Rerun and expect PASS.
- [ ] Commit with `feat: close and reconcile repair campaigns`.

---

### Task 13: Frontend companion orchestration checkpoint

**Files:**
- Read and execute: `../toir-front/docs/superpowers/plans/2026-07-12-toir-stage-3-repair-campaign-integration.md`

**Interfaces:**
- The frontend companion's Tasks 1-7 are the sole source of frontend files, RED/GREEN commands, and frontend commit boundaries. This backend task does not duplicate or replace those commits.
- Record the frontend pre-task and post-task HEADs in the Stage 3 progress ledger for Task 14 evidence.

- [ ] Confirm backend Tasks 1-12 are committed and the backend worktree contains no uncommitted implementation files.
- [ ] Change working directory exactly: `cd /Users/tenzorsoft/Desktop/Work/toir/toir-front`.
- [ ] Read the companion plan completely and execute Tasks 1 through 7 one at a time, including each task's RED test, GREEN gate, and frontend-only commit.
- [ ] Run the companion's focused regression command, scoped ESLint, `yarn i18n:check`, `yarn tsc --noEmit`, and `yarn build` from the frontend repository root.
- [ ] Capture `git rev-parse HEAD`, `git status --short --branch`, and `git log --oneline` for frontend evidence; do not stage or commit frontend files from the backend repository.
- [ ] Return exactly with `cd /Users/tenzorsoft/Desktop/Work/toir/toir-backend`; continue to Task 14 without creating a backend checkpoint commit.

---

### Task 14: Isolated Stage 3 verification, evidence, and independent review

**Files:**
- Create in backend repository: `docs/audits/2026-07-12-toir-stage-3-verification.md`
- Create in frontend repository: `../toir-front/docs/audits/2026-07-12-toir-stage-3-verification.md`

**Interfaces:**
- Evidence maps RC-01..RC-15, X-01..X-10, security/audit/archive requirements, every negative scenario, and all retained deferrals to named tests/results.

- [ ] Create detached backend/frontend verification worktrees at the exact pre-report HEADs.
- [ ] Run Java 24 compile plus all Stage 3 migration/repository/policy/service/controller/security/Work Order/Defect/WMS/Approval/Actual Cost/closure suites.
- [ ] Run local PostgreSQL 17 empty-schema Flyway chain and live concurrent generation/reservation/outbox/closure/FX immutability probes when executables are available; record exact commands and limitations.
- [ ] Run frontend focused tests, scoped lint, i18n, TypeScript, build, and the repository-wide tests/lint as a disclosed baseline. Never call a red global baseline green.
- [ ] Run `git diff --check`; capture status/logs and separate unrelated concurrent commits.
- [ ] Commit matched report-only files separately in both repositories.
- [ ] Invoke `superpowers:requesting-code-review` for an independent read-only review of the full Stage 3 ranges. Fix every Critical/Important finding test-first in a separate corrective commit and rerun affected plus full Stage 3 gates until approved.
- [ ] Update `.superpowers/sdd/progress.md`; do not push until the full remediation program's publishing gate authorizes it.

## Plan Self-Review Checklist

- RC-01..RC-15: mapped to Tasks 1-13.
- X-01..X-10: cardinality Task 3; ownership/global start Tasks 3/9; approvals Task 6/9; uniqueness Tasks 5/9; dates/status/outbox Task 8; archive Task 7; reconciliation Task 12.
- Source breadth: canonical existing aggregates in Task 2; nonexistent aggregates explicitly retained, not faked.
- PBAC/SoD/idempotency/locking/finance/material/outbox invariants: Tasks 3, 5, 6, 8, 9, 11.
- No placeholders, hidden skip gates, mutable downtime copies, client-authored progress, or manual risk formula are introduced.
