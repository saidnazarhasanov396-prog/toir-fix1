package com.toir.repository;

import com.toir.entity.warehouse.WarehouseStockBalance;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WarehouseStockBalanceRepository extends JpaRepository<WarehouseStockBalance, UUID> {

    Optional<WarehouseStockBalance> findByIdentityKeyAndIsDeletedFalse(String identityKey);

    Page<WarehouseStockBalance> findAllByWarehouseIdAndIsDeletedFalseOrderByUpdatedAtDesc(UUID warehouseId,
                                                                                          Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select b
            from WarehouseStockBalance b
            where b.identityKey = :identityKey
              and b.isDeleted = false
            """)
    Optional<WarehouseStockBalance> lockByIdentityKey(@Param("identityKey") String identityKey);
}
