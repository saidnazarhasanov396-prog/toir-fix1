package com.toir.service.integration;

import com.toir.dto.integration.atil.AtilMeterReadingImportRequest;
import com.toir.dto.integration.atil.AtilRepairRequestImportRequest;
import com.toir.dto.integration.atil.AtilSyncResult;
import com.toir.dto.integration.atil.AtilVehicleUpsertRequest;
import com.toir.dto.meter.EquipmentMeterRequest;
import com.toir.dto.meter.MeterReadingDto;
import com.toir.dto.meter.MeterReadingRequest;
import com.toir.dto.repairrequest.RepairRequestDto;
import com.toir.dto.repairrequest.RepairRequestRequest;
import com.toir.dto.vehicle.VehicleDetailDto;
import com.toir.dto.vehicle.VehicleRequest;
import com.toir.entity.ExternalEntityLink;
import com.toir.entity.equipment.EquipmentMeter;
import com.toir.enums.EquipmentStatus;
import com.toir.enums.MeterSource;
import com.toir.enums.MeterType;
import com.toir.enums.RequestSource;
import com.toir.enums.VehicleType;
import com.toir.repository.ExternalEntityLinkRepository;
import com.toir.repository.VehicleDetailsRepository;
import com.toir.repository.equipment.EquipmentMeterRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.service.MeterService;
import com.toir.service.VehicleService;
import com.toir.service.repair.RepairRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AtilInboundIntegrationService {

    private static final String SOURCE_SYSTEM = "ATIL";
    private static final String TARGET_SYSTEM = "TOIR";
    private static final String ENTITY_VEHICLE = "VEHICLE";
    private static final String ENTITY_EQUIPMENT = "EQUIPMENT";
    private static final String ENTITY_METER_READING = "METER_READING";
    private static final String ENTITY_REPAIR_REQUEST = "REPAIR_REQUEST";

    private final VehicleService vehicleService;
    private final MeterService meterService;
    private final RepairRequestService repairRequestService;
    private final VehicleDetailsRepository vehicleDetailsRepository;
    private final EquipmentRepository equipmentRepository;
    private final EquipmentMeterRepository equipmentMeterRepository;
    private final ExternalEntityLinkRepository externalEntityLinkRepository;

    @Transactional
    public AtilSyncResult upsertVehicles(List<AtilVehicleUpsertRequest> items) {
        List<AtilSyncResult.ItemResult> results = new ArrayList<>();
        int created = 0;
        int updated = 0;
        int failed = 0;
        for (AtilVehicleUpsertRequest item : safe(items)) {
            try {
                UUID equipmentId = resolveEquipmentId(item.vehicleId(), item.equipmentId(), item.plateNumber(), item.vin());
                VehicleRequest request = toVehicleRequest(item);
                String action;
                if (equipmentId == null) {
                    VehicleDetailDto createdVehicle = vehicleService.create(request);
                    equipmentId = createdVehicle.equipment().id();
                    created++;
                    action = "CREATED";
                } else {
                    vehicleService.update(equipmentId, request);
                    updated++;
                    action = "UPDATED";
                }
                upsertLink(
                        ENTITY_VEHICLE,
                        item.vehicleId().toString(),
                        ENTITY_EQUIPMENT,
                        equipmentId,
                        item.plateNumber(),
                        item.hashCode(),
                        "SYNCED",
                        null
                );
                results.add(ok(ENTITY_VEHICLE, item.vehicleId(), equipmentId, action));
            } catch (RuntimeException e) {
                failed++;
                results.add(failed(ENTITY_VEHICLE, item.vehicleId(), e));
            }
        }
        return new AtilSyncResult(created, updated, failed, safe(items).size(), results);
    }

    @Transactional
    public AtilSyncResult importMeterReadings(List<AtilMeterReadingImportRequest> items) {
        List<AtilSyncResult.ItemResult> results = new ArrayList<>();
        int created = 0;
        int updated = 0;
        int failed = 0;
        for (AtilMeterReadingImportRequest item : safe(items)) {
            try {
                if (item.meterReadingId() != null && sourceLink(ENTITY_METER_READING, item.meterReadingId().toString()).isPresent()) {
                    updated++;
                    results.add(ok(ENTITY_METER_READING, item.meterReadingId(), null, "SKIPPED_EXISTING"));
                    continue;
                }
                UUID equipmentId = resolveEquipmentId(item.vehicleId(), item.equipmentId(), null, null);
                if (equipmentId == null) {
                    throw new IllegalArgumentException("No TOIR equipment mapping found for ATIL vehicleId: " + item.vehicleId());
                }
                UUID meterId = meterFor(equipmentId, item.meterType());
                MeterReadingDto reading = meterService.addReading(new MeterReadingRequest(
                        meterId,
                        item.value(),
                        item.readAt() != null ? item.readAt() : Instant.now(),
                        MeterSource.IMPORT,
                        null,
                        item.deviceId(),
                        item.note()
                ));
                if (item.meterReadingId() != null) {
                    upsertLink(
                            ENTITY_METER_READING,
                            item.meterReadingId().toString(),
                            ENTITY_METER_READING,
                            reading.id(),
                            item.vehicleId().toString(),
                            item.hashCode(),
                            "SYNCED",
                            null
                    );
                }
                created++;
                results.add(ok(ENTITY_METER_READING, item.meterReadingId(), reading.id(), "CREATED"));
            } catch (RuntimeException e) {
                failed++;
                results.add(failed(ENTITY_METER_READING, item.meterReadingId(), e));
            }
        }
        return new AtilSyncResult(created, updated, failed, safe(items).size(), results);
    }

    @Transactional
    public AtilSyncResult importRepairRequests(List<AtilRepairRequestImportRequest> items) {
        List<AtilSyncResult.ItemResult> results = new ArrayList<>();
        int created = 0;
        int updated = 0;
        int failed = 0;
        for (AtilRepairRequestImportRequest item : safe(items)) {
            try {
                Optional<ExternalEntityLink> existing = sourceLink(ENTITY_REPAIR_REQUEST, item.repairRequestId().toString());
                if (existing.isPresent()) {
                    updated++;
                    results.add(ok(ENTITY_REPAIR_REQUEST, item.repairRequestId(), existing.get().getTargetEntityId(), "SKIPPED_EXISTING"));
                    continue;
                }
                UUID equipmentId = resolveEquipmentId(item.vehicleId(), item.equipmentId(), null, null);
                if (equipmentId == null) {
                    throw new IllegalArgumentException("No TOIR equipment mapping found for ATIL vehicleId: " + item.vehicleId());
                }
                RepairRequestDto repairRequest = repairRequestService.create(new RepairRequestRequest(
                        firstText(item.number(), "ATIL-RR-" + item.repairRequestId()),
                        item.title(),
                        item.description(),
                        null,
                        null,
                        null,
                        equipmentId,
                        item.departmentId(),
                        null,
                        item.reporterId(),
                        item.priority(),
                        item.criticality(),
                        RequestSource.OPERATOR,
                        item.targetCompletionAt(),
                        null
                ));
                upsertLink(
                        ENTITY_REPAIR_REQUEST,
                        item.repairRequestId().toString(),
                        ENTITY_REPAIR_REQUEST,
                        repairRequest.id(),
                        item.vehicleId().toString(),
                        item.hashCode(),
                        "SYNCED",
                        null
                );
                created++;
                results.add(ok(ENTITY_REPAIR_REQUEST, item.repairRequestId(), repairRequest.id(), "CREATED"));
            } catch (RuntimeException e) {
                failed++;
                results.add(failed(ENTITY_REPAIR_REQUEST, item.repairRequestId(), e));
            }
        }
        return new AtilSyncResult(created, updated, failed, safe(items).size(), results);
    }

    private VehicleRequest toVehicleRequest(AtilVehicleUpsertRequest item) {
        return new VehicleRequest(
                null,
                vehicleName(item),
                firstText(item.inventoryNumber(), item.garageNumber(), item.refId(), item.plateNumber()),
                item.garageNumber(),
                item.vin(),
                item.equipmentTypeId(),
                item.departmentId(),
                item.locationId(),
                mapStatus(item.status()),
                item.plateNumber(),
                null,
                item.vin(),
                item.brand(),
                item.model(),
                item.manufactureYear(),
                mapVehicleType(item.vehicleType()),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                item.assignedDriverId(),
                item.currentOdometerKm(),
                item.currentEngineHours(),
                null,
                null,
                null,
                null,
                item.gpsDeviceId(),
                null,
                null
        );
    }

    private UUID resolveEquipmentId(UUID vehicleId, UUID suppliedEquipmentId, String plateNumber, String vin) {
        Optional<ExternalEntityLink> linked = sourceLink(ENTITY_VEHICLE, vehicleId.toString());
        if (linked.isPresent()) {
            return linked.get().getTargetEntityId();
        }
        if (suppliedEquipmentId != null && equipmentRepository.existsByIdAndIsDeletedFalse(suppliedEquipmentId)) {
            return suppliedEquipmentId;
        }
        if (hasText(plateNumber)) {
            Optional<UUID> byPlate = vehicleDetailsRepository.findByPlateNumberAndIsDeletedFalse(plateNumber)
                    .map(details -> details.getEquipmentId());
            if (byPlate.isPresent()) {
                return byPlate.get();
            }
        }
        if (hasText(vin)) {
            return vehicleDetailsRepository.findByVinAndIsDeletedFalse(vin)
                    .map(details -> details.getEquipmentId())
                    .orElse(null);
        }
        return null;
    }

    private UUID meterFor(UUID equipmentId, MeterType meterType) {
        return equipmentMeterRepository.findAllByEquipmentIdAndIsDeletedFalse(equipmentId).stream()
                .filter(EquipmentMeter::isActive)
                .filter(meter -> meter.getMeterType() == meterType)
                .map(EquipmentMeter::getId)
                .findFirst()
                .orElseGet(() -> meterService.createMeter(new EquipmentMeterRequest(
                        equipmentId,
                        meterType,
                        meterName(meterType),
                        meterUnit(meterType),
                        0,
                        null,
                        true
                )).id());
    }

    private Optional<ExternalEntityLink> sourceLink(String sourceEntityType, String sourceEntityId) {
        return externalEntityLinkRepository.findBySourceSystemAndSourceEntityTypeAndSourceEntityIdAndIsDeletedFalse(
                SOURCE_SYSTEM,
                sourceEntityType,
                sourceEntityId
        );
    }

    private void upsertLink(
            String sourceEntityType,
            String sourceEntityId,
            String targetEntityType,
            UUID targetEntityId,
            String naturalKey,
            int payloadHash,
            String status,
            String error
    ) {
        ExternalEntityLink link = sourceLink(sourceEntityType, sourceEntityId).orElseGet(ExternalEntityLink::new);
        link.setSourceSystem(SOURCE_SYSTEM);
        link.setSourceEntityType(sourceEntityType);
        link.setSourceEntityId(sourceEntityId);
        link.setTargetSystem(TARGET_SYSTEM);
        link.setTargetEntityType(targetEntityType);
        link.setTargetEntityId(targetEntityId);
        link.setNaturalKey(naturalKey);
        link.setLastPayloadHash(Integer.toHexString(payloadHash));
        link.setLastSyncedAt(Instant.now());
        link.setSyncStatus(status);
        link.setLastError(error);
        externalEntityLinkRepository.save(link);
    }

    private AtilSyncResult.ItemResult ok(String sourceEntityType, UUID sourceEntityId, UUID targetEntityId, String action) {
        return new AtilSyncResult.ItemResult(
                sourceEntityType,
                sourceEntityId == null ? null : sourceEntityId.toString(),
                targetEntityId,
                action,
                "OK",
                null
        );
    }

    private AtilSyncResult.ItemResult failed(String sourceEntityType, UUID sourceEntityId, RuntimeException e) {
        return new AtilSyncResult.ItemResult(
                sourceEntityType,
                sourceEntityId == null ? null : sourceEntityId.toString(),
                null,
                "FAILED",
                "FAILED",
                e.getMessage()
        );
    }

    private static VehicleType mapVehicleType(String source) {
        if (!hasText(source)) {
            return VehicleType.OTHER;
        }
        String normalized = source.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "PASSENGER", "PASSENGER_CAR", "CAR", "SEDAN" -> VehicleType.PASSENGER_CAR;
            case "TRUCK", "CARGO", "HEAVY", "HEAVY_TRUCK" -> VehicleType.TRUCK;
            case "BUS" -> VehicleType.BUS;
            case "SPECIAL", "SPECIAL_EQUIPMENT", "EQUIPMENT" -> VehicleType.SPECIAL_EQUIPMENT;
            case "FORKLIFT" -> VehicleType.FORKLIFT;
            case "TRAILER" -> VehicleType.TRAILER;
            default -> VehicleType.OTHER;
        };
    }

    private static EquipmentStatus mapStatus(String source) {
        if (!hasText(source)) {
            return EquipmentStatus.ACTIVE;
        }
        String normalized = source.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "ACTIVE", "RESERVED", "IN_OPERATION" -> EquipmentStatus.ACTIVE;
            case "IN_MAINTENANCE" -> EquipmentStatus.IN_REPAIR;
            case "BLOCKED" -> EquipmentStatus.OUT_OF_SERVICE;
            case "DECOMMISSIONED" -> EquipmentStatus.DECOMMISSIONED;
            default -> EquipmentStatus.ACTIVE;
        };
    }

    private static String vehicleName(AtilVehicleUpsertRequest item) {
        String makeModel = firstText(
                join(item.brand(), item.model()),
                item.brand(),
                item.model(),
                "Vehicle"
        );
        return makeModel + " " + item.plateNumber();
    }

    private static String meterName(MeterType meterType) {
        return switch (meterType) {
            case MILEAGE_KM -> "Mileage";
            case ENGINE_HOURS -> "Engine hours";
            case CYCLES -> "Cycles";
            case TONS_PRODUCED -> "Tons produced";
            case KWH_CONSUMED -> "Energy consumed";
            case CUSTOM -> "ATIL meter";
        };
    }

    private static String meterUnit(MeterType meterType) {
        return switch (meterType) {
            case MILEAGE_KM -> "km";
            case ENGINE_HOURS -> "h";
            case CYCLES -> "cycles";
            case TONS_PRODUCED -> "t";
            case KWH_CONSUMED -> "kWh";
            case CUSTOM -> "unit";
        };
    }

    private static String join(String first, String second) {
        if (!hasText(first)) {
            return trimToNull(second);
        }
        if (!hasText(second)) {
            return trimToNull(first);
        }
        return first.trim() + " " + second.trim();
    }

    private static String firstText(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private static String trimToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static <T> List<T> safe(List<T> items) {
        return Objects.requireNonNullElse(items, List.of());
    }
}
