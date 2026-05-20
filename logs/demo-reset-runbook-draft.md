# Demo Reset Manual SQL Runbook Draft

Date: 2026-05-20

Scope: script draft only. Do not execute against any database until the approval checklist below is complete.

## Files

| File | Purpose |
|---|---|
| `src/main/resources/db/manual/2026-05-20_demo_reset_preflight.sql` | SELECT-only target, migration, admin/role, row-count, integration/webhook preflight. |
| `src/main/resources/db/manual/2026-05-20_demo_reset_delete_draft.sql` | Ordered manual DELETE draft. No role deletion and no migration-history changes. |
| `src/main/resources/db/manual/2026-05-20_demo_seed_draft.sql` | Deterministic Navoiyazot-style demo seed draft using stable UUIDs/codes and existing roles. |
| `src/main/resources/db/manual/2026-05-20_demo_reset_validation.sql` | SELECT-only post-reset validation and smoke-data checks. |

## Execution Order

1. Confirm written approvals and maintenance window.
2. Put the application and background workers in maintenance mode.
3. Take a full database backup.
4. Restore the backup to a production snapshot database.
5. Run preflight on the snapshot:

```bash
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f src/main/resources/db/manual/2026-05-20_demo_reset_preflight.sql
```

6. Review preflight output:
   - target database, user, server address, and port;
   - latest Flyway migrations;
   - `SYSTEM_ADMIN` role and wildcard permission;
   - admin users and admin role mappings;
   - row counts for clear tables;
   - active integration endpoints and webhook subscriptions.
7. Run the delete draft on the snapshot only after review:

```bash
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f src/main/resources/db/manual/2026-05-20_demo_reset_delete_draft.sql
```

8. Run the seed draft on the snapshot:

```bash
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f src/main/resources/db/manual/2026-05-20_demo_seed_draft.sql
```

9. Run validation on the snapshot:

```bash
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f src/main/resources/db/manual/2026-05-20_demo_reset_validation.sql
```

10. Run frontend/backend smoke tests against the snapshot.
11. Only after snapshot success, repeat the same approved process against the named production/demo target during the maintenance window.

## Backup And Rollback

- Backup is mandatory before any production/demo target execution.
- Dry run on a restored production snapshot is mandatory.
- Rollback is database backup restore only.
- Do not rely on reverse SQL for rollback.

## Maintenance Mode

Before executing delete/seed scripts on any shared environment:

- stop application writers and scheduled workers;
- disable outbound integration jobs or confirm they are isolated;
- confirm active webhook subscriptions will not call real external systems;
- block user traffic or use a maintenance banner.

## Approval Checklist

| Approval | Required signoff |
|---|---|
| PM/Product owner | Confirms garbage data can be replaced and approves demo dataset. |
| Tech lead | Confirms delete order, seed scope, and validation checks. |
| DBA/Ops | Confirms target database, backup, restore test, execution user, and maintenance window. |
| Security | Confirms admin preservation, role preservation, and demo credential handling. |
| Integration owner | Confirms integrations/webhooks are disabled, isolated, or safe. |
| Compliance owner | Confirms treatment of audit logs and file metadata. |

## Preservation Rules

- Do not touch `flyway_schema_history`.
- Do not remove any row from `roles`.
- Do not remove `SYSTEM_ADMIN`.
- Do not remove wildcard permission `*`.
- Do not remove admin users.
- Do not remove admin role mappings.
- Use ordered `DELETE`, not broad cascade clearing.

## Seed Scope

The seed draft creates a compact deterministic demo dataset:

- departments for Navoiyazot enterprise, ammonia, urea, and nitric acid workshops;
- role-specific demo users linked to existing roles;
- employees and one mechanical brigade;
- reference dictionaries needed for smoke flows;
- locations, equipment types, equipment, maintenance regulations;
- warehouses, spare parts, warehouse stock, and stock movement;
- PPR plan/tasks;
- repair requests, work orders, work order tasks;
- defects and defect list;
- procurement request and line;
- maintenance budget, budget line, actual cost;
- approval request and steps;
- inspection route/checkpoints/round/results;
- knowledge articles linked to equipment, defect, and work order.

The seed does not modify role permissions. Existing roles must be present before the seed runs.

## Smoke Test Checklist

| Area | Expected result |
|---|---|
| Admin login | Existing admin can still log in and has `SYSTEM_ADMIN` with `*`. |
| Demo login | Demo role users exist; final password hash must be approved before real execution. |
| Departments | Demo departments are visible and scoped correctly. |
| Equipment | Demo equipment appears by department, location, and type. |
| Warehouse | Demo warehouses show stock, including low-stock seal example. |
| PPR | Demo plan and tasks exist and display planned/approved statuses. |
| Repair request | Demo requests are linked to equipment and department. |
| Work order | Demo work orders include repair and PPR-linked flows. |
| Defects | Demo defect and defect list appear with linked equipment/work order. |
| Procurement | Demo low-stock procurement request and line exist. |
| Finance | Demo budget, budget line, and approved actual cost exist. |
| Approval | Demo approval request has two pending steps. |
| Inspection | Demo route, checkpoints, completed round, and results exist. |
| Knowledge | Demo articles render with JSON tags and linked records. |

## Known Draft Limits

- The seed is intentionally compact; it is not the full future production-scale demo dataset.
- `file_assets` are not cleared in the delete draft because filesystem cleanup needs a separate approved plan.
- Reference data is preserved by default; replacing dictionaries should be a separate reviewed script.
- The demo password hash is a draft value and needs security approval before real use.
- The scripts have not been executed in this phase.

## Verification Commands For This Draft

```bash
rg -n "\\b(DROP|TRUNCATE|ALTER)\\b" src/main/resources/db/manual/2026-05-20_demo_reset_preflight.sql src/main/resources/db/manual/2026-05-20_demo_reset_delete_draft.sql src/main/resources/db/manual/2026-05-20_demo_seed_draft.sql src/main/resources/db/manual/2026-05-20_demo_reset_validation.sql
rg -n "DELETE\\s+FROM\\s+flyway_schema_history|DELETE\\s+FROM\\s+roles\\b" src/main/resources/db/manual/2026-05-20_demo_reset_preflight.sql src/main/resources/db/manual/2026-05-20_demo_reset_delete_draft.sql src/main/resources/db/manual/2026-05-20_demo_seed_draft.sql src/main/resources/db/manual/2026-05-20_demo_reset_validation.sql
git diff --check
```
