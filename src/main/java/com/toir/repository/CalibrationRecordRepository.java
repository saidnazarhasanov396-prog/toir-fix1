package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.CalibrationRecord;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;


@Repository
public interface CalibrationRecordRepository extends JpaRepository<CalibrationRecord, UUID> {
    List<CalibrationRecord> findAllByEquipmentIdOrderByPerformedAtDesc(UUID equipmentId);
    List<CalibrationRecord> findAllByNextDueAtBefore(LocalDate date);
    List<CalibrationRecord> findAllByResult(String result);
}
