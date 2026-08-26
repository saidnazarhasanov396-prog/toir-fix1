package com.toir.telemetry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.toir.entity.equipment.EquipmentMeter;
import com.toir.enums.MeterType;
import com.toir.repository.equipment.EquipmentMeterRepository;
import com.toir.repository.equipment.EquipmentRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TelemetryMeterResolverTest {

    private static final UUID EQUIPMENT_ID = UUID.fromString("0301b754-f675-4cf2-96a2-8a84fc11ebd5");
    private static final UUID METER_ID = UUID.fromString("f67c1f29-1d51-4c57-b4f7-520209f29a20");

    @Mock
    private EquipmentRepository equipmentRepository;
    @Mock
    private EquipmentMeterRepository meterRepository;
    @InjectMocks
    private TelemetryMeterResolver resolver;

    @Test
    void resolvesByMeterUuidBeforeNameAndAlias() {
        EquipmentMeter meter = meter(METER_ID, EQUIPMENT_ID, "Operating hours", MeterType.ENGINE_HOURS, "h");
        when(equipmentRepository.existsByIdAndIsDeletedFalse(EQUIPMENT_ID)).thenReturn(true);
        when(meterRepository.findAllByEquipmentIdAndActiveTrueAndIsDeletedFalse(EQUIPMENT_ID))
                .thenReturn(List.of(meter));

        assertThat(resolver.resolve(EQUIPMENT_ID.toString(), METER_ID.toString(), "h"))
                .containsSame(meter);
    }

    @Test
    void resolvesNormalizedNameAndStandardAliasOnlyWhenUnique() {
        EquipmentMeter meter = meter(METER_ID, EQUIPMENT_ID, "Engine Hours", MeterType.ENGINE_HOURS, "h");
        when(equipmentRepository.existsByIdAndIsDeletedFalse(EQUIPMENT_ID)).thenReturn(true);
        when(meterRepository.findAllByEquipmentIdAndActiveTrueAndIsDeletedFalse(EQUIPMENT_ID)).thenReturn(List.of(meter));

        assertThat(resolver.resolve(EQUIPMENT_ID.toString(), "engine-hours", "h")).containsSame(meter);
        assertThat(resolver.resolve(EQUIPMENT_ID.toString(), "moto_hours", "h")).containsSame(meter);
        assertThat(resolver.resolve(EQUIPMENT_ID.toString(), "engine_hours", "hour")).containsSame(meter);
    }

    @Test
    void rejectsUnknownEquipmentAmbiguousAliasAndUnitMismatch() {
        EquipmentMeter first = meter(METER_ID, EQUIPMENT_ID, "Hours A", MeterType.ENGINE_HOURS, "h");
        EquipmentMeter second = meter(UUID.randomUUID(), EQUIPMENT_ID, "Hours B", MeterType.ENGINE_HOURS, "h");
        when(equipmentRepository.existsByIdAndIsDeletedFalse(EQUIPMENT_ID)).thenReturn(true);
        when(meterRepository.findAllByEquipmentIdAndActiveTrueAndIsDeletedFalse(EQUIPMENT_ID))
                .thenReturn(List.of(first, second));

        assertThat(resolver.resolve(EQUIPMENT_ID.toString(), "engine_hours", "h")).isEmpty();
        assertThat(resolver.resolve(EQUIPMENT_ID.toString(), METER_ID.toString(), "km")).isEmpty();
        assertThat(resolver.resolve("not-a-uuid", "engine_hours", "h")).isEmpty();
    }

    private static EquipmentMeter meter(UUID id, UUID equipmentId, String name, MeterType meterType, String unit) {
        EquipmentMeter meter = new EquipmentMeter();
        meter.setId(id);
        meter.setEquipmentId(equipmentId);
        meter.setName(name);
        meter.setMeterType(meterType);
        meter.setUnit(unit);
        meter.setActive(true);
        return meter;
    }
}
