# Parallel-All Approval Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extend the existing TOIR approval engine and UI with backward-compatible sequential and all-must-approve parallel routes.

**Architecture:** `ApprovalTemplate` defines `flowType`; `ApprovalRequest` snapshots route type, round, and template provenance; `ApprovalStep` remains the personal runtime task. `ApprovalService` branches decision aggregation by flow type under a request row lock and continues to finalize documents through the existing `ApprovalActionHandler` registry.

**Tech Stack:** Java 21, Spring Boot 3.3, Spring Data JPA, PostgreSQL/Flyway, JUnit 5/Mockito/Testcontainers, React 19, TypeScript 6, TanStack Query, Vitest, react-i18next.

## Global Constraints

- Preserve all existing endpoints and `SEQUENTIAL` behavior.
- Do not introduce a second approval subsystem.
- `PARALLEL_ALL` supports explicit active users only and does not support hybrid ordered/parallel routes.
- Reject requires a nonblank comment.
- `RETURN` is unsupported for `PARALLEL_ALL`.
- Backend `allowedActions` is authoritative.
- Every resubmission gets a new positive `approvalRound`; historical tasks never participate in current aggregation.
- Final business action executes exactly once.
- Preserve unrelated dirty backend files.

---

### Task 1: Additive persistence model and migration

**Files:**
- Create: `src/main/java/com/toir/enums/ApprovalFlowType.java`
- Create: `src/main/resources/db/migration/V20260727_2__parallel_all_approval.sql`
- Modify: `src/main/java/com/toir/enums/ApprovalDecision.java`
- Modify: `src/main/java/com/toir/entity/ApprovalTemplate.java`
- Modify: `src/main/java/com/toir/entity/ApprovalRequest.java`
- Modify: `src/main/java/com/toir/entity/ApprovalStep.java`
- Test: `src/test/java/com/toir/migration/ParallelAllApprovalMigrationContractTest.java`
- Test: `src/test/java/com/toir/entity/ApprovalOptimisticLockingTest.java`

**Interfaces:**
- Produces: `ApprovalFlowType.SEQUENTIAL`, `ApprovalFlowType.PARALLEL_ALL`.
- Produces: request snapshot fields `flowType`, `approvalRound`, `templateId`, `templateVersion`.
- Produces: step field `approvalRound` and decision `CANCELLED`.

- [ ] Write migration/entity tests asserting sequential backfills, defaults, non-null constraints, indexes, and unique `(request_id, approval_round, approver_id)`.
- [ ] Run the focused tests and confirm failure because columns/enums do not exist.
- [ ] Add the enum, entity mappings, and additive Flyway migration.
- [ ] Run focused migration/entity tests and confirm they pass.

### Task 2: Validate and snapshot route configuration

**Files:**
- Create: `src/main/java/com/toir/service/approval/ApprovalRouteSnapshot.java`
- Modify: `src/main/java/com/toir/dto/approval/ApprovalRuleDto.java`
- Modify: `src/main/java/com/toir/service/approval/ApprovalRuleService.java`
- Modify: `src/main/java/com/toir/service/approval/ApprovalRouteResolver.java`
- Modify: `src/main/java/com/toir/service/approval/DefaultApprovalRouteResolver.java`
- Modify: `src/main/java/com/toir/service/approval/LifecycleRouteResolution.java`
- Modify: `src/main/java/com/toir/service/approval/LifecycleApprovalStartPlan.java`
- Test: `src/test/java/com/toir/service/approval/ApprovalRuleServiceTest.java`
- Test: `src/test/java/com/toir/service/approval/DefaultApprovalRouteResolverTest.java`
- Test: `src/test/java/com/toir/controller/ApprovalRulesControllerTest.java`

**Interfaces:**
- Produces: `ApprovalRouteSnapshot(flowType, templateId, templateVersion, steps)`.
- Consumes: active users from `UserRepository`.
- Produces: `ApprovalRuleDto.flowType` and `ApprovalRuleDto.version`.

- [ ] Add failing tests for empty parallel routes, role assignments, duplicate users, inactive/deleted users, sequential/parallel mixing, and valid snapshots.
- [ ] Run focused tests and verify expected failures.
- [ ] Implement validation and route snapshot resolution while retaining the current sequential fallback.
- [ ] Run focused tests and confirm pass.

### Task 3: Materialize idempotent rounds and parallel tasks

**Files:**
- Modify: `src/main/java/com/toir/repository/ApprovalRequestRepository.java`
- Modify: `src/main/java/com/toir/repository/ApprovalStepRepository.java`
- Modify: `src/main/java/com/toir/service/ApprovalService.java`
- Modify: `src/main/java/com/toir/service/ApprovalScopeService.java`
- Test: `src/test/java/com/toir/service/ApprovalServiceTest.java`
- Test: `src/test/java/com/toir/repository/ApprovalRequestRepositoryPostgresTest.java`

**Interfaces:**
- Produces: `nextApprovalRound(targetType, targetId, actionType)`.
- Produces: one runtime task per snapshot assignment with the request round.
- Preserves: pending-request reuse and target/action advisory locking.

- [ ] Add failing tests for N tasks, pending reuse without duplicates, incremented resubmission round, and historical-task isolation.
- [ ] Run focused tests and verify expected failures.
- [ ] Implement round allocation, request provenance snapshotting, all-assignee notification, and parallel visibility in `ApprovalScopeService`.
- [ ] Run focused tests and confirm pass.

### Task 4: Parallel decision aggregation and exactly-once finalization

**Files:**
- Modify: `src/main/java/com/toir/service/ApprovalService.java`
- Modify: `src/main/java/com/toir/service/ApprovalScopeService.java`
- Modify: `src/main/java/com/toir/service/approval/LifecycleApprovalRoutePolicy.java`
- Test: `src/test/java/com/toir/service/ApprovalServiceTest.java`
- Test: `src/test/java/com/toir/integration/LifecycleApprovalConcurrencyIntegrationTest.java`
- Test: `src/test/java/com/toir/integration/LifecycleApprovalFinalizationRollbackIntegrationTest.java`

**Interfaces:**
- Consumes: `ApprovalRequestRepository.findByIdAndIsDeletedFalseForUpdate`.
- Produces: per-step approve/reject for current round.
- Preserves: `ApprovalActionExecutor.execute(request)` and `executeOnce`.

- [ ] Add failing tests for first/last approve, first reject cancellation, foreign task denial, comment requirement, repeat conflict, unsupported return, and two concurrent final approvals.
- [ ] Run focused tests and verify expected failures.
- [ ] Implement request locking for every target, current-round aggregation, cancellation of remaining tasks, and controlled conflicts.
- [ ] Run focused unit/integration tests and confirm pass.

### Task 5: Extend API projections and my-task endpoint

**Files:**
- Modify: `src/main/java/com/toir/dto/approval/ApprovalRequestDto.java`
- Modify: `src/main/java/com/toir/dto/approval/ApprovalStepDto.java`
- Modify: `src/main/java/com/toir/controller/ApprovalController.java`
- Modify: `src/main/java/com/toir/service/ApprovalService.java`
- Test: `src/test/java/com/toir/controller/ApprovalControllerTest.java`

**Interfaces:**
- Produces: `flowType`, `approvalRound`, counts, `currentUserTaskId`, `allowedActions`, and task rounds.
- Produces: `GET /api/v1/approvals/my-tasks`.

- [ ] Add failing JSON contract tests for sequential compatibility and parallel fields/actions.
- [ ] Run controller tests and verify expected failures.
- [ ] Implement DTO projection and endpoint.
- [ ] Run controller tests and confirm pass.

### Task 6: Add frontend types, form model, and API support

**Files:**
- Modify: `../toir-frontend/src/types/api.ts`
- Modify: `../toir-frontend/src/lib/api.ts`
- Modify: `../toir-frontend/src/modules/hr/libs/approvals/approval-rule-form.ts`
- Test: `../toir-frontend/src/modules/hr/libs/approvals/tests/approval-rule-form.test.ts`
- Test: `../toir-frontend/src/lib/api.test.ts`

**Interfaces:**
- Produces: `ApprovalFlowType`, parallel DTO fields, `CANCELLED`, `getMyApprovalTasks`.
- Produces: validated sequential or parallel template payloads.

- [ ] Add failing tests for parallel form creation, unique active-user payloads, empty/duplicate rejection, and API path.
- [ ] Run focused Vitest tests and verify expected failures.
- [ ] Extend types, API methods, and pure form helpers.
- [ ] Run focused tests and confirm pass.

### Task 7: Implement template editor and parallel runtime UI

**Files:**
- Modify: `../toir-frontend/src/modules/hr/pages/approval-rules-page.tsx`
- Modify: `../toir-frontend/src/modules/hr/pages/approvals-page.tsx`
- Modify: `../toir-frontend/src/modules/hr/pages/approval-detail-page.tsx`
- Modify: `../toir-frontend/src/modules/hr/components/approvals/approvals-table-columns.tsx`
- Modify: `../toir-frontend/src/modules/hr/libs/approvals/runtime-route.ts`
- Modify: `../toir-frontend/src/components/ui/approval-progress-bar.tsx`
- Modify: `../toir-frontend/src/components/ui/approval-history.tsx`
- Modify: `../toir-frontend/src/i18n/locales/ru.json`
- Modify: `../toir-frontend/src/i18n/locales/uz.json`
- Modify: `../toir-frontend/src/i18n/locales/en.json`
- Test: `../toir-frontend/src/modules/hr/libs/approvals/tests/runtime-route.test.ts`
- Test: `../toir-frontend/src/components/ui/approval-progress-bar.test.tsx`
- Test: `../toir-frontend/src/components/ui/approval-section.test.ts`

**Interfaces:**
- Consumes: active users from `api.getUsers`.
- Consumes: backend `allowedActions` and `currentUserTaskId`.
- Produces: parallel badge, aggregate progress, all-approver cards, and correct action refresh.

- [ ] Add failing tests for parallel integrity, progress, cancelled tasks, and backend-authorized task selection.
- [ ] Run focused tests and verify expected failures.
- [ ] Implement flow selector, active-user multiselect, inbox/detail rendering, and action wiring.
- [ ] Add locale strings in all three languages.
- [ ] Run focused tests, `yarn i18n:check`, and confirm pass.

### Task 8: Regression and build verification

**Files:**
- Test only; do not modify unrelated files.

**Interfaces:**
- Verifies all prior tasks as one deployable change.

- [ ] Run backend focused approval, migration, repository, security, lifecycle, and concurrency tests.
- [ ] Run `./mvnw -DskipTests compile`.
- [ ] Run frontend approval tests.
- [ ] Run `yarn lint`, `yarn i18n:check`, and `yarn build`.
- [ ] Review `git diff --check` and confirm existing dirty maintenance-schedule changes remain untouched.
