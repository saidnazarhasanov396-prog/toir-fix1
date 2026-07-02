package com.toir.service;

import com.toir.entity.SparePart;
import com.toir.entity.warehouse.WarehouseStock;
import com.toir.enums.NotificationSeverity;
import com.toir.service.warehouse.WmsStockSnapshot;

public final class ReplenishmentPolicyEvaluator {

    public static final String REASON_LOW_STOCK = "LOW_STOCK";

    private ReplenishmentPolicyEvaluator() {
    }

    public static ReplenishmentPolicyResult evaluate(WarehouseStock stock,
                                                     SparePart sparePart,
                                                     WmsStockSnapshot snapshot) {
        if (stock == null || snapshot == null) {
            return ReplenishmentPolicyResult.noPolicy(snapshot);
        }
        Double warehouseMinQty = positive(stock.getMinQty());
        Double sparePartMinStock = sparePart == null ? null : positive(sparePart.getMinStock());
        Double triggerThreshold = firstPositive(stock.getReorderPoint(), warehouseMinQty, sparePartMinStock);
        Double criticalThreshold = firstPositive(warehouseMinQty, sparePartMinStock, triggerThreshold);
        double usableAvailable = snapshot.availableQty().doubleValue();
        boolean reorderNeeded = triggerThreshold != null && usableAvailable <= triggerThreshold;
        double shortfall = triggerThreshold == null ? 0 : Math.max(triggerThreshold - usableAvailable, 0);
        double recommendedQuantity = recommendedQuantity(
                reorderNeeded,
                stock.getReorderQty(),
                stock.getMaxQty(),
                triggerThreshold,
                usableAvailable
        );
        NotificationSeverity severity = severity(reorderNeeded, usableAvailable, triggerThreshold, criticalThreshold);
        return new ReplenishmentPolicyResult(
                snapshot.qtyOnHand().doubleValue(),
                snapshot.qtyReserved().doubleValue(),
                usableAvailable,
                Math.max(snapshot.nonAvailableQty().doubleValue(), 0),
                warehouseMinQty,
                sparePartMinStock,
                triggerThreshold,
                criticalThreshold,
                positive(stock.getReorderPoint()),
                positive(stock.getReorderQty()),
                positive(stock.getMaxQty()),
                reorderNeeded,
                severity,
                shortfall,
                recommendedQuantity,
                reorderNeeded ? REASON_LOW_STOCK : null
        );
    }

    public static double forecastTotalShortage(Double triggerThreshold,
                                               double maintenanceDemandQty,
                                               double usableAvailable) {
        return Math.max(valueOrZero(triggerThreshold) + maintenanceDemandQty - usableAvailable, 0);
    }

    public static double forecastAwareSuggestedOrderQty(double policyRecommendedQty,
                                                        Double reorderQty,
                                                        double totalShortageQty) {
        double suggested = Math.max(policyRecommendedQty, totalShortageQty);
        Double positiveReorderQty = positive(reorderQty);
        if (positiveReorderQty != null) {
            suggested = Math.max(suggested, positiveReorderQty);
        }
        return suggested;
    }

    private static NotificationSeverity severity(boolean reorderNeeded,
                                                 double usableAvailable,
                                                 Double triggerThreshold,
                                                 Double criticalThreshold) {
        if (!reorderNeeded || triggerThreshold == null) {
            return NotificationSeverity.INFO;
        }
        if (usableAvailable <= 0 || (criticalThreshold != null && usableAvailable <= criticalThreshold)) {
            return NotificationSeverity.CRITICAL;
        }
        return NotificationSeverity.WARNING;
    }

    private static double recommendedQuantity(boolean reorderNeeded,
                                              Double reorderQty,
                                              Double maxQty,
                                              Double triggerThreshold,
                                              double usableAvailable) {
        if (!reorderNeeded || triggerThreshold == null) {
            return 0;
        }
        Double positiveReorderQty = positive(reorderQty);
        if (positiveReorderQty != null) {
            return positiveReorderQty;
        }
        Double positiveMaxQty = positive(maxQty);
        if (positiveMaxQty != null && positiveMaxQty > usableAvailable) {
            return Math.max(positiveMaxQty - usableAvailable, 0);
        }
        return Math.max(triggerThreshold * 2 - usableAvailable, 0);
    }

    private static Double firstPositive(Double first, Double second, Double third) {
        Double value = positive(first);
        if (value != null) {
            return value;
        }
        value = positive(second);
        return value == null ? positive(third) : value;
    }

    private static Double positive(Double value) {
        return value != null && value > 0 ? value : null;
    }

    private static Double positive(double value) {
        return value > 0 ? value : null;
    }

    private static double valueOrZero(Double value) {
        return value == null ? 0 : value;
    }

    public record ReplenishmentPolicyResult(
            double quantity,
            double reservedQty,
            double usableAvailable,
            double nonAvailableQty,
            Double minQty,
            Double sparePartMinStock,
            Double triggerThreshold,
            Double criticalThreshold,
            Double reorderPoint,
            Double reorderQty,
            Double maxQty,
            boolean reorderNeeded,
            NotificationSeverity severity,
            double shortfall,
            double recommendedQuantity,
            String reason
    ) {
        private static ReplenishmentPolicyResult noPolicy(WmsStockSnapshot snapshot) {
            double quantity = snapshot == null ? 0 : snapshot.qtyOnHand().doubleValue();
            double reserved = snapshot == null ? 0 : snapshot.qtyReserved().doubleValue();
            double usableAvailable = snapshot == null ? 0 : snapshot.availableQty().doubleValue();
            double nonAvailableQty = snapshot == null ? 0 : Math.max(snapshot.nonAvailableQty().doubleValue(), 0);
            return new ReplenishmentPolicyResult(
                    quantity,
                    reserved,
                    usableAvailable,
                    nonAvailableQty,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    false,
                    NotificationSeverity.INFO,
                    0,
                    0,
                    null
            );
        }
    }
}
