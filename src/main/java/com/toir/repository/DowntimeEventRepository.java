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
    java.util.Optional<DowntimeEvent> findByIdAndIsDeletedFalse(java.util.UUID id);

    java.util.List<DowntimeEvent> findAllByIsDeletedFalse();

    java.util.List<DowntimeEvent> findAllByIdInAndIsDeletedFalse(java.util.Collection<java.util.UUID> ids);

    boolean existsByIdAndIsDeletedFalse(java.util.UUID id);

    long countByIsDeletedFalse();

    @Query(value = "SELECT * FROM downtime_events WHERE equipment_id = :equipmentId AND is_deleted = false ORDER BY start_at DESC", nativeQuery = true)
    List<DowntimeEvent> findAllByEquipmentIdAndIsDeletedFalseOrderByStartAtDesc(@Param("equipmentId") UUID equipmentId);

    @Query(value = "SELECT * FROM downtime_events WHERE department_id = :departmentId AND is_deleted = false ORDER BY start_at DESC", nativeQuery = true)
    List<DowntimeEvent> findAllByDepartmentIdAndIsDeletedFalseOrderByStartAtDesc(@Param("departmentId") UUID departmentId);
}
