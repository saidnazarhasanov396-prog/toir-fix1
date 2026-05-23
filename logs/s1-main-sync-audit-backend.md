# S1 Main Sync Audit - Backend

Date: 2026-05-21

## 1. Current branch

`cadex-toir-main-sync`, created from `cadex-toir`.

## 2. Merge base commit

`c3d04d0ce4f1f10bddbb0056c5d57113f03026a4` (`p1-11`)

## 3. New commits from origin/main

```text
7bae9c9 (origin/main, origin/HEAD) Merge branch 'behzod' into 'main'
f7d288a (origin/Sardor) dynamic ppr plan tests
83408ed dynamic ppr plan
81b50c4 Merge branch 'behzod' into 'main'
779ea5b Merge branch 'main' of https://gitlab.tenzorsoft.com/tenzorsoft/toir/toir-backend into behzod
21e88bf Merge branch 'behzod' into 'main'
```

## 4. Files changed in origin/main since merge base

```text
A docs/frontend-equipment-dynamic-passport-report.md
A src/main/java/com/toir/controller/equipment/EquipmentAttributeController.java
M src/main/java/com/toir/dto/equipment/EquipmentCreateRequest.java
M src/main/java/com/toir/dto/equipment/EquipmentDetailDto.java
M src/main/java/com/toir/dto/equipment/EquipmentUpdateRequest.java
A src/main/java/com/toir/dto/equipmentattribute/EquipmentAttributeDefinitionDto.java
A src/main/java/com/toir/dto/equipmentattribute/EquipmentAttributeDefinitionRequest.java
A src/main/java/com/toir/dto/equipmentattribute/EquipmentAttributeValueDto.java
A src/main/java/com/toir/dto/equipmentattribute/EquipmentAttributeValueRequest.java
A src/main/java/com/toir/dto/maintenanceregulation/MaintenanceRegulationAttributeConditionDto.java
A src/main/java/com/toir/dto/maintenanceregulation/MaintenanceRegulationAttributeConditionRequest.java
M src/main/java/com/toir/dto/maintenanceregulation/MaintenanceRegulationDto.java
M src/main/java/com/toir/dto/maintenanceregulation/MaintenanceRegulationRequest.java
A src/main/java/com/toir/entity/equipment/EquipmentAttributeDefinition.java
A src/main/java/com/toir/entity/equipment/EquipmentAttributeValue.java
A src/main/java/com/toir/entity/maintenance/MaintenanceRegulationAttributeCondition.java
A src/main/java/com/toir/enums/EquipmentAttributeDataType.java
A src/main/java/com/toir/enums/MaintenanceRegulationConditionOperator.java
A src/main/java/com/toir/repository/equipment/EquipmentAttributeDefinitionRepository.java
A src/main/java/com/toir/repository/equipment/EquipmentAttributeValueRepository.java
A src/main/java/com/toir/repository/maintenance/MaintenanceRegulationAttributeConditionRepository.java
M src/main/java/com/toir/service/PprGeneratorService.java
A src/main/java/com/toir/service/equipment/EquipmentAttributeService.java
M src/main/java/com/toir/service/equipment/EquipmentService.java
M src/main/java/com/toir/service/maintanance/MaintenanceRegulationService.java
A src/main/resources/db/migration/V20260521_1__equipment_dynamic_passport_attributes.sql
A src/main/resources/db/migration/V20260521_2__maintenance_regulation_attribute_conditions.sql
A src/test/java/com/toir/controller/EquipmentAttributeControllerContractTest.java
M src/test/java/com/toir/controller/EquipmentControllerContractTest.java
M src/test/java/com/toir/controller/MaintenanceRegulationControllerContractTest.java
A src/test/java/com/toir/service/PprGeneratorDynamicConditionTest.java
A src/test/java/com/toir/service/equipment/EquipmentAttributeServiceTest.java
M src/test/java/com/toir/service/equipment/EquipmentServiceTest.java
M src/test/java/com/toir/service/maintanance/MaintenanceRegulationServiceTest.java
```

Diff stat: 34 files changed, 3263 insertions, 13 deletions.

## 5. Risky area audit

- Approval: no direct approval engine or approval integration files changed in main.
- Work order: no direct work order service/controller/security changes in main.
- PPR: `PprGeneratorService` changed to support dynamic equipment attribute conditions during task generation; this is a conflict hotspot with P1-07 plan approval/task execution gates.
- Procurement: no direct procurement backend files changed in main.
- Budget/actual cost: no direct changes.
- Repair request: no direct changes.
- Defect: no direct changes.
- Inspection: no direct changes.
- Contractor work: no direct changes.
- RBAC/PBAC/security: no direct security, scope, role, permission, or PBAC service changes in main.
- Migrations: two new Flyway migrations were added. They are new files and must be preserved; no old migration edits are present in the main delta.
- DTO/API contracts: equipment create/update/detail DTOs and maintenance regulation request/response DTOs changed. These affect frontend/backend compatibility.
- Frontend route guards/action guards: not applicable in backend.

## 6. Initial risk level

MEDIUM.

Reason: main does not directly change approval/security/lifecycle governance files, but it changes PPR generation and DTO/API contracts around equipment and maintenance regulation. Those areas intersect with completed P1-07 PPR approval-before-task-execution semantics and frontend compatibility.

## 7. Expected conflict hotspots

- `src/main/java/com/toir/service/PprGeneratorService.java`
- `src/main/java/com/toir/service/maintanance/MaintenanceRegulationService.java`
- `src/main/java/com/toir/service/equipment/EquipmentService.java`
- `src/main/java/com/toir/dto/equipment/EquipmentCreateRequest.java`
- `src/main/java/com/toir/dto/equipment/EquipmentDetailDto.java`
- `src/main/java/com/toir/dto/equipment/EquipmentUpdateRequest.java`
- `src/main/java/com/toir/dto/maintenanceregulation/MaintenanceRegulationDto.java`
- `src/main/java/com/toir/dto/maintenanceregulation/MaintenanceRegulationRequest.java`
- New migrations under `src/main/resources/db/migration/`
- Tests around equipment, maintenance regulation, and PPR generation.
