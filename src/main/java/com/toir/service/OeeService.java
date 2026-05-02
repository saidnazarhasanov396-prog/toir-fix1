package com.toir.service;
import com.toir.entity.OeeRecord;
import com.toir.repository.OeeRecordRepository;

import com.toir.dto.oee.OeeSummary;

import com.toir.exception.RestException;
import com.toir.dto.oee.OeeRecordDto;
import com.toir.dto.oee.OeeRecordRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class OeeService {

    private final OeeRecordRepository repository;


    @Transactional(readOnly = true)
    public List<OeeRecordDto> listByEquipment(UUID equipmentId) {
        return repository.findAllByEquipmentIdAndIsDeletedFalseOrderByShiftStartDesc(equipmentId).stream()
                .map(OeeRecordDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<OeeRecordDto> listBetween(UUID equipmentId, Instant from, Instant to) {
        List<OeeRecord> records = equipmentId != null
                ? repository.findAllByEquipmentIdAndShiftStartBetweenAndIsDeletedFalseOrderByShiftStartAsc(equipmentId, from, to)
                : repository.findAllByShiftStartBetweenAndIsDeletedFalse(from, to);
        return records.stream().map(OeeRecordDto::from).toList();
    }

    @Transactional(readOnly = true)
    public OeeRecordDto findById(UUID id) {
        return OeeRecordDto.from(getOrThrow(id));
    }

    public OeeRecordDto create(OeeRecordRequest r) {
        OeeRecord entity = new OeeRecord();
        apply(entity, r);
        return OeeRecordDto.from(repository.save(entity));
    }

    public OeeRecordDto update(UUID id, OeeRecordRequest r) {
        OeeRecord entity = getOrThrow(id);
        apply(entity, r);
        return OeeRecordDto.from(entity);
    }

    public void delete(UUID id) {
        var entity = getOrThrow(id);
        entity.setDeleted(true);
        repository.save(entity);
    }

    public OeeSummary summary(UUID equipmentId, Instant from, Instant to) {
        List<OeeRecord> records = equipmentId != null
                ? repository.findAllByEquipmentIdAndShiftStartBetweenAndIsDeletedFalseOrderByShiftStartAsc(equipmentId, from, to)
                : repository.findAllByShiftStartBetweenAndIsDeletedFalse(from, to);

        if (records.isEmpty()) {
            return new OeeSummary(equipmentId, from, to, 0, 0, 0, 0, 0);
        }

        double totalPlanned = 0, totalRun = 0, totalIdeal = 0, totalCount = 0, totalGood = 0;
        for (OeeRecord r : records) {
            totalPlanned += r.getPlannedProductionMinutes();
            totalRun += r.getRunMinutes();
            totalIdeal += r.getIdealCycleSeconds() * r.getTotalCount() / 60.0;
            totalCount += r.getTotalCount();
            totalGood += r.getGoodCount();
        }

        double availability = totalPlanned > 0 ? totalRun / totalPlanned : 0;
        double performance = totalRun > 0 ? totalIdeal / totalRun : 0;
        double quality = totalCount > 0 ? totalGood / totalCount : 0;
        double oee = availability * performance * quality;

        return new OeeSummary(equipmentId, from, to, availability, performance, quality, oee, records.size());
    }


    private OeeRecord getOrThrow(UUID id) {
        return repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("OEE record not found: " + id));
    }

    private void apply(OeeRecord entity, OeeRecordRequest r) {
        if (r.shiftEnd().isBefore(r.shiftStart())) {
            throw RestException.conflict("shiftEnd must be after shiftStart");
        }
        if (r.goodCount() > r.totalCount()) {
            throw RestException.conflict("goodCount cannot exceed totalCount");
        }
        entity.setEquipmentId(r.equipmentId());
        entity.setShiftStart(r.shiftStart());
        entity.setShiftEnd(r.shiftEnd());
        entity.setPlannedProductionMinutes(r.plannedProductionMinutes());
        entity.setRunMinutes(r.runMinutes());
        entity.setIdealCycleSeconds(r.idealCycleSeconds());
        entity.setTotalCount(r.totalCount());
        entity.setGoodCount(r.goodCount());
        entity.setNotes(r.notes());

        double availability = r.plannedProductionMinutes() > 0
                ? r.runMinutes() / r.plannedProductionMinutes() : 0;
        double idealTimeMin = r.idealCycleSeconds() * r.totalCount() / 60.0;
        double performance = r.runMinutes() > 0 ? idealTimeMin / r.runMinutes() : 0;
        double quality = r.totalCount() > 0 ? r.goodCount() / r.totalCount() : 0;
        entity.setAvailability(availability);
        entity.setPerformance(performance);
        entity.setQuality(quality);
        entity.setOee(availability * performance * quality);
    }
}
