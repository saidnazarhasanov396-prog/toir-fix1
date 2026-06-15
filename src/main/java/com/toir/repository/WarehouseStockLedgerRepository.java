package com.toir.repository;

import com.toir.entity.warehouse.WarehouseStockLedger;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WarehouseStockLedgerRepository extends JpaRepository<WarehouseStockLedger, UUID> {
    Optional<WarehouseStockLedger> findByIdempotencyKeyAndIsDeletedFalse(String idempotencyKey);

    Page<WarehouseStockLedger> findAllByWarehouseIdAndIsDeletedFalseOrderByPostedAtDesc(UUID warehouseId,
                                                                                        Pageable pageable);
}
