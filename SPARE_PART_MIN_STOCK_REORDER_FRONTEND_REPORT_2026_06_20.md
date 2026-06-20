# SparePart Min Stock Reorder Frontend Handoff

Date: 2026-06-20
Branch: `Codex_org`

## Summary

Backend low-stock detection now uses the Spare Parts Catalog `minStock` as the final fallback when a warehouse stock row has no active stock-level threshold.

Effective low-stock threshold priority:

```text
warehouse_stocks.reorder_point > warehouse_stocks.min_qty > spare_parts.min_stock
```

The comparison is against available stock:

```text
available = warehouse_stocks.quantity - warehouse_stocks.reserved_qty
```

If `available <= effectiveThreshold`, that spare part is returned as a low-stock/reorder candidate.

## Frontend Impact

No frontend API path, query parameter, or response field changes are required.

Existing frontend pages should start receiving the missing low-stock rows automatically after this backend is deployed:

- Spare Parts Catalog low-stock card/count can include rows where only catalog `minStock` is configured.
- Reorder Suggestions can include rows where `stock.minQty = 0`, `reorderPoint = null`, and `available <= sparePart.minStock`.
- Unified Replenishment Recommendations can show `LOW_STOCK` rows from catalog `minStock` fallback even when the threshold deficit is `0` at equality.

## Affected Endpoints

### `GET /api/v1/warehouse/replenishment-recommendations`

Used by the current Reorder Suggestions page.

Relevant query params remain unchanged:

```text
days
from
to
warehouseId
onlyDeficit
page
size
```

For low-stock-only rows:

- `reason = LOW_STOCK`
- `availableStock` is the stock value used for threshold comparison
- `minStock` can now come from `spare_parts.min_stock` when stock-level min is missing
- `totalShortageQty` remains the deficit against the effective threshold
- `suggestedOrderQty` / `recommendedQuantity` now use backend reorder recommendation logic, so equality with min stock can still recommend a purchase quantity

Example:

```json
{
  "sparePartCode": "SP-2026-0005",
  "sparePartName": "Laptop Kamera",
  "availableStock": 5,
  "minStock": 5,
  "reorderPoint": null,
  "maintenanceDemandQty": 0,
  "totalShortageQty": 0,
  "suggestedOrderQty": 5,
  "recommendedQuantity": 5,
  "severity": "WARNING",
  "reason": "LOW_STOCK"
}
```

### `GET /api/v1/warehouses/reorder/suggestions`

Legacy reorder endpoint now also includes catalog-min fallback candidates.

For fallback rows:

- `minQty` in the response carries the effective minimum threshold shown to the frontend
- `shortfall` is `0` when `available` is exactly equal to threshold
- `recommendedQuantity` is still positive when purchase is recommended
- `urgency = WARNING` when the row is triggered only by catalog `minStock`

### `GET /api/v1/warehouses/reorder/stats`

Stats are based on the same reorder service behavior, so totals can increase after deployment when catalog-only minimums exist.

### `GET /api/v1/warehouses/spare-parts/stats`

`lowStockItems` now joins `spare_parts` and uses the same threshold priority. The catalog card should no longer show `0` when rows are low only because `spare_parts.min_stock` is configured.

## UI Notes

No new frontend condition is needed.

Recommended display behavior:

- Keep using `availableStock` for low-stock visual state.
- Keep showing `minStock / reorderPoint` in the policy column.
- For low-stock rows with `totalShortageQty = 0` and `suggestedOrderQty > 0`, display the row because `reason = LOW_STOCK` is authoritative.
- Do not filter out low-stock rows just because shortage is zero; equality with the reorder threshold is still a reorder trigger.

## Regression Scenarios Covered

Backend tests now cover:

- `available = sparePart.minStock` creates a reorder suggestion when `stock.minQty = 0` and `reorderPoint = null`.
- `available > sparePart.minStock` does not create a suggestion.
- `reorderPoint` and positive `stock.minQty` keep priority over `sparePart.minStock`.
- unified replenishment rows keep nonzero `suggestedOrderQty` for catalog-min low-stock rows.
- warehouse spare part stats count catalog-min fallback low-stock rows.

Verification command:

```bash
./mvnw -q -Dtest=WarehouseReorderServiceTest,WarehouseReorderServiceStatsTest,InventoryReplenishmentRecommendationServiceTest,WarehouseStockRepositorySparePartsStatsTest test
```
