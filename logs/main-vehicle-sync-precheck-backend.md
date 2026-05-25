# Main Vehicle Sync Precheck Backend

Date: 2026-05-25

## Repository

- Path: `/Users/tenzorsoft/Desktop/Work/toir/toir-backend`
- Current branch: `codex/equipment-lifecycle-main-sync-backend`
- Remote: `origin https://gitlab.tenzorsoft.com/tenzorsoft/toir/toir-backend`
- `origin/main`: `944f6bf6e7c0a32ecde163e2c8bce9046a01fd9c`
- Merge base before sync: `9b1c73e2e3379f17855a26dba4fe61078a2056c2`
- Current branch is not `main` or `master`.

## Local Status Before Merge

- `git status --short` showed one untracked valid audit artifact:
  - `logs/equipment-average-life-and-dynamic-attributes-backend-audit.md`
- Decision: include it in the pre-merge save commit with this report and the conflict-analysis report. No secrets, build output, `target`, `.env`, or IDE files were staged.

## Local Commits Not In `origin/main`

- `160d1a8 feat: harden equipment dynamic attributes`
- `5c57d66 feat: add equipment average operating life`

## `origin/main` Commits Not In Local Branch

- `944f6bf Merge branch 'behzod' into 'main'`
- `6228446 Merge branch 'Sardor' into 'main'`
- `5b32424 Merge branch 'behzod' into 'main'`
- `02e5ed1 Merge branch 'behzod' into 'main'`
- `76b6b06 Merge branch 'codex/equipment-lifecycle-main-sync-backend' ... into behzod`
- `dfae851 Merge branch 'main' ... into Sardor`
- `22ec4fc department code`
- plus branch commits visible through merges: `5826c8e`, `0343534`, `6bbb576`, `03919bd`, `e765f60`, `94baeba`

## Changed Files From Our Branch

High-signal files from `origin/main..HEAD`:

- `src/main/java/com/toir/dto/equipment/EquipmentCreateRequest.java`
- `src/main/java/com/toir/dto/equipment/EquipmentUpdateRequest.java`
- `src/main/java/com/toir/dto/equipment/EquipmentDto.java`
- `src/main/java/com/toir/entity/equipment/Equipment.java`
- `src/main/java/com/toir/service/equipment/EquipmentService.java`
- `src/main/java/com/toir/service/equipment/EquipmentAttributeService.java`
- `src/main/java/com/toir/dto/vehicle/VehicleRequest.java`
- `src/main/java/com/toir/service/VehicleService.java`
- `src/main/resources/db/migration/V20260525_5__equipment_average_operating_life_hours.sql`
- `src/test/java/com/toir/migration/EquipmentAverageOperatingLifeMigrationContractTest.java`
- `src/test/java/com/toir/service/equipment/EquipmentServiceTest.java`
- `src/test/java/com/toir/service/equipment/EquipmentAttributeServiceTest.java`
- `src/test/java/com/toir/service/VehicleServiceTest.java`
- Phase reports under `logs/`

## Changed Files From `origin/main`

High-signal files from `HEAD..origin/main`:

- `src/main/java/com/toir/controller/VehicleController.java`
- `src/main/java/com/toir/dto/vehicle/VehicleDetailDto.java`
- `src/main/java/com/toir/dto/vehicle/VehicleDocumentDto.java`
- `src/main/java/com/toir/dto/vehicle/VehicleRequest.java`
- `src/main/java/com/toir/entity/equipment/VehicleDocument.java`
- `src/main/java/com/toir/repository/VehicleDocumentRepository.java`
- `src/main/java/com/toir/service/VehicleService.java`
- `src/main/resources/db/migration/V20260525_5__vehicle_documents.sql`
- `src/test/java/com/toir/controller/VehicleControllerContractTest.java`
- `src/test/java/com/toir/service/VehicleServiceTest.java`
- Department/demo-seed/RBAC test updates

## Overlap Files

Overlapping high-risk files include:

- `src/main/java/com/toir/dto/equipment/EquipmentCreateRequest.java`
- `src/main/java/com/toir/dto/equipment/EquipmentUpdateRequest.java`
- `src/main/java/com/toir/dto/equipment/EquipmentDto.java`
- `src/main/java/com/toir/entity/equipment/Equipment.java`
- `src/main/java/com/toir/service/equipment/EquipmentService.java`
- `src/main/java/com/toir/service/equipment/EquipmentAttributeService.java`
- `src/main/java/com/toir/dto/vehicle/VehicleRequest.java`
- `src/main/java/com/toir/service/VehicleService.java`
- `src/test/java/com/toir/service/VehicleServiceTest.java`
- `src/test/java/com/toir/service/equipment/EquipmentServiceTest.java`
- `src/test/java/com/toir/service/equipment/EquipmentAttributeServiceTest.java`

## High-Risk Notes

- `origin/main` introduced `V20260525_5__vehicle_documents.sql`; our branch already has `V20260525_5__equipment_average_operating_life_hours.sql`. This is a Flyway version collision and requires renaming our unpushed migration if merge proceeds.
- `origin/main` adds multi-document vehicle support and changes `VehicleService` constructor dependencies; our Phase 2 adds `EquipmentAttributeService` to the same service.
- `origin/main` is based on code without Phase 1/Phase 2, so diffs appear to remove `averageOperatingLifeHours`, reserved-key validation, and vehicle dynamic `attributes`. These must be preserved in the merge.
