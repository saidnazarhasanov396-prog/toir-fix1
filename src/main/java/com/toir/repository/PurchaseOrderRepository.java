package com.toir.repository;

import com.toir.entity.PurchaseOrder;
import com.toir.enums.PurchaseOrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, UUID> {

    Optional<PurchaseOrder> findByIdAndIsDeletedFalse(UUID id);

    boolean existsByNumberAndIsDeletedFalse(String number);

    long countByIsDeletedFalse();

    List<PurchaseOrder> findAllByCounteragentIdAndIsDeletedFalse(UUID counteragentId);

    List<PurchaseOrder> findAllByProcurementRequestIdAndIsDeletedFalse(UUID procurementRequestId);

    @Query("""
            select min(po.expectedDeliveryDate)
            from PurchaseOrder po
            join po.lines line
            where po.isDeleted = false
              and line.isDeleted = false
              and po.warehouseId = :warehouseId
              and line.sparePartId = :sparePartId
              and line.remainingQuantity > 0
              and po.expectedDeliveryDate is not null
              and po.status in (
                    com.toir.enums.PurchaseOrderStatus.SENT,
                    com.toir.enums.PurchaseOrderStatus.PARTIALLY_RECEIVED
              )
            """)
    LocalDate findEarliestExpectedDeliveryDate(
            @Param("warehouseId") UUID warehouseId,
            @Param("sparePartId") UUID sparePartId
    );

    @Query("""
            select po
            from PurchaseOrder po
            where po.isDeleted = false
              and (:counteragentId is null or po.counteragentId = :counteragentId)
              and (:status is null or po.status = :status)
              and (:warehouseId is null or po.warehouseId = :warehouseId)
              and (:fromDate is null or po.orderDate >= :fromDate)
              and (:toDate is null or po.orderDate <= :toDate)
              and (:scopeAdmin = true or po.warehouseId in :warehouseIds)
            order by po.updatedAt desc
            """)
    List<PurchaseOrder> search(
            @Param("counteragentId") UUID counteragentId,
            @Param("status") PurchaseOrderStatus status,
            @Param("warehouseId") UUID warehouseId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("scopeAdmin") boolean scopeAdmin,
            @Param("warehouseIds") Collection<UUID> warehouseIds
    );
}
