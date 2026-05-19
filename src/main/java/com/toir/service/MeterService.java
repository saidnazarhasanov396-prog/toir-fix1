package com.toir.service;

import com.toir.dto.meter.*;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.equipment.EquipmentMeter;
import com.toir.entity.equipment.MeterReading;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.enums.MeterType;
import com.toir.exception.RestException;
import com.toir.repository.MeterReadingRepository;
import com.toir.repository.equipment.EquipmentMeterRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.util.AuditBuilderService;
import com.toir.util.PaginationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MeterService {

    private final EquipmentMeterRepository meterRepository;
    private final MeterReadingRepository readingRepository;
    private final EquipmentRepository equipmentRepository;
    private final AuditBuilderService auditBuilderService;

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

        return MeterReadingDto.from(saved);
    }

    @Transactional(readOnly = true)
    public List<MeterReadingDto> history(UUID meterId, int limit) {
        getMeterOrThrow(meterId);
        int safeLimit = Math.min(Math.max(limit, 1), 1000);
        return readingRepository
                        .findAllByMeterIdAndIsDeletedFalseOrderByReadAtDesc(meterId, PaginationUtils.pageRequest(0, safeLimit))
                        .getContent()
                .stream().map(MeterReadingDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<MeterReadingDto> historyBetween(UUID meterId, Instant from, Instant to) {
        getMeterOrThrow(meterId);
        return readingRepository.findAllByMeterIdAndReadAtBetweenAndIsDeletedFalseOrderByReadAtAsc(meterId, from, to)
                .stream().map(MeterReadingDto::from).toList();
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
}
