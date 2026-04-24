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
    @Query(value = "SELECT * FROM procurement_requests WHERE status = :status AND is_deleted = false", nativeQuery = true)
    List<ProcurementRequest> findAllByStatus(@Param("status") ProcurementRequestStatus status);

    @Query(value = "SELECT * FROM procurement_requests WHERE department_id = :departmentId AND is_deleted = false", nativeQuery = true)
    List<ProcurementRequest> findAllByDepartmentId(@Param("departmentId") UUID departmentId);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM procurement_requests WHERE number = :number AND is_deleted = false)", nativeQuery = true)
    boolean existsByNumber(@Param("number") String number);

    @Query(value = "SELECT COUNT(*) FROM procurement_requests WHERE status = :status AND is_deleted = false", nativeQuery = true)
    long countByStatus(@Param("status") ProcurementRequestStatus status);
}
