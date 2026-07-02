# Replenishment, Low-Stock, and Reorder Business Logic

Date: 2026-07-02

This report describes the backend replenishment policy after the low-stock/reorder cleanup. Public endpoints are preserved; new response fields are additive.

## Canonical Stock Availability

All low-stock, reorder, replenishment, and procurement-generation paths must use WMS-projected stock where possible.

- `qtyOnHand`: total projected stock across WMS statuses.
- `qtyReserved`: total projected reservations.
- `usableAvailable`: projected `AVAILABLE` stock minus projected `AVAILABLE` reservations.
- `nonAvailableQty`: stock held in non-`AVAILABLE` statuses, such as quality, write-off, or hold states.

Only `WarehouseStockStatus.AVAILABLE` counts as usable for replenishment decisions. Non-available stock is surfaced for visibility but does not prevent low-stock or procurement recommendations.

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

Forecast shortage:

```text
totalShortageQty = max(triggerThreshold + maintenanceDemandQty - usableAvailable, 0)
suggestedOrderQty = max(policyRecommendedQty, totalShortageQty)
if reorderQty > 0:
  suggestedOrderQty = max(suggestedOrderQty, reorderQty)
```

## Backend Flows

### Stock Drops Below Threshold

1. A stock-changing flow updates WMS balances and synchronizes the legacy `WarehouseStock` projection.
2. The flow calls `LowStockRecommendationService.evaluateStockSafely(stock)`.
3. The evaluator loads the spare part and the WMS snapshot.
4. `ReplenishmentPolicyEvaluator` computes usable availability, thresholds, severity, and recommended quantity.
5. If `usableAvailable <= triggerThreshold`, the service opens or updates one active `LOW_STOCK` operational issue for the warehouse and spare part.
6. Issue metadata contains backward-compatible fields plus `usableAvailable`, `nonAvailableQty`, `triggerThreshold`, and `criticalThreshold`.

Covered stock-change entry points:

- inventory receipt, issue, transfer, return, reservation, and reservation cancellation
- procurement receipt and purchase-order receipt
- stock movement receipt
- repair/work-order material usage and return
- warehouse quality/status transfers, write-off approval submission, posting, and rejection

### Stock Recovers Above Threshold

1. A receipt, return, cancellation, or status transfer makes stock usable again.
2. The same low-stock evaluation hook runs after WMS projection synchronization.
3. If `usableAvailable > triggerThreshold`, any active `LOW_STOCK` issue for that warehouse and spare part is resolved.

### User Requests Reorder Suggestions

1. `WarehouseReorderService` lists warehouse stock rows for the requested scope.
2. Each row is evaluated with the shared policy and WMS snapshot.
3. Rows without a positive threshold or without reorder need are excluded.
4. Suggestions keep existing response fields and add policy visibility fields:
   `usableAvailable`, `nonAvailableQty`, `triggerThreshold`, `criticalThreshold`, `maxQty`, and `reason`.
5. Catalog-only spare parts still use spare-part minimum stock as a fallback when no warehouse stock row exists.

### User Creates Procurement From Recommendations

1. Recommendation items are validated and scoped to the target warehouse.
2. Repeated `(warehouseId, sparePartId)` payload items are ignored after the first.
3. The backend takes a transaction-scoped advisory lock for the `(warehouseId, sparePartId)` AUTO key.
4. Active AUTO procurement requests are checked before adding a line.
5. A single DRAFT AUTO request is created per warehouse with one line per selected spare part.

The legacy `POST /api/v1/procurement-requests/generate-from-low-stock` path now uses the same shared policy, WMS snapshot, duplicate guard, warehouse grouping, and scope checks.

## API Notes For Frontend

Existing endpoint shapes are preserved. The frontend can continue using existing fields and may adopt these additive fields where present:

- `usableAvailable`: the value that should drive "can consume" and "needs reorder" UI decisions.
- `nonAvailableQty`: stock present but blocked by status.
- `triggerThreshold`: the effective reorder trigger.
- `criticalThreshold`: the effective critical threshold.
- `maxQty`: the configured warehouse maximum stock level.
- `reason`: currently `LOW_STOCK` for policy-triggered suggestions.

Recommended frontend behavior:

- Display `usableAvailable`, not raw `quantity - reservedQty`, as the primary availability number for reorder decisions.
- Show `nonAvailableQty` as blocked stock when it is greater than zero.
- Label CRITICAL when severity is `CRITICAL`; do not recompute severity in the client.
- When creating procurement from recommendations, send unique spare-part selections per warehouse. The backend de-duplicates, but avoiding duplicate selections improves user feedback.
- Treat catalog-only suggestions as spare-part minimum-stock suggestions until a warehouse stock row exists.

## Remaining Cleanup Candidates

- Add a database-native uniqueness strategy for active AUTO request lines if the schema is later changed to denormalize `spare_part_id` onto `procurement_requests` or to add a dedicated generated key table. The current implementation uses transaction advisory locks plus active-request checks because active status is split across request headers and lines.
- Review `SparePartForecastService` operational issue source IDs. The current forecast issue identity includes the forecast period, which can leave older period-specific forecast issues active after a rolling window changes. A follow-up should move forecast issue identity to a stable warehouse/department/spare-part key and resolve superseded forecast issues.
- Consider merging legacy low-stock procurement generation and recommendation-based procurement creation behind one internal command service once frontend callers have converged on the recommendation workflow.
