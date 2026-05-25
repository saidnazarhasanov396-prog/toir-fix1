# Main Vehicle Sync Result Backend

Date: 2026-05-25

## 1. Branch

- Branch: `codex/equipment-lifecycle-main-sync-backend`
- Merge base before sync: `9b1c73e2e3379f17855a26dba4fe61078a2056c2`
- `origin/main` merged: `944f6bf6e7c0a32ecde163e2c8bce9046a01fd9c`

## 2. Commits

- Pre-merge save commit: `bd43f16 wip: save backend vehicle dynamic attribute work before main merge`
- Merge/conflict-resolution commit: `d9de1efe0ece51b873c47630ab4ea0e6bfc30d0b`

## 3. Files Changed

Main vehicle/document changes kept:

- `src/main/java/com/toir/controller/VehicleController.java`
- `src/main/java/com/toir/dto/vehicle/VehicleDetailDto.java`
- `src/main/java/com/toir/dto/vehicle/VehicleDocumentDto.java`
- `src/main/java/com/toir/entity/equipment/VehicleDocument.java`
- `src/main/java/com/toir/repository/VehicleDocumentRepository.java`
- `src/main/java/com/toir/service/VehicleService.java`
- `src/main/resources/db/migration/V20260525_5__vehicle_documents.sql`

Our Phase 1/2 behavior kept:

- `averageOperatingLifeHours` remains in Equipment entity/DTO/request/service/test coverage.
- `EquipmentAttributeService` reserved-key validation remains.
- Equipment create still enforces required dynamic attributes when `attributes` is omitted.
- `VehicleRequest.attributes` remains additive.
- `VehicleService` still persists dynamic vehicle attributes on create/update.
- Average-life migration renamed to `V20260525_6__equipment_average_operating_life_hours.sql`.

## 4. Actual Conflicts

- `src/main/java/com/toir/service/VehicleService.java`

## 5. Semantic Conflicts Found

- `VehicleService` constructor dependencies overlapped: main added `VehicleDocumentRepository`; Phase 2 added `EquipmentAttributeService`.
- Vehicle create/update overlapped semantically: main document support had to coexist with dynamic attribute persistence.
- Flyway duplicate version: main added `V20260525_5__vehicle_documents.sql`; our branch had `V20260525_5__equipment_average_operating_life_hours.sql`.

## 6. Resolution Summary

- Kept both service dependencies in `VehicleService`.
- Kept main's vehicle document read/upload/delete behavior and legacy single-document compatibility.
- Kept Phase 2 dynamic attribute upsert behavior:
  - create passes `List.of()` when vehicle request attributes are omitted
  - update only upserts when `request.attributes()` is non-null
- Kept hardcoded vehicle fields and made dynamic attributes additive.
- Renamed our unpushed average-life migration from version 5 to version 6 and updated its contract test.

## 7. Final API / Payload Behavior

- Equipment create requires positive `averageOperatingLifeHours`.
- Equipment update accepts positive `averageOperatingLifeHours` when supplied.
- Equipment responses expose `averageOperatingLifeHours`.
- Equipment type dynamic attribute keys reject reserved core-field collisions.
- Equipment create enforces required dynamic attributes even if `attributes` is omitted.
- Vehicle create/update accept existing hardcoded vehicle fields plus optional `attributes`.
- Vehicle document endpoints from `origin/main` are preserved.

## 8. Migration Conflict Check

- Duplicate Flyway version was found.
- Kept `origin/main`: `V20260525_5__vehicle_documents.sql`.
- Renamed our unpushed migration to `V20260525_6__equipment_average_operating_life_hours.sql`.
- No old applied migration from `origin/main` was renamed.

## 9. Verification

- `git diff --check`: passed
- Maven compile command attempted with `./mvnw`, `mvn`, then `/tmp/apache-maven-3.9.11/bin/mvn`.
- Result: Maven unavailable locally.
- `<maven> -q -DskipTests compile`: not run because Maven unavailable.
- `<maven> test`: not run because Maven unavailable.

## 10. Remaining Risks

- Backend compile/tests must be run on a Maven-capable machine.
- If the old `V20260525_5__equipment_average_operating_life_hours.sql` migration was already applied outside this local branch, the migration rename must be reviewed before deployment. Based on local context, Phase 1/2 were not pushed before this sync.
- Existing data with reserved dynamic attribute keys is not automatically cleaned up.

## 11. Push Status

- Pushed: no
