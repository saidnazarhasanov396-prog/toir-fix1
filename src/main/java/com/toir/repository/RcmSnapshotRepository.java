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
    @Query(value = "SELECT * FROM rcm_snapshots WHERE equipment_id = :equipmentId AND is_deleted = false ORDER BY captured_at DESC", nativeQuery = true)
    List<RcmSnapshot> findAllByEquipmentIdOrderByCapturedAtDesc(@Param("equipmentId") UUID equipmentId);

    @Query(value = "SELECT * FROM rcm_snapshots WHERE captured_at > :since AND is_deleted = false ORDER BY captured_at DESC", nativeQuery = true)
    List<RcmSnapshot> findAllByCapturedAtAfterOrderByCapturedAtDesc(@Param("since") Instant since);
}
