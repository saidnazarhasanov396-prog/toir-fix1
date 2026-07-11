# Vehicle and Equipment Employee Assignment Rollout

## Protected runtime status

**BLOCKED** — the protected remote runtime must remain blocked until authenticated database and application evidence is attached to every required item below. Source review and local test results are not production evidence.

The existing Vehicle migration `V20260616_4__vehicle_driver_sessions.sql` first rewrites assignments by joining `assigned_driver_id` to `hr_employees.user_id` without enforcing a unique match. An ambiguous User UUID can therefore be rewritten nondeterministically to one matching Employee, and a direct Employee UUID can be overwritten when the same UUID is also a User ID linked to a different Employee. Only values that still do not resolve to an active Employee after that rewrite are nulled. Do not deploy while the preflight reports any `AMBIGUOUS` or `UNRESOLVED` row.

## Release identity and Flyway evidence

- [ ] Record deployed backend and frontend remote commits, including repository, branch, full commit SHA, and protected-runtime release/deploy ID.
- [ ] Capture authenticated remote Flyway state before deployment (current version, success flag, installed rank, checksum, and execution timestamp).
- [ ] Record all pending migrations in execution order. Confirm the list includes `V20260711_3__equipment_responsible_employee_identity.sql` and identify whether legacy `V20260616_4__vehicle_driver_sessions.sql` is already applied.
- [ ] Capture authenticated remote Flyway state after deployment and prove every expected migration succeeded with the reviewed checksum.

## Read-only preflight and conversion counts

- [ ] Run `scripts/db/preflight_vehicle_driver_employee_identity.sql` against the protected database using read-only credentials and attach the complete result plus execution timestamp.
- [ ] Record Vehicle counts for `VALID_EMPLOYEE`, `LEGACY_USER_WITH_UNIQUE_EMPLOYEE`, `AMBIGUOUS`, and `UNRESOLVED`, including the total and reconciliation to all active non-null driver assignments.
- [ ] Record Equipment preflight/backfill counts for the same four classifications and reconcile the totals to all active non-null `equipment.responsible_id` values.
- [ ] Require `AMBIGUOUS = 0` and `UNRESOLVED = 0` for Vehicle and Equipment. Any non-zero count keeps the rollout **BLOCKED** until each UUID is reviewed and an approved remediation is evidenced.
- [ ] After migration, prove the unique legacy counts became Employee IDs, valid Employee IDs were preserved, and no assignment was silently removed.

## Database integrity evidence

- [ ] Attach the validated FK definitions from PostgreSQL catalogs, including validation status and referenced table, for `vehicle_details.assigned_driver_id -> hr_employees.id`, `equipment.responsible_id -> hr_employees.id`, and `equipment_usage_sessions.operator_employee_id -> hr_employees.id`.
- [ ] Prove the active Vehicle driver uniqueness index remains present and valid.
- [ ] Provide User-only proofs: a User UUID with no linked active Employee is rejected as a Vehicle driver, Equipment responsible person, and usage operator after rollout.
- [ ] Provide Employee-without-User proof: an active Employee with `user_id IS NULL` remains assignable and readable.
- [ ] Attach actor-field proof showing User identity fields such as `assigned_driver_assigned_by`, `issued_by`, `returned_by`, `created_by`, and `updated_by` retain their pre-rollout values and continue to reference authenticated Users.

## Authenticated GET API checks

- [ ] Record authenticated GET API checks for a Vehicle detail whose canonical `vehicle_details.assigned_driver_id` is an Employee ID; verify the returned driver identity and Employee display fields.
- [ ] Record authenticated GET API checks for Equipment detail/list/export responses; verify responsible IDs and personnel code/name come from Employees and no User fallback appears.
- [ ] Record authenticated GET API checks for Equipment usage sessions; verify `operator_employee_id` and actor User fields retain their separate meanings.
- [ ] Exercise an Employee without an account and confirm every applicable GET response still renders Employee identity.

## Authenticated UI checks

- [ ] Record UI checks for Vehicle create/edit/detail: selectors load Employees, submit Employee IDs, and display the canonical driver.
- [ ] Record UI checks for Equipment create/edit/detail: selectors load Employees, explicit clearing works, and saved/detail values agree.
- [ ] Record UI checks for Equipment usage start/detail: operator selection uses Employee IDs and actor display remains User-based where applicable.
- [ ] Capture screenshots or recordings with the protected-runtime release ID and test identities visible but no secrets.

## Unblock decision

- [ ] A release owner reviews every artifact, records their name and timestamp, and changes **BLOCKED** to approved only after all authenticated evidence is complete and both ambiguous/unresolved counts are zero.
