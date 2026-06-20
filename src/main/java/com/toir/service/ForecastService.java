package com.toir.service;

import com.toir.entity.equipment.Equipment;
import com.toir.entity.equipment.EquipmentDailyUsage;
import com.toir.repository.equipment.EquipmentDailyUsageRepository;
import com.toir.repository.equipment.EquipmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ForecastService {

    private static final int WINDOW_DAYS = 30;

    private final EquipmentDailyUsageRepository dailyUsageRepository;
    private final EquipmentRepository equipmentRepository;

    @Transactional
    public void recalculate(UUID equipmentId) {
        Equipment equipment = equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)
                .orElse(null);
        if (equipment == null) {
            log.warn("forecast_recalculate_equipment_not_found equipmentId={}", equipmentId);
            return;
        }

        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        LocalDate windowStart = today.minusDays(WINDOW_DAYS);

        List<EquipmentDailyUsage> recentUsage =
                dailyUsageRepository.findAllByEquipmentIdAndUsageDateBetweenAndIsDeletedFalse(
                        equipmentId, windowStart, today);

        List<EquipmentDailyUsage> activeDays = recentUsage.stream()
                .filter(u -> u.getUsageValue() != null && u.getUsageValue() > 0)
                .toList();

        double consumedResource = dailyUsageRepository.sumAllTimeUsage(equipmentId);

        Double avgUsagePerActiveDay = null;
        if (!activeDays.isEmpty()) {
            double totalUsage = activeDays.stream()
                    .mapToDouble(EquipmentDailyUsage::getUsageValue)
                    .sum();
            avgUsagePerActiveDay = totalUsage / activeDays.size();
        }

        Double resourceLimit = equipment.getLifetimeLimitValue();
        Double remainingResource = resourceLimit != null
                ? resourceLimit - consumedResource
                : null;

        Long remainingActiveDays = null;
        if (remainingResource != null && avgUsagePerActiveDay != null && avgUsagePerActiveDay > 0) {
            remainingActiveDays = Math.round(remainingResource / avgUsagePerActiveDay);
        }

        equipment.setForecastConsumedResource(consumedResource);
        equipment.setForecastRemainingResource(remainingResource);
        equipment.setForecastAvgUsagePerActiveDay(avgUsagePerActiveDay);
        equipment.setForecastRemainingActiveDays(remainingActiveDays);
        equipment.setForecastCalculatedAt(today);

        equipmentRepository.save(equipment);
    }

    @Transactional
    public void recordDailyUsage(UUID equipmentId, LocalDate usageDate, double delta) {
        if (delta == 0) {
            return;
        }
        EquipmentDailyUsage existing = dailyUsageRepository
                .findByEquipmentIdAndUsageDateAndIsDeletedFalse(equipmentId, usageDate)
                .orElse(null);

        if (existing != null) {
            existing.setUsageValue(existing.getUsageValue() + delta);
            dailyUsageRepository.save(existing);
        } else {
            EquipmentDailyUsage newUsage = EquipmentDailyUsage.builder()
                    .equipmentId(equipmentId)
                    .usageDate(usageDate)
                    .usageValue(delta)
                    .build();
            dailyUsageRepository.save(newUsage);
        }
    }

    @Transactional
    public void reverseDailyUsage(UUID equipmentId, LocalDate usageDate, double delta) {
        if (delta == 0) {
            return;
        }
        EquipmentDailyUsage existing = dailyUsageRepository
                .findByEquipmentIdAndUsageDateAndIsDeletedFalse(equipmentId, usageDate)
                .orElse(null);

        if (existing == null) {
            log.warn("forecast_reverse_daily_usage_record_not_found equipmentId={} date={}",
                    equipmentId, usageDate);
            return;
        }
        existing.setUsageValue(existing.getUsageValue() - delta);
        dailyUsageRepository.save(existing);
    }
}
