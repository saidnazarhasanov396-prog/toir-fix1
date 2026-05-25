# Demo Real-Case Seed Implementation Result

Date: 2026-05-25
Project: toir-backend
Profiles: dev,demo-seed only

## 1) Implemented structure

Implemented phase-based, SQL-driven demo seed orchestration:

1. `DemoRealCaseFoundationSeeder` (`@Order(11)`) -> `db/demo-seed/phase-1-foundation.sql`
2. `DemoRealCaseEquipmentWarehouseSeeder` (`@Order(12)`) -> `db/demo-seed/phase-2-equipment-warehouse.sql`
3. `DemoRealCaseMaintenanceWorkflowSeeder` (`@Order(13)`) -> `db/demo-seed/phase-3-maintenance-workflow.sql`
4. `DemoRealCaseFinanceDocsAuditSeeder` (`@Order(14)`) -> `db/demo-seed/phase-4-finance-docs-audit.sql`

Execution utility:
- `DemoSeedSqlExecutor` uses `ResourceDatabasePopulator` and classpath SQL scripts.

## 2) Production safety and profile gating

- All new phase seeders are gated by `@Profile("dev & demo-seed")`.
- Legacy demo seeders were hardened to the same gate:
  - `SampleDataSeeder`: `@Profile("dev & demo-seed")`
  - `ExtendedDataSeeder`: `@Profile("dev & demo-seed")`
- Flyway remains schema source of truth. No demo data Flyway migration was added.
- No `ddl-auto=create/update` introduced.

## 3) Changed files

- `src/main/java/com/toir/config/DemoSeedSqlExecutor.java`
- `src/main/java/com/toir/config/DemoRealCaseFoundationSeeder.java`
- `src/main/java/com/toir/config/DemoRealCaseEquipmentWarehouseSeeder.java`
- `src/main/java/com/toir/config/DemoRealCaseMaintenanceWorkflowSeeder.java`
- `src/main/java/com/toir/config/DemoRealCaseFinanceDocsAuditSeeder.java`
- `src/main/java/com/toir/config/SampleDataSeeder.java`
- `src/main/java/com/toir/config/ExtendedDataSeeder.java`
- `src/main/resources/db/demo-seed/phase-1-foundation.sql`
- `src/main/resources/db/demo-seed/phase-2-equipment-warehouse.sql`
- `src/main/resources/db/demo-seed/phase-3-maintenance-workflow.sql`
- `src/main/resources/db/demo-seed/phase-4-finance-docs-audit.sql`

## 4) Runtime verification executed

Because local `mvn`/`psql` are unavailable in host shell, verification was executed via Docker containers.

Commands used:

```bash
# compile check
mvn -q -DskipTests compile

# seed run (dev,demo-seed)
mvn -q spring-boot:run -Dspring-boot.run.profiles=dev,demo-seed

# verify SQL
psql -f scripts/demo-data/99_verify_demo_data.sql
```

Equivalent Docker-based commands were executed against `toir_demo` on `localhost:5433`.

Results:
- Compile: PASS (Docker Maven)
- Flyway migrate + startup + all 4 phase scripts: PASS (see app logs where each phase reports `started`/`finished`)
- No FK errors on final successful run
- No duplicate key errors on final successful run
- No JSONB seed errors on final successful run
- Re-run idempotency spot-check: PASS (selected table counts unchanged after repeated `dev,demo-seed` startup)

Idempotency spot-check counts (before and after rerun):
- `equipment`: 65 -> 65
- `work_orders`: 50 -> 50
- `ppr_tasks`: 75 -> 75
- `stock_movements`: 67 -> 67
- `uploaded_files`: 30 -> 30

## 5) Endpoint smoke test

Authenticated smoke checks were executed with admin JWT:
- `GET /api/v1/departments` -> 200
- `GET /api/v1/equipment` -> 200
- `GET /api/v1/warehouses` -> 200
- `GET /api/v1/work-orders` -> 200
- `GET /api/v1/defects` -> 200
- `GET /api/v1/audit-log` -> 200
- `GET /api/v1/warehouses/reorder/stats` -> 200
- `GET /api/v1/ppr-plans` -> 200
- Requested path `GET /api/v1/employees` -> 404
- Actual employee endpoint `GET /api/v1/hr/employees` -> 200

## 6) Demo credentials

Verified credential:
- username: `admin`
- password: `Root123456`

## 7) Known operational notes

- `S3ServiceImpl` logs MinIO connection warning in local run (`localhost:9000`) but does not block seed completion.
- Some tables are intentionally below 15 rows due finite/reference nature or scope; full table-by-table result is documented in:
  - `logs/demo-real-case-seed-row-count-result.md`
  - raw verify output: `logs/demo-real-case-verify-output.txt`

## 8) Remaining risks

- Several low-cardinality reference and planning tables remain <15 rows by business nature.
- KPI/timesheet tables are currently not seeded (`equipment_kpis`, `maintenance_kpis`, `hr_timesheet_entries`) and show 0 in verification.
- If PM requires strict 15+ for those finite or deferred domains, additional targeted scenario expansion is needed.
