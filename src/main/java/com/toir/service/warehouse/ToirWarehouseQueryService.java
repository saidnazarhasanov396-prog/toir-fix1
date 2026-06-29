package com.toir.service.warehouse;

import com.toir.dto.warehouse.WarehouseStockBalanceDto;
import com.toir.dto.warehouse.WarehouseStockLedgerDto;
import com.toir.dto.warehouse.WarehouseStockReconciliationDto;
import com.toir.enums.WarehouseStockStatus;
import com.toir.exception.RestException;
import com.toir.repository.WarehouseRepository;
import com.toir.repository.WarehouseStockBalanceRepository;
import com.toir.repository.WarehouseStockLedgerRepository;
import com.toir.util.PaginationUtils;
import java.util.UUID;
import java.util.List;
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
    public List<WarehouseStockReconciliationDto> stockReconciliation(UUID warehouseId) {
        requireWarehouse(warehouseId);
        return balanceRepository.reconcileWarehouseStock(warehouseId).stream()
                .map(WarehouseStockReconciliationDto::from)
                .toList();
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
