package com.toir.repository.equipment;

import com.toir.entity.equipment.EquipmentMeter;
import com.toir.enums.MeterType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class MeterRepositoryStatsTest {

    @Autowired
    EquipmentMeterRepository repository;

    @Test
    void getMeterStatsWithNoFiltersCountsAllMeters() {
        UUID equipmentId = UUID.randomUUID();
        saveMeter(equipmentId, MeterType.ENGINE_HOURS, true);
        saveMeter(equipmentId, MeterType.ENGINE_HOURS, true);
        saveMeter(equipmentId, MeterType.MILEAGE_KM, false);

        MeterStatsProjection stats = repository.getMeterStats(null, null, null, null);

        assertThat(stats.getTotalMeters()).isEqualTo(3);
        assertThat(stats.getActiveMeters()).isEqualTo(2);
    }

    @Test
    void getMeterStatsWithEquipmentIdFilterCountsOnlyThatEquipment() {
        UUID targetEquipment = UUID.randomUUID();
        UUID otherEquipment = UUID.randomUUID();
        saveMeter(targetEquipment, MeterType.ENGINE_HOURS, true);
        saveMeter(otherEquipment, MeterType.ENGINE_HOURS, true);

        MeterStatsProjection stats = repository.getMeterStats(null, null, targetEquipment, null);

        assertThat(stats.getTotalMeters()).isEqualTo(1);
    }

    @Test
    void getMeterStatsExcludesDeletedMeters() {
        UUID equipmentId = UUID.randomUUID();
        saveMeter(equipmentId, MeterType.ENGINE_HOURS, true);
        EquipmentMeter deleted = saveMeter(equipmentId, MeterType.ENGINE_HOURS, true);
        deleted.setDeleted(true);
        repository.save(deleted);

        MeterStatsProjection stats = repository.getMeterStats(null, null, null, null);

        assertThat(stats.getTotalMeters()).isEqualTo(1);
    }

    @Test
    void getMeterStatsWithMeterTypeFilterCountsOnlyThatType() {
        UUID equipmentId = UUID.randomUUID();
        saveMeter(equipmentId, MeterType.ENGINE_HOURS, true);
        saveMeter(equipmentId, MeterType.MILEAGE_KM, true);

        MeterStatsProjection stats = repository.getMeterStats(null, MeterType.ENGINE_HOURS.name(), null, null);

        assertThat(stats.getTotalMeters()).isEqualTo(1);
        assertThat(stats.getActiveMeters()).isEqualTo(1);
    }

    private EquipmentMeter saveMeter(UUID equipmentId, MeterType meterType, boolean active) {
        EquipmentMeter meter = new EquipmentMeter();
        meter.setEquipmentId(equipmentId);
        meter.setMeterType(meterType);
        meter.setName("Meter-" + UUID.randomUUID());
        meter.setUnit("h");
        meter.setCurrentValue(0);
        meter.setActive(active);
        meter.setDeleted(false);
        return repository.save(meter);
    }
}
