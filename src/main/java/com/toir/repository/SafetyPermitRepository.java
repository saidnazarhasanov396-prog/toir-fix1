package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.SafetyPermit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;


@Repository
public interface SafetyPermitRepository extends JpaRepository<SafetyPermit, UUID> {
    java.util.Optional<SafetyPermit> findByIdAndIsDeletedFalse(java.util.UUID id);

    java.util.List<SafetyPermit> findAllByIsDeletedFalse();

    java.util.List<SafetyPermit> findAllByIdInAndIsDeletedFalse(java.util.Collection<java.util.UUID> ids);

    boolean existsByIdAndIsDeletedFalse(java.util.UUID id);

    long countByIsDeletedFalse();

    @Query(value = "SELECT * FROM safety_permits WHERE work_order_id = :workOrderId AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<SafetyPermit> findByWorkOrderIdAndIsDeletedFalse(@Param("workOrderId") UUID workOrderId);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM safety_permits WHERE permit_number = :permitNumber AND is_deleted = false)", nativeQuery = true)
    boolean existsByPermitNumberAndIsDeletedFalse(@Param("permitNumber") String permitNumber);
}
