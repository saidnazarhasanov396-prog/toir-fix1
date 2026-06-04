package com.toir.service.maintanance;

import com.toir.dto.notification.NotificationDto;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.maintenance.MaintenanceDueEvent;
import com.toir.entity.maintenance.MaintenanceRegulation;
import com.toir.enums.AutomationAction;
import com.toir.enums.DuplicatePolicy;
import com.toir.enums.MaintenanceDueEventStatus;
import com.toir.enums.MaintenanceDueStatus;
import com.toir.enums.MaintenanceKind;
import com.toir.enums.MaintenanceTriggerPolicy;
import com.toir.enums.MaintenanceTriggerSource;
import com.toir.enums.NotificationSeverity;
import com.toir.enums.PeriodicityUnit;
import com.toir.security.PermissionConstants;
import com.toir.service.NotificationService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaintenanceAutomationNotificationServiceTest {

    @Mock
    NotificationService notificationService;

    @InjectMocks
    MaintenanceAutomationNotificationService service;

    @Test
    void eventStatusNotificationUsesDefaultResponsibleFirst() {
        UUID eventId = UUID.randomUUID();
        UUID responsibleEmployeeId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Equipment equipment = equipment(UUID.randomUUID());
        equipment.setResponsibleDepartmentId(UUID.randomUUID());
        equipment.setResponsibleId(UUID.randomUUID());
        MaintenanceDueEvent event = event(eventId, equipment.getId(), MaintenanceDueStatus.DUE);
        MaintenanceRegulation regulation = regulation(AutomationAction.TRACK_ONLY);
        regulation.setDefaultResponsibleId(responsibleEmployeeId);
        EquipmentMaintenanceEffectiveRule rule = EquipmentMaintenanceEffectiveRule.fromRegulation(equipment.getId(), regulation);
        when(notificationService.notifyEmployee(
                eq(responsibleEmployeeId),
                eq(MaintenanceAutomationNotificationService.MAINTENANCE_DUE),
                eq("Maintenance due: cycle-1"),
                eq(NotificationSeverity.WARNING),
                eq(MaintenanceAutomationNotificationService.ENTITY_TYPE),
                eq(eventId.toString())
        )).thenReturn(Optional.of(notification(userId, MaintenanceAutomationNotificationService.MAINTENANCE_DUE, eventId)));

        int sent = service.notifyEventStatus(event, rule, equipment);

        assertThat(sent).isEqualTo(1);
        verify(notificationService, never()).notifyDepartmentByPermission(
                eq(equipment.getResponsibleDepartmentId()),
                eq(PermissionConstants.MAINTENANCE_EVENT_READ),
                eq(MaintenanceAutomationNotificationService.MAINTENANCE_DUE),
                eq("Maintenance due: cycle-1"),
                eq(NotificationSeverity.WARNING),
                eq(MaintenanceAutomationNotificationService.ENTITY_TYPE),
                eq(eventId.toString())
        );
    }

    @Test
    void eventStatusNotificationUsesDepartmentWhenNoDefaultResponsible() {
        UUID eventId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        Equipment equipment = equipment(UUID.randomUUID());
        equipment.setResponsibleDepartmentId(departmentId);
        MaintenanceDueEvent event = event(eventId, equipment.getId(), MaintenanceDueStatus.OVERDUE);
        EquipmentMaintenanceEffectiveRule rule = EquipmentMaintenanceEffectiveRule.fromRegulation(equipment.getId(), regulation(AutomationAction.TRACK_ONLY));
        when(notificationService.notifyDepartmentByPermission(
                eq(departmentId),
                eq(PermissionConstants.MAINTENANCE_EVENT_READ),
                eq(MaintenanceAutomationNotificationService.MAINTENANCE_OVERDUE),
                eq("Maintenance overdue: cycle-1"),
                eq(NotificationSeverity.CRITICAL),
                eq(MaintenanceAutomationNotificationService.ENTITY_TYPE),
                eq(eventId.toString())
        )).thenReturn(List.of(notification(UUID.randomUUID(), MaintenanceAutomationNotificationService.MAINTENANCE_OVERDUE, eventId)));

        int sent = service.notifyEventStatus(event, rule, equipment);

        assertThat(sent).isEqualTo(1);
    }

    @Test
    void eventStatusNotificationFallsBackToEquipmentResponsibleWhenDepartmentHasNoRecipients() {
        UUID eventId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID responsibleEmployeeId = UUID.randomUUID();
        Equipment equipment = equipment(UUID.randomUUID());
        equipment.setResponsibleDepartmentId(departmentId);
        equipment.setResponsibleId(responsibleEmployeeId);
        MaintenanceDueEvent event = event(eventId, equipment.getId(), MaintenanceDueStatus.BLOCKED);
        EquipmentMaintenanceEffectiveRule rule = EquipmentMaintenanceEffectiveRule.fromRegulation(equipment.getId(), regulation(AutomationAction.TRACK_ONLY));
        when(notificationService.notifyDepartmentByPermission(
                eq(departmentId),
                eq(PermissionConstants.MAINTENANCE_EVENT_READ),
                eq(MaintenanceAutomationNotificationService.MAINTENANCE_BLOCKED),
                eq("Maintenance blocked: cycle-1"),
                eq(NotificationSeverity.CRITICAL),
                eq(MaintenanceAutomationNotificationService.ENTITY_TYPE),
                eq(eventId.toString())
        )).thenReturn(List.of());
        when(notificationService.notifyEmployee(
                eq(responsibleEmployeeId),
                eq(MaintenanceAutomationNotificationService.MAINTENANCE_BLOCKED),
                eq("Maintenance blocked: cycle-1"),
                eq(NotificationSeverity.CRITICAL),
                eq(MaintenanceAutomationNotificationService.ENTITY_TYPE),
                eq(eventId.toString())
        )).thenReturn(Optional.of(notification(UUID.randomUUID(), MaintenanceAutomationNotificationService.MAINTENANCE_BLOCKED, eventId)));

        int sent = service.notifyEventStatus(event, rule, equipment);

        assertThat(sent).isEqualTo(1);
    }

    @Test
    void notificationDedupIsPreservedByExistingNotificationService() {
        UUID eventId = UUID.randomUUID();
        UUID responsibleEmployeeId = UUID.randomUUID();
        Equipment equipment = equipment(UUID.randomUUID());
        MaintenanceDueEvent event = event(eventId, equipment.getId(), MaintenanceDueStatus.UPCOMING);
        MaintenanceRegulation regulation = regulation(AutomationAction.TRACK_ONLY);
        regulation.setDefaultResponsibleId(responsibleEmployeeId);
        EquipmentMaintenanceEffectiveRule rule = EquipmentMaintenanceEffectiveRule.fromRegulation(equipment.getId(), regulation);
        when(notificationService.notifyEmployee(
                eq(responsibleEmployeeId),
                eq(MaintenanceAutomationNotificationService.MAINTENANCE_UPCOMING),
                eq("Maintenance upcoming: cycle-1"),
                eq(NotificationSeverity.INFO),
                eq(MaintenanceAutomationNotificationService.ENTITY_TYPE),
                eq(eventId.toString())
        )).thenReturn(Optional.empty());

        int sent = service.notifyEventStatus(event, rule, equipment);

        assertThat(sent).isZero();
    }

    @Test
    void approvalAndWorkOrderNotificationsUseDueEventEntityForDedup() {
        UUID eventId = UUID.randomUUID();
        UUID responsibleEmployeeId = UUID.randomUUID();
        Equipment equipment = equipment(UUID.randomUUID());
        MaintenanceRegulation regulation = regulation(AutomationAction.REQUIRE_APPROVAL);
        regulation.setDefaultResponsibleId(responsibleEmployeeId);
        EquipmentMaintenanceEffectiveRule rule = EquipmentMaintenanceEffectiveRule.fromRegulation(equipment.getId(), regulation);
        MaintenanceDueEvent event = event(eventId, equipment.getId(), MaintenanceDueStatus.DUE);
        when(notificationService.notifyEmployee(
                eq(responsibleEmployeeId),
                eq(MaintenanceAutomationNotificationService.MAINTENANCE_REQUIRES_APPROVAL),
                eq("Maintenance requires approval: cycle-1"),
                eq(NotificationSeverity.WARNING),
                eq(MaintenanceAutomationNotificationService.ENTITY_TYPE),
                eq(eventId.toString())
        )).thenReturn(Optional.of(notification(UUID.randomUUID(), MaintenanceAutomationNotificationService.MAINTENANCE_REQUIRES_APPROVAL, eventId)));
        when(notificationService.notifyEmployee(
                eq(responsibleEmployeeId),
                eq(MaintenanceAutomationNotificationService.MAINTENANCE_WORK_ORDER_CREATED),
                eq("Maintenance work order created: cycle-1"),
                eq(NotificationSeverity.INFO),
                eq(MaintenanceAutomationNotificationService.ENTITY_TYPE),
                eq(eventId.toString())
        )).thenReturn(Optional.of(notification(UUID.randomUUID(), MaintenanceAutomationNotificationService.MAINTENANCE_WORK_ORDER_CREATED, eventId)));

        assertThat(service.notifyRequiresApproval(event, rule, equipment)).isEqualTo(1);
        assertThat(service.notifyWorkOrderCreated(event, rule, equipment)).isEqualTo(1);
    }

    private NotificationDto notification(UUID userId, String title, UUID eventId) {
        return new NotificationDto(
                UUID.randomUUID(),
                userId,
                title,
                "message",
                null,
                null,
                null,
                MaintenanceAutomationNotificationService.ENTITY_TYPE,
                eventId.toString(),
                null
        );
    }

    private MaintenanceDueEvent event(UUID id, UUID equipmentId, MaintenanceDueStatus dueStatus) {
        MaintenanceDueEvent event = new MaintenanceDueEvent();
        ReflectionTestUtils.setField(event, "id", id);
        event.setEquipmentId(equipmentId);
        event.setRegulationId(UUID.randomUUID());
        event.setDueStatus(dueStatus);
        event.setStatus(MaintenanceDueEventStatus.DETECTED);
        event.setTriggerSource(MaintenanceTriggerSource.CALENDAR_JOB);
        event.setCycleKey("cycle-1");
        event.setDueAt(Instant.parse("2026-06-03T00:00:00Z"));
        return event;
    }

    private Equipment equipment(UUID id) {
        Equipment equipment = new Equipment();
        ReflectionTestUtils.setField(equipment, "id", id);
        equipment.setCode("EQ-1");
        equipment.setName("Pump");
        equipment.setDepartmentId(UUID.randomUUID());
        return equipment;
    }

    private MaintenanceRegulation regulation(AutomationAction action) {
        MaintenanceRegulation regulation = new MaintenanceRegulation();
        ReflectionTestUtils.setField(regulation, "id", UUID.randomUUID());
        regulation.setCode("MR-1");
        regulation.setName("Monthly service");
        regulation.setEquipmentTypeId(UUID.randomUUID());
        regulation.setMaintenanceKind(MaintenanceKind.PREVENTIVE);
        regulation.setNormativeLaborHours(2.0);
        regulation.setActive(true);
        regulation.setPeriodicityUnit(PeriodicityUnit.MONTH);
        regulation.setPeriodicityValue(1);
        regulation.setTriggerPolicy(MaintenanceTriggerPolicy.ANY);
        regulation.setAutomationAction(action);
        regulation.setDuplicatePolicy(DuplicatePolicy.ONE_ITEM_PER_CYCLE);
        return regulation;
    }
}
