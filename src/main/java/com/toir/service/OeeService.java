package com.toir.service;

import com.toir.dto.oee.OeeRecordDto;
import com.toir.dto.oee.OeeRecordRequest;
import com.toir.dto.oee.OeeSummary;
import com.toir.entity.OeeRecord;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.exception.RestException;
import com.toir.repository.OeeRecordRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.util.AuditBuilderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OeeService {

    private final OeeRecordRepository repository;
    private final EquipmentRepository equipmentRepository;
    private final AuditBuilderService auditBuilderService;


    @Transactional(readOnly = true)
    public List<OeeRecordDto> list() {
        return repository.findAllByIsDeletedFalseOrderByShiftStartDesc().stream()
                .map(OeeRecordDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OeeRecordDto> listByEquipment(UUID equipmentId) {
        return repository.findAllByEquipmentIdAndIsDeletedFalseOrderByShiftStartDesc(equipmentId).stream()
                .map(OeeRecordDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<OeeRecordDto> listBetween(UUID equipmentId, Instant from, Instant to) {
        return listBetween(equipmentId, null, from, to);
    }

    @Transactional(readOnly = true)
    public List<OeeRecordDto> listBetween(UUID equipmentId, String equipmentSearch, Instant from, Instant to) {
        List<UUID> resolvedEquipmentIds = resolveEquipmentIds(equipmentId, equipmentSearch);
        if (resolvedEquipmentIds != null && resolvedEquipmentIds.isEmpty()) {
            return List.of();
        }
        List<OeeRecord> records = equipmentId != null
                ? repository.findAllByEquipmentIdAndShiftStartBetweenAndIsDeletedFalseOrderByShiftStartAsc(equipmentId, from, to)
                : resolvedEquipmentIds != null
                        ? repository.findAllByEquipmentIdInAndShiftStartBetweenAndIsDeletedFalseOrderByShiftStartAsc(resolvedEquipmentIds, from, to)
                : repository.findAllByShiftStartBetweenAndIsDeletedFalse(from, to);
        return records.stream().map(OeeRecordDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<OeeRecordDto> list(String equipmentSearch) {
        List<UUID> resolvedEquipmentIds = resolveEquipmentIds(null, equipmentSearch);
        if (resolvedEquipmentIds == null || resolvedEquipmentIds.isEmpty()) {
            return List.of();
        }
        return repository.findAllByEquipmentIdInAndIsDeletedFalseOrderByShiftStartDesc(resolvedEquipmentIds).stream()
                .map(OeeRecordDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public OeeRecordDto findById(UUID id) {
        return OeeRecordDto.from(getOrThrow(id));
    }

    @Transactional
    public OeeRecordDto create(OeeRecordRequest r) {
        OeeRecord entity = new OeeRecord();
        apply(entity, r);
        OeeRecord saved = repository.save(entity);

        auditBuilderService.log(
                "oee_record",
                saved.getId().toString(),
                AuditAction.CREATE,
                AuditModule.OEE_RECORD,
                "Запись OEE создана",
                null,
                saved
        );

        return OeeRecordDto.from(saved);
    }

    @Transactional
    public OeeRecordDto update(UUID id, OeeRecordRequest r) {
        OeeRecord entity = getOrThrow(id);
        apply(entity, r);

        OeeRecord saved = repository.save(entity);
        auditBuilderService.log(
                "oee_record",
                saved.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.OEE_RECORD,
                "Запись OEE обновлена",
                entity,
                saved
        );

        return OeeRecordDto.from(entity);
    }

    @Transactional
    public void delete(UUID id) {
        var entity = getOrThrow(id);
        entity.setDeleted(true);
        OeeRecord saved = repository.save(entity);

        auditBuilderService.log(
                "oee_record",
                saved.getId().toString(),
                AuditAction.DELETE,
                AuditModule.OEE_RECORD,
                "Запись OEE удалена",
                saved,
                null
        );
    }

    public OeeSummary summary(UUID equipmentId, String equipmentSearch, Instant from, Instant to) {
        List<UUID> resolvedEquipmentIds = resolveEquipmentIds(equipmentId, equipmentSearch);
        UUID summaryEquipmentId = equipmentId;
        if (summaryEquipmentId == null && resolvedEquipmentIds != null && resolvedEquipmentIds.size() == 1) {
            summaryEquipmentId = resolvedEquipmentIds.get(0);
        }
        if (resolvedEquipmentIds != null && resolvedEquipmentIds.isEmpty()) {
            return new OeeSummary(summaryEquipmentId, from, to, 0, 0, 0, 0, 0);
        }
        List<OeeRecord> records = equipmentId != null
                ? repository.findAllByEquipmentIdAndShiftStartBetweenAndIsDeletedFalseOrderByShiftStartAsc(equipmentId, from, to)
                : resolvedEquipmentIds != null
                        ? repository.findAllByEquipmentIdInAndShiftStartBetweenAndIsDeletedFalseOrderByShiftStartAsc(resolvedEquipmentIds, from, to)
                        : repository.findAllByShiftStartBetweenAndIsDeletedFalse(from, to);

        if (records.isEmpty()) {
            return new OeeSummary(summaryEquipmentId, from, to, 0, 0, 0, 0, 0);
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

        return new OeeSummary(summaryEquipmentId, from, to, availability, performance, quality, oee, records.size());
    }

    public OeeSummary summary(UUID equipmentId, Instant from, Instant to) {
        return summary(equipmentId, null, from, to);
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

    private List<UUID> resolveEquipmentIds(UUID equipmentId, String equipmentSearch) {
        if (equipmentId != null) {
            return List.of(equipmentId);
        }
        if (equipmentSearch == null || equipmentSearch.isBlank()) {
            return null;
        }
        String searchPattern = "%" + equipmentSearch.trim().toLowerCase() + "%";
        return equipmentRepository.findIdsByBusinessSearch(searchPattern);
    }
}
