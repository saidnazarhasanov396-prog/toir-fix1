package com.toir.service.contactor;

import com.toir.dto.common.BankAccountDto;
import com.toir.entity.common.BankAccount;
import com.toir.entity.contractors.Contractor;
import com.toir.entity.contractors.ContractorContract;
import com.toir.entity.contractors.ContractorWork;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.projects.ActualCost;
import com.toir.entity.projects.CostCategory;
import com.toir.enums.ActualCostStatus;
import com.toir.enums.ContractStatus;
import com.toir.enums.ContractorStatus;
import com.toir.enums.ContractorWorkStatus;
import com.toir.enums.PriorityLevel;
import com.toir.enums.WorkOrderStatus;
import com.toir.enums.WorkOrderType;
import com.toir.enums.WorkType;
import com.toir.repository.CostCategoryRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.actualCost.ActualCostRepository;
import com.toir.repository.contarctor.ContractorContractRepository;
import com.toir.repository.contarctor.ContractorRepository;
import com.toir.repository.contarctor.ContractorWorkRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.util.AuditBuilderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContractorServiceTest {

    @Mock
    ContractorRepository repository;

    @Mock
    ContractorContractRepository contractorContractRepository;

    @Mock
    ContractorWorkRepository contractorWorkRepository;

    @Mock
    WorkOrderRepository workOrderRepository;

    @Mock
    ActualCostRepository actualCostRepository;

    @Mock
    CostCategoryRepository costCategoryRepository;

    @Mock
    EquipmentRepository equipmentRepository;

    @Mock
    AuditBuilderService auditBuilderService;

    @InjectMocks
    ContractorService service;

    @Test
    void findDetailByIdReturnsContractsAssignedWorkOrdersAndEnrichedContractorWorks() {
        UUID contractorId = UUID.randomUUID();
        UUID contractId = UUID.randomUUID();
        UUID workOrderId = UUID.randomUUID();
        UUID contractorWorkId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        UUID actualCostId = UUID.randomUUID();
        UUID costCategoryId = UUID.randomUUID();

        when(repository.findByIdAndIsDeletedFalse(contractorId))
                .thenReturn(Optional.of(contractor(contractorId)));
        when(contractorContractRepository.findAllByContractorIdAndIsDeletedFalse(contractorId))
                .thenReturn(List.of(contract(contractId, contractorId)));
        when(workOrderRepository.findAllByContractorIdAndIsDeletedFalseOrderByUpdatedAtDesc(contractorId))
                .thenReturn(List.of(workOrder(workOrderId, contractorId, equipmentId)));
        when(contractorWorkRepository.findAllByContractorIdAndIsDeletedFalse(contractorId))
                .thenReturn(List.of(contractorWork(contractorWorkId, contractorId, workOrderId)));
        when(actualCostRepository.findAllByContractorWorkIdInAndIsDeletedFalseOrderByUpdatedAtDesc(List.of(contractorWorkId)))
                .thenReturn(List.of(actualCost(actualCostId, contractorWorkId, workOrderId, costCategoryId)));
        when(costCategoryRepository.findAllByIdInAndIsDeletedFalse(List.of(costCategoryId)))
                .thenReturn(List.of(costCategory(costCategoryId)));
        when(equipmentRepository.findAllByIdInAndIsDeletedFalse(List.of(equipmentId)))
                .thenReturn(List.of(equipment(equipmentId)));

        var detail = service.findDetailById(contractorId);

        assertThat(detail.id()).isEqualTo(contractorId);
        assertThat(detail.contracts()).hasSize(1);
        assertThat(detail.contracts().getFirst().id()).isEqualTo(contractId);
        assertThat(detail.workOrders()).hasSize(1);
        assertThat(detail.workOrders().getFirst().id()).isEqualTo(workOrderId);
        assertThat(detail.workOrders().getFirst().equipment().code()).isEqualTo("EQ-1");
        assertThat(detail.contractorWorks()).hasSize(1);
        assertThat(detail.contractorWorks().getFirst().workOrder().status()).isEqualTo("APPROVED");
        assertThat(detail.contractorWorks().getFirst().actualCosts()).hasSize(1);
        assertThat(detail.contractorWorks().getFirst().actualCosts().getFirst().costCategory().code()).isEqualTo("CTR");
        assertThat(detail.summary().activeContracts()).isEqualTo(1);
        assertThat(detail.summary().activeWorkOrders()).isEqualTo(1);
        assertThat(detail.summary().inProgressWorks()).isEqualTo(1);
        assertThat(detail.summary().totalContractAmount()).isEqualTo(10_000d);
        assertThat(detail.summary().totalContractorWorkCost()).isEqualTo(4_500d);
        assertThat(detail.summary().pendingActualCostReview()).isEqualTo(1);
    }

    @Test
    void findDetailByIdIncludesLegalAndBankDetails() {
        UUID contractorId = UUID.randomUUID();
        Contractor contractor = contractor(contractorId);
        contractor.setDirectorName("Karimov A.A.");
        contractor.setBankAccounts(List.of(
                new BankAccount("NBU", "20208000123456789", "00401"),
                new BankAccount("Kapitalbank", "00208000987654321", "01158")
        ));

        when(repository.findByIdAndIsDeletedFalse(contractorId)).thenReturn(Optional.of(contractor));
        when(contractorContractRepository.findAllByContractorIdAndIsDeletedFalse(contractorId))
                .thenReturn(List.of());
        when(contractorWorkRepository.findAllByContractorIdAndIsDeletedFalse(contractorId))
                .thenReturn(List.of());
        when(workOrderRepository.findAllByContractorIdAndIsDeletedFalseOrderByUpdatedAtDesc(contractorId))
                .thenReturn(List.of());

        var detail = service.findDetailById(contractorId);

        assertThat(detail.directorName()).isEqualTo("Karimov A.A.");
        assertThat(detail.bankAccounts()).hasSize(2);
        assertThat(detail.bankAccounts().get(0).bankName()).isEqualTo("NBU");
        assertThat(detail.bankAccounts().get(1).bankAccount()).isEqualTo("00208000987654321");
    }

    private Contractor contractor(UUID id) {
        Contractor contractor = new Contractor();
        contractor.setId(id);
        contractor.setCode("CTR-2026-0001");
        contractor.setName("Tashkent Service LLC");
        contractor.setStatus(ContractorStatus.ACTIVE);
        return contractor;
    }

    private ContractorContract contract(UUID id, UUID contractorId) {
        ContractorContract contract = new ContractorContract();
        contract.setId(id);
        contract.setContractorId(contractorId);
        contract.setNumber("CNT-2026-001");
        contract.setSubject("Maintenance services");
        contract.setStartDate(LocalDate.of(2026, 1, 1));
        contract.setEndDate(LocalDate.of(2026, 12, 31));
        contract.setAmount(10_000d);
        contract.setStatus(ContractStatus.ACTIVE);
        return contract;
    }

    private WorkOrder workOrder(UUID id, UUID contractorId, UUID equipmentId) {
        WorkOrder workOrder = new WorkOrder();
        workOrder.setId(id);
        workOrder.setNumber("WO-2026-0001");
        workOrder.setTitle("Pump repair");
        workOrder.setContractorId(contractorId);
        workOrder.setEquipmentId(equipmentId);
        workOrder.setStatus(WorkOrderStatus.APPROVED);
        workOrder.setType(WorkOrderType.PLANNED);
        workOrder.setWorkType(WorkType.REPAIR);
        workOrder.setPriority(PriorityLevel.MEDIUM);
        workOrder.setCreatedAt(Instant.parse("2026-06-24T07:00:00Z"));
        return workOrder;
    }

    private ContractorWork contractorWork(UUID id, UUID contractorId, UUID workOrderId) {
        ContractorWork work = new ContractorWork();
        work.setId(id);
        work.setContractorId(contractorId);
        work.setWorkOrderId(workOrderId);
        work.setDescription("Contractor pump repair");
        work.setStatus(ContractorWorkStatus.IN_PROGRESS);
        work.setStartedAt(Instant.parse("2026-06-24T08:00:00Z"));
        work.setCost(4_500d);
        return work;
    }

    private ActualCost actualCost(UUID id, UUID contractorWorkId, UUID workOrderId, UUID costCategoryId) {
        ActualCost actualCost = new ActualCost();
        actualCost.setId(id);
        actualCost.setContractorWorkId(contractorWorkId);
        actualCost.setWorkOrderId(workOrderId);
        actualCost.setCostCategoryId(costCategoryId);
        actualCost.setAmount(1_250d);
        actualCost.setStatus(ActualCostStatus.PENDING);
        actualCost.setCostDate(Instant.parse("2026-06-24T09:00:00Z"));
        actualCost.setCreatedAt(Instant.parse("2026-06-24T09:00:00Z"));
        return actualCost;
    }

    private CostCategory costCategory(UUID id) {
        CostCategory category = new CostCategory();
        category.setId(id);
        category.setCode("CTR");
        category.setName("Contractor services");
        return category;
    }

    private Equipment equipment(UUID id) {
        Equipment equipment = new Equipment();
        equipment.setId(id);
        equipment.setCode("EQ-1");
        equipment.setName("Pump #1");
        return equipment;
    }
}
