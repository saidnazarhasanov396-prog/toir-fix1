package com.toir.service;

import com.toir.entity.ApprovalRequest;
import com.toir.entity.PprTask;
import com.toir.entity.contractors.ContractorWork;
import com.toir.entity.defects.Defect;
import com.toir.entity.equipment.CalibrationRecord;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.maintenance.MaintenanceDueEvent;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.projects.MaintenanceBudget;
import com.toir.entity.repair.RepairRequest;
import com.toir.enums.EquipmentStatus;
import com.toir.enums.MaintenanceDueEventStatus;
import com.toir.enums.MaintenanceDueStatus;
import com.toir.enums.MaintenanceTriggerSource;
import com.toir.enums.NotificationSeverity;
import com.toir.enums.OperationalIssueType;
import com.toir.enums.WorkOrderStatus;
import com.toir.repository.ApprovalRequestRepository;
import com.toir.repository.CalibrationRecordRepository;
import com.toir.repository.PprTaskRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.contarctor.ContractorWorkRepository;
import com.toir.repository.defects.DefectRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.maintenance.MaintenanceBudgetRepository;
import com.toir.repository.maintenance.MaintenanceDueEventRepository;
import com.toir.repository.repair.RepairRequestRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OperationalIssueScannerServiceTest {

    @Mock
    OperationalIssueService issueService;

    @Mock
    WorkOrderRepository workOrderRepository;

    @Mock
    PprTaskRepository pprTaskRepository;

    @Mock
    RepairRequestRepository repairRequestRepository;

    @Mock
    CalibrationRecordRepository calibrationRecordRepository;

    @Mock
    EquipmentRepository equipmentRepository;

    @Mock
    MaintenanceDueEventRepository maintenanceDueEventRepository;

    @Mock
    ContractorWorkRepository contractorWorkRepository;

    @Mock
    MaintenanceBudgetRepository maintenanceBudgetRepository;

    @Mock
    ApprovalRequestRepository approvalRequestRepository;

    @Mock
    DefectRepository defectRepository;

    @InjectMocks
    OperationalIssueScannerService service;

    @BeforeEach
    void emptySources() {
        when(workOrderRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of());
        when(pprTaskRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of());
        when(repairRequestRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of());
        when(calibrationRecordRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of());
        when(equipmentRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of());
        when(maintenanceDueEventRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of());
        when(contractorWorkRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of());
        when(maintenanceBudgetRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of());
        when(approvalRequestRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of());
        when(defectRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of());
    }

    @Test
    void scanCreatesOverdueWorkOrderIssue() {
        UUID workOrderId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        WorkOrder workOrder = new WorkOrder();
        ReflectionTestUtils.setField(workOrder, "id", workOrderId);
        workOrder.setNumber("WO-1");
        workOrder.setEquipmentId(equipmentId);
        workOrder.setDepartmentId(departmentId);
        workOrder.setStatus(WorkOrderStatus.IN_PROGRESS);
        workOrder.setEndPlannedAt(Instant.now().minusSeconds(60));
        when(workOrderRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of(workOrder));

        var result = service.scanAll();

        assertThat(result.openedOrUpdated()).isEqualTo(1);
        verify(issueService).openOrUpdate(
                eq(OperationalIssueType.OVERDUE_WORK_ORDER),
                eq(NotificationSeverity.WARNING),
                eq(equipmentId),
                eq(departmentId),
                eq("WorkOrder"),
                eq(workOrderId),
                eq("Overdue work order WO-1"),
                any()
        );
    }

    @Test
    void scanCreatesExpiredEquipmentLifetimeIssue() {
        UUID equipmentId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, departmentId);
        equipment.setOperationStartDate(LocalDate.now().minusYears(2));
        equipment.setExpectedLifetimeMonths(12);
        when(equipmentRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of(equipment));

        service.scanAll();

        verify(issueService).openOrUpdate(
                eq(OperationalIssueType.EQUIPMENT_LIFETIME_EXPIRED),
                eq(NotificationSeverity.CRITICAL),
                eq(equipmentId),
                eq(departmentId),
                eq("EquipmentLifetime"),
                eq(equipmentId),
                eq("Equipment lifetime expired: EQ-1"),
                any()
        );
    }

    @Test
    void scanCreatesMaintenanceBlockedAsMissingMeterIssue() {
        UUID eventId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        MaintenanceDueEvent event = new MaintenanceDueEvent();
        ReflectionTestUtils.setField(event, "id", eventId);
        event.setEquipmentId(equipmentId);
        event.setDueStatus(MaintenanceDueStatus.BLOCKED);
        event.setStatus(MaintenanceDueEventStatus.DETECTED);
        event.setTriggerSource(MaintenanceTriggerSource.CALENDAR_JOB);
        event.setCycleKey("cycle-1");
        event.setExplanation("meter missing");
        when(maintenanceDueEventRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of(event));
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment(equipmentId, departmentId)));

        service.scanAll();

        verify(issueService).openOrUpdate(
                eq(OperationalIssueType.MISSING_METERS),
                eq(NotificationSeverity.CRITICAL),
                eq(equipmentId),
                eq(departmentId),
                eq("MaintenanceDueEvent"),
                eq(eventId),
                eq("Maintenance due: cycle-1"),
                eq("meter missing")
        );
    }

    private Equipment equipment(UUID id, UUID departmentId) {
        Equipment equipment = new Equipment();
        ReflectionTestUtils.setField(equipment, "id", id);
        equipment.setCode("EQ-1");
        equipment.setName("Pump");
        equipment.setStatus(EquipmentStatus.ACTIVE);
        equipment.setResponsibleDepartmentId(departmentId);
        return equipment;
    }
}
