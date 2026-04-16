package com.toir.meter;

import com.toir.common.exception.RestException;
import com.toir.meter.dto.EquipmentMeterDto;
import com.toir.meter.dto.EquipmentMeterRequest;
import com.toir.meter.dto.MeterReadingDto;
import com.toir.meter.dto.MeterReadingRequest;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class MeterService {

    private final EquipmentMeterRepository meterRepository;
    private final MeterReadingRepository readingRepository;

    public MeterService(EquipmentMeterRepository meterRepository,
                        MeterReadingRepository readingRepository) {
        this.meterRepository = meterRepository;
        this.readingRepository = readingRepository;
    }

    @Transactional(readOnly = true)
    public List<EquipmentMeterDto> listByEquipment(UUID equipmentId) {
        return meterRepository.findAllByEquipmentId(equipmentId).stream()
                .map(EquipmentMeterDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<EquipmentMeterDto> listAll() {
        return meterRepository.findAll().stream().map(EquipmentMeterDto::from).toList();
    }

    @Transactional(readOnly = true)
    public EquipmentMeterDto findMeter(UUID id) {
        return EquipmentMeterDto.from(getMeterOrThrow(id));
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
        return EquipmentMeterDto.from(meterRepository.save(meter));
    }

    public EquipmentMeterDto updateMeter(UUID id, EquipmentMeterRequest request) {
        EquipmentMeter meter = getMeterOrThrow(id);
        meter.setEquipmentId(request.equipmentId());
        meter.setMeterType(request.meterType());
        meter.setName(request.name());
        meter.setUnit(request.unit());
        meter.setRolloverValue(request.rolloverValue());
        if (request.active() != null) meter.setActive(request.active());
        return EquipmentMeterDto.from(meter);
    }

    public void deleteMeter(UUID id) {
        meterRepository.delete(getMeterOrThrow(id));
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
        return readingRepository
                .findAllByMeterIdOrderByReadAtDesc(meterId, PageRequest.of(0, safeLimit))
                .getContent().stream().map(MeterReadingDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<MeterReadingDto> historyBetween(UUID meterId, Instant from, Instant to) {
        getMeterOrThrow(meterId);
        return readingRepository.findAllByMeterIdAndReadAtBetweenOrderByReadAtAsc(meterId, from, to)
                .stream().map(MeterReadingDto::from).toList();
    }

    public void deleteReading(UUID readingId) {
        MeterReading reading = readingRepository.findById(readingId)
                .orElseThrow(() -> RestException.notFound("Meter reading not found: " + readingId));
        readingRepository.delete(reading);
    }

    private EquipmentMeter getMeterOrThrow(UUID id) {
        return meterRepository.findById(id)
                .orElseThrow(() -> RestException.notFound("Equipment meter not found: " + id));
    }
}
