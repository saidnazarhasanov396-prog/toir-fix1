package com.toir.service.maintanance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.toir.entity.PprPlan;
import com.toir.entity.maintenance.MaintenanceScheduleCalculationItem;
import com.toir.enums.MaintenanceKind;
import com.toir.enums.MaintenanceTriggerPolicy;
import com.toir.exception.MaintenanceScheduleSnapshotConflictException;
import com.toir.exception.MaintenanceScheduleSnapshotConflictException.Reason;
import com.toir.exception.RestException;
import com.toir.repository.PprPlanRepository;
import com.toir.repository.maintenance.MaintenanceScheduleCalculationItemRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MaintenanceScheduleSnapshotServiceTest {

    @Mock
    private MaintenanceScheduleCalculationItemRepository repository;

    @Mock
    private PprPlanRepository planRepository;

    @Mock
    private EntityManager entityManager;

    private MaintenanceScheduleSnapshotService service;

    @BeforeEach
    void setUp() {
        service = new MaintenanceScheduleSnapshotService(
                repository,
                planRepository,
                entityManager,
                new MaintenanceScheduleSourceItemKeyGenerator()
        );
    }

    @Test
    void insertsACompletePreviouslyMissingRevision() {
        UUID planId = UUID.randomUUID();
        MaintenanceScheduleCalculationItem item =
                item(planId, 1L, UUID.randomUUID(), 1L);
        allowInsert(planId);
        when(repository.countByPlanIdAndCalculationRevision(planId, 1L))
                .thenReturn(0L, 1L);
        when(repository.saveAll(any())).thenReturn(List.of(item));

        List<MaintenanceScheduleCalculationItem> saved =
                service.insertRevision(planId, 1L, List.of(item));

        assertThat(saved).containsExactly(item);
        assertThat(item.getSourceItemKey()).matches("[0-9a-f]{64}");
        assertThat(item.getSourceItemKeyVersion()).isEqualTo(1);
        verify(entityManager).flush();
        InOrder insertOrder = inOrder(planRepository, repository);
        insertOrder.verify(planRepository)
                .findByIdAndIsDeletedFalseForUpdate(planId);
        insertOrder.verify(repository)
                .countByPlanIdAndCalculationRevision(planId, 1L);
        insertOrder.verify(repository).saveAll(List.of(item));
    }

    @Test
    void rejectsAnExistingRevisionWithTypedConflict() {
        UUID planId = UUID.randomUUID();
        allowInsert(planId);
        when(repository.countByPlanIdAndCalculationRevision(planId, 1L)).thenReturn(2L);

        assertThatThrownBy(() ->
                service.insertRevision(
                        planId,
                        1L,
                        List.of(item(planId, 1L, UUID.randomUUID(), 1L))))
                .isInstanceOf(MaintenanceScheduleSnapshotConflictException.class)
                .satisfies(error -> assertThat(
                        ((MaintenanceScheduleSnapshotConflictException) error).getReason())
                        .isEqualTo(Reason.REVISION_ALREADY_EXISTS));

        verify(repository, never()).saveAll(any());
        verify(entityManager, never()).flush();
    }

    @Test
    void rejectsDuplicateSourceKeysBeforePersistence() {
        UUID planId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        MaintenanceScheduleCalculationItem first =
                item(planId, 1L, equipmentId, 1L);
        MaintenanceScheduleCalculationItem duplicate =
                item(planId, 1L, equipmentId, 1L);
        allowInsert(planId);
        when(repository.countByPlanIdAndCalculationRevision(planId, 1L)).thenReturn(0L);

        assertThatThrownBy(() ->
                service.insertRevision(planId, 1L, List.of(first, duplicate)))
                .isInstanceOf(MaintenanceScheduleSnapshotConflictException.class)
                .satisfies(error -> assertThat(
                        ((MaintenanceScheduleSnapshotConflictException) error).getReason())
                        .isEqualTo(Reason.DUPLICATE_SOURCE_ITEM_KEY));

        verify(repository, never()).saveAll(any());
    }

    @Test
    void rejectsItemsFromAnotherPlanOrRevision() {
        UUID planId = UUID.randomUUID();
        allowInsert(planId);
        when(repository.countByPlanIdAndCalculationRevision(planId, 2L)).thenReturn(0L);

        assertThatThrownBy(() -> service.insertRevision(
                planId,
                2L,
                List.of(item(UUID.randomUUID(), 1L, UUID.randomUUID(), 1L))
        ))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("same plan and revision");

        verify(repository, never()).saveAll(any());
    }

    @Test
    void rejectsExistingEntitiesInsteadOfMergingHistoricalRows() {
        UUID planId = UUID.randomUUID();
        MaintenanceScheduleCalculationItem item =
                item(planId, 1L, UUID.randomUUID(), 1L);
        item.setId(UUID.randomUUID());
        allowInsert(planId);
        when(repository.countByPlanIdAndCalculationRevision(planId, 1L)).thenReturn(0L);

        assertThatThrownBy(() -> service.insertRevision(planId, 1L, List.of(item)))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("only new calculation items");

        verify(repository, never()).saveAll(any());
    }

    @Test
    void readsAndCountsOnlyTheExactRevision() {
        UUID planId = UUID.randomUUID();
        MaintenanceScheduleCalculationItem first =
                item(planId, 1L, UUID.randomUUID(), 1L);
        MaintenanceScheduleCalculationItem second =
                item(planId, 1L, UUID.randomUUID(), 1L);
        when(repository.findAllByPlanIdAndCalculationRevisionOrderBySourceItemKey(planId, 1L))
                .thenReturn(List.of(first, second));
        when(repository.countByPlanIdAndCalculationRevision(planId, 1L)).thenReturn(2L);

        assertThat(service.readRevision(planId, 1L)).containsExactly(first, second);
        assertThat(service.countRevision(planId, 1L)).isEqualTo(2L);
    }

    @Test
    void insertingNewRevisionDoesNotMutateHistoricalRevision() {
        UUID planId = UUID.randomUUID();
        MaintenanceScheduleCalculationItem historical =
                item(planId, 1L, UUID.randomUUID(), 1L);
        MaintenanceScheduleCalculationItem current =
                item(planId, 2L, UUID.randomUUID(), 1L);
        allowInsert(planId);
        when(repository.countByPlanIdAndCalculationRevision(planId, 2L))
                .thenReturn(0L, 1L);
        when(repository.saveAll(any())).thenReturn(List.of(current));
        when(repository.findAllByPlanIdAndCalculationRevisionOrderBySourceItemKey(planId, 1L))
                .thenReturn(List.of(historical));

        service.insertRevision(planId, 2L, List.of(current));

        assertThat(service.readRevision(planId, 1L)).containsExactly(historical);
        verify(repository).saveAll(List.of(current));
    }

    private void allowInsert(UUID planId) {
        when(planRepository.findByIdAndIsDeletedFalseForUpdate(planId))
                .thenReturn(Optional.of(plan(planId)));
    }

    private static PprPlan plan(UUID planId) {
        PprPlan plan = new PprPlan();
        plan.setId(planId);
        return plan;
    }

    private static MaintenanceScheduleCalculationItem item(
            UUID planId,
            long revision,
            UUID equipmentId,
            long cycleOrdinal) {
        return MaintenanceScheduleCalculationItem.builder()
                .plan(plan(planId))
                .calculationRevision(revision)
                .equipmentId(equipmentId)
                .maintenanceType(MaintenanceKind.PREVENTIVE)
                .triggerType(MaintenanceTriggerPolicy.ANY)
                .triggerDiscriminator("calendar")
                .cycleOrdinal(cycleOrdinal)
                .plannedDate(LocalDate.of(2026, 1, 1))
                .taskTitleSnapshot("Preventive maintenance")
                .build();
    }
}
