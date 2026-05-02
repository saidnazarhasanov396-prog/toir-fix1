package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.ProcurementRequest;
import com.toir.enums.ProcurementRequestStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;


@Repository
public interface ProcurementRequestRepository extends JpaRepository<ProcurementRequest, UUID> {
    java.util.Optional<ProcurementRequest> findByIdAndIsDeletedFalse(java.util.UUID id);

    java.util.List<ProcurementRequest> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    java.util.List<ProcurementRequest> findAllByIdInAndIsDeletedFalse(java.util.Collection<java.util.UUID> ids);

    boolean existsByIdAndIsDeletedFalse(java.util.UUID id);

    long countByIsDeletedFalse();

    List<ProcurementRequest> findAllByStatusAndIsDeletedFalseOrderByUpdatedAtDesc(ProcurementRequestStatus status);

    List<ProcurementRequest> findAllByDepartmentIdAndIsDeletedFalseOrderByUpdatedAtDesc(UUID departmentId);

    boolean existsByNumberAndIsDeletedFalse(String number);

    @Query(value = "SELECT COUNT(*) FROM procurement_requests WHERE status = :status AND is_deleted = false", nativeQuery = true)
    long countByStatusAndIsDeletedFalse(@Param("status") String status);
}
