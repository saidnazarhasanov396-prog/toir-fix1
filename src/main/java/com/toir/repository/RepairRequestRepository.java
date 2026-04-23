package com.toir.repository;
import com.toir.entity.RepairRequest;
import com.toir.entity.RequestStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface RepairRequestRepository extends JpaRepository<RepairRequest, UUID> {
    boolean existsByNumber(String number);
    long countByStatus(RequestStatus status);

    @Query("select r from RepairRequest r where (:status is null or r.status = :status) " +
            "and (:departmentId is null or r.departmentId = :departmentId) " +
            "and (:equipmentId is null or r.equipmentId = :equipmentId) " +
            "order by r.detectedAt desc")
    List<RepairRequest> search(@Param("status") RequestStatus status,
                               @Param("departmentId") UUID departmentId,
                               @Param("equipmentId") UUID equipmentId);
}
