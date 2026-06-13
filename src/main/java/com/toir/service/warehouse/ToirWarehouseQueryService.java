package com.toir.service.warehouse;

import com.toir.dto.warehouse.WarehouseStockBalanceDto;
import com.toir.dto.warehouse.WarehouseStockLedgerDto;
import com.toir.exception.RestException;
import com.toir.repository.WarehouseRepository;
import com.toir.repository.WarehouseStockBalanceRepository;
import com.toir.repository.WarehouseStockLedgerRepository;
import com.toir.util.PaginationUtils;
import java.util.UUID;
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
        requireWarehouse(warehouseId);
        return balanceRepository.findAllByWarehouseIdAndIsDeletedFalseOrderByUpdatedAtDesc(
                warehouseId,
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

    private void requireWarehouse(UUID warehouseId) {
        if (warehouseId == null || !warehouseRepository.existsByIdAndIsDeletedFalse(warehouseId)) {
            throw RestException.notFound("Warehouse not found: " + warehouseId);
        }
    }
}
