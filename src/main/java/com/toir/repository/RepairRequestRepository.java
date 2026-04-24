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
    @Query(value = "SELECT COUNT(*) > 0 FROM repair_requests WHERE number = :number AND is_deleted = false", nativeQuery = true)
    boolean existsByNumber(@Param("number") String number);

    @Query(value = "SELECT COUNT(*) FROM repair_requests WHERE status = :status AND is_deleted = false", nativeQuery = true)
    long countByStatus(@Param("status") RequestStatus status);

    @Query(value = "SELECT * FROM repair_requests WHERE (:status IS NULL OR status = :status) " +
            "AND (:departmentId IS NULL OR department_id = :departmentId) " +
            "AND (:equipmentId IS NULL OR equipment_id = :equipmentId) AND is_deleted = false " +
            "ORDER BY detected_at DESC", nativeQuery = true)
    List<RepairRequest> search(@Param("status") RequestStatus status,
                               @Param("departmentId") UUID departmentId,
                               @Param("equipmentId") UUID equipmentId);
}
