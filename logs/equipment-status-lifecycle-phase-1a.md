# Equipment Status Lifecycle Phase 1A

## Branch
- `codex/equipment-lifecycle-main-sync-backend`

## Implementation status
- Implemented backend-only Phase 1A lifecycle hardening.
- No frontend files were modified.
- No dynamic passport, node, document category/versioning, spare-part catalog, or unrelated refactoring work was performed.

## Files changed

### Source
- `src/main/java/com/toir/controller/equipment/EquipmentController.java`
- `src/main/java/com/toir/dto/equipment/EquipmentStatusChangeRequest.java`
- `src/main/java/com/toir/dto/equipment/EquipmentStatusHistoryResponse.java`
- `src/main/java/com/toir/entity/equipment/EquipmentStatusHistory.java`
- `src/main/java/com/toir/enums/EquipmentStatusSource.java`
- `src/main/java/com/toir/repository/equipment/EquipmentStatusHistoryRepository.java`
- `src/main/java/com/toir/service/equipment/EquipmentStatusLifecycleService.java`
- `src/main/java/com/toir/service/equipment/EquipmentService.java`
- `src/main/java/com/toir/service/WorkOrderService.java`
- `src/main/java/com/toir/service/repair/RepairRequestService.java`
- `src/main/java/com/toir/service/defects/DefectService.java`
- `src/main/java/com/toir/service/MeterService.java`
- `src/main/java/com/toir/service/ConditionReadingService.java`
- `src/main/java/com/toir/service/TechnicalDocumentService.java`
- `src/main/java/com/toir/service/repair/RepairMaterialUsageService.java`

### Migration
- `src/main/resources/db/migration/V20260523_1__equipment_status_history.sql`

### Tests
- `src/test/java/com/toir/service/equipment/EquipmentStatusLifecycleServiceTest.java`
- `src/test/java/com/toir/service/equipment/EquipmentServiceTest.java`
- `src/test/java/com/toir/controller/EquipmentControllerContractTest.java`
- `src/test/java/com/toir/security/RbacEquipmentSecurityTest.java`
- Existing service/security tests updated only for new constructor dependencies.

## Migration added
- Added `equipment_status_history` table.
- Columns include: `id`, `equipment_id`, `from_status`, `to_status`, `reason`, `source`, `changed_by`, `changed_at`, `related_entity_type`, `related_entity_id`, `note`, `created_at`, `updated_at`, `is_deleted`.
- Added FK to `equipment(id)`.
- Added indexes on:
  - `equipment_id`
  - `changed_at`
  - `source`
  - `(related_entity_type, related_entity_id)`

## Endpoints added
- `PATCH /api/v1/equipment/{id}/status`
  - Requires existing equipment update permission.
  - Requires `status` and non-blank `reason`.
  - Writes status history and updates current equipment status.
- `GET /api/v1/equipment/{id}/status-history`
  - Requires existing equipment read permission.
  - Returns pageable status history ordered by `changedAt` descending.

## Status transition rules
- Manual transitions use `EquipmentStatusSource.MANUAL`, require a reason, store `changedBy` from the current user when available, and record `changedAt`.
- Work-order transitions use `EquipmentStatusSource.WORK_ORDER` with `relatedEntityType = WORK_ORDER` and `relatedEntityId = workOrderId`.
- Work-order start may set equipment to `IN_REPAIR` only from `ACTIVE` or `STANDBY`.
- Work-order start does not overwrite `OUT_OF_SERVICE`, `CONSERVATION`, or `DECOMMISSIONED`.
- Work-order completion/closure returns equipment only when current status is still `IN_REPAIR` and a related work-order transition exists. It returns to that transition's previous status.
- System transition helper was added for future internal lifecycle transitions.

## Decommission restrictions implemented
- New work orders are rejected for `DECOMMISSIONED` equipment.
- New repair requests are rejected for `DECOMMISSIONED` equipment.
- New defects are rejected for `DECOMMISSIONED` equipment.
- New meter readings are rejected for `DECOMMISSIONED` equipment.
- New condition readings are rejected for `DECOMMISSIONED` equipment.
- New technical document attachments are rejected for `DECOMMISSIONED` equipment.
- New repair material usage is rejected when the work order equipment is `DECOMMISSIONED`.
- Existing PPR behavior excluding `DECOMMISSIONED` equipment was preserved.

## Generic update behavior decision
- `EquipmentService.update(...)` now rejects direct status changes through generic equipment update with a controlled bad-request error:
  - `Equipment status changes must use the dedicated status endpoint`
- Create semantics were preserved; initial status is still allowed during equipment creation.
- This is safer than silently ignoring status because callers get a clear contract violation instead of believing a direct lifecycle change succeeded.

## Work-order auto-status behavior decision
- Work-order start writes a `WORK_ORDER` status transition to `IN_REPAIR` when allowed.
- Work-order complete/close writes a `WORK_ORDER` return transition only when the current equipment status is still work-order-managed (`IN_REPAIR`) and the original work-order transition can be found.
- Manual `OUT_OF_SERVICE`, `CONSERVATION`, and `DECOMMISSIONED` statuses are not overwritten by work-order completion/closure.
- Replacement work-order completion already has separate equipment replacement behavior and was not expanded in this Phase 1A change.

## Tests added/updated
- `EquipmentStatusLifecycleServiceTest`
  - `manualStatusChange_requiresReasonAndWritesHistory`
  - `manualStatusChange_requiresReason`
  - `manualStatusChange_updatesEquipmentStatus`
  - `genericEquipmentUpdate_doesNotSilentlyOverwriteStatus` is covered in `EquipmentServiceTest`.
  - `decommissionedEquipment_rejectsNewWorkOrder`
  - `decommissionedEquipment_rejectsNewDefect`
  - `decommissionedEquipment_rejectsNewRepairRequest`
  - `decommissionedEquipment_rejectsNewMeterReading`
  - `completeWorkOrder_doesNotOverwriteManualOutOfService`
  - `workOrderTransition_writesHistoryWithRelatedEntity`
- `EquipmentControllerContractTest`
  - `patchEquipmentStatus_returnsUpdatedStatus`
  - `getEquipmentStatusHistory_returnsTransitions`
  - `patchEquipmentStatus_withoutReason_returnsBadRequest`
- Existing tests were adjusted for new constructor dependencies without changing their business assertions.

## Commands run
- `git status --short --branch`
- `mvn -Dtest=EquipmentStatusLifecycleServiceTest,EquipmentServiceTest,EquipmentControllerContractTest test`
  - Result: failed because `mvn` is not on `PATH` in this shell.
- `/Users/tenzorsoft/.m2/wrapper/dists/apache-maven-3.9.12/6068d197/bin/mvn -Dtest=EquipmentStatusLifecycleServiceTest,EquipmentServiceTest,EquipmentControllerContractTest test`
  - Result: passed, 107 tests, 0 failures, 0 errors.
- `/Users/tenzorsoft/.m2/wrapper/dists/apache-maven-3.9.12/6068d197/bin/mvn test`
  - Result before fixing security test mock: failed with 59 errors, including one new missing mock in `RbacEquipmentSecurityTest` plus existing repository DB connection errors.
- `/Users/tenzorsoft/.m2/wrapper/dists/apache-maven-3.9.12/6068d197/bin/mvn -Dtest=EquipmentStatusLifecycleServiceTest,EquipmentServiceTest,EquipmentControllerContractTest,RbacEquipmentSecurityTest test`
  - Result: passed, 119 tests, 0 failures, 0 errors.
- `/Users/tenzorsoft/.m2/wrapper/dists/apache-maven-3.9.12/6068d197/bin/mvn test`
  - Result after the fix: failed with 47 errors, all observed failures are repository/Data JPA tests requiring PostgreSQL at `localhost:5433`.
- `git diff --name-status`
- `git status --short --branch`

## Test result
- Targeted lifecycle, equipment service, controller contract, and equipment RBAC verification passed.
- Full `mvn test` did not pass in this environment because the configured test PostgreSQL instance at `localhost:5433` refused connections.
- The full-suite failure is not a Java compile failure and not a remaining lifecycle unit/controller failure.

## Remaining risks
- Full repository/Data JPA verification still needs rerun with the expected PostgreSQL test database available.
- Replacement work-order completion has special existing behavior and was not broadened beyond avoiding blind status overwrite in this phase.
- Decommissioned equipment currently blocks new technical document attachments. If archival documents should be allowed after decommission, that needs a narrower business rule.
- Status ownership is derived from transition history and current status; there is no separate current-status-source field on `equipment`.
- Decommission restrictions were added to the inspected operational services, but other future operational entry points should call `assertOperationallyAllowed(...)` as they are added.

## Audit readiness
- Phase 1A backend contract and service behavior are ready for frontend Phase 1B integration work, subject to rerunning full `mvn test` when the PostgreSQL test database is available.
