package com.toir.service;

import com.toir.entity.Counteragent;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.users.Brigade;
import com.toir.entity.users.BrigadeMember;
import com.toir.entity.users.Employee;
import com.toir.repository.contarctor.ContractorContractRepository;
import com.toir.repository.users.EmployeeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkOrderAssignmentEligibilityServiceTest {
    @Mock EmployeeRepository employeeRepository;
    @Mock CounteragentService counteragentService;
    @Mock ContractorContractRepository contractRepository;
    @Mock WorkOrderService workOrderService;

    @Test
    void rejectsInactiveEmployeeAndSkillIneligiblePerformer() {
        var service = service();
        UUID department = UUID.randomUUID(); UUID user = UUID.randomUUID();
        WorkOrder workOrder = workOrder(department);
        Brigade brigade = Brigade.builder().departmentId(department).active(true).build();
        BrigadeMember member = BrigadeMember.builder().brigade(brigade).userId(user).active(true).build();
        workOrder.setPerformer(member);
        Employee employee = new Employee(); employee.setUserId(user); employee.setDepartmentId(department); employee.setActive(false);
        when(employeeRepository.findByUserIdAndIsDeletedFalse(user)).thenReturn(Optional.of(employee));

        brigade.setActive(false);
        assertThat(service.isCurrentlyEligible(workOrder)).isFalse();
        brigade.setActive(true);
        assertThat(service.isCurrentlyEligible(workOrder)).isFalse();

        employee.setActive(true);
        when(workOrderService.hasEligiblePerformerSkills(workOrder)).thenReturn(false);
        assertThat(service.isCurrentlyEligible(workOrder)).isFalse();
    }

    @Test
    void acceptsOnlyActiveCounteragentWithCurrentActiveContract() {
        var service = service();
        UUID counteragentId = UUID.randomUUID();
        WorkOrder workOrder = workOrder(UUID.randomUUID()); workOrder.setCounteragentId(counteragentId);
        when(counteragentService.loadActive(counteragentId, "work order assignment"))
                .thenThrow(com.toir.exception.RestException.badRequest("inactive"))
                .thenReturn(new Counteragent());
        when(contractRepository.existsActiveCounteragentContractValidOn(eq(counteragentId), any(LocalDate.class)))
                .thenReturn(false, true);

        assertThat(service.isCurrentlyEligible(workOrder)).isFalse();
        assertThat(service.isCurrentlyEligible(workOrder)).isFalse();
        assertThat(service.isCurrentlyEligible(workOrder)).isTrue();
    }

    private WorkOrderAssignmentEligibilityService service() {
        return new WorkOrderAssignmentEligibilityService(employeeRepository, counteragentService,
                contractRepository, workOrderService);
    }

    private static WorkOrder workOrder(UUID department) {
        WorkOrder workOrder = new WorkOrder(); workOrder.setId(UUID.randomUUID()); workOrder.setDepartmentId(department);
        return workOrder;
    }
}
