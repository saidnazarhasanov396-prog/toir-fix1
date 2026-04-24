package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.OeeRecord;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;


@Repository
public interface OeeRecordRepository extends JpaRepository<OeeRecord, UUID> {
    List<OeeRecord> findAllByEquipmentIdOrderByShiftStartDesc(UUID equipmentId);
    List<OeeRecord> findAllByEquipmentIdAndShiftStartBetweenOrderByShiftStartAsc(
            UUID equipmentId, Instant from, Instant to);
    List<OeeRecord> findAllByShiftStartBetween(Instant from, Instant to);
}
