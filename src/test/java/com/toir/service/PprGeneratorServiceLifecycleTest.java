package com.toir.service;

import com.toir.entity.PprPlan;
import com.toir.entity.PprTask;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.maintenance.MaintenanceRegulation;
import com.toir.enums.PlanStatus;
import com.toir.exception.RestException;
import com.toir.repository.PprPlanRepository;
import com.toir.repository.PprTaskRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.maintenance.MaintenanceRegulationRepository;
import com.toir.util.AuditBuilderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PprGeneratorServiceLifecycleTest {

    @Mock
    PprPlanRepository planRepository;

    @Mock
    PprTaskRepository taskRepository;

    @Mock
    MaintenanceRegulationRepository regulationRepository;

    @Mock
    EquipmentRepository equipmentRepository;

    @Mock
    AuditBuilderService auditBuilderService;

    @InjectMocks
    PprGeneratorService service;

    @Test
    void generateForPlanSucceedsForDraftAndGeneratedPlans() {
        for (PlanStatus status : new PlanStatus[]{PlanStatus.DRAFT, PlanStatus.GENERATED}) {
            UUID planId = UUID.randomUUID();
            when(planRepository.findByIdAndIsDeletedFalse(planId)).thenReturn(Optional.of(plan(planId, status)));
            when(regulationRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of());
            when(equipmentRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of());
            when(taskRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of());

            PprGeneratorService.GenerationResult result = service.generateForPlan(planId);

            assertThat(result.planId()).isEqualTo(planId);
            assertThat(result.created()).isZero();
            assertThat(result.skipped()).isZero();
        }
    }

    @Test
    void generateForPlanBlockedForApprovedInProgressClosedAndCancelledPlans() {
        for (PlanStatus status : new PlanStatus[]{
                PlanStatus.APPROVED,
                PlanStatus.IN_PROGRESS,
                PlanStatus.CLOSED,
                PlanStatus.CANCELLED
        }) {
            UUID planId = UUID.randomUUID();
            when(planRepository.findByIdAndIsDeletedFalse(planId)).thenReturn(Optional.of(plan(planId, status)));

            assertThatThrownBy(() -> service.generateForPlan(planId))
                    .isInstanceOfSatisfying(RestException.class, ex -> {
                        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                        assertThat(ex.getMessage()).contains("DRAFT or GENERATED");
                    });
        }

        verify(regulationRepository, never()).findAllByIsDeletedFalseOrderByUpdatedAtDesc();
        verify(equipmentRepository, never()).findAllByIsDeletedFalseOrderByUpdatedAtDesc();
        verify(taskRepository, never()).save(org.mockito.ArgumentMatchers.any(PprTask.class));
    }

    private PprPlan plan(UUID id, PlanStatus status) {
        PprPlan plan = new PprPlan();
        plan.setId(id);
        plan.setCode("PPR-2026-0001");
        plan.setName("Monthly PPR plan");
        plan.setYear(2026);
        plan.setMonth(6);
        plan.setStatus(status);
        return plan;
    }
}
