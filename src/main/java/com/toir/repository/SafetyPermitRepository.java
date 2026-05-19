package com.toir.repository;

import com.toir.entity.SafetyPermit;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface SafetyPermitRepository extends JpaRepository<SafetyPermit, UUID> {
    @Query(value = "SELECT * FROM safety_permits WHERE id = cast(:id as uuid) AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<SafetyPermit> findByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT * FROM safety_permits WHERE is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<SafetyPermit> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    @Query(value = "SELECT * FROM safety_permits WHERE id IN (:ids) AND is_deleted = false", nativeQuery = true)
    List<SafetyPermit> findAllByIdInAndIsDeletedFalse(@Param("ids") Collection<UUID> ids);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM safety_permits WHERE id = cast(:id as uuid) AND is_deleted = false)", nativeQuery = true)
    boolean existsByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT COUNT(*) FROM safety_permits WHERE is_deleted = false", nativeQuery = true)
    long countByIsDeletedFalse();

    @Query(value = "SELECT * FROM safety_permits WHERE work_order_id = :workOrderId AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<SafetyPermit> findByWorkOrderIdAndIsDeletedFalse(@Param("workOrderId") UUID workOrderId);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM safety_permits WHERE permit_number = :permitNumber AND is_deleted = false)", nativeQuery = true)
    boolean existsByPermitNumberAndIsDeletedFalse(@Param("permitNumber") String permitNumber);
}
