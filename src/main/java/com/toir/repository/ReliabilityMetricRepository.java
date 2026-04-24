package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.ReliabilityMetric;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;


@Repository
public interface ReliabilityMetricRepository extends JpaRepository<ReliabilityMetric, UUID> {
    List<ReliabilityMetric> findAllByEquipmentIdOrderByMetricDateDesc(UUID equipmentId);
}
