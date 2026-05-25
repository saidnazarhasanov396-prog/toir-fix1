# Real-Case Demo Seed: Remove Visible DEMO Labels Result

Date: 2026-05-25
Project: toir-backend
Scope: PM-visible DB/UI values only (no production startup behavior change, no Flyway demo data migration)

## 1) What was changed

`DEMO` labels were removed/replaced from seeded visible values in demo SQL phases:

- `src/main/resources/db/demo-seed/phase-1-foundation.sql`
- `src/main/resources/db/demo-seed/phase-2-equipment-warehouse.sql`
- `src/main/resources/db/demo-seed/phase-3-maintenance-workflow.sql`
- `src/main/resources/db/demo-seed/phase-4-finance-docs-audit.sql`

New verification SQL added:

- `scripts/demo-data/98_assert_no_visible_demo_terms.sql`

Internal technical names were kept unchanged as requested:

- `demo-seed` profile name: unchanged
- `DemoRealCase...Seeder` class names: unchanged

## 2) Required replacements applied

Explicit mappings requested by PM were applied in seed values:

- `DEMO_MECH_WEAR` -> `NAV-MECH-WEAR`
- `DEMO-LUBRICATION` -> `NAV-LUBRICATION`
- `DEMO-MAJOR` -> `NAV-MAJOR`
- `WO-2026-DEMO-001` -> `WO-2026-001`
- `RR-2026-DEMO-001` -> `RR-2026-001`
- `DEF-2026-DEMO-001` -> `DEF-2026-001`
- `Production-like defect captured by demo seed` -> production-like wording without `DEMO`

Additional visible `DEMO/demo` seeded labels were also removed where they appeared in user-facing seeded values (titles, codes, notes, tags, references).

## 3) Verification run sequence (executed)

Environment used:

- DB: PostgreSQL container `toir_demo` on `localhost:5433`
- App: `mvn spring-boot:run` in Maven Docker container with profiles

Executed sequence:

1. `DROP SCHEMA public CASCADE; CREATE SCHEMA public;` -> PASS
2. `dev` profile run -> PASS
3. `dev,demo-seed` run -> PASS
4. `dev,demo-seed` run again (idempotency) -> PASS
5. `98_assert_no_visible_demo_terms.sql` -> PASS
6. `99_verify_demo_data.sql` -> PASS
7. Smoke endpoints -> PASS (with known `/employees` path note)

## 4) Idempotency check

Tracked table counts after run #1 and run #2 were identical:

- `audit_logs`: 32 -> 32
- `defects`: 30 -> 30
- `equipment`: 65 -> 65
- `ppr_tasks`: 75 -> 75
- `repair_requests`: 40 -> 40
- `stock_movements`: 67 -> 67
- `users`: 13 -> 13
- `warehouse_stocks`: 150 -> 150
- `work_orders`: 50 -> 50

Evidence files:

- `logs/demo-real-case-idempotency-counts-run1.txt`
- `logs/demo-real-case-idempotency-counts-run2.txt`

## 5) No-visible-DEMO assert result

`98_assert_no_visible_demo_terms.sql` result:

- `violating_columns = 0`
- `violating_rows = 0`

Evidence:

- `logs/demo-real-case-no-visible-demo-assert-output.txt`

## 6) Demo row-count verify snapshot

`99_verify_demo_data.sql` executed successfully.

- Major workflow tables are populated (for example: `work_orders=50`, `repair_requests=40`, `defects=30`, `ppr_tasks=75`, `stock_movements=67`, `warehouse_stocks=150`).
- Some finite/reference or currently deferred tables remain below 15 as expected by design.
- Technical exclusions remain:
  - `integration_sync_logs`
  - `webhook_event_log`

Evidence:

- `logs/demo-real-case-verify-output-after-demo-label-cleanup.txt`

## 7) Endpoint smoke test

Using admin auth token:

- `GET /api/v1/departments` -> 200
- `GET /api/v1/employees` -> 404
- `GET /api/v1/hr/employees` -> 200
- `GET /api/v1/equipment` -> 200
- `GET /api/v1/warehouses` -> 200
- `GET /api/v1/work-orders` -> 200
- `GET /api/v1/defects` -> 200
- `GET /api/v1/audit-log` -> 200
- `GET /api/v1/warehouses/reorder/stats` -> 200
- `GET /api/v1/ppr-plans` -> 200

Evidence:

- `logs/demo-real-case-smoke-endpoints-output.txt`

## 8) Important operational note for existing local DBs

If a local DB already contains older seeded rows with visible `DEMO` values, run clean reseed to avoid mixed old/new labels:

1. `DROP SCHEMA public CASCADE; CREATE SCHEMA public;`
2. Run app with `dev`
3. Run app with `dev,demo-seed`

This keeps idempotency guarantees clean and avoids legacy visible labels from previous seed generations.
