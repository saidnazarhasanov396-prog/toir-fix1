package com.toir.telemetry;

import com.toir.dto.meter.MeterReadingDto;
import com.toir.dto.meter.MeterReadingRequest;
import com.toir.entity.equipment.EquipmentMeter;
import com.toir.enums.MeterSource;
import com.toir.service.MeterService;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class TelemetryReadingIngestor {

    private static final String DEVICE_ID = "equipment-telemetry-simulator";

    private final TelemetryMeterResolver resolver;
    private final MeterService meterService;
    private final MeterRealtimePublisher publisher;

    public TelemetryReadingIngestor(
            TelemetryMeterResolver resolver,
            MeterService meterService,
            MeterRealtimePublisher publisher
    ) {
        this.resolver = resolver;
        this.meterService = meterService;
        this.publisher = publisher;
    }

    public IngestionResult ingest(SimulatorSnapshot snapshot) {
        int accepted = 0;
        int skipped = 0;
        int rejected = 0;
        for (SimulatorSnapshot.Asset asset : snapshot.assets()) {
            for (Map.Entry<String, SimulatorSnapshot.Metric> entry : asset.metrics().entrySet()) {
                try {
                    Outcome outcome = ingestMetric(asset.assetId(), entry.getKey(), entry.getValue(), snapshot.sentAt());
                    switch (outcome) {
                        case ACCEPTED -> accepted++;
                        case SKIPPED -> skipped++;
                        case REJECTED -> rejected++;
                    }
                } catch (RuntimeException exception) {
                    rejected++;
                    log.warn("telemetry_reading_rejected assetId={} metric={}", asset.assetId(), entry.getKey(), exception);
                }
            }
        }
        return new IngestionResult(accepted, skipped, rejected);
    }

    private Outcome ingestMetric(
            String assetId,
            String metricKey,
            SimulatorSnapshot.Metric metric,
            Instant sentAt
    ) {
        if (!Double.isFinite(metric.value()) || metric.value() < 0) {
            return Outcome.REJECTED;
        }

        Optional<EquipmentMeter> resolved = resolver.resolve(assetId, metricKey, metric.unit());
        if (resolved.isEmpty()) {
            log.warn("telemetry_meter_not_resolved assetId={} metric={}", assetId, metricKey);
            return Outcome.SKIPPED;
        }

        EquipmentMeter meter = resolved.get();
        if (Double.compare(metric.value(), meter.getCurrentValue()) == 0
                || isOlderThanCurrentReading(sentAt, meter.getLastReadAt())) {
            return Outcome.SKIPPED;
        }
        if (isDecreaseWithoutRollover(meter, metric.value())) {
            log.warn(
                    "telemetry_reading_below_current assetId={} meterId={} incoming={} current={}",
                    assetId,
                    meter.getId(),
                    metric.value(),
                    meter.getCurrentValue());
            return Outcome.SKIPPED;
        }

        MeterReadingDto reading = meterService.addReading(new MeterReadingRequest(
                meter.getId(),
                metric.value(),
                sentAt,
                MeterSource.IOT,
                null,
                DEVICE_ID,
                null));
        publisher.publish(new MeterRealtimeUpdate(
                reading.meterId(),
                reading.equipmentId(),
                reading.value(),
                reading.readAt(),
                reading.source()));
        return Outcome.ACCEPTED;
    }

    private boolean isDecreaseWithoutRollover(EquipmentMeter meter, double incoming) {
        if (incoming >= meter.getCurrentValue()) {
            return false;
        }
        return meter.getRolloverValue() == null || meter.getRolloverValue() <= 0;
    }

    private boolean isOlderThanCurrentReading(Instant sentAt, Instant lastReadAt) {
        return lastReadAt != null && sentAt != null && sentAt.isBefore(lastReadAt);
    }

    public record IngestionResult(int accepted, int skipped, int rejected) {
    }

    private enum Outcome {
        ACCEPTED,
        SKIPPED,
        REJECTED
    }
}
