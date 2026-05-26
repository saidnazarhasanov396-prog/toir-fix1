# PPR Phase 3 Backend Generator Result

Date: 2026-05-26
Branch: `codex/equipment-lifecycle-main-sync-backend`
Base commit before Phase 3: `8e02e48 feat: add ppr type schedule target contract`

## Files changed

- `src/main/java/com/toir/service/PprGeneratorService.java`
- `src/test/java/com/toir/service/PprGeneratorServiceLifecycleTest.java`
- `logs/ppr-phase3-backend-generator-result.md`

No migrations were added or renamed. No WorkOrder generation was implemented.

## Generation behavior by pprType

`PprGeneratorService.generateForPlan(...)` now keeps legacy/null behavior and adds filtering only when new Phase 1 fields are explicitly present.

- `pprType = null`: no strict maintenance-kind filter; legacy regulation behavior is preserved.
- `PREVENTIVE_MAINTENANCE`: includes active regulations with maintenance kinds:
  - `PREVENTIVE`
  - `INSPECTION`
  - `DIAGNOSTIC`
  - `CONDITION_BASED`
  - `SEASONAL`
  - `METROLOGICAL`
  - `ELECTRICAL`
  - `INSTRUMENTATION`
- `PLANNED_REPAIR`: includes active regulations with:
  - `CURRENT_REPAIR`
  - `MEDIUM_REPAIR`
- `CAPITAL_REPAIR`: includes active regulations with:
  - `OVERHAUL`

Capital repair does not create WorkOrders, shutdowns, or equipment status changes in this phase.

## Schedule behavior

- `scheduleType = null`: old plan-month alignment logic is preserved.
- `CALENDAR + frequency`: regulations are filtered by matching periodicity unit:
  - `WEEKLY` -> `WEEK`
  - `MONTHLY` -> `MONTH`
  - `QUARTERLY` -> `QUARTER`
  - `YEARLY` -> `YEAR`
- `CALENDAR + frequency = null`: keeps the legacy date/month alignment behavior.
- `ONE_TIME`: does not require weekly/monthly/quarterly/yearly matching and uses the plan `fromDate/toDate` window.
- `OPERATING_HOURS`: returns controlled 400/business error:
  - `Operating-hours PPR generation is not implemented yet`

`averageOperatingLifeHours` is not used as current runtime.

## Target filtering behavior

The generator now reads `PprPlan.targets`:

- Equipment targets generate tasks only for matching `equipmentId`.
- Equipment type targets generate tasks only for matching `equipmentTypeId`.
- If both target kinds exist, matching is a union.
- If no targets exist, legacy behavior is preserved:
  - regulation `equipmentTypeId` is matched to equipment `equipmentTypeId`
  - plan `departmentId` still filters equipment when present
  - dynamic attribute conditions still apply
- Decommissioned equipment remains excluded.

For `scopeType = ENTERPRISE`, generation can run without `departmentId`; it still uses the same repository/service visibility path as before. No security model was loosened in this service.

## Capital repair behavior

For `CAPITAL_REPAIR + ONE_TIME`:

- only `OVERHAUL` regulations are used
- equipment/equipment-type targets are expanded through normal equipment matching
- `scheduledStart` uses plan start at `09:00`
- `scheduledEnd` uses plan end at `18:00`
- `dueDate` remains plan end at `18:00`

No planned shutdown, out-of-service state, or WorkOrder rows are created.

## Idempotency behavior

The generator still skips existing generated task codes and now also skips duplicates by semantic signature:

- `planId`
- `regulationId`
- `equipmentId`

This is implemented in service logic only. No DB unique constraint was added.

## Tests added/updated

Updated `PprGeneratorServiceLifecycleTest` with coverage for:

- legacy draft/generated plan generation still succeeds
- preventive monthly plans use preventive-compatible monthly regulations
- equipment target filtering
- equipment type target filtering
- department-scope filtering
- planned repair yearly filtering
- operating-hours generation rejection
- capital repair one-time overhaul generation and plan window scheduling
- duplicate skip by plan/regulation/equipment signature

Existing dynamic condition tests were run in the targeted suite.

## Verification commands/results

Passed:

```bash
/Users/tenzorsoft/.m2/wrapper/dists/apache-maven-3.9.12/6068d197/bin/mvn -q -DskipTests compile
```

Passed:

```bash
/Users/tenzorsoft/.m2/wrapper/dists/apache-maven-3.9.12/6068d197/bin/mvn test -Dtest=PprGeneratorServiceLifecycleTest,PprGeneratorDynamicConditionTest,PprPlanServiceLifecycleTest,PprPlanControllerContractTest,WorkOrderServiceTest
```

Result: `Tests run: 139, Failures: 0, Errors: 0, Skipped: 0`

Passed:

```bash
/Users/tenzorsoft/.m2/wrapper/dists/apache-maven-3.9.12/6068d197/bin/mvn test -Dtest=PprPlanTypeScheduleTargetsMigrationContractTest,FlywayMigrationVersionContractTest
```

Result: `Tests run: 2, Failures: 0, Errors: 0, Skipped: 0`

Full suite:

```bash
/Users/tenzorsoft/.m2/wrapper/dists/apache-maven-3.9.12/6068d197/bin/mvn test
```

Result: failed due local PostgreSQL being unavailable at `localhost:5433`.

Exact root failure observed:

```text
Connection to localhost:5433 refused. Check that the hostname and port are correct and that the postmaster is accepting TCP/IP connections.
```

Final full-suite summary:

```text
Tests run: 1772, Failures: 0, Errors: 48, Skipped: 5
```

The later errors were Spring `ApplicationContext failure threshold (1) exceeded` cascades after the first database connection failure.

Passed:

```bash
git diff --check
```

## Remaining risks

- Operating-hours generation is intentionally unsupported until a current runtime/counter source is modeled.
- PPR type-to-maintenance-kind mapping is currently code-based; product may later decide whether `OVERHAUL` also belongs in planned repair.
- Idempotency is service-level only; concurrent generation is not protected by a database unique constraint.
- Enterprise scope relies on existing repository visibility/service access patterns; no additional PBAC filtering was added inside the generator.
- Capital repair does not create downtime/shutdown records or change equipment status.

## Next recommended phase

Implement Phase 4 only after product confirms generation-to-work-order rules:

1. Add operating-hours due calculation using an actual current counter/meter source, not `averageOperatingLifeHours`.
2. Define generated WorkOrder contract from PPR tasks, including source links and idempotency.
3. Add capital repair shutdown/downtime modeling if required.
4. Add DB-level uniqueness for generated tasks if concurrent generation becomes a real workflow.
