package com.toir.telemetry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.toir.dto.meter.MeterReadingDto;
import com.toir.dto.meter.MeterReadingRequest;
import com.toir.entity.equipment.EquipmentMeter;
import com.toir.enums.MeterSource;
import com.toir.enums.MeterType;
import com.toir.service.MeterService;
import com.toir.telemetry.TelemetryReadingIngestor.IngestionResult;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TelemetryReadingIngestorTest {

    private static final UUID EQUIPMENT_ID = UUID.fromString("0301b754-f675-4cf2-96a2-8a84fc11ebd5");
    private static final UUID METER_ID = UUID.fromString("f67c1f29-1d51-4c57-b4f7-520209f29a20");

    @Mock
    private TelemetryMeterResolver resolver;
    @Mock
    private MeterService meterService;
    @Mock
    private MeterRealtimePublisher publisher;
    @InjectMocks
    private TelemetryReadingIngestor ingestor;

    @Test
    void persistsChangedValueAsIotAndPublishesCommittedUpdate() {
        EquipmentMeter meter = meter(1200.0, Instant.parse("2026-08-03T08:00:00Z"));
        when(resolver.resolve(EQUIPMENT_ID.toString(), "engine_hours", "h"))
                .thenReturn(Optional.of(meter));
        when(meterService.addReading(any())).thenReturn(readingDto(1200.01));

        IngestionResult result = ingestor.ingest(snapshot(1200.01, "2026-08-03T09:00:00Z"));

        ArgumentCaptor<MeterReadingRequest> request = ArgumentCaptor.forClass(MeterReadingRequest.class);
        verify(meterService).addReading(request.capture());
        assertThat(request.getValue().source()).isEqualTo(MeterSource.IOT);
        assertThat(request.getValue().deviceId()).isEqualTo("equipment-telemetry-simulator");
        verify(publisher).publish(org.mockito.ArgumentMatchers.argThat(update -> update.value() == 1200.01));
        assertThat(result.accepted()).isEqualTo(1);
    }

    @Test
    void skipsEqualStaleNegativeAndNonFiniteValues() {
        when(resolver.resolve(EQUIPMENT_ID.toString(), "equal", "h"))
                .thenReturn(Optional.of(meter(1200.0, Instant.parse("2026-08-03T08:00:00Z"))));
        when(resolver.resolve(EQUIPMENT_ID.toString(), "stale", "h"))
                .thenReturn(Optional.of(meter(1200.0, Instant.parse("2026-08-03T09:00:00Z"))));

        assertThat(ingestor.ingest(equalAndStaleSnapshot())).isEqualTo(new IngestionResult(0, 2, 0));
        assertThat(ingestor.ingest(negativeAndNonFiniteSnapshot())).isEqualTo(new IngestionResult(0, 0, 2));

        verifyNoInteractions(meterService, publisher);
    }

    @Test
    void skipsIncomingValueBelowStoredReadingSoGenerationCannotResetToZero() {
        when(resolver.resolve(EQUIPMENT_ID.toString(), METER_ID.toString(), "km"))
                .thenReturn(Optional.of(meter(13000.0, Instant.parse("2026-08-03T08:00:00Z"))));

        SimulatorSnapshot snapshot = new SimulatorSnapshot(
                java.util.List.of(new SimulatorSnapshot.Asset(
                        EQUIPMENT_ID.toString(),
                        Map.of(METER_ID.toString(), new SimulatorSnapshot.Metric(0.0, "km")))),
                Instant.parse("2026-08-03T09:00:00Z"));

        assertThat(ingestor.ingest(snapshot)).isEqualTo(new IngestionResult(0, 1, 0));
        verifyNoInteractions(meterService, publisher);
    }

    @Test
    void acceptsIncreaseFromStoredReadingKeyedByMeterUuid() {
        EquipmentMeter meter = meter(13000.0, Instant.parse("2026-08-03T08:00:00Z"));
        meter.setUnit("km");
        meter.setMeterType(MeterType.MILEAGE_KM);
        when(resolver.resolve(EQUIPMENT_ID.toString(), METER_ID.toString(), "km"))
                .thenReturn(Optional.of(meter));
        when(meterService.addReading(any())).thenReturn(readingDto(13000.05));

        SimulatorSnapshot snapshot = new SimulatorSnapshot(
                java.util.List.of(new SimulatorSnapshot.Asset(
                        EQUIPMENT_ID.toString(),
                        Map.of(METER_ID.toString(), new SimulatorSnapshot.Metric(13000.05, "km")))),
                Instant.parse("2026-08-03T09:00:00Z"));

        assertThat(ingestor.ingest(snapshot)).isEqualTo(new IngestionResult(1, 0, 0));
        verify(meterService).addReading(any());
    }

    @Test
    void isolatesOneRejectedMetricFromAnotherValidMetric() {
        when(resolver.resolve(anyString(), eq("bad_counter"), anyString()))
                .thenThrow(new IllegalStateException("bad mapping"));
        when(resolver.resolve(anyString(), eq("engine_hours"), eq("h")))
                .thenReturn(Optional.of(meter(1200.0, Instant.EPOCH)));
        when(meterService.addReading(any())).thenReturn(readingDto(1200.1));

        assertThat(ingestor.ingest(snapshotWithBadAndValidMetrics())).isEqualTo(new IngestionResult(1, 0, 1));

        verify(publisher).publish(any());
    }

    private static SimulatorSnapshot snapshot(double value, String sentAt) {
        return new SimulatorSnapshot(
                java.util.List.of(new SimulatorSnapshot.Asset(
                        EQUIPMENT_ID.toString(), Map.of("engine_hours", new SimulatorSnapshot.Metric(value, "h")))),
                Instant.parse(sentAt));
    }

    private static SimulatorSnapshot equalAndStaleSnapshot() {
        return new SimulatorSnapshot(
                java.util.List.of(new SimulatorSnapshot.Asset(
                        EQUIPMENT_ID.toString(), Map.of(
                                "equal", new SimulatorSnapshot.Metric(1200.0, "h"),
                                "stale", new SimulatorSnapshot.Metric(1200.1, "h")))),
                Instant.parse("2026-08-03T08:30:00Z"));
    }

    private static SimulatorSnapshot negativeAndNonFiniteSnapshot() {
        return new SimulatorSnapshot(
                java.util.List.of(new SimulatorSnapshot.Asset(
                        EQUIPMENT_ID.toString(), Map.of(
                                "negative", new SimulatorSnapshot.Metric(-1.0, "h"),
                                "non_finite", new SimulatorSnapshot.Metric(Double.NaN, "h")))),
                Instant.parse("2026-08-03T09:00:00Z"));
    }

    private static SimulatorSnapshot snapshotWithBadAndValidMetrics() {
        Map<String, SimulatorSnapshot.Metric> metrics = new LinkedHashMap<>();
        metrics.put("bad_counter", new SimulatorSnapshot.Metric(100.0, "h"));
        metrics.put("engine_hours", new SimulatorSnapshot.Metric(1200.1, "h"));
        return new SimulatorSnapshot(
                java.util.List.of(new SimulatorSnapshot.Asset(EQUIPMENT_ID.toString(), metrics)),
                Instant.parse("2026-08-03T09:00:00Z"));
    }

    private static EquipmentMeter meter(double currentValue, Instant lastReadAt) {
        EquipmentMeter meter = new EquipmentMeter();
        meter.setId(METER_ID);
        meter.setEquipmentId(EQUIPMENT_ID);
        meter.setMeterType(MeterType.ENGINE_HOURS);
        meter.setName("Engine hours");
        meter.setUnit("h");
        meter.setCurrentValue(currentValue);
        meter.setLastReadAt(lastReadAt);
        meter.setActive(true);
        return meter;
    }

    private static MeterReadingDto readingDto(double value) {
        return new MeterReadingDto(
                UUID.randomUUID(), METER_ID, EQUIPMENT_ID, value, value - 1200.0,
                Instant.parse("2026-08-03T09:00:00Z"), MeterSource.IOT, null,
                null, "Engine hours", "Excavator", "equipment-telemetry-simulator", null,
                Instant.parse("2026-08-03T09:00:01Z"));
    }
}
