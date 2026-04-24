package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.DowntimeEvent;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;


@Repository
public interface DowntimeEventRepository extends JpaRepository<DowntimeEvent, UUID> {
    @Query(value = "SELECT * FROM downtime_events WHERE equipment_id = :equipmentId AND is_deleted = false ORDER BY start_at DESC", nativeQuery = true)
    List<DowntimeEvent> findAllByEquipmentIdOrderByStartAtDesc(@Param("equipmentId") UUID equipmentId);

    @Query(value = "SELECT * FROM downtime_events WHERE department_id = :departmentId AND is_deleted = false ORDER BY start_at DESC", nativeQuery = true)
    List<DowntimeEvent> findAllByDepartmentIdOrderByStartAtDesc(@Param("departmentId") UUID departmentId);
}
