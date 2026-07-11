package com.toir.security;

import com.toir.entity.equipment.Equipment;
import com.toir.enums.EquipmentCategory;
import com.toir.enums.EquipmentStatus;
import com.toir.repository.CalibrationRecordRepository;
import com.toir.repository.DowntimeEventRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.actualCost.ActualCostRepository;
import com.toir.repository.defects.DefectRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.repair.RepairRequestRepository;
import com.toir.repository.users.EmployeeRepository;
import com.toir.repository.users.UserCertificationRepository;
import com.toir.repository.users.UserRepository;
import com.toir.service.RcmService;
import com.toir.service.ReportsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReportsPbacScopeTest {

    EquipmentRepository equipmentRepository;
    RepairRequestRepository repairRequestRepository;
    DefectRepository defectRepository;
    WorkOrderRepository workOrderRepository;
    DowntimeEventRepository downtimeEventRepository;
    ActualCostRepository actualCostRepository;
    CalibrationRecordRepository calibrationRecordRepository;
    UserCertificationRepository userCertificationRepository;
    UserRepository userRepository;
    EmployeeRepository employeeRepository;
    RcmService rcmService;
    ScopeAccessService scopeAccessService;
    ReportsService service;

    @BeforeEach
    void setUp() {
        equipmentRepository = mock(EquipmentRepository.class);
        repairRequestRepository = mock(RepairRequestRepository.class);
        defectRepository = mock(DefectRepository.class);
        workOrderRepository = mock(WorkOrderRepository.class);
        downtimeEventRepository = mock(DowntimeEventRepository.class);
        actualCostRepository = mock(ActualCostRepository.class);
        calibrationRecordRepository = mock(CalibrationRecordRepository.class);
        userCertificationRepository = mock(UserCertificationRepository.class);
        userRepository = mock(UserRepository.class);
        employeeRepository = mock(EmployeeRepository.class);
        rcmService = mock(RcmService.class);
        scopeAccessService = mock(ScopeAccessService.class);
        service = new ReportsService(
                equipmentRepository,
                repairRequestRepository,
                defectRepository,
                workOrderRepository,
                downtimeEventRepository,
                actualCostRepository,
                calibrationRecordRepository,
                userCertificationRepository,
                userRepository,
                rcmService,
                scopeAccessService,
                employeeRepository
        );
    }

    @Test
    void analyticsExportDoesNotBypassDepartmentScope() {
        UUID departmentA = UUID.randomUUID();
        UUID departmentB = UUID.randomUUID();
        when(scopeAccessService.isScopeAdmin()).thenReturn(false);
        when(scopeAccessService.currentDepartmentIdOrNull()).thenReturn(departmentA);
        when(equipmentRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of(
                equipment("EQ-A", departmentA),
                equipment("EQ-B", departmentB)
        ));

        ReportsService.CsvFile csv = service.equipmentCsv();

        assertThat(csv.content()).contains("EQ-A");
        assertThat(csv.content()).doesNotContain("EQ-B");
    }

    @Test
    void nonAdminWithoutDepartmentCannotExportGlobalData() {
        when(scopeAccessService.isScopeAdmin()).thenReturn(false);
        when(scopeAccessService.currentDepartmentIdOrNull()).thenReturn(null);

        assertThatThrownBy(() -> service.equipmentCsv())
                .isInstanceOf(AccessDeniedException.class);

        verify(equipmentRepository, never()).findAllByIsDeletedFalseOrderByUpdatedAtDesc();
    }

    @Test
    void systemAdminCanExportGlobalData() {
        UUID departmentA = UUID.randomUUID();
        UUID departmentB = UUID.randomUUID();
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(equipmentRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of(
                equipment("EQ-A", departmentA),
                equipment("EQ-B", departmentB)
        ));

        ReportsService.CsvFile csv = service.equipmentCsv();

        assertThat(csv.content()).contains("EQ-A", "EQ-B");
    }

    @Test
    void wildcardCanExportGlobalData() {
        UUID departmentA = UUID.randomUUID();
        UUID departmentB = UUID.randomUUID();
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(equipmentRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of(
                equipment("EQ-A", departmentA),
                equipment("EQ-B", departmentB)
        ));

        ReportsService.CsvFile csv = service.equipmentCsv();

        assertThat(csv.content()).contains("EQ-A", "EQ-B");
    }

    private Equipment equipment(String code, UUID departmentId) {
        Equipment equipment = new Equipment();
        equipment.setId(UUID.randomUUID());
        equipment.setCode(code);
        equipment.setName(code + " name");
        equipment.setEquipmentTypeId(UUID.randomUUID());
        equipment.setDepartmentId(departmentId);
        equipment.setStatus(EquipmentStatus.ACTIVE);
        equipment.setCategory(EquipmentCategory.PRODUCTION_EQUIPMENT);
        return equipment;
    }
}
