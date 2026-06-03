package com.toir.service.maintanance;

import com.toir.entity.equipment.Equipment;
import com.toir.entity.maintenance.MaintenanceDueEvent;
import com.toir.enums.MaintenanceDueEventStatus;
import com.toir.enums.MaintenanceDueStatus;
import com.toir.enums.MaintenanceTriggerSource;
import com.toir.enums.NotificationSeverity;
import com.toir.enums.OperationalIssueType;
import com.toir.exception.RestException;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.maintenance.MaintenanceDueEventRepository;
import com.toir.repository.maintenance.MaintenanceRegulationRepository;
import com.toir.service.OperationalIssueService;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaintenanceDueEventServiceTest {

    @Mock
    MaintenanceDueEventRepository repository;

    @Mock
    EquipmentRepository equipmentRepository;

    @Mock
    MaintenanceRegulationRepository regulationRepository;

    @Mock
    OperationalIssueService operationalIssueService;

    @InjectMocks
    MaintenanceDueEventService service;

    @Test
    void saveEventOpensOverdueOperationalIssueForResponsibleDepartment() {
        UUID eventId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        UUID responsibleDepartmentId = UUID.randomUUID();
        MaintenanceDueEvent event = event(eventId, equipmentId, MaintenanceDueStatus.OVERDUE);
        Equipment equipment = equipment(equipmentId, responsibleDepartmentId, UUID.randomUUID());
        when(repository.save(event)).thenReturn(event);

        service.saveEvent(event, equipment);

        verify(operationalIssueService).openOrUpdate(
                eq(OperationalIssueType.MAINTENANCE_OVERDUE),
                eq(NotificationSeverity.CRITICAL),
                eq(equipmentId),
                eq(responsibleDepartmentId),
                eq("MaintenanceDueEvent"),
                eq(eventId),
                eq("Maintenance due: cycle-1"),
                eq("overdue by calendar")
        );
    }

    @Test
    void saveEventMapsBlockedDueToMissingMeterIssue() {
        UUID eventId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        MaintenanceDueEvent event = event(eventId, equipmentId, MaintenanceDueStatus.BLOCKED);
        Equipment equipment = equipment(equipmentId, null, departmentId);
        when(repository.save(event)).thenReturn(event);

        service.saveEvent(event, equipment);

        verify(operationalIssueService).openOrUpdate(
                eq(OperationalIssueType.MISSING_METERS),
                eq(NotificationSeverity.CRITICAL),
                eq(equipmentId),
                eq(departmentId),
                eq("MaintenanceDueEvent"),
                eq(eventId),
                eq("Maintenance due: cycle-1"),
                eq("overdue by calendar")
        );
    }

    @Test
    void cancelOpenEventMarksItCancelledAndResolved() {
        UUID eventId = UUID.randomUUID();
        MaintenanceDueEvent event = event(eventId, UUID.randomUUID(), MaintenanceDueStatus.DUE);
        event.setStatus(MaintenanceDueEventStatus.DETECTED);
        when(repository.findByIdAndIsDeletedFalse(eventId)).thenReturn(Optional.of(event));
        when(repository.save(event)).thenReturn(event);

        MaintenanceDueEvent result = service.cancel(eventId, "manual override");

        assertThat(result.getStatus()).isEqualTo(MaintenanceDueEventStatus.CANCELLED);
        assertThat(result.getResolutionReason()).isEqualTo("manual override");
        assertThat(result.getResolvedAt()).isNotNull();
        verify(repository).save(event);
    }

    @Test
    void cancelClosedEventIsRejected() {
        UUID eventId = UUID.randomUUID();
        MaintenanceDueEvent event = event(eventId, UUID.randomUUID(), MaintenanceDueStatus.DUE);
        event.setStatus(MaintenanceDueEventStatus.COMPLETED);
        when(repository.findByIdAndIsDeletedFalse(eventId)).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> service.cancel(eventId, "too late"))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("already closed");
    }

    @Test
    void completeFromWorkOrderMarksEventCompletedAndResolvesOperationalIssue() {
        UUID eventId = UUID.randomUUID();
        MaintenanceDueEvent event = event(eventId, UUID.randomUUID(), MaintenanceDueStatus.DUE);
        event.setStatus(MaintenanceDueEventStatus.WORK_ORDER_CREATED);
        when(repository.save(event)).thenReturn(event);

        MaintenanceDueEvent result = service.completeFromWorkOrder(event, "Work order completed");

        assertThat(result.getStatus()).isEqualTo(MaintenanceDueEventStatus.COMPLETED);
        assertThat(result.getResolutionReason()).isEqualTo("Work order completed");
        assertThat(result.getResolvedAt()).isNotNull();
        verify(repository).save(event);
        verify(operationalIssueService).resolveOpen("MaintenanceDueEvent", eventId);
    }

    @Test
    void completeFromWorkOrderAlreadyCompletedEventIsIdempotent() {
        UUID eventId = UUID.randomUUID();
        Instant resolvedAt = Instant.parse("2026-06-03T12:00:00Z");
        MaintenanceDueEvent event = event(eventId, UUID.randomUUID(), MaintenanceDueStatus.DUE);
        event.setStatus(MaintenanceDueEventStatus.COMPLETED);
        event.setResolvedAt(resolvedAt);
        event.setResolutionReason("Work order completed");

        MaintenanceDueEvent result = service.completeFromWorkOrder(event, "Work order completed");

        assertThat(result.getStatus()).isEqualTo(MaintenanceDueEventStatus.COMPLETED);
        assertThat(result.getResolvedAt()).isEqualTo(resolvedAt);
        verify(repository, never()).save(any(MaintenanceDueEvent.class));
        verify(operationalIssueService).resolveOpen("MaintenanceDueEvent", eventId);
    }

    @Test
    void searchForEmptyPageDoesNotRequireReferenceLookups() {
        when(repository.findAll(
                any(org.springframework.data.jpa.domain.Specification.class),
                any(org.springframework.data.domain.Pageable.class)
        )).thenReturn(org.springframework.data.domain.Page.<MaintenanceDueEvent>empty());

        var result = service.search(null, null, null, null, null, null, null, 0, 20);

        assertThat(result.getContent()).isEmpty();
        ArgumentCaptor<java.util.Collection<UUID>> captor = ArgumentCaptor.forClass(java.util.Collection.class);
        verify(equipmentRepository).findAllByIdInAndIsDeletedFalse(captor.capture());
        assertThat(captor.getValue()).isEmpty();
    }

    private MaintenanceDueEvent event(UUID id, UUID equipmentId, MaintenanceDueStatus dueStatus) {
        MaintenanceDueEvent event = new MaintenanceDueEvent();
        ReflectionTestUtils.setField(event, "id", id);
        event.setEquipmentId(equipmentId);
        event.setDueStatus(dueStatus);
        event.setStatus(MaintenanceDueEventStatus.DETECTED);
        event.setTriggerSource(MaintenanceTriggerSource.CALENDAR_JOB);
        event.setCycleKey("cycle-1");
        event.setDueAt(Instant.parse("2026-06-03T00:00:00Z"));
        event.setExplanation("overdue by calendar");
        return event;
    }

    private Equipment equipment(UUID id, UUID responsibleDepartmentId, UUID departmentId) {
        Equipment equipment = new Equipment();
        ReflectionTestUtils.setField(equipment, "id", id);
        equipment.setCode("EQ-1");
        equipment.setName("Pump");
        equipment.setResponsibleDepartmentId(responsibleDepartmentId);
        equipment.setDepartmentId(departmentId);
        return equipment;
    }
}
