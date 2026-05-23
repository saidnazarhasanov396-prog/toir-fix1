# Equipment Dynamic Passport Required Policy Phase 2A - Backend

## Branch
- `codex/equipment-lifecycle-main-sync-backend`

## Scope
- Backend only.
- Dynamic passport required policy only.
- No frontend, status lifecycle, node/document/spare-part, or unrelated refactoring work was performed.

## Canonical policy model chosen
- Option A: relational policy table.
- New table: `equipment_attribute_required_criticality`.
- Meaning: an attribute definition is required when the equipment has a matching `Equipment.criticalityClassId`.
- Existing `EquipmentAttributeDefinition.required = true` behavior is preserved and still means always required for that equipment type.
- Equipment with no `criticalityClassId` ignores criticality-required policy and only enforces always-required attributes.

## Files changed
- `src/main/java/com/toir/dto/equipmentattribute/EquipmentAttributeDefinitionDto.java`
- `src/main/java/com/toir/dto/equipmentattribute/EquipmentAttributeDefinitionRequest.java`
- `src/main/java/com/toir/entity/equipment/EquipmentAttributeRequiredCriticality.java`
- `src/main/java/com/toir/repository/equipment/EquipmentAttributeRequiredCriticalityRepository.java`
- `src/main/java/com/toir/service/equipment/EquipmentAttributeService.java`
- `src/main/resources/db/migration/V20260523_2__equipment_attribute_required_criticality.sql`
- `src/test/java/com/toir/controller/equipment/EquipmentAttributeControllerContractTest.java`
- `src/test/java/com/toir/service/equipment/EquipmentAttributeServiceTest.java`
- `logs/equipment-dynamic-passport-required-policy-phase-2a-backend.md`

## Migration added
- `V20260523_2__equipment_attribute_required_criticality.sql`
- Adds:
  - FK to `equipment_attribute_definitions(id)`
  - FK to `criticality_classes(id)`
  - active uniqueness index on `(attribute_definition_id, criticality_class_id)`
  - active index on `attribute_definition_id`
  - active index on `criticality_class_id`
  - BaseEntity-compatible fields: `id`, `created_at`, `updated_at`, `is_deleted`

## DTO fields added
- Added `requiredForCriticalityClassIds: List<UUID>` to:
  - `EquipmentAttributeDefinitionRequest`
  - `EquipmentAttributeDefinitionDto`
- Request compatibility:
  - Existing clients that omit the field are supported.
  - Missing/null field normalizes to an empty list.
- Response behavior:
  - Service responses provide an array, never `null`.

## Validation behavior
- On equipment attribute value upsert:
  - Always-required attributes are still enforced.
  - Criticality-required attributes are enforced when `equipment.criticalityClassId` matches a configured policy.
  - Missing values return controlled `BAD_REQUEST` via `RestException`.
  - Missing attribute messages identify the key and reason, for example:
    - `motor_power (required by equipment type)`
    - `vibration_limit (required by criticality)`

## Create/update behavior
- Create/update definition validates all `requiredForCriticalityClassIds` exist in active `criticality_classes`.
- Unknown criticality class IDs return `NOT_FOUND`.
- Duplicate IDs in the request are normalized away before persistence to prevent duplicate active links.
- Update replaces existing active criticality-required links by soft-deleting old rows and inserting the requested policy set.
- Existing option/range/data-type validations are preserved.

## Delete/soft-delete behavior
- Soft-deleting an attribute definition now also soft-deletes its active `equipment_attribute_required_criticality` policy rows.
- FK constraints prevent dangling rows at the database level.

## Tests added/updated
- `EquipmentAttributeServiceTest`
  - `createDefinition_withRequiredCriticalityClasses_persistsPolicy`
  - `updateDefinition_replacesRequiredCriticalityClasses`
  - `upsertValues_missingAlwaysRequiredAttribute_returnsBadRequest`
  - `upsertValues_missingCriticalityRequiredAttribute_returnsBadRequest`
  - `upsertValues_criticalityRequiredAttributePresent_passes`
  - `upsertValues_noEquipmentCriticality_ignoresCriticalityRequiredPolicy`
  - `createDefinition_unknownCriticalityClass_returnsNotFoundOrBadRequest`
- `EquipmentAttributeControllerContractTest`
  - `createDefinition_acceptsRequiredForCriticalityClassIds`
  - `getDefinitions_returnsRequiredForCriticalityClassIds`
  - `updateDefinition_replacesRequiredForCriticalityClassIds`
  - `upsertValues_missingCriticalityRequiredAttribute_returnsBadRequest`

## Commands run
- `git status --short --branch`
- `/Users/tenzorsoft/.m2/wrapper/dists/apache-maven-3.9.12/6068d197/bin/mvn -Dtest=EquipmentAttributeControllerContractTest,EquipmentAttributeServiceTest test`
- `nc -z localhost 5433`
- `git diff --check`
- `git diff --name-status`

## Test result
- Targeted backend tests passed.
- Maven result:
  - `Tests run: 58, Failures: 0, Errors: 0, Skipped: 0`
  - `BUILD SUCCESS`

## Full Maven test status
- Full `mvn test` was not run because the configured PostgreSQL test database port is unavailable.
- `nc -z localhost 5433` exited non-zero.

## Remaining risks
- Full repository integration verification still requires PostgreSQL on the configured test port.
- Frontend does not yet expose `requiredForCriticalityClassIds`; Phase 2B should add UI/API type support.
- This phase is the backend policy foundation only; it does not add passport history/versioning.
- The working tree still contains pre-existing Phase 1A status lifecycle changes outside this Phase 2A scope.

## Frontend Phase 2B readiness
- Frontend Phase 2B can start.
- It should consume `requiredForCriticalityClassIds` in attribute definition create/update/list flows and surface criticality-based required validation messages.
