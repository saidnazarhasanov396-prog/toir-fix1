package com.toir.repository;

import com.toir.entity.warehouse.InventoryCountSession;
import com.toir.enums.InventoryCountSessionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface InventoryCountSessionRepository extends JpaRepository<InventoryCountSession, UUID> {

    Optional<InventoryCountSession> findByIdAndIsDeletedFalse(UUID id);

    long countByIsDeletedFalse();

    @Query("""
            select s
            from InventoryCountSession s
            where s.isDeleted = false
              and (:warehouseId is null or s.warehouseId = :warehouseId)
              and (:status is null or s.status = :status)
            order by s.updatedAt desc
            """)
    Page<InventoryCountSession> search(@Param("warehouseId") UUID warehouseId,
                                       @Param("status") InventoryCountSessionStatus status,
                                       Pageable pageable);
}
