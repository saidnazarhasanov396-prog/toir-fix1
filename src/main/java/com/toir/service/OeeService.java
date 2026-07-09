package com.toir.service;

import com.toir.dto.oee.OeeFilter;
import com.toir.dto.oee.OeeRecordDto;
import com.toir.dto.oee.OeeRecordRequest;
import com.toir.dto.oee.OeeSummary;
import com.toir.entity.OeeRecord;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.exception.RestException;
import com.toir.repository.OeeRecordRepository;
import com.toir.service.oee.OeeMetrics;
import com.toir.service.oee.OeeMetricsCalculator;
import com.toir.util.AuditBuilderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OeeService {

    private final OeeRecordRepository repository;
    private final AuditBuilderService auditBuilderService;
    private final OeeMetricsCalculator metricsCalculator;

    @Transactional(readOnly = true)
    public List<OeeRecordDto> list() {
        return list(defaultFilter());
    }

    @Transactional(readOnly = true)
    public List<OeeRecordDto> list(OeeFilter filter) {
        return filteredRecords(filter).stream()
                .map(record -> OeeRecordDto.from(record, metricsCalculator.calculate(record)))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OeeRecordDto> listByEquipment(UUID equipmentId) {
        return list(new OeeFilter(equipmentId, null, null, null, null, null, null, null, null, null, null, null, "shiftStart", "desc"));
    }

    @Transactional(readOnly = true)
    public List<OeeRecordDto> listBetween(UUID equipmentId, Instant from, Instant to) {
        return listBetween(equipmentId, null, from, to);
    }

    @Transactional(readOnly = true)
    public List<OeeRecordDto> listBetween(UUID equipmentId, String equipmentSearch, Instant from, Instant to) {
        return list(new OeeFilter(equipmentId, equipmentSearch, null, null, from, to, null, null, null, null, null, null, "shiftStart", "asc"));
    }

    @Transactional(readOnly = true)
    public List<OeeRecordDto> list(String equipmentSearch) {
        return list(new OeeFilter(null, equipmentSearch, null, null, null, null, null, null, null, null, null, null, "shiftStart", "desc"));
    }

    @Transactional(readOnly = true)
    public OeeRecordDto findById(UUID id) {
        OeeRecord record = getOrThrow(id);
        return OeeRecordDto.from(record, metricsCalculator.calculate(record));
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

        return OeeRecordDto.from(saved, metricsCalculator.calculate(saved));
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

        return OeeRecordDto.from(saved, metricsCalculator.calculate(saved));
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

    @Transactional(readOnly = true)
    public OeeSummary summary(OeeFilter filter) {
        List<OeeRecord> records = filteredRecords(filter);
        if (records.isEmpty()) {
            return new OeeSummary(filter.equipmentId(), filter.from(), filter.to(), 0, 0, 0, 0, 0);
        }

        OeeMetrics metrics = metricsCalculator.aggregate(records);
        return new OeeSummary(
                filter.equipmentId(),
                filter.from(),
                filter.to(),
                metrics.availability(),
                metrics.performance(),
                metrics.quality(),
                metrics.oee(),
                records.size()
        );
    }

    public OeeSummary summary(UUID equipmentId, String equipmentSearch, Instant from, Instant to) {
        return summary(new OeeFilter(equipmentId, equipmentSearch, null, null, from, to, null, null, null, null, null, null, "shiftStart", "desc"));
    }

    public OeeSummary summary(UUID equipmentId, Instant from, Instant to) {
        return summary(equipmentId, null, from, to);
    }

    private List<OeeRecord> filteredRecords(OeeFilter filter) {
        List<OeeRecord> records = repository.search(
                filter.equipmentId(),
                searchPattern(filter.equipmentSearch()),
                filter.departmentId(),
                filter.equipmentTypeId(),
                filter.from(),
                filter.to()
        );
        Comparator<OeeRecord> comparator = comparator(filter.sortBy(), filter.sortDir());
        return records.stream()
                .filter(record -> metricRangeMatches(metricsCalculator.calculate(record), filter))
                .sorted(comparator)
                .toList();
    }

    private boolean metricRangeMatches(OeeMetrics metrics, OeeFilter filter) {
        return inRange(metrics.oee(), filter.minOee(), filter.maxOee())
                && inRange(metrics.availability(), filter.minAvailability(), filter.maxAvailability())
                && inRange(metrics.quality(), filter.minQuality(), filter.maxQuality());
    }

    private boolean inRange(double value, Double min, Double max) {
        return (min == null || value >= min) && (max == null || value <= max);
    }

    private Comparator<OeeRecord> comparator(String sortBy, String sortDir) {
        Comparator<OeeRecord> comparator = switch (sortBy == null ? "shiftStart" : sortBy) {
            case "availability" -> Comparator.comparingDouble(r -> metricsCalculator.calculate(r).availability());
            case "performance" -> Comparator.comparingDouble(r -> metricsCalculator.calculate(r).performance());
            case "quality" -> Comparator.comparingDouble(r -> metricsCalculator.calculate(r).quality());
            case "oee" -> Comparator.comparingDouble(r -> metricsCalculator.calculate(r).oee());
            default -> Comparator.comparing(OeeRecord::getShiftStart, Comparator.nullsLast(Comparator.naturalOrder()));
        };
        return "asc".equalsIgnoreCase(sortDir) ? comparator : comparator.reversed();
    }

    private String searchPattern(String value) {
        if (value == null || value.isBlank()) return null;
        return "%" + value.trim().toLowerCase() + "%";
    }

    private OeeFilter defaultFilter() {
        return new OeeFilter(null, null, null, null, null, null, null, null, null, null, null, null, "shiftStart", "desc");
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

        OeeMetrics metrics = metricsCalculator.calculate(entity);
        entity.setAvailability(metrics.availability());
        entity.setPerformance(metrics.performance());
        entity.setQuality(metrics.quality());
        entity.setOee(metrics.oee());
    }
}
