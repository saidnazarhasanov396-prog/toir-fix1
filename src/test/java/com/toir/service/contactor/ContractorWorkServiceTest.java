package com.toir.service.contactor;

import com.toir.dto.contractorwork.ContractorWorkDto;
import com.toir.entity.contractors.Contractor;
import com.toir.entity.contractors.ContractorContract;
import com.toir.entity.contractors.ContractorWork;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.projects.ActualCost;
import com.toir.entity.projects.CostCategory;
import com.toir.enums.ActualCostStatus;
import com.toir.enums.ContractStatus;
import com.toir.enums.ContractorStatus;
import com.toir.enums.ContractorWorkStatus;
import com.toir.enums.WorkOrderStatus;
import com.toir.exception.RestException;
import com.toir.repository.CostCategoryRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.actualCost.ActualCostRepository;
import com.toir.repository.contarctor.ContractorContractRepository;
import com.toir.repository.contarctor.ContractorRepository;
import com.toir.repository.contarctor.ContractorWorkRepository;
import com.toir.service.FinanceScopeService;
import com.toir.util.AuditBuilderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContractorWorkServiceTest {

    @Mock
    ContractorWorkRepository repository;

    @Mock
    ContractorRepository contractorRepository;

    @Mock
    ContractorContractRepository contractorContractRepository;

    @Mock
    WorkOrderRepository workOrderRepository;

    @Mock
    ActualCostRepository actualCostRepository;

    @Mock
    CostCategoryRepository costCategoryRepository;

    @Mock
    FinanceScopeService financeScopeService;

    @Mock
    AuditBuilderService auditBuilderService;

    @InjectMocks
    ContractorWorkService service;

    @BeforeEach
    void setUp() {
        when(repository.save(any(ContractorWork.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(actualCostRepository.save(any(ActualCost.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void startShouldFailWhenContractorIsInactive() {
        UUID contractorId = UUID.randomUUID();
        UUID workId = UUID.randomUUID();
        when(repository.findByIdAndIsDeletedFalse(workId))
                .thenReturn(Optional.of(contractorWork(workId, contractorId, UUID.randomUUID(), ContractorWorkStatus.DRAFT, null)));
        when(contractorRepository.findByIdAndIsDeletedFalse(contractorId))
                .thenReturn(Optional.of(contractor(contractorId, ContractorStatus.INACTIVE)));

        assertThatThrownBy(() -> service.start(workId))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("Contractor must be ACTIVE");
                });
    }

    @Test
    void startShouldFailWhenNoActiveValidContractExists() {
        UUID contractorId = UUID.randomUUID();
        UUID workId = UUID.randomUUID();
        UUID workOrderId = UUID.randomUUID();
        when(repository.findByIdAndIsDeletedFalse(workId))
                .thenReturn(Optional.of(contractorWork(workId, contractorId, workOrderId, ContractorWorkStatus.DRAFT, null)));
        when(contractorRepository.findByIdAndIsDeletedFalse(contractorId))
                .thenReturn(Optional.of(contractor(contractorId, ContractorStatus.ACTIVE)));
        when(contractorContractRepository.findAllByContractorIdAndStatusAndIsDeletedFalse(contractorId, ContractStatus.ACTIVE))
                .thenReturn(List.of(contract(contractorId, ContractStatus.ACTIVE, LocalDate.now().minusDays(10), LocalDate.now().minusDays(1))));

        assertThatThrownBy(() -> service.start(workId))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("ACTIVE contract valid by date");
                });
    }

    @Test
    void startShouldFailWhenLinkedWorkOrderStatusIsNotExecutable() {
        UUID contractorId = UUID.randomUUID();
        UUID workId = UUID.randomUUID();
        UUID workOrderId = UUID.randomUUID();
        when(repository.findByIdAndIsDeletedFalse(workId))
                .thenReturn(Optional.of(contractorWork(workId, contractorId, workOrderId, ContractorWorkStatus.DRAFT, null)));
        when(contractorRepository.findByIdAndIsDeletedFalse(contractorId))
                .thenReturn(Optional.of(contractor(contractorId, ContractorStatus.ACTIVE)));
        when(contractorContractRepository.findAllByContractorIdAndStatusAndIsDeletedFalse(contractorId, ContractStatus.ACTIVE))
                .thenReturn(List.of(contract(contractorId, ContractStatus.ACTIVE, LocalDate.now().minusDays(1), LocalDate.now().plusDays(30))));
        when(workOrderRepository.findByIdAndIsDeletedFalse(workOrderId))
                .thenReturn(Optional.of(workOrder(workOrderId, WorkOrderStatus.DRAFT)));

        assertThatThrownBy(() -> service.start(workId))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("Linked work order must be APPROVED or IN_PROGRESS");
                });
    }

    @Test
    void startShouldSucceedWhenContractorAndContractAndWorkOrderAreValid() {
        UUID contractorId = UUID.randomUUID();
        UUID workId = UUID.randomUUID();
        UUID workOrderId = UUID.randomUUID();
        when(repository.findByIdAndIsDeletedFalse(workId))
                .thenReturn(Optional.of(contractorWork(workId, contractorId, workOrderId, ContractorWorkStatus.DRAFT, null)));
        when(contractorRepository.findByIdAndIsDeletedFalse(contractorId))
                .thenReturn(Optional.of(contractor(contractorId, ContractorStatus.ACTIVE)));
        when(contractorContractRepository.findAllByContractorIdAndStatusAndIsDeletedFalse(contractorId, ContractStatus.ACTIVE))
                .thenReturn(List.of(contract(contractorId, ContractStatus.ACTIVE, LocalDate.now().minusDays(1), LocalDate.now().plusDays(30))));
        when(workOrderRepository.findByIdAndIsDeletedFalse(workOrderId))
                .thenReturn(Optional.of(workOrder(workOrderId, WorkOrderStatus.APPROVED)));

        ContractorWorkDto result = service.start(workId);

        assertThat(result.status()).isEqualTo(ContractorWorkStatus.IN_PROGRESS);
        assertThat(result.startedAt()).isNotNull();
    }

    @Test
    void completeShouldFailWhenStatusIsNotInProgress() {
        UUID workId = UUID.randomUUID();
        when(repository.findByIdAndIsDeletedFalse(workId))
                .thenReturn(Optional.of(contractorWork(workId, UUID.randomUUID(), UUID.randomUUID(), ContractorWorkStatus.DRAFT, null)));

        assertThatThrownBy(() -> service.complete(workId, "done", 10d))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("Only IN_PROGRESS contractor works can be completed");
                });
    }

    @Test
    void completeShouldFailWhenResultIsBlank() {
        UUID workId = UUID.randomUUID();
        when(repository.findByIdAndIsDeletedFalse(workId))
                .thenReturn(Optional.of(contractorWork(workId, UUID.randomUUID(), UUID.randomUUID(), ContractorWorkStatus.IN_PROGRESS, null)));

        assertThatThrownBy(() -> service.complete(workId, "   ", 10d))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("Result is required");
                });
    }

    @Test
    void acceptShouldFailWhenContractorWorkIsNotCompleted() {
        UUID workId = UUID.randomUUID();
        when(repository.findByIdAndIsDeletedFalse(workId))
                .thenReturn(Optional.of(contractorWork(workId, UUID.randomUUID(), UUID.randomUUID(), ContractorWorkStatus.IN_PROGRESS, 10d)));

        assertThatThrownBy(() -> service.accept(workId, UUID.randomUUID(), "accepted"))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("Only completed works can be accepted");
                });
    }

    @Test
    void acceptShouldCreatePendingActualCostWhenCostExists() {
        UUID contractorId = UUID.randomUUID();
        UUID workId = UUID.randomUUID();
        UUID workOrderId = UUID.randomUUID();
        UUID acceptedById = UUID.randomUUID();
        UUID costCategoryId = UUID.randomUUID();

        ContractorWork work = contractorWork(workId, contractorId, workOrderId, ContractorWorkStatus.COMPLETED, 220d);
        when(repository.findByIdAndIsDeletedFalse(workId)).thenReturn(Optional.of(work));
        when(contractorRepository.findByIdAndIsDeletedFalse(contractorId))
                .thenReturn(Optional.of(contractor(contractorId, ContractorStatus.ACTIVE)));
        when(contractorContractRepository.findAllByContractorIdAndStatusAndIsDeletedFalse(contractorId, ContractStatus.ACTIVE))
                .thenReturn(List.of(contract(contractorId, ContractStatus.ACTIVE, LocalDate.now().minusDays(5), LocalDate.now().plusDays(5))));
        when(workOrderRepository.findByIdAndIsDeletedFalse(workOrderId))
                .thenReturn(Optional.of(workOrder(workOrderId, WorkOrderStatus.COMPLETED)));
        when(actualCostRepository.findTopByContractorWorkIdAndIsDeletedFalseOrderByUpdatedAtDesc(workId))
                .thenReturn(Optional.empty());
        when(actualCostRepository.findAllByWorkOrderIdAndIsDeletedFalseOrderByUpdatedAtDesc(workOrderId))
                .thenReturn(List.of(actualCost(workOrderId, costCategoryId, 100d)));

        ContractorWorkDto result = service.accept(workId, acceptedById, "Accepted after inspection");

        assertThat(result.status()).isEqualTo(ContractorWorkStatus.ACCEPTED);
        assertThat(result.acceptedById()).isEqualTo(acceptedById);
        assertThat(result.acceptedAt()).isNotNull();

        ArgumentCaptor<ActualCost> captor = ArgumentCaptor.forClass(ActualCost.class);
        verify(actualCostRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ActualCostStatus.PENDING);
        assertThat(captor.getValue().getContractorWorkId()).isEqualTo(workId);
        assertThat(captor.getValue().getWorkOrderId()).isEqualTo(workOrderId);
        assertThat(captor.getValue().getCostCategoryId()).isEqualTo(costCategoryId);
        assertThat(captor.getValue().getAmount()).isEqualTo(220d);
    }

    @Test
    void acceptShouldNotCreateDuplicateActualCostWhenAlreadyLinked() {
        UUID contractorId = UUID.randomUUID();
        UUID workId = UUID.randomUUID();
        UUID workOrderId = UUID.randomUUID();
        UUID acceptedById = UUID.randomUUID();

        ContractorWork work = contractorWork(workId, contractorId, workOrderId, ContractorWorkStatus.COMPLETED, 180d);
        when(repository.findByIdAndIsDeletedFalse(workId)).thenReturn(Optional.of(work));
        when(contractorRepository.findByIdAndIsDeletedFalse(contractorId))
                .thenReturn(Optional.of(contractor(contractorId, ContractorStatus.ACTIVE)));
        when(contractorContractRepository.findAllByContractorIdAndStatusAndIsDeletedFalse(contractorId, ContractStatus.ACTIVE))
                .thenReturn(List.of(contract(contractorId, ContractStatus.ACTIVE, LocalDate.now().minusDays(5), LocalDate.now().plusDays(5))));
        when(workOrderRepository.findByIdAndIsDeletedFalse(workOrderId))
                .thenReturn(Optional.of(workOrder(workOrderId, WorkOrderStatus.COMPLETED)));
        when(actualCostRepository.findTopByContractorWorkIdAndIsDeletedFalseOrderByUpdatedAtDesc(workId))
                .thenReturn(Optional.of(actualCost(workOrderId, UUID.randomUUID(), 180d)));

        service.accept(workId, acceptedById, "Accepted");

        verify(actualCostRepository, never()).save(any(ActualCost.class));
    }

    @Test
    void startShouldPreserveNotFoundWhenLinkedWorkOrderMissing() {
        UUID contractorId = UUID.randomUUID();
        UUID workId = UUID.randomUUID();
        UUID workOrderId = UUID.randomUUID();
        when(repository.findByIdAndIsDeletedFalse(workId))
                .thenReturn(Optional.of(contractorWork(workId, contractorId, workOrderId, ContractorWorkStatus.DRAFT, null)));
        when(contractorRepository.findByIdAndIsDeletedFalse(contractorId))
                .thenReturn(Optional.of(contractor(contractorId, ContractorStatus.ACTIVE)));
        when(contractorContractRepository.findAllByContractorIdAndStatusAndIsDeletedFalse(contractorId, ContractStatus.ACTIVE))
                .thenReturn(List.of(contract(contractorId, ContractStatus.ACTIVE, LocalDate.now().minusDays(1), LocalDate.now().plusDays(1))));
        when(workOrderRepository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.start(workId))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(ex.getMessage()).contains("Work order not found");
                });
    }

    @Test
    void createShouldFailWhenLinkedWorkOrderIsMissing() {
        UUID contractorId = UUID.randomUUID();
        UUID workOrderId = UUID.randomUUID();
        when(contractorRepository.findByIdAndIsDeletedFalse(contractorId))
                .thenReturn(Optional.of(contractor(contractorId, ContractorStatus.ACTIVE)));
        when(contractorContractRepository.findAllByContractorIdAndStatusAndIsDeletedFalse(contractorId, ContractStatus.ACTIVE))
                .thenReturn(List.of(contract(contractorId, ContractStatus.ACTIVE, LocalDate.now().minusDays(1), LocalDate.now().plusDays(1))));
        when(workOrderRepository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.empty());

        ContractorWorkDto payload = new ContractorWorkDto(
                null,
                contractorId,
                workOrderId,
                "External balancing",
                ContractorWorkStatus.DRAFT,
                null,
                null,
                null,
                null,
                null,
                UUID.randomUUID(),
                null,
                null
        );

        assertThatThrownBy(() -> service.create(payload))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(ex.getMessage()).contains("Work order not found");
                });
    }

    @Test
    void startShouldPreserveNotFoundWhenContractorMissing() {
        UUID contractorId = UUID.randomUUID();
        UUID workId = UUID.randomUUID();
        when(repository.findByIdAndIsDeletedFalse(workId))
                .thenReturn(Optional.of(contractorWork(workId, contractorId, UUID.randomUUID(), ContractorWorkStatus.DRAFT, null)));
        when(contractorRepository.findByIdAndIsDeletedFalse(contractorId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.start(workId))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(ex.getMessage()).contains("Contractor not found");
                });
    }

    @Test
    void acceptShouldResolveFallbackContractorCategoryWhenWorkOrderHasNoActualCosts() {
        UUID contractorId = UUID.randomUUID();
        UUID workId = UUID.randomUUID();
        UUID workOrderId = UUID.randomUUID();
        UUID acceptedById = UUID.randomUUID();
        UUID costCategoryId = UUID.randomUUID();

        ContractorWork work = contractorWork(workId, contractorId, workOrderId, ContractorWorkStatus.COMPLETED, 50d);
        when(repository.findByIdAndIsDeletedFalse(workId)).thenReturn(Optional.of(work));
        when(contractorRepository.findByIdAndIsDeletedFalse(contractorId))
                .thenReturn(Optional.of(contractor(contractorId, ContractorStatus.ACTIVE)));
        when(contractorContractRepository.findAllByContractorIdAndStatusAndIsDeletedFalse(contractorId, ContractStatus.ACTIVE))
                .thenReturn(List.of(contract(contractorId, ContractStatus.ACTIVE, LocalDate.now().minusDays(5), LocalDate.now().plusDays(5))));
        when(workOrderRepository.findByIdAndIsDeletedFalse(workOrderId))
                .thenReturn(Optional.of(workOrder(workOrderId, WorkOrderStatus.COMPLETED)));
        when(actualCostRepository.findTopByContractorWorkIdAndIsDeletedFalseOrderByUpdatedAtDesc(workId))
                .thenReturn(Optional.empty());
        when(actualCostRepository.findAllByWorkOrderIdAndIsDeletedFalseOrderByUpdatedAtDesc(workOrderId))
                .thenReturn(List.of());

        CostCategory category = new CostCategory();
        ReflectionTestUtils.setField(category, "id", costCategoryId);
        when(costCategoryRepository.findFirstByCodeAndIsDeletedFalse("CTR"))
                .thenReturn(Optional.of(category));

        service.accept(workId, acceptedById, "Accepted with fallback category");

        ArgumentCaptor<ActualCost> captor = ArgumentCaptor.forClass(ActualCost.class);
        verify(actualCostRepository).save(captor.capture());
        assertThat(captor.getValue().getCostCategoryId()).isEqualTo(costCategoryId);
    }

    private Contractor contractor(UUID id, ContractorStatus status) {
        Contractor contractor = new Contractor();
        ReflectionTestUtils.setField(contractor, "id", id);
        contractor.setStatus(status);
        return contractor;
    }

    private ContractorContract contract(UUID contractorId, ContractStatus status, LocalDate startDate, LocalDate endDate) {
        ContractorContract contract = new ContractorContract();
        contract.setContractorId(contractorId);
        contract.setStatus(status);
        contract.setStartDate(startDate);
        contract.setEndDate(endDate);
        return contract;
    }

    private WorkOrder workOrder(UUID id, WorkOrderStatus status) {
        WorkOrder workOrder = new WorkOrder();
        ReflectionTestUtils.setField(workOrder, "id", id);
        workOrder.setStatus(status);
        return workOrder;
    }

    private ContractorWork contractorWork(
            UUID id,
            UUID contractorId,
            UUID workOrderId,
            ContractorWorkStatus status,
            Double cost
    ) {
        ContractorWork work = new ContractorWork();
        ReflectionTestUtils.setField(work, "id", id);
        work.setContractorId(contractorId);
        work.setWorkOrderId(workOrderId);
        work.setStatus(status);
        work.setCost(cost);
        work.setDescription("Contractor work");
        return work;
    }

    private ActualCost actualCost(UUID workOrderId, UUID costCategoryId, double amount) {
        ActualCost actualCost = new ActualCost();
        actualCost.setWorkOrderId(workOrderId);
        actualCost.setCostCategoryId(costCategoryId);
        actualCost.setAmount(amount);
        actualCost.setStatus(ActualCostStatus.PENDING);
        actualCost.setCostDate(Instant.now());
        return actualCost;
    }
}
