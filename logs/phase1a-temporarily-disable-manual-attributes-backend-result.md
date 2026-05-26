# Phase 1A Backend Result: Temporarily Disable Manual Attributes

Date: 2026-05-26
Repository: `/Users/tenzorsoft/Desktop/Work/toir/toir-backend`

## Files Changed

- `src/main/java/com/toir/service/equipment/EquipmentService.java`
- `src/main/java/com/toir/service/VehicleService.java`
- `src/main/java/com/toir/service/equipment/EquipmentManualAttributeService.java`
- `src/main/java/com/toir/controller/equipment/EquipmentManualAttributeController.java`
- `src/main/resources/application.yml`
- `src/test/java/com/toir/service/equipment/EquipmentServiceTest.java`
- `src/test/java/com/toir/service/VehicleServiceTest.java`
- `src/test/java/com/toir/service/equipment/EquipmentManualAttributeServiceTest.java`
- `src/test/java/com/toir/controller/equipment/EquipmentManualAttributeControllerContractTest.java`

## Exact Behavior After Change

- Official equipment attributes continue to use `EquipmentAttributeService.upsertValues(...)`.
- Official vehicle attributes continue to use `EquipmentAttributeService.upsertValues(...)`.
- `null` or empty `manualAttributes` in equipment create/update requests are accepted and do not mutate legacy manual rows.
- Non-empty `manualAttributes` in equipment create/update requests are rejected with:
  - `Manual attributes are temporarily disabled. Use official equipment attributes.`
- Non-empty `manualAttributes` in vehicle create/update requests are rejected with:
  - `Manual vehicle attributes are temporarily disabled. Use official equipment attributes.`
- Manual attribute read/list endpoints remain available for legacy visibility.
- Manual attribute mutating endpoints call the temporary write guard before mutation and return the controlled disabled error while the flag is false.

## Temporarily Disabled

- Equipment create/update persistence of request `manualAttributes`.
- Vehicle create/update persistence of request `manualAttributes`.
- Standalone manual attribute mutations:
  - `POST /api/v1/equipment/{equipmentId}/manual-attributes`
  - `PUT /api/v1/equipment/{equipmentId}/manual-attributes`
  - `PUT /api/v1/equipment/manual-attributes/{attributeId}`
  - `DELETE /api/v1/equipment/manual-attributes/{attributeId}`
  - `POST /api/v1/vehicles/{equipmentId}/manual-attributes`
  - `PUT /api/v1/vehicles/{equipmentId}/manual-attributes`
  - `PUT /api/v1/vehicles/manual-attributes/{attributeId}`
  - `DELETE /api/v1/vehicles/manual-attributes/{attributeId}`

## Preserved For Future Integration

- `EquipmentManualAttribute` entity.
- `equipment_manual_attributes` table and Flyway migration.
- Manual attribute repository, DTOs, service, and controller classes.
- Manual attribute read/list behavior.
- Existing manual rows in the database.
- A reversible feature flag:
  - `app.features.manual-attributes.write-enabled=false`
  - Environment override: `APP_FEATURES_MANUAL_ATTRIBUTES_WRITE_ENABLED`

## Manual Attribute Compatibility Policy

- Existing manual data is preserved.
- Existing manual data can still be read/listed for legacy compatibility.
- Empty manual attribute lists in equipment/vehicle create/update no longer clear or replace existing manual rows.
- Write paths are disabled by default and can only be re-enabled intentionally through the feature flag.
- No conversion to official attributes is attempted in Phase 1A.

## Tests Added/Updated

- Added service tests:
  - `equipmentCreateStillAcceptsOfficialAttributes`
  - `equipmentUpdateStillAcceptsOfficialAttributes`
  - `equipmentCreateRejectsTemporarilyDisabledManualAttributes`
  - `equipmentUpdateRejectsTemporarilyDisabledManualAttributes`
  - `vehicleCreateStillAcceptsOfficialAttributes`
  - `vehicleUpdateStillAcceptsOfficialAttributes`
  - `vehicleCreateRejectsTemporarilyDisabledManualAttributes`
  - `vehicleUpdateRejectsTemporarilyDisabledManualAttributes`
- Added controller tests:
  - `manualAttributeWriteEndpointIsTemporarilyDisabled`
  - `manualAttributeReadEndpointRemainsReadOnlyIfKept`
- Updated `EquipmentManualAttributeServiceTest` to explicitly enable the write flag so existing future-integration service behavior remains covered.

## Verification Commands Run

Requested Maven commands could not run because this checkout has `pom.xml` but no Maven wrapper, and `mvn` is not installed in the environment:

```bash
./mvnw -DskipTests compile
# zsh: no such file or directory: ./mvnw

mvn -Dtest=EquipmentServiceTest,VehicleServiceTest,EquipmentManualAttributeControllerContractTest test
# zsh: command not found: mvn
```

Static checks run:

```bash
git diff --check
rg -n "^(<<<<<<<|=======|>>>>>>>)" src
rg -n "manualAttributes|ManualAttribute|EquipmentManualAttribute" src/main/java src/test
```

Results:

- `git diff --check`: passed.
- Conflict marker search: no anchored conflict markers found.
- Manual attribute search: expected remaining references found in guarded code, DTOs, entity/repository, read compatibility, and tests.

## Known Remaining Issues For Next Phases

- Backend Java tests and compile were not executable in this environment due missing Maven tooling.
- Vehicle CRUD RBAC/PBAC issues from the audit are not part of Phase 1A.
- Vehicle detail still does not return official attributes as a first-class field.
- Equipment/vehicle type-change required-attribute validation remains a Phase 2 issue.
- Manual attribute data migration or read-only UX policy is not implemented in Phase 1A.
