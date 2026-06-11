package com.toir.repository.maintenance;

import com.toir.entity.maintenance.MaintenanceRegulationSparePartRequirement;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MaintenanceRegulationSparePartRequirementRepository
        extends JpaRepository<MaintenanceRegulationSparePartRequirement, UUID> {

    @Query("""
            select r
            from MaintenanceRegulationSparePartRequirement r
            join fetch r.sparePart
            where r.isDeleted = false
              and r.active = true
              and r.regulationId = :regulationId
            order by r.updatedAt desc
            """)
    List<MaintenanceRegulationSparePartRequirement> findActiveByRegulationId(
            @Param("regulationId") UUID regulationId);

    @Query("""
            select r
            from MaintenanceRegulationSparePartRequirement r
            join fetch r.sparePart
            where r.isDeleted = false
              and r.active = true
              and r.regulationId in :regulationIds
            order by r.regulationId, r.updatedAt desc
            """)
    List<MaintenanceRegulationSparePartRequirement> findAllActiveByRegulationIdIn(
            @Param("regulationIds") Collection<UUID> regulationIds);

    @Query("""
            select count(r) > 0
            from MaintenanceRegulationSparePartRequirement r
            where r.isDeleted = false
              and r.active = true
              and r.regulationId = :regulationId
              and r.sparePartId = :sparePartId
              and (:excludeId is null or r.id <> :excludeId)
            """)
    boolean existsActiveByRegulationAndSparePart(@Param("regulationId") UUID regulationId,
                                                @Param("sparePartId") UUID sparePartId,
                                                @Param("excludeId") UUID excludeId);
}
