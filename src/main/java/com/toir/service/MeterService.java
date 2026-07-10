package com.toir.service;

import com.toir.dto.meter.*;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.equipment.EquipmentMeter;
import com.toir.entity.equipment.MeterReading;
import com.toir.entity.equipment.VehicleDetails;
import com.toir.entity.users.User;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.enums.MaintenanceTriggerSource;
import com.toir.enums.MeterReadingContext;
import com.toir.enums.MeterType;
import com.toir.exception.RestException;
import com.toir.repository.MeterReadingRepository;
import com.toir.repository.VehicleDetailsRepository;
import com.toir.repository.equipment.EquipmentMeterRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.users.UserRepository;
import com.toir.service.equipment.EquipmentStatusLifecycleService;
import com.toir.service.maintanance.MaintenanceAutomationService;
import com.toir.service.sparepartlifecycle.SparePartLifecycleEvaluationService;
import com.toir.util.AuditBuilderService;
import com.toir.util.PaginationUtils;
import com.toir.util.SortUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MeterService {

    private static final List<String> READING_SORT_FIELDS = List.of("createdAt", "readAt", "value");

    private final EquipmentMeterRepository meterRepository;
    private final MeterReadingRepository readingRepository;
    private final EquipmentRepository equipmentRepository;
    private final VehicleDetailsRepository vehicleDetailsRepository;
    private final UserRepository userRepository;
    private final AuditBuilderService auditBuilderService;
    private final EquipmentStatusLifecycleService equipmentStatusLifecycleService;
    private final ObjectProvider<MaintenanceAutomationService> maintenanceAutomationServiceProvider;
    private final SparePartLifecycleEvaluationService sparePartLifecycleEvaluationService;
    private final ForecastService forecastService;

    @Transactional(readOnly = true)
    public List<EquipmentMeterDto> listByEquipment(UUID equipmentId) {
        return meterRepository.findAllByEquipmentIdAndIsDeletedFalse(equipmentId).stream()
                .map(this::enrichWithEquipmentName)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<EquipmentMeterDto> listAll(String search, MeterType meterType) {
        return listAll(search, meterType, null, null);
    }

    @Transactional(readOnly = true)
    public List<EquipmentMeterDto> listAll(String search, MeterType meterType, UUID equipmentId, String equipmentSearch) {
        String meterTypeStr = meterType == null ? null : meterType.toString();
        return meterRepository.findAllByFiltersOrderByUpdatedAtDesc(search, meterTypeStr, equipmentId, equipmentSearch).stream()
                .map(this::enrichWithEquipmentName)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<EquipmentMeterDto> listAll(String search,
                                           MeterType meterType,
                                           UUID equipmentId,
                                           String equipmentSearch,
                                           String sortBy,
                                           String sortDir) {
        List<EquipmentMeterDto> rows = listAll(search, meterType, equipmentId, equipmentSearch);
        Comparator<EquipmentMeterDto> comparator = switch (sortBy == null ? "" : sortBy.trim()) {
            case "meterType" -> Comparator.comparing(
                    EquipmentMeterDto::meterType,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "lastReadingValue" -> Comparator.comparingDouble(EquipmentMeterDto::currentValue);
            case "lastReadingAt" -> Comparator.comparing(
                    EquipmentMeterDto::lastReadAt,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            default -> null;
        };
        if (comparator == null) {
            return rows;
        }
        if (SortUtils.direction(sortDir, Sort.Direction.ASC).isDescending()) {
            comparator = comparator.reversed();
        }
        return rows.stream().sorted(comparator).toList();
    }

    @Transactional(readOnly = true)
    public MeterStatsResponse getStats(String search, MeterType meterType, UUID equipmentId, String equipmentSearch) {
        String meterTypeStr = meterType == null ? null : meterType.toString();
        var stats = meterRepository.getMeterStats(search, meterTypeStr, equipmentId, equipmentSearch);
        return new MeterStatsResponse(
                stats.getTotalMeters() == null ? 0 : stats.getTotalMeters(),
                stats.getActiveMeters() == null ? 0 : stats.getActiveMeters(),
                stats.getTotalReadings() == null ? 0 : stats.getTotalReadings(),
                stats.getDueTriggers() == null ? 0 : stats.getDueTriggers()
        );
    }

    @Transactional(readOnly = true)
    public EquipmentMeterDto findMeter(UUID id) {
        return enrichWithEquipmentName(getMeterOrThrow(id));
    }

    @Transactional
    public EquipmentMeterDto createMeter(EquipmentMeterRequest request) {
        EquipmentMeter meter = new EquipmentMeter();
        meter.setEquipmentId(request.equipmentId());
        meter.setMeterType(request.meterType());
        meter.setName(request.name());
        meter.setUnit(request.unit());
        meter.setCurrentValue(request.initialValue());
        meter.setRolloverValue(request.rolloverValue());
        if (request.active() != null) meter.setActive(request.active());
        if (request.primary() != null) meter.setPrimary(request.primary());
        EquipmentMeter saved = meterRepository.save(meter);

        auditBuilderService.log(
                "equipment_meter",
                saved.getId().toString(),
                AuditAction.CREATE,
                AuditModule.EQUIPMENT_METER,
                "Счетчик оборудования создан",
                null,
                saved
        );

        return enrichWithEquipmentName(saved);
    }

    @Transactional
    public EquipmentMeterDto updateMeter(UUID id, EquipmentMeterRequest request) {
        EquipmentMeter meter = getMeterOrThrow(id);
        meter.setEquipmentId(request.equipmentId());
        meter.setMeterType(request.meterType());
        meter.setName(request.name());
        meter.setUnit(request.unit());
        meter.setRolloverValue(request.rolloverValue());
        if (request.active() != null) meter.setActive(request.active());
        if (request.primary() != null) meter.setPrimary(request.primary());
        EquipmentMeter saved = meterRepository.save(meter);

        auditBuilderService.log(
                "equipment_meter",
                saved.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.EQUIPMENT_METER,
                "Счетчик оборудования обновлен",
                meter,
                saved
        );

        return enrichWithEquipmentName(meter);
    }

    @Transactional
    public void deleteMeter(UUID id) {
        var entity = getMeterOrThrow(id);
        entity.setDeleted(true);
        EquipmentMeter saved = meterRepository.save(entity);

        auditBuilderService.log(
                "equipment_meter",
                saved.getId().toString(),
                AuditAction.DELETE,
                AuditModule.EQUIPMENT_METER,
                "Счетчик оборудования удален",
                saved,
                null
        );

    }

    @Transactional
    public MeterReadingDto addReading(MeterReadingRequest request) {
        return addReading(request, MeterReadingContext.MANUAL_UPDATE, null, null, null);
    }

    @Transactional
    public MeterReadingDto addReading(
            MeterReadingRequest request,
            MeterReadingContext readingContext,
            UUID repairRequestId,
            UUID workOrderId,
            UUID defectId
    ) {
        EquipmentMeter meter = getMeterOrThrow(request.meterId());
        if (!meter.isActive()) {
            throw RestException.conflict("Meter is not active: " + meter.getId());
        }
        equipmentStatusLifecycleService.assertOperationallyAllowed(meter.getEquipmentId(), "add meter reading");
        Instant readAt = request.readAt() != null ? request.readAt() : Instant.now();
        if (meter.getLastReadAt() != null && readAt.isBefore(meter.getLastReadAt())) {
            throw RestException.conflict(
                    "METER_READING_OUT_OF_ORDER: reading timestamp is older than the canonical current reading");
        }
        double newValue = request.value();
        double previous = meter.getCurrentValue();
        Double delta = null;
        if (newValue >= previous) {
            delta = newValue - previous;
        } else if (meter.getRolloverValue() != null && meter.getRolloverValue() > 0) {
            delta = (meter.getRolloverValue() - previous) + newValue;
        } else {
            throw RestException.conflict(
                    "Reading value (" + newValue + ") is less than current meter value (" + previous + ")");
        }

        MeterReading reading = new MeterReading();
        reading.setMeterId(meter.getId());
        reading.setEquipmentId(meter.getEquipmentId());
        reading.setValue(newValue);
        reading.setDelta(delta);
        reading.setReadAt(readAt);
        reading.setSource(request.source());
        reading.setRecordedByUserId(request.recordedByUserId());
        reading.setRepairRequestId(repairRequestId);
        reading.setWorkOrderId(workOrderId);
        reading.setDefectId(defectId);
        reading.setReadingContext(readingContext == null ? MeterReadingContext.MANUAL_UPDATE : readingContext);
        reading.setDeviceId(request.deviceId());
        reading.setNote(request.note());
        MeterReading saved = readingRepository.save(reading);

        meter.setCurrentValue(newValue);
        meter.setLastReadAt(readAt);
        syncVehicleMeter(meter, newValue);

        auditBuilderService.log(
                "meter_reading",
                saved.getId().toString(),
                AuditAction.CREATE,
                AuditModule.METER_READING,
                "Показание счетчика создано",
                null,
                saved
        );

        EquipmentMeter savedMeter = meterRepository.save(meter);
        auditBuilderService.log(
                "equipment_meter",
                savedMeter.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.EQUIPMENT_METER,
                "Счетчик оборудования обновлен",
                meter,
                savedMeter
        );

        triggerSparePartLifecycleAfterCommit(meter.getId(), readAt);

        MaintenanceAutomationService automationService = maintenanceAutomationServiceProvider.getIfAvailable();
        if (automationService != null) {
            try {
                automationService.evaluateEquipment(meter.getEquipmentId(), MaintenanceTriggerSource.METER_READING);
            } catch (RuntimeException ex) {
                log.warn(
                        "maintenance_automation_after_meter_reading_failed equipmentId={} meterId={} readingId={}",
                        meter.getEquipmentId(),
                        meter.getId(),
                        saved.getId(),
                        ex
                );
            }
        }

        try {
            LocalDate usageDate = readAt.atZone(ZoneId.systemDefault()).toLocalDate();
            forecastService.recordDailyUsage(meter.getEquipmentId(), usageDate, delta);
            forecastService.recalculate(meter.getEquipmentId());
        } catch (RuntimeException ex) {
            log.warn(
                    "forecast_recalculate_after_meter_reading_failed equipmentId={} meterId={} readingId={}",
                    meter.getEquipmentId(),
                    meter.getId(),
                    saved.getId(),
                    ex
            );
        }

        return enrichReading(saved);
    }

    private void triggerSparePartLifecycleAfterCommit(UUID meterId, Instant evaluatedAt) {
        Runnable evaluation = () -> {
            try {
                sparePartLifecycleEvaluationService.reevaluateForMeter(meterId, evaluatedAt);
            } catch (RuntimeException exception) {
                log.error("spare_part_lifecycle_after_meter_reading_failed meterId={}", meterId, exception);
            }
        };
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            evaluation.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                evaluation.run();
            }
        });
    }

    private void syncVehicleMeter(EquipmentMeter meter, double newValue) {
        if (meter.getMeterType() != MeterType.MILEAGE_KM && meter.getMeterType() != MeterType.ENGINE_HOURS) {
            return;
        }
        vehicleDetailsRepository.findByEquipmentIdAndIsDeletedFalse(meter.getEquipmentId())
                .ifPresent(vehicleDetails -> syncVehicleMeterValue(vehicleDetails, meter.getMeterType(), newValue));
    }

    private void syncVehicleMeterValue(VehicleDetails vehicleDetails, MeterType meterType, double newValue) {
        if (meterType == MeterType.MILEAGE_KM) {
            vehicleDetails.setCurrentOdometerKm(newValue);
        } else if (meterType == MeterType.ENGINE_HOURS) {
            vehicleDetails.setCurrentEngineHours(newValue);
        } else {
            return;
        }
        vehicleDetailsRepository.save(vehicleDetails);
    }

    @Transactional(readOnly = true)
    public List<MeterReadingDto> history(UUID meterId, int limit) {
        List<MeterReading> content = readingRepository
                        .findAllByMeterIdAndIsDeletedFalseOrderByReadAtDesc(meterId, PaginationUtils.pageRequest(0, limit))
                        .getContent();
        return enrichReadings(content);
    }

    @Transactional(readOnly = true)
    public Page<MeterReadingDto> listReadings(String search, String sort, String direction, int page, int size) {
        String requestedSort = sort;
        String requestedDirection = direction;
        if (sort != null && sort.contains(",")) {
            String[] parts = sort.split(",", 2);
            requestedSort = parts[0];
            requestedDirection = parts[1];
        }
        String safeSort = normalizeReadingSort(requestedSort);
        String safeDirection = "asc".equalsIgnoreCase(requestedDirection) ? "asc" : "desc";
        String normalizedSearch = search == null || search.isBlank() ? null : search.trim();
        Page<MeterReading> pageResult = readingRepository.searchReadings(
                        normalizedSearch,
                        safeSort,
                        safeDirection,
                        PaginationUtils.pageRequest(page, size)
                );
        List<MeterReadingDto> enriched = enrichReadings(pageResult.getContent());
        return new org.springframework.data.domain.PageImpl<>(enriched, pageResult.getPageable(), pageResult.getTotalElements());
    }

    @Transactional(readOnly = true)
    public List<MeterReadingDto> historyBetween(UUID meterId, Instant from, Instant to) {
        getMeterOrThrow(meterId);
        List<MeterReading> list = readingRepository.findAllByMeterIdAndReadAtBetweenAndIsDeletedFalseOrderByReadAtAsc(meterId, from, to);
        return enrichReadings(list);
    }

    @Transactional
    public void deleteReading(UUID readingId) {
        MeterReading reading = readingRepository.findByIdAndIsDeletedFalse(readingId)
                .orElseThrow(() -> RestException.notFound("Meter reading not found: " + readingId));
        reading.setDeleted(true);
        MeterReading saved = readingRepository.save(reading);

        auditBuilderService.log(
                "meter_reading",
                saved.getId().toString(),
                AuditAction.DELETE,
                AuditModule.METER_READING,
                "Показание счетчика удалено",
                saved,
                null
        );

        if (reading.getMeterId() != null) {
            rebuildCurrentMeterProjection(reading.getMeterId());
            triggerSparePartLifecycleAfterCommit(reading.getMeterId(), Instant.now());
        }

        if (reading.getDelta() != null) {
            try {
                LocalDate usageDate = reading.getReadAt().atZone(ZoneId.systemDefault()).toLocalDate();
                forecastService.reverseDailyUsage(reading.getEquipmentId(), usageDate, reading.getDelta());
                forecastService.recalculate(reading.getEquipmentId());
            } catch (RuntimeException ex) {
                log.warn(
                        "forecast_reverse_after_reading_delete_failed equipmentId={} readingId={}",
                        reading.getEquipmentId(),
                        reading.getId(),
                        ex
                );
            }
        }

    }

    private void rebuildCurrentMeterProjection(UUID meterId) {
        EquipmentMeter meter = getMeterOrThrow(meterId);
        readingRepository.findTopByMeterIdAndIsDeletedFalseOrderByReadAtDesc(meterId)
                .ifPresentOrElse(
                        latest -> {
                            meter.setCurrentValue(latest.getValue());
                            meter.setLastReadAt(latest.getReadAt());
                        },
                        () -> {
                            meter.setCurrentValue(0);
                            meter.setLastReadAt(null);
                        }
                );
        meterRepository.save(meter);
        syncVehicleMeter(meter, meter.getCurrentValue());
    }

    private EquipmentMeter getMeterOrThrow(UUID id) {
        return meterRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Equipment meter not found: " + id));
    }

    private EquipmentMeterDto enrichWithEquipmentName(EquipmentMeter meter) {
        String equipmentName = equipmentRepository.findByIdAndIsDeletedFalse(meter.getEquipmentId())
                .map(Equipment::getName)
                .orElse("Unknown");
        return EquipmentMeterDto.from(meter, equipmentName);
    }

    private String normalizeReadingSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return "createdAt";
        }
        String trimmed = sort.trim();
        if (READING_SORT_FIELDS.contains(trimmed)) {
            return trimmed;
        }
        throw RestException.badRequest("Unsupported meter reading sort: " + sort);
    }

    private MeterReadingDto enrichReading(MeterReading r) {
        if (r == null) return null;
        String userName = r.getRecordedByUserId() == null ? null :
                userRepository.findByIdAndIsDeletedFalse(r.getRecordedByUserId())
                        .map(User::getFullName)
                        .orElse(null);
        String meterName = r.getMeterId() == null ? null :
                meterRepository.findByIdAndIsDeletedFalse(r.getMeterId())
                        .map(EquipmentMeter::getName)
                        .orElse(null);
        String equipmentName = r.getEquipmentId() == null ? null :
                equipmentRepository.findByIdAndIsDeletedFalse(r.getEquipmentId())
                        .map(Equipment::getName)
                        .orElse(null);
        return MeterReadingDto.from(r, userName, meterName, equipmentName);
    }

    private List<MeterReadingDto> enrichReadings(List<MeterReading> readings) {
        if (readings == null || readings.isEmpty()) {
            return List.of();
        }

        Set<UUID> userIds = readings.stream()
                .map(MeterReading::getRecordedByUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Set<UUID> meterIds = readings.stream()
                .map(MeterReading::getMeterId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Set<UUID> equipmentIds = readings.stream()
                .map(MeterReading::getEquipmentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<UUID, String> userNames = new java.util.HashMap<>();
        if (!userIds.isEmpty()) {
            userRepository.findAllByIdInAndIsDeletedFalse(userIds)
                    .forEach(u -> userNames.put(u.getId(), u.getFullName()));
        }

        Map<UUID, String> meterNames = new java.util.HashMap<>();
        if (!meterIds.isEmpty()) {
            meterRepository.findAllByIdInAndIsDeletedFalse(meterIds)
                    .forEach(m -> meterNames.put(m.getId(), m.getName()));
        }

        Map<UUID, String> equipmentNames = new java.util.HashMap<>();
        if (!equipmentIds.isEmpty()) {
            equipmentRepository.findAllByIdInAndIsDeletedFalse(equipmentIds)
                    .forEach(e -> equipmentNames.put(e.getId(), e.getName()));
        }

        return readings.stream()
                .map(r -> MeterReadingDto.from(
                        r,
                        userNames.get(r.getRecordedByUserId()),
                        meterNames.get(r.getMeterId()),
                        equipmentNames.get(r.getEquipmentId())
                ))
                .toList();
    }
}
