# P0 Demo Evidence Run - 2026-06-06

Scope: P0 release/UAT evidence for the current demo chain without MT-01 Operational Cockpit.

MT-01 status: DEFERRED / PM DECISION PENDING. No Cockpit page, route, endpoint integration, or merged queue was added or used.

## Environment

- Machine timezone: Asia/Tashkent.
- Docker status: blocked locally. `docker --version` returned `command not found`.
- PostgreSQL/Testcontainers gate: blocked locally because Docker is unavailable and local test PostgreSQL on `localhost:5433` is not accepting connections.

## Audit Cleanup

- `P0_IMPLEMENTATION_AUDIT_2026_06_06.md` keeps EQ-03, MT-02, MT-04, SP-02, and FN-01 at `PARTIAL++`.
- MT-01 remains `DEFERRED / PM DECISION PENDING`.
- FN-01 wording now states material/procurement known-cost auto-linkage is implemented.
- Remaining blockers are evidence, Docker gate, reversal/cancel synchronization BA decision, and work-type-specific closure evidence.

## Demo Seed Idempotency

Implemented in source and compiled locally; Docker/Postgres execution is still blocked locally.

Added seed assets:
- `src/main/resources/db/demo-seed/phase-5-p0-demo.sql`
- `src/main/java/com/toir/config/DemoP0LeadershipDemoSeeder.java`

Exact equipment codes now present in seed source:
- `AUTO-PUMP-A1`
- `AUTO-PUMP-A2`
- `AUTO-PUMP-A3-NOMETER`

Local contract test:
- `./mvnw -Dtest=DemoP0SeedContractTest test`
- Result: build success; tests run 2, failures 0, errors 0, skipped 0.

Docker-backed idempotency test:
- `./mvnw -Dtest=SeedProfileStartupSmokeTest test`
- Result locally: build success; tests run 4, failures 0, errors 0, skipped 4 because Docker/Testcontainers could not find a Docker environment.
- Added assertion method: `startupWithDevAndDemoSeedProfilesSeedsExactP0DemoAssetsIdempotently`.

Blockers:
- Docker is not installed locally.
- Clean Docker/Postgres seed execution is required to produce before/after counts.

Command evidence:
- `docker --version && docker compose version` -> `zsh:1: command not found: docker`

Required release-gate rerun on Docker/Postgres machine:
- Start a clean Postgres database.
- Run backend with `dev,demo-seed`.
- Capture counts before and after a second seed run for equipment, meters, templates, spare requirements, stock rows, costs, due events, and users by natural keys.
- Assert no duplicates for the three required equipment codes and related natural keys.

## Backend Release Gate

Command:
- `./mvnw -Dtest=ReservationServiceTest,WarehouseStockConstraintMigrationTest test`

Local result:
- Build success.
- Tests run: 8, failures: 0, errors: 0, skipped: 2.
- The two skipped tests are Docker-gated Testcontainers checks; they did not run locally because Docker is unavailable.

Command:
- `./mvnw -Dtest=DemoP0SeedContractTest test`

Local result:
- Build success.
- Tests run: 2, failures: 0, errors: 0, skipped: 0.

Command:
- `./mvnw -Dtest=SeedProfileStartupSmokeTest test`

Local result:
- Build success.
- Tests run: 4, failures: 0, errors: 0, skipped: 4.
- The seed idempotency assertion is Docker-gated and skipped locally because Docker is unavailable.

Command:
- `./mvnw test`

Local result:
- Build failure due environment.
- Tests run: 2166, failures: 0, errors: 48, skipped: 8.
- Error class: environment/database context failures, including `Connection to localhost:5433 refused` and unavailable Docker/Testcontainers.
- No assertion regression was reported in this local run.

Required release-gate rerun on Docker-enabled CI/machine:
- `./mvnw test`
- `./mvnw -Dtest=ReservationServiceTest,WarehouseStockConstraintMigrationTest test`
- Expected: Docker-gated SP-02 proof is not skipped, stock oversell tests pass, DB constraints apply cleanly.

## Frontend Verification

Commands:
- `yarn test`
- `yarn build`

Results:
- `yarn test`: passed, 67 test files / 486 tests.
- `yarn build`: passed. Vite emitted the existing large-chunk warning only.

Static route verification:
- Registered routes found in `src/app/routes.tsx`:
  - `/equipment`
  - `/equipment/:equipmentId`
  - `/maintenance/due-events`
  - `/work-orders`
  - `/work-orders/:workOrderId`
  - `/warehouses`
  - `/financial-review`
- `/budget-control` is present through the budget control route/module and links.
- Cockpit scan: `rg -n "cockpit|Cockpit|operational cockpit|Operational Cockpit|MT-01" src package.json` returned no frontend matches.

Runtime route verification:
- Blocked locally because the seeded Docker/Postgres backend required for the UAT scenario is not available.

## UI-Only Leadership Script

Script source:
- `toir-backend/docs/uat/leadership-demo-script.md`

Execution status:
- Not executed locally.

Blockers:
- No Docker/Postgres seeded demo database.
- Exact demo codes are now present in seed source, but not executed on a real seeded database locally.

Primary route remains:
- Equipment Registry -> Equipment Card -> Passport completeness -> Due Event explanation -> Approval/Create WO -> WO Detail -> Labor/Materials -> Closure readiness -> Completion/Close -> Equipment Card next cycle/history -> Finance/Budget source rows.

Negative branch remains:
- `AUTO-PUMP-A3-NOMETER` -> passport completeness missing critical -> Due Event BLOCKED -> fixLink -> no WO creation from blocked event.

## Screenshot Evidence

Screenshots were not captured in this local run because the seeded backend database could not be started. Exact demo records are present in seed source, but were not executed on a real seeded Docker/Postgres database locally.
Placeholder screenshot directory:
- `toir-backend/docs/uat/evidence/2026-06-06-p0-demo/`

Required screenshots for final UAT pack:
- Equipment Registry with `AUTO-PUMP-A1` passport completeness badge.
- Equipment Card passport completeness block.
- Due Event structured explanation.
- Generated WO tasks copied from template.
- Closure readiness before evidence complete.
- Closure readiness after evidence complete.
- Equipment Card history/next cycle after close.
- Finance/Budget actual cost row with technical source.
- `AUTO-PUMP-A3-NOMETER` blocked explanation and fix action.

## Role Walkthrough

Not executed locally.

Blockers:
- No seeded demo database.
- Exact demo equipment codes are present in seed source, but local Docker/Postgres execution is unavailable.

Roles still required for final pass/fail evidence:
- Engineer/PPR engineer.
- Approver/maintenance manager.
- Foreman.
- Storekeeper.
- Economist.
- VIEWER.

## Remaining Blockers

- Run seed idempotency twice on clean Docker/Postgres and record counts.
- Run full backend gate on Docker-enabled machine with Testcontainers checks not skipped.
- Execute UI-only leadership script against seeded data.
- Capture screenshot pack.
- Complete real-role walkthrough.
- Decide reversal/cancel synchronization policy for source-linked material/procurement costs.
- Finalize work-type-specific closure evidence rules for meter/material/result snapshots.
