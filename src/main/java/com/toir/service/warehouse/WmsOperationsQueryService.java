package com.toir.service.warehouse;

import com.toir.dto.warehouse.InventoryCountStatsResponse;
import com.toir.dto.warehouse.WarehouseBinStatsResponse;
import com.toir.dto.warehouse.WarehouseQualityStatsResponse;
import com.toir.dto.warehouse.WarehouseQualityTransferHistoryDto;
import com.toir.dto.warehouse.WarehouseStockMoveJournalDto;
import com.toir.dto.warehouse.WarehouseStockMoveStatsResponse;
import com.toir.dto.warehouse.WarehouseStockStatsResponse;
import com.toir.dto.warehouse.WarehouseTaskDto;
import com.toir.dto.warehouse.WarehouseTaskStatsResponse;
import com.toir.dto.warehouse.WorkOrderWmsHistoryResponse;
import com.toir.dto.warehouse.WarehouseWriteoffRequestViewDto;
import com.toir.dto.warehouse.WarehouseWriteoffStatsResponse;
import com.toir.dto.warehouse.WmsDashboardResponse;
import com.toir.dto.warehouse.WmsLabelHistoryDto;
import com.toir.dto.warehouse.WmsLabelStatsResponse;
import com.toir.entity.SparePart;
import com.toir.entity.StockMovement;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.users.User;
import com.toir.entity.warehouse.Warehouse;
import com.toir.entity.warehouse.WarehouseBin;
import com.toir.entity.warehouse.WarehouseWriteoffRequest;
import com.toir.entity.warehouse.WmsLabelEvent;
import com.toir.enums.InventoryCountSessionStatus;
import com.toir.enums.StockMovementSourceType;
import com.toir.enums.StockMovementType;
import com.toir.enums.WarehouseQualityZoneType;
import com.toir.enums.WarehouseStockStatus;
import com.toir.enums.WarehouseTaskSourceType;
import com.toir.enums.WarehouseTaskStatus;
import com.toir.enums.WarehouseWriteoffStatus;
import com.toir.exception.RestException;
import com.toir.repository.InventoryCountSessionRepository;
import com.toir.repository.SparePartRepository;
import com.toir.repository.StockMovementRepository;
import com.toir.repository.WarehouseBinRepository;
import com.toir.repository.WarehouseRepository;
import com.toir.repository.WarehouseStockBalanceRepository;
import com.toir.repository.WarehouseStockReconciliationRow;
import com.toir.repository.WarehouseTaskRepository;
import com.toir.repository.WarehouseWriteoffRequestRepository;
import com.toir.repository.WmsLabelEventRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.users.UserRepository;
import com.toir.util.PaginationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class WmsOperationsQueryService {

    private final WarehouseTaskRepository taskRepository;
    private final InventoryCountSessionRepository countSessionRepository;
    private final WarehouseBinRepository binRepository;
    private final WarehouseStockBalanceRepository balanceRepository;
    private final StockMovementRepository stockMovementRepository;
    private final WarehouseWriteoffRequestRepository writeoffRepository;
    private final WmsLabelEventRepository labelEventRepository;
    private final WarehouseTaskService taskService;
    private final WarehouseRepository warehouseRepository;
    private final SparePartRepository sparePartRepository;
    private final EquipmentRepository equipmentRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public WarehouseTaskStatsResponse taskStats(UUID warehouseId) {
        return new WarehouseTaskStatsResponse(
                warehouseId == null ? taskRepository.countByIsDeletedFalse() : taskRepository.countByWarehouseIdAndIsDeletedFalse(warehouseId),
                taskRepository.countByStatus(warehouseId, WarehouseTaskStatus.DRAFT),
                taskRepository.countByStatus(warehouseId, WarehouseTaskStatus.OPEN),
                taskRepository.countByStatus(warehouseId, WarehouseTaskStatus.ASSIGNED),
                taskRepository.countByStatus(warehouseId, WarehouseTaskStatus.IN_PROGRESS),
                taskRepository.countByStatus(warehouseId, WarehouseTaskStatus.BLOCKED),
                taskRepository.countByStatus(warehouseId, WarehouseTaskStatus.DONE),
                taskRepository.countByStatus(warehouseId, WarehouseTaskStatus.CANCELLED),
                taskRepository.countOverdue(warehouseId, Instant.now())
        );
    }

    @Transactional(readOnly = true)
    public InventoryCountStatsResponse inventoryCountStats(UUID warehouseId) {
        return new InventoryCountStatsResponse(
                warehouseId == null ? countSessionRepository.countByIsDeletedFalse() : countSessionRepository.countByWarehouseIdAndIsDeletedFalse(warehouseId),
                countSessionRepository.countByStatus(warehouseId, InventoryCountSessionStatus.DRAFT),
                countSessionRepository.countByStatus(warehouseId, InventoryCountSessionStatus.OPEN),
                countSessionRepository.countByStatus(warehouseId, InventoryCountSessionStatus.COUNTING),
                countSessionRepository.countByStatus(warehouseId, InventoryCountSessionStatus.REVIEW),
                countSessionRepository.countByStatus(warehouseId, InventoryCountSessionStatus.APPROVED),
                countSessionRepository.countByStatus(warehouseId, InventoryCountSessionStatus.POSTED),
                countSessionRepository.countByStatus(warehouseId, InventoryCountSessionStatus.CANCELLED),
                countSessionRepository.countBlind(warehouseId)
        );
    }

    @Transactional(readOnly = true)
    public WarehouseBinStatsResponse binStats(UUID warehouseId) {
        return new WarehouseBinStatsResponse(
                binRepository.countAllVisible(warehouseId),
                binRepository.countByActive(warehouseId, true),
                binRepository.countByActive(warehouseId, false),
                binRepository.countBlocked(warehouseId),
                binRepository.countFrozen(warehouseId),
                binRepository.countByQualityZoneType(warehouseId, WarehouseQualityZoneType.STORAGE),
                binRepository.countByQualityZoneType(warehouseId, WarehouseQualityZoneType.RECEIVING),
                binRepository.countByQualityZoneType(warehouseId, WarehouseQualityZoneType.PICKING),
                binRepository.countByQualityZoneType(warehouseId, WarehouseQualityZoneType.QUARANTINE)
        );
    }

    @Transactional(readOnly = true)
    public WarehouseStockStatsResponse stockStats(UUID warehouseId) {
        return new WarehouseStockStatsResponse(
                balanceRepository.countRows(warehouseId),
                balanceRepository.countRowsByStatus(warehouseId, WarehouseStockStatus.AVAILABLE),
                balanceRepository.countRowsByStatus(warehouseId, WarehouseStockStatus.QUARANTINE),
                balanceRepository.countRowsByStatus(warehouseId, WarehouseStockStatus.BLOCKED),
                balanceRepository.countRowsByStatus(warehouseId, WarehouseStockStatus.WRITEOFF_PENDING),
                balanceRepository.sumQtyOnHand(warehouseId),
                balanceRepository.sumQtyReserved(warehouseId),
                balanceRepository.sumAvailableQty(warehouseId)
        );
    }

    @Transactional(readOnly = true)
    public Page<WarehouseStockMoveJournalDto> stockMoves(UUID warehouseId,
                                                         UUID sparePartId,
                                                         UUID binId,
                                                         WarehouseStockStatus stockStatus,
                                                         int page,
                                                         int size) {
        Page<StockMovement> movements = stockMovementRepository.searchWmsStockMoves(
                warehouseId,
                sparePartId,
                binId,
                stockStatus,
                PaginationUtils.pageRequest(page, size)
        );
        Enrichment enrichment = enrichmentForMovements(movements.getContent());
        return movements.map(movement -> WarehouseStockMoveJournalDto.from(
                movement,
                enrichment.warehouseName(movement.getWarehouseId()),
                enrichment.sparePartCode(movement.getSparePartId()),
                enrichment.sparePartName(movement.getSparePartId()),
                enrichment.binCode(movement.getFromBinId()),
                enrichment.binCode(movement.getToBinId())
        ));
    }

    @Transactional(readOnly = true)
    public WarehouseStockMoveStatsResponse stockMoveStats(UUID warehouseId) {
        Instant todayStart = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);
        return new WarehouseStockMoveStatsResponse(
                stockMovementRepository.countByType(warehouseId, StockMovementType.BIN_MOVE),
                stockMovementRepository.countMovesSince(warehouseId, todayStart),
                stockMovementRepository.countMovedSpareParts(warehouseId),
                decimal(stockMovementRepository.sumQuantityByTypeAndSource(warehouseId, StockMovementType.BIN_MOVE, null))
        );
    }

    @Transactional(readOnly = true)
    public Page<WarehouseQualityTransferHistoryDto> qualityTransfers(UUID warehouseId,
                                                                     UUID sparePartId,
                                                                     UUID binId,
                                                                     WarehouseStockStatus toStatus,
                                                                     int page,
                                                                     int size) {
        Page<StockMovement> movements = stockMovementRepository.searchQualityTransfers(
                warehouseId,
                sparePartId,
                binId,
                toStatus,
                PaginationUtils.pageRequest(page, size)
        );
        Enrichment enrichment = enrichmentForMovements(movements.getContent());
        return movements.map(movement -> WarehouseQualityTransferHistoryDto.from(
                movement,
                enrichment.warehouseName(movement.getWarehouseId()),
                enrichment.sparePartCode(movement.getSparePartId()),
                enrichment.sparePartName(movement.getSparePartId()),
                enrichment.binCode(movement.getBinId())
        ));
    }

    @Transactional(readOnly = true)
    public WarehouseQualityStatsResponse qualityStats(UUID warehouseId) {
        return new WarehouseQualityStatsResponse(
                stockMovementRepository.countByTypeAndSourceAndStatus(warehouseId, StockMovementType.TRANSFER, StockMovementSourceType.QUALITY_STATUS_TRANSFER, null),
                stockMovementRepository.countByTypeAndSourceAndStatus(warehouseId, StockMovementType.TRANSFER, StockMovementSourceType.QUALITY_STATUS_TRANSFER, WarehouseStockStatus.QUARANTINE),
                stockMovementRepository.countByTypeAndSourceAndStatus(warehouseId, StockMovementType.TRANSFER, StockMovementSourceType.QUALITY_STATUS_TRANSFER, WarehouseStockStatus.DAMAGED),
                stockMovementRepository.countByTypeAndSourceAndStatus(warehouseId, StockMovementType.TRANSFER, StockMovementSourceType.QUALITY_STATUS_TRANSFER, WarehouseStockStatus.AVAILABLE),
                decimal(stockMovementRepository.sumQuantityByTypeAndSource(warehouseId, StockMovementType.TRANSFER, StockMovementSourceType.QUALITY_STATUS_TRANSFER)),
                stockMovementRepository.maxOccurredAtByTypeAndSource(warehouseId, StockMovementType.TRANSFER, StockMovementSourceType.QUALITY_STATUS_TRANSFER)
        );
    }

    @Transactional(readOnly = true)
    public Page<WarehouseWriteoffRequestViewDto> writeoffs(UUID warehouseId,
                                                           UUID sparePartId,
                                                           WarehouseWriteoffStatus status,
                                                           int page,
                                                           int size) {
        Page<WarehouseWriteoffRequest> writeoffs = writeoffRepository.search(
                warehouseId,
                sparePartId,
                status,
                PaginationUtils.pageRequest(page, size)
        );
        Enrichment enrichment = enrichmentForWriteoffs(writeoffs.getContent());
        return writeoffs.map(request -> writeoffView(request, enrichment));
    }

    @Transactional(readOnly = true)
    public WarehouseWriteoffRequestViewDto writeoff(UUID id) {
        WarehouseWriteoffRequest request = writeoffRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Warehouse writeoff request not found: " + id));
        return writeoffView(request, enrichmentForWriteoffs(List.of(request)));
    }

    @Transactional(readOnly = true)
    public WarehouseWriteoffStatsResponse writeoffStats(UUID warehouseId) {
        return new WarehouseWriteoffStatsResponse(
                writeoffRepository.countVisible(warehouseId),
                writeoffRepository.countByStatus(warehouseId, WarehouseWriteoffStatus.DRAFT),
                writeoffRepository.countByStatus(warehouseId, WarehouseWriteoffStatus.PENDING_APPROVAL),
                writeoffRepository.countByStatus(warehouseId, WarehouseWriteoffStatus.APPROVED),
                writeoffRepository.countByStatus(warehouseId, WarehouseWriteoffStatus.POSTED),
                writeoffRepository.countByStatus(warehouseId, WarehouseWriteoffStatus.REJECTED),
                writeoffRepository.countByStatus(warehouseId, WarehouseWriteoffStatus.CANCELLED),
                writeoffRepository.sumQuantity(warehouseId)
        );
    }

    @Transactional(readOnly = true)
    public Page<WmsLabelHistoryDto> labelHistory(UUID warehouseId, String labelType, int page, int size) {
        Page<WmsLabelEvent> events = labelEventRepository.search(warehouseId, trimToNull(labelType), PaginationUtils.pageRequest(page, size));
        Enrichment enrichment = enrichmentForLabels(events.getContent());
        return events.map(event -> WmsLabelHistoryDto.from(
                event,
                enrichment.warehouseName(event.getWarehouseId()),
                enrichment.binCode(event.getBinId()),
                enrichment.sparePartCode(event.getSparePartId()),
                enrichment.sparePartName(event.getSparePartId()),
                enrichment.equipmentCode(event.getEquipmentId()),
                enrichment.equipmentName(event.getEquipmentId())
        ));
    }

    @Transactional(readOnly = true)
    public WmsLabelStatsResponse labelStats(UUID warehouseId) {
        return new WmsLabelStatsResponse(
                labelEventRepository.countVisible(warehouseId),
                labelEventRepository.countByLabelType(warehouseId, "BIN"),
                labelEventRepository.countByLabelType(warehouseId, "SPARE_PART"),
                labelEventRepository.countByLabelType(warehouseId, "EQUIPMENT"),
                labelEventRepository.findLastPrintedAt(warehouseId)
        );
    }

    @Transactional(readOnly = true)
    public WorkOrderWmsHistoryResponse workOrderHistory(UUID workOrderId) {
        List<WarehouseTaskDto> tasks = taskService.findBySource(WarehouseTaskSourceType.WORK_ORDER, workOrderId, 20);
        List<StockMovement> movements = stockMovementRepository.findAllByWorkOrderIdAndIsDeletedFalseOrderByOccurredAtDesc(workOrderId);
        Enrichment enrichment = enrichmentForMovements(movements);
        List<WarehouseStockMoveJournalDto> movementDtos = movements.stream()
                .map(movement -> WarehouseStockMoveJournalDto.from(
                        movement,
                        enrichment.warehouseName(movement.getWarehouseId()),
                        enrichment.sparePartCode(movement.getSparePartId()),
                        enrichment.sparePartName(movement.getSparePartId()),
                        enrichment.binCode(movement.getFromBinId() == null ? movement.getBinId() : movement.getFromBinId()),
                        enrichment.binCode(movement.getToBinId())
                ))
                .toList();
        return new WorkOrderWmsHistoryResponse(tasks, movementDtos);
    }

    @Transactional(readOnly = true)
    public WmsDashboardResponse dashboard(UUID warehouseId) {
        WarehouseTaskStatsResponse taskStats = taskStats(warehouseId);
        InventoryCountStatsResponse countStats = inventoryCountStats(warehouseId);
        WarehouseBinStatsResponse binStats = binStats(warehouseId);
        WarehouseStockStatsResponse stockStats = stockStats(warehouseId);
        WarehouseQualityStatsResponse qualityStats = qualityStats(warehouseId);
        WarehouseWriteoffStatsResponse writeoffStats = writeoffStats(warehouseId);
        WarehouseStockMoveStatsResponse moveStats = stockMoveStats(warehouseId);
        WmsLabelStatsResponse labelStats = labelStats(warehouseId);
        List<WarehouseTaskDto> recentTasks = taskService.findAll(null, null, warehouseId, null, 0, 5).getContent();
        List<WarehouseStockMoveJournalDto> recentMoves = stockMoves(warehouseId, null, null, null, 0, 5).getContent();
        List<WarehouseQualityTransferHistoryDto> recentQuality = qualityTransfers(warehouseId, null, null, null, 0, 5).getContent();
        List<WmsDashboardResponse.ReconciliationAlert> alerts = reconciliationAlerts(warehouseId);
        return new WmsDashboardResponse(
                healthScore(taskStats, alerts),
                taskStats,
                countStats,
                binStats,
                stockStats,
                qualityStats,
                writeoffStats,
                moveStats,
                labelStats,
                recentTasks,
                recentMoves,
                recentQuality,
                alerts
        );
    }

    private int healthScore(WarehouseTaskStatsResponse stats, List<WmsDashboardResponse.ReconciliationAlert> alerts) {
        long penalty = stats.overdue() * 8 + stats.blocked() * 6 + alerts.size() * 10;
        if (stats.open() + stats.assigned() + stats.inProgress() > 50) {
            penalty += 8;
        }
        return Math.max(0, Math.min(100, (int) (100 - penalty)));
    }

    private List<WmsDashboardResponse.ReconciliationAlert> reconciliationAlerts(UUID warehouseId) {
        if (warehouseId == null) {
            return List.of();
        }
        return balanceRepository.reconcileWarehouseStock(warehouseId).stream()
                .map(this::toAlertOrNull)
                .filter(Objects::nonNull)
                .limit(5)
                .toList();
    }

    private WmsDashboardResponse.ReconciliationAlert toAlertOrNull(WarehouseStockReconciliationRow row) {
        BigDecimal onHandVariance = zero(row.getWmsQtyOnHand()).subtract(zero(row.getLegacyQtyOnHand())).abs();
        BigDecimal reservedVariance = zero(row.getWmsQtyReserved()).subtract(zero(row.getLegacyQtyReserved())).abs();
        BigDecimal variance = onHandVariance.max(reservedVariance);
        if (variance.compareTo(BigDecimal.ZERO) == 0 && row.getLegacyPresent() == row.getWmsPresent()) {
            return null;
        }
        return new WmsDashboardResponse.ReconciliationAlert(
                variance.compareTo(BigDecimal.ZERO) > 0 ? "warning" : "info",
                "Stock reconciliation drift",
                "sparePartId=" + row.getSparePartId() + ", status=" + row.getStockStatus(),
                variance
        );
    }

    private WarehouseWriteoffRequestViewDto writeoffView(WarehouseWriteoffRequest request, Enrichment enrichment) {
        return WarehouseWriteoffRequestViewDto.from(
                request,
                enrichment.warehouseName(request.getWarehouseId()),
                enrichment.sparePartCode(request.getSparePartId()),
                enrichment.sparePartName(request.getSparePartId()),
                enrichment.binCode(request.getBinId()),
                enrichment.userName(request.getRequestedById()),
                enrichment.userName(request.getApprovedById())
        );
    }

    private Enrichment enrichmentForMovements(List<StockMovement> movements) {
        Set<UUID> warehouseIds = movements.stream().map(StockMovement::getWarehouseId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<UUID> sparePartIds = movements.stream().map(StockMovement::getSparePartId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<UUID> binIds = movements.stream()
                .flatMap(movement -> Stream.of(movement.getBinId(), movement.getFromBinId(), movement.getToBinId()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return new Enrichment(warehouses(warehouseIds), spareParts(sparePartIds), bins(binIds), Map.of(), Map.of());
    }

    private Enrichment enrichmentForWriteoffs(List<WarehouseWriteoffRequest> writeoffs) {
        Set<UUID> warehouseIds = writeoffs.stream().map(WarehouseWriteoffRequest::getWarehouseId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<UUID> sparePartIds = writeoffs.stream().map(WarehouseWriteoffRequest::getSparePartId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<UUID> binIds = writeoffs.stream().map(WarehouseWriteoffRequest::getBinId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<UUID> userIds = writeoffs.stream()
                .flatMap(writeoff -> Stream.of(writeoff.getRequestedById(), writeoff.getApprovedById()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return new Enrichment(warehouses(warehouseIds), spareParts(sparePartIds), bins(binIds), users(userIds), Map.of());
    }

    private Enrichment enrichmentForLabels(List<WmsLabelEvent> events) {
        Set<UUID> warehouseIds = events.stream().map(WmsLabelEvent::getWarehouseId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<UUID> sparePartIds = events.stream().map(WmsLabelEvent::getSparePartId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<UUID> binIds = events.stream().map(WmsLabelEvent::getBinId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<UUID> equipmentIds = events.stream().map(WmsLabelEvent::getEquipmentId).filter(Objects::nonNull).collect(Collectors.toSet());
        return new Enrichment(warehouses(warehouseIds), spareParts(sparePartIds), bins(binIds), Map.of(), equipment(equipmentIds));
    }

    private Map<UUID, Warehouse> warehouses(Collection<UUID> ids) {
        return ids.isEmpty() ? Map.of() : warehouseRepository.findAllByIdInAndIsDeletedFalse(ids).stream()
                .collect(Collectors.toMap(Warehouse::getId, Function.identity(), (first, second) -> first));
    }

    private Map<UUID, SparePart> spareParts(Collection<UUID> ids) {
        return ids.isEmpty() ? Map.of() : sparePartRepository.findAllByIdInAndIsDeletedFalse(ids).stream()
                .collect(Collectors.toMap(SparePart::getId, Function.identity(), (first, second) -> first));
    }

    private Map<UUID, WarehouseBin> bins(Collection<UUID> ids) {
        return ids.isEmpty() ? Map.of() : binRepository.findAllByIdInAndIsDeletedFalse(ids).stream()
                .collect(Collectors.toMap(WarehouseBin::getId, Function.identity(), (first, second) -> first));
    }

    private Map<UUID, User> users(Collection<UUID> ids) {
        return ids.isEmpty() ? Map.of() : userRepository.findAllByIdInAndIsDeletedFalse(ids).stream()
                .collect(Collectors.toMap(User::getId, Function.identity(), (first, second) -> first));
    }

    private Map<UUID, Equipment> equipment(Collection<UUID> ids) {
        return ids.isEmpty() ? Map.of() : equipmentRepository.findAllByIdInAndIsDeletedFalse(ids).stream()
                .collect(Collectors.toMap(Equipment::getId, Function.identity(), (first, second) -> first));
    }

    private BigDecimal decimal(Double value) {
        return BigDecimal.valueOf(value == null ? 0 : value);
    }

    private BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record Enrichment(
            Map<UUID, Warehouse> warehouses,
            Map<UUID, SparePart> spareParts,
            Map<UUID, WarehouseBin> bins,
            Map<UUID, User> users,
            Map<UUID, Equipment> equipment
    ) {
        String warehouseName(UUID id) {
            Warehouse warehouse = id == null ? null : warehouses.get(id);
            return warehouse == null ? null : warehouse.getName();
        }

        String sparePartCode(UUID id) {
            SparePart sparePart = id == null ? null : spareParts.get(id);
            return sparePart == null ? null : sparePart.getCode();
        }

        String sparePartName(UUID id) {
            SparePart sparePart = id == null ? null : spareParts.get(id);
            return sparePart == null ? null : sparePart.getName();
        }

        String binCode(UUID id) {
            WarehouseBin bin = id == null ? null : bins.get(id);
            return bin == null ? null : bin.getCode();
        }

        String userName(UUID id) {
            User user = id == null ? null : users.get(id);
            return user == null ? null : user.getFullName();
        }

        String equipmentCode(UUID id) {
            Equipment item = id == null ? null : equipment.get(id);
            return item == null ? null : item.getCode();
        }

        String equipmentName(UUID id) {
            Equipment item = id == null ? null : equipment.get(id);
            return item == null ? null : item.getName();
        }
    }
}
