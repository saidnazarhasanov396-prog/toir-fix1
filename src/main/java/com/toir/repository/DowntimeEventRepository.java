package com.toir.repository;

import com.toir.entity.DowntimeEvent;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface DowntimeEventRepository extends JpaRepository<DowntimeEvent, UUID> {
    @Query(value = "SELECT * FROM downtime_events WHERE id = cast(:id as uuid) AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<DowntimeEvent> findByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT * FROM downtime_events WHERE is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<DowntimeEvent> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    @Query(value = "SELECT * FROM downtime_events WHERE id IN (:ids) AND is_deleted = false", nativeQuery = true)
    List<DowntimeEvent> findAllByIdInAndIsDeletedFalse(@Param("ids") Collection<UUID> ids);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM downtime_events WHERE id = cast(:id as uuid) AND is_deleted = false)", nativeQuery = true)
    boolean existsByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT COUNT(*) FROM downtime_events WHERE is_deleted = false", nativeQuery = true)
    long countByIsDeletedFalse();

    @Query(value = "SELECT * FROM downtime_events WHERE equipment_id = :equipmentId AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<DowntimeEvent> findAllByEquipmentIdAndIsDeletedFalseOrderByStartAtDesc(@Param("equipmentId") UUID equipmentId);

    @Query(value = "SELECT * FROM downtime_events WHERE department_id = :departmentId AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<DowntimeEvent> findAllByDepartmentIdAndIsDeletedFalseOrderByStartAtDesc(@Param("departmentId") UUID departmentId);

    @Query(value = "SELECT * FROM downtime_events WHERE equipment_id IN (:equipmentIds) AND is_deleted = false ORDER BY start_at DESC", nativeQuery = true)
    List<DowntimeEvent> findAllByEquipmentIdInAndIsDeletedFalse(@Param("equipmentIds") Collection<UUID> equipmentIds);
}
