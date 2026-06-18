package com.toir.repository;

import com.toir.entity.equipment.EquipmentUsageSession;
import com.toir.enums.EquipmentUsageSessionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EquipmentUsageSessionRepository extends JpaRepository<EquipmentUsageSession, UUID> {

    Optional<EquipmentUsageSession> findByIdAndEquipmentIdAndIsDeletedFalse(UUID id, UUID equipmentId);

    Page<EquipmentUsageSession> findAllByEquipmentIdAndIsDeletedFalseOrderByStartedAtDesc(UUID equipmentId, Pageable pageable);

    boolean existsByEquipmentIdAndStatusAndIsDeletedFalse(UUID equipmentId, EquipmentUsageSessionStatus status);

    boolean existsByOperatorEmployeeIdAndStatusAndIsDeletedFalse(UUID operatorEmployeeId, EquipmentUsageSessionStatus status);
}
