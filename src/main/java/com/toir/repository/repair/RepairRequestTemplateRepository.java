package com.toir.repository.repair;

import com.toir.entity.repair.RepairRequestTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface RepairRequestTemplateRepository extends JpaRepository<RepairRequestTemplate, UUID> {

    List<RepairRequestTemplate> findAllByRepairRequest_IdAndIsDeletedFalseOrderBySequenceAsc(UUID repairRequestId);

    List<RepairRequestTemplate> findAllByRepairRequest_IdInAndIsDeletedFalseOrderBySequenceAsc(Collection<UUID> repairRequestIds);
}
