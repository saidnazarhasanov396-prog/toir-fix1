# Flyway Empty DB Repair Result

Date: 2026-05-25
Workspace: `D:\Projects\toir-backend`

## Root Cause

Historical `V` migrations in this repository are incremental and assume a pre-existing legacy core schema (for example `equipment`, `work_orders`, `repair_requests`, `defects`, etc.).

On a fresh empty PostgreSQL database, Flyway reached `V20260427_1__fleet_vehicle_foundation.sql` and failed immediately because `equipment` did not exist.

## First Failing Migration

- File: `src/main/resources/db/migration/V20260427_1__fleet_vehicle_foundation.sql`
- Error: `ERROR: relation "equipment" does not exist` (`SQLSTATE 42P01`)
- Evidence log: `logs/flyway-empty-db-run.log`

## What Was Changed

Added cumulative Flyway baseline migration:
- `src/main/resources/db/migration/B20260523_7__schema_baseline.sql`

Added migration smoke test:
- `src/test/java/com/toir/migration/FlywayEmptyDbSmokeTest.java`

Added missing test support dependency:
- `pom.xml` -> `org.testcontainers:junit-jupiter` (test scope)

Audit reports created:
- `logs/flyway-empty-db-failure-audit.md`
- `logs/flyway-empty-db-repair-result.md`

## Why This Fix Is Safe

- Existing `V*.sql` migration files were **not deleted**.
- Existing `V*.sql` migration files were **not renamed**.
- Existing applied `V*.sql` contents were **not edited**.
- `ddl-auto` was **not** switched to `create`/`update` in app config.
- Flyway remains enabled and validation remains enabled.

Using `B20260523_7__schema_baseline.sql` allows fresh environments to start from a cumulative schema state, while existing environments with applied Flyway history continue using their recorded history and ignore baseline migration files.

## Verification Commands

### 1) Build fresh JAR

```powershell
mvn -DskipTests clean package
```

Result: **PASS**

### 2) Verify packaged migrations in JAR

```powershell
jar tf target/toir-backend-1.0.0.jar | findstr /i "db/migration/B20260523_7 db/migration/V20260523"
```

Result: **PASS** (baseline + V20260523 scripts present)

### 3) Fresh empty DB startup verification

- Reset `toir_demo` DB in disposable PostgreSQL container.
- Start backend with dev profile and `ddl-auto=validate`.

Observed result:
- Flyway validated **27 migrations**.
- Flyway applied `B20260523_7__schema_baseline.sql` as `SQL_BASELINE`.
- Hibernate validation passed.
- App reached `Started ToirApplication`.

Evidence logs:
- `logs/flyway-empty-db-post-fix-run.stdout.log`
- `logs/flyway-empty-db-post-fix-run.stderr.log`

Schema history after migrate:

```text
installed_rank=1, version=20260523.7, script=B20260523_7__schema_baseline.sql, type=SQL_BASELINE, success=true
```

### 4) Duplicate version check

PowerShell equivalent check over `V*.sql` versions.

Result: `NO_DUPLICATE_VERSIONS`

### 5) Testcontainers smoke test

```powershell
mvn -Dtest=FlywayEmptyDbSmokeTest test
```

Result: **PASS with SKIP** in this environment (`disabledWithoutDocker=true`, Docker not usable from Java Testcontainers provider).

### 6) Diff whitespace check

```powershell
git diff --check
```

Result: **No whitespace errors** (only line-ending warning for `pom.xml`).

## Demo Reset Readiness (Manual Runbook)

1. Backup DB:

```powershell
pg_dump -h <host> -p <port> -U <user> -d <db> -Fc -f <backup_file.dump>
```

2. Drop and recreate DB:

```sql
SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '<db>';
DROP DATABASE IF EXISTS <db>;
CREATE DATABASE <db>;
```

3. Start backend with Flyway enabled (`validate-on-migrate=true`, `ddl-auto=validate`).

4. Verify Flyway history:

```sql
SELECT installed_rank, version, description, script, type, success
FROM flyway_schema_history
ORDER BY installed_rank;
```

5. Verify schema health (sample):

```sql
SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='public';
```

6. Run real-case seed data as a separate controlled step (not auto-enabled in prod startup).

## Why `ddl-auto=create/update` Was Not Used

`ddl-auto=create/update` was not adopted as runtime solution because it breaks deterministic schema versioning and team-safe migration history. Flyway remains the source of truth; Hibernate stays on `validate`.

## Remaining Risks

1. Baseline script is cumulative and large; future entity-to-schema drift should continue to be managed via new `V` migrations.
2. Testcontainers execution depends on local Java Docker provider configuration; CI should run this test where Docker is available.
3. Existing shared DBs should be validated once in staging to confirm baseline migration is ignored as expected with existing Flyway history.

## Explicit confirmation

- `flyway repair` was **not** used.
