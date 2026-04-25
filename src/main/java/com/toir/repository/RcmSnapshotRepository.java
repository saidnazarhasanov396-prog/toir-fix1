package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.RcmSnapshot;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;


@Repository
public interface RcmSnapshotRepository extends JpaRepository<RcmSnapshot, UUID> {
    java.util.Optional<RcmSnapshot> findByIdAndIsDeletedFalse(java.util.UUID id);

    java.util.List<RcmSnapshot> findAllByIsDeletedFalse();

    java.util.List<RcmSnapshot> findAllByIdInAndIsDeletedFalse(java.util.Collection<java.util.UUID> ids);

    boolean existsByIdAndIsDeletedFalse(java.util.UUID id);

    long countByIsDeletedFalse();

    @Query(value = "SELECT * FROM rcm_snapshots WHERE equipment_id = :equipmentId AND is_deleted = false ORDER BY captured_at DESC", nativeQuery = true)
    List<RcmSnapshot> findAllByEquipmentIdAndIsDeletedFalseOrderByCapturedAtDesc(@Param("equipmentId") UUID equipmentId);

    @Query(value = "SELECT * FROM rcm_snapshots WHERE captured_at > :since AND is_deleted = false ORDER BY captured_at DESC", nativeQuery = true)
    List<RcmSnapshot> findAllByCapturedAtAfterAndIsDeletedFalseOrderByCapturedAtDesc(@Param("since") Instant since);
}
