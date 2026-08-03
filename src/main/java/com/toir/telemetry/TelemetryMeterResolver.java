package com.toir.telemetry;

import com.toir.entity.equipment.EquipmentMeter;
import com.toir.enums.MeterType;
import com.toir.repository.equipment.EquipmentMeterRepository;
import com.toir.repository.equipment.EquipmentRepository;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import org.springframework.stereotype.Component;

@Component
public class TelemetryMeterResolver {

    private static final Map<String, MeterType> STANDARD_ALIASES = Map.ofEntries(
            Map.entry("engine_hours", MeterType.ENGINE_HOURS),
            Map.entry("moto_hours", MeterType.ENGINE_HOURS),
            Map.entry("motohours", MeterType.ENGINE_HOURS),
            Map.entry("mileage_km", MeterType.MILEAGE_KM),
            Map.entry("odometer_km", MeterType.MILEAGE_KM),
            Map.entry("cycles", MeterType.CYCLES),
            Map.entry("cycle_count", MeterType.CYCLES),
            Map.entry("tons_produced", MeterType.TONS_PRODUCED),
            Map.entry("kwh_consumed", MeterType.KWH_CONSUMED),
            Map.entry("energy_kwh", MeterType.KWH_CONSUMED));

    private final EquipmentRepository equipmentRepository;
    private final EquipmentMeterRepository meterRepository;

    public TelemetryMeterResolver(EquipmentRepository equipmentRepository, EquipmentMeterRepository meterRepository) {
        this.equipmentRepository = equipmentRepository;
        this.meterRepository = meterRepository;
    }

    public Optional<EquipmentMeter> resolve(String assetId, String metricKey, String unit) {
        UUID equipmentId = parseUuid(assetId).orElse(null);
        if (equipmentId == null || !equipmentRepository.existsByIdAndIsDeletedFalse(equipmentId)) {
            return Optional.empty();
        }
        List<EquipmentMeter> meters = meterRepository
                .findAllByEquipmentIdAndActiveTrueAndIsDeletedFalse(equipmentId);
        return resolveUnique(meters, metricKey)
                .filter(meter -> unitsEqual(meter.getUnit(), unit));
    }

    private Optional<EquipmentMeter> resolveUnique(List<EquipmentMeter> meters, String metricKey) {
        Optional<UUID> meterId = parseUuid(metricKey);
        if (meterId.isPresent()) {
            return uniqueMatch(meters, meter -> meterId.get().equals(meter.getId()));
        }

        String normalizedKey = normalize(metricKey);
        if (normalizedKey == null) {
            return Optional.empty();
        }
        Predicate<EquipmentMeter> hasNormalizedName = meter -> normalizedKey.equals(normalize(meter.getName()));
        if (meters.stream().anyMatch(hasNormalizedName)) {
            return uniqueMatch(meters, hasNormalizedName);
        }

        MeterType meterType = STANDARD_ALIASES.get(normalizedKey);
        return meterType == null ? Optional.empty() : uniqueMatch(meters, meter -> meterType == meter.getMeterType());
    }

    private Optional<EquipmentMeter> uniqueMatch(List<EquipmentMeter> meters, Predicate<EquipmentMeter> matches) {
        EquipmentMeter found = null;
        for (EquipmentMeter meter : meters) {
            if (matches.test(meter)) {
                if (found != null) {
                    return Optional.empty();
                }
                found = meter;
            }
        }
        return Optional.ofNullable(found);
    }

    private Optional<UUID> parseUuid(String value) {
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
    }

    private boolean unitsEqual(String meterUnit, String simulatorUnit) {
        return meterUnit != null && simulatorUnit != null
                && meterUnit.trim().equalsIgnoreCase(simulatorUnit.trim());
    }
}
