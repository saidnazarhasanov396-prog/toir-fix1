package com.toir.service;

import com.toir.dto.meter.MeterReadingRequest;
import com.toir.entity.equipment.EquipmentMeter;
import com.toir.entity.equipment.MeterReading;
import com.toir.entity.equipment.VehicleDetails;
import com.toir.enums.MeterSource;
import com.toir.enums.MeterType;
import com.toir.repository.MeterReadingRepository;
import com.toir.repository.VehicleDetailsRepository;
import com.toir.repository.equipment.EquipmentMeterRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.users.UserRepository;
import com.toir.service.equipment.EquipmentStatusLifecycleService;
import com.toir.service.maintanance.MaintenanceAutomationService;
import com.toir.service.sparepartlifecycle.SparePartLifecycleEvaluationService;
import com.toir.util.AuditBuilderService;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MeterServiceLifecycleSafetyTest {

    @Mock EquipmentMeterRepository meterRepository;
    @Mock MeterReadingRepository readingRepository;
    @Mock EquipmentRepository equipmentRepository;
    @Mock VehicleDetailsRepository vehicleDetailsRepository;
    @Mock UserRepository userRepository;
    @Mock AuditBuilderService auditBuilderService;
    @Mock EquipmentStatusLifecycleService equipmentStatusLifecycleService;
    @Mock ObjectProvider<MaintenanceAutomationService> maintenanceAutomationServiceProvider;
    @Mock SparePartLifecycleEvaluationService sparePartLifecycleEvaluationService;
    @Mock ForecastService forecastService;

    @InjectMocks MeterService service;

    @Test
    void rejectsReadingOlderThanCanonicalCurrentReading() {
        UUID meterId = UUID.randomUUID();
        EquipmentMeter meter = meter(meterId, UUID.randomUUID(), MeterType.ENGINE_HOURS, 100);
        meter.setLastReadAt(Instant.parse("2026-07-10T08:00:00Z"));
        when(meterRepository.findByIdAndIsDeletedFalse(meterId)).thenReturn(Optional.of(meter));

        MeterReadingRequest request = new MeterReadingRequest(
                meterId,
                110.0,
                Instant.parse("2026-07-10T07:59:59Z"),
                MeterSource.MANUAL,
                null,
                null,
                "late synchronization"
        );

        assertThatThrownBy(() -> service.addReading(request))
                .hasMessageStartingWith("METER_READING_OUT_OF_ORDER:");
        verify(readingRepository, never()).save(any());
        verify(meterRepository, never()).save(any());
    }

    @Test
    void deletingLatestReadingRebuildsMeterAndVehicleProjectionFromRemainingReading() {
        UUID equipmentId = UUID.randomUUID();
        UUID meterId = UUID.randomUUID();
        Instant previousReadAt = Instant.parse("2026-07-09T08:00:00Z");
        Instant latestReadAt = Instant.parse("2026-07-10T08:00:00Z");
        EquipmentMeter meter = meter(meterId, equipmentId, MeterType.MILEAGE_KM, 150);
        meter.setLastReadAt(latestReadAt);
        MeterReading latest = reading(UUID.randomUUID(), meterId, equipmentId, 150, 50, latestReadAt);
        MeterReading previous = reading(UUID.randomUUID(), meterId, equipmentId, 100, 25, previousReadAt);
        VehicleDetails vehicle = new VehicleDetails();
        vehicle.setId(UUID.randomUUID());
        vehicle.setEquipmentId(equipmentId);
        vehicle.setCurrentOdometerKm(150);

        when(readingRepository.findByIdAndIsDeletedFalse(latest.getId())).thenReturn(Optional.of(latest));
        when(readingRepository.save(any(MeterReading.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(meterRepository.findByIdAndIsDeletedFalse(meterId)).thenReturn(Optional.of(meter));
        when(readingRepository.findTopByMeterIdAndIsDeletedFalseOrderByReadAtDesc(meterId))
                .thenReturn(Optional.of(previous));
        when(meterRepository.save(any(EquipmentMeter.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(vehicleDetailsRepository.findByEquipmentIdAndIsDeletedFalse(equipmentId))
                .thenReturn(Optional.of(vehicle));
        when(vehicleDetailsRepository.save(any(VehicleDetails.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.deleteReading(latest.getId());

        assertThat(meter.getCurrentValue()).isEqualTo(100);
        assertThat(meter.getLastReadAt()).isEqualTo(previousReadAt);
        assertThat(vehicle.getCurrentOdometerKm()).isEqualTo(100);
        verify(meterRepository).save(meter);
        verify(vehicleDetailsRepository).save(vehicle);
        verify(sparePartLifecycleEvaluationService).reevaluateForMeter(any(UUID.class), any(Instant.class));
    }

    private static EquipmentMeter meter(UUID meterId, UUID equipmentId, MeterType type, double currentValue) {
        EquipmentMeter meter = new EquipmentMeter();
        meter.setId(meterId);
        meter.setEquipmentId(equipmentId);
        meter.setMeterType(type);
        meter.setName(type.name());
        meter.setUnit(type == MeterType.MILEAGE_KM ? "km" : "h");
        meter.setCurrentValue(currentValue);
        meter.setActive(true);
        return meter;
    }

    private static MeterReading reading(UUID id,
                                        UUID meterId,
                                        UUID equipmentId,
                                        double value,
                                        double delta,
                                        Instant readAt) {
        MeterReading reading = new MeterReading();
        reading.setId(id);
        reading.setMeterId(meterId);
        reading.setEquipmentId(equipmentId);
        reading.setValue(value);
        reading.setDelta(delta);
        reading.setReadAt(readAt);
        reading.setSource(MeterSource.MANUAL);
        return reading;
    }
}
