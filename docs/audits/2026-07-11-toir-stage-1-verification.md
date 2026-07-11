# TOiR Stage 1 Safety and Integrity Verification

**Date:** 2026-07-11

**Branch:** `Codex_org`

**Scope:** Stage 1 of the TOiR business-logic remediation: schema integrity, operation-specific authorization, Work Order safety, Repair Campaign generation idempotency, and exact money boundaries.

## Result

**PASS for the scoped Stage 1 gate.** At detached backend commit `a020d88a`, the exact targeted backend suite passed 240/240 tests. At detached frontend commit `f3123242`, the exact frontend contract suite passed 142/142 tests and the production build succeeded.

The authoritative commands ran in isolated temporary worktrees so concurrent jobs in the active checkouts could not share Maven `target/`, TypeScript build metadata, or Vite output. Unrelated commits continued landing on both active branches while verification ran; those later employee-assignment, equipment, reporting, warehouse, and merge changes are outside this Stage 1 claim.

Stage 1 deliberately does not implement the Planned Shutdown aggregate and lifecycle. It establishes the interfaces that Stage 2 will consume.

## Delivered controls

### Database and persistence

- `V20260711_1__toir_stage1_integrity_foundation.sql` adds optimistic `version` columns to Planned Shutdown and Repair Campaign.
- Work Orders persist `requires_shutdown`, `requires_isolation`, and a nullable deterministic `generation_key`.
- Active, non-deleted Work Orders have database-enforced uniqueness through `uq_work_orders_active_generation_key`.
- Repair Campaign monetary columns use `numeric(19,4)`, and campaigns persist a three-character `currency_code` with the compatibility default `UZS`.
- JPA maps Planned Shutdown and Repair Campaign versions with `@Version`.

### Authorization

- Planned Shutdown and Repair Campaign controllers use operation-specific authorities rather than unrelated broad permissions.
- Approval-domain permission resolution uses the corresponding Planned Shutdown or Repair Campaign approval permission.
- The established seeded roles receive documented conservative/full permission sets; the migration does not target nonexistent role codes.
- Frontend permission constants and action checks use the same permission identifiers as the backend.

### Work Order safety

- Work Order request and response contracts round-trip `requiresShutdown` and `requiresIsolation`.
- A Work Order marked with either flag cannot start without a safety checklist.
- An ordinary Work Order with neither flag remains backward-compatible when no checklist exists.

### Repair Campaign generation

- Generation is rejected unless the campaign is `APPROVED` or `IN_PROGRESS`.
- Each generated Work Order uses `RC:<campaignId>:<stageId>:<equipmentId>` as its canonical generation key.
- Equipment is processed in deterministic UUID order.
- A transaction-scoped PostgreSQL advisory lock serializes lookup/create for each generation key; the partial unique index remains the final database authority.
- Replays return the existing canonical active Work Order instead of creating duplicates.
- The API requires an idempotency key, and the frontend preserves it across retries and rotates it only after success.

### Exact money boundaries

- Backend Repair Campaign monetary values use `BigDecimal`, normalized to scale 4 with `HALF_UP` at the finance boundary.
- JSON monetary fields are decimal strings; explicit JSON `null` is rejected while omitted optional/default fields retain the documented default behavior.
- Frontend Repair Campaign contracts preserve decimal strings without JavaScript number coercion and carry `currencyCode`.

## Commit evidence

| Area | Backend commit(s) | Frontend commit(s) |
|---|---|---|
| Stage 1 design and plan | `a689f9af`, `74c340b1` | `8c8d53df` |
| Integrity schema | `fa474ea0` | — |
| Business-flow permissions | `39f1dcb0`, `44bb7abb` | `63535658` |
| Fail-closed safety flags | `29204a16` | — |
| Idempotent generation | `3cc7161d`, `1fa79eed` | `fdc16997` |
| Exact money | `a228390c`, `f8c61869` | `50bc0611` |

The repository also contains later, separately scoped employee-assignment and frontend workflow commits. They are not claimed as Stage 1 evidence.

## Final verification commands

| Check | Exact command | Observed result |
|---|---|---|
| Backend Stage 1 suite | `JAVA_HOME=$(/usr/libexec/java_home -v 24) ./mvnw -Dtest=ToirStage1IntegrityMigrationContractTest,RbacToirBusinessFlowSecurityTest,SafetyChecklistServiceTest,WorkOrderServiceTest,RepairCampaignServiceTest,RepairCampaignControllerContractTest,ApprovalServiceTest test` | **PASS** — 240 tests, 0 failures, 0 errors, 0 skipped; `BUILD SUCCESS`; `/tmp/toir-stage1-backend-task6` at `a020d88a` |
| Frontend contracts | `yarn test --run src/lib/access-control.test.ts src/lib/api.test.ts src/modules/repairs/libs/repair-campaigns/tests/repair-campaign-form-contract.test.ts` | **PASS** — 3 files, 142 tests; `/tmp/toir-stage1-frontend-task6` at `f3123242` |
| Frontend production build | `yarn build` | **PASS** — TypeScript project build and Vite production bundle completed; Vite emitted only its non-fatal large-chunk warning |
| Migration contract | `JAVA_HOME=$(/usr/libexec/java_home -v 24) ./mvnw -Dtest=ToirStage1IntegrityMigrationContractTest test` | **PASS** — 3 tests, 0 failures/errors/skips; `BUILD SUCCESS` |
| PostgreSQL empty-DB smoke | `JAVA_HOME=$(/usr/libexec/java_home -v 24) ./mvnw -Dtest=FlywayEmptyDbSmokeTest test` | **NOT RUN** — `docker info` exited 127: `docker: command not found` |
| Diff hygiene | `git diff --check` in each active repository | **PASS** — exit 0 in both repositories before the report commits |
| Repository state | `git status --short --branch` and `git log --oneline` in each repository | **CAPTURED** — active checkout details below; detached verification worktrees were clean |

### Superseded infrastructure attempt

The same backend command was first attempted in the active checkout and failed during Maven's global `testCompile` with 100 unrelated missing-symbol errors. Investigation showed the source files were present and tracked but their class outputs were absent while a concurrently launched Equipment Maven test was writing the same `target/` directory. The isolated rerun compiled all 1,520 main and 476 test sources and passed 240/240, so the shared-output attempt is recorded as superseded infrastructure evidence, not a product failure.

## Negative-path evidence

The targeted tests cover unrelated-authority rejection, operation-specific authorization, shutdown/isolation work without a checklist, ordinary-work compatibility, draft campaign generation rejection, idempotent replay, deterministic generation keys, exact decimal serialization, excessive-scale normalization, and explicit-null rejection.

## Known limitations and Stage 2 contract

- No repository-wide backend or frontend test suite is claimed here.
- No repository-wide frontend lint result is claimed here.
- The PostgreSQL advisory lock and Flyway migration were not live-smoke-tested because the Docker CLI is not installed. Contract and service ordering tests passed, but this is not equivalent to a live PostgreSQL concurrency/empty-database run.
- The operation annotation matrix does not enumerate every controller method that shares an authority.
- Isolation-only and explicit-null safety-flag symmetry are not separate Work Order test cases, although the implementation uses the same OR guard for both flags.
- Stage 2 must build the Planned Shutdown aggregate and lifecycle on the verified `@Version`, safety-flag, and operation-permission interfaces. Stage 1 does not infer or fabricate shutdown safety evidence.

## Working-tree isolation

Authoritative verification used clean detached worktrees `/tmp/toir-stage1-backend-task6` (`a020d88a`) and `/tmp/toir-stage1-frontend-task6` (`f3123242`). The active backend branch advanced while tests ran and, at the final pre-report capture, was ahead of `origin/Codex_org` with unrelated reporting/vehicle/equipment work in progress:

- `src/main/java/com/toir/service/ReportsService.java`
- `src/main/java/com/toir/service/equipment/EquipmentService.java`
- `src/test/java/com/toir/migration/VehicleDriverEmployeeIdentityPreflightContractTest.java`
- `src/test/java/com/toir/service/VehicleServiceDriverAssignmentTest.java`
- `src/test/java/com/toir/service/equipment/EquipmentResponsibleRefTest.java`
- `src/test/java/com/toir/service/ReportsServiceTest.java` (untracked)

The active frontend checkout was clean apart from this untracked report at the same capture. All unrelated files were excluded from both report commits and from the Stage 1 pass/fail claim.
