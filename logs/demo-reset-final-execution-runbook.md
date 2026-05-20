# Demo Reset Final Execution Runbook

Date: 2026-05-20
Branch: `behzod`
Status: Final runbook for approval review only. Do not execute until GO criteria are met.

## 1. Pre-Execution Checklist

Do not proceed unless every item is checked by the named owner.

| Item | Owner | Status |
|---|---|---|
| PM approval received | PM | REQUIRED |
| Tech lead approval received | Tech lead | REQUIRED |
| DBA/Ops approval received | DBA/Ops | REQUIRED |
| Security approval received | Security | REQUIRED |
| Integration owner approval received | Integration owner | REQUIRED |
| Maintenance window approved and active | DBA/Ops + PM | REQUIRED |
| Production/demo target DB name and host confirmed | DBA/Ops | REQUIRED |
| Fresh backup completed | DBA/Ops | REQUIRED |
| Fresh backup restore test passed | DBA/Ops | REQUIRED |
| Admin credentials confirmed | Security + DBA/Ops | REQUIRED |
| Integrations/webhooks disabled or isolated | Integration owner | REQUIRED |
| Application writers/background jobs stopped or maintenance mode enabled | DBA/Ops | REQUIRED |
| Reviewed SQL files match approved branch `behzod` | Tech lead | REQUIRED |

## 2. Target DB Confirmation

Run preflight first and review target identity before any reset script. Use the approved target URL only.

```bash
export DATABASE_URL="<APPROVED_TARGET_DATABASE_URL>"
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 \
  -f src/main/resources/db/manual/2026-05-20_demo_reset_preflight.sql \
  > logs/prod_demo_reset_preflight_YYYYMMDD_HHMM.txt
```

Required review points from preflight output:

- `current_database()` is the approved target.
- `current_user` is the approved execution user.
- server address/port match the approved target.
- latest Flyway migrations are present.
- `SYSTEM_ADMIN` exists and has wildcard permission `*`.
- admin users and admin role mappings exist.
- active integrations/webhooks are understood and isolated.

Stop immediately if target identity is unclear.

## 3. Backup Requirement

Backup must be taken immediately before real execution. DBA/Ops owns the exact command.

```bash
# Placeholder only. DBA/Ops must replace with the approved backup command.
pg_dump "<APPROVED_TARGET_DATABASE_URL>" \
  --format=custom \
  --file="<APPROVED_BACKUP_PATH>/toir_before_demo_reset_YYYYMMDD_HHMM.dump"
```

Record:

- backup file path;
- backup start/end time;
- backup size/checksum;
- operator;
- target DB identity.

## 4. Restore-Test Requirement

Before real execution, restore the fresh backup to a separate database and run:

1. preflight;
2. delete draft;
3. seed draft;
4. validation;
5. backend startup;
6. smoke tests.

Do not proceed to the real target unless the fresh-backup restore test passes.

## 5. Maintenance Mode Steps

1. Announce maintenance start.
2. Stop or pause application write traffic.
3. Stop scheduled/background workers that may write domain data.
4. Confirm no active operators are using the system.
5. Keep read-only monitoring available if DBA/Ops requires it.
6. Record maintenance start timestamp.

## 6. Integration/Webhook Isolation

Before delete/seed:

1. Disable outbound integration workers.
2. Disable or isolate webhook delivery.
3. Confirm active `integration_endpoints` from preflight are safe.
4. Confirm active `webhook_subscriptions` from preflight are safe.
5. Keep external systems from receiving demo reset events.

Do not proceed if integration owner cannot confirm isolation.

## 7. Exact Execution Order

These commands must be run only after approvals, maintenance mode, fresh backup, restore test, target confirmation, and integration isolation.

### 7.1 Preflight

```bash
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 \
  -f src/main/resources/db/manual/2026-05-20_demo_reset_preflight.sql \
  > logs/prod_demo_reset_preflight_YYYYMMDD_HHMM.txt
```

### 7.2 Review Gate

Human review required before continuing:

- DBA/Ops confirms target DB.
- Security confirms admin/role preservation.
- Integration owner confirms webhooks/integrations isolated.
- Tech lead confirms script versions.

### 7.3 Delete

```bash
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 \
  -f src/main/resources/db/manual/2026-05-20_demo_reset_delete_draft.sql \
  > logs/prod_demo_reset_delete_YYYYMMDD_HHMM.txt
```

### 7.4 Seed

```bash
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 \
  -f src/main/resources/db/manual/2026-05-20_demo_seed_draft.sql \
  > logs/prod_demo_seed_YYYYMMDD_HHMM.txt
```

### 7.5 Validation

```bash
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 \
  -f src/main/resources/db/manual/2026-05-20_demo_reset_validation.sql \
  > logs/prod_demo_reset_validation_YYYYMMDD_HHMM.txt
```

Required validation result:

- `SYSTEM_ADMIN` wildcard status is `OK`.
- admin user status is `OK`.
- demo row counts are present.
- core relation orphan counts are all `0`.
- smoke data exists for work orders, PPR, warehouse stock, procurement, finance, approvals, inspections, and knowledge.

### 7.6 App Startup

Start backend using the approved production/demo deployment procedure. Confirm:

- application starts successfully;
- DB validation passes;
- no startup migration errors;
- no repeated integration/webhook delivery errors.

### 7.7 Smoke Tests

Run the post-execution smoke checklist in Section 8.

## 8. Post-Execution Smoke Checklist

| Area | Expected result |
|---|---|
| Admin login | Existing admin can log in and still has `SYSTEM_ADMIN` with wildcard `*`. |
| Demo users login | Approved demo users can log in with approved credentials. |
| Equipment | Demo equipment appears by department, location, and type. |
| Warehouse | Demo warehouses and stock rows appear, including low-stock seal example. |
| PPR | Demo PPR plan/tasks appear; task status data is visible. |
| Repair request | Demo repair requests are linked to equipment and department. |
| Work order | Demo work orders appear, including repair and PPR-linked examples. |
| Defects | Demo defect and defect list appear with linked equipment/work order. |
| Procurement | Demo procurement request and line appear. |
| Finance | Demo budget, budget line, and actual cost appear. |
| Approval | Demo approval request has expected pending steps. |
| Inspection | Demo route, checkpoints, completed round, and results appear. |
| Knowledge | Demo knowledge articles render with tags and linked records. |

## 9. Rollback

Rollback method: restore backup only.

Do not attempt reverse SQL rollback.

Rollback triggers:

- target DB identity mismatch discovered after execution starts;
- validation fails and cannot be explained safely;
- admin access is broken;
- backend cannot start;
- critical smoke tests fail;
- integrations/webhooks unexpectedly fire and cannot be isolated.

Rollback steps:

1. Keep system in maintenance mode.
2. Stop application writers.
3. DBA/Ops restores the pre-reset backup.
4. Run target identity and admin/role validation.
5. Start backend.
6. Run minimum smoke tests.
7. Record rollback incident notes.

## 10. Final Recommendation

Ready for approval review.

Not ready for real execution until all approvals are complete and the fresh backup/restore test passes. The execution operator must reconfirm the target DB immediately before delete/seed and must stop if any preflight or approval gate is unclear.
