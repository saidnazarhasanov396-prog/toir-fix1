package com.toir.repository;

import com.toir.entity.RcmSnapshot;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface RcmSnapshotRepository extends JpaRepository<RcmSnapshot, UUID> {
    @Query(value = "SELECT * FROM rcm_snapshots WHERE id = cast(:id as uuid) AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<RcmSnapshot> findByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT * FROM rcm_snapshots WHERE is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<RcmSnapshot> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    @Query(value = "SELECT * FROM rcm_snapshots WHERE id IN (:ids) AND is_deleted = false", nativeQuery = true)
    List<RcmSnapshot> findAllByIdInAndIsDeletedFalse(@Param("ids") Collection<UUID> ids);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM rcm_snapshots WHERE id = cast(:id as uuid) AND is_deleted = false)", nativeQuery = true)
    boolean existsByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT COUNT(*) FROM rcm_snapshots WHERE is_deleted = false", nativeQuery = true)
    long countByIsDeletedFalse();

    @Query(value = "SELECT * FROM rcm_snapshots WHERE equipment_id = :equipmentId AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<RcmSnapshot> findAllByEquipmentIdAndIsDeletedFalseOrderByCapturedAtDesc(@Param("equipmentId") UUID equipmentId);

    @Query(value = "SELECT * FROM rcm_snapshots WHERE captured_at > :since AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<RcmSnapshot> findAllByCapturedAtAfterAndIsDeletedFalseOrderByCapturedAtDesc(@Param("since") Instant since);
}
