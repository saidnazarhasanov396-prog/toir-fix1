# Phase 2 Backend Result: Equipment/Vehicle Type-Change Required Attributes

## Summary

Implemented a backend guard for Equipment and Vehicle update flows so an equipment type change cannot omit the official `attributes` payload.

Overall status: PARTIAL verification. Code changes are in place and static git checks passed, but Maven verification could not run because this workspace has neither `./mvnw` nor `mvn`.

## Files Changed For Phase 2

- `src/main/java/com/toir/service/equipment/EquipmentService.java`
- `src/main/java/com/toir/service/VehicleService.java`
- `src/test/java/com/toir/service/equipment/EquipmentServiceTest.java`
- `src/test/java/com/toir/service/VehicleServiceTest.java`
- `src/test/java/com/toir/service/equipment/EquipmentAttributeServiceTest.java`

Note: the backend worktree also contains earlier Phase 1C security changes that were already present before this phase.

## Type-Change Policy Implemented

- If `equipmentTypeId` is unchanged and `attributes` is omitted, the existing update behavior is preserved.
- If `equipmentTypeId` changes and `attributes` is `null`, update is rejected with:
  - `Attributes are required when equipment type changes.`
- If `equipmentTypeId` changes and `attributes` is an empty list, the update proceeds to the existing official attribute validation path.
  - Required attributes for the new type are enforced by `EquipmentAttributeService.upsertValues(...)`.
  - If the new type has no required attributes, an explicit empty official payload is allowed.
- If `equipmentTypeId` changes and `attributes` is provided, values are validated against the new equipment type through the existing definition resolution rules.
  - Old-type keys are rejected as unknown keys.
  - Old-type definition IDs are rejected as not allowed for the equipment type.

## Old Attribute Value Handling Policy

No old official attribute rows are physically deleted in Phase 2.

The existing `EquipmentAttributeService.findValues(...)` behavior is used as the active-value policy: it returns DTOs from definitions belonging to the current `equipmentTypeId`, so values from the previous equipment type are preserved in storage but are not shown as active current attributes after a type change.

## Backend Validation Behavior

- `EquipmentService.update(...)` detects `oldEquipmentTypeId` versus requested `equipmentTypeId` before applying the update.
- `VehicleService.update(...)` applies the same check for vehicle equipment.
- Required/new-type validation stays centralized in `EquipmentAttributeService.upsertValues(...)`.
- Manual key/value writes remain disabled by the Phase 1A guard and feature flag default:
  - `app.features.manual-attributes.write-enabled=false`

## Tests Added/Updated

- `equipmentUpdateSameTypeWithoutAttributesKeepsExistingBehavior()`
- `equipmentTypeChangeWithoutAttributesIsRejected()`
- `equipmentTypeChangeWithEmptyAttributesAndRequiredNewTypeIsRejected()`
- `equipmentTypeChangeWithRequiredNewTypeAttributesSucceeds()`
- `equipmentTypeChangeRejectsOldTypeAttributeKey()`
- `equipmentTypeChangeRejectsOldTypeAttributeDefinitionId()`
- `vehicleUpdateSameTypeWithoutAttributesKeepsExistingBehavior()`
- `vehicleTypeChangeWithoutAttributesIsRejected()`
- `vehicleTypeChangeWithEmptyAttributesAndRequiredNewTypeIsRejected()`
- `vehicleTypeChangeWithRequiredNewTypeAttributesSucceeds()`
- `vehicleTypeChangeRejectsOldTypeAttributeKey()`
- `vehicleTypeChangeRejectsOldTypeAttributeDefinitionId()`
- `equipmentDetailAfterTypeChangeShowsOnlyNewTypeAttributes()`
- `equipmentAttributeEndpointAfterTypeChangeShowsOnlyCurrentTypeAttributes()`

## Verification Commands Run

- `./mvnw -DskipTests compile || mvn -DskipTests compile`
  - Failed: `./mvnw` not found, `mvn` not found.
- `./mvnw test || mvn test`
  - Failed: `./mvnw` not found, `mvn` not found.
- `./mvnw -Dtest=EquipmentServiceTest,VehicleServiceTest test || mvn -Dtest=EquipmentServiceTest,VehicleServiceTest test`
  - Failed: `./mvnw` not found, `mvn` not found.
- `./mvnw -Dtest=EquipmentAttributeServiceTest test || mvn -Dtest=EquipmentAttributeServiceTest test`
  - Failed: `./mvnw` not found, `mvn` not found.
- `./mvnw -Dtest=EquipmentControllerContractTest,VehicleControllerContractTest test || mvn -Dtest=EquipmentControllerContractTest,VehicleControllerContractTest test`
  - Failed: `./mvnw` not found, `mvn` not found.
- `git diff --check`
  - Passed.
- `rg -n "^(<<<<<<<|=======|>>>>>>>)" src`
  - No unresolved conflict markers found.

## Known Remaining Issues

- Backend compile/test verification is blocked until Maven or a Maven wrapper is available.
- No local commit was created because the requested backend verification could not pass in this environment.
- Existing Phase 1C security changes remain uncommitted in the same worktree and should be kept together or reviewed before final commit.
