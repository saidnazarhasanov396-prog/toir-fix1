# Flyway Empty DB Failure Audit

Date: 2026-05-25
Workspace: `D:\Projects\toir-backend`

## 1) Flyway setup audit

Checked files:
- `src/main/resources/application.yml`
- `src/main/resources/application-dev.yml`
- `src/main/resources/application-prod.yml`

Findings:
- `spring.flyway.enabled: true` (global)
- `spring.flyway.validate-on-migrate: true` (global)
- `spring.jpa.hibernate.ddl-auto: validate` (dev/prod)
- Flyway location: `classpath:db/migration`
- Migrations folder in use: `src/main/resources/db/migration`
- No vendor subfolder configuration (`{vendor}`) is configured.

## 2) Empty DB reproduction procedure

Disposable PostgreSQL container:
- Image: `postgres:16-alpine`
- Port mapping: `5433:5432`
- DB: `toir_demo`
- User: `postgres`

Startup command used:

```powershell
java -jar target/toir-backend-1.0.0.jar \
  --spring.profiles.active=dev \
  --spring.main.web-application-type=none \
  --server.port=0
```

Log file:
- `logs/flyway-empty-db-run.log`

## 3) First failing migration (captured)

First failure:
- Migration file: `V20260427_1__fleet_vehicle_foundation.sql`
- SQL State: `42P01`
- Error: `ERROR: relation "equipment" does not exist`
- Flyway location: `db/migration/V20260427_1__fleet_vehicle_foundation.sql`

Exact root failing context from log:
- Flyway successfully validated and started from empty schema.
- It executed:
  - `V20260424_1`
  - `V20260425_1`
  - `V20260425_2`
- Then failed on `V20260427_1` at first statement:
  - `ALTER TABLE equipment ...`

## 4) Why this migration expected the missing object

`V20260427_1__fleet_vehicle_foundation.sql` is an incremental migration that alters pre-existing core tables (`equipment`) and adds `vehicle_details`. It assumes `equipment` already exists from an earlier baseline/foundation schema, but no earlier `V` migration in this repository creates the legacy core schema.

Consequence:
- Fresh empty DB cannot proceed through historical incremental `V` chain without a cumulative baseline/foundation state.
