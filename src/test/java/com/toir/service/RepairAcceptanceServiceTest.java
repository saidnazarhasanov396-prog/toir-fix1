package com.toir.service;

import com.toir.dto.repairacceptance.RepairAcceptanceDecisionRequest;
import com.toir.entity.maintenance.RepairAcceptance;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.enums.RepairAcceptanceDefectStatus;
import com.toir.enums.RepairAcceptanceQualityGrade;
import com.toir.enums.RepairAcceptanceStage;
import com.toir.enums.RepairAcceptanceStatus;
import com.toir.exception.RestException;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.maintenance.RepairAcceptanceDefectRepository;
import com.toir.repository.maintenance.RepairAcceptanceRepository;
import com.toir.security.ScopeAccessService;
import com.toir.util.AuditBuilderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RepairAcceptanceServiceTest {

    @Mock
    RepairAcceptanceRepository repository;

    @Mock
    RepairAcceptanceDefectRepository defectRepository;

    @Mock
    WorkOrderRepository workOrderRepository;

    @Mock
    ScopeAccessService scopeAccessService;

    @Mock
    AuditBuilderService auditBuilderService;

    @Mock
    com.toir.repository.PprTaskRepository pprTaskRepository;

    @Mock
    com.toir.repository.PprPlanRepository pprPlanRepository;

    @Mock
    CompletionActService completionActService;

    @InjectMocks
    RepairAcceptanceService service;

    @Test
    void acceptWithRequiredRunInBeforeCompletionReturns400() {
        UUID workOrderId = UUID.randomUUID();
        UUID acceptanceId = UUID.randomUUID();
        RepairAcceptance acceptance = acceptance(workOrderId, acceptanceId);
        acceptance.setRunInRequired(true);

        stubAccess(workOrderId, acceptance);

        assertThatThrownBy(() -> service.accept(workOrderId, acceptanceId,
                new RepairAcceptanceDecisionRequest(UUID.randomUUID(), RepairAcceptanceQualityGrade.GOOD, "ok")))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Run-in must be completed");

        verify(repository, never()).save(any());
    }

    @Test
    void acceptFinalWithOpenCriticalDefectsReturns400() {
        UUID workOrderId = UUID.randomUUID();
        UUID acceptanceId = UUID.randomUUID();
        RepairAcceptance acceptance = acceptance(workOrderId, acceptanceId);
        when(defectRepository.existsByAcceptance_IdAndCriticalTrueAndStatusAndIsDeletedFalse(
                acceptanceId,
                RepairAcceptanceDefectStatus.OPEN
        )).thenReturn(true);

        stubAccess(workOrderId, acceptance);

        assertThatThrownBy(() -> service.accept(workOrderId, acceptanceId,
                new RepairAcceptanceDecisionRequest(UUID.randomUUID(), RepairAcceptanceQualityGrade.GOOD, "ok")))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("critical defects are open");

        verify(repository, never()).save(any());
    }

    @Test
    void acceptFinalWithoutBlockingEvidencePersistsAcceptedStatus() {
        UUID workOrderId = UUID.randomUUID();
        UUID acceptanceId = UUID.randomUUID();
        RepairAcceptance acceptance = acceptance(workOrderId, acceptanceId);
        when(defectRepository.existsByAcceptance_IdAndCriticalTrueAndStatusAndIsDeletedFalse(
                acceptanceId,
                RepairAcceptanceDefectStatus.OPEN
        )).thenReturn(false);
        when(repository.save(any(RepairAcceptance.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(defectRepository.findAllByAcceptance_IdAndIsDeletedFalseOrderByUpdatedAtDesc(acceptanceId))
                .thenReturn(List.of());

        stubAccess(workOrderId, acceptance);

        service.accept(workOrderId, acceptanceId,
                new RepairAcceptanceDecisionRequest(UUID.randomUUID(), RepairAcceptanceQualityGrade.GOOD, "ok"));

        verify(repository).save(any(RepairAcceptance.class));
        verify(completionActService).ensureForAcceptedFinal(workOrderId, acceptanceId, "ok");
    }

    @Test
    void rejectFinalAcceptanceReturnsCompletedWorkOrderAndPprTaskToWork() {
        UUID workOrderId = UUID.randomUUID();
        UUID acceptanceId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        RepairAcceptance acceptance = acceptance(workOrderId, acceptanceId);
        WorkOrder workOrder = new WorkOrder();
        workOrder.setId(workOrderId);
        workOrder.setDepartmentId(UUID.randomUUID());
        workOrder.setStatus(com.toir.enums.WorkOrderStatus.COMPLETED);
        workOrder.setPprTaskId(taskId);
        com.toir.entity.PprPlan plan = new com.toir.entity.PprPlan();
        plan.setId(UUID.randomUUID());
        plan.setStatus(com.toir.enums.PlanStatus.IN_PROGRESS);
        com.toir.entity.PprTask task = new com.toir.entity.PprTask();
        task.setId(taskId);
        task.setPlan(plan);
        task.setStatus(com.toir.enums.PprTaskStatus.IN_PROGRESS);

        when(workOrderRepository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(repository.findByIdAndIsDeletedFalse(acceptanceId)).thenReturn(Optional.of(acceptance));
        when(repository.save(any(RepairAcceptance.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(defectRepository.findAllByAcceptance_IdAndIsDeletedFalseOrderByUpdatedAtDesc(acceptanceId))
                .thenReturn(List.of());
        when(pprTaskRepository.findByIdAndIsDeletedFalse(taskId)).thenReturn(Optional.of(task));

        service.reject(workOrderId, acceptanceId,
                new RepairAcceptanceDecisionRequest(UUID.randomUUID(), RepairAcceptanceQualityGrade.NOT_ACCEPTED,
                        "Исправить замечания"));

        org.assertj.core.api.Assertions.assertThat(workOrder.getStatus())
                .isEqualTo(com.toir.enums.WorkOrderStatus.IN_PROGRESS);
        org.assertj.core.api.Assertions.assertThat(task.getStatus()).isEqualTo(com.toir.enums.PprTaskStatus.IN_PROGRESS);
        org.assertj.core.api.Assertions.assertThat(plan.getStatus()).isEqualTo(com.toir.enums.PlanStatus.IN_PROGRESS);
        verify(workOrderRepository).save(workOrder);
        verify(pprTaskRepository).save(task);
    }

    private void stubAccess(UUID workOrderId, RepairAcceptance acceptance) {
        WorkOrder workOrder = new WorkOrder();
        workOrder.setId(workOrderId);
        workOrder.setDepartmentId(UUID.randomUUID());
        when(workOrderRepository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(repository.findByIdAndIsDeletedFalse(acceptance.getId())).thenReturn(Optional.of(acceptance));
    }

    private RepairAcceptance acceptance(UUID workOrderId, UUID acceptanceId) {
        RepairAcceptance acceptance = new RepairAcceptance();
        acceptance.setId(acceptanceId);
        acceptance.setWorkOrderId(workOrderId);
        acceptance.setStage(RepairAcceptanceStage.FINAL);
        acceptance.setStatus(RepairAcceptanceStatus.DRAFT);
        return acceptance;
    }
}
