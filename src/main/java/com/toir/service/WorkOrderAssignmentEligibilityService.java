package com.toir.service;

import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.users.Brigade;
import com.toir.entity.users.BrigadeMember;
import com.toir.entity.users.Employee;
import com.toir.exception.RestException;
import com.toir.repository.contarctor.ContractorContractRepository;
import com.toir.repository.users.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class WorkOrderAssignmentEligibilityService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Tashkent");

    private final EmployeeRepository employeeRepository;
    private final CounteragentService counteragentService;
    private final ContractorContractRepository contractorContractRepository;
    private final WorkOrderService workOrderService;

    @Transactional(readOnly = true)
    public boolean isCurrentlyEligible(WorkOrder workOrder) {
        if (workOrder == null) return false;
        return eligiblePerformer(workOrder) || eligibleContractor(workOrder);
    }

    private boolean eligiblePerformer(WorkOrder workOrder) {
        Employee employee = workOrder.getPerformerEmployee();
        BrigadeMember member = workOrder.getPerformer();
        if (employee == null && member != null && member.getUserId() != null) {
            var matches = employeeRepository.findAllByUserIdAndIsDeletedFalse(member.getUserId());
            employee = matches.size() == 1 ? matches.getFirst() : null;
        }
        if (employee == null || employee.isDeleted() || !employee.isActive()
                || !java.util.Objects.equals(employee.getDepartmentId(), workOrder.getDepartmentId())) {
            return false;
        }
        if (member != null) {
            Brigade brigade = member.getBrigade();
            if (!member.isActive() || member.isDeleted() || brigade == null || brigade.isDeleted()
                    || !brigade.isActive()
                    || !java.util.Objects.equals(brigade.getDepartmentId(), workOrder.getDepartmentId())
                    || employee.getUserId() == null
                    || !java.util.Objects.equals(employee.getUserId(), member.getUserId())) {
                return false;
            }
        }
        return workOrderService.hasEligiblePerformerSkills(workOrder);
    }


    private boolean eligibleContractor(WorkOrder workOrder) {
        if (workOrder.getCounteragentId() == null) return false;
        try {
            counteragentService.loadActive(workOrder.getCounteragentId(), "work order assignment");
        } catch (RestException ex) {
            return false;
        }
        return contractorContractRepository.existsActiveCounteragentContractValidOn(
                workOrder.getCounteragentId(), LocalDate.now(BUSINESS_ZONE));
    }
}
