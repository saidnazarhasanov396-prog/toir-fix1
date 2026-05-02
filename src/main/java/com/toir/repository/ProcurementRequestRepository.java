package com.toir.repository;

import com.toir.entity.ProcurementRequest;
import com.toir.enums.ProcurementRequestStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface ProcurementRequestRepository extends JpaRepository<ProcurementRequest, UUID> {
    @Query(value = "SELECT * FROM procurement_requests WHERE id = cast(:id as uuid) AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<ProcurementRequest> findByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT * FROM procurement_requests WHERE is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<ProcurementRequest> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    @Query(value = "SELECT * FROM procurement_requests WHERE id IN (:ids) AND is_deleted = false", nativeQuery = true)
    List<ProcurementRequest> findAllByIdInAndIsDeletedFalse(@Param("ids") Collection<UUID> ids);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM procurement_requests WHERE id = cast(:id as uuid) AND is_deleted = false)", nativeQuery = true)
    boolean existsByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT COUNT(*) FROM procurement_requests WHERE is_deleted = false", nativeQuery = true)
    long countByIsDeletedFalse();

    @Query(value = "SELECT * FROM procurement_requests WHERE status = cast(:status as varchar) AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<ProcurementRequest> findAllByStatusAndIsDeletedFalseOrderByUpdatedAtDesc(@Param("status") ProcurementRequestStatus status);

    @Query(value = "SELECT * FROM procurement_requests WHERE department_id = cast(:departmentId as uuid) AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<ProcurementRequest> findAllByDepartmentIdAndIsDeletedFalseOrderByUpdatedAtDesc(@Param("departmentId") UUID departmentId);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM procurement_requests WHERE number = cast(:number as varchar) AND is_deleted = false)", nativeQuery = true)
    boolean existsByNumberAndIsDeletedFalse(@Param("number") String number);

    @Query(value = "SELECT COUNT(*) FROM procurement_requests WHERE status = :status AND is_deleted = false", nativeQuery = true)
    long countByStatusAndIsDeletedFalse(@Param("status") String status);
}
