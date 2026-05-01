package com.toir.service;
import com.toir.entity.Equipment;
import com.toir.entity.EquipmentMeter;
import com.toir.entity.MeterReading;
import com.toir.repository.EquipmentMeterRepository;
import com.toir.repository.EquipmentRepository;
import com.toir.repository.MeterReadingRepository;

import com.toir.exception.RestException;
import com.toir.dto.meter.EquipmentMeterDto;
import com.toir.dto.meter.EquipmentMeterRequest;
import com.toir.dto.meter.MeterReadingDto;
import com.toir.dto.meter.MeterReadingRequest;
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

    @Transactional(readOnly = true)
    public List<EquipmentMeterDto> listByEquipment(UUID equipmentId) {
        return com.toir.util.UpdatedAtSorter.descending(meterRepository.findAllByEquipmentIdAndIsDeletedFalse(equipmentId)).stream()
                .map(this::enrichWithEquipmentName)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<EquipmentMeterDto> listAll() {
        return com.toir.util.UpdatedAtSorter.descending(meterRepository.findAllByIsDeletedFalse()).stream()
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
        return enrichWithEquipmentName(meterRepository.save(meter));
    }

    public EquipmentMeterDto updateMeter(UUID id, EquipmentMeterRequest request) {
        EquipmentMeter meter = getMeterOrThrow(id);
        meter.setEquipmentId(request.equipmentId());
        meter.setMeterType(request.meterType());
        meter.setName(request.name());
        meter.setUnit(request.unit());
        meter.setRolloverValue(request.rolloverValue());
        if (request.active() != null) meter.setActive(request.active());
        meterRepository.save(meter);
        return enrichWithEquipmentName(meter);
    }

    public void deleteMeter(UUID id) {
        var entity = getMeterOrThrow(id);
        entity.setDeleted(true);
        meterRepository.save(entity);
    }

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

        return MeterReadingDto.from(saved);
    }

    @Transactional(readOnly = true)
    public List<MeterReadingDto> history(UUID meterId, int limit) {
        getMeterOrThrow(meterId);
        int safeLimit = Math.min(Math.max(limit, 1), 1000);
        return com.toir.util.UpdatedAtSorter.descending(readingRepository
                        .findAllByMeterIdAndIsDeletedFalseOrderByReadAtDesc(meterId, PaginationUtils.updatedAtDescPageRequest(0, safeLimit))
                        .getContent())
                .stream().map(MeterReadingDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<MeterReadingDto> historyBetween(UUID meterId, Instant from, Instant to) {
        getMeterOrThrow(meterId);
        return com.toir.util.UpdatedAtSorter.descending(readingRepository.findAllByMeterIdAndReadAtBetweenAndIsDeletedFalseOrderByReadAtAsc(meterId, from, to))
                .stream().map(MeterReadingDto::from).toList();
    }

    public void deleteReading(UUID readingId) {
        MeterReading reading = readingRepository.findByIdAndIsDeletedFalse(readingId)
                .orElseThrow(() -> RestException.notFound("Meter reading not found: " + readingId));
        reading.setDeleted(true);
        readingRepository.save(reading);
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
