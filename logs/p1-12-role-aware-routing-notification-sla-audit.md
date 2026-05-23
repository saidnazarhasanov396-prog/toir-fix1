# P1-12 Role-Aware Routing, Notification, and SLA Audit

Branch: `cadex-toir-p1-12`

## Current Notification Model

- Notifications are stored in `notifications` with a required `recipient_id`, `title`, `message`, `channel`, `status`, `severity`, `entity_type`, `entity_id`, and `read_at`.
- DTO shape is `NotificationDto(id, recipientId, title, message, channel, status, severity, entityType, entityId, readAt)`.
- `SENT` currently behaves as unread; `READ` is set by `markRead`.
- There is no schema support for role-recipient, department-recipient, action path metadata, acknowledgement, or escalation ownership in the base notification entity.
- Frontend types already tolerate richer optional fields (`recipientRoleCode`, `department`, `recipientUser`, `metadata`), but backend base DTO does not emit them.

## Recipient Model

- `NotificationService.send` creates direct user notifications only.
- `NotificationFacadeService.list/summary/unreadCount` operate by a supplied recipient UUID.
- `NotificationController` currently accepts `recipientId` on list and unread-count requests and does not clamp it to the current user for non-admin users.
- `NotificationController.markRead` currently marks by notification id without verifying the current user owns the notification.
- `OverdueDetectorService` resolves username `admin` and sends overdue notifications there when present. This is not role-aware and should not be expanded without routing changes.

## Current Creation Points

- Direct notification creation exists in `NotificationService.send`.
- `OverdueDetectorService.evaluate` creates overdue notifications for PPR tasks, repair requests, work orders, calibration records, and user certifications, routed to `admin`.
- Financial review pages and services have notification-adjacent APIs, but base notification workflow events are not wired into approval, repair request assignment, procurement submission, inspection failure triage, or actual cost creation.

## Routing Gaps

- Repair request creation does not notify department maintainers/assigners.
- Repair request assignment does not notify the assigned user.
- Generic approval request creation does not notify the current approver.
- Generic approval final decision does not notify the requester/responsible party.
- Inspection FAIL triage creates/reuses defect and repair request records but does not notify department maintenance/defect readers.
- Actual cost creation sets `PENDING` but does not notify finance reviewers.
- Procurement submission is lifecycle-enforced and approval-integrated in controllers, but no direct notification is generated except any future approval hook.
- PPR plan approval submission is approval-integrated in controllers, but no direct notification is generated except any future approval hook.

## SLA/Overdue Fields Available

- Repair requests: `targetCompletionAt`, `actualCompletionAt`, `status`, `priority`, `departmentId`, `reporterId`, `assignedToId`.
- Work orders: `startPlannedAt`, `endPlannedAt`, `startedAt`, `completedAt`, `status`, `priority`, `departmentId`, `createdById`, `approvedById`.
- PPR tasks: `scheduledStart`, `scheduledEnd`, `dueDate`, `status`, parent plan department.
- Approval requests: `createdAt`, `status`, `currentStep`, `completedAt`; no due date exists.
- Procurement requests: `requiredBy`, `submittedAt`, `approvedAt`, `orderedAt`, `receivedAt`, `status`, `departmentId`, `warehouseId`.
- Actual costs: `createdAt`, `reviewedAt`, `status`, linked work order/repair request/budget line for department inference; no explicit due date exists.

## PBAC/Security Gaps

- Notification list/unread endpoints can be queried for another user by passing `recipientId`.
- Notification mark-read is not recipient-scoped.
- Department-scoped recipient resolution is absent.
- Existing `ScopeAccessService` preserves `SYSTEM_ADMIN` and `*` behavior and can be reused to clamp current-user notification access.
- `UserRepository.findAllWithRolesAndIsDeletedFalse` and role permission JSON provide enough schema support to resolve role/permission recipients by department.

## Frontend Display Gaps

- Header badge reads `/notifications/summary` and displays unread count.
- Notifications page reads `/notifications`, supports status/severity/entity filters, and marks notifications read.
- Notification action links currently support `Equipment`, `WorkOrder`, `RepairRequest`, `PprTask`, `Defect`, `ContractorWork`, and `ActualCost`.
- `WorkOrder` notification links go to print view, not the detail/work queue.
- `RepairRequest`, `PprTask`, and `Defect` links do not include document ids when available.
- Link helper is local to `notifications-page.tsx`, so it is not directly unit-tested.

## Safe Implementation Candidates

- Keep the base notification DTO shape stable.
- Add backend routing helpers that create direct user notifications only after resolving explicit users or department users with required permissions.
- Resolve explicit employee assignment through `Employee.userId`.
- Avoid notifying `SYSTEM_ADMIN`/`*` users by default when department-specific recipients exist.
- Add duplicate prevention for open notifications with same recipient, entity type, entity id, and title.
- Clamp notification list/unread/mark-read to current recipient unless the current user is `SYSTEM_ADMIN` or has `*`.
- Hook notification creation into:
  - repair request creation and assignment,
  - approval request creation and next-step/final decision,
  - inspection FAIL triage creation,
  - actual cost pending creation.
- Update dashboard PPR overdue counting to compute overdue from existing dates and terminal statuses rather than relying only on persisted `OVERDUE`.
- Export and test frontend notification link builder; add document-aware links where existing route shapes support it.

## Follow-Up Candidates Requiring Schema or Larger Design

- Role-recipient notification rows.
- Department-recipient notification rows.
- Notification metadata/action-path persistence.
- SLA due-date policies for approvals and actual costs.
- Scheduler-backed escalation/reminder engine.
- Bulk notification export and financial inbox endpoints currently referenced by frontend but not part of base notification controller.
- Global admin escalation policy that avoids alert fatigue while still surfacing unowned events.

## Initial Risk Level

MEDIUM

Reasons:
- P1-12 touches workflow-adjacent services that already enforce lifecycle gates.
- Existing notification endpoints have recipient/PBAC gaps.
- Safe routing is possible without migrations, but role-aware fan-out must avoid broad admin spam and avoid weakening `SYSTEM_ADMIN`/`*`.
- SLA support is partially present but inconsistent between read-only dashboard counters and the mutating overdue detector.

## Tests To Add

- Notification service duplicate prevention and department-permission recipient resolution.
- Notification controller rejects or clamps non-admin cross-recipient list/unread/mark-read access.
- Repair request assignment creates a notification for the assigned user.
- Approval creation notifies current approver; terminal decision notifies requester.
- Inspection FAIL triage notifies department defect/inspection recipients.
- Actual cost pending creation notifies finance reviewer when resolvable.
- Dashboard overdue PPR excludes terminal statuses and respects department scope.
- Frontend notification link builder covers work orders, repair requests, PPR tasks/plans, procurement, budgets, approvals, actual costs, and missing ids.
