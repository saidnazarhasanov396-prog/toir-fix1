# Equipment Node Work Order Target Phase 3E Backend

## Summary
- Work orders now support optional `equipmentNodeId` on create.
- Work orders can target equipment only, or equipment plus one equipment node/component.
- Existing create payloads without `equipmentNodeId` remain compatible.
- Work order responses now expose node reference fields.
- Equipment node delete now fails if active/non-deleted work orders reference the node.

## Files Changed
- `src/main/java/com/toir/entity/maintenance/WorkOrder.java`
- `src/main/java/com/toir/dto/workorder/WorkOrderRequest.java`
- `src/main/java/com/toir/dto/workorder/WorkOrderDto.java`
- `src/main/java/com/toir/repository/WorkOrderRepository.java`
- `src/main/java/com/toir/service/WorkOrderService.java`
- `src/main/java/com/toir/service/equipment/EquipmentNodeService.java`
- `src/test/java/com/toir/service/WorkOrderServiceTest.java`
- `src/test/java/com/toir/controller/WorkOrderControllerContractTest.java`
- `src/test/java/com/toir/service/equipment/EquipmentNodeServiceTest.java`
- `src/test/java/com/toir/model/TriadSchemaModelContractTest.java`
- `logs/equipment-node-work-order-target-phase-3e-frontend-impact.md`

## Migration Added
- `src/main/resources/db/migration/V20260523_8__work_orders_equipment_node_target.sql`
  - Adds nullable `work_orders.equipment_node_id`.
  - Adds partial index `idx_work_orders_equipment_node_id`.
  - Adds FK `fk_work_orders_equipment_node` to `equipment_nodes(id)`.

## DTO / API Contract Changes
- `WorkOrderRequest`
  - Adds optional `equipmentNodeId`.
  - Preserves the previous constructor shape for existing backend tests/callers.
- `WorkOrderDto`
  - Adds:
    - `equipmentNodeId`
    - `equipmentNodeCode`
    - `equipmentNodeName`
    - `equipmentNodeType`
  - Preserves the previous constructor shape for existing backend tests/callers.
- `POST /api/v1/work-orders`
  - Accepts optional `equipmentNodeId`.
- `GET /api/v1/work-orders`
  - List responses include node reference fields when present.
- `GET /api/v1/work-orders/{id}`
  - Detail response includes node reference fields when present.
- Current backend gap:
  - The repository does not currently expose a generic work-order update endpoint or service method. Node target update/clear behavior is therefore not implemented in Phase 3E.

## Validation Rules
- `equipmentNodeId` is optional.
- If provided:
  - equipment node must exist and be non-deleted,
  - equipment node must belong to the same `equipmentId` as the work order,
  - otherwise the service returns controlled not-found or bad-request errors.
- Existing equipment decommission guard is preserved through `EquipmentStatusLifecycleService.assertOperationallyAllowed(..., "create work order")`.
- Existing replacement work-order validation and reservation behavior is unchanged.

## Defect / Repair Request Defaulting Decision
- Defect defaulting is implemented.
- If `defectId` points to a defect with `equipmentNodeId` and the work-order create request omits `equipmentNodeId`, the work order inherits the defect node.
- If the request includes `equipmentNodeId`, the request value is treated as the explicit target and is validated against the work-order equipment.
- Repair request defaulting is not implemented because repair requests currently do not expose an equipment node target.

## Delete Restriction Behavior
- Equipment node delete still rejects:
  - nodes with child nodes,
  - nodes referenced by active/non-deleted defects,
  - nodes referenced by active/non-deleted technical documents.
- Equipment node delete now also rejects nodes referenced by active/non-deleted work orders.
- Conflict message:
  - `Equipment node is referenced by work orders`

## Tests Added Or Updated
- `WorkOrderServiceTest`
  - `createWorkOrder_withEquipmentNode_setsNodeTarget`
  - `createWorkOrder_withNodeFromDifferentEquipment_returnsBadRequest`
  - `createWorkOrder_withoutNode_stillWorks`
  - `createWorkOrder_fromDefectWithNode_inheritsNodeIfImplemented`
- `WorkOrderControllerContractTest`
  - `createWorkOrder_acceptsEquipmentNodeId`
  - `getWorkOrder_returnsEquipmentNodeReference`
- `EquipmentNodeServiceTest`
  - `deleteNode_withReferencedWorkOrder_returnsConflict`
- `TriadSchemaModelContractTest`
  - `workOrderMustExposeEquipmentNodeTarget`

## Commands Run
- `/tmp/apache-maven-3.9.11/bin/mvn -Dtest=WorkOrderServiceTest,WorkOrderControllerContractTest,EquipmentNodeServiceTest,TriadSchemaModelContractTest test`
- `/tmp/apache-maven-3.9.11/bin/mvn clean -Dtest=WorkOrderServiceTest,WorkOrderControllerContractTest,EquipmentNodeServiceTest,TriadSchemaModelContractTest test`
- `git diff --check`
- `/tmp/apache-maven-3.9.11/bin/mvn test`

## Targeted Test Result
- Passed from clean compile.
- Tests run: 123
- Failures: 0
- Errors: 0
- Skipped: 0

## Full Maven Test Status
- Failed because PostgreSQL test database was unavailable at `localhost:5433`.
- Maven summary:
  - Tests run: 1608
  - Failures: 0
  - Errors: 47
  - Skipped: 0
- Root failure observed in DB-backed tests:
  - `Connection to localhost:5433 refused. Check that the hostname and port are correct and that the postmaster is accepting TCP/IP connections.`

## Remaining Backend Risks
- Full repository verification still needs to be rerun with PostgreSQL test DB available.
- Generic work-order update is not present in the current backend; update/set/clear `equipmentNodeId` remains future work unless a work-order update route is added.
- Repair requests do not have node targets, so work orders cannot inherit a node from repair requests yet.

## Frontend Impact Report
- `logs/equipment-node-work-order-target-phase-3e-frontend-impact.md`
