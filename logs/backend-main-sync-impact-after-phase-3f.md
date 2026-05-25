# Backend main sync impact after Phase 3F

Date: 2026-05-23

## Branches and commits

- Starting branch: `codex/equipment-lifecycle-main-sync-backend`
- Original branch commit before sync: `328259d0e546db005082b46aa0c07fb5cd7d697e`
- Latest `main` commit merged: `fd01069dcf23f6522a3979c07f7c93c50594251a`
- Merge base used for incoming analysis: `328259d0e546db005082b46aa0c07fb5cd7d697e`
- Merge command: `git merge --no-ff main`
- Text conflicts: none
- Non-textual migration version conflict: yes, resolved by renaming branch-owned node-target migrations.

## Incoming main commits

`git log --oneline --decorate $BASE..main` showed:

```text
fd01069 (origin/main, origin/HEAD, main) Merge branch 'behzod' into 'main'
0754b4d Merge branch 'codex/equipment-lifecycle-main-sync-backend' of https://gitlab.tenzorsoft.com/tenzorsoft/toir/toir-backend into behzod
3c9d9ac (origin/Sardor) Merge branch 'main' of https://gitlab.tenzorsoft.com/tenzorsoft/toir/toir-backend into Sardor
98319aa ppr
9b92aa6 Merge branch 'behzod' into 'main'
ac2ac4e Merge branch 'codex/equipment-lifecycle-main-sync-backend' of https://gitlab.tenzorsoft.com/tenzorsoft/toir/toir-backend into behzod
0b412e6 Merge branch 'Sardor' of https://gitlab.tenzorsoft.com/tenzorsoft/toir/toir-backend into behzod
d32d7d4 workflow
a0c4890 changes
c5219f5 changes
c94b4db changes
c2365ba Merge branch 'main' of https://gitlab.tenzorsoft.com/tenzorsoft/toir/toir-backend into behzod
85dcff2 Merge branch 'codex/equipment-lifecycle-main-sync-backend' into 'main'
cccca17 Merge branch 'cadex-toir-p1-12' of https://gitlab.tenzorsoft.com/tenzorsoft/toir/toir-backend into behzod
```

## Incoming main changed files

`git diff --name-status $BASE..main` showed 44 changed files:

```text
M	docs/frontend-equipment-dynamic-passport-report.md
M	pom.xml
M	src/main/java/com/toir/controller/OeeController.java
M	src/main/java/com/toir/controller/PprPlanController.java
M	src/main/java/com/toir/controller/equipment/EquipmentAttributeController.java
M	src/main/java/com/toir/controller/maintenance/MaintenanceAdvisorController.java
M	src/main/java/com/toir/dto/equipmentattribute/EquipmentAttributeDefinitionDto.java
M	src/main/java/com/toir/dto/equipmentattribute/EquipmentAttributeOptionSourceDto.java
M	src/main/java/com/toir/dto/pprplanning/PprPlanDto.java
M	src/main/java/com/toir/dto/pprplanning/PprPlanRequest.java
M	src/main/java/com/toir/dto/pprplanning/PprTaskRequest.java
M	src/main/java/com/toir/dto/uom/UnitOfMeasurementDto.java
M	src/main/java/com/toir/entity/PprPlan.java
M	src/main/java/com/toir/entity/PprTask.java
M	src/main/java/com/toir/repository/OeeRecordRepository.java
M	src/main/java/com/toir/repository/PprPlanRepository.java
M	src/main/java/com/toir/repository/equipment/EquipmentAttributeOptionSourceRepository.java
M	src/main/java/com/toir/service/OeeService.java
M	src/main/java/com/toir/service/PprGeneratorService.java
M	src/main/java/com/toir/service/PprPlanService.java
M	src/main/java/com/toir/service/RcmAutoPlannerService.java
M	src/main/java/com/toir/service/UnitOfMeasurementService.java
M	src/main/java/com/toir/service/equipment/EquipmentAttributeService.java
M	src/main/java/com/toir/service/maintanance/MaintenanceAdvisor.java
A	src/main/resources/db/migration/V20260523_4__ppr_plan_date_range_and_optional_task_equipment.sql
M	src/test/java/com/toir/controller/EquipmentAttributeControllerContractTest.java
M	src/test/java/com/toir/controller/OeeControllerContractTest.java
M	src/test/java/com/toir/controller/PprPlanControllerContractTest.java
M	src/test/java/com/toir/controller/equipment/EquipmentAttributeControllerContractTest.java
A	src/test/java/com/toir/controller/maintenance/MaintenanceAdvisorControllerContractTest.java
M	src/test/java/com/toir/security/PprPbacScopeTest.java
M	src/test/java/com/toir/security/PprPlanEndpointSecurityTest.java
M	src/test/java/com/toir/security/RbacPprSecurityTest.java
M	src/test/java/com/toir/security/RoleMatrixEndpointAccessSmokeTest.java
M	src/test/java/com/toir/service/ConditionReadingServiceTest.java
M	src/test/java/com/toir/service/OeeServiceTest.java
M	src/test/java/com/toir/service/PprGeneratorDynamicConditionTest.java
M	src/test/java/com/toir/service/PprGeneratorServiceLifecycleTest.java
M	src/test/java/com/toir/service/PprPlanServiceLifecycleTest.java
M	src/test/java/com/toir/service/PprPlanServiceListFilterTest.java
M	src/test/java/com/toir/service/PprPlanServiceTaskCodePolicyTest.java
M	src/test/java/com/toir/service/UnitOfMeasurementServiceTest.java
M	src/test/java/com/toir/service/WorkOrderServiceTest.java
M	src/test/java/com/toir/service/equipment/EquipmentAttributeServiceTest.java
```

Incoming diff stat: 44 files changed, 1014 insertions, 224 deletions.

## Initial impact assessment

- Exact file overlap with branch phase files: none by path, because the branch head was the merge base for `main`.
- Practical overlap: dynamic passport equipment attribute DTO/service/controller changed in main and can affect Phase 1C/2A API contract consumers.
- Practical overlap: work-order tests changed in main, but production work-order node-target behavior was not changed.
- Migrations: main introduced `V20260523_4__ppr_plan_date_range_and_optional_task_equipment.sql`, which collided by Flyway version with this branch's `V20260523_4__defects_equipment_node_target.sql`.
- Security/RBAC/PBAC: main updated PPR/security tests only. No phase permission endpoint was removed.
- POM/build: main added `org.testcontainers:postgresql` test dependency.

## Conflict resolution summary

Textual merge conflicts: none.

Flyway version/order resolution:

- Kept main migration as `V20260523_4__ppr_plan_date_range_and_optional_task_equipment.sql`.
- Renamed branch migration `V20260523_4__defects_equipment_node_target.sql` to `V20260523_5__defects_equipment_node_target.sql`.
- Renamed branch migration `V20260523_5__technical_documents_equipment_node_target.sql` to `V20260523_6__technical_documents_equipment_node_target.sql`.
- Renamed branch migration `V20260523_6__work_orders_equipment_node_target.sql` to `V20260523_8__work_orders_equipment_node_target.sql`.
- Migration contents were not changed or removed.

Final relevant Flyway order:

```text
V20260523_1__equipment_status_history.sql
V20260523_2__equipment_attribute_required_criticality.sql
V20260523_3__equipment_attribute_value_history.sql
V20260523_4__ppr_plan_date_range_and_optional_task_equipment.sql
V20260523_5__defects_equipment_node_target.sql
V20260523_6__technical_documents_equipment_node_target.sql
V20260523_8__work_orders_equipment_node_target.sql
```

## Impact matrix

| Area | Incoming main changes | Our phase files affected? | Conflict? | Resolution | Risk | Follow-up needed? |
| --- | --- | --- | --- | --- | --- | --- |
| Equipment status lifecycle | No status lifecycle production changes. | No. Endpoints and history service remain present. | No. | Preserved `PATCH /api/v1/equipment/{id}/status`, `GET /api/v1/equipment/{id}/status-history`, decommission guards, and direct status-change rejection in generic update. | LOW | No. |
| Dynamic passport | Option source list became paginated/searchable; attribute definition `unit` response changed from string to `UnitOfMeasurementDto`; `UnitOfMeasurementDto` changed. | Yes, indirect API contract overlap with Phase 1C/2A DTO/service/controller. | No text conflict. | Preserved direct list payload for attribute values, `requiredForCriticalityClassIds`, required criticality policy, and value history endpoint. Accepted main DTO/service contract updates. | MEDIUM | Yes, frontend contract note created. |
| Equipment node hierarchy | No incoming main hierarchy changes. | No. | No. | Preserved `parentId`, `parentNodeId` alias, same-equipment parent validation, self/circular checks, serial uniqueness validation, and delete restrictions. | LOW | No. |
| Defects node target | No incoming main defect production changes. | No. | Flyway version only. | Preserved optional `equipmentNodeId`, response node fields, same-equipment validation, and delete conflict. Migration renamed to version 5. | LOW | No. |
| Technical documents node target | No incoming main technical document production changes. | No. | Flyway version sequencing only. | Preserved optional `equipmentNodeId`, node fields, `GET /api/v1/equipment-nodes/{nodeId}/documents`, delete conflict, and `documentType`/`fileAssetId` aliases. Migration renamed to version 6. | LOW | No. |
| Work orders node target | PPR-related tests touched `WorkOrderServiceTest`; no generic work-order update added. | Test overlap only. | Flyway version sequencing only. | Preserved optional `equipmentNodeId`, response node fields, defect inheritance, delete conflict, and no generic work-order update. Migration renamed to version 7. | LOW | No. |
| Equipment node lifecycle endpoint | No incoming main lifecycle changes. | No. | No. | Preserved `GET /api/v1/equipment-nodes/{nodeId}/lifecycle`, `includeTimeline`, `limit`, node summary/counts/defects/workOrders/documents/timeline response. | LOW | No. |
| Flyway migrations | Main added PPR date range migration at `V20260523_4`. | Yes, duplicate version with phase node-target migration. | Non-textual Flyway version conflict. | Kept main at version 4; shifted branch node-target migrations to 5, 6, and 7. | MEDIUM | Verify deployed environments have not already applied old 4/5/6 names from this unmerged branch. |
| Tests/build tooling | Main added Testcontainers PostgreSQL test dependency and many PPR/OEE/UOM/maintenance-advisor tests. | Targeted phase test set still compiles/passes. | No. | Used cached Maven 3.9.12 binary because `mvn` was not on PATH and no `mvnw` exists in repo. | LOW | PATH cleanup optional. |
| Security/RBAC/PBAC | Main updated PPR and role matrix security tests. | No phase behavior removed. | No. | Accepted main security test changes. Phase targeted contract/security surfaces remain covered by targeted tests. | LOW | No. |
| API contracts / DTOs | PPR plan request/response moved from `year`/`month` to `fromDate`/`toDate`; PPR list/stats added `day`; PPR task `equipmentId` optional; option sources now paged; attribute definition `unit` now object; maintenance advisor added filters/stats; OEE no-filter list now returns all. | Dynamic passport has contract impact; other impacts are incoming main features outside lifecycle phases. | No text conflict. | Preserved all phase DTO fields; accepted main contract changes. | MEDIUM | Frontend contract note created. |

## Files changed by merge/conflict resolution

The merge brought in the incoming main files listed above. Additional conflict-resolution changes:

```text
R100 src/main/resources/db/migration/V20260523_4__defects_equipment_node_target.sql -> src/main/resources/db/migration/V20260523_5__defects_equipment_node_target.sql
R100 src/main/resources/db/migration/V20260523_5__technical_documents_equipment_node_target.sql -> src/main/resources/db/migration/V20260523_6__technical_documents_equipment_node_target.sql
R100 src/main/resources/db/migration/V20260523_6__work_orders_equipment_node_target.sql -> src/main/resources/db/migration/V20260523_8__work_orders_equipment_node_target.sql
```

Audit/report files added by this sync:

```text
A logs/backend-main-sync-impact-after-phase-3f.md
A logs/backend-main-sync-after-phase-3f-frontend-note.md
```

## Commands run

```text
git status --short --branch
git log --oneline -5
git fetch --all --prune
git checkout main
git pull --ff-only origin main
git rev-parse main
git checkout codex/equipment-lifecycle-main-sync-backend
BASE=$(git merge-base HEAD main); git log --oneline --decorate $BASE..main
BASE=$(git merge-base HEAD main); git diff --name-status $BASE..main
BASE=$(git merge-base HEAD main); git diff --stat $BASE..main
comm -12 <(git diff --name-only $BASE..HEAD | sort) <(git diff --name-only $BASE..main | sort)
git merge --no-ff main
git mv ...V20260523_6__work_orders... ...V20260523_7__work_orders...
git mv ...V20260523_5__technical_documents... ...V20260523_6__technical_documents...
git mv ...V20260523_4__defects... ...V20260523_5__defects...
git diff --name-status main..HEAD
git status --short --branch
git log --oneline --decorate --graph -20
git diff --check
/Users/tenzorsoft/.m2/wrapper/dists/apache-maven-3.9.12/6068d197/bin/mvn -Dtest=EquipmentStatusLifecycleServiceTest,EquipmentServiceTest,EquipmentAttributeControllerContractTest,EquipmentAttributeServiceTest,EquipmentNodeServiceTest,EquipmentNodeControllerContractTest,EquipmentNodeLifecycleServiceTest,DefectServiceTest,DefectControllerContractTest,TechnicalDocumentServiceTest,TechnicalDocumentControllerContractTest,WorkOrderServiceTest,WorkOrderControllerContractTest,TriadSchemaModelContractTest test
/Users/tenzorsoft/.m2/wrapper/dists/apache-maven-3.9.12/6068d197/bin/mvn test
```

Note: the original workspace path `/Users/tenzorsoft/Desktop/Work/toir` is not a Git repository. The backend repository used for all Git and Maven work is `/Users/tenzorsoft/Desktop/Work/toir/toir-backend`.

## Verification results

- `git diff --check`: passed with no whitespace errors.
- Targeted Maven command: passed.
  - Tests run: 338
  - Failures: 0
  - Errors: 0
  - Skipped: 0
- Full `mvn test`: failed due unavailable local PostgreSQL test DB.
  - Tests run: 1629
  - Failures: 0
  - Errors: 47
  - Skipped: 0
  - Root cause observed in output: `Connection to localhost:5433 refused. Check that the hostname and port are correct and that the postmaster is accepting TCP/IP connections.`
  - Erroring test classes were DB-backed repository tests, including warehouse stock, repair request, spare part, vehicle details, work order stats, defect stats/list stats, equipment repository/type/meter stats, knowledge article stats/query, maintenance template stats, and employee search/stats.
  - This matches the known environment limitation and is not treated as a merge/source failure.

## Remaining risks

- Medium: migration rename is safe for this branch before merge to shared environments, but any environment that somehow applied the old unmerged `V20260523_4/5/6` node-target filenames would need Flyway history handling.
- Medium: frontend/API consumers must adapt to incoming main contract changes for PPR and equipment attribute option/unit responses.
- Low: local shell startup prints `/usr/local/bin/brew` missing from `.zprofile`; non-blocking for Git/Maven commands.

## Readiness

- Backend branch is safe to continue the next backend phase after frontend/API consumers acknowledge the main contract changes and the DB-backed full suite is rerun with PostgreSQL available on `localhost:5433`.
- No frontend repository files were modified.
- A frontend-facing note was created because main introduced API contract changes.
