package com.toir.ai.repair;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.toir.dto.repairrequest.RepairRequestDto;
import com.toir.entity.repair.RepairRequest;
import com.toir.enums.CriticalityLevel;
import com.toir.enums.PriorityLevel;
import com.toir.enums.RequestSource;
import com.toir.enums.RequestStatus;
import com.toir.repository.ai.AiAnalysisRunRepository;
import com.toir.repository.defects.DefectRepository;
import com.toir.repository.repair.RepairRequestRepository;
import com.toir.service.repair.RepairRequestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiRepairRequestOrchestratorTest {

    @Mock
    RepairRequestService repairRequestService;
    @Mock
    RepairRequestRepository repairRequestRepository;
    @Mock
    DefectRepository defectRepository;
    @Mock
    AiAnalysisRunRepository analysisRunRepository;

    AiRepairRequestProperties properties = new AiRepairRequestProperties();
    AiRepairRequestOrchestrator orchestrator;
    ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        orchestrator = new AiRepairRequestOrchestrator(
                properties,
                new AiRepairConclusionParser(),
                repairRequestService,
                repairRequestRepository,
                defectRepository,
                analysisRunRepository,
                mapper
        );
        lenient().when(analysisRunRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createsRepairRequestFromVisualDefect() {
        UUID equipmentId = UUID.randomUUID();
        UUID reporterId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        when(repairRequestRepository.findByEquipmentIdAndStatusInAndIsDeletedFalse(eq(equipmentId), any()))
                .thenReturn(List.of());
        when(repairRequestRepository.existsByNumberAndIsDeletedFalse(any())).thenReturn(false);
        when(repairRequestService.create(any())).thenReturn(dto(requestId, "AI-RR-1"));

        AiRepairApplyResult result = orchestrator.apply(
                AiRepairKind.VISUAL_INSPECTION,
                equipmentId,
                null,
                reporterId,
                visualPayload("bearing_wear", "low"),
                null,
                null
        );

        assertThat(result.action()).isEqualTo(AiRepairAction.CREATED);
        assertThat(result.repairRequestId()).isEqualTo(requestId);
        ArgumentCaptor<com.toir.dto.repairrequest.RepairRequestRequest> captor =
                ArgumentCaptor.forClass(com.toir.dto.repairrequest.RepairRequestRequest.class);
        verify(repairRequestService).create(captor.capture());
        assertThat(captor.getValue().source()).isEqualTo(RequestSource.AI);
        assertThat(captor.getValue().priority()).isEqualTo(PriorityLevel.LOW);
        verify(repairRequestService).assignAiProblemKey(requestId, "bearing_wear");
    }

    @Test
    void updatesExistingOpenRequestForSameProblem() {
        UUID equipmentId = UUID.randomUUID();
        UUID reporterId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        RepairRequest existing = new RepairRequest();
        ReflectionTestUtils.setField(existing, "id", requestId);
        existing.setEquipmentId(equipmentId);
        existing.setStatus(RequestStatus.OPEN);
        existing.setAiProblemKey("bearing_wear");
        existing.setTitle("Old title");
        existing.setDescription("Old");
        existing.setPriority(PriorityLevel.LOW);
        existing.setCriticality(CriticalityLevel.LOW);
        existing.setNumber("RR-1");
        existing.setDetectedAt(Instant.now());
        when(repairRequestRepository.findByEquipmentIdAndStatusInAndIsDeletedFalse(eq(equipmentId), any()))
                .thenReturn(List.of(existing));
        when(defectRepository.findAllByRepairRequestIdInAndIsDeletedFalseOrderByUpdatedAtDesc(any()))
                .thenReturn(List.of());
        when(repairRequestService.applyAiConclusion(eq(requestId), any(), eq(false)))
                .thenReturn(dto(requestId, "RR-1"));

        AiRepairApplyResult result = orchestrator.apply(
                AiRepairKind.VISUAL_INSPECTION,
                equipmentId,
                null,
                reporterId,
                visualPayload("bearing_wear", "high"),
                null,
                null
        );

        assertThat(result.action()).isEqualTo(AiRepairAction.UPDATED);
        verify(repairRequestService, never()).create(any());
        verify(repairRequestService).applyAiConclusion(eq(requestId), any(), eq(false));
    }

    @Test
    void skipsWhenEquipmentMissing() {
        AiRepairApplyResult result = orchestrator.apply(
                AiRepairKind.VISUAL_INSPECTION,
                null,
                null,
                UUID.randomUUID(),
                visualPayload("bearing_wear", "high"),
                null,
                null
        );

        assertThat(result.action()).isEqualTo(AiRepairAction.SKIPPED);
        assertThat(result.skippedReason()).isEqualTo(AiRepairSkipReason.EQUIPMENT_MISSING);
        verify(repairRequestService, never()).create(any());
    }

    @Test
    void skipsWhenNoRepairConclusion() {
        UUID equipmentId = UUID.randomUUID();
        ObjectNode report = mapper.createObjectNode();
        report.put("sufficient_for_inspection", true);
        report.put("inspection_status", "normal");
        ObjectNode payload = mapper.createObjectNode();
        payload.set("inspection_report", report);

        AiRepairApplyResult result = orchestrator.apply(
                AiRepairKind.VISUAL_INSPECTION,
                equipmentId,
                null,
                UUID.randomUUID(),
                payload,
                null,
                null
        );

        assertThat(result.skippedReason()).isEqualTo(AiRepairSkipReason.NO_REPAIR_CONCLUSION);
        verify(repairRequestService, never()).create(any());
    }

    @Test
    void jobIdIsProcessedOnce() {
        UUID jobId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        com.toir.entity.ai.AiAnalysisRun run = new com.toir.entity.ai.AiAnalysisRun();
        run.setAction(AiRepairAction.CREATED);
        run.setRepairRequestId(requestId);
        when(analysisRunRepository.findByJobIdAndIsDeletedFalse(jobId)).thenReturn(Optional.of(run));

        AiRepairApplyResult result = orchestrator.apply(
                AiRepairKind.CAUSE_REPAIR,
                UUID.randomUUID(),
                null,
                UUID.randomUUID(),
                mapper.createObjectNode().put("repair_action", "Replace seal"),
                null,
                jobId
        );

        assertThat(result.action()).isEqualTo(AiRepairAction.CREATED);
        assertThat(result.repairRequestId()).isEqualTo(requestId);
        verify(repairRequestService, never()).create(any());
    }

    private ObjectNode visualPayload(String type, String severity) {
        ObjectNode report = mapper.createObjectNode();
        report.put("sufficient_for_inspection", true);
        report.put("highest_severity", severity);
        report.putArray("defects").addObject()
                .put("type", type)
                .put("description_uz", type)
                .put("severity", severity);
        ObjectNode payload = mapper.createObjectNode();
        payload.set("inspection_report", report);
        return payload;
    }

    private RepairRequestDto dto(UUID id, String number) {
        return new RepairRequestDto(
                id,
                number,
                "title",
                "description",
                UUID.randomUUID(),
                null,
                UUID.randomUUID(),
                null,
                null,
                UUID.randomUUID(),
                null,
                null,
                PriorityLevel.MEDIUM,
                CriticalityLevel.MEDIUM,
                RequestStatus.OPEN,
                RequestSource.AI,
                Instant.now(),
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                List.of()
        );
    }
}
