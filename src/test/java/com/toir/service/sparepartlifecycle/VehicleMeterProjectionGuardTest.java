package com.toir.service.sparepartlifecycle;

import com.toir.entity.equipment.EquipmentMeter;
import com.toir.entity.equipment.VehicleDetails;
import com.toir.enums.MeterType;
import com.toir.repository.equipment.EquipmentMeterRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VehicleMeterProjectionGuardTest {

    @Mock
    EquipmentMeterRepository meterRepository;

    @InjectMocks
    VehicleMeterProjectionGuard guard;

    @Test
    void rejectsDirectOdometerChangeWhenAnActiveCanonicalMeterExists() {
        UUID equipmentId = UUID.randomUUID();
        VehicleDetails details = vehicle(equipmentId, 1_000, 25);
        when(meterRepository.findAllActiveByEquipmentAndType(equipmentId, MeterType.MILEAGE_KM))
                .thenReturn(List.of(meter(equipmentId, MeterType.MILEAGE_KM)));

        assertThatThrownBy(() -> guard.assertCompatibleUpdate(equipmentId, details, 1_001.0, 25.0))
                .hasMessageStartingWith("METER_PROJECTION_READ_ONLY:");
    }

    @Test
    void allowsUnchangedProjectionWhenAnActiveMeterExists() {
        UUID equipmentId = UUID.randomUUID();
        VehicleDetails details = vehicle(equipmentId, 1_000, 25);

        assertThatCode(() -> guard.assertCompatibleUpdate(equipmentId, details, 1_000.0, 25.0))
                .doesNotThrowAnyException();
    }

    @Test
    void allowsCompatibilityUpdateWhenNoAuthoritativeMeterExists() {
        UUID equipmentId = UUID.randomUUID();
        VehicleDetails details = vehicle(equipmentId, 1_000, 25);
        when(meterRepository.findAllActiveByEquipmentAndType(equipmentId, MeterType.ENGINE_HOURS))
                .thenReturn(List.of());

        assertThatCode(() -> guard.assertCompatibleUpdate(equipmentId, details, 1_000.0, 30.0))
                .doesNotThrowAnyException();
    }

    private static VehicleDetails vehicle(UUID equipmentId, double odometer, double engineHours) {
        VehicleDetails details = new VehicleDetails();
        details.setEquipmentId(equipmentId);
        details.setCurrentOdometerKm(odometer);
        details.setCurrentEngineHours(engineHours);
        return details;
    }

    private static EquipmentMeter meter(UUID equipmentId, MeterType type) {
        EquipmentMeter meter = new EquipmentMeter();
        meter.setId(UUID.randomUUID());
        meter.setEquipmentId(equipmentId);
        meter.setMeterType(type);
        meter.setActive(true);
        return meter;
    }
}
