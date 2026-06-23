package com.toir.service;

import com.toir.entity.Department;
import com.toir.entity.contractors.ContractorWork;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.projects.ActualCost;
import com.toir.entity.projects.CostCategory;
import com.toir.enums.ActualCostStatus;
import com.toir.enums.ContractorWorkStatus;
import com.toir.repository.CostCategoryRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.actualCost.ActualCostRepository;
import com.toir.repository.actualCost.ActualCostReviewEventRepository;
import com.toir.repository.actualCost.ActualCostReviewRouteOverrideRepository;
import com.toir.repository.contarctor.ContractorWorkRepository;
import com.toir.repository.department.DepartmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActualCostReviewFacadeServiceTest {

    @Mock
    ActualCostRepository actualCostRepository;
    @Mock
    ActualCostService actualCostService;
    @Mock
    FinanceScopeService financeScopeService;
    @Mock
    NotificationService notificationService;
    @Mock
    ActualCostReviewRouteOverrideService routeOverrideService;
    @Mock
    ActualCostReviewRouteOverrideRepository routeOverrideRepository;
    @Mock
    ActualCostReviewEventRepository eventRepository;
    @Mock
    WorkOrderRepository workOrderRepository;
    @Mock
    ContractorWorkRepository contractorWorkRepository;
    @Mock
    DepartmentRepository departmentRepository;
    @Mock
    CostCategoryRepository costCategoryRepository;

    @InjectMocks
    ActualCostReviewFacadeService service;

    @Test
    void actualCostRegisterEnrichesDepartmentContractorWorkOrderAndCostCategoryRefs() {
        UUID actualCostId = UUID.randomUUID();
        UUID contractorWorkId = UUID.randomUUID();
        UUID contractorId = UUID.randomUUID();
        UUID workOrderId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID costCategoryId = UUID.randomUUID();

        ActualCost actualCost = new ActualCost();
        ReflectionTestUtils.setField(actualCost, "id", actualCostId);
        actualCost.setContractorWorkId(contractorWorkId);
        actualCost.setCostCategoryId(costCategoryId);
        actualCost.setStatus(ActualCostStatus.PENDING);
        actualCost.setAmount(100.0);
        actualCost.setCostDate(Instant.parse("2026-05-26T09:00:00Z"));
        actualCost.setNotes("pump");

        ContractorWork contractorWork = new ContractorWork();
        ReflectionTestUtils.setField(contractorWork, "id", contractorWorkId);
        contractorWork.setContractorId(contractorId);
        contractorWork.setWorkOrderId(workOrderId);
        contractorWork.setDescription("Pump contractor work");
        contractorWork.setStatus(ContractorWorkStatus.IN_PROGRESS);
        contractorWork.setCost(100.0);

        WorkOrder workOrder = new WorkOrder();
        ReflectionTestUtils.setField(workOrder, "id", workOrderId);
        workOrder.setNumber("WO-1");
        workOrder.setTitle("Pump repair");
        workOrder.setDepartmentId(departmentId);

        Department department = new Department();
        ReflectionTestUtils.setField(department, "id", departmentId);
        department.setCode("D-1");
        department.setName("Mechanical");

        CostCategory costCategory = new CostCategory();
        ReflectionTestUtils.setField(costCategory, "id", costCategoryId);
        costCategory.setCode("CC-1");
        costCategory.setName("Service");

        when(actualCostRepository.findAllByFiltersOrderByUpdatedAtDesc(null, "pump"))
                .thenReturn(List.of(actualCost));
        when(financeScopeService.filterActualCosts(List.of(actualCost))).thenReturn(List.of(actualCost));
        when(routeOverrideRepository.findFirstByActualCostIdAndActiveTrueAndIsDeletedFalseOrderByCreatedAtDesc(actualCostId))
                .thenReturn(Optional.empty());
        when(contractorWorkRepository.findByIdAndIsDeletedFalse(contractorWorkId)).thenReturn(Optional.of(contractorWork));
        when(workOrderRepository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(departmentRepository.findByIdAndIsDeletedFalse(departmentId)).thenReturn(Optional.of(department));
        when(costCategoryRepository.findByIdAndIsDeletedFalse(costCategoryId)).thenReturn(Optional.of(costCategory));

        var item = service.actualCostRegister("pump").getFirst();

        assertThat(item.department()).hasFieldOrPropertyWithValue("id", departmentId);
        assertThat(item.workOrder()).hasFieldOrPropertyWithValue("id", workOrderId);
        assertThat(item.costCategory()).hasFieldOrPropertyWithValue("id", costCategoryId);
        assertThat(item.contractorWork()).hasFieldOrPropertyWithValue("id", contractorWorkId);
        Object contractor = ((com.toir.dto.financialreview.ActualCostReviewItem.ContractorWorkRef) item.contractorWork()).contractor();
        assertThat(contractor).hasFieldOrPropertyWithValue("id", contractorId);
    }
}
