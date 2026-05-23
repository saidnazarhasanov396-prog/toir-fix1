# Equipment Dynamic Passport Contract Phase 1C - Backend

## Branch
- `codex/equipment-lifecycle-main-sync-backend`

## Canonical contract chosen
- Backend dynamic passport DTOs remain the canonical source of truth.
- Attribute definition response shape:
  - `id`
  - `equipmentTypeId`
  - `key`
  - `label`
  - `labelRu`
  - `labelUz`
  - `dataType`
  - `unit`
  - `required`
  - `minValue`
  - `maxValue`
  - `optionSourceId`
  - `options`
  - `groupName`
  - `sortOrder`
- Attribute value write payload is a direct JSON list of `EquipmentAttributeValueRequest` objects.
- Stale wrapper payloads such as `{ "values": [...] }` are not part of the canonical contract.

## Backend endpoints confirmed
- `GET /api/v1/equipment-attribute-option-sources`
- `POST /api/v1/equipment-attribute-option-sources`
- `GET /api/v1/equipment-attribute-option-sources/{sourceId}/options`
- `PUT /api/v1/equipment-attribute-option-sources/{sourceId}/options`
- `GET /api/v1/equipment-types/{equipmentTypeId}/attributes`
- `POST /api/v1/equipment-types/{equipmentTypeId}/attributes`
- `PUT /api/v1/equipment-types/{equipmentTypeId}/attributes/{attributeId}`
- `DELETE /api/v1/equipment-types/{equipmentTypeId}/attributes/{attributeId}`
- `GET /api/v1/equipment/{equipmentId}/attributes`
- `PUT /api/v1/equipment/{equipmentId}/attributes`
- `POST /api/v1/equipment/{equipmentId}/attributes`

## Files changed
- `src/test/java/com/toir/controller/equipment/EquipmentAttributeControllerContractTest.java`

## Backend behavior changed
- No backend production behavior was changed for Phase 1C.
- Existing DTO/service/controller behavior was preserved.
- Contract coverage was added around the existing backend contract.

## Tests added/updated
- Added `EquipmentAttributeControllerContractTest` covering:
  - canonical definition response fields
  - create definition payload with `optionSourceId` and inline `options`
  - direct list payload for equipment attribute values
  - rejection of stale wrapped value payload shape

## Commands run
- `git status --short --branch`
- `/Users/tenzorsoft/.m2/wrapper/dists/apache-maven-3.9.12/6068d197/bin/mvn -Dtest=EquipmentAttributeControllerContractTest,EquipmentAttributeServiceTest test`
- `nc -z localhost 5433`
- `git diff --name-status`
- `git diff --check`

## Test result
- Targeted backend tests passed.
- Maven result:
  - `Tests run: 47, Failures: 0, Errors: 0, Skipped: 0`
  - `BUILD SUCCESS`

## Full Maven test status
- Full `mvn test` was not run in Phase 1C because the configured PostgreSQL test port `localhost:5433` is not available in this workspace.
- `nc -z localhost 5433` exited non-zero.

## Remaining risks
- Phase 1C did not change backend production code because the backend contract was already clear and compatible with the target canonical shape.
- Full backend integration verification still requires an available PostgreSQL test database.
- Existing Phase 1A status lifecycle files remain in the working tree and are outside this Phase 1C scope.

## Audit readiness
- Backend dynamic passport contract is ready for frontend alignment and Phase 2 planning, subject to full integration tests when PostgreSQL is available.
