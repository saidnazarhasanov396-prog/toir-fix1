package com.toir.repository.equipment;

import com.toir.test.RepositorySliceTest;
import com.toir.entity.equipment.EquipmentMeter;
import com.toir.enums.MeterType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@RepositorySliceTest
@Sql(statements = """
        CREATE TABLE IF NOT EXISTS meter_readings (
            id uuid NOT NULL PRIMARY KEY,
            created_at timestamp NOT NULL,
            updated_at timestamp NOT NULL,
            is_deleted boolean DEFAULT false NOT NULL,
            meter_id uuid NOT NULL,
            equipment_id uuid NOT NULL,
            "value" double precision NOT NULL,
            delta double precision,
            read_at timestamp NOT NULL,
            source varchar(255) NOT NULL,
            recorded_by_user_id uuid,
            repair_request_id uuid,
            work_order_id uuid,
            defect_id uuid,
            reading_context varchar(255) DEFAULT 'MANUAL_UPDATE' NOT NULL,
            device_id varchar(255),
            note text
        )
        """)
class MeterRepositoryStatsTest {

    @Autowired
    EquipmentMeterRepository repository;

    @Autowired
    JdbcTemplate jdbcTemplate;

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

    @Test
    void getMeterStatsCountsReadingsForFilteredMeters() {
        UUID targetEquipment = UUID.randomUUID();
        UUID otherEquipment = UUID.randomUUID();
        EquipmentMeter target = saveMeter(targetEquipment, MeterType.ENGINE_HOURS, true);
        EquipmentMeter other = saveMeter(otherEquipment, MeterType.ENGINE_HOURS, true);
        saveReading(target.getId(), targetEquipment, false);
        saveReading(target.getId(), targetEquipment, false);
        saveReading(target.getId(), targetEquipment, true);
        saveReading(other.getId(), otherEquipment, false);

        MeterStatsProjection stats = repository.getMeterStats(null, null, targetEquipment, null);

        assertThat(stats.getTotalMeters()).isEqualTo(1);
        assertThat(stats.getTotalReadings()).isEqualTo(2);
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

    private void saveReading(UUID meterId, UUID equipmentId, boolean deleted) {
        Instant now = Instant.parse("2026-06-01T10:00:00Z");
        jdbcTemplate.update("""
                INSERT INTO meter_readings (
                    id, created_at, updated_at, is_deleted, meter_id, equipment_id,
                    "value", read_at, source, reading_context
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                now,
                now,
                deleted,
                meterId,
                equipmentId,
                100.0,
                now,
                "MANUAL",
                "MANUAL_UPDATE"
        );
    }
}
