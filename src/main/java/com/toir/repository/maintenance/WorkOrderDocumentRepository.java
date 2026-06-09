package com.toir.repository.maintenance;

import com.toir.entity.maintenance.WorkOrderDocument;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkOrderDocumentRepository extends JpaRepository<WorkOrderDocument, UUID> {

    @Query("""
            select wod
            from WorkOrderDocument wod
            join fetch wod.file f
            join wod.workOrder workOrder
            where workOrder.id = :workOrderId
              and workOrder.isDeleted = false
              and f.deleted = false
            order by wod.createdAt desc
            """)
    List<WorkOrderDocument> findAllByWorkOrderId(@Param("workOrderId") UUID workOrderId);

    @Query("""
            select wod
            from WorkOrderDocument wod
            join fetch wod.file f
            join wod.workOrder workOrder
            where wod.id = :id
              and workOrder.id = :workOrderId
              and workOrder.isDeleted = false
              and f.deleted = false
            """)
    Optional<WorkOrderDocument> findByIdAndWorkOrderId(@Param("id") UUID id, @Param("workOrderId") UUID workOrderId);
}
