# Main Vehicle Conflict Analysis Backend

Date: 2026-05-25

Branch: `codex/equipment-lifecycle-main-sync-backend`

Analyzed before running `git merge origin/main`.

## 1. Main-Only Changes

`origin/main` adds vehicle document management:

- `VehicleController` adds batch document upload/list/detail/presigned-url/delete endpoints under `/api/v1/vehicles/{equipmentId}/documents`.
- `VehicleDetailDto` adds `documents` while preserving a legacy single `document` reference.
- New files:
  - `VehicleDocumentDto`
  - `VehicleDocument`
  - `VehicleDocumentRepository`
  - `V20260525_5__vehicle_documents.sql`
- `VehicleService` gains document repository support, multi-document upload, cleanup, metadata/presigned URL logic, and legacy single-document compatibility.

Other main changes:

- Department request/service/repository changes.
- Demo seed and demo assertion changes.
- Contract and service test updates for vehicle documents, departments, work orders, RBAC.

Effect on our implementation: vehicle document service changes overlap directly with our Phase 2 `VehicleService` constructor and create/update methods.

## 2. Our-Only Changes

Phase 1:

- `Equipment.averageOperatingLifeHours`
- `average_operating_life_hours` Flyway migration
- `EquipmentCreateRequest`, `EquipmentUpdateRequest`, `EquipmentDto` field exposure
- create required/positive validation and update positive validation
- DTO, controller contract, migration tests

Phase 2:

- `EquipmentAttributeService` reserved-key validation on create, batch create, and update.
- Equipment create passes empty dynamic attributes list when request attributes are omitted, enforcing required definitions.
- `VehicleRequest.attributes`
- `VehicleService` persists vehicle dynamic attributes on create/update while preserving hardcoded vehicle fields.
- Tests for reserved keys, required create enforcement, and vehicle dynamic attributes.

## 3. Overlapping Changes

Textual and semantic overlap:

- `VehicleRequest`: main version lacks `attributes`; our version adds optional `List<EquipmentAttributeValueRequest> attributes` and compatibility helpers.
- `VehicleService`: main adds `VehicleDocumentRepository`; our version adds `EquipmentAttributeService` and dynamic attribute persistence in create/update.
- `VehicleServiceTest`: main adds document tests; our version adds dynamic attribute tests.
- `EquipmentCreateRequest`, `EquipmentUpdateRequest`, `EquipmentDto`, `Equipment`, `EquipmentService`: main is based on pre-Phase-1 code; our Phase 1 fields must remain.
- `EquipmentAttributeService`: main is based on pre-Phase-2 code; reserved-key validation must remain.

## 4. Contract Conflicts

- Equipment create/update/response must keep `averageOperatingLifeHours`.
- Vehicle request must keep all existing hardcoded fields and add optional `attributes`.
- Vehicle response should preserve main's new document response shape. Dynamic attribute values do not need to be embedded because the existing equipment attributes endpoint remains the read path.
- Vehicle create must enforce required dynamic attributes by passing an empty list when `attributes` is omitted.
- Vehicle update should only update dynamic attributes when `attributes` is provided.

## 5. Migration Conflicts

Conflict found:

- Our branch: `V20260525_5__equipment_average_operating_life_hours.sql`
- `origin/main`: `V20260525_5__vehicle_documents.sql`

Resolution plan:

- Preserve `origin/main` vehicle documents migration as `V20260525_5__vehicle_documents.sql`.
- Rename our unpushed Phase 1 migration to the next safe version, expected `V20260525_6__equipment_average_operating_life_hours.sql`.
- Update migration contract tests to reference the renamed migration.
- Do not rename any already-applied migration from `origin/main`.

## 6. Resolution Plan

Expected final backend contract:

- Equipment create requires positive `averageOperatingLifeHours`.
- Equipment update supports positive `averageOperatingLifeHours` when supplied.
- Equipment responses expose `averageOperatingLifeHours`.
- Equipment dynamic attribute definition keys cannot collide with reserved core fields.
- Equipment create enforces required dynamic attributes even when `attributes` is omitted.
- Vehicle create/update preserves hardcoded vehicle fields and accepts optional dynamic `attributes`.
- Vehicle document endpoints and DTOs from `origin/main` remain intact.

Files expected to edit during merge:

- `VehicleRequest`
- `VehicleService`
- `VehicleDetailDto` only if conflict markers require it
- `Equipment*` DTO/entity/service files
- `EquipmentAttributeService`
- migration filenames/tests
- related tests

Actual conflict files will be appended after `git merge origin/main`.

## 7. Actual Conflict Files After Merge

`git merge origin/main` produced one textual conflict:

- `src/main/java/com/toir/service/VehicleService.java`

Other Vehicle/document, department, demo seed, and test files merged automatically.

Manual resolution required:

- Keep `VehicleDocumentRepository` from `origin/main`.
- Keep `EquipmentAttributeService` from Phase 2.
- Ensure both constructor dependencies exist and create/update still call `equipmentAttributeService.upsertValues(...)`.
