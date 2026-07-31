package com.toir.service;

import com.toir.dto.rcm.autoplan.RcmAutoPlanConfirmRequest;
import com.toir.dto.rcm.autoplan.RcmAutoPlanDecision;
import com.toir.dto.rcm.autoplan.RcmAutoPlanPreviewResponse;
import com.toir.dto.rcm.autoplan.RcmAutoPlanPreviewRow;
import com.toir.entity.PprPlan;
import com.toir.entity.PprTask;
import com.toir.enums.PriorityLevel;
import com.toir.exception.RestException;
import com.toir.repository.PprPlanRepository;
import com.toir.repository.PprTaskRepository;
import com.toir.util.AuditBuilderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RcmAutoPlannerServiceTest {
    @Mock RcmAutoPlanPreviewService previewService;
    @Mock PprPlanRepository planRepository;
    @Mock PprTaskRepository taskRepository;
    @Mock AuditBuilderService auditBuilderService;
    RcmAutoPlannerService service;

    @BeforeEach
    void setUp() {
        service = new RcmAutoPlannerService(previewService, planRepository, taskRepository, auditBuilderService);
    }

    @Test
    void rejectsStalePreviewWithoutWrites() {
        UUID planId = UUID.randomUUID();
        when(previewService.preview(30, planId)).thenReturn(preview(planId, "new", List.of()));

        assertThatThrownBy(() -> service.confirm(new RcmAutoPlanConfirmRequest(30, planId, "old")))
                .isInstanceOf(RestException.class)
                .extracting("errorCode").isEqualTo("RCM_PREVIEW_STALE");
        verify(taskRepository, never()).save(any());
    }

    @Test
    void createsOnlyRowsClassifiedCreateWithStableSourceMetadata() {
        UUID planId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        UUID regulationId = UUID.randomUUID();
        LocalDateTime start = LocalDateTime.parse("2026-08-01T08:00:00");
        String sourceKey = RcmAutoPlanPreviewService.sourceKey(planId, equipmentId, regulationId);
        RcmAutoPlanPreviewRow row = new RcmAutoPlanPreviewRow(equipmentId, "EQ-1", "Pump", 70,
                List.of(), planId, "Plan", regulationId, "Monthly service", start,
                start.plusHours(4), start.plusDays(7), PriorityLevel.HIGH, RcmAutoPlanDecision.CREATE,
                null, null, List.of(), sourceKey);
        when(previewService.preview(30, planId)).thenReturn(preview(planId, "fingerprint", List.of(row)));
        PprPlan plan = new PprPlan();
        plan.setId(planId);
        when(planRepository.findByIdAndIsDeletedFalse(planId)).thenReturn(Optional.of(plan));
        when(taskRepository.save(any())).thenAnswer(invocation -> {
            PprTask task = invocation.getArgument(0);
            task.setId(UUID.randomUUID());
            return task;
        });

        var result = service.confirm(new RcmAutoPlanConfirmRequest(30, planId, "fingerprint"));

        assertThat(result.tasksCreated()).isEqualTo(1);
        ArgumentCaptor<PprTask> captor = ArgumentCaptor.forClass(PprTask.class);
        verify(taskRepository).save(captor.capture());
        assertThat(captor.getValue().getSourceType()).isEqualTo("RCM_AUTO_PLAN");
        assertThat(captor.getValue().getSourceKey()).isEqualTo(sourceKey);
        assertThat(captor.getValue().getCode()).startsWith("RCM-EQ-1-");
    }

    private static RcmAutoPlanPreviewResponse preview(UUID planId, String fingerprint,
                                                       List<RcmAutoPlanPreviewRow> rows) {
        return new RcmAutoPlanPreviewResponse(30, planId, "Plan", Instant.now(), rows.size(),
                (int) rows.stream().filter(row -> row.decision() == RcmAutoPlanDecision.CREATE).count(),
                0, 0, 0, fingerprint, rows);
    }
}
