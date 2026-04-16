package com.toir.reliability;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReliabilityMetricRepository extends JpaRepository<ReliabilityMetric, UUID> {
    List<ReliabilityMetric> findAllByEquipmentIdOrderByMetricDateDesc(UUID equipmentId);
}
