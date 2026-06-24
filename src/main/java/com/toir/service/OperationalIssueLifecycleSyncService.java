package com.toir.service;

import com.toir.entity.defects.Defect;
import com.toir.enums.DefectStatus;
import com.toir.repository.defects.DefectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OperationalIssueLifecycleSyncService {

    private static final Set<DefectStatus> TERMINAL_DEFECT_STATUSES = EnumSet.of(
            DefectStatus.RESOLVED,
            DefectStatus.CLOSED,
            DefectStatus.CANCELLED
    );

    private final OperationalIssueService operationalIssueService;
    private final DefectRepository defectRepository;

    @Transactional
    public void resolveDefectIssueIfTerminal(Defect defect, String resolutionMessage) {
        if (defect == null || defect.getId() == null || !TERMINAL_DEFECT_STATUSES.contains(defect.getStatus())) {
            return;
        }
        operationalIssueService.resolveOpen("Defect", defect.getId(), resolutionMessage);
    }

    @Transactional
    public void resolveRepairRequestIssue(UUID repairRequestId, String resolutionMessage) {
        if (repairRequestId == null) {
            return;
        }
        operationalIssueService.resolveOpen("RepairRequest", repairRequestId, resolutionMessage);
    }

    @Transactional
    public void sweepRepairRequest(UUID repairRequestId, String resolutionMessage) {
        if (repairRequestId == null) {
            return;
        }
        defectRepository.findAllByRepairRequestIdAndIsDeletedFalseOrderByUpdatedAtDesc(repairRequestId)
                .forEach(defect -> resolveDefectIssueIfTerminal(defect, resolutionMessage));
        resolveRepairRequestIssue(repairRequestId, resolutionMessage);
    }
}
