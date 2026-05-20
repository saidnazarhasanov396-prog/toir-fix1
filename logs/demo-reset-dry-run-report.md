# Demo Reset Phase B2 Dry Run Report

Date: 2026-05-20
Workspace: D:\Projects\toir-backend

## B3.1 Expanded Seed Status

- Expanded seed draft prepared on 2026-05-20.
- Dry-run was not executed in the current local shell because `toir_demo` was not reachable on `localhost:5433`, `127.0.0.1:5433`, or `host.docker.internal:5433`, and no `DATABASE_URL` / `LOCAL_DEMO_URL` was set.
- See `logs/demo-large-seed-expansion-report.md`.
- The PASS evidence below remains valid for the previous compact seed only.

## B2 Follow-Up: ON CONFLICT Fix (toir_demo)

- Follow-up date: 2026-05-20
- Dry-run context from user: restored local `toir_demo`, preflight passed, delete passed, seed failed at `src/main/resources/db/manual/2026-05-20_demo_seed_draft.sql:135`.

### Root cause

- `equipment` insert used `ON CONFLICT (code) DO NOTHING`.
- In restored `toir_demo`, `equipment.code` is enforced by a **partial** unique index:
  - `idx_equipment_code_active_unique ON equipment(code) WHERE is_deleted = false`
- PostgreSQL could not infer this partial unique index from `ON CONFLICT (code)` without matching predicate, causing:
  - `ERROR: there is no unique or exclusion constraint matching the ON CONFLICT specification`

### Fixed ON CONFLICT clauses

- Updated only manual seed draft:
  - File: `src/main/resources/db/manual/2026-05-20_demo_seed_draft.sql`
  - Line 135:
    - Before: `ON CONFLICT (code) DO NOTHING;`
    - After: `ON CONFLICT (id) DO NOTHING;`
- Rationale: seed rows use fixed deterministic UUIDs, and `equipment.id` has a non-partial primary key constraint (`equipment_pkey`), keeping idempotency without schema changes.

### ON CONFLICT target verification

- `grep` inventory executed for all seed `ON CONFLICT` clauses.
- Catalog validation executed against restored `toir_demo`:
  - Output: `logs/demo_seed_on_conflict_target_check.txt`
  - Result: all current conflict targets resolve to valid non-partial unique/PK indexes (`target_valid = true` for all 40 entries).

### Rerun instructions (after restoring snapshot again)

Use the same non-production target only:

```bash
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f src/main/resources/db/manual/2026-05-20_demo_reset_preflight.sql > logs/demo_reset_preflight_output.txt
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f src/main/resources/db/manual/2026-05-20_demo_reset_delete_draft.sql > logs/demo_reset_delete_output.txt
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f src/main/resources/db/manual/2026-05-20_demo_seed_draft.sql > logs/demo_seed_output.txt
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f src/main/resources/db/manual/2026-05-20_demo_reset_validation.sql > logs/demo_reset_validation_output.txt
```

For this local containerized environment (example), `DATABASE_URL` can be:

```bash
postgresql://postgres:root123@host.docker.internal:5433/toir_demo
```

## 1. DB target

- Initial candidate `postgresql://toir:toir@127.0.0.1:5432/toir` (local compose postgres) was rejected for dry run because it had no `flyway_schema_history` and no schema.
- Executed target:
  - `postgresql://postgres:root123@host.docker.internal:5433/toir`
  - Confirm query output (`logs/demo_reset_target_check_output.txt`):

```text
 current_database | current_user | inet_server_addr | inet_server_port 
------------------+--------------+------------------+------------------
 toir             | postgres     | 172.17.0.2       |             5432
(1 row)
```

- Target classification: local non-production Dockerized DB (safe from live production), but not a prepared production snapshot/demo dataset.

## 2. Preflight result

- Command intent executed using containerized `psql` against the target URL.
- Output file: `logs/demo_reset_preflight_output.txt`
- Result: **FAILED**
- Failure:

```text
ERROR:  relation "roles" does not exist
LINE 2: FROM roles
```

- What did run before failure:
  - DB identity check query succeeded.
  - Flyway history query succeeded (14 migration entries listed).
- Preflight review checklist status:
  - `SYSTEM_ADMIN` exists: **Not verifiable (roles table missing)**
  - `"*"` wildcard exists: **Not verifiable (roles table missing)**
  - Admin users exist: **Not verifiable (users/roles checks not reached)**
  - Roles exist: **Failed (relation missing)**
  - Integration/webhook endpoints known: **Not reached**
  - Row counts expected: **Not reached**

## 3. Delete result

- Output file: `logs/demo_reset_delete_output.txt`
- Exit code: `3`
- Result: **FAILED immediately**

```text
ERROR:  relation "notifications" does not exist
LINE 1: DELETE FROM notifications;
```

- Impact: no meaningful delete run was possible; script stopped at first missing table.

## 4. Seed result

- Output file: `logs/demo_seed_output.txt`
- Exit code: `3`
- Result: **FAILED immediately**

```text
ERROR:  relation "departments" does not exist
LINE 1: INSERT INTO departments ...
```

## 5. Validation result

- Output file: `logs/demo_reset_validation_output.txt`
- Exit code: `3`
- Result: **FAILED**

```text
ERROR:  relation "roles" does not exist
LINE 4: FROM roles
```

## 6. Errors if any

1. Local shell had no `DATABASE_URL` and no host `psql`; execution required containerized `psql`.
2. Confirmed local DB target lacked business schema objects (`roles`, `users`, `departments`, etc.).
3. Backend startup via requested command was not possible directly because `mvn` is not installed in this environment.
4. Fallback startup check via Docker Compose failed because no built artifact exists:

```text
COPY target/*.jar app.jar
ERROR: lstat /target: no such file or directory
```

- Startup log: `logs/demo_backend_startup_output.txt`

## 7. Whether scripts are ready for approved production/demo execution

**NO. Not ready.**

This dry run did not reach data reset/seed/validation logic because the target database did not contain required schema tables.

## 8. Required fixes before real execution

1. Provide/restore a **real production snapshot, staging, or prepared demo DB** that contains the full current TOIR schema and baseline data.
2. Re-run preflight and ensure it passes all mandatory checks (`SYSTEM_ADMIN`, wildcard permission, admin users, roles, endpoints, row counts).
3. Ensure execution environment has direct `psql` (or standardize documented containerized `psql` command wrappers).
4. Ensure backend startup path is runnable (`mvn`/wrapper installed, or prebuilt `target/*.jar` for Docker image build).
5. Re-run full sequence only after items 1-4 are complete.

## Additional smoke-check status

- Application login smoke checks could not be executed because backend did not start.
- Table existence probe for smoke domains (`roles`, `users`, `departments`, `equipment`, `warehouse`, `ppr`, `repair`, `work_order`, `defect`, `procurement`, `finance`, `approval`, `inspection`, `knowledge`) returned **all false** on this target.
- Probe output: `logs/demo_smoke_table_existence.txt`
