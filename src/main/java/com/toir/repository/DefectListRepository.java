package com.toir.repository;
import com.toir.entity.DefectList;
import com.toir.entity.DefectListStatus;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DefectListRepository extends JpaRepository<DefectList, UUID> {
    boolean existsByCode(String code);
    List<DefectList> findAllByEquipmentId(UUID equipmentId);
    List<DefectList> findAllByRepairRequestId(UUID repairRequestId);
    List<DefectList> findAllByStatusOrderByCreatedAtDesc(DefectListStatus status);
}
