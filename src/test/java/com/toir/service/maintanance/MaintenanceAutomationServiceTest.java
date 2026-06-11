package com.toir.service.maintanance;

import com.toir.dto.maintenanceplanning.MaintenanceDueCalculationDto;
import com.toir.dto.workorder.WorkOrderDto;
import com.toir.dto.workorder.WorkOrderRequest;
import com.toir.entity.PprPlan;
import com.toir.entity.PprTask;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.maintenance.EquipmentMaintenanceRule;
import com.toir.entity.maintenance.MaintenanceDueEvent;
import com.toir.entity.maintenance.MaintenanceOperation;
import com.toir.entity.maintenance.MaintenanceRegulation;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.maintenance.WorkOrderTask;
import com.toir.enums.*;
import com.toir.repository.PprPlanRepository;
import com.toir.repository.PprTaskRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.maintenance.MaintenanceDueEventRepository;
import com.toir.repository.maintenance.MaintenanceOperationRepository;
import com.toir.repository.maintenance.MaintenanceRegulationRepository;
import com.toir.repository.users.UserRepository;
import com.toir.security.SecurityAccessService;
import com.toir.service.WorkOrderNumberService;
import com.toir.service.WorkOrderService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaintenanceAutomationServiceTest {

    @Mock
    EquipmentRepository equipmentRepository;

    @Mock
    MaintenanceRegulationRepository regulationRepository;

    @Mock
    MaintenanceDueEventRepository eventRepository;

    @Mock
    MaintenanceOperationRepository maintenanceOperationRepository;

    @Mock
    MaintenanceDueEventService eventService;

    @Mock
    MaintenanceDueCalculationService dueCalculationService;

    @Mock
    EquipmentMaintenanceEffectiveRuleResolver effectiveRuleResolver;

    @Mock
    PprPlanRepository pprPlanRepository;

    @Mock
    PprTaskRepository pprTaskRepository;

    @Mock
    WorkOrderRepository workOrderRepository;

    @Mock
    WorkOrderService workOrderService;

    @Mock
    WorkOrderNumberService workOrderNumberService;

    @Mock
    UserRepository userRepository;

    @Mock
    SecurityAccessService securityAccessService;

    @Mock
    MaintenanceAutomationNotificationService notificationService;

    @InjectMocks
    MaintenanceAutomationService service;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void evaluateEquipmentCreatesDueEventFirstForTrackOnlyPolicy() {
        UUID equipmentId = UUID.randomUUID();
        UUID typeId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, typeId);
        MaintenanceRegulation regulation = regulation(UUID.randomUUID(), typeId, AutomationAction.TRACK_ONLY);
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        mockEffectiveRules(equipmentId, regulation);
        when(dueCalculationService.calculate(any(EquipmentMaintenanceEffectiveRule.class))).thenReturn(due(equipmentId, regulation.getId()));
        when(eventRepository.findByScopeAndCycleKey(
                eq(equipmentId), eq(regulation.getId()), eq(null), any())).thenReturn(Optional.empty());
        when(eventService.saveEvent(any(), eq(equipment))).thenAnswer(invocation -> assignDueEventId(invocation.getArgument(0, MaintenanceDueEvent.class)));

        var result = service.evaluateEquipment(equipmentId, MaintenanceTriggerSource.CALENDAR_JOB);

        assertThat(result.events()).isEqualTo(1);
        assertThat(result.tasksCreated()).isZero();
        assertThat(result.workOrdersCreated()).isZero();
        ArgumentCaptor<MaintenanceDueEvent> eventCaptor = ArgumentCaptor.forClass(MaintenanceDueEvent.class);
        verify(eventService).saveEvent(eventCaptor.capture(), eq(equipment));
        assertThat(eventCaptor.getValue().getStatus()).isEqualTo(MaintenanceDueEventStatus.DETECTED);
        assertThat(eventCaptor.getValue().getTriggerSource()).isEqualTo(MaintenanceTriggerSource.CALENDAR_JOB);
        verify(pprTaskRepository, never()).save(any());
        verify(workOrderService, never()).create(any());
    }

    @Test
    void meterReadingSkipsCalendarOnlyRegulations() {
        UUID equipmentId = UUID.randomUUID();
        UUID typeId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, typeId);
        MaintenanceRegulation regulation = regulation(UUID.randomUUID(), typeId, AutomationAction.TRACK_ONLY);
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        mockEffectiveRules(equipmentId, regulation);

        var result = service.evaluateEquipment(equipmentId, MaintenanceTriggerSource.METER_READING);

        assertThat(result.events()).isZero();
        assertThat(result.tasksCreated()).isZero();
        assertThat(result.workOrdersCreated()).isZero();
        verify(dueCalculationService, never()).calculate(any(EquipmentMaintenanceEffectiveRule.class));
        verify(eventService, never()).saveEvent(any(), any());
        verify(workOrderService, never()).create(any());
    }

    @Test
    void evaluateEquipmentSuppressesDuplicateWhenOpenTaskAlreadyExistsForCycle() {
        UUID equipmentId = UUID.randomUUID();
        UUID typeId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, typeId);
        MaintenanceRegulation regulation = regulation(UUID.randomUUID(), typeId, AutomationAction.CREATE_TASK);
        regulation.setDuplicatePolicy(DuplicatePolicy.ONE_ITEM_PER_CYCLE);
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        mockEffectiveRules(equipmentId, regulation);
        when(dueCalculationService.calculate(any(EquipmentMaintenanceEffectiveRule.class))).thenReturn(due(equipmentId, regulation.getId()));
        when(eventRepository.findByScopeAndCycleKey(
                eq(equipmentId), eq(regulation.getId()), eq(null), any())).thenReturn(Optional.empty());
        when(pprTaskRepository.existsOpenByCycleKey(any())).thenReturn(true);
        when(eventService.saveEvent(any(), eq(equipment))).thenAnswer(invocation -> assignDueEventId(invocation.getArgument(0, MaintenanceDueEvent.class)));

        var result = service.evaluateEquipment(equipmentId, MaintenanceTriggerSource.CALENDAR_JOB);

        assertThat(result.events()).isEqualTo(1);
        assertThat(result.suppressed()).isEqualTo(1);
        ArgumentCaptor<MaintenanceDueEvent> eventCaptor = ArgumentCaptor.forClass(MaintenanceDueEvent.class);
        verify(eventService).saveEvent(eventCaptor.capture(), eq(equipment));
        assertThat(eventCaptor.getValue().getStatus()).isEqualTo(MaintenanceDueEventStatus.SUPPRESSED_DUPLICATE);
        verify(pprTaskRepository, never()).save(any());
        verify(workOrderService, never()).create(any());
    }

    @Test
    void blockedEventStaysDetectedAndDoesNotCreateDownstreamActions() {
        UUID equipmentId = UUID.randomUUID();
        UUID typeId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, typeId);
        MaintenanceRegulation regulation = regulation(UUID.randomUUID(), typeId, AutomationAction.CREATE_WORK_ORDER);
        regulation.setRequiresApproval(true);
        regulation.setTriggerMeterType(MeterType.ENGINE_HOURS);
        regulation.setTriggerMeterInterval(500.0);
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        mockEffectiveRules(equipmentId, regulation);
        when(dueCalculationService.calculate(any(EquipmentMaintenanceEffectiveRule.class))).thenReturn(blockedDue(equipmentId, regulation.getId()));
        when(eventRepository.findByScopeAndCycleKey(
                eq(equipmentId), eq(regulation.getId()), eq(null), any())).thenReturn(Optional.empty());
        when(eventService.saveEvent(any(), eq(equipment))).thenAnswer(invocation -> assignDueEventId(invocation.getArgument(0, MaintenanceDueEvent.class)));

        var result = service.evaluateEquipment(equipmentId, MaintenanceTriggerSource.METER_READING);

        assertThat(result.events()).isEqualTo(1);
        assertThat(result.blockedEvents()).isEqualTo(1);
        assertThat(result.tasksCreated()).isZero();
        assertThat(result.workOrdersCreated()).isZero();
        ArgumentCaptor<MaintenanceDueEvent> eventCaptor = ArgumentCaptor.forClass(MaintenanceDueEvent.class);
        verify(eventService).saveEvent(eventCaptor.capture(), eq(equipment));
        assertThat(eventCaptor.getValue().getDueStatus()).isEqualTo(MaintenanceDueStatus.BLOCKED);
        assertThat(eventCaptor.getValue().getStatus()).isEqualTo(MaintenanceDueEventStatus.DETECTED);
        verify(pprTaskRepository, never()).save(any());
        verify(workOrderService, never()).create(any());
    }

    @ParameterizedTest
    @CsvSource({
            "520.0, 500",
            "999.0, 500",
            "1000.0, 1000"
    })
    void meterReadingCycleKeyUsesStableMeterBucket(double currentValue, String expectedBucket) {
        UUID equipmentId = UUID.randomUUID();
        UUID typeId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, typeId);
        MaintenanceRegulation regulation = regulation(UUID.randomUUID(), typeId, AutomationAction.TRACK_ONLY);
        regulation.setTriggerMeterType(MeterType.ENGINE_HOURS);
        regulation.setTriggerMeterInterval(500.0);
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        mockEffectiveRules(equipmentId, regulation);
        when(dueCalculationService.calculate(any(EquipmentMaintenanceEffectiveRule.class))).thenReturn(meterDue(equipmentId, regulation.getId(), currentValue));
        when(eventRepository.findByScopeAndCycleKey(
                eq(equipmentId), eq(regulation.getId()), eq(null), any())).thenReturn(Optional.empty());
        when(eventService.saveEvent(any(), eq(equipment))).thenAnswer(invocation -> assignDueEventId(invocation.getArgument(0, MaintenanceDueEvent.class)));

        service.evaluateEquipment(equipmentId, MaintenanceTriggerSource.METER_READING);

        ArgumentCaptor<MaintenanceDueEvent> eventCaptor = ArgumentCaptor.forClass(MaintenanceDueEvent.class);
        verify(eventService).saveEvent(eventCaptor.capture(), eq(equipment));
        MaintenanceDueEvent event = eventCaptor.getValue();
        assertThat(event.getCycleKey()).isEqualTo("%s:REG:%s:METER:ENGINE_HOURS:%s"
                .formatted(equipmentId, regulation.getId(), expectedBucket));
        assertThat(event.getCycleKey()).doesNotContain("CALENDAR");
        assertThat(event.getCycleKey()).doesNotMatch(".*\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.*");
        assertThat(event.getMeterType()).isEqualTo(MeterType.ENGINE_HOURS);
        assertThat(event.getMeterCurrentValue()).isEqualTo(currentValue);
        assertThat(event.getMeterInterval()).isEqualTo(500.0);
        assertThat(event.getMeterAnchorValue()).isZero();
        assertThat(event.getMeterRemaining()).isZero();
    }

    @Test
    void duplicateMeterReadingInSameIntervalUpdatesExistingDueEvent() {
        UUID equipmentId = UUID.randomUUID();
        UUID typeId = UUID.randomUUID();
        UUID regulationId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, typeId);
        MaintenanceRegulation regulation = regulation(regulationId, typeId, AutomationAction.TRACK_ONLY);
        regulation.setTriggerMeterType(MeterType.ENGINE_HOURS);
        regulation.setTriggerMeterInterval(500.0);
        MaintenanceDueEvent existing = new MaintenanceDueEvent();
        ReflectionTestUtils.setField(existing, "id", UUID.randomUUID());
        existing.setEquipmentId(equipmentId);
        existing.setRegulationId(regulationId);
        existing.setCycleKey("%s:REG:%s:METER:ENGINE_HOURS:500".formatted(equipmentId, regulationId));
        existing.setStatus(MaintenanceDueEventStatus.DETECTED);
        existing.setDueStatus(MaintenanceDueStatus.DUE);
        existing.setTriggerSource(MaintenanceTriggerSource.METER_READING);

        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        mockEffectiveRules(equipmentId, regulation);
        when(dueCalculationService.calculate(any(EquipmentMaintenanceEffectiveRule.class))).thenReturn(
                meterDue(equipmentId, regulationId, 520.0),
                meterDue(equipmentId, regulationId, 999.0)
        );
        when(eventRepository.findByScopeAndCycleKey(
                eq(equipmentId), eq(regulation.getId()), eq(null), any())).thenReturn(Optional.empty(), Optional.of(existing));
        when(eventService.saveEvent(any(), eq(equipment))).thenAnswer(invocation -> assignDueEventId(invocation.getArgument(0, MaintenanceDueEvent.class)));

        service.evaluateEquipment(equipmentId, MaintenanceTriggerSource.METER_READING);
        service.evaluateEquipment(equipmentId, MaintenanceTriggerSource.METER_READING);

        ArgumentCaptor<MaintenanceDueEvent> eventCaptor = ArgumentCaptor.forClass(MaintenanceDueEvent.class);
        verify(eventService, org.mockito.Mockito.times(2)).saveEvent(eventCaptor.capture(), eq(equipment));
        assertThat(eventCaptor.getAllValues()).hasSize(2);
        assertThat(eventCaptor.getAllValues().get(0).getId()).isNotEqualTo(existing.getId());
        assertThat(eventCaptor.getAllValues().get(1).getId()).isEqualTo(existing.getId());
        assertThat(eventCaptor.getAllValues().get(1).getCycleKey())
                .isEqualTo("%s:REG:%s:METER:ENGINE_HOURS:500".formatted(equipmentId, regulationId));
        verify(workOrderService, never()).create(any());
        verify(pprTaskRepository, never()).save(any());
    }

    @Test
    void approveBlockedEventNormalizesStatusWithoutCreatingActions() {
        UUID eventId = UUID.randomUUID();
        MaintenanceDueEvent event = new MaintenanceDueEvent();
        ReflectionTestUtils.setField(event, "id", eventId);
        event.setDueStatus(MaintenanceDueStatus.BLOCKED);
        event.setStatus(MaintenanceDueEventStatus.AWAITING_APPROVAL);
        when(eventService.getOrThrow(eventId)).thenReturn(event);
        when(eventRepository.save(event)).thenReturn(event);
        when(eventService.toDto(event)).thenReturn(null);

        service.approveDueEvent(eventId, UUID.randomUUID());

        assertThat(event.getStatus()).isEqualTo(MaintenanceDueEventStatus.DETECTED);
        verify(eventRepository).save(event);
        verify(regulationRepository, never()).findByIdAndIsDeletedFalse(any());
        verify(pprTaskRepository, never()).save(any());
        verify(workOrderService, never()).create(any());
    }

    @Test
    void createWorkOrderFromBlockedEventDoesNotCreateWorkOrder() {
        UUID eventId = UUID.randomUUID();
        MaintenanceDueEvent event = new MaintenanceDueEvent();
        ReflectionTestUtils.setField(event, "id", eventId);
        event.setDueStatus(MaintenanceDueStatus.BLOCKED);
        event.setStatus(MaintenanceDueEventStatus.DETECTED);
        when(eventService.getOrThrow(eventId)).thenReturn(event);

        assertThatThrownBy(() -> service.createWorkOrderFromEvent(eventId, UUID.randomUUID()))
                .isInstanceOfSatisfying(com.toir.exception.RestException.class, ex ->
                        assertThat(ex.getMessage()).contains("DUE or OVERDUE"));

        assertThat(event.getStatus()).isEqualTo(MaintenanceDueEventStatus.DETECTED);
        verify(regulationRepository, never()).findByIdAndIsDeletedFalse(any());
        verify(pprTaskRepository, never()).save(any());
        verify(workOrderService, never()).create(any());
    }

    @Test
    void createWorkOrderFromUpcomingEventIsRejected() {
        UUID eventId = UUID.randomUUID();
        MaintenanceDueEvent event = new MaintenanceDueEvent();
        ReflectionTestUtils.setField(event, "id", eventId);
        event.setDueStatus(MaintenanceDueStatus.UPCOMING);
        event.setStatus(MaintenanceDueEventStatus.DETECTED);
        when(eventService.getOrThrow(eventId)).thenReturn(event);

        assertThatThrownBy(() -> service.createWorkOrderFromEvent(eventId, UUID.randomUUID()))
                .isInstanceOfSatisfying(com.toir.exception.RestException.class, ex ->
                        assertThat(ex.getMessage()).contains("DUE or OVERDUE"));

        verify(workOrderService, never()).create(any());
    }

    @Test
    void createWorkOrderFromNotDueEventIsRejected() {
        UUID eventId = UUID.randomUUID();
        MaintenanceDueEvent event = new MaintenanceDueEvent();
        ReflectionTestUtils.setField(event, "id", eventId);
        event.setDueStatus(MaintenanceDueStatus.NOT_DUE);
        event.setStatus(MaintenanceDueEventStatus.DETECTED);
        when(eventService.getOrThrow(eventId)).thenReturn(event);

        assertThatThrownBy(() -> service.createWorkOrderFromEvent(eventId, UUID.randomUUID()))
                .isInstanceOfSatisfying(com.toir.exception.RestException.class, ex ->
                        assertThat(ex.getMessage()).contains("DUE or OVERDUE"));

        verify(workOrderService, never()).create(any());
    }

    @Test
    void approveDueEventDeniesCrossDepartmentScopedUser() {
        UUID eventId = UUID.randomUUID();
        MaintenanceDueEvent event = new MaintenanceDueEvent();
        ReflectionTestUtils.setField(event, "id", eventId);
        event.setDueStatus(MaintenanceDueStatus.DUE);
        event.setStatus(MaintenanceDueEventStatus.AWAITING_APPROVAL);
        when(eventService.getOrThrow(eventId)).thenReturn(event);
        doThrow(new AccessDeniedException("Access denied by data scope"))
                .when(eventService).assertCanAccessEvent(event);

        assertThatThrownBy(() -> service.approveDueEvent(eventId, UUID.randomUUID()))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("data scope");

        verify(effectiveRuleResolver, never()).resolveApplicable(any());
        verify(workOrderService, never()).create(any());
        verify(pprTaskRepository, never()).save(any());
    }

    @Test
    void createWorkOrderFromDueEventDeniesCrossDepartmentScopedUser() {
        UUID eventId = UUID.randomUUID();
        MaintenanceDueEvent event = new MaintenanceDueEvent();
        ReflectionTestUtils.setField(event, "id", eventId);
        event.setDueStatus(MaintenanceDueStatus.DUE);
        event.setStatus(MaintenanceDueEventStatus.DETECTED);
        when(eventService.getOrThrow(eventId)).thenReturn(event);
        doThrow(new AccessDeniedException("Access denied by data scope"))
                .when(eventService).assertCanAccessEvent(event);

        assertThatThrownBy(() -> service.createWorkOrderFromEvent(eventId, UUID.randomUUID()))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("data scope");

        verify(effectiveRuleResolver, never()).resolveApplicable(any());
        verify(workOrderService, never()).create(any());
        verify(pprTaskRepository, never()).save(any());
    }

    @Test
    void createWorkOrderFromDueEventCreatesWorkOrderForAccessibleEvent() {
        UUID eventId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        UUID typeId = UUID.randomUUID();
        UUID regulationId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID workOrderId = UUID.randomUUID();
        MaintenanceDueEvent event = new MaintenanceDueEvent();
        ReflectionTestUtils.setField(event, "id", eventId);
        event.setEquipmentId(equipmentId);
        event.setRegulationId(regulationId);
        event.setDueStatus(MaintenanceDueStatus.DUE);
        event.setStatus(MaintenanceDueEventStatus.DETECTED);
        event.setCycleKey("cycle-direct-work-order");
        event.setDueAt(Instant.parse("2026-06-03T00:00:00Z"));
        Equipment equipment = equipment(equipmentId, typeId);
        equipment.setResponsibleDepartmentId(departmentId);
        MaintenanceRegulation regulation = regulation(regulationId, typeId, AutomationAction.REQUIRE_APPROVAL);

        when(eventService.getOrThrow(eventId)).thenReturn(event);
        when(effectiveRuleResolver.resolveApplicable(equipmentId)).thenReturn(List.of(
                EquipmentMaintenanceEffectiveRule.fromRegulation(equipmentId, regulation)
        ));
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(workOrderRepository.existsOpenByCycleKey("cycle-direct-work-order")).thenReturn(false);
        when(workOrderNumberService.nextAutoNumber()).thenReturn("WO-AUTO-2026-0001");
        when(workOrderService.create(any())).thenReturn(workOrder(workOrderId));
        when(eventRepository.save(event)).thenReturn(event);
        when(eventService.toDto(event)).thenReturn(null);

        service.createWorkOrderFromEvent(eventId, UUID.randomUUID());

        verify(eventService).assertCanAccessEvent(event);
        verify(workOrderService).create(any());
        assertThat(event.getStatus()).isEqualTo(MaintenanceDueEventStatus.WORK_ORDER_CREATED);
        assertThat(event.getCreatedWorkOrderId()).isEqualTo(workOrderId);
    }

    @Test
    void createWorkOrderFromDueEventUsesPhysicalDepartmentWhenResponsibleDepartmentMatches() {
        UUID eventId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        UUID typeId = UUID.randomUUID();
        UUID regulationId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID workOrderId = UUID.randomUUID();
        MaintenanceDueEvent event = dueEvent(eventId, equipmentId, regulationId, "cycle-matching-department");
        Equipment equipment = equipment(equipmentId, typeId);
        equipment.setDepartmentId(departmentId);
        equipment.setResponsibleDepartmentId(departmentId);
        MaintenanceRegulation regulation = regulation(regulationId, typeId, AutomationAction.CREATE_WORK_ORDER);

        stubDirectWorkOrderCreation(event, equipment, regulation, workOrderId);

        service.createWorkOrderFromEvent(eventId, UUID.randomUUID());

        ArgumentCaptor<WorkOrderRequest> requestCaptor = ArgumentCaptor.forClass(WorkOrderRequest.class);
        verify(workOrderService).create(requestCaptor.capture());
        assertThat(requestCaptor.getValue().departmentId()).isEqualTo(departmentId);
    }

    @Test
    void createWorkOrderFromDueEventUsesPhysicalDepartmentWhenResponsibleDepartmentDiffers() {
        UUID eventId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        UUID typeId = UUID.randomUUID();
        UUID regulationId = UUID.randomUUID();
        UUID physicalDepartmentId = UUID.randomUUID();
        UUID responsibleDepartmentId = UUID.randomUUID();
        UUID workOrderId = UUID.randomUUID();
        MaintenanceDueEvent event = dueEvent(eventId, equipmentId, regulationId, "cycle-different-department");
        Equipment equipment = equipment(equipmentId, typeId);
        equipment.setDepartmentId(physicalDepartmentId);
        equipment.setResponsibleDepartmentId(responsibleDepartmentId);
        MaintenanceRegulation regulation = regulation(regulationId, typeId, AutomationAction.CREATE_WORK_ORDER);

        stubDirectWorkOrderCreation(event, equipment, regulation, workOrderId);

        service.createWorkOrderFromEvent(eventId, UUID.randomUUID());

        ArgumentCaptor<WorkOrderRequest> requestCaptor = ArgumentCaptor.forClass(WorkOrderRequest.class);
        verify(workOrderService).create(requestCaptor.capture());
        assertThat(requestCaptor.getValue().departmentId()).isEqualTo(physicalDepartmentId);
    }

    @Test
    void createWorkOrderFromDueEventDoesNotPassInvalidGeneratedTaskAsPprTask() {
        UUID eventId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        UUID typeId = UUID.randomUUID();
        UUID regulationId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID workOrderId = UUID.randomUUID();
        MaintenanceDueEvent event = dueEvent(eventId, equipmentId, regulationId, "cycle-invalid-task-link");
        event.setCreatedTaskId(taskId);
        Equipment equipment = equipment(equipmentId, typeId);
        equipment.setDepartmentId(departmentId);
        MaintenanceRegulation regulation = regulation(regulationId, typeId, AutomationAction.CREATE_WORK_ORDER);
        PprPlan generatedPlan = pprPlan(departmentId);
        PprTask plannedTask = new PprTask();
        ReflectionTestUtils.setField(plannedTask, "id", taskId);
        plannedTask.setPlan(generatedPlan);
        plannedTask.setStatus(PprTaskStatus.PLANNED);

        stubDirectWorkOrderCreation(event, equipment, regulation, workOrderId);
        when(pprTaskRepository.findByIdAndIsDeletedFalseWithPlan(taskId)).thenReturn(Optional.of(plannedTask));

        service.createWorkOrderFromEvent(eventId, UUID.randomUUID());

        ArgumentCaptor<WorkOrderRequest> requestCaptor = ArgumentCaptor.forClass(WorkOrderRequest.class);
        verify(workOrderService).create(requestCaptor.capture());
        assertThat(requestCaptor.getValue().pprTaskId()).isNull();
        assertThat(requestCaptor.getValue().maintenanceDueEventId()).isEqualTo(eventId);
    }

    @Test
    void createWorkOrderFromDueEventCopiesTemplateOperationsToWorkOrderTasks() {
        UUID eventId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        UUID typeId = UUID.randomUUID();
        UUID regulationId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID workOrderId = UUID.randomUUID();
        MaintenanceDueEvent event = new MaintenanceDueEvent();
        ReflectionTestUtils.setField(event, "id", eventId);
        event.setEquipmentId(equipmentId);
        event.setRegulationId(regulationId);
        event.setTemplateId(templateId);
        event.setDueStatus(MaintenanceDueStatus.DUE);
        event.setStatus(MaintenanceDueEventStatus.DETECTED);
        event.setCycleKey("cycle-template-work-order");
        event.setDueAt(Instant.parse("2026-06-03T00:00:00Z"));
        Equipment equipment = equipment(equipmentId, typeId);
        equipment.setResponsibleDepartmentId(departmentId);
        MaintenanceRegulation regulation = regulation(regulationId, typeId, AutomationAction.CREATE_WORK_ORDER);
        regulation.setTemplateId(templateId);
        WorkOrder createdWorkOrder = new WorkOrder();
        ReflectionTestUtils.setField(createdWorkOrder, "id", workOrderId);
        MaintenanceOperation inspect = operation(templateId, "Inspect coupling", 1.25);
        MaintenanceOperation lubricate = operation(templateId, "Lubricate bearings", 0.75);

        when(eventService.getOrThrow(eventId)).thenReturn(event);
        when(effectiveRuleResolver.resolveApplicable(equipmentId)).thenReturn(List.of(
                EquipmentMaintenanceEffectiveRule.fromRegulation(equipmentId, regulation)
        ));
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(workOrderRepository.existsOpenByCycleKey("cycle-template-work-order")).thenReturn(false);
        when(workOrderNumberService.nextAutoNumber()).thenReturn("WO-AUTO-2026-0001");
        when(workOrderService.create(any())).thenReturn(workOrder(workOrderId));
        when(workOrderRepository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(createdWorkOrder));
        when(maintenanceOperationRepository.findAllByTemplateIdInAndIsDeletedFalse(List.of(templateId)))
                .thenReturn(List.of(inspect, lubricate));
        when(workOrderRepository.save(createdWorkOrder)).thenReturn(createdWorkOrder);
        when(eventRepository.save(event)).thenReturn(event);
        when(eventService.toDto(event)).thenReturn(null);

        service.createWorkOrderFromEvent(eventId, UUID.randomUUID());

        assertThat(createdWorkOrder.getTasks()).hasSize(2);
        WorkOrderTask first = createdWorkOrder.getTasks().get(0);
        assertThat(first.getTitle()).isEqualTo("Inspect coupling");
        assertThat(first.getPlannedHours()).isEqualTo(1.25);
        assertThat(first.getSourceTemplateId()).isEqualTo(templateId);
        assertThat(first.getSourceOperationId()).isEqualTo(inspect.getId());
        WorkOrderTask second = createdWorkOrder.getTasks().get(1);
        assertThat(second.getTitle()).isEqualTo("Lubricate bearings");
        assertThat(second.getPlannedHours()).isEqualTo(0.75);
        assertThat(second.getSourceTemplateId()).isEqualTo(templateId);
        assertThat(second.getSourceOperationId()).isEqualTo(lubricate.getId());

        first.setTitle("Edited in WO");
        assertThat(inspect.getName()).isEqualTo("Inspect coupling");
        verify(workOrderRepository).save(createdWorkOrder);
    }

    @Test
    void createWorkOrderFromDueEventExplainsWhenTemplateHasNoOperations() {
        UUID eventId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        UUID typeId = UUID.randomUUID();
        UUID regulationId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID workOrderId = UUID.randomUUID();
        MaintenanceDueEvent event = new MaintenanceDueEvent();
        ReflectionTestUtils.setField(event, "id", eventId);
        event.setEquipmentId(equipmentId);
        event.setRegulationId(regulationId);
        event.setTemplateId(templateId);
        event.setDueStatus(MaintenanceDueStatus.DUE);
        event.setStatus(MaintenanceDueEventStatus.DETECTED);
        event.setCycleKey("cycle-empty-template-work-order");
        event.setDueAt(Instant.parse("2026-06-03T00:00:00Z"));
        Equipment equipment = equipment(equipmentId, typeId);
        equipment.setResponsibleDepartmentId(departmentId);
        MaintenanceRegulation regulation = regulation(regulationId, typeId, AutomationAction.CREATE_WORK_ORDER);
        regulation.setTemplateId(templateId);
        WorkOrder createdWorkOrder = new WorkOrder();
        ReflectionTestUtils.setField(createdWorkOrder, "id", workOrderId);

        when(eventService.getOrThrow(eventId)).thenReturn(event);
        when(effectiveRuleResolver.resolveApplicable(equipmentId)).thenReturn(List.of(
                EquipmentMaintenanceEffectiveRule.fromRegulation(equipmentId, regulation)
        ));
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(workOrderRepository.existsOpenByCycleKey("cycle-empty-template-work-order")).thenReturn(false);
        when(workOrderNumberService.nextAutoNumber()).thenReturn("WO-AUTO-2026-0001");
        when(workOrderService.create(any())).thenReturn(workOrder(workOrderId));
        when(workOrderRepository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(createdWorkOrder));
        when(maintenanceOperationRepository.findAllByTemplateIdInAndIsDeletedFalse(List.of(templateId)))
                .thenReturn(List.of());
        when(eventRepository.save(event)).thenReturn(event);
        when(eventService.toDto(event)).thenReturn(null);

        service.createWorkOrderFromEvent(eventId, UUID.randomUUID());

        assertThat(createdWorkOrder.getTasks()).isEmpty();
        assertThat(event.getExplanation()).contains("template has no operations/checklist items");
        verify(workOrderRepository, never()).save(createdWorkOrder);
    }

    @Test
    void approveDueEventEnforcesRegulationApprovalPermission() {
        UUID eventId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        UUID typeId = UUID.randomUUID();
        UUID regulationId = UUID.randomUUID();
        MaintenanceDueEvent event = new MaintenanceDueEvent();
        ReflectionTestUtils.setField(event, "id", eventId);
        event.setEquipmentId(equipmentId);
        event.setRegulationId(regulationId);
        event.setDueStatus(MaintenanceDueStatus.DUE);
        event.setStatus(MaintenanceDueEventStatus.AWAITING_APPROVAL);
        event.setCycleKey("cycle");
        MaintenanceRegulation regulation = regulation(regulationId, typeId, AutomationAction.CREATE_WORK_ORDER);
        regulation.setApprovalPermission("MAINTENANCE_MANAGER");
        EquipmentMaintenanceEffectiveRule rule = EquipmentMaintenanceEffectiveRule.fromRegulation(equipmentId, regulation);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                "approver",
                "n/a",
                List.of(new SimpleGrantedAuthority("MAINTENANCE_EVENT_APPROVE"))
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        when(eventService.getOrThrow(eventId)).thenReturn(event);
        when(effectiveRuleResolver.resolveApplicable(equipmentId)).thenReturn(List.of(rule));
        when(securityAccessService.hasPermission(authentication, "MAINTENANCE_MANAGER")).thenReturn(false);

        assertThatThrownBy(() -> service.approveDueEvent(eventId, UUID.randomUUID()))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("MAINTENANCE_MANAGER");

        verify(workOrderService, never()).create(any());
        verify(pprTaskRepository, never()).save(any());
        verify(eventRepository, never()).save(any());
    }

    @Test
    void approveRequireApprovalPolicyCreatesWorkOrderWhenConfigured() {
        UUID eventId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        UUID typeId = UUID.randomUUID();
        UUID regulationId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID workOrderId = UUID.randomUUID();
        MaintenanceDueEvent event = new MaintenanceDueEvent();
        ReflectionTestUtils.setField(event, "id", eventId);
        event.setEquipmentId(equipmentId);
        event.setRegulationId(regulationId);
        event.setDueStatus(MaintenanceDueStatus.DUE);
        event.setStatus(MaintenanceDueEventStatus.AWAITING_APPROVAL);
        event.setCycleKey("cycle-work-order");
        event.setDueAt(Instant.parse("2026-06-03T00:00:00Z"));
        Equipment equipment = equipment(equipmentId, typeId);
        equipment.setResponsibleDepartmentId(departmentId);
        MaintenanceRegulation regulation = regulation(regulationId, typeId, AutomationAction.REQUIRE_APPROVAL);
        regulation.setApprovalResultAction(ApprovalResultAction.CREATE_WORK_ORDER);

        when(eventService.getOrThrow(eventId)).thenReturn(event);
        when(effectiveRuleResolver.resolveApplicable(equipmentId)).thenReturn(List.of(
                EquipmentMaintenanceEffectiveRule.fromRegulation(equipmentId, regulation)
        ));
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(workOrderRepository.existsOpenByCycleKey("cycle-work-order")).thenReturn(false);
        when(workOrderNumberService.nextAutoNumber()).thenReturn("WO-AUTO-2026-0001");
        when(workOrderService.create(any())).thenReturn(workOrder(workOrderId));
        when(eventRepository.save(event)).thenReturn(event);
        when(eventService.toDto(event)).thenReturn(null);

        service.approveDueEvent(eventId, UUID.randomUUID());

        verify(eventService).assertCanAccessEvent(event);
        verify(workOrderService).create(any());
        verify(pprTaskRepository, never()).save(any());
        assertThat(event.getStatus()).isEqualTo(MaintenanceDueEventStatus.WORK_ORDER_CREATED);
        assertThat(event.getCreatedWorkOrderId()).isEqualTo(workOrderId);
    }

    @Test
    void approveRequireApprovalPolicyCreatesTaskWhenConfigured() {
        UUID eventId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        UUID typeId = UUID.randomUUID();
        UUID regulationId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        MaintenanceDueEvent event = new MaintenanceDueEvent();
        ReflectionTestUtils.setField(event, "id", eventId);
        event.setEquipmentId(equipmentId);
        event.setRegulationId(regulationId);
        event.setDueStatus(MaintenanceDueStatus.DUE);
        event.setStatus(MaintenanceDueEventStatus.AWAITING_APPROVAL);
        event.setCycleKey("cycle-task-approved");
        event.setDueAt(Instant.parse("2026-06-03T00:00:00Z"));
        Equipment equipment = equipment(equipmentId, typeId);
        equipment.setResponsibleDepartmentId(departmentId);
        MaintenanceRegulation regulation = regulation(regulationId, typeId, AutomationAction.REQUIRE_APPROVAL);
        regulation.setTemplateId(UUID.randomUUID());
        regulation.setApprovalResultAction(ApprovalResultAction.CREATE_TASK);

        when(eventService.getOrThrow(eventId)).thenReturn(event);
        when(effectiveRuleResolver.resolveApplicable(equipmentId)).thenReturn(List.of(
                EquipmentMaintenanceEffectiveRule.fromRegulation(equipmentId, regulation)
        ));
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(pprTaskRepository.existsOpenByCycleKey("cycle-task-approved")).thenReturn(false);
        when(pprTaskRepository.maxSequenceByCodePrefix(any())).thenReturn(0L);
        when(pprPlanRepository.findByCodeAndIsDeletedFalse(any())).thenReturn(Optional.of(pprPlan(departmentId)));
        when(pprTaskRepository.save(any())).thenAnswer(invocation -> assignPprTaskId(invocation.getArgument(0, PprTask.class)));
        when(eventRepository.save(event)).thenReturn(event);
        when(eventService.toDto(event)).thenReturn(null);

        service.approveDueEvent(eventId, UUID.randomUUID());

        verify(pprTaskRepository).save(any());
        verify(workOrderService, never()).create(any());
        assertThat(event.getStatus()).isEqualTo(MaintenanceDueEventStatus.TASK_CREATED);
        assertThat(event.getCreatedTaskId()).isNotNull();
    }

    @Test
    void approveRequireApprovalPolicyDefaultsNullApprovalResultActionToCreateTask() {
        UUID eventId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        UUID typeId = UUID.randomUUID();
        UUID regulationId = UUID.randomUUID();
        MaintenanceDueEvent event = new MaintenanceDueEvent();
        ReflectionTestUtils.setField(event, "id", eventId);
        event.setEquipmentId(equipmentId);
        event.setRegulationId(regulationId);
        event.setDueStatus(MaintenanceDueStatus.DUE);
        event.setStatus(MaintenanceDueEventStatus.AWAITING_APPROVAL);
        event.setCycleKey("cycle-task-default");
        event.setDueAt(Instant.parse("2026-06-03T00:00:00Z"));
        Equipment equipment = equipment(equipmentId, typeId);
        MaintenanceRegulation regulation = regulation(regulationId, typeId, AutomationAction.REQUIRE_APPROVAL);
        regulation.setTemplateId(UUID.randomUUID());
        regulation.setApprovalResultAction(null);

        when(eventService.getOrThrow(eventId)).thenReturn(event);
        when(effectiveRuleResolver.resolveApplicable(equipmentId)).thenReturn(List.of(
                EquipmentMaintenanceEffectiveRule.fromRegulation(equipmentId, regulation)
        ));
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(pprTaskRepository.existsOpenByCycleKey("cycle-task-default")).thenReturn(false);
        when(pprTaskRepository.maxSequenceByCodePrefix(any())).thenReturn(0L);
        when(pprPlanRepository.findByCodeAndIsDeletedFalse(any())).thenReturn(Optional.of(pprPlan(null)));
        when(pprTaskRepository.save(any())).thenAnswer(invocation -> assignPprTaskId(invocation.getArgument(0, PprTask.class)));
        when(eventRepository.save(event)).thenReturn(event);
        when(eventService.toDto(event)).thenReturn(null);

        service.approveDueEvent(eventId, UUID.randomUUID());

        verify(pprTaskRepository).save(any());
        verify(workOrderService, never()).create(any());
        assertThat(event.getStatus()).isEqualTo(MaintenanceDueEventStatus.TASK_CREATED);
        assertThat(event.getCreatedTaskId()).isNotNull();
    }

    @Test
    void evaluateAllCalendarRulesContinuesAfterEquipmentFailure() {
        UUID failedEquipmentId = UUID.randomUUID();
        UUID okEquipmentId = UUID.randomUUID();
        Equipment failedEquipment = equipment(failedEquipmentId, UUID.randomUUID());
        Equipment okEquipment = equipment(okEquipmentId, UUID.randomUUID());
        when(equipmentRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of(failedEquipment, okEquipment));
        when(equipmentRepository.findByIdAndIsDeletedFalse(failedEquipmentId)).thenThrow(new IllegalStateException("broken equipment"));
        when(equipmentRepository.findByIdAndIsDeletedFalse(okEquipmentId)).thenReturn(Optional.of(okEquipment));
        when(effectiveRuleResolver.resolveApplicable(okEquipmentId)).thenReturn(List.of());

        var result = service.evaluateAllCalendarRules();

        assertThat(result.checkedEquipment()).isEqualTo(1);
        assertThat(result.failures()).isEqualTo(1);
        verify(effectiveRuleResolver).resolveApplicable(okEquipmentId);
    }

    @Test
    void createWorkOrderPolicyCreatesWorkOrderAndStoresCreatedWorkOrderId() {
        UUID equipmentId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID typeId = UUID.randomUUID();
        UUID workOrderId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, typeId);
        equipment.setResponsibleDepartmentId(departmentId);
        MaintenanceRegulation regulation = regulation(UUID.randomUUID(), typeId, AutomationAction.CREATE_WORK_ORDER);
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        mockEffectiveRules(equipmentId, regulation);
        when(dueCalculationService.calculate(any(EquipmentMaintenanceEffectiveRule.class))).thenReturn(due(equipmentId, regulation.getId()));
        when(eventRepository.findByScopeAndCycleKey(
                eq(equipmentId), eq(regulation.getId()), eq(null), any())).thenReturn(Optional.empty());
        when(eventService.saveEvent(any(), eq(equipment))).thenAnswer(invocation -> assignDueEventId(invocation.getArgument(0, MaintenanceDueEvent.class)));
        when(workOrderNumberService.nextAutoNumber()).thenReturn("WO-AUTO-2026-0001");
        when(workOrderRepository.existsOpenByCycleKey(any())).thenReturn(false);
        when(workOrderService.create(any())).thenReturn(workOrder(workOrderId));
        when(eventRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0, MaintenanceDueEvent.class));

        var result = service.evaluateEquipment(equipmentId, MaintenanceTriggerSource.CALENDAR_JOB);

        assertThat(result.workOrdersCreated()).isEqualTo(1);
        ArgumentCaptor<WorkOrderRequest> requestCaptor = ArgumentCaptor.forClass(WorkOrderRequest.class);
        verify(workOrderService).create(requestCaptor.capture());
        assertThat(requestCaptor.getValue().equipmentId()).isEqualTo(equipmentId);
        assertThat(requestCaptor.getValue().departmentId()).isEqualTo(departmentId);
        assertThat(requestCaptor.getValue().maintenanceDueEventId()).isNotNull();
        assertThat(requestCaptor.getValue().cycleKey()).contains(equipmentId.toString(), regulation.getId().toString());
        ArgumentCaptor<MaintenanceDueEvent> savedEventCaptor = ArgumentCaptor.forClass(MaintenanceDueEvent.class);
        verify(eventRepository).save(savedEventCaptor.capture());
        assertThat(savedEventCaptor.getValue().getStatus()).isEqualTo(MaintenanceDueEventStatus.WORK_ORDER_CREATED);
        assertThat(savedEventCaptor.getValue().getCreatedWorkOrderId()).isEqualTo(workOrderId);
    }

    @Test
    void individualEquipmentRuleCreateWorkOrderPolicyCreatesWorkOrder() {
        UUID equipmentId = UUID.randomUUID();
        UUID ruleId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID workOrderId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, UUID.randomUUID());
        equipment.setResponsibleDepartmentId(departmentId);
        EquipmentMaintenanceRule rule = individualRule(equipmentId, ruleId, AutomationAction.CREATE_WORK_ORDER);
        EquipmentMaintenanceEffectiveRule effectiveRule = EquipmentMaintenanceEffectiveRule.fromIndividual(rule);

        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(effectiveRuleResolver.resolveApplicable(equipmentId)).thenReturn(List.of(effectiveRule));
        when(dueCalculationService.calculate(any(EquipmentMaintenanceEffectiveRule.class)))
                .thenReturn(due(equipmentId, null, ruleId));
        when(eventRepository.findByScopeAndCycleKey(eq(equipmentId), eq(null), eq(ruleId), any()))
                .thenReturn(Optional.empty());
        when(eventService.saveEvent(any(), eq(equipment)))
                .thenAnswer(invocation -> assignDueEventId(invocation.getArgument(0, MaintenanceDueEvent.class)));
        when(workOrderNumberService.nextAutoNumber()).thenReturn("WO-AUTO-2026-0001");
        when(workOrderRepository.existsOpenByCycleKey(any())).thenReturn(false);
        when(workOrderService.create(any())).thenReturn(workOrder(workOrderId));
        when(eventRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0, MaintenanceDueEvent.class));

        var result = service.evaluateEquipment(equipmentId, MaintenanceTriggerSource.CALENDAR_JOB);

        assertThat(result.workOrdersCreated()).isEqualTo(1);
        ArgumentCaptor<WorkOrderRequest> requestCaptor = ArgumentCaptor.forClass(WorkOrderRequest.class);
        verify(workOrderService).create(requestCaptor.capture());
        assertThat(requestCaptor.getValue().equipmentId()).isEqualTo(equipmentId);
        assertThat(requestCaptor.getValue().maintenanceDueEventId()).isNotNull();
        assertThat(requestCaptor.getValue().cycleKey()).contains(equipmentId.toString(), ruleId.toString());
        ArgumentCaptor<MaintenanceDueEvent> savedEventCaptor = ArgumentCaptor.forClass(MaintenanceDueEvent.class);
        verify(eventRepository).save(savedEventCaptor.capture());
        assertThat(savedEventCaptor.getValue().getRegulationId()).isNull();
        assertThat(savedEventCaptor.getValue().getEquipmentMaintenanceRuleId()).isEqualTo(ruleId);
        assertThat(savedEventCaptor.getValue().getStatus()).isEqualTo(MaintenanceDueEventStatus.WORK_ORDER_CREATED);
        assertThat(savedEventCaptor.getValue().getCreatedWorkOrderId()).isEqualTo(workOrderId);
    }

    @Test
    void createTaskActionDueStatusCreatesPprTask() {
        UUID equipmentId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID typeId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, typeId);
        equipment.setResponsibleDepartmentId(departmentId);
        MaintenanceRegulation regulation = regulation(UUID.randomUUID(), typeId, AutomationAction.CREATE_TASK);
        regulation.setTemplateId(UUID.randomUUID());
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        mockEffectiveRules(equipmentId, regulation);
        when(dueCalculationService.calculate(any(EquipmentMaintenanceEffectiveRule.class)))
                .thenReturn(due(equipmentId, regulation.getId()));
        when(eventRepository.findByScopeAndCycleKey(
                eq(equipmentId), eq(regulation.getId()), eq(null), any())).thenReturn(Optional.empty());
        when(eventService.saveEvent(any(), eq(equipment))).thenAnswer(invocation -> assignDueEventId(invocation.getArgument(0, MaintenanceDueEvent.class)));
        when(pprTaskRepository.existsOpenByCycleKey(any())).thenReturn(false);
        when(pprTaskRepository.maxSequenceByCodePrefix(any())).thenReturn(0L);
        when(pprPlanRepository.findByCodeAndIsDeletedFalse(any())).thenReturn(Optional.of(pprPlan(departmentId)));
        when(pprTaskRepository.save(any())).thenAnswer(invocation -> assignPprTaskId(invocation.getArgument(0, PprTask.class)));
        when(eventRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0, MaintenanceDueEvent.class));

        var result = service.evaluateEquipment(equipmentId, MaintenanceTriggerSource.CALENDAR_JOB);

        assertThat(result.tasksCreated()).isEqualTo(1);
        ArgumentCaptor<PprTask> taskCaptor = ArgumentCaptor.forClass(PprTask.class);
        verify(pprTaskRepository).save(taskCaptor.capture());
        assertThat(taskCaptor.getValue().getEquipmentId()).isEqualTo(equipmentId);
        assertThat(taskCaptor.getValue().getRegulationId()).isEqualTo(regulation.getId());
        assertThat(taskCaptor.getValue().getCycleKey()).contains(equipmentId.toString(), regulation.getId().toString());
        assertThat(taskCaptor.getValue().getMaintenanceDueEventId()).isNotNull();
        ArgumentCaptor<MaintenanceDueEvent> eventCaptor = ArgumentCaptor.forClass(MaintenanceDueEvent.class);
        verify(eventRepository).save(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getDueStatus()).isEqualTo(MaintenanceDueStatus.DUE);
        assertThat(eventCaptor.getValue().getStatus()).isEqualTo(MaintenanceDueEventStatus.TASK_CREATED);
        assertThat(eventCaptor.getValue().getCreatedTaskId()).isEqualTo(taskCaptor.getValue().getId());
    }

    @Test
    void createTaskActionOverdueStatusCreatesPprTask() {
        UUID equipmentId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID typeId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, typeId);
        equipment.setResponsibleDepartmentId(departmentId);
        MaintenanceRegulation regulation = regulation(UUID.randomUUID(), typeId, AutomationAction.CREATE_TASK);
        regulation.setTemplateId(UUID.randomUUID());
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        mockEffectiveRules(equipmentId, regulation);
        when(dueCalculationService.calculate(any(EquipmentMaintenanceEffectiveRule.class)))
                .thenReturn(overdueDue(equipmentId, regulation.getId()));
        when(eventRepository.findByScopeAndCycleKey(
                eq(equipmentId), eq(regulation.getId()), eq(null), any())).thenReturn(Optional.empty());
        when(eventService.saveEvent(any(), eq(equipment))).thenAnswer(invocation -> assignDueEventId(invocation.getArgument(0, MaintenanceDueEvent.class)));
        when(pprTaskRepository.existsOpenByCycleKey(any())).thenReturn(false);
        when(pprTaskRepository.maxSequenceByCodePrefix(any())).thenReturn(0L);
        when(pprPlanRepository.findByCodeAndIsDeletedFalse(any())).thenReturn(Optional.of(pprPlan(departmentId)));
        when(pprTaskRepository.save(any())).thenAnswer(invocation -> assignPprTaskId(invocation.getArgument(0, PprTask.class)));
        when(eventRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0, MaintenanceDueEvent.class));

        var result = service.evaluateEquipment(equipmentId, MaintenanceTriggerSource.CALENDAR_JOB);

        assertThat(result.tasksCreated()).isEqualTo(1);
        ArgumentCaptor<MaintenanceDueEvent> eventCaptor = ArgumentCaptor.forClass(MaintenanceDueEvent.class);
        verify(eventRepository).save(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getDueStatus()).isEqualTo(MaintenanceDueStatus.OVERDUE);
        assertThat(eventCaptor.getValue().getStatus()).isEqualTo(MaintenanceDueEventStatus.TASK_CREATED);
        assertThat(eventCaptor.getValue().getCreatedTaskId()).isNotNull();
        verify(pprTaskRepository).save(any());
    }

    @Test
    void createTaskActionWithoutTemplateDoesNotCreateTaskAndExplainsReason() {
        UUID equipmentId = UUID.randomUUID();
        UUID typeId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, typeId);
        MaintenanceRegulation regulation = regulation(UUID.randomUUID(), typeId, AutomationAction.CREATE_TASK);
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        mockEffectiveRules(equipmentId, regulation);
        when(dueCalculationService.calculate(any(EquipmentMaintenanceEffectiveRule.class)))
                .thenReturn(due(equipmentId, regulation.getId()));
        when(eventRepository.findByScopeAndCycleKey(
                eq(equipmentId), eq(regulation.getId()), eq(null), any())).thenReturn(Optional.empty());
        when(eventService.saveEvent(any(), eq(equipment))).thenAnswer(invocation -> assignDueEventId(invocation.getArgument(0, MaintenanceDueEvent.class)));
        when(eventRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0, MaintenanceDueEvent.class));

        var result = service.evaluateEquipment(equipmentId, MaintenanceTriggerSource.CALENDAR_JOB);

        assertThat(result.tasksCreated()).isZero();
        ArgumentCaptor<MaintenanceDueEvent> eventCaptor = ArgumentCaptor.forClass(MaintenanceDueEvent.class);
        verify(eventRepository).save(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getStatus()).isEqualTo(MaintenanceDueEventStatus.DETECTED);
        assertThat(eventCaptor.getValue().getExplanation()).contains("templateId is required to create task");
        verify(pprTaskRepository, never()).save(any());
    }

    @Test
    void createTaskActionDuplicateCycleDoesNotCreateSecondTask() {
        UUID equipmentId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID typeId = UUID.randomUUID();
        UUID regulationId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, typeId);
        equipment.setResponsibleDepartmentId(departmentId);
        MaintenanceRegulation regulation = regulation(regulationId, typeId, AutomationAction.CREATE_TASK);
        regulation.setTemplateId(UUID.randomUUID());
        MaintenanceDueEvent existing = new MaintenanceDueEvent();
        ReflectionTestUtils.setField(existing, "id", UUID.randomUUID());
        existing.setEquipmentId(equipmentId);
        existing.setRegulationId(regulationId);
        existing.setCycleKey("%s:%s:CALENDAR:2026-06-01".formatted(equipmentId, regulationId));
        existing.setStatus(MaintenanceDueEventStatus.TASK_CREATED);
        existing.setDueStatus(MaintenanceDueStatus.DUE);
        existing.setTriggerSource(MaintenanceTriggerSource.CALENDAR_JOB);
        existing.setCreatedTaskId(taskId);

        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        mockEffectiveRules(equipmentId, regulation);
        when(dueCalculationService.calculate(any(EquipmentMaintenanceEffectiveRule.class))).thenReturn(
                calendarDue(equipmentId, regulationId),
                calendarDue(equipmentId, regulationId)
        );
        when(eventRepository.findByScopeAndCycleKey(
                eq(equipmentId), eq(regulationId), eq(null), any())).thenReturn(Optional.empty(), Optional.of(existing));
        when(eventService.saveEvent(any(), eq(equipment))).thenAnswer(invocation -> assignDueEventId(invocation.getArgument(0, MaintenanceDueEvent.class)));
        when(pprTaskRepository.existsOpenByCycleKey(any())).thenReturn(false);
        when(pprTaskRepository.maxSequenceByCodePrefix(any())).thenReturn(0L);
        when(pprPlanRepository.findByCodeAndIsDeletedFalse(any())).thenReturn(Optional.of(pprPlan(departmentId)));
        when(pprTaskRepository.save(any())).thenAnswer(invocation -> assignPprTaskId(invocation.getArgument(0, PprTask.class)));
        when(pprTaskRepository.findByIdAndIsDeletedFalse(taskId)).thenReturn(Optional.of(new PprTask()));
        when(eventRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0, MaintenanceDueEvent.class));

        service.evaluateEquipment(equipmentId, MaintenanceTriggerSource.CALENDAR_JOB);
        var secondResult = service.evaluateEquipment(equipmentId, MaintenanceTriggerSource.CALENDAR_JOB);

        verify(pprTaskRepository).save(any());
        assertThat(secondResult.tasksCreated()).isZero();
    }

    @Test
    void meterReadingStillSucceedsWhenAutomationFails() {
        UUID equipmentId = UUID.randomUUID();
        UUID typeId = UUID.randomUUID();
        MaintenanceRegulation regulation = regulation(UUID.randomUUID(), typeId, AutomationAction.CREATE_TASK);
        regulation.setTemplateId(UUID.randomUUID());
        regulation.setTriggerMeterType(MeterType.ENGINE_HOURS);
        regulation.setTriggerMeterInterval(500.0);
        Equipment equipment = equipment(equipmentId, typeId);
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        mockEffectiveRules(equipmentId, regulation);
        when(dueCalculationService.calculate(any(EquipmentMaintenanceEffectiveRule.class)))
                .thenReturn(meterDue(equipmentId, regulation.getId(), 500.0));
        when(eventRepository.findByScopeAndCycleKey(
                eq(equipmentId), eq(regulation.getId()), eq(null), any())).thenReturn(Optional.empty());
        when(eventService.saveEvent(any(), eq(equipment))).thenAnswer(invocation -> assignDueEventId(invocation.getArgument(0, MaintenanceDueEvent.class)));
        when(pprTaskRepository.existsOpenByCycleKey(any())).thenReturn(false);
        when(pprTaskRepository.maxSequenceByCodePrefix(any())).thenReturn(0L);
        when(pprPlanRepository.findByCodeAndIsDeletedFalse(any())).thenReturn(Optional.of(pprPlan(null)));
        when(pprTaskRepository.save(any())).thenThrow(new IllegalStateException("task automation failed"));

        var result = service.evaluateEquipment(equipmentId, MaintenanceTriggerSource.METER_READING);

        assertThat(result.checkedEquipment()).isEqualTo(1);
        assertThat(result.failures()).isEqualTo(1);
        verify(eventService).saveEvent(any(), eq(equipment));
    }

    @Test
    void overdueCreateWorkOrderPolicyCreatesWorkOrder() {
        UUID equipmentId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID typeId = UUID.randomUUID();
        UUID workOrderId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, typeId);
        equipment.setResponsibleDepartmentId(departmentId);
        MaintenanceRegulation regulation = regulation(UUID.randomUUID(), typeId, AutomationAction.CREATE_WORK_ORDER);
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        mockEffectiveRules(equipmentId, regulation);
        when(dueCalculationService.calculate(any(EquipmentMaintenanceEffectiveRule.class)))
                .thenReturn(overdueDue(equipmentId, regulation.getId()));
        when(eventRepository.findByScopeAndCycleKey(
                eq(equipmentId), eq(regulation.getId()), eq(null), any())).thenReturn(Optional.empty());
        when(eventService.saveEvent(any(), eq(equipment))).thenAnswer(invocation -> assignDueEventId(invocation.getArgument(0, MaintenanceDueEvent.class)));
        when(workOrderNumberService.nextAutoNumber()).thenReturn("WO-AUTO-2026-0001");
        when(workOrderRepository.existsOpenByCycleKey(any())).thenReturn(false);
        when(workOrderService.create(any())).thenReturn(workOrder(workOrderId));
        when(eventRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0, MaintenanceDueEvent.class));

        var result = service.evaluateEquipment(equipmentId, MaintenanceTriggerSource.CALENDAR_JOB);

        assertThat(result.workOrdersCreated()).isEqualTo(1);
        ArgumentCaptor<MaintenanceDueEvent> eventCaptor = ArgumentCaptor.forClass(MaintenanceDueEvent.class);
        verify(eventRepository).save(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getDueStatus()).isEqualTo(MaintenanceDueStatus.OVERDUE);
        assertThat(eventCaptor.getValue().getStatus()).isEqualTo(MaintenanceDueEventStatus.WORK_ORDER_CREATED);
        assertThat(eventCaptor.getValue().getCreatedWorkOrderId()).isEqualTo(workOrderId);
    }

    @Test
    void upcomingCreateWorkOrderPolicyCreatesEventOnly() {
        UUID equipmentId = UUID.randomUUID();
        UUID typeId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, typeId);
        MaintenanceRegulation regulation = regulation(UUID.randomUUID(), typeId, AutomationAction.CREATE_WORK_ORDER);
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        mockEffectiveRules(equipmentId, regulation);
        when(dueCalculationService.calculate(any(EquipmentMaintenanceEffectiveRule.class)))
                .thenReturn(upcomingDue(equipmentId, regulation.getId()));
        when(eventRepository.findByScopeAndCycleKey(
                eq(equipmentId), eq(regulation.getId()), eq(null), any())).thenReturn(Optional.empty());
        when(eventService.saveEvent(any(), eq(equipment))).thenAnswer(invocation -> assignDueEventId(invocation.getArgument(0, MaintenanceDueEvent.class)));

        var result = service.evaluateEquipment(equipmentId, MaintenanceTriggerSource.CALENDAR_JOB);

        assertThat(result.events()).isEqualTo(1);
        assertThat(result.workOrdersCreated()).isZero();
        assertThat(result.tasksCreated()).isZero();
        ArgumentCaptor<MaintenanceDueEvent> eventCaptor = ArgumentCaptor.forClass(MaintenanceDueEvent.class);
        verify(eventService).saveEvent(eventCaptor.capture(), eq(equipment));
        assertThat(eventCaptor.getValue().getDueStatus()).isEqualTo(MaintenanceDueStatus.UPCOMING);
        assertThat(eventCaptor.getValue().getStatus()).isEqualTo(MaintenanceDueEventStatus.DETECTED);
        verify(workOrderService, never()).create(any());
        verify(pprTaskRepository, never()).save(any());
    }

    @Test
    void decommissionedEquipmentIsSkipped() {
        UUID equipmentId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, UUID.randomUUID());
        equipment.setStatus(EquipmentStatus.DECOMMISSIONED);
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));

        var result = service.evaluateEquipment(equipmentId, MaintenanceTriggerSource.CALENDAR_JOB);

        assertThat(result.checkedEquipment()).isEqualTo(1);
        assertThat(result.events()).isZero();
        verify(effectiveRuleResolver, never()).resolveApplicable(any());
    }

    @Test
    void effectiveResolverExclusionCreatesNoEventOrDownstreamAction() {
        UUID equipmentId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, UUID.randomUUID());
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(effectiveRuleResolver.resolveApplicable(equipmentId)).thenReturn(List.of());

        var result = service.evaluateEquipment(equipmentId, MaintenanceTriggerSource.CALENDAR_JOB);

        assertThat(result.events()).isZero();
        assertThat(result.tasksCreated()).isZero();
        assertThat(result.workOrdersCreated()).isZero();
        verify(dueCalculationService, never()).calculate(any(EquipmentMaintenanceEffectiveRule.class));
        verify(eventService, never()).saveEvent(any(), any());
        verify(workOrderService, never()).create(any());
    }

    @Test
    void overrideRuleUsesOverrideValuesAndPopulatesEquipmentMaintenanceRuleId() {
        UUID equipmentId = UUID.randomUUID();
        UUID typeId = UUID.randomUUID();
        UUID baseTemplateId = UUID.randomUUID();
        UUID overrideTemplateId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, typeId);
        MaintenanceRegulation regulation = regulation(UUID.randomUUID(), typeId, AutomationAction.TRACK_ONLY);
        regulation.setTemplateId(baseTemplateId);
        regulation.setTriggerMeterType(MeterType.ENGINE_HOURS);
        regulation.setTriggerMeterInterval(500.0);
        EquipmentMaintenanceRule override = overrideRule(equipmentId, regulation.getId(), overrideTemplateId);
        override.setTriggerMeterInterval(250.0);
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(effectiveRuleResolver.resolveApplicable(equipmentId)).thenReturn(List.of(
                EquipmentMaintenanceEffectiveRule.fromOverride(equipmentId, regulation, override)
        ));
        when(dueCalculationService.calculate(any(EquipmentMaintenanceEffectiveRule.class)))
                .thenReturn(meterRuleDue(equipmentId, regulation.getId(), override.getId(), 260.0, 250.0));
        when(eventRepository.findByScopeAndCycleKey(
                eq(equipmentId), eq(regulation.getId()), eq(override.getId()), any())).thenReturn(Optional.empty());
        when(eventService.saveEvent(any(), eq(equipment))).thenAnswer(invocation -> assignDueEventId(invocation.getArgument(0, MaintenanceDueEvent.class)));

        service.evaluateEquipment(equipmentId, MaintenanceTriggerSource.METER_READING);

        ArgumentCaptor<EquipmentMaintenanceEffectiveRule> ruleCaptor =
                ArgumentCaptor.forClass(EquipmentMaintenanceEffectiveRule.class);
        verify(dueCalculationService).calculate(ruleCaptor.capture());
        assertThat(ruleCaptor.getValue().templateId()).isEqualTo(overrideTemplateId);
        assertThat(ruleCaptor.getValue().triggerMeterInterval()).isEqualTo(250.0);
        ArgumentCaptor<MaintenanceDueEvent> eventCaptor = ArgumentCaptor.forClass(MaintenanceDueEvent.class);
        verify(eventService).saveEvent(eventCaptor.capture(), eq(equipment));
        assertThat(eventCaptor.getValue().getRegulationId()).isEqualTo(regulation.getId());
        assertThat(eventCaptor.getValue().getEquipmentMaintenanceRuleId()).isEqualTo(override.getId());
        assertThat(eventCaptor.getValue().getTemplateId()).isEqualTo(overrideTemplateId);
        assertThat(eventCaptor.getValue().getCycleKey())
                .isEqualTo("%s:RULE:%s:METER:ENGINE_HOURS:250".formatted(equipmentId, override.getId()));
    }

    @Test
    void repeatedMeterReadingsInSameCycleCreateOnlyOneWorkOrder() {
        UUID equipmentId = UUID.randomUUID();
        UUID typeId = UUID.randomUUID();
        UUID regulationId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID workOrderId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, typeId);
        equipment.setResponsibleDepartmentId(departmentId);
        MaintenanceRegulation regulation = regulation(regulationId, typeId, AutomationAction.CREATE_WORK_ORDER);
        regulation.setTriggerMeterType(MeterType.ENGINE_HOURS);
        regulation.setTriggerMeterInterval(500.0);
        MaintenanceDueEvent existing = new MaintenanceDueEvent();
        ReflectionTestUtils.setField(existing, "id", UUID.randomUUID());
        existing.setEquipmentId(equipmentId);
        existing.setRegulationId(regulationId);
        existing.setCycleKey("%s:REG:%s:METER:ENGINE_HOURS:1000".formatted(equipmentId, regulationId));
        existing.setStatus(MaintenanceDueEventStatus.WORK_ORDER_CREATED);
        existing.setDueStatus(MaintenanceDueStatus.DUE);
        existing.setTriggerSource(MaintenanceTriggerSource.METER_READING);
        existing.setCreatedWorkOrderId(workOrderId);

        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        mockEffectiveRules(equipmentId, regulation);
        when(dueCalculationService.calculate(any(EquipmentMaintenanceEffectiveRule.class))).thenReturn(
                meterDue(equipmentId, regulationId, 1000.0),
                meterDue(equipmentId, regulationId, 1001.0)
        );
        when(eventRepository.findByScopeAndCycleKey(
                eq(equipmentId), eq(regulationId), eq(null), any())).thenReturn(Optional.empty(), Optional.of(existing));
        when(eventService.saveEvent(any(), eq(equipment))).thenAnswer(invocation -> assignDueEventId(invocation.getArgument(0, MaintenanceDueEvent.class)));
        when(workOrderNumberService.nextAutoNumber()).thenReturn("WO-AUTO-2026-0001");
        when(workOrderRepository.existsOpenByCycleKey(any())).thenReturn(false);
        when(workOrderService.create(any())).thenReturn(workOrder(workOrderId));
        when(eventRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0, MaintenanceDueEvent.class));

        service.evaluateEquipment(equipmentId, MaintenanceTriggerSource.METER_READING);
        var secondResult = service.evaluateEquipment(equipmentId, MaintenanceTriggerSource.METER_READING);

        verify(workOrderService).create(any());
        assertThat(secondResult.workOrdersCreated()).isZero();
    }

    @Test
    void calendarBlockedCycleKeyIsStableAcrossEvaluations() {
        UUID equipmentId = UUID.randomUUID();
        UUID typeId = UUID.randomUUID();
        UUID regulationId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, typeId);
        MaintenanceRegulation regulation = regulation(regulationId, typeId, AutomationAction.TRACK_ONLY);
        MaintenanceDueEvent existing = new MaintenanceDueEvent();
        ReflectionTestUtils.setField(existing, "id", UUID.randomUUID());
        existing.setEquipmentId(equipmentId);
        existing.setRegulationId(regulationId);
        existing.setCycleKey("%s:%s:CALENDAR:BLOCKED:NO_ANCHOR".formatted(equipmentId, regulationId));
        existing.setStatus(MaintenanceDueEventStatus.DETECTED);
        existing.setDueStatus(MaintenanceDueStatus.BLOCKED);
        existing.setTriggerSource(MaintenanceTriggerSource.CALENDAR_JOB);

        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        mockEffectiveRules(equipmentId, regulation);
        when(dueCalculationService.calculate(any(EquipmentMaintenanceEffectiveRule.class))).thenReturn(
                calendarBlockedDue(equipmentId, regulationId),
                calendarBlockedDue(equipmentId, regulationId)
        );
        when(eventRepository.findByScopeAndCycleKey(
                eq(equipmentId), eq(regulationId), eq(null), any())).thenReturn(Optional.empty(), Optional.of(existing));
        when(eventService.saveEvent(any(), eq(equipment))).thenAnswer(invocation -> assignDueEventId(invocation.getArgument(0, MaintenanceDueEvent.class)));

        service.evaluateEquipment(equipmentId, MaintenanceTriggerSource.CALENDAR_JOB);
        service.evaluateEquipment(equipmentId, MaintenanceTriggerSource.CALENDAR_JOB);

        ArgumentCaptor<MaintenanceDueEvent> eventCaptor = ArgumentCaptor.forClass(MaintenanceDueEvent.class);
        verify(eventService, org.mockito.Mockito.times(2)).saveEvent(eventCaptor.capture(), eq(equipment));
        assertThat(eventCaptor.getAllValues().get(0).getCycleKey())
                .isEqualTo("%s:%s:CALENDAR:BLOCKED:NO_ANCHOR".formatted(equipmentId, regulationId));
        assertThat(eventCaptor.getAllValues().get(1).getCycleKey())
                .isEqualTo("%s:%s:CALENDAR:BLOCKED:NO_ANCHOR".formatted(equipmentId, regulationId));
        assertThat(eventCaptor.getAllValues().get(0).getCycleKey()).doesNotMatch(".*T\\d{2}:\\d{2}:\\d{2}.*");
    }

    @Test
    void calendarCycleKeyUsesDueDateAndRepeatedRunUpdatesSameEvent() {
        UUID equipmentId = UUID.randomUUID();
        UUID typeId = UUID.randomUUID();
        UUID regulationId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, typeId);
        MaintenanceRegulation regulation = regulation(regulationId, typeId, AutomationAction.TRACK_ONLY);
        MaintenanceDueEvent existing = new MaintenanceDueEvent();
        ReflectionTestUtils.setField(existing, "id", UUID.randomUUID());
        existing.setEquipmentId(equipmentId);
        existing.setRegulationId(regulationId);
        existing.setCycleKey("%s:%s:CALENDAR:2026-06-01".formatted(equipmentId, regulationId));
        existing.setStatus(MaintenanceDueEventStatus.DETECTED);
        existing.setDueStatus(MaintenanceDueStatus.DUE);
        existing.setTriggerSource(MaintenanceTriggerSource.CALENDAR_JOB);

        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        mockEffectiveRules(equipmentId, regulation);
        when(dueCalculationService.calculate(any(EquipmentMaintenanceEffectiveRule.class))).thenReturn(
                calendarDue(equipmentId, regulationId),
                calendarDue(equipmentId, regulationId)
        );
        when(eventRepository.findByScopeAndCycleKey(
                eq(equipmentId), eq(regulationId), eq(null), any())).thenReturn(Optional.empty(), Optional.of(existing));
        when(eventService.saveEvent(any(), eq(equipment))).thenAnswer(invocation -> assignDueEventId(invocation.getArgument(0, MaintenanceDueEvent.class)));

        service.evaluateEquipment(equipmentId, MaintenanceTriggerSource.CALENDAR_JOB);
        service.evaluateEquipment(equipmentId, MaintenanceTriggerSource.CALENDAR_JOB);

        ArgumentCaptor<MaintenanceDueEvent> eventCaptor = ArgumentCaptor.forClass(MaintenanceDueEvent.class);
        verify(eventService, org.mockito.Mockito.times(2)).saveEvent(eventCaptor.capture(), eq(equipment));
        assertThat(eventCaptor.getAllValues().get(0).getCycleKey())
                .isEqualTo("%s:%s:CALENDAR:2026-06-01".formatted(equipmentId, regulationId));
        assertThat(eventCaptor.getAllValues().get(1).getId()).isEqualTo(existing.getId());
        assertThat(eventCaptor.getAllValues().get(0).getCycleKey()).doesNotMatch(".*T\\d{2}:\\d{2}:\\d{2}.*");
    }

    @Test
    void calendarCreateWorkOrderPolicyDoesNotCreateDuplicateWorkOrdersForSameCycle() {
        UUID equipmentId = UUID.randomUUID();
        UUID typeId = UUID.randomUUID();
        UUID regulationId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID workOrderId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, typeId);
        equipment.setResponsibleDepartmentId(departmentId);
        MaintenanceRegulation regulation = regulation(regulationId, typeId, AutomationAction.CREATE_WORK_ORDER);
        MaintenanceDueEvent existing = new MaintenanceDueEvent();
        ReflectionTestUtils.setField(existing, "id", UUID.randomUUID());
        existing.setEquipmentId(equipmentId);
        existing.setRegulationId(regulationId);
        existing.setCycleKey("%s:%s:CALENDAR:2026-06-01".formatted(equipmentId, regulationId));
        existing.setStatus(MaintenanceDueEventStatus.WORK_ORDER_CREATED);
        existing.setDueStatus(MaintenanceDueStatus.DUE);
        existing.setTriggerSource(MaintenanceTriggerSource.CALENDAR_JOB);
        existing.setCreatedWorkOrderId(workOrderId);

        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        mockEffectiveRules(equipmentId, regulation);
        when(dueCalculationService.calculate(any(EquipmentMaintenanceEffectiveRule.class))).thenReturn(
                calendarDue(equipmentId, regulationId),
                calendarDue(equipmentId, regulationId)
        );
        when(eventRepository.findByScopeAndCycleKey(
                eq(equipmentId), eq(regulationId), eq(null), any())).thenReturn(Optional.empty(), Optional.of(existing));
        when(eventService.saveEvent(any(), eq(equipment))).thenAnswer(invocation -> assignDueEventId(invocation.getArgument(0, MaintenanceDueEvent.class)));
        when(workOrderNumberService.nextAutoNumber()).thenReturn("WO-AUTO-2026-0001");
        when(workOrderRepository.existsOpenByCycleKey(any())).thenReturn(false);
        when(workOrderService.create(any())).thenReturn(workOrder(workOrderId));
        when(eventRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0, MaintenanceDueEvent.class));

        service.evaluateEquipment(equipmentId, MaintenanceTriggerSource.CALENDAR_JOB);
        service.evaluateEquipment(equipmentId, MaintenanceTriggerSource.CALENDAR_JOB);

        verify(workOrderService).create(any());
    }

    private MaintenanceDueEvent assignDueEventId(MaintenanceDueEvent event) {
        if (event.getId() == null) {
            ReflectionTestUtils.setField(event, "id", UUID.randomUUID());
        }
        return event;
    }

    private PprTask assignPprTaskId(PprTask task) {
        if (task.getId() == null) {
            ReflectionTestUtils.setField(task, "id", UUID.randomUUID());
        }
        return task;
    }

    private PprPlan pprPlan(UUID departmentId) {
        PprPlan plan = new PprPlan();
        ReflectionTestUtils.setField(plan, "id", UUID.randomUUID());
        plan.setDepartmentId(departmentId);
        return plan;
    }

    private void mockEffectiveRules(UUID equipmentId, MaintenanceRegulation regulation) {
        when(effectiveRuleResolver.resolveApplicable(equipmentId)).thenReturn(List.of(
                EquipmentMaintenanceEffectiveRule.fromRegulation(equipmentId, regulation)
        ));
    }

    private Equipment equipment(UUID id, UUID typeId) {
        Equipment equipment = new Equipment();
        ReflectionTestUtils.setField(equipment, "id", id);
        equipment.setEquipmentTypeId(typeId);
        equipment.setCode("EQ-1");
        equipment.setName("Pump");
        equipment.setStatus(EquipmentStatus.ACTIVE);
        return equipment;
    }

    private MaintenanceDueEvent dueEvent(UUID eventId, UUID equipmentId, UUID regulationId, String cycleKey) {
        MaintenanceDueEvent event = new MaintenanceDueEvent();
        ReflectionTestUtils.setField(event, "id", eventId);
        event.setEquipmentId(equipmentId);
        event.setRegulationId(regulationId);
        event.setDueStatus(MaintenanceDueStatus.DUE);
        event.setStatus(MaintenanceDueEventStatus.DETECTED);
        event.setCycleKey(cycleKey);
        event.setDueAt(Instant.parse("2026-06-03T00:00:00Z"));
        return event;
    }

    private void stubDirectWorkOrderCreation(MaintenanceDueEvent event,
                                             Equipment equipment,
                                             MaintenanceRegulation regulation,
                                             UUID workOrderId) {
        when(eventService.getOrThrow(event.getId())).thenReturn(event);
        when(effectiveRuleResolver.resolveApplicable(equipment.getId())).thenReturn(List.of(
                EquipmentMaintenanceEffectiveRule.fromRegulation(equipment.getId(), regulation)
        ));
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipment.getId())).thenReturn(Optional.of(equipment));
        when(workOrderRepository.existsOpenByCycleKey(event.getCycleKey())).thenReturn(false);
        when(workOrderNumberService.nextAutoNumber()).thenReturn("WO-AUTO-2026-0001");
        when(workOrderService.create(any())).thenReturn(workOrder(workOrderId));
        when(eventRepository.save(event)).thenReturn(event);
        when(eventService.toDto(event)).thenReturn(null);
    }

    private MaintenanceRegulation regulation(UUID id, UUID typeId, AutomationAction action) {
        MaintenanceRegulation regulation = new MaintenanceRegulation();
        ReflectionTestUtils.setField(regulation, "id", id);
        regulation.setCode("MR-1");
        regulation.setName("Monthly service");
        regulation.setEquipmentTypeId(typeId);
        regulation.setMaintenanceKind(MaintenanceKind.PREVENTIVE);
        regulation.setNormativeLaborHours(2.0);
        regulation.setPeriodicityUnit(PeriodicityUnit.MONTH);
        regulation.setPeriodicityValue(1);
        regulation.setTriggerPolicy(MaintenanceTriggerPolicy.ANY);
        regulation.setInitialSchedulePolicy(MaintenanceInitialSchedulePolicy.FROM_OPERATION_START);
        regulation.setAutomationAction(action);
        regulation.setDuplicatePolicy(DuplicatePolicy.ONE_ITEM_PER_CYCLE);
        regulation.setDefaultPriority(PriorityLevel.HIGH);
        regulation.setRequiresApproval(false);
        return regulation;
    }

    private MaintenanceOperation operation(UUID templateId, String name, double durationHours) {
        MaintenanceOperation operation = new MaintenanceOperation();
        ReflectionTestUtils.setField(operation, "id", UUID.randomUUID());
        operation.setName(name);
        operation.setDescription(name + " description");
        operation.setDurationHours(durationHours);
        operation.setSafetyNotes("Lockout required");
        operation.setToolsRequired("Standard toolset");
        return operation;
    }

    private MaintenanceDueCalculationDto due(UUID equipmentId, UUID regulationId) {
        return due(equipmentId, regulationId, null);
    }

    private MaintenanceDueCalculationDto due(UUID equipmentId, UUID regulationId, UUID equipmentMaintenanceRuleId) {
        return new MaintenanceDueCalculationDto(
                equipmentId,
                regulationId,
                equipmentMaintenanceRuleId,
                MaintenanceDueStatus.DUE,
                true,
                false,
                null,
                Instant.parse("2026-06-03T00:00:00Z"),
                Instant.parse("2026-06-03T00:00:00Z"),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "due by calendar"
        );
    }

    private EquipmentMaintenanceRule individualRule(UUID equipmentId, UUID ruleId, AutomationAction automationAction) {
        EquipmentMaintenanceRule rule = new EquipmentMaintenanceRule();
        ReflectionTestUtils.setField(rule, "id", ruleId);
        rule.setEquipmentId(equipmentId);
        rule.setCode("EMR-2026-0001");
        rule.setName("Equipment-specific service");
        rule.setMaintenanceKind(MaintenanceKind.PREVENTIVE);
        rule.setNormativeLaborHours(2.0);
        rule.setActive(true);
        rule.setPeriodicityUnit(PeriodicityUnit.MONTH);
        rule.setPeriodicityValue(1);
        rule.setTriggerPolicy(MaintenanceTriggerPolicy.ANY);
        rule.setAutomationAction(automationAction);
        rule.setDuplicatePolicy(DuplicatePolicy.ONE_ITEM_PER_CYCLE);
        rule.setDefaultPriority(PriorityLevel.HIGH);
        rule.setRequiresApproval(false);
        return rule;
    }

    private MaintenanceDueCalculationDto blockedDue(UUID equipmentId, UUID regulationId) {
        return new MaintenanceDueCalculationDto(
                equipmentId,
                regulationId,
                null,
                MaintenanceDueStatus.BLOCKED,
                false,
                false,
                null,
                null,
                null,
                MeterType.ENGINE_HOURS,
                520.0,
                520.0,
                0.0,
                500.0,
                500.0,
                0.0,
                0.0,
                "Required active meter is missing: ENGINE_HOURS"
        );
    }

    private MaintenanceDueCalculationDto meterDue(UUID equipmentId, UUID regulationId, double currentValue) {
        double interval = 500.0;
        double nextMeterDueValue = Math.floor(currentValue / interval) * interval;
        return new MaintenanceDueCalculationDto(
                equipmentId,
                regulationId,
                null,
                MaintenanceDueStatus.DUE,
                false,
                true,
                null,
                null,
                null,
                MeterType.ENGINE_HOURS,
                currentValue,
                currentValue,
                0.0,
                interval,
                nextMeterDueValue,
                0.0,
                0.0,
                "Meter trigger due"
        );
    }

    private MaintenanceDueCalculationDto meterRuleDue(UUID equipmentId,
                                                      UUID regulationId,
                                                      UUID ruleId,
                                                      double currentValue,
                                                      double interval) {
        double nextMeterDueValue = Math.floor(currentValue / interval) * interval;
        return new MaintenanceDueCalculationDto(
                equipmentId,
                regulationId,
                ruleId,
                MaintenanceDueStatus.DUE,
                false,
                true,
                null,
                null,
                null,
                MeterType.ENGINE_HOURS,
                currentValue,
                currentValue,
                0.0,
                interval,
                nextMeterDueValue,
                0.0,
                0.0,
                "Meter trigger due"
        );
    }

    private MaintenanceDueCalculationDto calendarBlockedDue(UUID equipmentId, UUID regulationId) {
        return new MaintenanceDueCalculationDto(
                equipmentId,
                regulationId,
                null,
                MaintenanceDueStatus.BLOCKED,
                false,
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "No completion anchor for calendar trigger."
        );
    }

    private MaintenanceDueCalculationDto calendarDue(UUID equipmentId, UUID regulationId) {
        return new MaintenanceDueCalculationDto(
                equipmentId,
                regulationId,
                null,
                MaintenanceDueStatus.DUE,
                true,
                false,
                null,
                Instant.parse("2026-06-01T00:00:00Z"),
                Instant.parse("2026-06-01T00:00:00Z"),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "Calendar trigger due"
        );
    }

    private MaintenanceDueCalculationDto upcomingDue(UUID equipmentId, UUID regulationId) {
        return new MaintenanceDueCalculationDto(
                equipmentId,
                regulationId,
                null,
                MaintenanceDueStatus.UPCOMING,
                true,
                false,
                null,
                Instant.parse("2026-06-10T00:00:00Z"),
                Instant.parse("2026-06-10T00:00:00Z"),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "Calendar trigger upcoming"
        );
    }

    private MaintenanceDueCalculationDto overdueDue(UUID equipmentId, UUID regulationId) {
        return new MaintenanceDueCalculationDto(
                equipmentId,
                regulationId,
                null,
                MaintenanceDueStatus.OVERDUE,
                true,
                false,
                null,
                Instant.parse("2026-05-20T00:00:00Z"),
                Instant.parse("2026-05-20T00:00:00Z"),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "Calendar trigger overdue"
        );
    }

    private EquipmentMaintenanceRule overrideRule(UUID equipmentId, UUID baseRegulationId, UUID templateId) {
        EquipmentMaintenanceRule rule = new EquipmentMaintenanceRule();
        ReflectionTestUtils.setField(rule, "id", UUID.randomUUID());
        rule.setCode("EMR-1");
        rule.setName("Override service");
        rule.setEquipmentId(equipmentId);
        rule.setBaseRegulationId(baseRegulationId);
        rule.setTemplateId(templateId);
        rule.setMaintenanceKind(MaintenanceKind.PREVENTIVE);
        rule.setNormativeLaborHours(1.0);
        rule.setActive(true);
        rule.setPeriodicityUnit(PeriodicityUnit.MONTH);
        rule.setPeriodicityValue(1);
        rule.setTriggerMeterType(MeterType.ENGINE_HOURS);
        rule.setTriggerMeterInterval(250.0);
        rule.setTriggerPolicy(MaintenanceTriggerPolicy.ANY);
        return rule;
    }

    private WorkOrderDto workOrder(UUID id) {
        return new WorkOrderDto(
                id,
                "WO-AUTO-2026-0001",
                "Monthly service",
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Pump",
                "Maintenance",
                null,
                null,
                null,
                null,
                WorkOrderStatus.PLANNED,
                WorkOrderType.PLANNED,
                null,
                PriorityLevel.HIGH,
                Instant.parse("2026-06-03T09:00:00Z"),
                Instant.parse("2026-06-03T18:00:00Z"),
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
}

