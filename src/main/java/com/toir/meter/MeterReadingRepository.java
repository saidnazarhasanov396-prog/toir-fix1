package com.toir.meter;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MeterReadingRepository extends JpaRepository<MeterReading, UUID> {
    Page<MeterReading> findAllByMeterIdOrderByReadAtDesc(UUID meterId, Pageable pageable);
    List<MeterReading> findAllByMeterIdAndReadAtBetweenOrderByReadAtAsc(UUID meterId, Instant from, Instant to);
    Optional<MeterReading> findTopByMeterIdOrderByReadAtDesc(UUID meterId);
    List<MeterReading> findAllByEquipmentIdOrderByReadAtDesc(UUID equipmentId);
}
