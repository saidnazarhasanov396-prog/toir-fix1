package com.toir.service.planning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.toir.entity.ApprovalRequest;
import com.toir.entity.PprPlan;
import com.toir.entity.PprTask;
import com.toir.entity.planning.PprPlanningSession;
import com.toir.entity.planning.PprPlanningVariantItem;
import com.toir.enums.ApprovalTargetType;
import com.toir.enums.CompletionEvidenceType;
import com.toir.enums.PlanStatus;
import com.toir.enums.PriorityLevel;
import com.toir.enums.planning.PprPlanningSessionStatus;
import com.toir.repository.PprPlanRepository;
import com.toir.repository.PprTaskRepository;
import com.toir.repository.planning.PprPlanningSessionRepository;
import com.toir.repository.planning.PprPlanningVariantItemRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PprPlanningMaterializationServiceTest {
    @Mock PprPlanningSessionRepository sessions;
    @Mock PprPlanningVariantItemRepository items;
    @Mock PprPlanRepository plans;
    @Mock PprTaskRepository tasks;
    @Mock PprPlanningApprovalBindingService bindingService;

    private PprPlanningMaterializationService service;
    private UUID sessionId;
    private UUID variantId;
    private UUID approvalId;
    private PprPlanningSession session;
    private ApprovalRequest approval;

    @BeforeEach
    void setUp() {
        service = new PprPlanningMaterializationService(sessions, items, plans, tasks, bindingService);
        sessionId = UUID.randomUUID();
        variantId = UUID.randomUUID();
        approvalId = UUID.randomUUID();
        session = new PprPlanningSession();
        session.setId(sessionId);
        session.setName("ППР 2027");
        session.setYear(2027);
        session.setDepartmentId(UUID.randomUUID());
        session.setStartDate(LocalDate.of(2027, 1, 1));
        session.setEndDate(LocalDate.of(2027, 12, 31));
        session.setStatus(PprPlanningSessionStatus.PENDING_APPROVAL);
        session.setSelectedVariantId(variantId);
        session.setApprovalRequestId(approvalId);
        approval = new ApprovalRequest();
        approval.setId(approvalId);
        approval.setTargetType(ApprovalTargetType.PPR_PLANNING_SESSION);
        approval.setTargetId(sessionId);
        approval.setPprPlanningVariantId(variantId);
        approval.setCalculationRevision(2L);
        approval.setCalculationContentHash("b".repeat(64));
        approval.setCalculationContentHashVersion(1);
    }

    @Test
    void createsOneApprovedPlanAndExactTasksFromBoundRevision() {
        PprPlanningVariantItem item = item();
        when(sessions.findByIdAndIsDeletedFalseForUpdate(sessionId)).thenReturn(Optional.of(session));
        when(items.findAllByVariantIdAndRevisionOrderBySourceItemKey(variantId, 2L)).thenReturn(List.of(item));
        when(plans.saveAndFlush(any(PprPlan.class))).thenAnswer(invocation -> {
            PprPlan plan = invocation.getArgument(0);
            plan.setId(UUID.randomUUID());
            return plan;
        });
        when(tasks.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PprPlan result = service.materialize(approval, UUID.randomUUID());

        assertThat(result.getStatus()).isEqualTo(PlanStatus.APPROVED);
        assertThat(result.getPlanningSessionId()).isEqualTo(sessionId);
        assertThat(result.getSourceVariantId()).isEqualTo(variantId);
        assertThat(result.getSourceVariantRevision()).isEqualTo(2L);
        assertThat(result.getTasks()).hasSize(1);
        PprTask task = result.getTasks().getFirst();
        assertThat(task.getSourceVariantItemId()).isEqualTo(item.getId());
        assertThat(task.getWorkOrderLeadDays()).isEqualTo(7);
        assertThat(task.getRequiredEvidenceTypes()).containsExactly(CompletionEvidenceType.AFTER_PHOTO);
        assertThat(session.getApprovedPlanId()).isEqualTo(result.getId());
        assertThat(session.getStatus()).isEqualTo(PprPlanningSessionStatus.APPROVED);
    }

    @Test
    void retryReturnsAlreadyMaterializedPlanWithoutCreatingRows() {
        UUID planId = UUID.randomUUID();
        session.setApprovedPlanId(planId);
        session.setStatus(PprPlanningSessionStatus.APPROVED);
        PprPlan existing = new PprPlan();
        existing.setId(planId);
        when(sessions.findByIdAndIsDeletedFalseForUpdate(sessionId)).thenReturn(Optional.of(session));
        when(plans.findByIdAndIsDeletedFalse(planId)).thenReturn(Optional.of(existing));

        assertThat(service.materialize(approval, UUID.randomUUID())).isSameAs(existing);
        verify(plans, never()).saveAndFlush(any());
        verify(tasks, never()).saveAll(any());
    }

    private PprPlanningVariantItem item() {
        PprPlanningVariantItem item = new PprPlanningVariantItem();
        item.setId(UUID.randomUUID());
        item.setEquipmentId(UUID.randomUUID());
        item.setSourceItemKey("c".repeat(64));
        item.setTaskTitleSnapshot("ТО оборудования");
        item.setScheduledStart(LocalDateTime.of(2027, 3, 10, 9, 0));
        item.setScheduledEnd(LocalDateTime.of(2027, 3, 10, 11, 0));
        item.setDueDate(LocalDateTime.of(2027, 3, 10, 11, 0));
        item.setPriority(PriorityLevel.MEDIUM);
        item.setWorkOrderLeadDays(7);
        item.setRequiredEvidenceTypes(Set.of(CompletionEvidenceType.AFTER_PHOTO));
        return item;
    }
}
