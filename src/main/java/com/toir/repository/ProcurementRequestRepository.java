package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.ProcurementRequest;
import com.toir.enums.ProcurementRequestStatus;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;


@Repository
public interface ProcurementRequestRepository extends JpaRepository<ProcurementRequest, UUID> {
    java.util.Optional<ProcurementRequest> findByIdAndIsDeletedFalse(java.util.UUID id);

    java.util.List<ProcurementRequest> findAllByIsDeletedFalse();

    java.util.List<ProcurementRequest> findAllByIdInAndIsDeletedFalse(java.util.Collection<java.util.UUID> ids);

    boolean existsByIdAndIsDeletedFalse(java.util.UUID id);

    long countByIsDeletedFalse();

    List<ProcurementRequest> findAllByStatusAndIsDeletedFalse(ProcurementRequestStatus status);

    List<ProcurementRequest> findAllByDepartmentIdAndIsDeletedFalse(UUID departmentId);

    boolean existsByNumberAndIsDeletedFalse(String number);

    long countByStatusAndIsDeletedFalse(ProcurementRequestStatus status);
}
