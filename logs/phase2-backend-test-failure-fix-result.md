# Phase 2 Backend Test Failure Fix Result

## Summary

Fixed backend test failures reported after Phase 1C/2/2B by correcting test fixtures and unused stubs. No Maven or test command was run.

## Files Changed

- `src/test/java/com/toir/service/VehicleServiceTest.java`
- `src/test/java/com/toir/controller/VehicleControllerContractTest.java`
- `src/test/java/com/toir/security/EquipmentAttributePbacScopeTest.java`
- `src/test/java/com/toir/security/EquipmentAttributeControllerSecurityTest.java`
- `src/test/java/com/toir/security/VehiclePbacScopeTest.java`

No backend production code was changed.

## Root Cause And Fixes

### VehicleServiceTest false type-change failures

Affected tests:

- `updateVehicleNormalizesBlankVinToNull`
- `updateVehicleAllowsSoftDeletedUniqueValuesWhenActiveLookupsDoNotFindThem`
- `updateVehicleDoesNotSelfConflictOnUnchangedUniqueFields`

Root cause:

- `VehicleService.isEquipmentTypeChanged(...)` already correctly treats `request.equipmentTypeId == null` as no type change.
- The failing tests used `fullRequest(...)`, which generates a random `equipmentTypeId`.
- The existing vehicle fixture also generates a different random `equipmentTypeId`, so the tests accidentally became type-change updates with omitted `attributes`.

Fix:

- Updated those three test requests to use the existing equipment's `equipmentTypeId` through `withEquipmentTypeAndAttributes(..., equipment.getEquipmentTypeId(), null)`.
- This keeps the tests focused on VIN/unique-field behavior and preserves Phase 2 type-change validation.

### VehicleControllerContractTest unnecessary stubbing

Affected test:

- `statsWithFiltersPassesScopedDepartmentAndSearchToService`

Root cause:

- The test stubbed `scopeAccessService.currentDepartmentIdOrNull()`, but the controller does not call it when `isScopeAdmin()` resolves true from the shared setup.

Fix:

- Removed the unused `currentDepartmentIdOrNull()` stub.

### EquipmentAttributePbacScopeTest history endpoint 500

Affected test:

- `equipmentAttributeHistoryRequiresEquipmentScope`

Root cause:

- The test returned `Page.empty()` for the history endpoint. In standalone MockMvc serialization this can expose unpaged `Pageable` behavior and produce a server error during response rendering.

Fix:

- Changed the stub to return `Page.empty(PageRequest.of(0, 20))`, matching the controller's paged history contract.
- Applied the same safe fixture pattern to `EquipmentAttributeControllerSecurityTest.equipmentReadCanReadAttributeValuesAndHistory`.

### EquipmentAttributePbacScopeTest admin unnecessary stubbing

Affected test:

- `equipmentAttributeAdminCanAccessOtherDepartment`

Root cause:

- The controller path for equipment with a department delegates to `scopeAccessService.assertCanAccessDepartment(...)`.
- The direct `isScopeAdmin()` stub was not used in this mocked PBAC test.

Fix:

- Removed the unused `isScopeAdmin()` stub.

### VehiclePbacScopeTest admin unnecessary stubbing

Affected test:

- `vehicleAdminCanAccessAllDepartments`

Root cause:

- The controller path for vehicle equipment with a department delegates to `scopeAccessService.assertCanAccessDepartment(...)`.
- The direct `isScopeAdmin()` stub was not used in this mocked PBAC test.

Fix:

- Removed the unused `isScopeAdmin()` stub.

## Production Behavior Safety

- Vehicle type change with omitted `attributes` remains rejected.
- Vehicle update with same `equipmentTypeId` and omitted `attributes` remains valid.
- `request.equipmentTypeId == null` still means no type change in production logic.
- Official `attributes[]` flow remains unchanged.
- Manual key/value writes remain disabled by `app.features.manual-attributes.write-enabled=false`.
- Vehicle RBAC/PBAC and equipment attribute endpoint scope checks were not weakened.

## Static Checks Run

- `git diff --check`
  - Passed.
- `rg -n "^(<<<<<<<|=======|>>>>>>>)" src`
  - No unresolved conflict markers found.

## Tests

No test commands were run, per instruction.

Suggested manual verification commands:

```bash
mvn -Dtest=VehicleControllerContractTest,EquipmentAttributePbacScopeTest,VehiclePbacScopeTest,VehicleServiceTest test
mvn test
git diff --check
rg -n "^(<<<<<<<|=======|>>>>>>>)" src
```
