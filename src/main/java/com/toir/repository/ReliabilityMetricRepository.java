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
    java.util.Optional<ReliabilityMetric> findByIdAndIsDeletedFalse(java.util.UUID id);

    java.util.List<ReliabilityMetric> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    java.util.List<ReliabilityMetric> findAllByIdInAndIsDeletedFalse(java.util.Collection<java.util.UUID> ids);

    boolean existsByIdAndIsDeletedFalse(java.util.UUID id);

    long countByIsDeletedFalse();

    @Query(value = "SELECT * FROM reliability_metrics WHERE equipment_id = :equipmentId AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<ReliabilityMetric> findAllByEquipmentIdAndIsDeletedFalseOrderByMetricDateDesc(@Param("equipmentId") UUID equipmentId);
}
