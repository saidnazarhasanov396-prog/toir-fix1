# Production-safe reset/seed strategy result

## Root cause
- `ExtendedDataSeeder` writes `BrigadeMember.qualifications` as `List<String>`.
- Entity mapping used `@Convert(StringListJsonConverter)` which binds SQL as `varchar`.
- DB column type is `jsonb`, so PostgreSQL rejected inserts with `varchar -> jsonb` mismatch.

## What was changed

### Seeder safety and profile isolation
- `SampleDataSeeder` changed from `@Profile("dev")` to `@Profile("demo-seed")`.
- `ExtendedDataSeeder` changed from `@Profile("dev")` to `@Profile("demo-seed")`.
- Removed `@ConditionalOnProperty(app.bootstrap.seed-demo-data=true)` from both demo seeders.
- `DataBootstrap` remains production-safe bootstrap (roles + optional admin only).
- Removed obsolete `app.bootstrap.seed-demo-data` keys from:
  - `src/main/resources/application-dev.yml`
  - `src/main/resources/application-prod.yml`

### JSONB fix
- `BrigadeMember.qualifications` mapping changed to:
  - `@JdbcTypeCode(SqlTypes.JSON)`
  - `@Column(name = "qualifications", columnDefinition = "jsonb")`
  - `List<String> qualifications`
- Removed legacy `@Convert(StringListJsonConverter.class)` from `BrigadeMember`.

### Production-safe additive Flyway repairs
- Added `V20260525_1__department_reference_cleanup_and_fk_hardening.sql`
  - Nullifies orphan nullable `department_id` links in `brigades` and `warehouses`.
  - Adds FK constraints if missing.
  - No truncate/drop/delete of business rows.
- Added `V20260525_2__brigade_member_qualifications_jsonb_compat.sql`
  - Normalizes `brigade_members.qualifications` to JSON array semantics.
  - Sets default to `'[]'::jsonb`.
  - Adds array-shape check constraint if missing.

## Tests and safety checks added
- `src/test/java/com/toir/model/BrigadeMemberMappingContractTest.java`
  - Verifies JSON mapping annotations and no legacy converter.
- `src/test/java/com/toir/repository/BrigadeMemberQualificationsPersistenceTest.java`
  - Persistence-level JSONB verification (Testcontainers-enabled).
- `src/test/java/com/toir/bootstrap/SeedProfileStartupSmokeTest.java`
  - Verifies startup behavior for `dev`, `dev,demo-seed`, and `prod` profiles (Testcontainers-enabled).

## Local/demo reset runbook updates
- Updated `README.md` with production-safe reset/seed section:
  - backup first
  - local/demo-only drop/create
  - run Flyway via normal startup
  - run demo seed manually with `dev,demo-seed`
  - smoke-test endpoints
  - explicit warning: no universal truncate/drop in production migrations

## Verification commands and results

### Duplicate migration version check
- Command (PowerShell equivalent of requested duplicate scan) returned **no duplicates**.

### Startup/Flyway verification on fresh empty PostgreSQL
Used dockerized PostgreSQL (`postgres:16-alpine`) + startup from the project:

1. `dev` profile:
   - Flyway migrated to latest (`v20260525.2`), app started.
   - Demo data counts:
     - `departments=0`
     - `brigades=0`
     - `brigade_members=0`

2. `dev,demo-seed` profiles:
   - Flyway migrated to latest (`v20260525.2`), app started.
   - Demo data counts:
     - `departments=7`
     - `brigades=2`
     - `brigade_members=1`
   - `jsonb_typeof(brigade_members.qualifications) = 'array'`.

3. `prod` profile:
   - Flyway migrated to latest (`v20260525.2`), app started.
   - Demo data counts:
     - `departments=0`
     - `brigades=0`
     - `brigade_members=0`

### Flyway history confirmation
- `20260525.1 | V20260525_1__department_reference_cleanup_and_fk_hardening.sql | success=true`
- `20260525.2 | V20260525_2__brigade_member_qualifications_jsonb_compat.sql | success=true`

### JAR migration packaging verification
- Verified from a fresh package build directory (`D:/Projects/toir-backend-buildtmp`) that JAR contains:
  - `BOOT-INF/classes/db/migration/V20260523_*`
  - `BOOT-INF/classes/db/migration/V20260525_*`

### `git diff --check`
- Executed successfully, no whitespace errors.
- Only CRLF conversion warnings were reported.

## Why ddl-auto=create/update was not used
- Kept `spring.jpa.hibernate.ddl-auto=validate`.
- Flyway remains single source of truth for schema evolution.
- All repairs are additive migrations and profile-safe seeding controls.

## Production-safe cleanup rules enforced
- No destructive truncate/drop/delete migrations added for production data.
- Orphan/reference cleanup is explicit, scoped, and additive.
- FK hardening is conditional (`IF NOT EXISTS`) and preceded by safe cleanup.
- JSONB compatibility repair is idempotent and constraint-backed.

## Remaining risks / notes
- `target/toir-backend-1.0.0.jar` is locked by an external process on this machine, so in-place `mvn package` in the main workspace could not repackage that exact path during this run.
- Packaging validation was completed in a clean copied workspace (`D:/Projects/toir-backend-buildtmp`) and confirmed expected `BOOT-INF` migration contents.
- Existing previously-untracked files from earlier Flyway work were left untouched.

## Confirmation
- `flyway repair` was **not** used.
