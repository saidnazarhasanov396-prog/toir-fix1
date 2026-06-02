package com.toir.repository.maintenance;

import com.toir.entity.maintenance.EquipmentMaintenanceRule;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface EquipmentMaintenanceRuleRepository extends JpaRepository<EquipmentMaintenanceRule, UUID> {

    @Query(value = "SELECT * FROM equipment_maintenance_rules WHERE id = cast(:id as uuid) AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<EquipmentMaintenanceRule> findByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT * FROM equipment_maintenance_rules WHERE equipment_id = :equipmentId AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<EquipmentMaintenanceRule> findAllByEquipmentIdAndIsDeletedFalse(@Param("equipmentId") UUID equipmentId);

    @Query(value = "SELECT * FROM equipment_maintenance_rules WHERE is_active = true AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<EquipmentMaintenanceRule> findAllActive();

    @Query(value = "SELECT * FROM equipment_maintenance_rules WHERE id IN (:ids) AND is_deleted = false", nativeQuery = true)
    List<EquipmentMaintenanceRule> findAllByIdInAndIsDeletedFalse(@Param("ids") Collection<UUID> ids);

    @Query("""
            select r
            from EquipmentMaintenanceRule r
            where r.isDeleted = false
              and r.equipmentId in :equipmentIds
              and (:active is null or r.active = :active)
            order by r.updatedAt desc
            """)
    List<EquipmentMaintenanceRule> findAllByEquipmentIdInAndOptionalActive(
            @Param("equipmentIds") Collection<UUID> equipmentIds,
            @Param("active") Boolean active
    );

    @Query(value = "SELECT EXISTS(SELECT 1 FROM equipment_maintenance_rules WHERE code = :code AND is_deleted = false)", nativeQuery = true)
    boolean existsByCodeAndIsDeletedFalse(@Param("code") String code);

    @Query(value = """
            SELECT COALESCE(MAX(CAST(SUBSTRING(code FROM LENGTH(:prefix) + 1) AS BIGINT)), 0)
            FROM equipment_maintenance_rules
            WHERE code LIKE CONCAT(:prefix, '%')
              AND SUBSTRING(code FROM LENGTH(:prefix) + 1) ~ '^[0-9]+$'
            """, nativeQuery = true)
    long maxSequenceByCodePrefix(@Param("prefix") String prefix);
}
