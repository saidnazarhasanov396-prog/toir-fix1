package com.toir.service;

import com.toir.entity.PprPlan;
import com.toir.entity.PprPlanTarget;
import com.toir.entity.PprTask;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.maintenance.MaintenanceRegulation;
import com.toir.dto.workorder.WorkOrderDto;
import com.toir.dto.workorder.WorkOrderRequest;
import com.toir.enums.EquipmentCategory;
import com.toir.enums.EquipmentStatus;
import com.toir.enums.MaintenanceKind;
import com.toir.enums.PeriodicityUnit;
import com.toir.enums.PlanStatus;
import com.toir.enums.PprFrequency;
import com.toir.enums.PprScheduleType;
import com.toir.enums.PprScopeType;
import com.toir.enums.PprTargetType;
import com.toir.enums.PprTaskStatus;
import com.toir.enums.PprType;
import com.toir.exception.RestException;
import com.toir.repository.PprPlanRepository;
import com.toir.repository.PprTaskRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.equipment.EquipmentAttributeDefinitionRepository;
import com.toir.repository.equipment.EquipmentAttributeValueRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.maintenance.MaintenanceRegulationAttributeConditionRepository;
import com.toir.repository.maintenance.MaintenanceRegulationRepository;
import com.toir.util.AuditBuilderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
    MaintenanceRegulationAttributeConditionRepository conditionRepository;

    @Mock
    EquipmentRepository equipmentRepository;

    @Mock
    EquipmentAttributeDefinitionRepository attributeDefinitionRepository;

    @Mock
    EquipmentAttributeValueRepository attributeValueRepository;

    @Mock
    AuditBuilderService auditBuilderService;

    @Mock
    WorkOrderRepository workOrderRepository;

    @Mock
    WorkOrderService workOrderService;

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

    @Test
    void preventiveMonthlyPlanGeneratesOnlyPreventiveCompatibleMonthlyTasks() {
        UUID planId = UUID.randomUUID();
        UUID typeId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        PprPlan plan = plan(planId, PlanStatus.DRAFT);
        plan.setPprType(PprType.PREVENTIVE_MAINTENANCE);
        plan.setScheduleType(PprScheduleType.CALENDAR);
        plan.setFrequency(PprFrequency.MONTHLY);
        MaintenanceRegulation preventive = regulation("MR-PREV", typeId, MaintenanceKind.PREVENTIVE, PeriodicityUnit.MONTH);
        MaintenanceRegulation repair = regulation("MR-REPAIR", typeId, MaintenanceKind.CURRENT_REPAIR, PeriodicityUnit.MONTH);
        MaintenanceRegulation weekly = regulation("MR-WEEK", typeId, MaintenanceKind.INSPECTION, PeriodicityUnit.WEEK);
        Equipment equipment = equipment(equipmentId, "P-101", typeId, plan.getDepartmentId());
        stubGeneration(plan, List.of(preventive, repair, weekly), List.of(equipment), List.of());

        PprGeneratorService.GenerationResult result = service.generateForPlan(planId);

        assertThat(result.created()).isEqualTo(1);
        ArgumentCaptor<PprTask> taskCaptor = ArgumentCaptor.forClass(PprTask.class);
        verify(taskRepository).save(taskCaptor.capture());
        assertThat(taskCaptor.getValue().getRegulationId()).isEqualTo(preventive.getId());
    }

    @Test
    void preventivePlanWithEquipmentTargetGeneratesOnlyForSelectedEquipment() {
        UUID planId = UUID.randomUUID();
        UUID typeId = UUID.randomUUID();
        UUID selectedEquipmentId = UUID.randomUUID();
        UUID otherEquipmentId = UUID.randomUUID();
        PprPlan plan = explicitPreventivePlan(planId);
        plan.getTargets().add(equipmentTarget(plan, selectedEquipmentId));
        MaintenanceRegulation regulation = regulation("MR-PREV", typeId, MaintenanceKind.PREVENTIVE, PeriodicityUnit.MONTH);
        Equipment selected = equipment(selectedEquipmentId, "P-101", typeId, plan.getDepartmentId());
        Equipment other = equipment(otherEquipmentId, "P-102", typeId, plan.getDepartmentId());
        stubGeneration(plan, List.of(regulation), List.of(selected, other), List.of());

        PprGeneratorService.GenerationResult result = service.generateForPlan(planId);

        assertThat(result.created()).isEqualTo(1);
        ArgumentCaptor<PprTask> taskCaptor = ArgumentCaptor.forClass(PprTask.class);
        verify(taskRepository).save(taskCaptor.capture());
        assertThat(taskCaptor.getValue().getEquipmentId()).isEqualTo(selectedEquipmentId);
    }

    @Test
    void preventivePlanWithEquipmentTypeTargetGeneratesOnlyForSelectedType() {
        UUID planId = UUID.randomUUID();
        UUID selectedTypeId = UUID.randomUUID();
        UUID otherTypeId = UUID.randomUUID();
        PprPlan plan = explicitPreventivePlan(planId);
        plan.getTargets().add(equipmentTypeTarget(plan, selectedTypeId));
        MaintenanceRegulation selectedTypeRegulation =
                regulation("MR-PREV", selectedTypeId, MaintenanceKind.PREVENTIVE, PeriodicityUnit.MONTH);
        MaintenanceRegulation otherTypeRegulation =
                regulation("MR-OTHER", otherTypeId, MaintenanceKind.PREVENTIVE, PeriodicityUnit.MONTH);
        Equipment selectedTypeEquipment = equipment(UUID.randomUUID(), "P-101", selectedTypeId, plan.getDepartmentId());
        Equipment otherTypeEquipment = equipment(UUID.randomUUID(), "M-101", otherTypeId, plan.getDepartmentId());
        stubGeneration(plan, List.of(selectedTypeRegulation, otherTypeRegulation),
                List.of(selectedTypeEquipment, otherTypeEquipment), List.of());

        PprGeneratorService.GenerationResult result = service.generateForPlan(planId);

        assertThat(result.created()).isEqualTo(1);
        ArgumentCaptor<PprTask> taskCaptor = ArgumentCaptor.forClass(PprTask.class);
        verify(taskRepository).save(taskCaptor.capture());
        assertThat(taskCaptor.getValue().getEquipmentId()).isEqualTo(selectedTypeEquipment.getId());
    }

    @Test
    void departmentScopeFiltersEquipmentByDepartment() {
        UUID planId = UUID.randomUUID();
        UUID typeId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        PprPlan plan = explicitPreventivePlan(planId);
        plan.setDepartmentId(departmentId);
        plan.setScopeType(PprScopeType.DEPARTMENT);
        MaintenanceRegulation regulation = regulation("MR-PREV", typeId, MaintenanceKind.PREVENTIVE, PeriodicityUnit.MONTH);
        Equipment inDepartment = equipment(UUID.randomUUID(), "P-101", typeId, departmentId);
        Equipment outsideDepartment = equipment(UUID.randomUUID(), "P-102", typeId, UUID.randomUUID());
        stubGeneration(plan, List.of(regulation), List.of(inDepartment, outsideDepartment), List.of());

        PprGeneratorService.GenerationResult result = service.generateForPlan(planId);

        assertThat(result.created()).isEqualTo(1);
        ArgumentCaptor<PprTask> taskCaptor = ArgumentCaptor.forClass(PprTask.class);
        verify(taskRepository).save(taskCaptor.capture());
        assertThat(taskCaptor.getValue().getEquipmentId()).isEqualTo(inDepartment.getId());
    }

    @Test
    void plannedRepairYearlyGeneratesOnlyPlannedRepairCompatibleRegulations() {
        UUID planId = UUID.randomUUID();
        UUID typeId = UUID.randomUUID();
        PprPlan plan = plan(planId, PlanStatus.DRAFT);
        plan.setStartDate(LocalDate.of(2026, 1, 1));
        plan.setEndDate(LocalDate.of(2026, 1, 31));
        plan.setPprType(PprType.PLANNED_REPAIR);
        plan.setScheduleType(PprScheduleType.CALENDAR);
        plan.setFrequency(PprFrequency.YEARLY);
        MaintenanceRegulation plannedRepair =
                regulation("MR-CURRENT", typeId, MaintenanceKind.CURRENT_REPAIR, PeriodicityUnit.YEAR);
        MaintenanceRegulation preventive =
                regulation("MR-PREV", typeId, MaintenanceKind.PREVENTIVE, PeriodicityUnit.YEAR);
        Equipment equipment = equipment(UUID.randomUUID(), "P-101", typeId, plan.getDepartmentId());
        stubGeneration(plan, List.of(plannedRepair, preventive), List.of(equipment), List.of());

        PprGeneratorService.GenerationResult result = service.generateForPlan(planId);

        assertThat(result.created()).isEqualTo(1);
        ArgumentCaptor<PprTask> taskCaptor = ArgumentCaptor.forClass(PprTask.class);
        verify(taskRepository).save(taskCaptor.capture());
        assertThat(taskCaptor.getValue().getRegulationId()).isEqualTo(plannedRepair.getId());
    }

    @Test
    void operatingHoursPlanIsRejectedWithoutGeneratingTasks() {
        UUID planId = UUID.randomUUID();
        PprPlan plan = plan(planId, PlanStatus.DRAFT);
        plan.setPprType(PprType.PLANNED_REPAIR);
        plan.setScheduleType(PprScheduleType.OPERATING_HOURS);
        plan.setIntervalHours(10_000L);
        when(planRepository.findByIdAndIsDeletedFalse(planId)).thenReturn(Optional.of(plan));

        assertThatThrownBy(() -> service.generateForPlan(planId))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).isEqualTo("Operating-hours PPR generation is not implemented yet");
                });
        verify(taskRepository, never()).save(any(PprTask.class));
    }

    @Test
    void capitalRepairOneTimeWithEquipmentTypeTargetGeneratesOnlyOverhaulForMatchingEquipmentAndPlanWindow() {
        UUID planId = UUID.randomUUID();
        UUID typeId = UUID.randomUUID();
        PprPlan plan = plan(planId, PlanStatus.DRAFT);
        plan.setPprType(PprType.CAPITAL_REPAIR);
        plan.setScheduleType(PprScheduleType.ONE_TIME);
        plan.setScopeType(PprScopeType.ENTERPRISE);
        plan.setDepartmentId(null);
        plan.getTargets().add(equipmentTypeTarget(plan, typeId));
        MaintenanceRegulation overhaul = regulation("MR-OVERHAUL", typeId, MaintenanceKind.OVERHAUL, PeriodicityUnit.YEAR);
        MaintenanceRegulation preventive = regulation("MR-PREV", typeId, MaintenanceKind.PREVENTIVE, PeriodicityUnit.MONTH);
        Equipment equipment = equipment(UUID.randomUUID(), "P-101", typeId, null);
        stubGeneration(plan, List.of(overhaul, preventive), List.of(equipment), List.of());

        PprGeneratorService.GenerationResult result = service.generateForPlan(planId);

        assertThat(result.created()).isEqualTo(1);
        ArgumentCaptor<PprTask> taskCaptor = ArgumentCaptor.forClass(PprTask.class);
        verify(taskRepository).save(taskCaptor.capture());
        PprTask task = taskCaptor.getValue();
        assertThat(task.getRegulationId()).isEqualTo(overhaul.getId());
        assertThat(task.getScheduledStart()).isEqualTo(plan.getStartDate().atTime(LocalTime.of(9, 0)));
        assertThat(task.getScheduledEnd()).isEqualTo(plan.getEndDate().atTime(LocalTime.of(18, 0)));
    }

    @Test
    void runningGeneratorAgainSkipsExistingPlanRegulationEquipmentTask() {
        UUID planId = UUID.randomUUID();
        UUID typeId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        PprPlan plan = explicitPreventivePlan(planId);
        MaintenanceRegulation regulation = regulation("MR-PREV", typeId, MaintenanceKind.PREVENTIVE, PeriodicityUnit.MONTH);
        Equipment equipment = equipment(equipmentId, "P-101", typeId, plan.getDepartmentId());
        PprTask existing = new PprTask();
        existing.setId(UUID.randomUUID());
        existing.setCode("ANY-CODE");
        existing.setPlan(plan);
        existing.setRegulationId(regulation.getId());
        existing.setEquipmentId(equipmentId);
        stubGeneration(plan, List.of(regulation), List.of(equipment), List.of(existing), false);

        PprGeneratorService.GenerationResult result = service.generateForPlan(planId);

        assertThat(result.created()).isZero();
        assertThat(result.skipped()).isEqualTo(1);
        verify(taskRepository, never()).save(any(PprTask.class));
    }

    @Test
    void approvedPlanWithApprovedTaskCreatesLinkedWorkOrder() {
        UUID planId = UUID.randomUUID();
        UUID createdById = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID regulationId = UUID.randomUUID();
        UUID workOrderId = UUID.randomUUID();
        PprPlan plan = executablePlan(planId, PprType.PREVENTIVE_MAINTENANCE, departmentId);
        PprTask task = approvedTask(plan, regulationId, equipmentId);
        Equipment equipment = equipment(equipmentId, "P-101", UUID.randomUUID(), departmentId);
        MaintenanceRegulation regulation =
                regulation("MR-PREV", equipment.getEquipmentTypeId(), MaintenanceKind.PREVENTIVE, PeriodicityUnit.MONTH);
        regulation.setId(regulationId);
        stubWorkOrderGeneration(plan, List.of(task), List.of(equipment), List.of(regulation));
        when(workOrderRepository.existsByNumberAndIsDeletedFalse(any())).thenReturn(false);
        when(workOrderService.create(any())).thenReturn(workOrderDto(workOrderId, task));

        PprGeneratorService.WorkOrderGenerationResult result =
                service.generateWorkOrdersForPlan(planId, createdById);

        assertThat(result.planId()).isEqualTo(planId);
        assertThat(result.createdCount()).isEqualTo(1);
        assertThat(result.skippedCount()).isZero();
        assertThat(result.createdWorkOrderIds()).containsExactly(workOrderId);
        ArgumentCaptor<WorkOrderRequest> requestCaptor = ArgumentCaptor.forClass(WorkOrderRequest.class);
        verify(workOrderService).create(requestCaptor.capture());
        WorkOrderRequest request = requestCaptor.getValue();
        assertThat(request.pprTaskId()).isEqualTo(task.getId());
        assertThat(request.equipmentId()).isEqualTo(equipmentId);
        assertThat(request.departmentId()).isEqualTo(departmentId);
        assertThat(request.createdById()).isEqualTo(createdById);
        assertThat(request.startPlannedAt()).isEqualTo(task.getScheduledStart().atZone(ZoneId.systemDefault()).toInstant());
        assertThat(request.endPlannedAt()).isEqualTo(task.getScheduledEnd().atZone(ZoneId.systemDefault()).toInstant());
        assertThat(request.type()).isEqualTo(com.toir.enums.WorkOrderType.PLANNED);
        assertThat(request.workType()).isEqualTo(com.toir.enums.WorkType.REPAIR);
        assertThat(request.summary()).contains(plan.getCode(), plan.getName(), task.getCode());
    }

    @Test
    void preventiveInspectionTaskMapsToInspectionDiagnosticsWorkOrder() {
        UUID planId = UUID.randomUUID();
        UUID createdById = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        UUID regulationId = UUID.randomUUID();
        PprPlan plan = executablePlan(planId, PprType.PREVENTIVE_MAINTENANCE, departmentId);
        PprTask task = approvedTask(plan, regulationId, equipmentId);
        Equipment equipment = equipment(equipmentId, "P-101", UUID.randomUUID(), departmentId);
        MaintenanceRegulation regulation =
                regulation("MR-INSP", equipment.getEquipmentTypeId(), MaintenanceKind.INSPECTION, PeriodicityUnit.MONTH);
        regulation.setId(regulationId);
        stubWorkOrderGeneration(plan, List.of(task), List.of(equipment), List.of(regulation));
        when(workOrderRepository.existsByNumberAndIsDeletedFalse(any())).thenReturn(false);
        when(workOrderService.create(any())).thenReturn(workOrderDto(UUID.randomUUID(), task));

        service.generateWorkOrdersForPlan(planId, createdById);

        ArgumentCaptor<WorkOrderRequest> requestCaptor = ArgumentCaptor.forClass(WorkOrderRequest.class);
        verify(workOrderService).create(requestCaptor.capture());
        assertThat(requestCaptor.getValue().type()).isEqualTo(com.toir.enums.WorkOrderType.INSPECTION);
        assertThat(requestCaptor.getValue().workType()).isEqualTo(com.toir.enums.WorkType.DIAGNOSTICS);
    }

    @Test
    void plannedRepairTaskMapsToPlannedWorkOrder() {
        UUID planId = UUID.randomUUID();
        UUID createdById = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        UUID regulationId = UUID.randomUUID();
        PprPlan plan = executablePlan(planId, PprType.PLANNED_REPAIR, departmentId);
        PprTask task = approvedTask(plan, regulationId, equipmentId);
        Equipment equipment = equipment(equipmentId, "P-101", UUID.randomUUID(), departmentId);
        MaintenanceRegulation regulation =
                regulation("MR-CURRENT", equipment.getEquipmentTypeId(), MaintenanceKind.CURRENT_REPAIR, PeriodicityUnit.YEAR);
        regulation.setId(regulationId);
        stubWorkOrderGeneration(plan, List.of(task), List.of(equipment), List.of(regulation));
        when(workOrderRepository.existsByNumberAndIsDeletedFalse(any())).thenReturn(false);
        when(workOrderService.create(any())).thenReturn(workOrderDto(UUID.randomUUID(), task));

        service.generateWorkOrdersForPlan(planId, createdById);

        ArgumentCaptor<WorkOrderRequest> requestCaptor = ArgumentCaptor.forClass(WorkOrderRequest.class);
        verify(workOrderService).create(requestCaptor.capture());
        assertThat(requestCaptor.getValue().type()).isEqualTo(com.toir.enums.WorkOrderType.PLANNED);
        assertThat(requestCaptor.getValue().workType()).isEqualTo(com.toir.enums.WorkType.REPAIR);
    }

    @Test
    void capitalRepairTaskMapsToOverhaulWorkOrder() {
        UUID planId = UUID.randomUUID();
        UUID createdById = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        UUID regulationId = UUID.randomUUID();
        PprPlan plan = executablePlan(planId, PprType.CAPITAL_REPAIR, departmentId);
        PprTask task = approvedTask(plan, regulationId, equipmentId);
        Equipment equipment = equipment(equipmentId, "P-101", UUID.randomUUID(), departmentId);
        MaintenanceRegulation regulation =
                regulation("MR-OVERHAUL", equipment.getEquipmentTypeId(), MaintenanceKind.OVERHAUL, PeriodicityUnit.YEAR);
        regulation.setId(regulationId);
        stubWorkOrderGeneration(plan, List.of(task), List.of(equipment), List.of(regulation));
        when(workOrderRepository.existsByNumberAndIsDeletedFalse(any())).thenReturn(false);
        when(workOrderService.create(any())).thenReturn(workOrderDto(UUID.randomUUID(), task));

        service.generateWorkOrdersForPlan(planId, createdById);

        ArgumentCaptor<WorkOrderRequest> requestCaptor = ArgumentCaptor.forClass(WorkOrderRequest.class);
        verify(workOrderService).create(requestCaptor.capture());
        assertThat(requestCaptor.getValue().type()).isEqualTo(com.toir.enums.WorkOrderType.OVERHAUL);
        assertThat(requestCaptor.getValue().workType()).isEqualTo(com.toir.enums.WorkType.REPAIR);
    }

    @Test
    void taskWithoutEquipmentIsSkippedWhenGeneratingWorkOrders() {
        UUID planId = UUID.randomUUID();
        PprPlan plan = executablePlan(planId, PprType.PREVENTIVE_MAINTENANCE, UUID.randomUUID());
        PprTask task = approvedTask(plan, UUID.randomUUID(), null);
        stubWorkOrderGeneration(plan, List.of(task), List.of(), List.of());

        PprGeneratorService.WorkOrderGenerationResult result =
                service.generateWorkOrdersForPlan(planId, UUID.randomUUID());

        assertThat(result.createdCount()).isZero();
        assertThat(result.skippedCount()).isEqualTo(1);
        assertThat(result.skippedItems()).extracting(PprGeneratorService.WorkOrderGenerationSkippedItem::reason)
                .containsExactly("TASK_EQUIPMENT_MISSING");
        verify(workOrderService, never()).create(any());
    }

    @Test
    void existingWorkOrderForTaskIsSkippedWhenGeneratingWorkOrders() {
        UUID planId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        PprPlan plan = executablePlan(planId, PprType.PREVENTIVE_MAINTENANCE, departmentId);
        PprTask task = approvedTask(plan, UUID.randomUUID(), equipmentId);
        stubWorkOrderGeneration(plan, List.of(task), List.of(), List.of());
        when(workOrderRepository.existsByPprTaskIdAndIsDeletedFalse(task.getId())).thenReturn(true);

        PprGeneratorService.WorkOrderGenerationResult result =
                service.generateWorkOrdersForPlan(planId, UUID.randomUUID());

        assertThat(result.createdCount()).isZero();
        assertThat(result.skippedCount()).isEqualTo(1);
        assertThat(result.skippedItems()).extracting(PprGeneratorService.WorkOrderGenerationSkippedItem::reason)
                .containsExactly("WORK_ORDER_ALREADY_EXISTS");
        verify(workOrderService, never()).create(any());
    }

    @Test
    void nonApprovedPprTaskIsSkippedWhenGeneratingWorkOrders() {
        UUID planId = UUID.randomUUID();
        PprPlan plan = executablePlan(planId, PprType.PREVENTIVE_MAINTENANCE, UUID.randomUUID());
        PprTask task = approvedTask(plan, UUID.randomUUID(), UUID.randomUUID());
        task.setStatus(PprTaskStatus.PLANNED);
        stubWorkOrderGeneration(plan, List.of(task), List.of(), List.of());

        PprGeneratorService.WorkOrderGenerationResult result =
                service.generateWorkOrdersForPlan(planId, UUID.randomUUID());

        assertThat(result.createdCount()).isZero();
        assertThat(result.skippedCount()).isEqualTo(1);
        assertThat(result.skippedItems()).extracting(PprGeneratorService.WorkOrderGenerationSkippedItem::reason)
                .containsExactly("TASK_STATUS_NOT_APPROVED");
        verify(workOrderService, never()).create(any());
    }

    @Test
    void draftPlanCannotGenerateWorkOrders() {
        UUID planId = UUID.randomUUID();
        PprPlan plan = plan(planId, PlanStatus.DRAFT);
        when(planRepository.findByIdAndIsDeletedFalse(planId)).thenReturn(Optional.of(plan));

        assertThatThrownBy(() -> service.generateWorkOrdersForPlan(planId, UUID.randomUUID()))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("APPROVED or IN_PROGRESS");
                });

        verify(workOrderService, never()).create(any());
    }

    private PprPlan plan(UUID id, PlanStatus status) {
        PprPlan plan = new PprPlan();
        plan.setId(id);
        plan.setCode("PPR-2026-0001");
        plan.setName("Monthly PPR plan");
        plan.setStartDate(LocalDate.of(2026, 6, 1));
        plan.setEndDate(LocalDate.of(2026, 6, 30));
        plan.setStatus(status);
        plan.setDepartmentId(UUID.randomUUID());
        plan.setCreatedById(UUID.randomUUID());
        plan.setTasks(new ArrayList<>());
        plan.setTargets(new ArrayList<>());
        return plan;
    }

    private PprPlan explicitPreventivePlan(UUID id) {
        PprPlan plan = plan(id, PlanStatus.DRAFT);
        plan.setPprType(PprType.PREVENTIVE_MAINTENANCE);
        plan.setScheduleType(PprScheduleType.CALENDAR);
        plan.setFrequency(PprFrequency.MONTHLY);
        plan.setScopeType(PprScopeType.DEPARTMENT);
        return plan;
    }

    private PprPlan executablePlan(UUID id, PprType pprType, UUID departmentId) {
        PprPlan plan = plan(id, PlanStatus.APPROVED);
        plan.setPprType(pprType);
        plan.setDepartmentId(departmentId);
        return plan;
    }

    private PprTask approvedTask(PprPlan plan, UUID regulationId, UUID equipmentId) {
        PprTask task = new PprTask();
        task.setId(UUID.randomUUID());
        task.setCode("PT-2026-0001");
        task.setPlan(plan);
        task.setRegulationId(regulationId);
        task.setEquipmentId(equipmentId);
        task.setTitle("PPR task");
        task.setScheduledStart(java.time.LocalDateTime.of(2026, 6, 10, 9, 0));
        task.setScheduledEnd(java.time.LocalDateTime.of(2026, 6, 10, 18, 0));
        task.setDueDate(java.time.LocalDateTime.of(2026, 6, 11, 9, 0));
        task.setStatus(PprTaskStatus.APPROVED);
        task.setPriority(com.toir.enums.PriorityLevel.HIGH);
        task.setPlannedLaborHours(8.0);
        return task;
    }

    private WorkOrderDto workOrderDto(UUID workOrderId, PprTask task) {
        return new WorkOrderDto(
                workOrderId,
                "WO-PPR-2026-0001",
                task.getTitle(),
                task.getEquipmentId(),
                task.getPlan().getDepartmentId(),
                "Equipment",
                "Department",
                null,
                null,
                task.getId(),
                null,
                com.toir.enums.WorkOrderStatus.DRAFT,
                com.toir.enums.WorkOrderType.PLANNED,
                com.toir.enums.WorkType.REPAIR,
                task.getPriority(),
                task.getScheduledStart().atZone(ZoneId.systemDefault()).toInstant(),
                task.getScheduledEnd().atZone(ZoneId.systemDefault()).toInstant(),
                null,
                null,
                null,
                null,
                null,
                UUID.randomUUID(),
                null,
                null,
                null,
                null,
                List.of(),
                null,
                null,
                0,
                0
        );
    }

    private void stubWorkOrderGeneration(PprPlan plan,
                                         List<PprTask> tasks,
                                         List<Equipment> equipment,
                                         List<MaintenanceRegulation> regulations) {
        when(planRepository.findByIdAndIsDeletedFalse(plan.getId())).thenReturn(Optional.of(plan));
        when(taskRepository.findAllByPlanIdAndIsDeletedFalseOrderByScheduledStartAscIdAsc(plan.getId())).thenReturn(tasks);
        if (!equipment.isEmpty()) {
            when(equipmentRepository.findAllByIdInAndIsDeletedFalse(
                    equipment.stream().map(Equipment::getId).toList()
            )).thenReturn(equipment);
        }
        if (!regulations.isEmpty()) {
            when(regulationRepository.findAllByIdInAndIsDeletedFalse(
                    regulations.stream().map(MaintenanceRegulation::getId).toList()
            )).thenReturn(regulations);
        }
    }

    private void stubGeneration(PprPlan plan,
                                List<MaintenanceRegulation> regulations,
                                List<Equipment> equipment,
                                List<PprTask> existingTasks) {
        stubGeneration(plan, regulations, equipment, existingTasks, true);
    }

    private void stubGeneration(PprPlan plan,
                                List<MaintenanceRegulation> regulations,
                                List<Equipment> equipment,
                                List<PprTask> existingTasks,
                                boolean stubWrites) {
        when(planRepository.findByIdAndIsDeletedFalse(plan.getId())).thenReturn(Optional.of(plan));
        when(regulationRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(regulations);
        when(conditionRepository.findAllByRegulationIdInAndIsDeletedFalse(anyCollection())).thenReturn(List.of());
        when(equipmentRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(equipment);
        when(attributeDefinitionRepository.findAllByEquipmentTypeIdInAndIsDeletedFalse(anyCollection())).thenReturn(List.of());
        when(attributeValueRepository.findAllByEquipmentIdInAndIsDeletedFalse(anyCollection())).thenReturn(List.of());
        when(taskRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(existingTasks);
        if (!stubWrites) {
            return;
        }
        when(taskRepository.save(any(PprTask.class))).thenAnswer(invocation -> {
            PprTask task = invocation.getArgument(0);
            task.setId(UUID.randomUUID());
            return task;
        });
        when(planRepository.save(any(PprPlan.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private MaintenanceRegulation regulation(String code,
                                             UUID typeId,
                                             MaintenanceKind kind,
                                             PeriodicityUnit periodicityUnit) {
        MaintenanceRegulation regulation = new MaintenanceRegulation();
        regulation.setId(UUID.randomUUID());
        regulation.setCode(code);
        regulation.setName(code + " task");
        regulation.setEquipmentTypeId(typeId);
        regulation.setMaintenanceKind(kind);
        regulation.setNormativeLaborHours(8.0);
        regulation.setActive(true);
        regulation.setPeriodicityUnit(periodicityUnit);
        regulation.setPeriodicityValue(1);
        regulation.setRequiresShutdown(false);
        return regulation;
    }

    private Equipment equipment(UUID id, String code, UUID typeId, UUID departmentId) {
        Equipment equipment = new Equipment();
        equipment.setId(id);
        equipment.setCode(code);
        equipment.setName("Equipment " + code);
        equipment.setInventoryNumber("INV-" + code);
        equipment.setEquipmentTypeId(typeId);
        equipment.setDepartmentId(departmentId);
        equipment.setStatus(EquipmentStatus.ACTIVE);
        equipment.setCategory(EquipmentCategory.PRODUCTION_EQUIPMENT);
        return equipment;
    }

    private PprPlanTarget equipmentTarget(PprPlan plan, UUID equipmentId) {
        PprPlanTarget target = new PprPlanTarget();
        target.setId(UUID.randomUUID());
        target.setPlan(plan);
        target.setTargetType(PprTargetType.EQUIPMENT);
        target.setEquipmentId(equipmentId);
        return target;
    }

    private PprPlanTarget equipmentTypeTarget(PprPlan plan, UUID equipmentTypeId) {
        PprPlanTarget target = new PprPlanTarget();
        target.setId(UUID.randomUUID());
        target.setPlan(plan);
        target.setTargetType(PprTargetType.EQUIPMENT_TYPE);
        target.setEquipmentTypeId(equipmentTypeId);
        return target;
    }
}
