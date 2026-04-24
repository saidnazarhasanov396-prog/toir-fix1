package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.ReliabilityMetric;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;


@Repository
public interface ReliabilityMetricRepository extends JpaRepository<ReliabilityMetric, UUID> {
    @Query(value = "SELECT * FROM reliability_metrics WHERE equipment_id = :equipmentId AND is_deleted = false ORDER BY metric_date DESC", nativeQuery = true)
    List<ReliabilityMetric> findAllByEquipmentIdOrderByMetricDateDesc(@Param("equipmentId") UUID equipmentId);
}
