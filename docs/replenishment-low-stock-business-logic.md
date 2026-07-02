# Replenishment, Low-Stock, and Reorder Business Logic

Date: 2026-07-02

This document describes the current backend implementation. Public endpoints are preserved and response-field changes are additive. It does not claim frontend adoption is complete.

## Implementation Status

The core low-stock, reorder-suggestion, replenishment-recommendation, and AUTO procurement-generation paths now share the same replenishment policy:

- `ReplenishmentPolicyEvaluator` is the shared threshold and suggested-quantity evaluator.
- `WmsStockSnapshot` is the authoritative availability input for decision paths.
- `LegacyStockProjectionService` keeps the deprecated `WarehouseStock` compatibility projection in sync after WMS stock changes.
- `LowStockRecommendationService`, `WarehouseReorderService`, `InventoryReplenishmentRecommendationService`, and `ProcurementRequestService.generateFromLowStock(...)` all evaluate policy decisions from WMS snapshots, not from stale legacy `WarehouseStock.quantity - reservedQty`.

Known remaining limitations are listed near the end of this document.

## Canonical Stock Availability

Only `WarehouseStockStatus.AVAILABLE` stock counts as usable for low-stock and procurement decisions.

`WmsStockSnapshot` exposes these values:

- `qtyOnHand`: total projected stock across all WMS statuses.
- `qtyReserved`: total projected reservations.
- `usableAvailable`: projected `AVAILABLE` stock minus projected `AVAILABLE` reservations.
- `nonAvailableQty`: stock held in non-`AVAILABLE` statuses, such as quarantine, damaged, expired, or write-off pending.
- `stockStatusBreakdown`: internal snapshot detail by WMS stock status.

The deprecated `warehouse_stocks` compatibility view still exposes `quantity` and `reserved_qty` as totals across all WMS statuses. That is acceptable for compatibility display and policy-row lookup, but backend decisions must use `WmsStockSnapshot`.

## Shared Threshold Policy

The canonical evaluator is `ReplenishmentPolicyEvaluator`.

Thresholds:

```text
triggerThreshold =
  reorderPoint if reorderPoint > 0
  else WarehouseStock.minQty if minQty > 0
  else SparePart.minStock if minStock > 0

criticalThreshold =
  WarehouseStock.minQty if minQty > 0
  else SparePart.minStock if minStock > 0
  else triggerThreshold
```

Decision:

```text
reorderNeeded = triggerThreshold exists and usableAvailable <= triggerThreshold

CRITICAL = reorderNeeded and (usableAvailable <= 0 or usableAvailable <= criticalThreshold)
WARNING  = reorderNeeded and not CRITICAL
INFO     = not reorderNeeded
```

Recommended quantity:

```text
if not reorderNeeded:
  recommendedQuantity = 0
else if reorderQty > 0:
  recommendedQuantity = reorderQty
else if maxQty > usableAvailable:
  recommendedQuantity = maxQty - usableAvailable
else:
  recommendedQuantity = max(triggerThreshold * 2 - usableAvailable, 0)
```

Forecast-aware recommendation shortage:

```text
totalShortageQty = max(triggerThreshold + maintenanceDemandQty - usableAvailable, 0)
suggestedOrderQty = max(policyRecommendedQty, totalShortageQty)
if reorderQty > 0:
  suggestedOrderQty = max(suggestedOrderQty, reorderQty)
```

Standalone `SparePartForecastService.shortageQty` is maintenance-demand shortage only: `max(requiredQty - usableAvailable, 0)`. `InventoryReplenishmentRecommendationService.totalShortageQty` combines policy shortage and maintenance demand when low-stock and forecast rows are merged.

## Reorder Suggestion Response Semantics

`ReorderSuggestionDto` keeps the legacy fields and adds policy visibility fields.

- `quantity`: total projected stock across all WMS statuses.
- `available`: usable available stock. This legacy field now intentionally means WMS usable availability in production responses.
- `usableAvailable`: the same usable availability value, exposed explicitly for new clients.
- `nonAvailableQty`: aggregate blocked/non-usable stock. This is the only blocked-stock detail exposed by this endpoint today.
- `triggerThreshold`: the effective low-stock trigger.
- `criticalThreshold`: the effective critical threshold.
- `maxQty`: configured warehouse maximum stock.
- `reason`: currently `LOW_STOCK` for policy-triggered reorder suggestions.

`stockStatusBreakdown` is not currently exposed on reorder suggestions. If the frontend needs per-status display, add it deliberately as an additive DTO field; do not infer it from `quantity - available`.

## Backend Flows

### Stock Drops Below Threshold

1. A stock-changing flow posts the WMS stock ledger and/or reservation ledger.
2. The flow synchronizes the deprecated `WarehouseStock` projection with `LegacyStockProjectionService.sync(...)`.
3. If the flow changes usable availability, it calls `LowStockRecommendationService.evaluateStockSafely(stock)`.
4. The evaluator reloads the WMS snapshot and the spare part.
5. `ReplenishmentPolicyEvaluator` computes usable availability, thresholds, severity, and recommended quantity.
6. If `usableAvailable <= triggerThreshold`, the service opens or updates one active `LOW_STOCK` operational issue for the warehouse and spare part.
7. The issue source id is stable: `low-stock:{warehouseId}:{sparePartId}`.
8. Issue metadata contains backward-compatible fields plus `usableAvailable`, `nonAvailableQty`, `triggerThreshold`, `criticalThreshold`, `maxQty`, and `recommendedOrderQuantity`.

### Stock Recovers Above Threshold

1. A receipt, return, reservation release/cancel, adjustment increase, or status transfer makes stock usable again.
2. The same low-stock evaluation hook runs after WMS projection synchronization.
3. If `usableAvailable > triggerThreshold`, any active `LOW_STOCK` issue for that warehouse and spare part is resolved.

### User Requests Reorder Suggestions

1. `WarehouseReorderService` lists policy rows in the requested warehouse scope.
2. Each row is evaluated with `ReplenishmentPolicyEvaluator` and a `WmsStockSnapshot`.
3. Rows without a positive trigger threshold, or rows that are above the threshold, are excluded.
4. Suggestions keep existing response fields and add `usableAvailable`, `nonAvailableQty`, `triggerThreshold`, `criticalThreshold`, `maxQty`, and `reason`.
5. Catalog-only spare parts use `SparePart.minStock` when no warehouse stock policy row exists. Catalog-only suggestions are enterprise-level fallback rows, not warehouse-specific WMS stock rows.

### User Creates Procurement From Recommendations

1. `ReplenishmentProcurementRequestService` reloads recommendations and validates selected items against the target warehouse.
2. Repeated `(warehouseId, sparePartId)` payload items are ignored after the first.
3. The backend takes a transaction-scoped advisory lock for the `(warehouseId, sparePartId)` AUTO key.
4. Existing active AUTO procurement requests are checked before adding a line.
5. A single DRAFT AUTO request is created per warehouse with one line per selected spare part.

The legacy `POST /api/v1/procurement-requests/generate-from-low-stock` path now uses the same shared policy, WMS snapshot, duplicate guard, warehouse grouping, and scope checks.

## Covered Stock-Change Entry Points

| Flow | Changes usable availability? | Syncs projection? | Evaluates low stock? | Notes |
| --- | --- | --- | --- | --- |
| `StockMovementService.create(...)` for `RECEIPT`, `RETURN`, `ISSUE`, `TRANSFER`, `ADJUSTMENT` | Yes | Yes | Yes | Generic `RESERVATION` and `RELEASE` are rejected in favor of `ReservationService`; equipment movements are rejected or domain-owned. |
| `StockMovementService.receipt(...)` | Yes | Yes | Yes | Dedicated manual receipt endpoint. |
| `StockMovementService.issue(...)` | Yes | Yes | Yes | Dedicated manual issue endpoint. |
| `InventoryTransactionService` receipt, issue, transfer, return, adjustment | Yes | Yes | Yes | Transfer evaluates both source and destination warehouses. |
| `InventoryCountSessionService` posted adjustment lines | Yes when variance is non-zero | Yes | Yes | Zero-variance lines do not post stock and do not need evaluation. |
| `ReservationService` reserve, cancel, fulfill | Yes via reserved quantity | Yes | Yes | Reservation state is not handled by generic stock movements. |
| `ProcurementRequestService.markReceived(...)` | Yes | Yes | Yes | Spare-part receipts evaluate each received line. Equipment receipts do not affect spare-part stock. |
| `PurchaseOrderService.receive(...)` | Yes | Yes | Yes | Spare-part receipts evaluate each received line. |
| `RepairMaterialUsageService` material usage | Yes | Yes | Yes | Direct repair/work-order issue path. |
| `WorkOrderWmsService` pick confirmation and material return | Yes | Yes | Yes | WMS work-order issue and return path. |
| `WarehouseQualityService` status transfer | Yes when source/target usability differs | Yes | Yes | Example: `AVAILABLE -> QUARANTINE` can open low stock; `QUARANTINE -> AVAILABLE` can resolve it. |
| `WarehouseQualityService` write-off submit, post, reject | Yes | Yes | Yes | Submit/reject move between usable and non-usable statuses; post removes pending write-off stock. |
| `WarehouseStockMoveService` bin move | No | Yes | No | Same `stockStatus` moves between bins; usable availability is unchanged. |
| `WarehouseTaskService` `PUTAWAY` completion | No by itself | Via `WarehouseStockMoveService` | No | Completion delegates to a same-status bin move. The receipt that generated the task is where low-stock recovery is evaluated. |
| `WarehouseTaskService` `RECEIVE` completion | No stock ledger post in current implementation | No | No | This is workflow/scanning state only today. Procurement/PO receipt endpoints own stock receipt. |
| `WarehouseBinService` block/unblock or metadata updates | No | No | No | Bin status changes do not move quantity, reservations, or WMS stock status. |

## Compatibility View Limitation

The `warehouse_stocks` compatibility view is a deprecated read-only projection. It currently computes:

```text
quantity     = sum(warehouse_stock_balances.qty_on_hand)
reserved_qty = sum(warehouse_stock_balances.qty_reserved)
```

Those sums include every WMS stock status. Do not use `WarehouseStock.getAvailable()` or SQL `warehouse_stocks.quantity - warehouse_stocks.reserved_qty` for reorder, low-stock, replenishment, or procurement decisions.

Current decision services that were checked:

- `LowStockRecommendationService`: uses `LegacyStockProjectionService.current(...)` and `ReplenishmentPolicyEvaluator`.
- `WarehouseReorderService`: uses `currentAll(...)` / `currentForWarehouse(...)` WMS snapshots.
- `InventoryReplenishmentRecommendationService`: consumes reorder suggestions where `available` is usable availability, and derives reserved stock as `quantity - nonAvailableQty - usableAvailable`.
- `ProcurementRequestService.generateFromLowStock(...)`: uses WMS snapshots and the shared evaluator.
- `SparePartForecastService`: uses WMS snapshots for `availableQty`.
- `WarehouseAnalyticsService`: uses WMS snapshots for warehouse deficit status.

Known limitation: some aggregate dashboard/statistics counters still come from repository-level compatibility-view SQL and may count low-stock items using total legacy availability. These counters are display metrics, not procurement or issue-generation decisions.

## API Notes For Frontend

Recommended frontend behavior:

- Display `usableAvailable` as the primary "available to consume" and "needs reorder" value.
- Keep reading `available` for backward compatibility, but treat it as usable available stock in production reorder responses.
- Show `nonAvailableQty` as blocked stock when it is greater than zero.
- Do not recompute severity in the client; use backend `urgency`/`severity`.
- Do not infer per-status breakdown from aggregate fields. The reorder endpoint currently exposes only `nonAvailableQty`.
- When creating procurement from recommendations, send unique spare-part selections per warehouse. The backend de-duplicates, but avoiding duplicate selections improves user feedback.
- Treat catalog-only suggestions as spare-part minimum-stock suggestions until a warehouse-specific stock policy row exists.

## Endpoint Compatibility Notes

- `GET /api/v1/warehouses/reorder/suggestions`: legacy fields remain. `available` now means usable availability; `usableAvailable` is additive and explicit.
- `GET /api/v1/inventory/replenishment/recommendations`: current stock includes total WMS stock. Reserved stock excludes non-available stock. Suggested quantity is forecast-aware when forecast and low-stock rows merge.
- `POST /api/v1/inventory/replenishment/procurement-requests`: creates DRAFT AUTO requests from selected recommendation rows and uses duplicate guards.
- `POST /api/v1/procurement-requests/generate-from-low-stock`: legacy compatibility path using the same WMS policy evaluator.
- Stock receipt/issue/reservation/status-transfer endpoints preserve their public shapes; low-stock evaluation hooks are backend-side lifecycle effects.

## Remaining Cleanup Candidates

- Add a database-native uniqueness strategy for active AUTO request lines if the schema later denormalizes `spare_part_id` onto `procurement_requests` or adds a dedicated generated key table. The current implementation uses transaction advisory locks plus active-request checks because active status is split across request headers and lines.
- Review `SparePartForecastService` operational issue source IDs. Current forecast issue identity includes the forecast period, which can leave older period-specific forecast issues active after a rolling window changes. A follow-up should move forecast issue identity to a stable warehouse/department/spare-part key and resolve superseded forecast issues.
- Convert dashboard/statistics low-stock counters that still rely on compatibility-view SQL to the shared WMS snapshot policy if those counters become operationally authoritative.
- Consider rejecting or redirecting generic `StockMovementType.BIN_MOVE` and `StockMovementType.WRITEOFF` values in `StockMovementService.create(...)`. The dedicated WMS bin-move and warehouse-quality/write-off services are the authoritative stock-changing paths.
- Consider merging legacy low-stock procurement generation and recommendation-based procurement creation behind one internal command service once frontend callers have converged on the recommendation workflow.
