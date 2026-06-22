package com.toir.repository.equipment;

import com.toir.entity.equipment.EquipmentCommissioningAct;
import com.toir.enums.EquipmentCommissioningStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface EquipmentCommissioningActRepository extends JpaRepository<EquipmentCommissioningAct, UUID> {
    Optional<EquipmentCommissioningAct> findByIdAndIsDeletedFalse(UUID id);
    boolean existsByActNumberAndIsDeletedFalse(String actNumber);
    boolean existsByActNumberAndIdNotAndIsDeletedFalse(String actNumber, UUID id);
    boolean existsByEquipmentIdAndStatusInAndIsDeletedFalse(
            UUID equipmentId, Collection<EquipmentCommissioningStatus> statuses);
    boolean existsByEquipmentIdAndStatusAndIsDeletedFalse(UUID equipmentId, EquipmentCommissioningStatus status);
    Optional<EquipmentCommissioningAct> findFirstByEquipmentIdAndStatusAndIsDeletedFalseOrderByApprovedAtDesc(
            UUID equipmentId, EquipmentCommissioningStatus status);

    @Query("""
            select distinct a.equipmentId
            from EquipmentCommissioningAct a
            where a.isDeleted = false
              and a.equipmentId in :equipmentIds
              and a.status in :statuses
            """)
    List<UUID> findEquipmentIdsWithStatuses(
            @Param("equipmentIds") Collection<UUID> equipmentIds,
            @Param("statuses") Collection<EquipmentCommissioningStatus> statuses);

    @Query("""
            select a from EquipmentCommissioningAct a
            where a.isDeleted = false
              and (:status is null or a.status = :status)
              and (:equipmentId is null or a.equipmentId = :equipmentId)
              and (:departmentId is null or a.targetDepartmentId = :departmentId)
              and (:search is null or lower(a.actNumber) like :search)
            order by a.updatedAt desc
            """)
    Page<EquipmentCommissioningAct> search(
            @Param("status") EquipmentCommissioningStatus status,
            @Param("equipmentId") UUID equipmentId,
            @Param("departmentId") UUID departmentId,
            @Param("search") String search,
            Pageable pageable);
}
