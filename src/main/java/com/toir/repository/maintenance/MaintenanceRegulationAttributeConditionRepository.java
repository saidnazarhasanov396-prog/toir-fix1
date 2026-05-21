package com.toir.repository.maintenance;

import com.toir.entity.maintenance.MaintenanceRegulationAttributeCondition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface MaintenanceRegulationAttributeConditionRepository
        extends JpaRepository<MaintenanceRegulationAttributeCondition, UUID> {

    @Query(value = """
            SELECT * FROM maintenance_regulation_attribute_conditions
            WHERE regulation_id = :regulationId AND is_deleted = false
            ORDER BY attribute_key ASC, created_at ASC
            """, nativeQuery = true)
    List<MaintenanceRegulationAttributeCondition> findAllByRegulationIdAndIsDeletedFalse(
            @Param("regulationId") UUID regulationId);

    @Query(value = """
            SELECT * FROM maintenance_regulation_attribute_conditions
            WHERE regulation_id IN (:regulationIds) AND is_deleted = false
            ORDER BY attribute_key ASC, created_at ASC
            """, nativeQuery = true)
    List<MaintenanceRegulationAttributeCondition> findAllByRegulationIdInAndIsDeletedFalse(
            @Param("regulationIds") Collection<UUID> regulationIds);
}
