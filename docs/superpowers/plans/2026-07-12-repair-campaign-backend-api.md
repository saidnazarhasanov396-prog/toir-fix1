# Repair Campaign Backend API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the repair-campaign backend contracts already consumed by `origin/fix/finance-module-style` and push the verified result to `origin/Codex_org`.

**Architecture:** Extend existing repair-campaign, defect, and attachment boundaries instead of duplicating them. Add a focused risk aggregate with nested REST endpoints, persistence, scope checks, and audit logging.

**Tech Stack:** Java 21, Spring Boot, Spring MVC/Security/Data JPA, PostgreSQL/Flyway, JUnit 5, Mockito, Maven.

## Global Constraints

- Preserve requests to `POST /repair-campaigns/{id}/close` with no body.
- Reuse the standard `DefectResponse`/Spring `Page` contract.
- Do not create `/repair-campaigns/{id}/documents`; use attachment groups with `REPAIR_CAMPAIGN`.
- Read authority is `REPAIR_CAMPAIGN_READ`; risk/document mutation authority is `REPAIR_CAMPAIGN_UPDATE`.
- Risk status flow is `OPEN -> MITIGATING -> CLOSED` or `OPEN -> ACCEPTED`.
- Do not implement future Phase 5 close-readiness expansion in this change.

---

### Task 1: Campaign attachment target

**Files:**
- Modify: `src/main/java/com/toir/enums/AttachmentTargetType.java`
- Modify: `src/main/java/com/toir/service/attachment/AttachmentTargetAccessService.java`
- Modify: `src/main/java/com/toir/controller/AttachmentGroupController.java`
- Test: `src/test/java/com/toir/service/attachment/AttachmentTargetAccessServiceTest.java`
- Test: `src/test/java/com/toir/controller/AttachmentGroupControllerContractTest.java`

**Interfaces:**
- Consumes: `RepairCampaignRepository.findByIdAndIsDeletedFalse(UUID)` and `ScopeAccessService.assertCanAccessDepartment(UUID)`.
- Produces: `AttachmentTargetType.REPAIR_CAMPAIGN`, mapped to `FileCategory.DOCUMENT`.

- [ ] Add failing tests that parse `REPAIR_CAMPAIGN`, enforce campaign department scope, and allow read/update authorities in controller contracts.
- [ ] Run `./mvnw -Dtest=AttachmentTargetAccessServiceTest,AttachmentGroupControllerContractTest test` and confirm failures are due to missing campaign support.
- [ ] Add the enum value, repository dependency, access branch, document category, and controller authorities.
- [ ] Re-run the focused tests and confirm they pass.

### Task 2: Optional campaign close notes

**Files:**
- Create: `src/main/java/com/toir/dto/repaircampaign/RepairCampaignCloseRequest.java`
- Modify: `src/main/java/com/toir/entity/repair/RepairCampaign.java`
- Modify: `src/main/java/com/toir/dto/repaircampaign/RepairCampaignDto.java`
- Modify: `src/main/java/com/toir/controller/repair/RepairCampaignController.java`
- Modify: `src/main/java/com/toir/service/repair/RepairCampaignService.java`
- Create: `src/main/resources/db/migration/V20260712_7__repair_campaign_reporting_api.sql`
- Test: `src/test/java/com/toir/controller/RepairCampaignControllerContractTest.java`
- Test: `src/test/java/com/toir/service/RepairCampaignServiceTest.java`

**Interfaces:**
- Produces: `RepairCampaignService.close(UUID, RepairCampaignCloseRequest)` with a compatibility overload `close(UUID)` if existing callers need it.
- Produces: `RepairCampaignDto.closingNotes` and database column `repair_campaigns.closing_notes`.

- [ ] Add failing controller tests for missing body, `{}`, and `{ "notes": " summary " }`, plus a service test that persists trimmed notes.
- [ ] Run the two focused test classes and verify the new assertions fail for the missing contract.
- [ ] Add the request record, optional request binding, entity/DTO field, service persistence, and migration column.
- [ ] Re-run the focused tests and keep existing no-body behavior green.

### Task 3: Campaign defect aggregation

**Files:**
- Modify: `src/main/java/com/toir/repository/defects/DefectRepository.java`
- Modify: `src/main/java/com/toir/service/defects/DefectService.java`
- Modify: `src/main/java/com/toir/controller/repair/RepairCampaignController.java`
- Test: `src/test/java/com/toir/service/defects/DefectServiceTest.java`
- Test: `src/test/java/com/toir/controller/RepairCampaignControllerContractTest.java`

**Interfaces:**
- Produces: `Page<DefectResponse> DefectService.searchByRepairCampaign(UUID, DefectStatus, String, int, int)`.
- Produces: `GET /api/v1/repair-campaigns/{id}/defects?status=&severity=&page=&size=`.

- [ ] Add failing service tests for campaign JOIN filtering and response enrichment, and controller tests for query binding and read authority.
- [ ] Run the focused tests and confirm the endpoint/method is missing.
- [ ] Add a distinct native JOIN query through `work_orders.defect_id`, campaign existence/scope validation, response reuse, and controller endpoint.
- [ ] Re-run focused tests and confirm Spring Page metadata and filters pass.

### Task 4: Campaign risk CRUD

**Files:**
- Create: `src/main/java/com/toir/enums/RepairCampaignRiskLevel.java`
- Create: `src/main/java/com/toir/enums/RepairCampaignRiskStatus.java`
- Create: `src/main/java/com/toir/entity/repair/RepairCampaignRisk.java`
- Create: `src/main/java/com/toir/repository/repair/RepairCampaignRiskRepository.java`
- Create: `src/main/java/com/toir/dto/repaircampaign/RepairCampaignRiskCreateRequest.java`
- Create: `src/main/java/com/toir/dto/repaircampaign/RepairCampaignRiskUpdateRequest.java`
- Create: `src/main/java/com/toir/dto/repaircampaign/RepairCampaignRiskResponse.java`
- Create: `src/main/java/com/toir/service/repair/RepairCampaignRiskService.java`
- Create: `src/main/java/com/toir/controller/repair/RepairCampaignRiskController.java`
- Modify: `src/main/resources/db/migration/V20260712_7__repair_campaign_reporting_api.sql`
- Create: `src/test/java/com/toir/service/repair/RepairCampaignRiskServiceTest.java`
- Create: `src/test/java/com/toir/controller/RepairCampaignRiskControllerContractTest.java`

**Interfaces:**
- Produces: list/create/update/delete methods scoped by both campaign ID and risk ID.
- Produces: response fields `id`, `campaignId`, `title`, `description`, `likelihood`, `impact`, `status`, `ownerId`, `ownerName`, `mitigationPlan`, `dueDate`, `createdAt`, `updatedAt`.

- [ ] Add failing service tests for OPEN creation, bulk owner names, legal/illegal transitions, cross-campaign lookup, owner validation, and soft delete.
- [ ] Add failing controller contract/security tests for all four endpoints and expected status codes.
- [ ] Run focused tests and confirm failures are caused by missing production types.
- [ ] Add enums, entity, repository, DTOs, service, controller, schema constraints/indexes, and audit records.
- [ ] Re-run focused tests and refactor only after they pass.

### Task 5: Defect-list filters and optional campaign defect list

**Files:**
- Modify: `src/main/java/com/toir/controller/defects/DefectListController.java`
- Modify: `src/main/java/com/toir/service/defects/DefectListService.java`
- Modify: `src/main/java/com/toir/repository/defects/DefectListRepository.java`
- Modify: `src/main/java/com/toir/service/WorkOrderService.java` or the campaign work-order validation owner identified by tests.
- Modify: `docs/repair-campaign-business-logic.md`
- Test: `src/test/java/com/toir/controller/DefectListControllerContractTest.java`
- Test: existing work-order/campaign service test class owning defect-list validation.

**Interfaces:**
- Produces: `GET /api/v1/defect-lists?equipmentId=&status=&page=&size=&search=`.
- Preserves validation for a supplied `defectListId`, but removes the unconditional approved-list requirement for `MEDIUM_REPAIR`/`CAPITAL_REPAIR`.

- [ ] Add failing controller/service tests proving status is forwarded and both status/equipment filters apply.
- [ ] Add a failing campaign work-order test proving medium/capital repair creation works without `defectListId` and supplied invalid lists remain rejected.
- [ ] Run focused tests and verify expected failures.
- [ ] Extend controller/service/repository status filtering and relax only the unconditional requirement.
- [ ] Update business documentation to state the optional-at-creation rule and re-run focused tests.

### Task 6: Verification and delivery

**Files:**
- Review all changed files and `docs/superpowers/specs/2026-07-12-repair-campaign-backend-api-design.md`.

- [ ] Run all focused repair-campaign, defect, attachment, and risk tests with JDK 24 targeting Java 21.
- [ ] Run `./mvnw test` and inspect the full exit code/output.
- [ ] Run `./mvnw -DskipTests package` and inspect the full exit code/output.
- [ ] Review `git diff --check`, migration ordering, `git status`, and the requirements checklist.
- [ ] Commit the implementation with an intentional message.
- [ ] Push verified `HEAD` to `origin/Codex_org` without force.

