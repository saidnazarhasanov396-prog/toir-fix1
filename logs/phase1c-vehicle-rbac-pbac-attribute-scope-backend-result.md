# Phase 1C Backend Result: Vehicle RBAC/PBAC and Attribute Scope

## Files changed

- `src/main/java/com/toir/controller/VehicleController.java`
- `src/main/java/com/toir/controller/equipment/EquipmentAttributeController.java`
- `src/test/java/com/toir/controller/VehicleControllerContractTest.java`
- `src/test/java/com/toir/controller/EquipmentAttributeControllerContractTest.java`
- `src/test/java/com/toir/controller/equipment/EquipmentAttributeControllerContractTest.java`
- `src/test/java/com/toir/security/VehicleControllerSecurityTest.java`
- `src/test/java/com/toir/security/VehiclePbacScopeTest.java`
- `src/test/java/com/toir/security/EquipmentAttributeControllerSecurityTest.java`
- `src/test/java/com/toir/security/EquipmentAttributePbacScopeTest.java`

## Security rules added

- Vehicle endpoints now use explicit RBAC annotations with the existing equipment permission model:
  - Vehicle read/list/detail/stats/document reads: `EQUIPMENT_READ`
  - Vehicle create: `EQUIPMENT_CREATE`
  - Vehicle update/document mutations: `EQUIPMENT_UPDATE`
  - Vehicle delete: `EQUIPMENT_DELETE`
- No vehicle-specific permission constants exist in the project, so the closest established convention is the equipment permission set because vehicles are modeled as equipment with category `VEHICLE`.

## PBAC/scope behavior

- Vehicle list and stats now clamp `departmentId` through `ScopeAccessService.enforceDepartmentScope`.
- Non-admin users without a current department are denied vehicle list/stats access.
- Vehicle detail/update/delete and document endpoints now load the canonical equipment row, verify category `VEHICLE`, and enforce department access before delegating to service methods.
- Vehicle create now validates `request.departmentId()` through `ScopeAccessService.assertCanAccessDepartment`.
- Admin/wildcard scope behavior remains aligned with existing equipment controller behavior.

## Attribute endpoint scope behavior

- Official equipment attribute value endpoints now enforce equipment department scope:
  - `GET /api/v1/equipment/{equipmentId}/attributes`
  - `GET /api/v1/equipment/{equipmentId}/attributes/history`
  - `PUT /api/v1/equipment/{equipmentId}/attributes`
  - `POST /api/v1/equipment/{equipmentId}/attributes`
- Read/history still require `EQUIPMENT_READ`.
- Write endpoints still require `EQUIPMENT_UPDATE`.
- DTO shapes and official attribute value contracts were not changed.

## Preserved behavior

- Phase 1A manual attribute write disable remains unchanged.
- Phase 1B vehicle detail official `attributes` response remains unchanged.
- Manual attribute entity/table/repository/controller/service code was not deleted.
- No Flyway migration was changed.
- No arbitrary key/value or loose JSON flow was introduced.

## Tests added/updated

- Added `VehicleControllerSecurityTest`.
- Added `VehiclePbacScopeTest`.
- Added `EquipmentAttributeControllerSecurityTest`.
- Added `EquipmentAttributePbacScopeTest`.
- Updated existing controller contract tests to construct controllers with the new scope dependencies.

## Verification commands run

- `./mvnw -DskipTests compile || mvn -DskipTests compile` failed: no `./mvnw`; `mvn` command not found.
- `./mvnw test || mvn test` failed: no `./mvnw`; `mvn` command not found.
- `./mvnw -Dtest=VehicleControllerSecurityTest,VehiclePbacScopeTest test || mvn -Dtest=VehicleControllerSecurityTest,VehiclePbacScopeTest test` failed: no Maven runner available.
- `./mvnw -Dtest=EquipmentAttributeControllerSecurityTest,EquipmentAttributePbacScopeTest test || mvn -Dtest=EquipmentAttributeControllerSecurityTest,EquipmentAttributePbacScopeTest test` failed: no Maven runner available.
- `./mvnw -Dtest=VehicleServiceTest,VehicleControllerContractTest test || mvn -Dtest=VehicleServiceTest,VehicleControllerContractTest test` failed: no Maven runner available.
- `git diff --check` passed.
- `rg -n "^(<<<<<<<|=======|>>>>>>>)" src` returned clean.
- `rg -n "manualAttributes|ManualAttribute|EquipmentManualAttribute" src/main/java src/test/java` confirmed manual attribute code remains present and disabled by Phase 1A guards.

## Known remaining issues

- Backend Java tests and compile could not be executed in this local environment because neither Maven wrapper nor system Maven is available.
- Vehicle-specific authorities may be introduced later, but this phase intentionally uses existing `EQUIPMENT_*` permissions to match current architecture.
