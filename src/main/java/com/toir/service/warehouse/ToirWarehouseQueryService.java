package com.toir.service.warehouse;

import com.toir.dto.warehouse.WarehouseStockBalanceDto;
import com.toir.dto.warehouse.WarehouseStockMoveJournalDto;
import com.toir.dto.warehouse.WarehouseStockMoveStatsResponse;
import com.toir.dto.warehouse.WarehouseStockLedgerDto;
import com.toir.dto.warehouse.WarehouseStockReconciliationDto;
import com.toir.dto.warehouse.WarehouseStockStatsResponse;
import com.toir.entity.SparePart;
import com.toir.entity.StockMovement;
import com.toir.entity.warehouse.Warehouse;
import com.toir.enums.WarehouseStockStatus;
import com.toir.exception.RestException;
import com.toir.repository.SparePartRepository;
import com.toir.repository.StockMovementRepository;
import com.toir.repository.WarehouseRepository;
import com.toir.repository.WarehouseStockBalanceRepository;
import com.toir.repository.WarehouseStockLedgerRepository;
import com.toir.util.PaginationUtils;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ToirWarehouseQueryService {

    private final WarehouseRepository warehouseRepository;
    private final WarehouseStockBalanceRepository balanceRepository;
    private final WarehouseStockLedgerRepository ledgerRepository;
    private final StockMovementRepository movementRepository;
    private final SparePartRepository sparePartRepository;

    @Transactional(readOnly = true)
    public Page<WarehouseStockBalanceDto> stockBalances(UUID warehouseId, int page, int size) {
        return stockBalances(warehouseId, null, null, null, null, null, page, size);
    }

    @Transactional(readOnly = true)
    public Page<WarehouseStockBalanceDto> stockBalances(UUID warehouseId,
                                                        UUID binId,
                                                        UUID sparePartId,
                                                        WarehouseStockStatus stockStatus,
                                                        String lotNumber,
                                                        String serialNumber,
                                                        int page,
                                                        int size) {
        requireWarehouse(warehouseId);
        return balanceRepository.search(
                warehouseId,
                binId,
                sparePartId,
                stockStatus,
                trimToNull(lotNumber),
                trimToNull(serialNumber),
                PaginationUtils.pageRequest(page, size)
        ).map(WarehouseStockBalanceDto::from);
    }

    @Transactional(readOnly = true)
    public Page<WarehouseStockLedgerDto> stockLedgers(UUID warehouseId, int page, int size) {
        requireWarehouse(warehouseId);
        return ledgerRepository.findAllByWarehouseIdAndIsDeletedFalseOrderByPostedAtDesc(
                warehouseId,
                PaginationUtils.pageRequest(page, size)
        ).map(WarehouseStockLedgerDto::from);
    }


    @Transactional(readOnly = true)
    public WarehouseStockStatsResponse stockStats(UUID warehouseId) {
        requireWarehouse(warehouseId);
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
                                                          WarehouseStockStatus stockStatus,
                                                          int page,
                                                          int size) {
        Page<StockMovement> movements = movementRepository.searchStockMoves(
                warehouseId,
                sparePartId,
                stockStatus,
                PaginationUtils.pageRequest(page, size)
        );
        Map<UUID, Warehouse> warehouses = warehouses(movements.getContent().stream()
                .map(StockMovement::getWarehouseId)
                .toList());
        Map<UUID, SparePart> spareParts = spareParts(movements.getContent().stream()
                .map(StockMovement::getSparePartId)
                .toList());
        return movements.map(movement -> {
            Warehouse warehouse = warehouses.get(movement.getWarehouseId());
            SparePart sparePart = spareParts.get(movement.getSparePartId());
            return WarehouseStockMoveJournalDto.from(
                    movement,
                    warehouse == null ? null : warehouse.getName(),
                    sparePart == null ? null : sparePart.getCode(),
                    sparePart == null ? null : sparePart.getName(),
                    null,
                    null
            );
        });
    }

    @Transactional(readOnly = true)
    public WarehouseStockMoveStatsResponse stockMoveStats(UUID warehouseId) {
        if (warehouseId != null) {
            requireWarehouse(warehouseId);
        }
        Instant todayStart = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);
        Double totalQuantity = movementRepository.sumStockMoveQuantity(warehouseId);
        return new WarehouseStockMoveStatsResponse(
                movementRepository.countStockMoves(warehouseId),
                movementRepository.countStockMovesSince(warehouseId, todayStart),
                movementRepository.countMovedSparePartsAll(warehouseId),
                BigDecimal.valueOf(totalQuantity == null ? 0.0d : totalQuantity)
        );
    }

    @Transactional(readOnly = true)
    public List<WarehouseStockReconciliationDto> stockReconciliation(UUID warehouseId) {
        requireWarehouse(warehouseId);
        return balanceRepository.reconcileWarehouseStock(warehouseId).stream()
                .map(WarehouseStockReconciliationDto::from)
                .toList();
    }


    private Map<UUID, Warehouse> warehouses(Collection<UUID> ids) {
        List<UUID> filtered = ids.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (filtered.isEmpty()) {
            return Map.of();
        }
        return warehouseRepository.findAllByIdInAndIsDeletedFalse(filtered).stream()
                .collect(Collectors.toMap(Warehouse::getId, Function.identity()));
    }

    private Map<UUID, SparePart> spareParts(Collection<UUID> ids) {
        List<UUID> filtered = ids.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (filtered.isEmpty()) {
            return Map.of();
        }
        return sparePartRepository.findAllByIdInAndIsDeletedFalse(filtered).stream()
                .collect(Collectors.toMap(SparePart::getId, Function.identity()));
    }

    private void requireWarehouse(UUID warehouseId) {
        if (warehouseId == null || !warehouseRepository.existsByIdAndIsDeletedFalse(warehouseId)) {
            throw RestException.notFound("Warehouse not found: " + warehouseId);
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
