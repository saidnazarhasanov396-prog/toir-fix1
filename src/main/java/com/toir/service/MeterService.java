package com.toir.service;

import com.toir.dto.meter.*;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.equipment.EquipmentMeter;
import com.toir.entity.equipment.MeterReading;
import com.toir.entity.users.User;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.enums.MaintenanceTriggerSource;
import com.toir.enums.MeterType;
import com.toir.exception.RestException;
import com.toir.repository.MeterReadingRepository;
import com.toir.repository.equipment.EquipmentMeterRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.users.UserRepository;
import com.toir.service.equipment.EquipmentStatusLifecycleService;
import com.toir.service.maintanance.MaintenanceAutomationService;
import com.toir.util.AuditBuilderService;
import com.toir.util.PaginationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MeterService {

    private static final List<String> READING_SORT_FIELDS = List.of("createdAt", "readAt", "value");

    private final EquipmentMeterRepository meterRepository;
    private final MeterReadingRepository readingRepository;
    private final EquipmentRepository equipmentRepository;
    private final UserRepository userRepository;
    private final AuditBuilderService auditBuilderService;
    private final EquipmentStatusLifecycleService equipmentStatusLifecycleService;
    private final MaintenanceAutomationService maintenanceAutomationService;

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
        EquipmentMeter meter = getMeterOrThrow(request.meterId());
        if (!meter.isActive()) {
            throw RestException.conflict("Meter is not active: " + meter.getId());
        }
        equipmentStatusLifecycleService.assertOperationallyAllowed(meter.getEquipmentId(), "add meter reading");
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

        Instant readAt = request.readAt() != null ? request.readAt() : Instant.now();

        MeterReading reading = new MeterReading();
        reading.setMeterId(meter.getId());
        reading.setEquipmentId(meter.getEquipmentId());
        reading.setValue(newValue);
        reading.setDelta(delta);
        reading.setReadAt(readAt);
        reading.setSource(request.source());
        reading.setRecordedByUserId(request.recordedByUserId());
        reading.setDeviceId(request.deviceId());
        reading.setNote(request.note());
        MeterReading saved = readingRepository.save(reading);

        meter.setCurrentValue(newValue);
        meter.setLastReadAt(readAt);

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

        try {
            maintenanceAutomationService.evaluateEquipment(meter.getEquipmentId(), MaintenanceTriggerSource.METER_READING);
        } catch (RuntimeException ex) {
            // Meter reading is the source of truth; automation failures are recorded separately and must not lose readings.
        }

        return enrichReading(saved);
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
