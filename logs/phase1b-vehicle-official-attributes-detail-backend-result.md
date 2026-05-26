# Phase 1B Backend Result: Vehicle Official Attributes In Detail

Date: 2026-05-26
Repository: `/Users/tenzorsoft/Desktop/Work/toir/toir-backend`

## Files Changed

Phase 1B backend files:

- `src/main/java/com/toir/dto/vehicle/VehicleDetailDto.java`
- `src/main/java/com/toir/service/VehicleService.java`
- `src/test/java/com/toir/service/VehicleServiceTest.java`
- `src/test/java/com/toir/controller/VehicleControllerContractTest.java`

Phase 1A files remain modified in the same uncommitted worktree:

- `src/main/java/com/toir/controller/equipment/EquipmentManualAttributeController.java`
- `src/main/java/com/toir/service/equipment/EquipmentManualAttributeService.java`
- `src/main/java/com/toir/service/equipment/EquipmentService.java`
- `src/main/resources/application.yml`
- `src/test/java/com/toir/service/equipment/EquipmentManualAttributeServiceTest.java`
- `src/test/java/com/toir/service/equipment/EquipmentServiceTest.java`
- `src/test/java/com/toir/controller/equipment/EquipmentManualAttributeControllerContractTest.java`

## API Response Contract After Change

`GET /api/v1/vehicles/{equipmentId}` now returns root-level official `attributes` alongside existing vehicle detail fields.

Contract shape:

```json
{
  "equipment": { "...": "Vehicle equipment summary" },
  "vehicleDetails": { "...": "Vehicle-specific common fields" },
  "attributes": [
    {
      "id": "value-id",
      "equipmentId": "vehicle-equipment-id",
      "attributeDefinitionId": "definition-id",
      "key": "payload_capacity",
      "label": "Payload capacity",
      "labelRu": "Грузоподъемность",
      "labelUz": "Yuk ko'tarish",
      "dataType": "NUMBER",
      "unit": "kg",
      "required": false,
      "optionSourceId": null,
      "options": [],
      "groupName": "vehicle_metrics",
      "sortOrder": 10,
      "valueText": null,
      "valueNumber": 12000,
      "valueDate": null,
      "valueBoolean": null,
      "valueOption": null,
      "valueJson": null
    }
  ],
  "manualAttributes": []
}
```

`attributes` is always non-null in DTO construction and defaults to an empty list.

## Official Attribute Behavior

- `VehicleService.findByEquipmentId` loads official values through `EquipmentAttributeService.findValues(equipmentId)`.
- `VehicleService.create` still calls `EquipmentAttributeService.upsertValues(...)` for `request.attributes()`.
- `VehicleService.update` still calls `EquipmentAttributeService.upsertValues(...)` when `request.attributes()` is provided.
- Create/update return DTOs now include official attributes loaded after upsert.
- Vehicle list endpoints remain light; no official attributes were added to list/search.

## Manual Attribute Compatibility

- Phase 1A manual write disable remains intact.
- Manual key/value entity/table/repository/service/controller are still preserved.
- Existing `manualAttributes` remains as a legacy/read-only response field.
- Manual attributes are not the primary dynamic attribute contract.
- No arbitrary `extraData`, JSONB replacement, or loose key/value flow was introduced.

## Tests Added/Updated

- `vehicleDetailReturnsOfficialAttributes`
- `vehicleDetailReturnsEmptyAttributesWhenNoOfficialValues`
- `vehicleCreateThenDetailContainsOfficialAttributes`
- `vehicleUpdateThenDetailReflectsOfficialAttributes`
- `vehicleDetailDoesNotRequireManualAttributes`
- `phase1aVehicleManualAttributesStillRejected`
- `VehicleControllerContractTest.getVehicleDetailReturnsOfficialAttributes`

Existing Phase 1A tests remain in place for disabled manual writes.

## Verification Commands Run

Requested backend commands could not execute because this checkout has no Maven wrapper and the environment has no `mvn` binary:

```bash
./mvnw -DskipTests compile || mvn -DskipTests compile
# ./mvnw: no such file or directory
# mvn: command not found

./mvnw test || mvn test
# ./mvnw: no such file or directory
# mvn: command not found

./mvnw -Dtest=VehicleServiceTest test || mvn -Dtest=VehicleServiceTest test
# ./mvnw: no such file or directory
# mvn: command not found

./mvnw -Dtest=VehicleControllerContractTest test || mvn -Dtest=VehicleControllerContractTest test
# ./mvnw: no such file or directory
# mvn: command not found
```

Static verification run:

```bash
git diff --check
rg -n "^(<<<<<<<|=======|>>>>>>>)" src
rg -n "manualAttributes|ManualAttribute|EquipmentManualAttribute" src/main/java src/test
```

Results:

- `git diff --check`: passed.
- Conflict marker search: no anchored markers found.
- Manual attribute search: expected guarded/legacy references remain.

## Known Remaining Issues

- Backend compile/tests were not runnable in this environment due missing Maven tooling, so no local backend commit was created.
- Vehicle CRUD RBAC/PBAC remains a later phase.
- Equipment/vehicle type-change required-attribute validation remains a later phase.
- Manual attribute legacy display remains present and is not redesigned in Phase 1B.
