# Equipment Dynamic Passport Value History Phase 2C Backend

## Branch

`codex/equipment-lifecycle-main-sync-backend`

## Scope

Backend-only implementation for dynamic equipment passport value history/audit. No frontend, status lifecycle, node/document, spare-part, or unrelated behavior changes were made intentionally.

## Files changed

- `src/main/java/com/toir/controller/equipment/EquipmentAttributeController.java`
- `src/main/java/com/toir/dto/equipmentattribute/EquipmentAttributeValueHistoryDto.java`
- `src/main/java/com/toir/entity/equipment/EquipmentAttributeValueHistory.java`
- `src/main/java/com/toir/enums/EquipmentAttributeValueHistorySource.java`
- `src/main/java/com/toir/repository/equipment/EquipmentAttributeValueHistoryRepository.java`
- `src/main/java/com/toir/service/equipment/EquipmentAttributeService.java`
- `src/main/resources/db/migration/V20260523_3__equipment_attribute_value_history.sql`
- `src/test/java/com/toir/controller/equipment/EquipmentAttributeControllerContractTest.java`
- `src/test/java/com/toir/service/equipment/EquipmentAttributeServiceTest.java`

## Migration added

`V20260523_3__equipment_attribute_value_history.sql`

Adds `equipment_attribute_value_history` with:

- Base columns: `id`, `created_at`, `updated_at`, `is_deleted`
- Audit target: `equipment_id`, `attribute_definition_id`
- Snapshot metadata: `attribute_key`, `attribute_label`
- Value delta: `old_value`, `new_value`
- Audit metadata: `changed_by`, `changed_at`, `source`, `reason`
- FKs to `equipment(id)` and `equipment_attribute_definitions(id)`
- Indexes for `equipment_id`, `attribute_definition_id`, `changed_at`, `changed_by`, and `(equipment_id, changed_at DESC)`

## History model chosen

`EquipmentAttributeValueHistory` stores one row per changed passport attribute. The source enum is `EquipmentAttributeValueHistorySource` with:

- `MANUAL`
- `SYSTEM`
- `IMPORT`
- `API`

Current direct API upserts write `source = API`. `changedBy` and `reason` are nullable because the existing equipment attribute upsert service path does not currently receive an authenticated user or reason argument.

## Value comparison and serialization

History stores normalized string values in `old_value` and `new_value`.

- `TEXT`, `FILE`, `REFERENCE`, `SELECT`, `MULTI_SELECT`, and `JSON` are trimmed and blank-normalized to `null`.
- `NUMBER` and `RANGE` use `BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()`.
- `DATE` uses ISO `LocalDate.toString()`.
- `BOOLEAN` uses `Boolean.toString()`.

This prevents false duplicate history for numeric-equivalent values such as `10`, `10.0`, and `10.00`.

## Capture behavior

`EquipmentAttributeService.upsertValues(...)` now:

- Preserves existing validation for required fields, criticality-required fields, type-specific values, min/max, static options, and option-source-backed values.
- Creates history when a value is first created.
- Creates history when an existing value changes.
- Does not create history when a submitted value is equivalent to the stored value.
- Creates one history row per changed attribute in a multi-attribute request.
- Saves value history only after the value batch passes validation.

## Endpoint added

`GET /api/v1/equipment/{equipmentId}/attributes/history`

Query parameters:

- `page`, default `0`
- `size`, default `20`
- `attributeDefinitionId`, optional

Response:

- `Page<EquipmentAttributeValueHistoryDto>`, ordered by `changedAt DESC`.

## Security behavior

The history endpoint uses the existing equipment read convention:

`SYSTEM_ADMIN` or `*` or `EQUIPMENT_READ`

Value changes still use the existing equipment update permission on the existing upsert endpoints.

## Tests added/updated

Service coverage in `EquipmentAttributeServiceTest`:

- `upsertValues_createsHistoryForNewValue`
- `upsertValues_createsHistoryForChangedValue`
- `upsertValues_doesNotCreateHistoryForUnchangedValue`
- `upsertValues_multipleChangedValues_createMultipleHistoryRows`
- `upsertValues_preservesRequiredPolicyValidation`
- `upsertValues_numericEquivalentValue_doesNotCreateDuplicateHistory`

Controller coverage in `EquipmentAttributeControllerContractTest`:

- `getAttributeValueHistory_returnsPage`
- `getAttributeValueHistory_filtersByAttributeDefinitionId`
- `upsertValues_recordsHistory`

## Commands run

- `git status --short --branch`
- `rg -n "class EquipmentAttributeService|upsertValues|replaceValues|class EquipmentAttributeController|/attributes/history|equipment_attribute" src/main/java src/test/java src/main/resources/db/migration logs || true`
- `/Users/tenzorsoft/.m2/wrapper/dists/apache-maven-3.9.12/6068d197/bin/mvn -Dtest=EquipmentAttributeControllerContractTest,EquipmentAttributeServiceTest test`
- `nc -z localhost 5433; echo postgres_5433_exit=$?`
- `git diff --check`
- `git diff --name-status`
- `git status --short --branch`

## Targeted test result

Passed:

`/Users/tenzorsoft/.m2/wrapper/dists/apache-maven-3.9.12/6068d197/bin/mvn -Dtest=EquipmentAttributeControllerContractTest,EquipmentAttributeServiceTest test`

Result:

- Tests run: 67
- Failures: 0
- Errors: 0
- Skipped: 0
- BUILD SUCCESS

## Full Maven test status

Full `mvn test` was not run because PostgreSQL on `localhost:5433` is not available in this environment:

`postgres_5433_exit=1`

This is consistent with the earlier backend limitation for DB-backed tests.

## Diff hygiene

`git diff --check` passed.

The repository already contains earlier Phase 1A/2A modified and untracked backend files. Those were preserved and not reverted.

## Remaining risks

- `changedBy` is currently nullable because the existing dynamic attribute service method does not accept current-user context.
- `reason` is currently nullable and not accepted by the existing value upsert contract; adding a reasoned manual-edit API can be handled later without breaking this history table.
- JSON and multi-select values are normalized as trimmed strings, not semantic JSON trees. Whitespace-only differences are ignored, but semantically equivalent JSON with different key order may still create history.
- Full DB-backed Maven verification still needs PostgreSQL on `localhost:5433`.

## Phase 2D readiness

Frontend Phase 2D can start against:

- `GET /api/v1/equipment/{equipmentId}/attributes/history`
- Optional `attributeDefinitionId` filter
- `Page<EquipmentAttributeValueHistoryDto>` response
- Source enum values `MANUAL`, `SYSTEM`, `IMPORT`, `API`
