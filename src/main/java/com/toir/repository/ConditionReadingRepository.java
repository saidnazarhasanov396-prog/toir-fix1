package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.enums.ConditionParameter;
import com.toir.entity.ConditionReading;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;


@Repository
public interface ConditionReadingRepository extends JpaRepository<ConditionReading, UUID> {
    List<ConditionReading> findAllByEquipmentIdOrderByRecordedAtDesc(UUID equipmentId);
    List<ConditionReading> findAllByEquipmentIdAndParameterOrderByRecordedAtDesc(UUID equipmentId, ConditionParameter parameter);
    List<ConditionReading> findAllByRecordedAtAfterOrderByRecordedAtDesc(Instant since);
    List<ConditionReading> findAllBySeverityOrderByRecordedAtDesc(String severity);
}
