package com.toir.repository.maintenance;

import com.toir.entity.maintenance.WorkOrderSparePartRequirement;
import com.toir.enums.WorkOrderSparePartRequirementSourceType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkOrderSparePartRequirementRepository
        extends JpaRepository<WorkOrderSparePartRequirement, UUID> {

    @Query("""
            select r
            from WorkOrderSparePartRequirement r
            join fetch r.sparePart
            left join fetch r.operation
            left join fetch r.template
            left join fetch r.sourceRequirement
            where r.isDeleted = false
              and r.workOrderId = :workOrderId
            order by r.updatedAt desc
            """)
    List<WorkOrderSparePartRequirement> findActiveByWorkOrderId(@Param("workOrderId") UUID workOrderId);

    Optional<WorkOrderSparePartRequirement> findByWorkOrderIdAndSourceTypeAndSourceRequirementIdAndIsDeletedFalse(
            UUID workOrderId,
            WorkOrderSparePartRequirementSourceType sourceType,
            UUID sourceRequirementId);
}
