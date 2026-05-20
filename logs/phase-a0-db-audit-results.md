# Phase A0.1 DB FK/Orphan Audit Results

Audit date: 2026-05-20

Scope: backend only. No Java source, migrations, schema objects, constraints, indexes, or database data were changed.

## 1. Execution Summary

Execution status: NOT EXECUTED.

Reason: `DATABASE_URL` is not set in the current shell, so the target database could not be identified or confirmed as staging/production-snapshot. Per the task safety rule, the audit was not run against any fallback datasource.

Command requested:

```bash
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f src/main/resources/db/manual/audit_fk_orphans_20260520.sql > logs/audit_fk_orphans_20260520_output.txt
```

Target confirmation:

| Item | Result |
|---|---|
| `DATABASE_URL` present | No |
| Target DB | Unknown |
| Confirmed staging or production snapshot | No |
| Live production approval present | No |
| Script executed | No |
| Output file generated | No |

## 2. Parsed Output Status

No `logs/audit_fk_orphans_20260520_output.txt` file exists, and there is no live audit output to normalize.

## 3. Orphan Counts by Relation

Pending. Run the SELECT-only audit script against a confirmed staging DB or production snapshot first.

## 4. Business Consistency Conflict Counts

Pending. No database result set was available.

## 5. Duplicate Active Records

Pending. The generated SQL script includes checks for:

- active duplicate user email/username
- active duplicate role code
- active duplicate equipment code/inventory number
- active duplicate employee/user link
- active duplicate warehouse stock by warehouse/spare part
- active duplicate brigade member by brigade/user

## 6. Existing FK/Index Inventory

Pending. The generated SQL script includes catalog inventory for:

- table existence
- foreign keys
- primary/unique/check constraints
- indexes and partial indexes
- nullable columns, column types, defaults, and `is_deleted` columns

## 7. Safe FK Candidates

No FK is safe to add from this Phase A0.1 run because live orphan and consistency counts are unknown.

Temporary classification:

| Relation group | Safe now? | Reason |
|---|---|---|
| Existing recent FK candidates: `work_orders.repair_request_id`, `work_orders.defect_id`, `defects.repair_request_id` | No | Need actual constraint inventory and orphan counts |
| Clear aggregate children: approval steps, PPR tasks, work order tasks, procurement lines, budget lines, inspection child rows | No | Need orphan counts |
| Scope-critical links: equipment, repair requests, work orders, warehouses, stock, actual costs | No | Need orphan and cross-department conflict counts |
| Actor fields | No | User-vs-employee semantics still need business confirmation |
| Polymorphic approval document links | No | Should not become DB FKs; use resolver tests |

## 8. Relations Requiring Cleanup

Unknown until the SQL audit output is available. Any relation with `orphan_count > 0` from the script should be classified as cleanup-required before adding an FK.

## 9. Relations Requiring Business Confirmation

These remain blocked independent of orphan counts:

- `equipment.responsible_id`: User or Employee?
- `repair_requests.reporter_id`: User or Employee?
- `repair_requests.assigned_to_id`: User, Employee, or Brigade?
- `work_order_tasks.assigned_to_id`: User or Employee?
- `procurement_requests.requested_by`: User or Employee?
- `procurement_requests.approved_by`: User or Employee?
- `actual_costs.reviewed_by_id`: User or Employee?
- `knowledge_articles.author_id`: User or Employee?
- `approval_requests.document_id`: polymorphic by design; no direct FK.

## 10. Next Action

Set `DATABASE_URL` to a confirmed staging database or production snapshot, then run:

```bash
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f src/main/resources/db/manual/audit_fk_orphans_20260520.sql > logs/audit_fk_orphans_20260520_output.txt
```

After that, rerun Phase A0.1 normalization against `logs/audit_fk_orphans_20260520_output.txt`.

Final recommendation: Phase A1 must wait until live counts are known. The first safe FK/data cleanup batch should be selected only after this audit output is parsed.
