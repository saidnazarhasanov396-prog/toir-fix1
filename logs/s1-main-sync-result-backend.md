# S1 Main Sync Result - Backend

Date: 2026-05-21

## 1. Merge result

Merged `origin/main` into `cadex-toir-main-sync`.

Merge commit: `01e3a1c` (`Merge remote-tracking branch 'origin/main' into cadex-toir-main-sync`)

## 2. Conflict files

None.

## 3. Conflict resolution

No backend conflicts occurred. Main changes were included by Git's merge strategy without manual conflict edits.

After verification exposed local Java 24 toolchain issues, `pom.xml` was updated only for build-tool compatibility:

- Pin Lombok to `1.18.38`.
- Explicitly enable Lombok annotation processing in `maven-compiler-plugin`.
- Add `-Dnet.bytebuddy.experimental=true` to Surefire for Mockito/Byte Buddy on Java 24.

This change does not alter application logic, RBAC/PBAC, migrations, or lifecycle gates.

## 4. Main updates now included

- Dynamic equipment passport attribute model, DTOs, repositories, controller, and service.
- Maintenance regulation attribute condition model, DTOs, repositories, and service integration.
- PPR generator filtering by equipment attribute conditions.
- New Flyway migrations:
  - `V20260521_1__equipment_dynamic_passport_attributes.sql`
  - `V20260521_2__maintenance_regulation_attribute_conditions.sql`
- New and updated tests for dynamic equipment attributes, maintenance regulation attribute conditions, and PPR generation conditions.

## 5. cadex-toir business features preserved

- P1-11 generic approval integration remains present.
- Approval document semantics for `WORK_ORDER`, `PPR_PLAN`, `PROCUREMENT_REQUEST`, and `MAINTENANCE_BUDGET` were not directly changed by main.
- Existing P0/P1 lifecycle gates, RBAC/PBAC services, scope access, and completed phase tests were not removed by the merge.
- `SYSTEM_ADMIN` and `"*"` behavior were not directly changed by the backend merge.

## 6. Behavior changes discovered

- PPR task generation now additionally filters matching equipment by maintenance regulation attribute conditions.
- Equipment create/update/detail contracts now include dynamic attribute values.
- Maintenance regulation request/response contracts now include attribute conditions.

## 7. Risky files requiring manual review

- `src/main/java/com/toir/service/PprGeneratorService.java`
- `src/main/java/com/toir/service/maintanance/MaintenanceRegulationService.java`
- `src/main/java/com/toir/service/equipment/EquipmentService.java`
- `src/main/java/com/toir/dto/equipment/EquipmentCreateRequest.java`
- `src/main/java/com/toir/dto/equipment/EquipmentUpdateRequest.java`
- `src/main/java/com/toir/dto/equipment/EquipmentDetailDto.java`
- `src/main/java/com/toir/dto/maintenanceregulation/MaintenanceRegulationRequest.java`
- `src/main/java/com/toir/dto/maintenanceregulation/MaintenanceRegulationDto.java`
- New Flyway migrations under `src/main/resources/db/migration/`.
- `pom.xml` build-tool compatibility configuration.

## 8. Tests run

- Initial `mvn` command failed because `mvn` is not on PATH in this shell. Used IntelliJ bundled Maven at `/Applications/IntelliJ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn`.
- Initial bundled Maven run exposed Java 24 toolchain issues:
  - Lombok annotation processing disabled/incompatible with inherited Lombok `1.18.34`.
  - Mockito/Byte Buddy rejected Java 24 class file version without `net.bytebuddy.experimental=true`.
- After the `pom.xml` build-tool compatibility fix, the requested targeted checks passed:
  - `mvn test -Dtest=ApprovalPbacScopeTest,RbacApprovalSecurityTest`: PASS, 37 tests.
  - `mvn test -Dtest=WorkOrderServiceTest,WorkOrderControllerContractTest,RbacWorkOrderSecurityTest,WorkOrderPbacScopeTest`: PASS, 132 tests.
  - `mvn test -Dtest=PprPlanControllerContractTest,PprPlanServiceTaskCodePolicyTest,RbacPprSecurityTest,PprPbacScopeTest`: PASS, 59 tests.
  - `mvn test -Dtest=ProcurementRequestServiceTest,RbacProcurementSecurityTest,ProcurementPbacScopeTest`: PASS, 41 tests.
  - `mvn test -Dtest=ActualCostServiceTest,RbacActualCostSecurityTest,ActualCostPbacScopeTest,BudgetPbacScopeTest,RbacBudgetSecurityTest`: PASS, 47 tests.
  - `mvn test -Dtest=SecurityAccessServiceTest,ScopeAccessServiceTest`: PASS, 20 tests.
- Full `mvn test`: not run because PostgreSQL is not listening on `localhost:5433`.
- `git diff --check`: PASS.
- `rg "<<<<<<<|=======|>>>>>>>" .`: no backend output.
- `rg "<<<<<<<|>>>>>>>" .`: no backend output.

## 9. Remaining blockers

Full broad backend test remains blocked by missing local PostgreSQL on `localhost:5433`.
