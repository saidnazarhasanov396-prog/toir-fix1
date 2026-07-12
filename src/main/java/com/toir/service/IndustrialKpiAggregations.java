package com.toir.service;

import com.toir.entity.DowntimeEvent;
import com.toir.entity.ReliabilityMetric;
import com.toir.entity.SparePart;
import com.toir.entity.StockMovement;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.repair.RepairRequest;
import com.toir.enums.PriorityLevel;
import com.toir.enums.RequestStatus;
import com.toir.enums.WorkOrderStatus;
import com.toir.enums.WorkType;

import java.time.Duration;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

final class IndustrialKpiAggregations {

    private IndustrialKpiAggregations() {
    }

    static boolean isEmergencyRequest(RepairRequest request) {
        return request != null && request.getPriority() == PriorityLevel.EMERGENCY;
    }

    static boolean isActiveEmergencyRequest(RepairRequest request) {
        return isEmergencyRequest(request)
                && request.getStatus() != RequestStatus.CLOSED
                && request.getStatus() != RequestStatus.CANCELLED;
    }

    static boolean isCompletedRepair(WorkOrder workOrder) {
        return isRepairWorkOrder(workOrder)
                && (workOrder.getStatus() == WorkOrderStatus.COMPLETED
                || workOrder.getStatus() == WorkOrderStatus.CLOSED);
    }

    static boolean isRepairWorkOrder(WorkOrder workOrder) {
        return workOrder != null
                && workOrder.getWorkType() == WorkType.REPAIR
                && workOrder.getStatus() != WorkOrderStatus.CANCELLED;
    }

    static long downtimeMinutes(DowntimeEvent event) {
        if (event == null) {
            return 0;
        }
        if (event.getDurationMinutes() != null) {
            return Math.max(event.getDurationMinutes(), 0);
        }
        if (event.getStartAt() != null && event.getEndAt() != null) {
            return Math.max(Duration.between(event.getStartAt(), event.getEndAt()).toMinutes(), 0);
        }
        return 0;
    }

    static List<ReliabilityMetric> latestReliabilityMetrics(Collection<ReliabilityMetric> metrics) {
        if (metrics == null || metrics.isEmpty()) {
            return List.of();
        }
        Comparator<ReliabilityMetric> byMetricDate = Comparator.comparing(
                ReliabilityMetric::getMetricDate,
                Comparator.nullsFirst(Comparator.naturalOrder())
        );
        return metrics.stream()
                .filter(Objects::nonNull)
                .filter(metric -> metric.getEquipmentId() != null)
                .collect(Collectors.toMap(
                        ReliabilityMetric::getEquipmentId,
                        metric -> metric,
                        (left, right) -> byMetricDate.compare(left, right) >= 0 ? left : right
                ))
                .values().stream()
                .toList();
    }

    static BigDecimal stockIssueCost(StockMovement movement, SparePart sparePart) {
        if (movement.getTotalAmount() != null) {
            return movement.getTotalAmount();
        }
        BigDecimal unitCost = movement.getUnitCost() != null
                ? BigDecimal.valueOf(movement.getUnitCost())
                : sparePart != null && sparePart.getAverageCost() != null
                ? sparePart.getAverageCost()
                : BigDecimal.ZERO;
        return movement.getQuantity().multiply(unitCost);
    }
}
