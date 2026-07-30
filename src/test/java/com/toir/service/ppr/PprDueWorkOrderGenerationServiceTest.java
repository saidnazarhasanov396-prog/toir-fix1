package com.toir.service.ppr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.toir.config.PprLifecycleProperties;
import com.toir.dto.workorder.WorkOrderRequest;
import com.toir.entity.PprPlan;
import com.toir.entity.PprTask;
import com.toir.entity.equipment.Equipment;
import com.toir.enums.PlanStatus;
import com.toir.enums.PprTaskStatus;
import com.toir.enums.PprType;
import com.toir.repository.PprTaskRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.maintenance.EquipmentMaintenanceRuleRepository;
import com.toir.repository.maintenance.MaintenanceRegulationRepository;
import com.toir.service.WorkOrderNumberService;
import com.toir.service.WorkOrderService;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PprDueWorkOrderGenerationServiceTest {

    @Mock PprTaskRepository tasks;
    @Mock WorkOrderRepository workOrders;
    @Mock EquipmentRepository equipment;
    @Mock MaintenanceRegulationRepository regulations;
    @Mock EquipmentMaintenanceRuleRepository rules;
    @Mock WorkOrderNumberService numbers;
    @Mock WorkOrderService workOrderService;

    private PprLifecycleProperties properties;
    private PprDueWorkOrderGenerationService service;

    @BeforeEach
    void setUp() {
        properties = new PprLifecycleProperties();
        properties.setDueWorkOrderGenerationEnabled(true);
        properties.getWorkOrderGeneration().setActorId(UUID.randomUUID());
        properties.getWorkOrderGeneration().setTimezone(ZoneId.of("Asia/Tashkent"));
        properties.getWorkOrderGeneration().setBatchSize(25);
        service = new PprDueWorkOrderGenerationService(
                tasks,
                workOrders,
                equipment,
                regulations,
                rules,
                numbers,
                workOrderService,
                new PprWorkOrderEligibilityService(),
                properties);
    }

    @Test
    void createsOneCanonicalGeneratedWorkOrderForDueTask() {
        Instant now = Instant.parse("2027-03-03T04:00:00Z");
        PprTask task = dueTask();
        Equipment machine = new Equipment();
        machine.setId(task.getEquipmentId());
        machine.setDepartmentId(task.getPlan().getDepartmentId());
        when(tasks.findDueForWorkOrderGeneration(
                LocalDateTime.ofInstant(now, ZoneId.of("Asia/Tashkent")), 25))
                .thenReturn(List.of(task));
        when(equipment.findByIdAndIsDeletedFalse(task.getEquipmentId())).thenReturn(Optional.of(machine));
        when(numbers.nextPprNumber()).thenReturn("WO-PPR-2027-0001");

        PprDueWorkOrderGenerationService.GenerationRunResult result = service.generateDue(now);

        assertThat(result.createdCount()).isEqualTo(1);
        assertThat(result.skipped()).isEmpty();
        ArgumentCaptor<WorkOrderRequest> request = ArgumentCaptor.forClass(WorkOrderRequest.class);
        verify(workOrderService).createGenerated(
                request.capture(), eq(properties.getWorkOrderGeneration().getActorId()));
        assertThat(request.getValue().pprTaskId()).isEqualTo(task.getId());
        assertThat(request.getValue().generationKey()).isEqualTo("ppr-task:" + task.getId());
        assertThat(request.getValue().startPlannedAt())
                .isEqualTo(task.getScheduledStart().atZone(ZoneId.of("Asia/Tashkent")).toInstant());
    }

    @Test
    void skipsCandidateWhenAWorkOrderAlreadyExists() {
        Instant now = Instant.parse("2027-03-03T04:00:00Z");
        PprTask task = dueTask();
        when(tasks.findDueForWorkOrderGeneration(any(LocalDateTime.class), eq(25))).thenReturn(List.of(task));
        when(workOrders.existsByPprTaskIdAndIsDeletedFalse(task.getId())).thenReturn(true);

        PprDueWorkOrderGenerationService.GenerationRunResult result = service.generateDue(now);

        assertThat(result.createdCount()).isZero();
        assertThat(result.skipped()).singleElement()
                .extracting(PprDueWorkOrderGenerationService.SkippedItem::reason)
                .isEqualTo("WORK_ORDER_ALREADY_EXISTS");
        verify(workOrderService, never()).createGenerated(any(), any());
    }

    @Test
    void disabledFeatureDoesNotQueryOrCreateAnything() {
        properties.setDueWorkOrderGenerationEnabled(false);

        PprDueWorkOrderGenerationService.GenerationRunResult result = service.generateDue(Instant.now());

        assertThat(result.createdCount()).isZero();
        verify(tasks, never()).findDueForWorkOrderGeneration(any(), any(Integer.class));
        verify(workOrderService, never()).createGenerated(any(), any());
    }

    @Test
    void enabledSchedulerRequiresExplicitTechnicalActor() {
        properties.getWorkOrderGeneration().setActorId(null);

        assertThatThrownBy(() -> service.generateDue(Instant.now()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("actor-id");
    }

    private PprTask dueTask() {
        PprPlan plan = new PprPlan();
        plan.setId(UUID.randomUUID());
        plan.setCode("PPR-2027-0001");
        plan.setName("ППР 2027");
        plan.setStatus(PlanStatus.APPROVED);
        plan.setPprType(PprType.PREVENTIVE_MAINTENANCE);
        plan.setDepartmentId(UUID.randomUUID());

        PprTask task = new PprTask();
        task.setId(UUID.randomUUID());
        task.setCode("PT-001");
        task.setPlan(plan);
        task.setTitle("Плановое ТО");
        task.setEquipmentId(UUID.randomUUID());
        task.setStatus(PprTaskStatus.APPROVED);
        task.setScheduledStart(LocalDateTime.of(2027, 3, 10, 9, 0));
        task.setScheduledEnd(LocalDateTime.of(2027, 3, 10, 11, 0));
        task.setWorkOrderLeadDays(7);
        return task;
    }
}
