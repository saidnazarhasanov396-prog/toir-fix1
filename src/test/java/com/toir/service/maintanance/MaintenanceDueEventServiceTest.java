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
import com.toir.security.ScopeAccessService;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
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

    @Mock
    ScopeAccessService scopeAccessService;

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
    void saveEventResolvesOperationalIssueWhenStatusIsNoLongerOverdueOrBlocked() {
        UUID eventId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        MaintenanceDueEvent event = event(eventId, equipmentId, MaintenanceDueStatus.DUE);
        Equipment equipment = equipment(equipmentId, null, UUID.randomUUID());
        when(repository.save(event)).thenReturn(event);

        service.saveEvent(event, equipment);

        verify(operationalIssueService).resolveOpen("MaintenanceDueEvent", eventId);
        verify(operationalIssueService, never()).openOrUpdate(
                any(OperationalIssueType.class),
                any(NotificationSeverity.class),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
        );
    }

    @Test
    void saveEventNormalizesMeterExactThresholdBeforeOperationalIssueSync() {
        UUID eventId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        MaintenanceDueEvent event = event(eventId, equipmentId, MaintenanceDueStatus.OVERDUE);
        event.setTriggerSource(MaintenanceTriggerSource.METER_READING);
        event.setMeterType(com.toir.enums.MeterType.MILEAGE_KM);
        event.setMeterCurrentValue(13000.0);
        event.setMeterAnchorValue(0.0);
        event.setMeterInterval(1000.0);
        event.setMeterRemaining(0.0);
        event.setExplanation("Calendar trigger not due from regulation created; Meter trigger overdue");
        Equipment equipment = equipment(equipmentId, null, UUID.randomUUID());
        when(repository.save(event)).thenReturn(event);

        service.saveEvent(event, equipment);

        assertThat(event.getDueStatus()).isEqualTo(MaintenanceDueStatus.DUE);
        assertThat(event.getExplanation()).contains("Meter trigger due");
        assertThat(event.getExplanation()).doesNotContain("Meter trigger overdue");
        verify(operationalIssueService).resolveOpen("MaintenanceDueEvent", eventId);
        verify(operationalIssueService, never()).openOrUpdate(
                any(OperationalIssueType.class),
                any(NotificationSeverity.class),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
        );
    }

    @Test
    void saveEventNormalizesStaleMeterOverdueWhenRemainingIsPositive() {
        UUID eventId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        MaintenanceDueEvent event = event(eventId, equipmentId, MaintenanceDueStatus.OVERDUE);
        event.setTriggerSource(MaintenanceTriggerSource.METER_READING);
        event.setMeterType(com.toir.enums.MeterType.ENGINE_HOURS);
        event.setMeterCurrentValue(980.0);
        event.setMeterAnchorValue(500.0);
        event.setMeterInterval(500.0);
        event.setMeterRemaining(20.0);
        event.setExplanation("Meter trigger overdue");
        Equipment equipment = equipment(equipmentId, null, UUID.randomUUID());
        when(repository.save(event)).thenReturn(event);

        service.saveEvent(event, equipment);

        assertThat(event.getDueStatus()).isEqualTo(MaintenanceDueStatus.UPCOMING);
        assertThat(event.getExplanation()).contains("Meter trigger upcoming");
        assertThat(event.getExplanation()).doesNotContain("Meter trigger overdue");
        verify(operationalIssueService).resolveOpen("MaintenanceDueEvent", eventId);
        verify(operationalIssueService, never()).openOrUpdate(
                any(OperationalIssueType.class),
                any(NotificationSeverity.class),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
        );
    }

    @Test
    void cancelOpenEventMarksItCancelledAndResolved() {
        UUID eventId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        MaintenanceDueEvent event = event(eventId, equipmentId, MaintenanceDueStatus.DUE);
        Equipment equipment = equipment(equipmentId, null, departmentId);
        event.setStatus(MaintenanceDueEventStatus.DETECTED);
        when(repository.findByIdAndIsDeletedFalse(eventId)).thenReturn(Optional.of(event));
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(repository.save(event)).thenReturn(event);

        MaintenanceDueEvent result = service.cancel(eventId, "manual override");

        verify(scopeAccessService).assertCanAccessDepartment(departmentId);
        assertThat(result.getStatus()).isEqualTo(MaintenanceDueEventStatus.CANCELLED);
        assertThat(result.getResolutionReason()).isEqualTo("manual override");
        assertThat(result.getResolvedAt()).isNotNull();
        verify(repository).save(event);
        verify(operationalIssueService).resolveOpen("MaintenanceDueEvent", eventId);
    }

    @Test
    void cancelDueEventDeniesCrossDepartmentScopedUser() {
        UUID eventId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        MaintenanceDueEvent event = event(eventId, equipmentId, MaintenanceDueStatus.DUE);
        Equipment equipment = equipment(equipmentId, null, departmentId);
        event.setStatus(MaintenanceDueEventStatus.DETECTED);
        when(repository.findByIdAndIsDeletedFalse(eventId)).thenReturn(Optional.of(event));
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        doThrow(new AccessDeniedException("Access denied by data scope"))
                .when(scopeAccessService).assertCanAccessDepartment(departmentId);

        assertThatThrownBy(() -> service.cancel(eventId, "manual override"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("data scope");

        verify(repository, never()).save(any());
        verify(operationalIssueService, never()).resolveOpen(any(), any());
    }

    @Test
    void cancelClosedEventIsRejected() {
        UUID eventId = UUID.randomUUID();
        MaintenanceDueEvent event = event(eventId, UUID.randomUUID(), MaintenanceDueStatus.DUE);
        event.setStatus(MaintenanceDueEventStatus.COMPLETED);
        when(repository.findByIdAndIsDeletedFalse(eventId)).thenReturn(Optional.of(event));
        when(equipmentRepository.findByIdAndIsDeletedFalse(event.getEquipmentId()))
                .thenReturn(Optional.of(equipment(event.getEquipmentId(), null, UUID.randomUUID())));

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

    @Test
    void toDtoNormalizesMeterEventAtExactThresholdAsDue() {
        UUID eventId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        MaintenanceDueEvent event = event(eventId, equipmentId, MaintenanceDueStatus.OVERDUE);
        event.setTriggerSource(MaintenanceTriggerSource.METER_READING);
        event.setMeterType(com.toir.enums.MeterType.MILEAGE_KM);
        event.setMeterCurrentValue(13000.0);
        event.setMeterAnchorValue(0.0);
        event.setMeterInterval(1000.0);
        event.setMeterRemaining(0.0);
        event.setExplanation("Meter trigger overdue; Calendar trigger not due from regulation created");
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.empty());

        var dto = service.toDto(event);

        assertThat(dto.dueStatus()).isEqualTo(MaintenanceDueStatus.DUE);
        assertThat(dto.meterRemaining()).isZero();
        assertThat(dto.explanation()).contains("Meter trigger due");
        assertThat(dto.explanation()).doesNotContain("Meter trigger overdue");
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
