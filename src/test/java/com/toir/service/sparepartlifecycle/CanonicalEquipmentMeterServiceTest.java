package com.toir.service.sparepartlifecycle;

import com.toir.entity.equipment.EquipmentMeter;
import com.toir.enums.MeterType;
import com.toir.repository.equipment.EquipmentMeterRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CanonicalEquipmentMeterServiceTest {

    @Mock
    EquipmentMeterRepository meterRepository;

    @InjectMocks
    CanonicalEquipmentMeterService service;

    @Test
    void resolvesExplicitActiveMeterWhenIdentityMatches() {
        UUID equipmentId = UUID.randomUUID();
        EquipmentMeter meter = meter(equipmentId, MeterType.ENGINE_HOURS, true, false);
        when(meterRepository.findByIdAndIsDeletedFalse(meter.getId())).thenReturn(Optional.of(meter));

        EquipmentMeter resolved = service.resolve(equipmentId, MeterType.ENGINE_HOURS, meter.getId());

        assertThat(resolved).isSameAs(meter);
        assertThat(service.currentValue(equipmentId, MeterType.ENGINE_HOURS, meter.getId()))
                .isEqualByComparingTo("125.5");
    }

    @Test
    void rejectsExplicitMeterFromAnotherEquipment() {
        UUID requestedEquipmentId = UUID.randomUUID();
        EquipmentMeter meter = meter(UUID.randomUUID(), MeterType.ENGINE_HOURS, true, false);
        when(meterRepository.findByIdAndIsDeletedFalse(meter.getId())).thenReturn(Optional.of(meter));

        assertThatThrownBy(() -> service.resolve(requestedEquipmentId, MeterType.ENGINE_HOURS, meter.getId()))
                .hasMessageStartingWith("METER_MISMATCH:");
    }

    @Test
    void rejectsExplicitMeterWithAnotherType() {
        UUID equipmentId = UUID.randomUUID();
        EquipmentMeter meter = meter(equipmentId, MeterType.MILEAGE_KM, true, false);
        when(meterRepository.findByIdAndIsDeletedFalse(meter.getId())).thenReturn(Optional.of(meter));

        assertThatThrownBy(() -> service.resolve(equipmentId, MeterType.ENGINE_HOURS, meter.getId()))
                .hasMessageStartingWith("METER_MISMATCH:");
    }

    @Test
    void rejectsInactiveExplicitMeter() {
        UUID equipmentId = UUID.randomUUID();
        EquipmentMeter meter = meter(equipmentId, MeterType.ENGINE_HOURS, false, false);
        when(meterRepository.findByIdAndIsDeletedFalse(meter.getId())).thenReturn(Optional.of(meter));

        assertThatThrownBy(() -> service.resolve(equipmentId, MeterType.ENGINE_HOURS, meter.getId()))
                .hasMessageStartingWith("METER_INACTIVE:");
    }

    @Test
    void prefersUniqueActivePrimaryMeter() {
        UUID equipmentId = UUID.randomUUID();
        EquipmentMeter primary = meter(equipmentId, MeterType.ENGINE_HOURS, true, true);
        when(meterRepository.findAllActivePrimary(equipmentId, MeterType.ENGINE_HOURS))
                .thenReturn(List.of(primary));

        assertThat(service.resolve(equipmentId, MeterType.ENGINE_HOURS, null)).isSameAs(primary);
    }

    @Test
    void usesOnlyActiveMeterWhenNoPrimaryExists() {
        UUID equipmentId = UUID.randomUUID();
        EquipmentMeter onlyMeter = meter(equipmentId, MeterType.ENGINE_HOURS, true, false);
        when(meterRepository.findAllActivePrimary(equipmentId, MeterType.ENGINE_HOURS))
                .thenReturn(List.of());
        when(meterRepository.findAllActiveByEquipmentAndType(equipmentId, MeterType.ENGINE_HOURS))
                .thenReturn(List.of(onlyMeter));

        assertThat(service.resolve(equipmentId, MeterType.ENGINE_HOURS, null)).isSameAs(onlyMeter);
    }

    @Test
    void reportsRequiredWhenNoActiveMeterExists() {
        UUID equipmentId = UUID.randomUUID();
        when(meterRepository.findAllActivePrimary(equipmentId, MeterType.ENGINE_HOURS))
                .thenReturn(List.of());
        when(meterRepository.findAllActiveByEquipmentAndType(equipmentId, MeterType.ENGINE_HOURS))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.resolve(equipmentId, MeterType.ENGINE_HOURS, null))
                .hasMessageStartingWith("METER_REQUIRED:");
    }

    @Test
    void reportsAmbiguousWhenSeveralActiveMetersExistWithoutPrimary() {
        UUID equipmentId = UUID.randomUUID();
        EquipmentMeter first = meter(equipmentId, MeterType.ENGINE_HOURS, true, false);
        EquipmentMeter second = meter(equipmentId, MeterType.ENGINE_HOURS, true, false);
        when(meterRepository.findAllActivePrimary(equipmentId, MeterType.ENGINE_HOURS))
                .thenReturn(List.of());
        when(meterRepository.findAllActiveByEquipmentAndType(equipmentId, MeterType.ENGINE_HOURS))
                .thenReturn(List.of(first, second));

        assertThatThrownBy(() -> service.resolve(equipmentId, MeterType.ENGINE_HOURS, null))
                .hasMessageStartingWith("METER_AMBIGUOUS:");
    }

    private static EquipmentMeter meter(UUID equipmentId, MeterType type, boolean active, boolean primary) {
        EquipmentMeter meter = new EquipmentMeter();
        meter.setId(UUID.randomUUID());
        meter.setEquipmentId(equipmentId);
        meter.setMeterType(type);
        meter.setName(type.name());
        meter.setUnit(type == MeterType.MILEAGE_KM ? "km" : "h");
        meter.setCurrentValue(new BigDecimal("125.5").doubleValue());
        meter.setActive(active);
        meter.setPrimary(primary);
        return meter;
    }
}
