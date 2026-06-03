package com.toir.service.maintanance;

import com.toir.dto.maintenanceplanning.MaintenanceDueCalculationDto;
import com.toir.dto.workorder.WorkOrderDto;
import com.toir.dto.workorder.WorkOrderRequest;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.maintenance.MaintenanceDueEvent;
import com.toir.entity.maintenance.MaintenanceRegulation;
import com.toir.enums.AutomationAction;
import com.toir.enums.DuplicatePolicy;
import com.toir.enums.EquipmentStatus;
import com.toir.enums.MaintenanceDueEventStatus;
import com.toir.enums.MaintenanceDueStatus;
import com.toir.enums.MaintenanceKind;
import com.toir.enums.MaintenanceTriggerPolicy;
import com.toir.enums.MaintenanceTriggerSource;
import com.toir.enums.PeriodicityUnit;
import com.toir.enums.PriorityLevel;
import com.toir.enums.WorkOrderStatus;
import com.toir.enums.WorkOrderType;
import com.toir.repository.PprPlanRepository;
import com.toir.repository.PprTaskRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.maintenance.MaintenanceDueEventRepository;
import com.toir.repository.maintenance.MaintenanceRegulationRepository;
import com.toir.repository.users.UserRepository;
import com.toir.service.WorkOrderService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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
    MaintenanceDueEventService eventService;

    @Mock
    MaintenanceDueCalculationService dueCalculationService;

    @Mock
    PprPlanRepository pprPlanRepository;

    @Mock
    PprTaskRepository pprTaskRepository;

    @Mock
    WorkOrderRepository workOrderRepository;

    @Mock
    WorkOrderService workOrderService;

    @Mock
    UserRepository userRepository;

    @InjectMocks
    MaintenanceAutomationService service;

    @Test
    void evaluateEquipmentCreatesDueEventFirstForTrackOnlyPolicy() {
        UUID equipmentId = UUID.randomUUID();
        UUID typeId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, typeId);
        MaintenanceRegulation regulation = regulation(UUID.randomUUID(), typeId, AutomationAction.TRACK_ONLY);
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(regulationRepository.findAllByEquipmentTypeIdAndActiveTrueAndIsDeletedFalse(typeId)).thenReturn(List.of(regulation));
        when(dueCalculationService.calculate(equipmentId, regulation)).thenReturn(due(equipmentId, regulation.getId()));
        when(eventRepository.findByCycleKeyAndIsDeletedFalse(any())).thenReturn(Optional.empty());
        when(eventService.saveEvent(any(), eq(equipment))).thenAnswer(invocation -> assignId(invocation.getArgument(0)));

        var result = service.evaluateEquipment(equipmentId, MaintenanceTriggerSource.METER_READING);

        assertThat(result.events()).isEqualTo(1);
        assertThat(result.tasksCreated()).isZero();
        assertThat(result.workOrdersCreated()).isZero();
        ArgumentCaptor<MaintenanceDueEvent> eventCaptor = ArgumentCaptor.forClass(MaintenanceDueEvent.class);
        verify(eventService).saveEvent(eventCaptor.capture(), eq(equipment));
        assertThat(eventCaptor.getValue().getStatus()).isEqualTo(MaintenanceDueEventStatus.DETECTED);
        assertThat(eventCaptor.getValue().getTriggerSource()).isEqualTo(MaintenanceTriggerSource.METER_READING);
        verify(pprTaskRepository, never()).save(any());
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
        when(regulationRepository.findAllByEquipmentTypeIdAndActiveTrueAndIsDeletedFalse(typeId)).thenReturn(List.of(regulation));
        when(dueCalculationService.calculate(equipmentId, regulation)).thenReturn(due(equipmentId, regulation.getId()));
        when(eventRepository.findByCycleKeyAndIsDeletedFalse(any())).thenReturn(Optional.empty());
        when(pprTaskRepository.existsOpenByCycleKey(any())).thenReturn(true);
        when(eventService.saveEvent(any(), eq(equipment))).thenAnswer(invocation -> assignId(invocation.getArgument(0)));

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
    void createWorkOrderPolicyCreatesWorkOrderAndStoresCreatedWorkOrderId() {
        UUID equipmentId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID typeId = UUID.randomUUID();
        UUID workOrderId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, typeId);
        equipment.setResponsibleDepartmentId(departmentId);
        MaintenanceRegulation regulation = regulation(UUID.randomUUID(), typeId, AutomationAction.CREATE_WORK_ORDER);
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(regulationRepository.findAllByEquipmentTypeIdAndActiveTrueAndIsDeletedFalse(typeId)).thenReturn(List.of(regulation));
        when(dueCalculationService.calculate(equipmentId, regulation)).thenReturn(due(equipmentId, regulation.getId()));
        when(eventRepository.findByCycleKeyAndIsDeletedFalse(any())).thenReturn(Optional.empty());
        when(eventService.saveEvent(any(), eq(equipment))).thenAnswer(invocation -> assignId(invocation.getArgument(0)));
        when(workOrderRepository.countByIsDeletedFalse()).thenReturn(0L);
        when(workOrderRepository.existsByNumberAndIsDeletedFalse(any())).thenReturn(false);
        when(workOrderRepository.existsOpenByCycleKey(any())).thenReturn(false);
        when(workOrderService.create(any())).thenReturn(workOrder(workOrderId));
        when(eventRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

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
    void decommissionedEquipmentIsSkipped() {
        UUID equipmentId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, UUID.randomUUID());
        equipment.setStatus(EquipmentStatus.DECOMMISSIONED);
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));

        var result = service.evaluateEquipment(equipmentId, MaintenanceTriggerSource.CALENDAR_JOB);

        assertThat(result.checkedEquipment()).isEqualTo(1);
        assertThat(result.events()).isZero();
        verify(regulationRepository, never()).findAllByEquipmentTypeIdAndActiveTrueAndIsDeletedFalse(any());
    }

    private MaintenanceDueEvent assignId(MaintenanceDueEvent event) {
        if (event.getId() == null) {
            ReflectionTestUtils.setField(event, "id", UUID.randomUUID());
        }
        return event;
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
        regulation.setAutomationAction(action);
        regulation.setDuplicatePolicy(DuplicatePolicy.ONE_ITEM_PER_CYCLE);
        regulation.setDefaultPriority(PriorityLevel.HIGH);
        regulation.setRequiresApproval(false);
        return regulation;
    }

    private MaintenanceDueCalculationDto due(UUID equipmentId, UUID regulationId) {
        return new MaintenanceDueCalculationDto(
                equipmentId,
                regulationId,
                null,
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
