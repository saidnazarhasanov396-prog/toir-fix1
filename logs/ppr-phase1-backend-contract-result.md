# PPR Phase 1 Backend Contract Result

Date: 2026-05-26
Branch: codex/equipment-lifecycle-main-sync-backend
Base before commit: 947270903ee8dfcb2d0ad79f2d10c4a398488bd8

## Summary

Implemented additive backend contract support for PPR type, schedule, and plan-level targets. This phase intentionally does not implement type-specific PPR generation, automatic generated work orders, or resource/hour-based due calculation.

Existing date-range PPR create/update remains supported through the legacy request constructor and nullable new request fields.

## Files Changed

- `src/main/java/com/toir/enums/PprType.java`
- `src/main/java/com/toir/enums/PprScheduleType.java`
- `src/main/java/com/toir/enums/PprFrequency.java`
- `src/main/java/com/toir/enums/PprScopeType.java`
- `src/main/java/com/toir/enums/PprTargetType.java`
- `src/main/java/com/toir/entity/PprPlan.java`
- `src/main/java/com/toir/entity/PprPlanTarget.java`
- `src/main/java/com/toir/repository/PprPlanTargetRepository.java`
- `src/main/java/com/toir/dto/pprplanning/PprPlanRequest.java`
- `src/main/java/com/toir/dto/pprplanning/PprPlanDto.java`
- `src/main/java/com/toir/dto/pprplanning/PprPlanTargetDto.java`
- `src/main/java/com/toir/service/PprPlanService.java`
- `src/main/resources/db/migration/V20260526_2__ppr_plan_type_schedule_targets.sql`
- `src/test/java/com/toir/controller/PprPlanControllerContractTest.java`
- `src/test/java/com/toir/service/PprPlanServiceLifecycleTest.java`
- `src/test/java/com/toir/migration/PprPlanTypeScheduleTargetsMigrationContractTest.java`

## Migration

Migration added: `V20260526_2__ppr_plan_type_schedule_targets.sql`

The migration:
- adds nullable `ppr_type`, `schedule_type`, `frequency`, `interval_hours`, and `scope_type` columns to `ppr_plans`;
- creates `ppr_plan_targets`;
- adds foreign keys to `ppr_plans`, `equipment`, and `equipment_types`;
- adds active-row indexes and partial unique indexes for active equipment/equipment-type targets;
- keeps all new PPR plan columns nullable for backward compatibility.

No old applied migrations were renamed or modified.

## Enum And Model Changes

Added PPR enums:
- `PprType`: `PREVENTIVE_MAINTENANCE`, `PLANNED_REPAIR`, `CAPITAL_REPAIR`
- `PprScheduleType`: `CALENDAR`, `OPERATING_HOURS`, `ONE_TIME`
- `PprFrequency`: `WEEKLY`, `MONTHLY`, `QUARTERLY`, `YEARLY`
- `PprScopeType`: `DEPARTMENT`, `ENTERPRISE`
- `PprTargetType`: `EQUIPMENT`, `EQUIPMENT_TYPE`

`PprPlan` now stores nullable contract fields and owns `targets` with cascade/orphan removal.

Target storage model:
- table: `ppr_plan_targets`
- entity: `PprPlanTarget`
- one plan can have multiple equipment targets and multiple equipment-type targets;
- `target_type=EQUIPMENT` requires `equipment_id`;
- `target_type=EQUIPMENT_TYPE` requires `equipment_type_id`.

## DTO/API Changes

`PprPlanRequest` now accepts:
- `pprType`
- `scheduleType`
- `frequency`
- `intervalHours`
- `scopeType`
- `equipmentIds`
- `equipmentTypeIds`

`PprPlanDto` now returns:
- `pprType`
- `scheduleType`
- `frequency`
- `intervalHours`
- `scopeType`
- `targets`

`targets` uses `PprPlanTargetDto` with `id`, `targetType`, `equipmentId`, and `equipmentTypeId`.

Legacy request construction with only `name`, `departmentId`, `createdById`, `notes`, `fromDate`, and `toDate` is still supported.

## Validation And Defaults

Default behavior for legacy/null create requests:
- `pprType = PREVENTIVE_MAINTENANCE`
- `scheduleType = CALENDAR`
- `frequency = null`
- `scopeType = DEPARTMENT` when `departmentId` is present
- `scopeType = ENTERPRISE` when `departmentId` is null
- no targets are created unless target arrays are supplied

Schedule validation:
- `CALENDAR` allows `frequency` and rejects `intervalHours`;
- `OPERATING_HOURS` requires `intervalHours > 0` and rejects `frequency`;
- `ONE_TIME` rejects both `frequency` and `intervalHours`.

Scope validation:
- `DEPARTMENT` requires `departmentId`;
- `ENTERPRISE` allows `departmentId` to be null and does not loosen existing security checks.

Target validation:
- duplicate target IDs are rejected;
- null IDs inside target arrays are rejected;
- equipment IDs must exist;
- equipment type IDs must exist;
- equipment targets must belong to the selected department when `departmentId` is present.

Update semantics:
- explicit `equipmentIds`/`equipmentTypeIds` replace plan targets;
- explicit empty arrays clear targets;
- omitted target arrays preserve existing targets for legacy frontend compatibility.

## Generator And Work Order Behavior

PPR generator behavior was preserved. No type-specific generation was added in this phase.

Manual work order creation from PPR task remains unchanged, including the teammate-added validation that the PPR task equipment must match requested work order equipment.

No automatic bulk/generated work order behavior was added.

No resource/hour-based due calculation was added, and `averageOperatingLifeHours` is not used as a current runtime counter.

## Preserved Existing Work

The merge-era teammate changes remain in place:
- `MaintenanceRegulation.templateId` support;
- template compatibility validation in `MaintenanceRegulationService`;
- PPR task to WorkOrder equipment match validation in `WorkOrderService`;
- `V20260526_1__add_template_id_to_maintenance_regulations.sql`.

Existing local lifecycle work remains untouched:
- `averageOperatingLifeHours`;
- dynamic equipment attributes;
- vehicle dynamic/current metrics;
- reserved-key validation for equipment attributes.

## Tests Added/Updated

Updated:
- `PprPlanControllerContractTest`
- `PprPlanServiceLifecycleTest`

Added:
- `PprPlanTypeScheduleTargetsMigrationContractTest`

Covered cases include:
- legacy create without new PPR fields;
- preventive/calendar/monthly create;
- planned repair operating-hours create;
- missing/non-positive interval validation;
- capital repair enterprise and invalid department scope;
- equipment and equipment-type target persistence;
- department mismatch validation for equipment targets;
- update replacement of type/schedule/targets;
- legacy update preserving omitted target arrays;
- response DTO exposure of new fields and targets;
- migration columns, table, foreign keys, and indexes.

## Verification Commands And Results

Passed:

```bash
/Users/tenzorsoft/.m2/wrapper/dists/apache-maven-3.9.12/6068d197/bin/mvn -q -DskipTests compile
```

Passed:

```bash
/Users/tenzorsoft/.m2/wrapper/dists/apache-maven-3.9.12/6068d197/bin/mvn test -Dtest=PprPlanControllerContractTest,PprPlanServiceLifecycleTest,PprPlanServiceListFilterTest,PprGeneratorServiceLifecycleTest,PprGeneratorDynamicConditionTest,WorkOrderServiceTest
```

Result: `Tests run: 144, Failures: 0, Errors: 0, Skipped: 0`

Passed:

```bash
/Users/tenzorsoft/.m2/wrapper/dists/apache-maven-3.9.12/6068d197/bin/mvn test -Dtest=PprPlanTypeScheduleTargetsMigrationContractTest,FlywayMigrationVersionContractTest
```

Result: `Tests run: 2, Failures: 0, Errors: 0, Skipped: 0`

Full suite:

```bash
/Users/tenzorsoft/.m2/wrapper/dists/apache-maven-3.9.12/6068d197/bin/mvn test
```

Result: `Tests run: 1764, Failures: 0, Errors: 48, Skipped: 5`

The full suite failed because local PostgreSQL at `localhost:5433` was unavailable:

```text
Connection to localhost:5433 refused. Check that the hostname and port are correct and that the postmaster is accepting TCP/IP connections.
```

The errors are Spring/JPA context failures in repository/DataJpa tests after the database connection refusal.

Passed:

```bash
git diff --check
```

## Remaining Risks

- Existing database rows created before this migration will have null PPR contract columns until touched by service logic or backfilled in a later phase.
- `PprPlanDto` currently returns persisted enum fields as stored; old rows may return null values.
- Type-specific task generation, hour-counter scheduling, and automatic work order generation are still intentionally missing.
- Enterprise-scope behavior depends on the existing authorization model; this phase does not introduce a broader PBAC model.
- `ppr_plan_targets` stores equipment/equipment-type IDs directly instead of JPA associations, matching existing UUID-based patterns but leaving display-name enrichment for a later API/UI phase.

## Next Recommended Phase

1. Add frontend fields for PPR type, schedule, interval hours, scope, equipment targets, and equipment-type targets against this contract.
2. Define generator behavior by `pprType` and `scheduleType`, including a real current operating-hours source before implementing `OPERATING_HOURS` due logic.
3. Add task/work order generation mapping for preventive, planned repair, and capital repair after contract adoption is verified.
4. Add response enrichment for target display names if the frontend needs it.
