package com.toir.service.equipment;

import com.toir.dto.notification.NotificationDto;
import com.toir.entity.equipment.Equipment;
import com.toir.enums.NotificationChannel;
import com.toir.enums.NotificationSeverity;
import com.toir.enums.NotificationStatus;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.security.PermissionConstants;
import com.toir.service.NotificationService;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WarrantyExpiryServiceTest {

    @Mock
    EquipmentRepository equipmentRepository;

    @Mock
    NotificationService notificationService;

    @InjectMocks
    WarrantyExpiryService service;

    @Test
    void notifyExpiringWarrantiesSendsNotificationsFor30And7DayTargets() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate in30 = today.plusDays(30);
        LocalDate in7 = today.plusDays(7);
        UUID departmentId = UUID.randomUUID();

        Equipment equipment30 = equipment("EQ-30", in30, departmentId);
        Equipment equipment7 = equipment("EQ-7", in7, departmentId);

        when(equipmentRepository.findWarrantyExpiringOn(List.of(in30, in7)))
                .thenReturn(List.of(equipment30, equipment7));
        when(notificationService.notifyDepartmentByPermission(
                any(),
                eq(PermissionConstants.EQUIPMENT_READ),
                any(),
                any(),
                eq(NotificationSeverity.INFO),
                eq("WARRANTY_EXPIRY"),
                any()
        )).thenReturn(List.of(notificationDto()));

        int sent = service.notifyExpiringWarranties();

        assertThat(sent).isEqualTo(2);

        ArgumentCaptor<String> titleCaptor = ArgumentCaptor.forClass(String.class);
        verify(notificationService).notifyDepartmentByPermission(
                eq(departmentId),
                eq(PermissionConstants.EQUIPMENT_READ),
                titleCaptor.capture(),
                any(),
                eq(NotificationSeverity.INFO),
                eq("WARRANTY_EXPIRY"),
                eq(equipment30.getId().toString())
        );
        assertThat(titleCaptor.getValue()).contains("EQ-30");
    }

    @Test
    void notifyExpiringWarrantiesUsesResponsibleDepartmentWhenPresent() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate in7 = today.plusDays(7);
        UUID responsibleDepartmentId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();

        Equipment equipment = equipment("EQ-RESP", in7, departmentId);
        equipment.setResponsibleDepartmentId(responsibleDepartmentId);

        when(equipmentRepository.findWarrantyExpiringOn(any()))
                .thenReturn(List.of(equipment));
        when(notificationService.notifyDepartmentByPermission(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
        )).thenReturn(List.of());

        service.notifyExpiringWarranties();

        verify(notificationService).notifyDepartmentByPermission(
                eq(responsibleDepartmentId),
                eq(PermissionConstants.EQUIPMENT_READ),
                any(),
                any(),
                eq(NotificationSeverity.INFO),
                eq("WARRANTY_EXPIRY"),
                eq(equipment.getId().toString())
        );
    }

    @Test
    void notifyExpiringWarrantiesReturnsZeroWhenNoEquipmentFound() {
        when(equipmentRepository.findWarrantyExpiringOn(any())).thenReturn(List.of());

        int sent = service.notifyExpiringWarranties();

        assertThat(sent).isZero();
        verify(notificationService, times(0)).notifyDepartmentByPermission(
                any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void notifyExpiringWarrantiesUsesWarrantyUntilWhenEndDateMissing() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate in7 = today.plusDays(7);
        UUID departmentId = UUID.randomUUID();

        Equipment equipment = equipment("EQ-UNTIL", null, departmentId);
        equipment.setWarrantyUntil(in7);

        when(equipmentRepository.findWarrantyExpiringOn(any())).thenReturn(List.of(equipment));
        when(notificationService.notifyDepartmentByPermission(
                any(), any(), any(), any(), any(), any(), any()
        )).thenReturn(List.of(notificationDto()));

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        int sent = service.notifyExpiringWarranties();

        assertThat(sent).isEqualTo(1);
        verify(notificationService).notifyDepartmentByPermission(
                eq(departmentId),
                eq(PermissionConstants.EQUIPMENT_READ),
                any(),
                bodyCaptor.capture(),
                eq(NotificationSeverity.INFO),
                eq("WARRANTY_EXPIRY"),
                eq(equipment.getId().toString())
        );
        assertThat(bodyCaptor.getValue()).contains("7 kun ichida");
        assertThat(bodyCaptor.getValue()).contains(in7.toString());
    }

    @Test
    void notifyExpiringWarrantiesSkipsEquipmentWithNoEffectiveEndDate() {
        Equipment equipment = equipment("EQ-NO-END", null, UUID.randomUUID());

        when(equipmentRepository.findWarrantyExpiringOn(any())).thenReturn(List.of(equipment));

        int sent = service.notifyExpiringWarranties();

        assertThat(sent).isZero();
        verify(notificationService, times(0)).notifyDepartmentByPermission(
                any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void notifyExpiringWarrantiesContinuesWhenNotificationFails() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Equipment first = equipment("EQ-1", today.plusDays(30), UUID.randomUUID());
        Equipment second = equipment("EQ-2", today.plusDays(7), UUID.randomUUID());

        when(equipmentRepository.findWarrantyExpiringOn(any())).thenReturn(List.of(first, second));
        when(notificationService.notifyDepartmentByPermission(
                eq(first.getDepartmentId()),
                any(), any(), any(), any(), any(), any()
        )).thenThrow(new RuntimeException("delivery failed"));
        when(notificationService.notifyDepartmentByPermission(
                eq(second.getDepartmentId()),
                any(), any(), any(), any(), any(), any()
        )).thenReturn(List.of(notificationDto(), notificationDto()));

        int sent = service.notifyExpiringWarranties();

        assertThat(sent).isEqualTo(2);
        verify(notificationService, times(2)).notifyDepartmentByPermission(
                any(), any(), any(), any(), any(), any(), any());
    }

    private Equipment equipment(String code, LocalDate warrantyEnd, UUID departmentId) {
        Equipment equipment = new Equipment();
        UUID id = UUID.randomUUID();
        ReflectionTestUtils.setField(equipment, "id", id);
        equipment.setCode(code);
        equipment.setName("Pump " + code);
        equipment.setHasWarranty(true);
        equipment.setWarrantyEndDate(warrantyEnd);
        equipment.setDepartmentId(departmentId);
        return equipment;
    }

    private NotificationDto notificationDto() {
        return new NotificationDto(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "title",
                "body",
                NotificationChannel.WEB,
                NotificationStatus.PENDING,
                NotificationSeverity.INFO,
                "WARRANTY_EXPIRY",
                UUID.randomUUID().toString(),
                null
        );
    }
}
