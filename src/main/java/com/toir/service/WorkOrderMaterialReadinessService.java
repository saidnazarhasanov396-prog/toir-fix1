package com.toir.service;

import com.toir.dto.workorder.WorkOrderMaterialReadinessDto;
import com.toir.dto.workorder.WorkOrderMaterialReadinessRowDto;
import com.toir.entity.Reservation;
import com.toir.entity.SparePart;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.maintenance.WorkOrderSparePartRequirement;
import com.toir.entity.repair.RepairMaterialUsage;
import com.toir.entity.warehouse.RepairMaterialReturn;
import com.toir.entity.warehouse.Warehouse;
import com.toir.entity.warehouse.WarehouseStockBalance;
import com.toir.enums.MaterialReadinessStatus;
import com.toir.enums.RepairMaterialReturnStatus;
import com.toir.enums.ReservationStatus;
import com.toir.exception.RestException;
import com.toir.repository.RepairMaterialReturnRepository;
import com.toir.repository.ReservationRepository;
import com.toir.repository.PurchaseOrderRepository;
import com.toir.repository.WarehouseRepository;
import com.toir.repository.WarehouseStockBalanceRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.maintenance.WorkOrderSparePartRequirementRepository;
import com.toir.repository.repair.RepairMaterialUsageRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkOrderMaterialReadinessService {

    private final WorkOrderRepository workOrderRepository;
    private final WorkOrderSparePartRequirementRepository requirementRepository;
    private final ReservationRepository reservationRepository;
    private final RepairMaterialUsageRepository materialUsageRepository;
    private final RepairMaterialReturnRepository materialReturnRepository;
    private final WarehouseStockBalanceRepository warehouseStockBalanceRepository;
    private final WarehouseRepository warehouseRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;

    @Transactional(readOnly = true)
    public WorkOrderMaterialReadinessDto getReadiness(UUID workOrderId) {
        WorkOrder workOrder = workOrderRepository.findByIdAndIsDeletedFalse(workOrderId)
                .orElseThrow(() -> RestException.notFound("Work order not found: " + workOrderId));
        List<WorkOrderSparePartRequirement> requirements =
                requirementRepository.findActiveByWorkOrderId(workOrderId);
        List<Reservation> reservations =
                reservationRepository.findAllByWorkOrderIdAndStatusAndIsDeletedFalseOrderByUpdatedAtDesc(
                        workOrderId,
                        ReservationStatus.ACTIVE
                );
        List<RepairMaterialUsage> usages =
                materialUsageRepository.findAllByWorkOrderIdAndIsDeletedFalseOrderByUpdatedAtDesc(workOrderId);
        List<RepairMaterialReturn> returns =
                materialReturnRepository.findAllByWorkOrderIdAndIsDeletedFalseOrderByUpdatedAtDesc(workOrderId);

        Map<UUID, RepairMaterialUsage> usageById = usages.stream()
                .filter(usage -> usage.getId() != null)
                .collect(Collectors.toMap(RepairMaterialUsage::getId, Function.identity(), (left, right) -> left));

        List<WorkOrderMaterialReadinessRowDto> rows = requirements.stream()
                .map(requirement -> row(workOrder, requirement, reservations, usages, returns, usageById))
                .toList();
        MaterialReadinessStatus overallStatus = overallStatus(rows);
        boolean blocking = rows.stream().anyMatch(WorkOrderMaterialReadinessRowDto::blocking);

        return new WorkOrderMaterialReadinessDto(
                workOrderId,
                workOrder.getEquipmentId(),
                overallStatus,
                blocking,
                Instant.now(),
                rows
        );
    }

    private WorkOrderMaterialReadinessRowDto row(
            WorkOrder workOrder,
            WorkOrderSparePartRequirement requirement,
            List<Reservation> reservations,
            List<RepairMaterialUsage> usages,
            List<RepairMaterialReturn> returns,
            Map<UUID, RepairMaterialUsage> usageById
    ) {
        UUID requirementId = requirement.getId();
        UUID sparePartId = sparePartId(requirement);
        BigDecimal requiredQty = positive(requirement.getRequiredQty());
        BigDecimal reservedQty = reservations.stream()
                .filter(reservation -> matchesRequirementOrFallback(
                        reservation.getRequirementId(),
                        reservation.getWorkOrderId(),
                        reservation.getSparePartId(),
                        requirement
                ))
                .map(Reservation::getQuantity).map(this::positive).reduce(BigDecimal.ZERO,BigDecimal::add);
        BigDecimal issuedQty = usages.stream()
                .filter(usage -> matchesRequirementOrFallback(
                        usage.getRequirementId(),
                        usage.getWorkOrderId(),
                        usage.getSparePartId(),
                        requirement
                ))
                .map(RepairMaterialUsage::getQuantity).map(this::positive).reduce(BigDecimal.ZERO,BigDecimal::add);
        BigDecimal returnedQty = returns.stream()
                .filter(returned -> returned.getStatus() == null
                        || returned.getStatus() == RepairMaterialReturnStatus.POSTED)
                .filter(returned -> matchesReturn(returned, requirement, usageById))
                .map(RepairMaterialReturn::getQuantity).map(this::positive).reduce(BigDecimal.ZERO,BigDecimal::add);

        BigDecimal netIssued = issuedQty.subtract(returnedQty).max(BigDecimal.ZERO);
        BigDecimal coveredQty = reservedQty.add(netIssued);
        BigDecimal remainingQty = requiredQty.subtract(coveredQty).max(BigDecimal.ZERO);
        UUID preferredWarehouseId = requirement.getWarehouseId() == null
                ? workOrder.getWarehouseId()
                : requirement.getWarehouseId();
        StockSnapshot stock = stockSnapshot(sparePartId, preferredWarehouseId);
        ReadinessDecision decision = decide(requiredQty, netIssued, coveredQty, remainingQty, stock,
                preferredWarehouseId);
        LocalDate expectedDeliveryDate = remainingQty.signum() <= 0
                ? null
                : expectedDeliveryDate(
                        preferredWarehouseId == null ? stock.sourceWarehouseId() : preferredWarehouseId,
                        sparePartId
                );
        SparePart sparePart = requirement.getSparePart();

        return new WorkOrderMaterialReadinessRowDto(
                requirementId,
                sparePartId,
                sparePart == null ? null : sparePart.getName(),
                requirement.getUnit(),
                stock.sourceWarehouseId(),
                stock.sourceWarehouseCode(),
                stock.sourceWarehouseName(),
                stock.onHandQty(),
                stock.wmsReservedQty(),
                stock.availableQty(),
                requiredQty,
                reservedQty,
                issuedQty,
                returnedQty,
                decision.shortageQty(),
                decision.status(),
                decision.blocking(),
                expectedDeliveryDate,
                "TOIR_WMS",
                stock.lastUpdatedAt(),
                decision.nextAction(),
                decision.nextActionQty(),
                requirement.getNotes()
        );
    }

    private LocalDate expectedDeliveryDate(UUID warehouseId, UUID sparePartId) {
        if (warehouseId == null || sparePartId == null) {
            return null;
        }
        try {
            return purchaseOrderRepository.findEarliestExpectedDeliveryDate(warehouseId, sparePartId);
        } catch (RuntimeException error) {
            log.warn("Purchase order ETA is unavailable for warehouseId={} sparePartId={}",
                    warehouseId, sparePartId, error);
            return null;
        }
    }

    private StockSnapshot stockSnapshot(UUID sparePartId, UUID preferredWarehouseId) {
        if (sparePartId == null) {
            return StockSnapshot.empty();
        }
        List<WarehouseStockBalance> balances;
        try {
            balances = warehouseStockBalanceRepository.findAllBySparePartIdAndIsDeletedFalse(sparePartId);
        } catch (RuntimeException error) {
            log.warn("WMS stock balances are unavailable for sparePartId={}", sparePartId, error);
            return StockSnapshot.unavailable();
        }
        Instant lastUpdatedAt = balances.stream()
                .map(WarehouseStockBalance::getUpdatedAt)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElseGet(Instant::now);
        Map<UUID, WarehouseTotals> totalsByWarehouse = new LinkedHashMap<>();
        for (WarehouseStockBalance balance : balances) {
            if (balance.getWarehouseId() == null) {
                continue;
            }
            totalsByWarehouse.merge(
                    balance.getWarehouseId(),
                    WarehouseTotals.from(balance),
                    WarehouseTotals::add
            );
        }
        BigDecimal totalAvailable = totalsByWarehouse.values().stream()
                .map(WarehouseTotals::availableQty)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        WarehouseTotals preferred = totalsByWarehouse.get(preferredWarehouseId);
        WarehouseTotals best = totalsByWarehouse.values().stream()
                .max(Comparator.comparing(WarehouseTotals::availableQty))
                .orElse(null);
        WarehouseTotals source = preferred != null && preferred.availableQty().signum() > 0 ? preferred : best;
        if (source == null) {
            source = preferred;
        }

        Map<UUID, Warehouse> warehouses = totalsByWarehouse.isEmpty()
                ? Map.of()
                : warehouseRepository.findAllByIdInAndIsDeletedFalse(List.copyOf(totalsByWarehouse.keySet())).stream()
                        .collect(Collectors.toMap(Warehouse::getId, Function.identity(), (left, right) -> left));
        Warehouse sourceWarehouse = source == null ? null : warehouses.get(source.warehouseId());
        return new StockSnapshot(
                source == null ? null : source.warehouseId(),
                sourceWarehouse == null ? null : sourceWarehouse.getCode(),
                sourceWarehouse == null ? null : sourceWarehouse.getName(),
                source == null ? BigDecimal.ZERO : source.onHandQty(),
                source == null ? BigDecimal.ZERO : source.reservedQty(),
                source == null ? BigDecimal.ZERO : source.availableQty(),
                preferred == null ? BigDecimal.ZERO : preferred.availableQty(),
                totalAvailable,
                lastUpdatedAt,
                true
        );
    }

    private ReadinessDecision decide(
            BigDecimal requiredQty,
            BigDecimal netIssued,
            BigDecimal coveredQty,
            BigDecimal remainingQty,
            StockSnapshot stock,
            UUID preferredWarehouseId
    ) {
        if (requiredQty.signum() <= 0) {
            return ReadinessDecision.done(MaterialReadinessStatus.NOT_REQUIRED);
        }
        if (netIssued.compareTo(requiredQty) >= 0) {
            return ReadinessDecision.done(MaterialReadinessStatus.ISSUED);
        }
        if (coveredQty.compareTo(requiredQty) >= 0) {
            return ReadinessDecision.done(MaterialReadinessStatus.RESERVED);
        }
        if (!stock.wmsAvailable()) {
            return ReadinessDecision.action(MaterialReadinessStatus.WMS_UNAVAILABLE, remainingQty,
                    "NONE", BigDecimal.ZERO);
        }
        BigDecimal actualShortage = remainingQty.subtract(stock.totalAvailableQty()).max(BigDecimal.ZERO);
        if (preferredWarehouseId == null && stock.availableQty().compareTo(remainingQty) >= 0) {
            return ReadinessDecision.action(MaterialReadinessStatus.AVAILABLE, actualShortage,
                    "RESERVE", remainingQty);
        }
        if (preferredWarehouseId != null && stock.preferredAvailableQty().compareTo(remainingQty) >= 0) {
            return ReadinessDecision.action(MaterialReadinessStatus.AVAILABLE, actualShortage,
                    "RESERVE", remainingQty);
        }
        if (stock.totalAvailableQty().compareTo(remainingQty) >= 0) {
            return ReadinessDecision.action(MaterialReadinessStatus.TRANSFER_REQUIRED, BigDecimal.ZERO,
                    "TRANSFER", remainingQty);
        }
        if (stock.totalAvailableQty().signum() > 0) {
            return ReadinessDecision.action(MaterialReadinessStatus.PARTIALLY_AVAILABLE, actualShortage,
                    "CREATE_PROCUREMENT", actualShortage);
        }
        return ReadinessDecision.action(MaterialReadinessStatus.PROCUREMENT_REQUIRED, remainingQty,
                "CREATE_PROCUREMENT", remainingQty);
    }

    private boolean matchesRequirementOrFallback(
            UUID actualRequirementId,
            UUID actualWorkOrderId,
            UUID actualSparePartId,
            WorkOrderSparePartRequirement requirement
    ) {
        if (actualRequirementId != null) {
            return actualRequirementId.equals(requirement.getId());
        }
        return Objects.equals(actualWorkOrderId, requirement.getWorkOrderId())
                && Objects.equals(actualSparePartId, sparePartId(requirement));
    }

    private boolean matchesReturn(
            RepairMaterialReturn returned,
            WorkOrderSparePartRequirement requirement,
            Map<UUID, RepairMaterialUsage> usageById
    ) {
        RepairMaterialUsage usage = returned.getMaterialUsageId() == null
                ? null
                : usageById.get(returned.getMaterialUsageId());
        if (usage != null) {
            return matchesRequirementOrFallback(
                    usage.getRequirementId(),
                    usage.getWorkOrderId(),
                    usage.getSparePartId(),
                    requirement
            );
        }
        return Objects.equals(returned.getWorkOrderId(), requirement.getWorkOrderId())
                && Objects.equals(returned.getSparePartId(), sparePartId(requirement));
    }

    private MaterialReadinessStatus overallStatus(List<WorkOrderMaterialReadinessRowDto> rows) {
        if (rows.isEmpty()) {
            return MaterialReadinessStatus.NOT_REQUIRED;
        }
        MaterialReadinessStatus[] blockingPriority = {
                MaterialReadinessStatus.WMS_UNAVAILABLE,
                MaterialReadinessStatus.RECONCILIATION_REQUIRED,
                MaterialReadinessStatus.PROCUREMENT_REQUIRED,
                MaterialReadinessStatus.SHORTAGE,
                MaterialReadinessStatus.TRANSFER_REQUIRED,
                MaterialReadinessStatus.PARTIALLY_AVAILABLE,
                MaterialReadinessStatus.PARTIAL,
                MaterialReadinessStatus.AVAILABLE
        };
        for (MaterialReadinessStatus status : blockingPriority) {
            if (rows.stream().anyMatch(row -> row.blocking() && row.readinessStatus() == status)) {
                return status;
            }
        }
        if (rows.stream().allMatch(row -> row.readinessStatus() == MaterialReadinessStatus.ISSUED)) {
            return MaterialReadinessStatus.ISSUED;
        }
        boolean allReservedIssuedOrNotRequired = rows.stream().allMatch(row ->
                row.readinessStatus() == MaterialReadinessStatus.RESERVED
                        || row.readinessStatus() == MaterialReadinessStatus.READY
                        || row.readinessStatus() == MaterialReadinessStatus.ISSUED
                        || row.readinessStatus() == MaterialReadinessStatus.NOT_REQUIRED);
        boolean anyReservedOrIssued = rows.stream().anyMatch(row ->
                row.readinessStatus() == MaterialReadinessStatus.RESERVED
                        || row.readinessStatus() == MaterialReadinessStatus.READY
                        || row.readinessStatus() == MaterialReadinessStatus.ISSUED);
        if (allReservedIssuedOrNotRequired && anyReservedOrIssued) {
            return MaterialReadinessStatus.RESERVED;
        }
        if (rows.stream().allMatch(row -> row.readinessStatus() == MaterialReadinessStatus.NOT_REQUIRED)) {
            return MaterialReadinessStatus.NOT_REQUIRED;
        }
        if (rows.stream().anyMatch(row -> row.readinessStatus() == MaterialReadinessStatus.WAITING_WMS)) {
            return MaterialReadinessStatus.WAITING_WMS;
        }
        return MaterialReadinessStatus.UNKNOWN;
    }

    private UUID sparePartId(WorkOrderSparePartRequirement requirement) {
        if (requirement.getSparePartId() != null) {
            return requirement.getSparePartId();
        }
        return requirement.getSparePart() == null ? null : requirement.getSparePart().getId();
    }

    private BigDecimal positive(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.max(BigDecimal.ZERO);
    }

    private record WarehouseTotals(
            UUID warehouseId,
            BigDecimal onHandQty,
            BigDecimal reservedQty,
            BigDecimal availableQty
    ) {
        static WarehouseTotals from(WarehouseStockBalance balance) {
            return new WarehouseTotals(
                    balance.getWarehouseId(),
                    zero(balance.getQtyOnHand()),
                    zero(balance.getQtyReserved()),
                    zero(balance.getAvailableQty()).max(BigDecimal.ZERO)
            );
        }

        WarehouseTotals add(WarehouseTotals other) {
            return new WarehouseTotals(
                    warehouseId,
                    onHandQty.add(other.onHandQty),
                    reservedQty.add(other.reservedQty),
                    availableQty.add(other.availableQty)
            );
        }

        private static BigDecimal zero(BigDecimal value) {
            return value == null ? BigDecimal.ZERO : value;
        }
    }

    private record StockSnapshot(
            UUID sourceWarehouseId,
            String sourceWarehouseCode,
            String sourceWarehouseName,
            BigDecimal onHandQty,
            BigDecimal wmsReservedQty,
            BigDecimal availableQty,
            BigDecimal preferredAvailableQty,
            BigDecimal totalAvailableQty,
            Instant lastUpdatedAt,
            boolean wmsAvailable
    ) {
        static StockSnapshot empty() {
            return new StockSnapshot(null, null, null, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, Instant.now(), true);
        }

        static StockSnapshot unavailable() {
            return new StockSnapshot(null, null, null, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, false);
        }
    }

    private record ReadinessDecision(
            MaterialReadinessStatus status,
            boolean blocking,
            BigDecimal shortageQty,
            String nextAction,
            BigDecimal nextActionQty
    ) {
        static ReadinessDecision done(MaterialReadinessStatus status) {
            return new ReadinessDecision(status, false, BigDecimal.ZERO, "NONE", BigDecimal.ZERO);
        }

        static ReadinessDecision action(
                MaterialReadinessStatus status,
                BigDecimal shortageQty,
                String nextAction,
                BigDecimal nextActionQty
        ) {
            return new ReadinessDecision(status, true, shortageQty, nextAction, nextActionQty);
        }
    }
}
