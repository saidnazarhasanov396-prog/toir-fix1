package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.RepairRequest;
import com.toir.enums.RequestStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;


@Repository
public interface RepairRequestRepository extends JpaRepository<RepairRequest, UUID> {
    boolean existsByNumber(String number);

    long countByStatus(RequestStatus status);

    @Query("SELECT r FROM RepairRequest r WHERE (:status IS NULL OR r.status = :status) " +
            "AND (:departmentId IS NULL OR r.departmentId = :departmentId) " +
            "AND (:equipmentId IS NULL OR r.equipmentId = :equipmentId) " +
            "ORDER BY r.detectedAt DESC")
    List<RepairRequest> search(@Param("status") RequestStatus status,
                               @Param("departmentId") UUID departmentId,
                               @Param("equipmentId") UUID equipmentId);
}
