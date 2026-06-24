# Operational Issue Lifecycle Sync Design

Date: 2026-06-24

## Decision Summary

Operational issues must follow the lifecycle of their source business object in real time. The Problems page should not wait for the nightly scanner when a defect, repair request, or linked work order already reached a terminal state.

The selected design is:

```text
real-time sync + repair request completed/closed final sweep + scanner fallback
```

The source entity remains the source of truth. Operational issues are resolved only when the linked source state proves the issue is resolved. UI selections may display what changed, but they must not be the authority that closes an issue.

## Current Codebase Fit

The backend already has the core pieces:

- `OperationalIssueService.resolveOpen(sourceType, sourceId, resolutionMessage)` resolves one open operational issue for a source.
- `OperationalIssueScannerService.scanInspectionDefects()` resolves `"Defect"` issues when a defect is `RESOLVED`, `CLOSED`, or `CANCELLED`.
- `WorkOrderService.syncDefectOnComplete()` already moves a linked defect to `RESOLVED` after linked work orders become non-active.
- `WorkOrderService.syncRepairRequestOnComplete()` already moves a repair request to `COMPLETED` when all linked work orders are terminal and all linked defects are resolved or closed.
- `RepairRequestService.close()` already validates linked work orders and linked defects before closing a repair request, except for admin override.

This design extends those lifecycle points instead of adding a separate scheduler-only mechanism.

## Rules

### Defect Operational Issues

When a defect reaches one of these terminal states, the open operational issue with `sourceType = "Defect"` and `sourceId = defect.id` must be resolved immediately:

```text
RESOLVED
CLOSED
CANCELLED
```

This applies when the defect is resolved directly, resolved through a linked work order, or closed after all linked work orders reach terminal state.

The resolution message should be short and deterministic, for example:

```text
Defect resolved from linked work order completion.
Defect closed after linked work orders reached terminal state.
Defect was cancelled.
```

### Repair Request Operational Issues

When a repair request reaches `COMPLETED` through linked work order completion, the service must run a final sweep:

- Resolve operational issues for all linked defects that are already terminal.
- Resolve the operational issue with `sourceType = "RepairRequest"` and `sourceId = repairRequest.id` if it exists.
- Do not change defect status in this sweep. It only mirrors terminal states into operational issues.

When a repair request is closed, run the same sweep after close succeeds.

### Admin Override

Admin override must not silently mark open defects as resolved.

If a repair request is closed by admin override while some linked defects are still open, only these operational issues may be resolved:

- the repair request operational issue, if closing the request itself makes that source issue resolved;
- linked defect operational issues whose defect state is already terminal.

Open linked defects keep their operational issues open.

### Scanner Fallback

The existing scanner fallback remains in place. It should continue resolving missed or legacy operational issues when it sees a terminal source state.

The scanner is not the primary mechanism for normal user actions. It is a safety net for missed edge cases, migrations, and interrupted transactions.

## Backend Components

### OperationalIssueLifecycleSyncService

Create a small service dedicated to lifecycle mirroring.

Responsibilities:

- `resolveDefectIssueIfTerminal(Defect defect, String resolutionMessage)`
- `resolveRepairRequestIssue(UUID repairRequestId, String resolutionMessage)`
- `sweepRepairRequest(UUID repairRequestId, String resolutionMessage)`

The service may use:

- `OperationalIssueService`
- `DefectRepository`
- `RepairRequestRepository` only if needed for source lookups

It must not mutate defect or repair request statuses. Existing lifecycle services keep status ownership.

### WorkOrderService Integration

After `syncDefectOnComplete()` saves a defect as `RESOLVED`, call the lifecycle sync service for that defect.

After `syncDefectOnClose()` saves a defect as `CLOSED`, call the lifecycle sync service for that defect.

After `syncRepairRequestOnComplete()` saves a repair request as `COMPLETED`, call `sweepRepairRequest(request.id, ...)`.

### DefectService Integration

After direct defect resolve saves `RESOLVED`, call the lifecycle sync service for that defect.

Direct defect cancel or close flows are outside this implementation unless they already exist as explicit service methods.

### RepairRequestService Integration

After `close()` saves a repair request as `CLOSED`, call `sweepRepairRequest(request.id, ...)`.

The sweep should run after existing validation and status save. If validation fails, no operational issue changes should happen.

## Data Flow

### Work Order Completion

```text
WorkOrder COMPLETE
  -> WorkOrderService syncs linked defect status
  -> Defect becomes RESOLVED when no active linked work orders remain
  -> OperationalIssueLifecycleSyncService resolves sourceType="Defect"
  -> WorkOrderService may complete linked repair request
  -> Final sweep resolves terminal linked defect issues and repair request issue
```

### Repair Request Close

```text
RepairRequest CLOSE request
  -> existing close validation runs
  -> repair request becomes CLOSED
  -> final sweep resolves terminal linked defect issues
  -> final sweep resolves sourceType="RepairRequest" issue
```

## Error Handling

Lifecycle sync should not throw when there is no open operational issue for the source. Missing open issue is a normal state.

If resolving an operational issue fails due to an unexpected persistence error, the parent transaction should fail. The source status and operational issue status must not diverge inside a successful transaction.

## Testing

Add focused backend tests:

- `WorkOrderServiceTest`: completing the last active linked work order resolves the linked defect operational issue.
- `WorkOrderServiceTest`: completing a linked work order does not resolve a defect issue while another active work order remains.
- `WorkOrderServiceTest`: completing a repair request through work order completion runs final sweep for terminal linked defects.
- `DefectServiceTest`: direct defect resolve resolves the linked operational issue.
- `RepairRequestServiceTest`: closing a completed repair request runs final sweep.
- `RepairRequestServiceTest`: admin override close does not resolve operational issues for still-open linked defects.
- `OperationalIssueLifecycleSyncServiceTest`: service is idempotent when no open operational issue exists.

The existing scanner tests should remain valid and continue proving the fallback behavior.

## Non-Goals

This design does not add frontend controls for selecting which defects were completed.

This design does not auto-resolve defects from repair request close. Defect status changes remain owned by defect and work order lifecycle logic.

This design does not remove the nightly scanner.
