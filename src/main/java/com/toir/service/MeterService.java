package com.toir.service;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.equipment.EquipmentMeter;
import com.toir.entity.equipment.MeterReading;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.enums.MeterType;
import com.toir.repository.equipment.EquipmentMeterRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.MeterReadingRepository;

import com.toir.exception.RestException;
import com.toir.dto.meter.EquipmentMeterDto;
import com.toir.dto.meter.EquipmentMeterRequest;
import com.toir.dto.meter.MeterReadingDto;
import com.toir.dto.meter.MeterReadingRequest;
import com.toir.util.AuditBuilderService;
import com.toir.util.AuditSerializationService;
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
    private final AuditSerializationService auditSerializationService;

    @Transactional(readOnly = true)
    public List<EquipmentMeterDto> listByEquipment(UUID equipmentId) {
        return meterRepository.findAllByEquipmentIdAndIsDeletedFalse(equipmentId).stream()
                .map(this::enrichWithEquipmentName)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<EquipmentMeterDto> listAll(String search, MeterType meterType) {
        String meterTypeStr = meterType == null ? null : meterType.toString();
        return meterRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc(search,meterTypeStr).stream()
                .map(this::enrichWithEquipmentName)
                .toList();
    }

    @Transactional(readOnly = true)
    public EquipmentMeterDto findMeter(UUID id) {
        return enrichWithEquipmentName(getMeterOrThrow(id));
    }

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
        auditMeter(AuditAction.CREATE, saved.getId(), null, saved);
        return enrichWithEquipmentName(saved);
    }

    public EquipmentMeterDto updateMeter(UUID id, EquipmentMeterRequest request) {
        EquipmentMeter meter = getMeterOrThrow(id);
        String oldJson = auditSerializationService.toJson(meter);
        meter.setEquipmentId(request.equipmentId());
        meter.setMeterType(request.meterType());
        meter.setName(request.name());
        meter.setUnit(request.unit());
        meter.setRolloverValue(request.rolloverValue());
        if (request.active() != null) meter.setActive(request.active());
        meterRepository.save(meter);
        auditMeter(AuditAction.UPDATE, meter.getId(), oldJson, meter);
        return enrichWithEquipmentName(meter);
    }

    public void deleteMeter(UUID id) {
        var entity = getMeterOrThrow(id);
        String oldJson = auditSerializationService.toJson(entity);
        entity.setDeleted(true);
        EquipmentMeter saved = meterRepository.save(entity);
        auditMeter(AuditAction.DELETE, saved.getId(), oldJson, null);
    }

    public MeterReadingDto addReading(MeterReadingRequest request) {
        EquipmentMeter meter = getMeterOrThrow(request.meterId());
        if (!meter.isActive()) {
            throw RestException.conflict("Meter is not active: " + meter.getId());
        }
        String oldMeterJson = auditSerializationService.toJson(meter);
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
        auditReading(AuditAction.CREATE, saved.getId(), null, saved);
        auditMeter(AuditAction.UPDATE, meter.getId(), oldMeterJson, meter);

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

    public void deleteReading(UUID readingId) {
        MeterReading reading = readingRepository.findByIdAndIsDeletedFalse(readingId)
                .orElseThrow(() -> RestException.notFound("Meter reading not found: " + readingId));
        String oldJson = auditSerializationService.toJson(reading);
        reading.setDeleted(true);
        MeterReading saved = readingRepository.save(reading);
        auditReading(AuditAction.DELETE, saved.getId(), oldJson, null);
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

    private void auditMeter(AuditAction action, UUID id, String oldJson, EquipmentMeter current) {
        String newJson = current == null ? null : auditSerializationService.toJson(current);
        auditBuilderService.log(
                "equipment_meter",
                id != null ? id.toString() : null,
                action,
                AuditModule.EQUIPMENT_METER,
                auditMeterMessage(action),
                oldJson,
                newJson
        );
    }

    private void auditReading(AuditAction action, UUID id, String oldJson, MeterReading current) {
        String newJson = current == null ? null : auditSerializationService.toJson(current);
        auditBuilderService.log(
                "meter_reading",
                id != null ? id.toString() : null,
                action,
                AuditModule.METER_READING,
                auditReadingMessage(action),
                oldJson,
                newJson
        );
    }

    private String auditMeterMessage(AuditAction action) {
        return switch (action) {
            case CREATE -> "Счетчик оборудования создан";
            case UPDATE -> "Счетчик оборудования обновлен";
            case DELETE -> "Счетчик оборудования удален";
            default -> "Действие выполнено над счетчиком оборудования";
        };
    }

    private String auditReadingMessage(AuditAction action) {
        return switch (action) {
            case CREATE -> "Показание счетчика создано";
            case UPDATE -> "Показание счетчика обновлено";
            case DELETE -> "Показание счетчика удалено";
            default -> "Действие выполнено над показанием счетчика";
        };
    }
}
