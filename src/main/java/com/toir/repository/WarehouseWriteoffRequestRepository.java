package com.toir.repository;

import com.toir.entity.warehouse.WarehouseWriteoffRequest;
import com.toir.enums.WarehouseWriteoffStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WarehouseWriteoffRequestRepository extends JpaRepository<WarehouseWriteoffRequest, UUID> {

    Optional<WarehouseWriteoffRequest> findByIdAndIsDeletedFalse(UUID id);

    long countByIsDeletedFalse();

    @Query("""
            select w
            from WarehouseWriteoffRequest w
            where w.isDeleted = false
              and (:warehouseId is null or w.warehouseId = :warehouseId)
              and (:sparePartId is null or w.sparePartId = :sparePartId)
              and (:status is null or w.status = :status)
            order by w.updatedAt desc
            """)
    Page<WarehouseWriteoffRequest> search(@Param("warehouseId") UUID warehouseId,
                                          @Param("sparePartId") UUID sparePartId,
                                          @Param("status") WarehouseWriteoffStatus status,
                                          Pageable pageable);

    @Query("""
            select count(w)
            from WarehouseWriteoffRequest w
            where w.isDeleted = false
              and (:warehouseId is null or w.warehouseId = :warehouseId)
            """)
    long countVisible(@Param("warehouseId") UUID warehouseId);

    @Query("""
            select count(w)
            from WarehouseWriteoffRequest w
            where w.isDeleted = false
              and (:warehouseId is null or w.warehouseId = :warehouseId)
              and w.status = :status
            """)
    long countByStatus(@Param("warehouseId") UUID warehouseId,
                       @Param("status") WarehouseWriteoffStatus status);

    @Query("""
            select coalesce(sum(w.quantity), 0)
            from WarehouseWriteoffRequest w
            where w.isDeleted = false
              and (:warehouseId is null or w.warehouseId = :warehouseId)
            """)
    BigDecimal sumQuantity(@Param("warehouseId") UUID warehouseId);
}
