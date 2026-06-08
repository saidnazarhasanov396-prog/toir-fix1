# P0 Demo Evidence Run - 2026-06-06

Scope: P0 UAT/demo-chain evidence for the current branch without MT-01 Operational Cockpit.

MT-01 status: DEFERRED / PM DECISION PENDING. No Cockpit page, route, endpoint integration, or merged operational queue was added or used.

Security/runtime scope: locked for this branch. Security config, runtime secrets, JWT/auth filters, `JwtService`, `SecurityConfig`, `.env`, `.env.example`, `application-prod.yml` security/env values, and docker security/env values were not changed during this evidence pass. TeamLead/DevOps owns final security/runtime configuration.

## Environment

- Machine timezone: Asia/Tashkent.
- Backend branch: `p0-hardening-demo-chain-qa01`.
- Backend commit at local verification: `d034ed26ba68e874da07de5dfff7f23454571f18`.
- Docker status: unavailable locally. `docker --version` returned `zsh:1: command not found: docker`.
- Local PostgreSQL/Testcontainers gate: blocked locally because Docker is unavailable and test PostgreSQL on `localhost:5433` is not accepting connections.
- Reported external release gate status from the handoff brief: Docker/Postgres-enabled backend regression passed with 2180 tests, 0 failures, 0 errors, 11 skipped. This local evidence run could not independently reproduce that Docker-enabled result because Docker is not installed here.

## Clean State Check

Backend before verification:
- `git fetch origin` completed.
- `git status -sb`: branch `p0-hardening-demo-chain-qa01...origin/p0-hardening-demo-chain-qa01`, clean.
- `git diff --name-only`: empty.

Frontend before verification:
- `git fetch origin` completed.
- Branch: `p0-hardening-demo-chain-qa01...origin/p0-hardening-demo-chain-qa01`.
- Existing unrelated dirty files remained excluded from this evidence pack:
  - `.yarn/install-state.gz`
  - `server-test-maintenance-templates-actions.png`
  - `server-test-maintenance-templates-rows.png`
  - `server-test-maintenance-templates.png`
  - `server-test-operational-issues-filter.png`
  - `server-test-required-spare-parts-section.png`
  - `server-test-required-spare-parts.png`

## Backend Verification

Command:
- `./mvnw -Dtest=DemoP0SeedContractTest test`

Result:
- Build success.
- Tests run: 2, failures: 0, errors: 0, skipped: 0.

Command:
- `./mvnw -Dtest=SeedProfileStartupSmokeTest test`

Result:
- Build success.
- Tests run: 4, failures: 0, errors: 0, skipped: 4.
- Skipped because Testcontainers could not find Docker (`/var/run/docker.sock` missing).

Command:
- `./mvnw -Dtest=ReservationServiceTest,WarehouseStockConstraintMigrationTest test`

Result:
- Build success.
- Tests run: 8, failures: 0, errors: 0, skipped: 2.
- `ReservationServiceTest`: 6 tests passed.
- `WarehouseStockConstraintMigrationTest`: 2 Docker-gated tests skipped locally because Docker is unavailable.

Command:
- `./mvnw test`

Local result:
- Build failure due local environment.
- Tests run: 2178, failures: 0, errors: 48, skipped: 11.
- Error class: Spring `@DataJpaTest`/repository context failures caused by `Connection to localhost:5433 refused`, plus Docker/Testcontainers skips because Docker is unavailable.
- This local run did not show assertion failures.

Docker-gated acceptance not reproduced locally:
- Seed idempotency test was skipped locally.
- SP-02 Docker-gated constraint/concurrency proof was skipped locally.
- Full-stack startup was not possible locally because Docker is unavailable.

## Frontend Verification

Command:
- `yarn test`

Result:
- Passed.
- Test files: 67 passed.
- Tests: 486 passed.

Command:
- `yarn build`

Result:
- Passed.
- Vite emitted the known large-chunk warning only.

MT-01/Cockpit:
- No Cockpit/MT-01 implementation was added or used in this evidence pass.

## Full Stack Startup

Status: blocked locally.

Reason:
- Docker CLI is unavailable (`zsh:1: command not found: docker`).
- Seeded Docker/Postgres demo database could not be started on this machine.

Not captured locally:
- backend URL
- frontend URL
- active backend profile
- database used
- seed profile runtime status
- health endpoint result
- login verification
- UI visibility of `AUTO-PUMP-A1`, `AUTO-PUMP-A2`, `AUTO-PUMP-A3-NOMETER`

## Seed Idempotency Evidence

Static seed source is present and contract-tested locally, but clean Docker/Postgres seed execution could not be run here.

Exact demo equipment codes present in seed source:
- `AUTO-PUMP-A1`
- `AUTO-PUMP-A2`
- `AUTO-PUMP-A3-NOMETER`

Seed idempotency count table:

| Entity | After first seed | After second seed | Duplicate check |
| --- | ---: | ---: | --- |
| equipment | Not captured locally | Not captured locally | Blocked: Docker/Postgres unavailable |
| meters | Not captured locally | Not captured locally | Blocked: Docker/Postgres unavailable |
| meter readings | Not captured locally | Not captured locally | Blocked: Docker/Postgres unavailable |
| due events | Not captured locally | Not captured locally | Blocked: Docker/Postgres unavailable |
| maintenance regulations | Not captured locally | Not captured locally | Blocked: Docker/Postgres unavailable |
| templates | Not captured locally | Not captured locally | Blocked: Docker/Postgres unavailable |
| template operations | Not captured locally | Not captured locally | Blocked: Docker/Postgres unavailable |
| spare/material requirements | Not captured locally | Not captured locally | Blocked: Docker/Postgres unavailable |
| warehouse stock rows | Not captured locally | Not captured locally | Blocked: Docker/Postgres unavailable |
| budget lines | Not captured locally | Not captured locally | Blocked: Docker/Postgres unavailable |
| actual costs | Not captured locally | Not captured locally | Blocked: Docker/Postgres unavailable |
| users/roles | Not captured locally | Not captured locally | Blocked: Docker/Postgres unavailable |

Acceptance status:
- No duplicate proof could be captured locally.
- A Docker/Postgres-enabled machine must run the seed twice and record counts by natural key.

## UI-Only Leadership Script

Script source:
- `toir-backend/docs/uat/leadership-demo-script.md`

Execution status:
- Not executed locally.

Reason:
- No seeded Docker/Postgres demo database is available on this machine.

Primary route still required:
- Equipment Registry -> Equipment Card -> Passport completeness -> Due Event explanation -> Approval/Create WO -> WO Detail -> Labor/Materials -> Closure readiness -> Completion/Close -> Equipment Card next cycle/history -> Finance/Budget source rows.

Negative branch still required:
- `AUTO-PUMP-A3-NOMETER` -> Equipment Card -> missing critical setup -> Due Event `BLOCKED` -> blockingCode/blockingField/fixLink -> Create WO prevented or same backend blocked reason -> no WO created from blocked event.

## Screenshot Evidence

Screenshot directory:
- `toir-backend/docs/uat/evidence/2026-06-06-p0-demo/screenshots/`

Screenshots captured locally:
- None. Runtime UI evidence is blocked locally because the seeded backend/database could not be started.

Required screenshot files still pending:
- `01-equipment-registry-auto-pump-a1-passport-badge.png`
- `02-equipment-card-passport-completeness.png`
- `03-due-event-structured-explanation.png`
- `04-generated-wo-template-tasks.png`
- `05-closure-readiness-before-evidence.png`
- `06-closure-readiness-after-evidence.png`
- `07-equipment-card-history-next-cycle-after-close.png`
- `08-finance-budget-source-row.png`
- `09-auto-pump-a3-blocked-fix-action.png`

## Route Verification

Static/frontend tests and build passed.

Runtime route verification status:
- Blocked locally due no Docker/Postgres seeded backend.

Routes still requiring browser evidence:
- `/equipment`
- `/equipment/:id`
- `/maintenance/due-events`
- `/work-orders/:id`
- `/warehouses`
- `/budget-control`
- `/financial-review`

## Role Walkthrough

Status: not executed locally.

Reason:
- No seeded demo database/runtime stack is available on this machine.

Role pass/fail table:

| Role | Result | Notes |
| --- | --- | --- |
| Engineer/PPR engineer | Not executed | Requires seeded UI runtime |
| Approver/maintenance manager | Not executed | Requires seeded UI runtime |
| Foreman | Not executed | Requires seeded UI runtime |
| Storekeeper | Not executed | Requires seeded UI runtime |
| Economist | Not executed | Requires seeded UI runtime |
| VIEWER | Not executed | Requires seeded UI runtime |

Acceptance still pending:
- VIEWER has no mutation actions.
- Storekeeper can reserve/issue but cannot approve finance.
- Economist can view/review finance but cannot close technical WO.
- Engineer/Foreman can perform technical actions within scope.
- Forbidden backend actions return consistent human-readable errors.

## Remaining Blockers

- Run seed idempotency twice on a clean Docker/Postgres database and record natural-key counts.
- Run full backend gate on a Docker/Postgres-enabled machine with Docker-gated tests not skipped.
- Start full stack and record backend URL, frontend URL, active profile, database, seed profile status, and health endpoint result.
- Execute the UI-only leadership script without SQL/Postman/manual DB edits/direct API calls.
- Capture the required screenshot pack.
- Complete real-role walkthrough.
- Keep FN-01 at `PARTIAL++` until reversal/cancel synchronization is decided/implemented.
- Keep work-type-specific closure evidence for meter/material/result snapshots open until explicitly implemented and evidenced.
- Keep RB-02 final security hardening TeamLead/DevOps-owned.

## Explicit Scope Notes

- No finance rows were deleted.
- Security config was not touched.
- MT-01 Operational Cockpit was not implemented or used.
