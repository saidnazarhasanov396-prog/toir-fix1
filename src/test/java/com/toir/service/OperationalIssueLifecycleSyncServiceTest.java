package com.toir.service;

import com.toir.entity.defects.Defect;
import com.toir.enums.DefectStatus;
import com.toir.repository.defects.DefectRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OperationalIssueLifecycleSyncServiceTest {

    @Mock
    OperationalIssueService operationalIssueService;

    @Mock
    DefectRepository defectRepository;

    @InjectMocks
    OperationalIssueLifecycleSyncService service;

    @Test
    void resolveDefectIssueIfTerminalResolvesResolvedDefectIssue() {
        UUID defectId = UUID.randomUUID();
        Defect defect = defect(defectId, DefectStatus.RESOLVED);

        service.resolveDefectIssueIfTerminal(defect, "Defect resolved.");

        verify(operationalIssueService).resolveOpen("Defect", defectId, "Defect resolved.");
    }

    @Test
    void resolveDefectIssueIfTerminalSkipsOpenDefectIssue() {
        UUID defectId = UUID.randomUUID();
        Defect defect = defect(defectId, DefectStatus.OPEN);

        service.resolveDefectIssueIfTerminal(defect, "Defect resolved.");

        verify(operationalIssueService, never()).resolveOpen("Defect", defectId, "Defect resolved.");
    }

    @Test
    void sweepRepairRequestResolvesOnlyTerminalLinkedDefectsAndRepairRequestIssue() {
        UUID repairRequestId = UUID.randomUUID();
        UUID resolvedDefectId = UUID.randomUUID();
        UUID closedDefectId = UUID.randomUUID();
        UUID openDefectId = UUID.randomUUID();
        Defect resolved = defect(resolvedDefectId, DefectStatus.RESOLVED);
        Defect closed = defect(closedDefectId, DefectStatus.CLOSED);
        Defect open = defect(openDefectId, DefectStatus.OPEN);
        when(defectRepository.findAllByRepairRequestIdAndIsDeletedFalseOrderByUpdatedAtDesc(repairRequestId))
                .thenReturn(List.of(resolved, closed, open));

        service.sweepRepairRequest(repairRequestId, "Repair request completed.");

        verify(operationalIssueService).resolveOpen("Defect", resolvedDefectId, "Repair request completed.");
        verify(operationalIssueService).resolveOpen("Defect", closedDefectId, "Repair request completed.");
        verify(operationalIssueService, never()).resolveOpen("Defect", openDefectId, "Repair request completed.");
        verify(operationalIssueService).resolveOpen("RepairRequest", repairRequestId, "Repair request completed.");
    }

    @Test
    void sweepRepairRequestSkipsNullRepairRequestId() {
        service.sweepRepairRequest(null, "Repair request completed.");

        verify(defectRepository, never()).findAllByRepairRequestIdAndIsDeletedFalseOrderByUpdatedAtDesc(null);
        verify(operationalIssueService, never()).resolveOpen("RepairRequest", null, "Repair request completed.");
    }

    private static Defect defect(UUID id, DefectStatus status) {
        Defect defect = new Defect();
        defect.setId(id);
        defect.setStatus(status);
        return defect;
    }
}
