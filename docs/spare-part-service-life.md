# Spare-Part Service Life

## Domain boundary

`equipment_spare_parts` remains the bill of materials/applicability catalog. It does not represent a physical installation. Physical history starts only through an explicit install or replace command in `spare_part_installations`; issued material alone never creates lifecycle history.

An installation always references `equipment_id`. Vehicles use the same Equipment identity. Installed-part expiry updates derived due/readiness data only and never modifies Equipment or Vehicle design-lifetime fields.

## Rules and precedence

Effective rules resolve deterministically at the evaluation instant:

1. equipment + node + normalized slot;
2. equipment + node;
3. equipment;
4. catalog default.

An overlap at the same exact scope is rejected. Multiple effective rules at the winning scope produce `RULE_AMBIGUOUS`; repository ordering is never a tie breaker. Install captures the selected revision and ordered limits as immutable JSON plus structured meter baselines, so later rule changes cannot rewrite history.

Calendar limits use calendar day/month/year arithmetic. Meter and all new auditable resource values use decimal columns. `ANY`, `ALL`, and `MANUAL` combination modes use inclusive warning/due thresholds.

## Meter authority

`EquipmentMeter` is authoritative. `VehicleDetails.currentOdometerKm` and `currentEngineHours` are compatibility projections written in the same meter transaction. Direct vehicle updates that contradict an active meter are rejected with `METER_PROJECTION_READ_ONLY`.

Without an explicit meter ID, resolution chooses one active primary meter, or the only active meter of the required type. Missing and ambiguous groups return `METER_REQUIRED` and `METER_AMBIGUOUS`. Migration does not guess primary meters. Older reading timestamps are rejected; deleting the latest reading rebuilds the current meter and vehicle projection from remaining readings.

## Lifecycle transactions and idempotency

Install, remove, and replace require `Idempotency-Key`. The server hashes a canonical JSON request and locks the command plus Equipment/installation rows. Same key and payload replays the successful result; another payload conflicts; an in-progress duplicate returns a retryable conflict. PostgreSQL partial uniqueness prevents two active rows at the same equipment position.

Replacement closes and flushes the old row before inserting the new active row, links both directions with one correlation ID, resolves old due events, and evaluates the new installation in the same transaction. Removal always records a disposition. `RETURN_TO_STOCK` is rejected until a safe transactional WMS return path is available.

Work-order completion accepts optional explicit `sparePartLifecycleOperations`. Each operation gets a deterministic key derived from work order and client operation key. Omitting the array preserves legacy completion behavior; material lines are never inferred as installations.

Active installations are re-evaluated immediately after install/replace, after committed canonical meter additions or latest-reading deletion/correction, by the daily UTC calendar scan, and through the privileged `/{installationId}/reevaluate` endpoint. Evaluations always read the immutable installation snapshot and captured baselines.

## Readiness

Operational readiness is a read model separate from `Equipment.status`. Precedence is evaluation error, blocked, maintenance required, warning, ready. Protected/manual/work-order Equipment statuses are never overwritten.

## APIs and permissions

- Rules: `/api/v1/spare-part-life-rules`
- Current/history and commands: `/api/v1/spare-part-installations`
- Due events: `/api/v1/spare-part-due-events`
- Readiness: `/api/v1/equipment/{equipmentId}/operational-readiness`
- Next required actions: `/api/v1/equipment/{equipmentId}/next-required-actions`
- Effective rule preview: `/api/v1/equipment/{equipmentId}/spare-part-life-rules/effective`

Next-action responses keep parent design life, maintenance events, and installed-part events in separate collections. Primary selection uses severity, then calendar date, then a same-unit remaining ratio; dates and incompatible meter units are never subtracted from one another.

The equipment detail UI preserves the BOM surface and adds current/history, readiness, and three separated next-action groups. Standalone install/remove/replace commands and work-order completion both require explicit lifecycle intent. The work-order form can link an operation to an exact previously issued material-usage row; completion material rows alone do not create lifecycle operations.

Permissions are split across rule read/write, installation read/install/remove/replace, due read/acknowledge, and privileged override.

## Migration and deferred scope

Migrations are additive and create no historical installation rows. Refurbishment, cross-asset cumulative physical-instance life, historical reconstruction, and automatic return-to-stock remain deferred. Run the diagnostic SQL before enabling rules broadly and resolve ambiguous meters or vehicle projection divergence explicitly.
