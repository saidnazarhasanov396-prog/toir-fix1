package com.toir.repository;
import com.toir.entity.ProcurementRequest;
import com.toir.enums.ProcurementRequestStatus;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProcurementRequestRepository extends JpaRepository<ProcurementRequest, UUID> {
    List<ProcurementRequest> findAllByStatus(ProcurementRequestStatus status);
    List<ProcurementRequest> findAllByDepartmentId(UUID departmentId);
    boolean existsByNumber(String number);
    long countByStatus(ProcurementRequestStatus status);
}
