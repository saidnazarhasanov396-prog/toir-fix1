# Work Order Conditional Required Fields Backend Audit

Date: 2026-05-25

## Scope

Audit only. No backend implementation code was changed.

Requirement audited:

- Emergency work orders must require `repairRequestId`.
- Defect-based work orders must require `defectId`.

## Files Inspected

- `src/main/java/com/toir/controller/WorkOrderController.java`
- `src/main/java/com/toir/service/WorkOrderService.java`
- `src/main/java/com/toir/dto/workorder/WorkOrderRequest.java`
- `src/main/java/com/toir/dto/workorder/WorkOrderDto.java`
- `src/main/java/com/toir/entity/maintenance/WorkOrder.java`
- `src/main/java/com/toir/entity/defects/Defect.java`
- `src/main/java/com/toir/enums/WorkOrderType.java`
- `src/main/java/com/toir/enums/WorkType.java`
- `src/main/java/com/toir/enums/PriorityLevel.java`
- `src/main/java/com/toir/enums/WorkOrderStatus.java`
- `src/main/java/com/toir/exception/RestException.java`
- `src/main/java/com/toir/exception/GlobalExceptionHandler.java`
- `src/main/java/com/toir/exception/ErrorResponse.java`
- `src/main/java/com/toir/repository/defects/DefectRepository.java`
- `src/main/java/com/toir/repository/repair/RepairRequestRepository.java`
- `src/main/resources/db/migration/V20260515_1__work_orders_repair_request_fk.sql`
- `src/main/resources/db/migration/V20260516_1__repair_defect_work_order_triad_cleanup.sql`
- `src/test/java/com/toir/controller/WorkOrderControllerContractTest.java`
- `src/test/java/com/toir/service/WorkOrderServiceTest.java`

## Current Endpoint Contracts

### Create Work Order

Endpoint:

```http
POST /api/v1/work-orders
```

Controller:

- `WorkOrderController.create(@Valid @RequestBody WorkOrderRequest request)`
- Returns `201 Created`
- Response body: `WorkOrderDto`

Request DTO:

```java
public record WorkOrderRequest(
    @NotBlank String number,
    @NotBlank String title,
    @NotNull UUID equipmentId,
    UUID equipmentNodeId,
    @NotNull UUID departmentId,
    UUID repairRequestId,
    UUID defectId,
    UUID pprTaskId,
    UUID contractorId,
    @NotNull WorkOrderType type,
    WorkType workType,
    UUID warehouseId,
    UUID replacementEquipmentId,
    PriorityLevel priority,
    Instant startPlannedAt,
    Instant endPlannedAt,
    @NotNull UUID createdById,
    String summary
)
```

Important naming:

- Backend request field is `type`, not `workOrderType`.
- Backend request field is `workType`.
- Backend link fields are `repairRequestId` and `defectId`.

Response DTO includes:

- scalar IDs: `repairRequestId`, `defectId`, `equipmentId`, `equipmentNodeId`, `departmentId`
- enums: `type`, `workType`, `priority`, `status`
- linked objects: `repairRequest`, `defect`

### Other Work Order Endpoints

- `GET /api/v1/work-orders`
- `GET /api/v1/work-orders/stats`
- `GET /api/v1/work-orders/mobile-feed`
- `GET /api/v1/work-orders/{id}`
- `POST /api/v1/work-orders/{id}/approve`
- `POST /api/v1/work-orders/{id}/start`
- `POST /api/v1/work-orders/{id}/complete`
- `POST /api/v1/work-orders/{id}/close`

## Exact DTO Fields and Enums

### Work Order Type

Backend enum:

```java
public enum WorkOrderType {
    PLANNED, EMERGENCY, DEFECT, OVERHAUL, INSPECTION
}
```

Emergency is represented as:

```text
type = EMERGENCY
```

Defect-based work order is represented as:

```text
type = DEFECT
```

This is separate from `WorkType`.

### Work Type

Backend enum:

```java
public enum WorkType {
    REPAIR,
    REPLACEMENT,
    DIAGNOSTICS
}
```

`WorkType` describes the work kind. It does not currently define defect-based creation.

### Priority and Status

Priority enum:

```java
LOW, MEDIUM, HIGH, CRITICAL, EMERGENCY
```

Work order status enum:

```java
DRAFT, PLANNED, APPROVED, IN_PROGRESS, SUSPENDED, COMPLETED, CLOSED, CANCELLED
```

`PriorityLevel.EMERGENCY` exists, but the PM requirement maps to `WorkOrderType.EMERGENCY`, not priority.

## Current Validation Behavior

### DTO Bean Validation

`WorkOrderRequest` currently requires:

- `number`
- `title`
- `equipmentId`
- `departmentId`
- `type`
- `createdById`

`repairRequestId` and `defectId` are not annotated as globally required because they are conditional.

### Service Validation

`WorkOrderService.create(...)` currently calls:

```java
validateTypeRequiredRelations(request);
```

Current conditional validation:

```java
if (request.type() == WorkOrderType.EMERGENCY && request.repairRequestId() == null) {
    throw RestException.badRequest("repairRequestId is required when work order type is EMERGENCY");
}
if (request.type() == WorkOrderType.DEFECT && request.defectId() == null) {
    throw RestException.badRequest("defectId is required when work order type is DEFECT");
}
```

So the backend already enforces the two requested conditional rules in the current branch.

### Link Validation

`validateCreateRelations(...)` currently validates:

- selected repair request exists and is not deleted
- repair request status is not `REJECTED`, `CLOSED`, or `CANCELLED`
- repair request equipment matches `equipmentId` when both are present
- selected defect exists and is not deleted
- defect equipment matches `equipmentId` when both are present
- if selected defect has `repairRequestId`, request must include the same `repairRequestId`

This means:

- If `defectId` is selected and the defect belongs to a repair request, `repairRequestId` is also required.
- If `defectId` belongs to a different repair request, backend rejects with `400`.
- If `defectId` belongs to different equipment, backend rejects with `400`.

### Error Format

`RestException.badRequest(...)` is handled by `GlobalExceptionHandler` and returns:

```json
{
  "message": "...",
  "path": "/api/v1/work-orders",
  "timestamp": "...",
  "code": 400
}
```

Bean validation failures also return `400` with the same response shape.

## Current Tests

Existing service tests already cover:

- `createEmergencyWorkOrderWithoutRepairRequestReturns400`
- `createDefectWorkOrderWithoutDefectReturns400`
- `createWithoutRepairRequestAndDefectKeepsExistingBehavior`
- `createWithValidRepairRequestSucceeds`
- `createWithValidDefectSucceeds`
- `createWithRepairRequestAndMatchingDefectSucceeds`
- `createWithRepairRequestAndDifferentDefectRequestReturns400`
- `createWithDefectFromDifferentEquipmentReturns400`
- `createWithRepairRequestFromDifferentEquipmentReturns400`
- `createWithDefectLinkedToRepairRequestRequiresRepairRequestId`

Existing controller tests cover:

- unknown repair request returns `404`
- unknown defect returns `404`
- mismatched repair request and defect returns `400`

Gap:

- Controller contract tests do not appear to directly assert `type=EMERGENCY` without `repairRequestId` returns `400`.
- Controller contract tests do not appear to directly assert `type=DEFECT` without `defectId` returns `400`.

## Exact Condition for Emergency

Recommended and current backend condition:

```java
request.type() == WorkOrderType.EMERGENCY
```

Required field:

```java
repairRequestId != null
```

Do not use:

- `priority == EMERGENCY`
- `workType`
- title/name text

## Exact Condition for Defect-Based Flow

Recommended and current backend condition:

```java
request.type() == WorkOrderType.DEFECT
```

Required field:

```java
defectId != null
```

Important distinction:

- A non-`DEFECT` work order may still link a `defectId`; current backend allows this if relation checks pass.
- The PM phrase "defect-based work order" should map to `WorkOrderType.DEFECT`, not `WorkType` and not merely "any request that has a defectId".

## Proposed Backend Validation Changes

The core backend validation is already implemented in the current branch.

Recommended backend implementation work, if any:

1. Keep `validateTypeRequiredRelations(...)` in `WorkOrderService.create(...)`.
2. Add controller contract tests for the two conditional `400` cases if not already present.
3. Keep response contract unchanged.
4. Keep database columns nullable; this is conditional business validation, not a universal DB `NOT NULL`.

## Required Tests

Required backend tests for final implementation acceptance:

- `Emergency without repairRequestId returns 400`
  - Already covered at service level.
  - Add controller contract coverage.
- `Emergency with repairRequestId succeeds`
  - Current generic `createWithValidRepairRequestSucceeds` covers valid repair request but not explicitly `type=EMERGENCY`; add explicit service test if stricter coverage is required.
- `Non-Emergency without repairRequestId still works if allowed`
  - Already covered by `createWithoutRepairRequestAndDefectKeepsExistingBehavior`.
- `Defect-based without defectId returns 400`
  - Already covered at service level.
  - Add controller contract coverage.
- `Defect-based with defectId succeeds`
  - Current generic `createWithValidDefectSucceeds` covers valid defect but not explicitly `type=DEFECT`; add explicit service test if stricter coverage is required.
- `defectId must belong to repairRequest/equipment if applicable`
  - Already covered at service level.

## API Compatibility Risk

Backend conditional validation may reject requests previously accepted by older frontend code:

- `type=EMERGENCY` without `repairRequestId`
- `type=DEFECT` without `defectId`

This is intended by PM requirement, but the frontend must prevent these invalid submissions and show conditional required labels.

No response shape change is needed.

## Risk List

- Frontend currently sends the backend field as `type`, so backend naming is compatible; UI text may call it "Work Order Type".
- If existing integrations create `EMERGENCY` work orders without repair requests, those integrations now receive `400`.
- If existing integrations create `DEFECT` work orders without defects, those integrations now receive `400`.
- `PriorityLevel.EMERGENCY` could confuse implementers; do not use priority for this PM rule.
- Defects can technically exist without repair request; backend only requires `repairRequestId` with `defectId` when the selected defect itself has a repair request.

## Implementation Phases

### Phase 1: Confirm Backend Coverage

- Keep current `WorkOrderService.validateTypeRequiredRelations(...)`.
- Add missing controller contract tests for direct `400` cases.

### Phase 2: Frontend Alignment

- Add conditional UI required labels and Step 1 validation.
- Prevent known-invalid submissions before backend request.

### Phase 3: Relation Safety

- Keep backend validation for repair request/equipment/defect consistency.
- Ensure frontend loading filters mirror backend constraints.

### Phase 4: Regression Verification

- Run targeted backend WorkOrder service/controller tests.
- Run frontend Work Order validation tests and build.

## Verification

No tests were run for this audit because the task requested audit only and no implementation changes.

Commands used were read-only inspection commands (`rg`, `sed`, `git status`).

## Push Status

No push was performed.
