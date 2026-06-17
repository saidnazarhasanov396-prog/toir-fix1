package com.toir.repository.repair;

import com.toir.entity.repair.RepairRequestTemplateAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface RepairRequestTemplateActionRepository extends JpaRepository<RepairRequestTemplateAction, UUID> {

    List<RepairRequestTemplateAction> findAllByRepairRequest_IdAndIsDeletedFalseOrderBySequenceAsc(UUID repairRequestId);

    List<RepairRequestTemplateAction> findAllByRepairRequest_IdInAndIsDeletedFalseOrderBySequenceAsc(Collection<UUID> repairRequestIds);

    boolean existsByRepairRequest_IdAndIsDeletedFalse(UUID repairRequestId);
}
