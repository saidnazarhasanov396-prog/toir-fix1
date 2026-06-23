package com.toir.repository;

import com.toir.entity.ReliabilityMetric;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface ReliabilityMetricRepository extends JpaRepository<ReliabilityMetric, UUID> {
    @Query(value = "SELECT * FROM reliability_metrics WHERE id = cast(:id as uuid) AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<ReliabilityMetric> findByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT * FROM reliability_metrics WHERE is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<ReliabilityMetric> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    @Query(value = "SELECT * FROM reliability_metrics WHERE id IN (:ids) AND is_deleted = false", nativeQuery = true)
    List<ReliabilityMetric> findAllByIdInAndIsDeletedFalse(@Param("ids") Collection<UUID> ids);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM reliability_metrics WHERE id = cast(:id as uuid) AND is_deleted = false)", nativeQuery = true)
    boolean existsByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT COUNT(*) FROM reliability_metrics WHERE is_deleted = false", nativeQuery = true)
    long countByIsDeletedFalse();

    @Query(value = "SELECT * FROM reliability_metrics WHERE equipment_id = :equipmentId AND is_deleted = false ORDER BY metric_date DESC, updated_at DESC", nativeQuery = true)
    List<ReliabilityMetric> findAllByEquipmentIdAndIsDeletedFalseOrderByMetricDateDesc(@Param("equipmentId") UUID equipmentId);
}
