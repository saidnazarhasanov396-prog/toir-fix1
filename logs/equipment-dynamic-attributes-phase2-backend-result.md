# Equipment Dynamic Attributes Phase 2 Backend Result

Date: 2026-05-25

Branch: `codex/equipment-lifecycle-main-sync-backend`

## Scope

Implemented Phase 2 backend changes for dynamic equipment attributes only:

- Reserved dynamic attribute key validation for equipment type attribute definitions.
- Required dynamic attribute enforcement on equipment create when `attributes` is omitted or empty.
- Optional dynamic attribute value support on vehicle create/update.

No Phase 1 `averageOperatingLifeHours` changes were reverted. No vehicle database columns were removed. No cycles/revolutions/unit enum was added.

## Files Changed

- `src/main/java/com/toir/service/equipment/EquipmentAttributeService.java`
- `src/main/java/com/toir/service/equipment/EquipmentService.java`
- `src/main/java/com/toir/dto/vehicle/VehicleRequest.java`
- `src/main/java/com/toir/service/VehicleService.java`
- `src/test/java/com/toir/service/equipment/EquipmentAttributeServiceTest.java`
- `src/test/java/com/toir/service/equipment/EquipmentServiceTest.java`
- `src/test/java/com/toir/service/VehicleServiceTest.java`
- `logs/equipment-dynamic-attributes-phase2-backend-result.md`

## Reserved-Key Validation Behavior

`EquipmentAttributeService` now rejects equipment attribute definition keys that collide with core system fields. Validation runs for:

- single attribute definition create
- batch attribute definition create
- attribute definition update

Keys are normalized before comparison by trimming, lowercasing, and removing `_`, `-`, and whitespace separators. For example, `averageOperatingLifeHours`, `average_operating_life_hours`, and `average-operating-life-hours` all collide.

The reserved set includes core identity, type/location/ownership, status/category, warranty/commissioning, and `averageOperatingLifeHours` keys. Rejections use the existing bad-request exception style and include `reserved key` in the message.

## Required Dynamic Create Enforcement

`EquipmentService.create(...)` now treats omitted create attributes as an empty list before calling `EquipmentAttributeService.upsertValues(...)`. This allows existing required-attribute validation to run even when the client omits the `attributes` field.

Update semantics remain partial: `EquipmentService.update(...)` still only updates dynamic attributes when `request.attributes()` is not null.

## Vehicle Attributes Request Support

`VehicleRequest` now accepts optional `List<EquipmentAttributeValueRequest> attributes` while keeping a compatibility constructor for existing request construction.

`VehicleService.createVehicle(...)` persists dynamic attribute values for the underlying `Equipment` and passes an empty list when the request omits `attributes`, so required dynamic attributes are enforced for vehicle create.

`VehicleService.updateVehicle(...)` updates dynamic attribute values only when `request.attributes()` is non-null, preserving existing values when omitted.

Vehicle response shape was not expanded in this phase. Frontend should continue reading dynamic values through the existing equipment attribute values endpoint.

## Tests Added / Updated

- Reserved-key validation:
  - creating definition with `model` fails
  - creating definition with `averageOperatingLifeHours` fails
  - creating definition with `average_operating_life_hours` fails
  - batch creation rejects a reserved key
  - update rejects changing a key to a reserved key
  - normal key creation remains covered by existing tests
- Equipment create:
  - create with required dynamic attribute and omitted `attributes` fails
  - create with required dynamic attribute and empty `attributes` fails
- Vehicle create/update:
  - vehicle create accepts and persists a dynamic metric attribute
  - vehicle create with missing required dynamic metric fails
  - vehicle update changes a dynamic metric attribute

## Verification

- `git diff --check`: passed
- Maven command attempted:
  - `./mvnw -q -DskipTests compile && ./mvnw test`, or `mvn`, or `/tmp/apache-maven-3.9.11/bin/mvn`
- Result: Maven unavailable in the local environment, so backend compile/tests were not run locally.

## Remaining Risks

- Backend Maven compile/test verification must be run on a machine with Maven available.
- Vehicle dynamic attribute values are not embedded in vehicle detail responses; clients should use the existing equipment attributes read endpoint.
- Reserved-key validation prevents future duplicates, but existing database rows with reserved dynamic keys would need cleanup if already present.
