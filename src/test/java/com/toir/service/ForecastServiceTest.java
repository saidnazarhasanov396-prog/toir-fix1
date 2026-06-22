package com.toir.service;

import com.toir.entity.equipment.Equipment;
import com.toir.entity.equipment.EquipmentDailyUsage;
import com.toir.repository.equipment.EquipmentDailyUsageRepository;
import com.toir.repository.equipment.EquipmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ForecastServiceTest {

    @Mock
    EquipmentDailyUsageRepository dailyUsageRepository;

    @Mock
    EquipmentRepository equipmentRepository;

    @InjectMocks
    ForecastService service;

    @Test
    void recalculateWhenEquipmentNotFoundDoesNotSave() {
        UUID equipmentId = UUID.randomUUID();
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.empty());

        service.recalculate(equipmentId);

        verify(equipmentRepository, never()).save(any());
    }

    @Test
    void recalculateWithNoDailyUsageSetsConsumedToZeroAndLeavesAveragesNull() {
        UUID equipmentId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, 10_000.0);
        LocalDate today = LocalDate.now(ZoneId.systemDefault());

        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(dailyUsageRepository.findAllByEquipmentIdAndUsageDateBetweenAndIsDeletedFalse(
                eq(equipmentId), any(LocalDate.class), eq(today))).thenReturn(List.of());
        when(dailyUsageRepository.sumAllTimeUsage(equipmentId)).thenReturn(0.0);

        service.recalculate(equipmentId);

        ArgumentCaptor<Equipment> captor = ArgumentCaptor.forClass(Equipment.class);
        verify(equipmentRepository).save(captor.capture());
        Equipment saved = captor.getValue();
        assertThat(saved.getForecastConsumedResource()).isEqualTo(0.0);
        assertThat(saved.getForecastAvgUsagePerActiveDay()).isNull();
        assertThat(saved.getForecastRemainingActiveDays()).isNull();
        assertThat(saved.getForecastRemainingResource()).isEqualTo(10_000.0);
        assertThat(saved.getForecastCalculatedAt()).isEqualTo(today);
    }

    @Test
    void recalculateComputesAverageOverActiveDaysInWindow() {
        UUID equipmentId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, 1_000.0);
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        LocalDate yesterday = today.minusDays(1);
        LocalDate twoDaysAgo = today.minusDays(2);

        EquipmentDailyUsage active1 = usage(equipmentId, yesterday, 10.0);
        EquipmentDailyUsage active2 = usage(equipmentId, twoDaysAgo, 20.0);
        EquipmentDailyUsage inactive = usage(equipmentId, today.minusDays(3), 0.0);

        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(dailyUsageRepository.findAllByEquipmentIdAndUsageDateBetweenAndIsDeletedFalse(
                equipmentId, today.minusDays(30), today)).thenReturn(List.of(active1, active2, inactive));
        when(dailyUsageRepository.sumAllTimeUsage(equipmentId)).thenReturn(100.0);

        service.recalculate(equipmentId);

        ArgumentCaptor<Equipment> captor = ArgumentCaptor.forClass(Equipment.class);
        verify(equipmentRepository).save(captor.capture());
        Equipment saved = captor.getValue();
        assertThat(saved.getForecastConsumedResource()).isEqualTo(100.0);
        assertThat(saved.getForecastAvgUsagePerActiveDay()).isEqualTo(15.0);
        assertThat(saved.getForecastRemainingResource()).isEqualTo(900.0);
        assertThat(saved.getForecastRemainingActiveDays()).isEqualTo(60L);
    }

    @Test
    void recalculateWithNullLifetimeLimitLeavesRemainingFieldsNull() {
        UUID equipmentId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, null);
        LocalDate today = LocalDate.now(ZoneId.systemDefault());

        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(dailyUsageRepository.findAllByEquipmentIdAndUsageDateBetweenAndIsDeletedFalse(
                equipmentId, today.minusDays(30), today)).thenReturn(List.of());
        when(dailyUsageRepository.sumAllTimeUsage(equipmentId)).thenReturn(50.0);

        service.recalculate(equipmentId);

        ArgumentCaptor<Equipment> captor = ArgumentCaptor.forClass(Equipment.class);
        verify(equipmentRepository).save(captor.capture());
        Equipment saved = captor.getValue();
        assertThat(saved.getForecastConsumedResource()).isEqualTo(50.0);
        assertThat(saved.getForecastRemainingResource()).isNull();
        assertThat(saved.getForecastRemainingActiveDays()).isNull();
    }

    @Test
    void recalculateComputesRemainingActiveDaysViaMathRound() {
        UUID equipmentId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, 1_000.0);
        LocalDate today = LocalDate.now(ZoneId.systemDefault());

        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(dailyUsageRepository.findAllByEquipmentIdAndUsageDateBetweenAndIsDeletedFalse(
                equipmentId, today.minusDays(30), today))
                .thenReturn(List.of(usage(equipmentId, today, 7.0)));
        when(dailyUsageRepository.sumAllTimeUsage(equipmentId)).thenReturn(300.0);

        service.recalculate(equipmentId);

        ArgumentCaptor<Equipment> captor = ArgumentCaptor.forClass(Equipment.class);
        verify(equipmentRepository).save(captor.capture());
        Equipment saved = captor.getValue();
        assertThat(saved.getForecastRemainingResource()).isEqualTo(700.0);
        assertThat(saved.getForecastAvgUsagePerActiveDay()).isEqualTo(7.0);
        assertThat(saved.getForecastRemainingActiveDays()).isEqualTo(100L);
    }

    @Test
    void recordDailyUsageCreatesNewRowWhenNoneExists() {
        UUID equipmentId = UUID.randomUUID();
        LocalDate usageDate = LocalDate.of(2026, 6, 10);

        when(dailyUsageRepository.findByEquipmentIdAndUsageDateAndIsDeletedFalse(equipmentId, usageDate))
                .thenReturn(Optional.empty());
        when(dailyUsageRepository.save(any(EquipmentDailyUsage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.recordDailyUsage(equipmentId, usageDate, 12.5);

        ArgumentCaptor<EquipmentDailyUsage> captor = ArgumentCaptor.forClass(EquipmentDailyUsage.class);
        verify(dailyUsageRepository).save(captor.capture());
        EquipmentDailyUsage saved = captor.getValue();
        assertThat(saved.getEquipmentId()).isEqualTo(equipmentId);
        assertThat(saved.getUsageDate()).isEqualTo(usageDate);
        assertThat(saved.getUsageValue()).isEqualTo(12.5);
    }

    @Test
    void recordDailyUsageAddsDeltaToExistingRow() {
        UUID equipmentId = UUID.randomUUID();
        LocalDate usageDate = LocalDate.of(2026, 6, 10);
        EquipmentDailyUsage existing = usage(equipmentId, usageDate, 5.0);

        when(dailyUsageRepository.findByEquipmentIdAndUsageDateAndIsDeletedFalse(equipmentId, usageDate))
                .thenReturn(Optional.of(existing));
        when(dailyUsageRepository.save(existing)).thenReturn(existing);

        service.recordDailyUsage(equipmentId, usageDate, 3.0);

        assertThat(existing.getUsageValue()).isEqualTo(8.0);
        verify(dailyUsageRepository).save(existing);
    }

    @Test
    void recordDailyUsageWithZeroDeltaIsNoOp() {
        UUID equipmentId = UUID.randomUUID();
        LocalDate usageDate = LocalDate.of(2026, 6, 10);

        service.recordDailyUsage(equipmentId, usageDate, 0.0);

        verify(dailyUsageRepository, never()).save(any());
        verify(dailyUsageRepository, never()).findByEquipmentIdAndUsageDateAndIsDeletedFalse(any(), any());
    }

    @Test
    void reverseDailyUsageSubtractsDeltaFromExistingRow() {
        UUID equipmentId = UUID.randomUUID();
        LocalDate usageDate = LocalDate.of(2026, 6, 10);
        EquipmentDailyUsage existing = usage(equipmentId, usageDate, 15.0);

        when(dailyUsageRepository.findByEquipmentIdAndUsageDateAndIsDeletedFalse(equipmentId, usageDate))
                .thenReturn(Optional.of(existing));
        when(dailyUsageRepository.save(existing)).thenReturn(existing);

        service.reverseDailyUsage(equipmentId, usageDate, 5.0);

        assertThat(existing.getUsageValue()).isEqualTo(10.0);
        verify(dailyUsageRepository).save(existing);
    }

    @Test
    void reverseDailyUsageWhenNoRowExistsDoesNotSave() {
        UUID equipmentId = UUID.randomUUID();
        LocalDate usageDate = LocalDate.of(2026, 6, 10);

        when(dailyUsageRepository.findByEquipmentIdAndUsageDateAndIsDeletedFalse(equipmentId, usageDate))
                .thenReturn(Optional.empty());

        service.reverseDailyUsage(equipmentId, usageDate, 5.0);

        verify(dailyUsageRepository, never()).save(any());
    }

    private Equipment equipment(UUID id, Double lifetimeLimitValue) {
        Equipment equipment = new Equipment();
        equipment.setId(id);
        equipment.setLifetimeLimitValue(lifetimeLimitValue);
        return equipment;
    }

    private EquipmentDailyUsage usage(UUID equipmentId, LocalDate usageDate, double usageValue) {
        EquipmentDailyUsage usage = new EquipmentDailyUsage();
        usage.setEquipmentId(equipmentId);
        usage.setUsageDate(usageDate);
        usage.setUsageValue(usageValue);
        return usage;
    }
}
