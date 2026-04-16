package com.toir.downtime;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DowntimeEventRepository extends JpaRepository<DowntimeEvent, UUID> {
    List<DowntimeEvent> findAllByEquipmentIdOrderByStartAtDesc(UUID equipmentId);
    List<DowntimeEvent> findAllByDepartmentIdOrderByStartAtDesc(UUID departmentId);
}
