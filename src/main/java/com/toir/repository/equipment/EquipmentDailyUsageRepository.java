package com.toir.repository.equipment;

import com.toir.entity.equipment.EquipmentDailyUsage;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface EquipmentDailyUsageRepository extends JpaRepository<EquipmentDailyUsage, UUID> {

    @Query(value = """
            SELECT * FROM equipment_daily_usage
            WHERE equipment_id = cast(:equipmentId as uuid)
              AND usage_date = cast(:usageDate as date)
              AND is_deleted = false
            LIMIT 1
            """, nativeQuery = true)
    Optional<EquipmentDailyUsage> findByEquipmentIdAndUsageDateAndIsDeletedFalse(
            @Param("equipmentId") UUID equipmentId,
            @Param("usageDate") LocalDate usageDate
    );

    @Query(value = """
            SELECT * FROM equipment_daily_usage
            WHERE equipment_id = cast(:equipmentId as uuid)
              AND usage_date BETWEEN cast(:fromDate as date) AND cast(:toDate as date)
              AND is_deleted = false
            ORDER BY usage_date ASC
            """, nativeQuery = true)
    List<EquipmentDailyUsage> findAllByEquipmentIdAndUsageDateBetweenAndIsDeletedFalse(
            @Param("equipmentId") UUID equipmentId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate
    );

    @Query(value = """
            SELECT COALESCE(SUM(usage_value), 0)
            FROM equipment_daily_usage
            WHERE equipment_id = cast(:equipmentId as uuid)
              AND is_deleted = false
            """, nativeQuery = true)
    double sumAllTimeUsage(@Param("equipmentId") UUID equipmentId);
}
