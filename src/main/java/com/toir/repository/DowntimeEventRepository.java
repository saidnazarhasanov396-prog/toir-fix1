package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.DowntimeEvent;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;


@Repository
public interface DowntimeEventRepository extends JpaRepository<DowntimeEvent, UUID> {
    List<DowntimeEvent> findAllByEquipmentIdOrderByStartAtDesc(UUID equipmentId);
    List<DowntimeEvent> findAllByDepartmentIdOrderByStartAtDesc(UUID departmentId);
}
