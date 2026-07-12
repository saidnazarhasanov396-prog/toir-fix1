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
        BrigadeMember performer = workOrder.getPerformer();
        if (performer == null || !performer.isActive() || performer.isDeleted() || performer.getUserId() == null) {
            return false;
        }
        Brigade brigade = performer.getBrigade();
        if (brigade == null || brigade.isDeleted() || !brigade.isActive()
                || !java.util.Objects.equals(brigade.getDepartmentId(), workOrder.getDepartmentId())) {
            return false;
        }
        Employee employee = employeeRepository.findByUserIdAndIsDeletedFalse(performer.getUserId()).orElse(null);
        return employee != null && employee.isActive()
                && java.util.Objects.equals(employee.getDepartmentId(), workOrder.getDepartmentId())
                && workOrderService.hasEligiblePerformerSkills(workOrder);
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
