package com.toir.repository;

import com.toir.entity.WorkOrderCompletionEvidence;
import com.toir.enums.CompletionEvidenceType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkOrderCompletionEvidenceRepository
        extends JpaRepository<WorkOrderCompletionEvidence, UUID> {

    List<WorkOrderCompletionEvidence> findAllByWorkOrderIdAndIsDeletedFalse(UUID workOrderId);

    boolean existsByWorkOrderIdAndEvidenceTypeAndFileAssetIdAndIsDeletedFalse(
            UUID workOrderId,
            CompletionEvidenceType evidenceType,
            UUID fileAssetId);
}
