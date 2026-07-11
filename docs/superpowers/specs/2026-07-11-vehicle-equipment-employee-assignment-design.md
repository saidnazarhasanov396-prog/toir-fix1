# Vehicle and Equipment Employee Assignment Design

## Goal

Make Employee identity the enforced canonical identity for Vehicle drivers, Equipment responsible people, and Equipment usage operators, while preserving User identity for authentication and action actors.

## Scope

This change covers the normal Vehicle and Equipment write/read paths, Java and SQL seed paths, legacy Equipment responsible-data conversion, database constraints, explicit Equipment responsible-person clearing, focused tests, and deployment preflight documentation.

It does not introduce a Person abstraction, split Vehicle driver from Vehicle responsible person, change Repair Request or Work Order assignments, or convert authentication/audit actors to Employee.

## Canonical Identity Rules

- `vehicle_details.assigned_driver_id` references `hr_employees.id` and is the canonical Vehicle operational assignment.
- `equipment.responsible_id` references `hr_employees.id` and is the canonical Equipment responsible-person assignment. For Vehicle rows it remains only a compatibility mirror of the canonical driver.
- `equipment_usage_sessions.operator_employee_id` references `hr_employees.id`.
- `assigned_driver_assigned_by`, `issued_by`, `returned_by`, `created_by`, `updated_by`, and other authenticated action actors remain User IDs.
- An Employee with `user_id IS NULL` remains assignable.
- A User without a linked Employee is rejected by normal operational writes and is never substituted at runtime.

## Seed Design

`SampleDataSeeder.linkEquipmentCriticalityAndResponsible` will no longer place the admin User ID in `Equipment.responsibleId`. It will resolve the intended User to Employees through exact `hr_employees.user_id = users.id` linkage and use the Employee ID only when exactly one non-deleted Employee matches. Missing or ambiguous mapping will leave the responsible assignment null and emit an explicit warning without creating an artificial account/personnel link.

The Navoiy Equipment SQL seed will retain its deterministic responsible usernames only as lookup inputs. A lateral/exact-count mapping will resolve each username through `users.id -> hr_employees.user_id`, returning an Employee ID only for an exact-one match. Its `responsible_id` expressions will never return `users.id`. Re-execution remains idempotent through the existing conflict-update logic.

Post-seed contract checks will prove that every non-null Equipment responsible ID resolves to `hr_employees`, including Employee-without-account coverage, and that a User-only account is not assigned.

## Equipment Migration Design

The verified next migration is `V20260711_3__equipment_responsible_employee_identity.sql`; versions `V20260711_1` and `V20260711_2` already exist in the current checkout.

For each non-null `equipment.responsible_id`, classification is:

- `VALID_EMPLOYEE`: the UUID is an Employee ID and does not create a conflicting User interpretation.
- `LEGACY_USER_WITH_UNIQUE_EMPLOYEE`: it is not an Employee ID, is a User ID, and exactly one non-deleted Employee links through `user_id`.
- `AMBIGUOUS`: multiple Employees link to the User, or the UUID is simultaneously an Employee ID and a conflicting User-to-different-Employee identity.
- `UNRESOLVED`: neither a valid Employee nor a uniquely resolvable legacy User mapping.

Only `LEGACY_USER_WITH_UNIQUE_EMPLOYEE` rows are updated. Valid Employee values remain unchanged. Before constraint creation, a PostgreSQL `DO` block raises a descriptive exception for ambiguous or unresolved rows and includes bounded equipment ID, responsible UUID, User-match flag, and Employee-match count evidence. No row is nulled or deleted.

After backfill, the migration creates an index if needed, adds `equipment.responsible_id -> hr_employees(id)` as `NOT VALID`, validates it, and documents the column/FK semantics. Existing repository delete behavior is retained.

PostgreSQL migration tests cover preservation, unique conversion, ambiguous/unresolved failure, null allowance, User-only rejection after validation, actor-field non-mutation, and clean-schema migration.

## Equipment Update Tri-State Design

The existing API has no general nullable-field wrapper. `EquipmentUpdateRequest` will therefore add a dedicated Boolean `clearResponsible` field:

- omitted `responsibleId` and absent/false `clearResponsible`: preserve the stored value;
- non-null `responsibleId` and absent/false `clearResponsible`: validate with `EmployeeRepository` and assign;
- `clearResponsible: true` and null/omitted `responsibleId`: clear the assignment;
- `clearResponsible: true` plus a non-null UUID: reject as contradictory.

Create semantics remain unchanged: a missing responsible person is allowed. The frontend sends `responsibleId: null` together with `clearResponsible: true` when the edit selector is explicitly cleared; it does not load Users or map `employee.userId`.

Backend tests cover all three states and Employee/User identity boundaries. Frontend tests cover selector option values and the explicit clear payload.

## Write and Read Hardening

Normal Vehicle create/update, Equipment create/update, ATIL Vehicle import, and Equipment usage-session start continue validating operational IDs through `EmployeeRepository`. No normal write path gains a User-to-Employee compatibility fallback.

Equipment response enrichment continues batch-loading `hr_employees`. A non-null responsible ID that cannot resolve to Employee after the migration is a data-integrity failure rather than a User-display fallback. Existing inactive Employees may display; Employees without User accounts display from Employee name/code fields.

Vehicle detail/read tests will prove `vehicle_details.assigned_driver_id` remains canonical even when the Equipment compatibility mirror differs. Equipment CSV will retain the raw ID for compatibility and add Employee personnel code/name using batch loading, never User lookup.

## Vehicle Rollout Safety

The existing Vehicle driver migration remains unchanged. A read-only preflight SQL artifact will classify assigned-driver values as Employee, uniquely resolvable legacy User, ambiguous, or unresolved. It will prominently flag that the existing migration nulls unresolved driver IDs, so rollout must stop until unresolved production rows are reviewed.

No duplicate Vehicle FK migration will be created.

## Runtime and Rollout Verification

A rollout checklist will require recording deployed backend/frontend commits, remote Flyway version, pending migrations, preflight/backfill counts, ambiguous/unresolved counts, validated FK definitions, API checks, UI checks, and proof that actor User fields were untouched.

The protected remote runtime will remain `BLOCKED` until authenticated read access and database evidence are available. Source and local-test success will not be reported as production completion.

## Verification Strategy

Backend verification includes focused Vehicle, Equipment, usage-session, seed-contract, canonical-read, export, and migration tests. PostgreSQL/Testcontainers tests are run when Docker is available and otherwise reported as blocked rather than passed. The frontend runs focused Vitest coverage plus `yarn build`, which performs TypeScript build and Vite production compilation.

All unrelated pre-existing working-tree changes are preserved. At final handoff, both repositories' complete working trees are intentionally committed and pushed to their existing `Codex_org` branches, as explicitly authorized.

## Error Handling and Safety

- Migration conversion is based only on UUID/FK linkage, never name, phone, or email.
- Ambiguous/unresolved data blocks migration with bounded non-secret evidence.
- Seed ambiguity never falls back to User ID.
- Contradictory clear/assign payloads return a client error.
- Remote/shared databases are not mutated by this task.
- Existing actor/audit identity columns are not included in conversion SQL.
