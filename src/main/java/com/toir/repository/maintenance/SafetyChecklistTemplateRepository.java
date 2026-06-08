package com.toir.repository.maintenance;

import com.toir.entity.maintenance.SafetyChecklistTemplate;
import com.toir.enums.WorkOrderType;
import com.toir.enums.WorkType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SafetyChecklistTemplateRepository extends JpaRepository<SafetyChecklistTemplate, UUID> {

    Optional<SafetyChecklistTemplate> findByIdAndIsDeletedFalse(UUID id);

    List<SafetyChecklistTemplate> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    Optional<SafetyChecklistTemplate> findFirstByWorkOrderTypeAndActiveTrueAndIsDeletedFalseOrderByUpdatedAtDesc(WorkOrderType workOrderType);

    Optional<SafetyChecklistTemplate> findFirstByWorkOrderTypeIsNullAndWorkTypeAndActiveTrueAndIsDeletedFalseOrderByUpdatedAtDesc(WorkType workType);

    Optional<SafetyChecklistTemplate> findFirstByWorkOrderTypeIsNullAndWorkTypeIsNullAndActiveTrueAndIsDeletedFalseOrderByUpdatedAtDesc();

    @Query("""
            select t from SafetyChecklistTemplate t
            left join fetch t.items i
            where t.id = :id and t.isDeleted = false
            order by i.sequence asc
            """)
    Optional<SafetyChecklistTemplate> findByIdWithItems(@Param("id") UUID id);
}
