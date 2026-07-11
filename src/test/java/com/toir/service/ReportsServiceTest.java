package com.toir.service;

import com.toir.entity.equipment.Equipment;
import com.toir.entity.users.Employee;
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
import com.toir.security.ScopeAccessService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportsServiceTest {

    @Mock EquipmentRepository equipmentRepository;
    @Mock RepairRequestRepository repairRequestRepository;
    @Mock DefectRepository defectRepository;
    @Mock WorkOrderRepository workOrderRepository;
    @Mock DowntimeEventRepository downtimeEventRepository;
    @Mock ActualCostRepository actualCostRepository;
    @Mock CalibrationRecordRepository calibrationRecordRepository;
    @Mock UserCertificationRepository userCertificationRepository;
    @Mock UserRepository userRepository;
    @Mock EmployeeRepository employeeRepository;
    @Mock RcmService rcmService;
    @Mock ScopeAccessService scopeAccessService;

    @InjectMocks ReportsService service;

    @Test
    void equipmentCsvKeepsRawResponsibleIdAndBatchEnrichesEmployeeIdentity() {
        UUID firstResponsibleId = UUID.randomUUID();
        UUID secondResponsibleId = UUID.randomUUID();
        Equipment first = equipment("EQ-1", firstResponsibleId);
        Equipment second = equipment("EQ-2", secondResponsibleId);
        Employee firstEmployee = employee(firstResponsibleId, "EMP-001", "Ali", "Valiyev");
        Employee secondEmployee = employee(secondResponsibleId, "EMP-002", "Bobur", "Karimov");

        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(equipmentRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of(first, second));
        when(employeeRepository.findAllByIdInAndIsDeletedFalse(anyCollection()))
                .thenReturn(List.of(firstEmployee, secondEmployee));

        String csv = service.equipmentCsv().content();

        assertThat(csv).contains("responsibleId,responsibleEmployeeCode,responsibleEmployeeName");
        assertThat(csv).contains(firstResponsibleId + ",EMP-001,Valiyev Ali");
        assertThat(csv).contains(secondResponsibleId + ",EMP-002,Karimov Bobur");
        verify(employeeRepository, times(1)).findAllByIdInAndIsDeletedFalse(anyCollection());
    }

    private static Equipment equipment(String code, UUID responsibleId) {
        Equipment equipment = new Equipment();
        equipment.setId(UUID.randomUUID());
        equipment.setCode(code);
        equipment.setName("Equipment " + code);
        equipment.setInventoryNumber("INV-" + code);
        equipment.setEquipmentTypeId(UUID.randomUUID());
        equipment.setDepartmentId(UUID.randomUUID());
        equipment.setResponsibleId(responsibleId);
        equipment.setStatus(EquipmentStatus.ACTIVE);
        equipment.setCategory(EquipmentCategory.PRODUCTION_EQUIPMENT);
        return equipment;
    }

    private static Employee employee(UUID id, String code, String firstName, String lastName) {
        Employee employee = new Employee();
        employee.setId(id);
        employee.setPersonnelNumber(code);
        employee.setFirstName(firstName);
        employee.setLastName(lastName);
        return employee;
    }
}
