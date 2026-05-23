# P1-12 Role-aware routing and notification/SLA hardening fix report

## 1. Audit summary

- Audit report: `logs/p1-12-role-aware-routing-notification-sla-audit.md`.
- Current notification model is user-recipient based (`recipientId`) with read state through `NotificationStatus.READ`; DTO/API shape was kept compatible.
- Pre-change gaps were global/unguarded notification reads, limited business-event routing, no duplicate prevention for workflow notifications, and frontend links missing several document types.
- Existing SLA-safe fields were reused only where available. No new schema or migrations were introduced.

## 2. Backend changes

- `NotificationService` now supports user-targeted, employee-resolved, and department+permission routed workflow notifications.
- `NotificationController` now clamps non-admin notification reads and mark-read operations to the current recipient. `SYSTEM_ADMIN` and `*` behavior is preserved for broad access.
- `ApprovalService` now notifies the current approver on approval creation/reuse and notifies the requester when an approval is finally approved/rejected.
- `RepairRequestService` now notifies department assigners on create and the assigned user on assignment.
- `ActualCostService` now routes pending actual-cost review notifications to department finance approvers when a department can be resolved from the linked work order, repair request, or budget.
- `InspectionService` now routes FAIL-created defect/triage notifications to the responsible department through `DEFECT_READ`, falling back to `INSPECTION_READ`.
- `DashboardService` now computes PPR overdue visibility from existing `dueDate`/`OVERDUE` state while excluding terminal statuses.

## 3. Frontend changes

- Notification link resolution now covers `ApprovalRequest`, `WorkOrder`, `RepairRequest`, `PprTask`, `PprPlan`, `ProcurementRequest`, `MaintenanceBudget`, and `ActualCost`.
- Existing metadata `actionPath` remains authoritative when present.
- Missing document data safely renders without a link.
- No route guard, permission gate, or 403/logout behavior was weakened.

## 4. Notification routing matrix

| Event | Recipient policy |
| --- | --- |
| Repair Request created | Department users with `REPAIR_REQUEST_ASSIGN` |
| Repair Request assigned | Explicit assigned user |
| Approval requested/reused | Current approval step approver |
| Approval final approved/rejected | Approval requester |
| Actual Cost created as `PENDING` | Department users with `ACTUAL_COST_APPROVE` when department resolves |
| Inspection FAIL triage/defect created | Department users with `DEFECT_READ`, fallback `INSPECTION_READ` |

## 5. SLA/overdue rules

- No new due dates were invented.
- PPR overdue dashboard count uses existing `dueDate` before now or persisted `OVERDUE`.
- Terminal PPR task statuses `COMPLETED` and `CANCELLED` are excluded.
- Existing department scoping in dashboard queries remains authoritative.

## 6. Recipient resolution behavior

- Explicit user recipients are preferred when available.
- Employee recipients resolve through employee-to-user mapping.
- Department routing requires same department plus matching role code or permission.
- Non-admin department recipients are preferred. `SYSTEM_ADMIN` and `*` are recognized and preserved, but they are not globally spammed by default.
- Cross-department notification fanout was not added.

## 7. Duplicate prevention behavior

- Workflow notification helpers skip duplicate open notifications for the same recipient, entity type, entity id, and title while status is `PENDING` or `SENT`.
- Existing direct `send(...)` behavior was left compatible.

## 8. Tests run

Backend:

- `mvn clean test -Dtest=NotificationServiceTest,NotificationControllerContractTest,ApprovalPbacScopeTest,RepairRequestServiceTest,ActualCostServiceTest,InspectionServiceTest,SecurityAccessServiceTest,ScopeAccessServiceTest` -> passed, 117 tests.
- `mvn test -Dtest=*Notification*Test,*Approval*Test,*RepairRequest*Test,*WorkOrder*Test` -> 282 tests passed, 7 errors caused by PostgreSQL unavailable at `localhost:5433`.
- `mvn test -Dtest=*Ppr*Test,*Procurement*Test,*ActualCost*Test,*Inspection*Test` -> passed, 231 tests.
- `mvn test -Dtest=SecurityAccessServiceTest,ScopeAccessServiceTest` coverage is included in the clean targeted run.

Frontend:

- `yarn test -- notification` -> passed, 3 tests.
- `yarn test -- approval` -> passed, 7 tests.
- `yarn test -- repair-request` -> passed, 8 tests.
- `yarn test -- work-order` -> passed, 17 tests.
- `yarn test -- ppr` -> passed, 19 tests.
- `yarn test -- procurement` -> passed, 10 tests.
- `yarn test -- actual-cost` -> passed, 7 tests.
- `yarn test -- inspection` -> passed, 8 tests.
- `yarn test -- routes-rbac app-shell-rbac access-control permission-gate` -> passed, 124 tests.
- `yarn build` -> passed.

General:

- Backend `git diff --check` -> passed.
- Frontend `git diff --check` -> passed.
- Frontend conflict marker scan -> clean.
- Backend source conflict marker scan excluding `logs/**` -> clean. The all-repo scan only matched historical S1 log text that quotes the marker-scan command.

## 9. Known blockers

- Local PostgreSQL is not listening at `localhost:5433` (`nc -z localhost 5433` failed).
- DB-backed repository stats tests failed only because the Spring test context could not obtain a JDBC connection:
  - `WorkOrderRepositoryStatsTest`: 5 errors.
  - `RepairRequestRepositoryStatsTest`: 2 errors.
- Full `mvn test` was not run after confirming the required local DB was unavailable.

## 10. What was not changed

- No demo reset SQL/scripts/logs were touched.
- No old Flyway migrations were edited.
- No new migrations were added.
- No scheduler, async notification engine, escalation engine, or global admin fanout was introduced.
- No DTO response shape changes were made.
- Existing RBAC/PBAC, lifecycle gates, generic approval integration, `SYSTEM_ADMIN`, and `*` semantics were preserved.

## 11. Next recommendation

Proceed to P1-13: Final business workflow smoke checklist and release readiness review, after rerunning DB-backed backend tests with PostgreSQL available at `localhost:5433`.
