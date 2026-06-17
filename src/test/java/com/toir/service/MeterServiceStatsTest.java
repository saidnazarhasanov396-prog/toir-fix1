package com.toir.service;

import com.toir.dto.meter.MeterReadingRequest;
import com.toir.dto.meter.MeterStatsResponse;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.equipment.EquipmentMeter;
import com.toir.entity.equipment.MeterReading;
import com.toir.entity.equipment.VehicleDetails;
import com.toir.enums.MaintenanceTriggerSource;
import com.toir.enums.MeterReadingContext;
import com.toir.enums.MeterSource;
import com.toir.enums.MeterType;
import com.toir.repository.MeterReadingRepository;
import com.toir.repository.VehicleDetailsRepository;
import com.toir.repository.equipment.EquipmentMeterRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.equipment.MeterStatsProjection;
import com.toir.repository.users.UserRepository;
import com.toir.service.equipment.EquipmentStatusLifecycleService;
import com.toir.service.maintanance.MaintenanceAutomationService;
import com.toir.util.AuditBuilderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MeterServiceStatsTest {

    @Mock
    EquipmentMeterRepository meterRepository;
    @Mock
    MeterReadingRepository readingRepository;
    @Mock
    EquipmentRepository equipmentRepository;
    @Mock
    VehicleDetailsRepository vehicleDetailsRepository;
    @Mock
    UserRepository userRepository;
    @Mock
    AuditBuilderService auditBuilderService;
    @Mock
    EquipmentStatusLifecycleService equipmentStatusLifecycleService;
    @Mock
    ObjectProvider<MaintenanceAutomationService> maintenanceAutomationServiceProvider;
    @Mock
    MaintenanceAutomationService maintenanceAutomationService;

    @InjectMocks
    MeterService service;

    @Test
    void getStatsWithNoFiltersReturnsMappedResponse() {
        MeterStatsProjection projection = mockProjection(20L, 15L, 200L, 3L);
        when(meterRepository.getMeterStats(isNull(), isNull(), isNull(), isNull())).thenReturn(projection);

        MeterStatsResponse stats = service.getStats(null, null, null, null);

        assertThat(stats.totalMeters()).isEqualTo(20);
        assertThat(stats.activeMeters()).isEqualTo(15);
        assertThat(stats.totalReadings()).isEqualTo(200);
        assertThat(stats.dueTriggers()).isEqualTo(3);
        verify(meterRepository).getMeterStats(null, null, null, null);
    }

    @Test
    void getStatsWithMeterTypePassesStringName() {
        MeterStatsProjection projection = mockProjection(5L, 5L, 50L, 0L);
        when(meterRepository.getMeterStats(isNull(), eq("ENGINE_HOURS"), isNull(), isNull())).thenReturn(projection);

        service.getStats(null, MeterType.ENGINE_HOURS, null, null);

        verify(meterRepository).getMeterStats(null, "ENGINE_HOURS", null, null);
    }

    @Test
    void getStatsWithEquipmentIdPassesIdToRepository() {
        UUID equipmentId = UUID.randomUUID();
        MeterStatsProjection projection = mockProjection(2L, 2L, 10L, 0L);
        when(meterRepository.getMeterStats(isNull(), isNull(), eq(equipmentId), isNull())).thenReturn(projection);

        service.getStats(null, null, equipmentId, null);

        verify(meterRepository).getMeterStats(null, null, equipmentId, null);
    }

    @Test
    void getStatsWithNullMeterTypePassesNullString() {
        MeterStatsProjection projection = mockProjection(10L, 10L, 0L, 0L);
        when(meterRepository.getMeterStats(isNull(), isNull(), isNull(), isNull())).thenReturn(projection);

        service.getStats(null, null, null, null);

        verify(meterRepository).getMeterStats(null, null, null, null);
    }

    @Test
    void getStatsHandlesNullProjectionValuesGracefully() {
        MeterStatsProjection projection = mockProjection(null, null, null, null);
        when(meterRepository.getMeterStats(any(), any(), any(), any())).thenReturn(projection);

        MeterStatsResponse stats = service.getStats(null, null, null, null);

        assertThat(stats.totalMeters()).isZero();
        assertThat(stats.activeMeters()).isZero();
        assertThat(stats.totalReadings()).isZero();
        assertThat(stats.dueTriggers()).isZero();
    }

    @Test
    void listReadingsDefaultsToCreatedAtDescAndMapsPage() {
        MeterReading reading = reading(UUID.randomUUID(), "shift reading");
        when(readingRepository.searchReadings(null, "createdAt", "desc", PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(reading), PageRequest.of(0, 20), 1));

        var result = service.listReadings(null, null, null, 0, 20);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().getFirst().id()).isEqualTo(reading.getId());
        assertThat(result.getContent().getFirst().createdAt()).isEqualTo(reading.getCreatedAt());
        verify(readingRepository).searchReadings(null, "createdAt", "desc", PageRequest.of(0, 20));
    }

    @Test
    void listReadingsTrimsSearchAndPassesSortDirection() {
        when(readingRepository.searchReadings("shift", "readAt", "asc", PageRequest.of(2, 5)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(2, 5), 0));

        service.listReadings(" shift ", "readAt", "asc", 2, 5);

        verify(readingRepository).searchReadings("shift", "readAt", "asc", PageRequest.of(2, 5));
    }

    @Test
    void listReadingsAcceptsCombinedSortDirection() {
        when(readingRepository.searchReadings(null, "createdAt", "desc", PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        service.listReadings(null, "createdAt,desc", "asc", 0, 20);

        verify(readingRepository).searchReadings(null, "createdAt", "desc", PageRequest.of(0, 20));
    }

    @Test
    void addReadingKeepsMeterUpdateWhenMaintenanceAutomationFails() {
        UUID meterId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        Instant readAt = Instant.parse("2026-06-04T09:15:00Z");
        EquipmentMeter meter = new EquipmentMeter();
        meter.setId(meterId);
        meter.setEquipmentId(equipmentId);
        meter.setMeterType(MeterType.ENGINE_HOURS);
        meter.setName("Engine hours");
        meter.setUnit("h");
        meter.setCurrentValue(100.0);
        meter.setActive(true);
        Equipment equipment = new Equipment();
        equipment.setId(equipmentId);
        equipment.setName("Pump");
        when(meterRepository.findByIdAndIsDeletedFalse(meterId)).thenReturn(java.util.Optional.of(meter));
        when(readingRepository.save(any(MeterReading.class))).thenAnswer(invocation -> {
            MeterReading saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
        when(meterRepository.save(any(EquipmentMeter.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(java.util.Optional.of(equipment));
        doThrow(new IllegalStateException("automation failed"))
                .when(maintenanceAutomationService)
                .evaluateEquipment(equipmentId, MaintenanceTriggerSource.METER_READING);
        when(maintenanceAutomationServiceProvider.getIfAvailable()).thenReturn(maintenanceAutomationService);

        var result = service.addReading(new MeterReadingRequest(
                meterId,
                125.0,
                readAt,
                MeterSource.MANUAL,
                null,
                "tablet-1",
                "shift reading"
        ));

        assertThat(result.value()).isEqualTo(125.0);
        assertThat(result.delta()).isEqualTo(25.0);
        assertThat(meter.getCurrentValue()).isEqualTo(125.0);
        assertThat(meter.getLastReadAt()).isEqualTo(readAt);
        verify(meterRepository).save(meter);
        verify(maintenanceAutomationService).evaluateEquipment(equipmentId, MaintenanceTriggerSource.METER_READING);
    }

    @Test
    void addReadingWithRepairRequestContextStoresContextAndSyncsVehicleOdometer() {
        UUID meterId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        UUID repairRequestId = UUID.randomUUID();
        Instant readAt = Instant.parse("2026-06-15T06:30:00Z");

        EquipmentMeter meter = new EquipmentMeter();
        meter.setId(meterId);
        meter.setEquipmentId(equipmentId);
        meter.setMeterType(MeterType.MILEAGE_KM);
        meter.setName("Odometer");
        meter.setUnit("km");
        meter.setCurrentValue(9_000.0);
        meter.setActive(true);

        VehicleDetails vehicleDetails = new VehicleDetails();
        vehicleDetails.setId(UUID.randomUUID());
        vehicleDetails.setEquipmentId(equipmentId);
        vehicleDetails.setCurrentOdometerKm(9_000.0);
        vehicleDetails.setCurrentEngineHours(250.0);

        Equipment equipment = new Equipment();
        equipment.setId(equipmentId);
        equipment.setName("Truck");

        when(meterRepository.findByIdAndIsDeletedFalse(meterId)).thenReturn(java.util.Optional.of(meter));
        when(vehicleDetailsRepository.findByEquipmentIdAndIsDeletedFalse(equipmentId))
                .thenReturn(java.util.Optional.of(vehicleDetails));
        when(vehicleDetailsRepository.save(any(VehicleDetails.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(readingRepository.save(any(MeterReading.class))).thenAnswer(invocation -> {
            MeterReading saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
        when(meterRepository.save(any(EquipmentMeter.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(java.util.Optional.of(equipment));

        var result = service.addReading(
                new MeterReadingRequest(
                        meterId,
                        10_000.0,
                        readAt,
                        MeterSource.MANUAL,
                        null,
                        "tablet-1",
                        "breakdown intake"
                ),
                MeterReadingContext.FAILURE_DETECTED,
                repairRequestId,
                null,
                null
        );

        assertThat(result.value()).isEqualTo(10_000.0);
        assertThat(result.repairRequestId()).isEqualTo(repairRequestId);
        assertThat(result.readingContext()).isEqualTo(MeterReadingContext.FAILURE_DETECTED);
        assertThat(vehicleDetails.getCurrentOdometerKm()).isEqualTo(10_000.0);
        verify(vehicleDetailsRepository).save(vehicleDetails);
        verify(readingRepository).save(org.mockito.ArgumentMatchers.argThat(reading ->
                repairRequestId.equals(reading.getRepairRequestId())
                        && reading.getReadingContext() == MeterReadingContext.FAILURE_DETECTED
                        && reading.getWorkOrderId() == null
                        && reading.getDefectId() == null
        ));
    }

    private MeterStatsProjection mockProjection(Long total, Long active, Long readings, Long due) {
        MeterStatsProjection p = mock(MeterStatsProjection.class);
        when(p.getTotalMeters()).thenReturn(total);
        when(p.getActiveMeters()).thenReturn(active);
        when(p.getTotalReadings()).thenReturn(readings);
        when(p.getDueTriggers()).thenReturn(due);
        return p;
    }

    private MeterReading reading(UUID id, String note) {
        MeterReading reading = new MeterReading();
        reading.setId(id);
        reading.setMeterId(null);
        reading.setEquipmentId(null);
        reading.setValue(125.5);
        reading.setDelta(5.5);
        reading.setReadAt(Instant.parse("2026-05-31T07:55:00Z"));
        reading.setSource(MeterSource.MANUAL);
        reading.setRecordedByUserId(null);
        reading.setDeviceId("tablet-1");
        reading.setNote(note);
        reading.setCreatedAt(Instant.parse("2026-05-31T08:00:00Z"));
        reading.setUpdatedAt(Instant.parse("2026-05-31T08:00:00Z"));
        return reading;
    }
}
