package com.toir.service;

import com.toir.dto.laborentry.LaborEntryDto;
import com.toir.entity.LaborEntry;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.projects.ActualCost;
import com.toir.entity.projects.CostCategory;
import com.toir.entity.users.User;
import com.toir.enums.ActualCostSourceType;
import com.toir.enums.UserStatus;
import com.toir.enums.WorkOrderStatus;
import com.toir.repository.CostCategoryRepository;
import com.toir.repository.LaborEntryRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.actualCost.ActualCostRepository;
import com.toir.repository.users.UserRepository;
import com.toir.service.repair.RepairCampaignBudgetLineResolver;
import com.toir.util.AuditBuilderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class LaborEntryServiceTest {

    @Mock
    LaborEntryRepository repository;

    @Mock
    UserRepository userRepository;

    @Mock
    WorkOrderRepository workOrderRepository;

    @Mock
    ActualCostRepository actualCostRepository;

    @Mock
    CostCategoryRepository costCategoryRepository;

    @Mock
    AuditBuilderService auditBuilderService;

    @Mock
    RepairCampaignBudgetLineResolver repairCampaignBudgetLineResolver;

    @InjectMocks
    LaborEntryService service;

    @Test
    void enrichesLaborEntriesWithUserObject() {
        UUID workOrderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        LaborEntry entry = laborEntry(workOrderId, userId, null, "Internal labor");
        User user = user(userId, "Ali Worker", "ali.worker");

        when(repository.findAllByWorkOrderIdAndIsDeletedFalseOrderByWorkDateAsc(workOrderId)).thenReturn(List.of(entry));
        when(userRepository.findAllByIdInAndIsDeletedFalse(org.mockito.ArgumentMatchers.anyCollection()))
                .thenReturn(List.of(user));

        List<LaborEntryDto> result = service.findByWorkOrder(workOrderId);

        assertThat(result).hasSize(1);
        LaborEntryDto dto = result.get(0);
        assertThat(dto.userId()).isEqualTo(userId);
        assertThat(dto.user()).isNotNull();
        assertThat(dto.user().id()).isEqualTo(userId);
        assertThat(dto.user().fullName()).isEqualTo("Ali Worker");
        assertThat(dto.user().username()).isEqualTo("ali.worker");
        assertThat(dto.user().email()).isEqualTo("ali.worker@example.com");
        assertThat(dto.user().phone()).isEqualTo("+998900000001");
        assertThat(dto.user().status()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void contractorOnlyRowsDoNotLookupUser() {
        UUID workOrderId = UUID.randomUUID();
        LaborEntry contractorRow = laborEntry(workOrderId, null, "Vendor X", "Contractor labor");
        when(repository.findAllByWorkOrderIdAndIsDeletedFalseOrderByWorkDateAsc(workOrderId))
                .thenReturn(List.of(contractorRow));

        List<LaborEntryDto> result = service.findByWorkOrder(workOrderId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).user()).isNull();
        assertThat(result.get(0).userId()).isNull();
        verify(userRepository, never()).findAllByIdInAndIsDeletedFalse(org.mockito.ArgumentMatchers.anyCollection());
    }

    @Test
    void missingDeletedUserDoesNotThrow() {
        UUID workOrderId = UUID.randomUUID();
        UUID missingUserId = UUID.randomUUID();
        LaborEntry legacyRow = laborEntry(workOrderId, missingUserId, null, "Legacy");

        when(repository.findAllByWorkOrderIdAndIsDeletedFalseOrderByWorkDateAsc(workOrderId))
                .thenReturn(List.of(legacyRow));
        when(userRepository.findAllByIdInAndIsDeletedFalse(org.mockito.ArgumentMatchers.anyCollection()))
                .thenReturn(List.of());

        List<LaborEntryDto> result = service.findByWorkOrder(workOrderId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).userId()).isEqualTo(missingUserId);
        assertThat(result.get(0).user()).isNull();
    }

    @Test
    void batchLoadsUsersOnce() {
        UUID workOrderId = UUID.randomUUID();
        UUID userId1 = UUID.randomUUID();
        UUID userId2 = UUID.randomUUID();
        LaborEntry row1 = laborEntry(workOrderId, userId1, null, "r1");
        LaborEntry row2 = laborEntry(workOrderId, userId1, null, "r2");
        LaborEntry row3 = laborEntry(workOrderId, userId2, null, "r3");

        when(repository.findAllByWorkOrderIdAndIsDeletedFalseOrderByWorkDateAsc(workOrderId))
                .thenReturn(List.of(row1, row2, row3));
        when(userRepository.findAllByIdInAndIsDeletedFalse(org.mockito.ArgumentMatchers.anyCollection()))
                .thenReturn(List.of(user(userId1, "U1", "u1"), user(userId2, "U2", "u2")));

        service.findByWorkOrder(workOrderId);

        ArgumentCaptor<Collection<UUID>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(userRepository, times(1)).findAllByIdInAndIsDeletedFalse(captor.capture());
        assertThat(captor.getValue()).containsExactlyInAnyOrder(userId1, userId2);
    }

    @Test
    void createRejectsClosedWorkOrder() {
        UUID workOrderId = UUID.randomUUID();
        WorkOrder workOrder = new WorkOrder();
        workOrder.setId(workOrderId);
        workOrder.setStatus(WorkOrderStatus.CLOSED);
        lenient().when(workOrderRepository.findByIdAndIsDeletedFalse(workOrderId))
                .thenReturn(Optional.of(workOrder));

        assertThatThrownBy(() -> service.create(workOrderId, laborEntryDto()))
                .hasMessageContaining("Labor entries cannot be added to closed work order");

        verify(repository, never()).save(org.mockito.ArgumentMatchers.any(LaborEntry.class));
    }

    @Test
    void createWithRateCreatesSourceLinkedPendingActualCost() {
        UUID workOrderId = UUID.randomUUID();
        UUID laborEntryId = UUID.randomUUID();
        UUID budgetLineId = UUID.randomUUID();
        WorkOrder workOrder = openWorkOrder(workOrderId);
        CostCategory laborCategory = costCategory("LABOR");
        when(workOrderRepository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(repository.save(any(LaborEntry.class))).thenAnswer(invocation -> {
            LaborEntry saved = invocation.getArgument(0);
            saved.setId(laborEntryId);
            return saved;
        });
        when(costCategoryRepository.findFirstByCodeAndIsDeletedFalse("LABOR")).thenReturn(Optional.of(laborCategory));
        when(actualCostRepository.findTopBySourceTypeAndSourceIdAndIsDeletedFalseOrderByUpdatedAtDesc(
                ActualCostSourceType.LABOR_ENTRY,
                laborEntryId
        )).thenReturn(Optional.empty());
        when(repairCampaignBudgetLineResolver.resolveForWorkOrder(workOrder)).thenReturn(budgetLineId);

        service.create(workOrderId, laborEntryDto());

        ArgumentCaptor<ActualCost> captor = ArgumentCaptor.forClass(ActualCost.class);
        verify(actualCostRepository).save(captor.capture());
        ActualCost actualCost = captor.getValue();
        assertThat(actualCost.getSourceType()).isEqualTo(ActualCostSourceType.LABOR_ENTRY);
        assertThat(actualCost.getSourceId()).isEqualTo(laborEntryId);
        assertThat(actualCost.getWorkOrderId()).isEqualTo(workOrderId);
        assertThat(actualCost.getBudgetLineId()).isEqualTo(budgetLineId);
        assertThat(actualCost.getCostCategoryId()).isEqualTo(laborCategory.getId());
        assertThat(actualCost.getAmount()).isEqualTo(375000.0);
    }

    @Test
    void updateWithRateUpdatesExistingLaborActualCostWithoutCreatingDuplicate() {
        UUID workOrderId = UUID.randomUUID();
        UUID laborEntryId = UUID.randomUUID();
        LaborEntry existingLabor = laborEntry(workOrderId, UUID.randomUUID(), null, "Internal labor");
        existingLabor.setId(laborEntryId);
        ActualCost existingCost = new ActualCost();
        existingCost.setId(UUID.randomUUID());
        existingCost.setSourceType(ActualCostSourceType.LABOR_ENTRY);
        existingCost.setSourceId(laborEntryId);
        existingCost.setAmount(10);
        CostCategory laborCategory = costCategory("LABOR");
        when(repository.findByIdAndIsDeletedFalse(laborEntryId)).thenReturn(Optional.of(existingLabor));
        when(repository.save(any(LaborEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(workOrderRepository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(openWorkOrder(workOrderId)));
        when(costCategoryRepository.findFirstByCodeAndIsDeletedFalse("LABOR")).thenReturn(Optional.of(laborCategory));
        when(actualCostRepository.findTopBySourceTypeAndSourceIdAndIsDeletedFalseOrderByUpdatedAtDesc(
                ActualCostSourceType.LABOR_ENTRY,
                laborEntryId
        )).thenReturn(Optional.of(existingCost));

        service.update(laborEntryId, laborEntryDto());

        ArgumentCaptor<ActualCost> captor = ArgumentCaptor.forClass(ActualCost.class);
        verify(actualCostRepository).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(existingCost.getId());
        assertThat(captor.getValue().getAmount()).isEqualTo(375000.0);
    }

    @Test
    void createWithoutRateDoesNotCreateFakeActualCost() {
        UUID workOrderId = UUID.randomUUID();
        when(workOrderRepository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(openWorkOrder(workOrderId)));
        when(repository.save(any(LaborEntry.class))).thenAnswer(invocation -> {
            LaborEntry saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        service.create(workOrderId, new LaborEntryDto(
                null,
                null,
                UUID.randomUUID(),
                null,
                "Contractor",
                LocalDate.of(2026, 5, 15),
                2.5,
                null,
                "Labor"
        ));

        verify(actualCostRepository, never()).save(any(ActualCost.class));
    }

    private LaborEntry laborEntry(UUID workOrderId, UUID userId, String contractorName, String description) {
        LaborEntry entry = new LaborEntry();
        entry.setId(UUID.randomUUID());
        entry.setWorkOrderId(workOrderId);
        entry.setUserId(userId);
        entry.setContractorName(contractorName);
        entry.setWorkDate(LocalDate.of(2026, 5, 15));
        entry.setHours(2.5);
        entry.setRate(150000.0);
        entry.setDescription(description);
        return entry;
    }

    private WorkOrder openWorkOrder(UUID workOrderId) {
        WorkOrder workOrder = new WorkOrder();
        workOrder.setId(workOrderId);
        workOrder.setStatus(WorkOrderStatus.IN_PROGRESS);
        return workOrder;
    }

    private CostCategory costCategory(String code) {
        CostCategory category = new CostCategory();
        category.setId(UUID.randomUUID());
        category.setCode(code);
        category.setName(code);
        return category;
    }

    private User user(UUID id, String fullName, String username) {
        User user = new User();
        user.setId(id);
        user.setFullName(fullName);
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setPhone("+998900000001");
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }

    private LaborEntryDto laborEntryDto() {
        return new LaborEntryDto(
                null,
                null,
                UUID.randomUUID(),
                null,
                "Contractor",
                LocalDate.of(2026, 5, 15),
                2.5,
                150000.0,
                "Labor"
        );
    }
}
