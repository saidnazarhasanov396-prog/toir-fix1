package com.toir.repository;

import com.toir.entity.warehouse.WmsLabelEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface WmsLabelEventRepository extends JpaRepository<WmsLabelEvent, UUID> {

    @Query("""
            select e
            from WmsLabelEvent e
            where e.isDeleted = false
              and (:warehouseId is null or e.warehouseId = :warehouseId)
              and (cast(:labelType as string) is null or e.labelType = cast(:labelType as string))
            order by e.createdAt desc
            """)
    Page<WmsLabelEvent> search(@Param("warehouseId") UUID warehouseId,
                               @Param("labelType") String labelType,
                               Pageable pageable);

    long countByIsDeletedFalse();

    long countByLabelTypeAndIsDeletedFalse(String labelType);

    @Query("""
            select count(e)
            from WmsLabelEvent e
            where e.isDeleted = false
              and (:warehouseId is null or e.warehouseId = :warehouseId)
            """)
    long countVisible(@Param("warehouseId") UUID warehouseId);

    @Query("""
            select count(e)
            from WmsLabelEvent e
            where e.isDeleted = false
              and (:warehouseId is null or e.warehouseId = :warehouseId)
              and e.labelType = :labelType
            """)
    long countByLabelType(@Param("warehouseId") UUID warehouseId,
                          @Param("labelType") String labelType);

    @Query("""
            select max(e.createdAt)
            from WmsLabelEvent e
            where e.isDeleted = false
              and (:warehouseId is null or e.warehouseId = :warehouseId)
            """)
    Instant findLastPrintedAt(@Param("warehouseId") UUID warehouseId);
}
