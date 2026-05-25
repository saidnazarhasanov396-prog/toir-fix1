# Backend remaining test fix result

## Remaining failures found
- `com.toir.controller.equipment.EquipmentAttributeControllerContractTest`
  - failing test: `batchCreateRejectsInvalidBody`
  - observed: expected `400`, got `500`
- `com.toir.migration.FlywayEmptyDbSmokeTest`
  - failing test: `migrateFromEmptyDatabaseShouldApplyBaselineSuccessfully`
  - observed: `Could not find a valid Docker environment`

## Root cause analysis
1. Batch attribute endpoint:
- Invalid batch request payload path could throw validation-related exceptions that were not explicitly mapped in global advice in this branch context.
- Batch endpoint contract needed stronger pre-service guards for `null`/empty/null-item and invalid required fields to guarantee `400` and avoid accidental fallthrough to service or persistence-level failures.

2. Flyway empty-db smoke test:
- Docker-dependent Testcontainers test was not guarded to skip in Docker-less environment.
- In Codex environment, Docker is unavailable; without skip guard this fails as infrastructure error (not app logic).

## Files changed
- `src/main/java/com/toir/controller/equipment/EquipmentAttributeController.java`
- `src/main/java/com/toir/service/equipment/EquipmentAttributeService.java`
- `src/main/java/com/toir/exception/GlobalExceptionHandler.java`
- `src/test/java/com/toir/controller/equipment/EquipmentAttributeControllerContractTest.java`
- `src/test/java/com/toir/migration/FlywayEmptyDbSmokeTest.java` (added)
- Existing local changes preserved (not reset/discarded):
  - `src/main/resources/application-dev.yml`
  - `src/test/resources/application-test.yml`

## Batch invalid-body 500 -> 400 fix
- Added controller-level batch pre-validation in `createDefinitionsBatch(...)`:
  - `null` list -> `400`
  - empty list -> `400`
  - `null` item -> `400`
  - blank key -> `400`
  - blank label -> `400`
  - null `dataType` -> `400`
  - `minValue > maxValue` -> `400`
  - duplicate normalized keys in same batch -> `400`
- Added service-level defensive validation for request basics (`key`, `label`, `dataType`, non-null request) before entity mapping in:
  - `createDefinition`
  - `createDefinitionsBatch`
  - `updateDefinition`
- Added explicit global advice mapping for `HandlerMethodValidationException` so validation exceptions no longer fall into generic `500`.
- Updated controller contract tests to enforce `400` for:
  - invalid item body
  - `null` body
  - empty list
  - null item inside list
  - and verify service is not called for these invalid payloads.

## FlywayEmptyDbSmokeTest Docker skip fix
- Added `src/test/java/com/toir/migration/FlywayEmptyDbSmokeTest.java`.
- Applied `@Testcontainers(disabledWithoutDocker = true)` so:
  - Docker available -> test runs normally.
  - Docker unavailable -> test is skipped (not failed).
- Test logic itself is preserved (Flyway migrate and schema history assertions).

## Commands run
- `git branch --show-current`
- `git status --short`
- `git log --oneline -5`
- `git diff -- <file>`
- `rg ...` / file inspections (`Get-Content`)
- `git diff --check`
- `git status --short` (pre-commit)
- `git commit -m "fix: handle invalid equipment attribute batch requests"`
- `git push -u origin behzod`

## Explicit environment note
- Tests were **not run** in this Codex task by instruction (`no mvn test`, `no Docker/Testcontainers`, no test execution).

## What to run locally after pull
- `mvn -Dtest=EquipmentAttributeControllerContractTest test`
- `mvn -Dtest=FlywayEmptyDbSmokeTest test`
- `mvn test`

## Push status
- Branch: `behzod`
- Fix commit: `c329f86`
- Push: successful (`origin/behzod`)
