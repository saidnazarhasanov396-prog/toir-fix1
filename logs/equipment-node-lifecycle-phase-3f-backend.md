# Equipment Node Lifecycle Phase 3F Backend Report

Date: 2026-05-23

## Implementation status

Implemented backend read API for equipment node lifecycle / repair history aggregation.

## Endpoint added

- `GET /api/v1/equipment-nodes/{nodeId}/lifecycle`
- Endpoint decision: `/lifecycle` was chosen instead of `/history` because the response includes current node summary, related records, counts, and timeline data.

## DTOs added

- `EquipmentNodeLifecycleDto`
  - `node`
  - `counts`
  - `defects`
  - `workOrders`
  - `documents`
  - `timeline`
- Nested DTO records:
  - `NodeSummary`
  - `Counts`
  - `DefectItem`
  - `WorkOrderItem`
  - `DocumentItem`
  - `TimelineItem`

## Repositories and service methods added

- Added `EquipmentNodeLifecycleService#getLifecycle(UUID nodeId, boolean includeTimeline, Integer limit)`.
- Added `DefectRepository#findAllByEquipmentNodeIdAndIsDeletedFalseOrderByUpdatedAtDesc(UUID equipmentNodeId)`.
- Added `WorkOrderRepository#findAllByEquipmentNodeIdAndIsDeletedFalseOrderByUpdatedAtDesc(UUID equipmentNodeId)`.
- Reused existing `TechnicalDocumentRepository#findAllByEquipmentNodeIdAndIsDeletedFalse(UUID equipmentNodeId)`.
- Reused `FileAssetRepository#findAllByIdInAndIsDeletedFalse(...)` to populate document file references when present.

## Response structure

- `node`
  - `id`
  - `equipmentId`
  - `parentId`
  - `code`
  - `name`
  - `nodeType`
  - `serialNumber`
- `counts`
  - `defects`
  - `workOrders`
  - `documents`
- `defects`
  - `id`
  - `code`
  - `title`
  - `status`
  - `severity`
  - `createdAt`
  - `updatedAt`
- `workOrders`
  - `id`
  - `number`
  - `title`
  - `status`
  - `type`
  - `workType`
  - `priority`
  - `createdAt`
  - `updatedAt`
- `documents`
  - `id`
  - `title`
  - `type`
  - `revision`
  - `documentDate`
  - `file`
  - `createdAt`
  - `updatedAt`
- `timeline`
  - `type`
  - `id`
  - `title`
  - `status`
  - `metadata`
  - `occurredAt`
  - `sourceCreatedAt`

## Filters and query params

- `includeTimeline`: optional boolean, default `true`.
- `limit`: optional integer, default `50`, maximum `200`.
- `limit` applies to each related record section and to the timeline.
- Values below `1` fall back to the default limit.

## Validation and permission behavior

- Node lookup uses `EquipmentNodeRepository#findByIdAndIsDeletedFalse`.
- Missing or deleted node returns controlled `404 Not Found`.
- Endpoint is guarded with the existing read authority pattern:
  - `SYSTEM_ADMIN`
  - `*`
  - `EQUIPMENT_READ`
- No new permissions were introduced.

## Timeline behavior

- Timeline contains `DEFECT`, `WORK_ORDER`, and `DOCUMENT` items.
- Sort order is descending by `occurredAt`.
- `occurredAt` uses `updatedAt` when present, otherwise `createdAt`.
- Items without a usable date are omitted from the timeline.

## Files changed

- `src/main/java/com/toir/controller/equipment/EquipmentNodeController.java`
- `src/main/java/com/toir/dto/equipmentnode/EquipmentNodeLifecycleDto.java`
- `src/main/java/com/toir/repository/WorkOrderRepository.java`
- `src/main/java/com/toir/repository/defects/DefectRepository.java`
- `src/main/java/com/toir/service/equipment/EquipmentNodeLifecycleService.java`
- `src/test/java/com/toir/controller/equipment/EquipmentNodeControllerContractTest.java`
- `src/test/java/com/toir/service/equipment/EquipmentNodeLifecycleServiceTest.java`
- `logs/equipment-node-lifecycle-phase-3f-backend.md`
- `logs/equipment-node-lifecycle-phase-3f-frontend-impact.md`

## Migration

- No migration added. Phase 3F is read-only aggregation over Phase 3C, 3D, and 3E node target columns.

## Tests added or updated

- Added `EquipmentNodeLifecycleServiceTest`
  - `getLifecycle_returnsNodeSummaryAndCounts`
  - `getLifecycle_includesDefectsWorkOrdersAndDocuments`
  - `getLifecycle_returnsTimelineSortedDescending`
  - `getLifecycle_nodeNotFound_returnsNotFound`
  - `getLifecycle_excludesDeletedRecords`
- Updated `EquipmentNodeControllerContractTest`
  - `getNodeLifecycle_returnsExpectedShape`

## Commands run

- `/tmp/apache-maven-3.9.11/bin/mvn -Dtest=EquipmentNodeLifecycleServiceTest,EquipmentNodeControllerContractTest test`
- `/tmp/apache-maven-3.9.11/bin/mvn clean -Dtest=EquipmentNodeLifecycleServiceTest,EquipmentNodeControllerContractTest test`
- `git diff --check`
- `/tmp/apache-maven-3.9.11/bin/mvn test`

## Targeted test result

- Command: `/tmp/apache-maven-3.9.11/bin/mvn clean -Dtest=EquipmentNodeLifecycleServiceTest,EquipmentNodeControllerContractTest test`
- Result: passed
- Tests run: 10
- Failures: 0
- Errors: 0
- Skipped: 0

## Full Maven test status

- Command: `/tmp/apache-maven-3.9.11/bin/mvn test`
- Result: failed because PostgreSQL test DB at `localhost:5433` was unavailable.
- Tests run: 1614
- Failures: 0
- Errors: 47
- Skipped: 0
- Root error observed: `Connection to localhost:5433 refused. Check that the hostname and port are correct and that the postmaster is accepting TCP/IP connections.`

## Diff check

- Command: `git diff --check`
- Result: passed

## Remaining backend risks

- No pagination was added; the endpoint uses a bounded `limit` per section and timeline.
- Repository queries return all active node-linked rows before service-level limiting, so very high-volume nodes may need paged repository methods later.
- Permission behavior uses existing authority checks, but no additional department/PBAC scoping was added inside the service.
- Timeline uses `updatedAt`/`createdAt`; it does not infer domain event dates that are not already exposed by the linked records.

## Frontend impact report

- `logs/equipment-node-lifecycle-phase-3f-frontend-impact.md`
