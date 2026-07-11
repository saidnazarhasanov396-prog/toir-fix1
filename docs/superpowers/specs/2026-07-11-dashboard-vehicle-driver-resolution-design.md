# Dashboard Vehicle Driver Resolution Design

## Problem

The Dashboard "top broken equipment responsibles" ranking must display the operational Employee assigned as a Vehicle driver. `vehicle_details.assigned_driver_id` is the canonical Vehicle assignment, while `equipment.responsible_id` is only the Equipment responsible assignment. Current Dashboard resolution consults Vehicle details only when `equipment.category == VEHICLE`, so legacy/imported rows with an inconsistent or missing category can hide a valid assigned driver.

## Design

For the already-batched top-broken Equipment IDs, load active `VehicleDetails` rows once. Resolve the responsible Employee ID in this order:

1. If a `VehicleDetails` row exists for the Equipment, use its `assigned_driver_id` (including `null`, which means no driver is assigned).
2. Otherwise use `equipment.responsible_id` for ordinary Equipment.

The presence of the subtype row, not the denormalized Equipment category, determines whether Vehicle driver semantics apply. Load all resulting Employee IDs with the existing single `EmployeeRepository.findAllByIdInAndIsDeletedFalse` batch query. Do not query User and do not mirror the driver into `equipment.responsible_id`.

## Error and Display Behavior

- A resolved Employee is returned with ID, full name, and position.
- A non-null Employee ID that cannot be enriched remains represented by its raw ID and the frontend shows its existing "unavailable" state.
- A Vehicle details row with no assigned driver is shown as unassigned; it must not fall back to an Equipment responsible value.
- Ranking membership remains unchanged: only Equipment already included in the top-failure ranking is shown.

## Tests

Add regression coverage proving:

- a VehicleDetails-assigned driver wins even when Equipment category is legacy/inconsistent;
- a VehicleDetails row with a null driver does not fall back to `equipment.responsible_id`;
- ordinary Equipment still uses `equipment.responsible_id`;
- Vehicle details and Employees remain batch-loaded with no N+1 queries.

Run focused Dashboard service and analytics scope tests before pushing `Codex_org`.
