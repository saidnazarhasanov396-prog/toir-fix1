package com.toir.repository.maintenance;

import com.toir.entity.maintenance.MaintenanceTemplateSparePartRequirement;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MaintenanceTemplateSparePartRequirementRepository
        extends JpaRepository<MaintenanceTemplateSparePartRequirement, UUID> {

    @Query("""
            select r
            from MaintenanceTemplateSparePartRequirement r
            left join fetch r.operation
            join fetch r.sparePart
            where r.isDeleted = false
              and r.active = true
              and r.templateId = :templateId
            order by r.updatedAt desc
            """)
    List<MaintenanceTemplateSparePartRequirement> findActiveByTemplateId(@Param("templateId") UUID templateId);

    @Query("""
            select r
            from MaintenanceTemplateSparePartRequirement r
            left join fetch r.operation
            join fetch r.sparePart
            where r.isDeleted = false
              and r.active = true
              and r.templateId in :templateIds
            order by r.templateId, r.updatedAt desc
            """)
    List<MaintenanceTemplateSparePartRequirement> findAllActiveByTemplateIdIn(@Param("templateIds") Collection<UUID> templateIds);

    @Query("""
            select r
            from MaintenanceTemplateSparePartRequirement r
            left join fetch r.operation
            join fetch r.sparePart
            where r.isDeleted = false
              and r.id = :id
              and r.templateId = :templateId
            """)
    Optional<MaintenanceTemplateSparePartRequirement> findByIdAndTemplateIdAndIsDeletedFalse(
            @Param("id") UUID id,
            @Param("templateId") UUID templateId);

    @Query("""
            select count(r) > 0
            from MaintenanceTemplateSparePartRequirement r
            where r.isDeleted = false
              and r.active = true
              and r.templateId = :templateId
              and ((:operationId is null and r.operationId is null) or r.operationId = :operationId)
              and r.sparePartId = :sparePartId
              and (:excludeId is null or r.id <> :excludeId)
            """)
    boolean existsActiveByTemplateOperationAndSparePart(@Param("templateId") UUID templateId,
                                                       @Param("operationId") UUID operationId,
                                                       @Param("sparePartId") UUID sparePartId,
                                                       @Param("excludeId") UUID excludeId);

    List<MaintenanceTemplateSparePartRequirement> findAllBySparePartIdAndActiveTrueAndIsDeletedFalse(UUID sparePartId);
}
