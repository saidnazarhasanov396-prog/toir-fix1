package com.toir.service;
import com.toir.entity.ReliabilityMetric;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.repository.ReliabilityMetricRepository;

import com.toir.dto.reliability.ReliabilityMetricDto;
import com.toir.util.AuditBuilderService;
import com.toir.util.AuditSerializationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class ReliabilityMetricService {

    private final ReliabilityMetricRepository repository;
    private final AuditBuilderService auditBuilderService;
    private final AuditSerializationService auditSerializationService;


    @Transactional(readOnly = true)
    public List<ReliabilityMetricDto> findByEquipment(UUID equipmentId) {
        return repository.findAllByEquipmentIdAndIsDeletedFalseOrderByMetricDateDesc(equipmentId).stream()
                .map(ReliabilityMetricDto::from).toList();
    }

    public ReliabilityMetricDto record(ReliabilityMetricDto r) {
        ReliabilityMetric m = new ReliabilityMetric();
        m.setEquipmentId(r.equipmentId());
        m.setMetricDate(r.metricDate());
        m.setMtbfHours(r.mtbfHours());
        m.setMttrHours(r.mttrHours());
        m.setAvailability(r.availability());
        m.setFailureRate(r.failureRate());
        ReliabilityMetric saved = repository.save(m);
        audit(AuditAction.CREATE, saved.getId(), null, saved);
        return ReliabilityMetricDto.from(saved);
    }

    private void audit(AuditAction action, UUID id, String oldJson, ReliabilityMetric current) {
        String newJson = current == null ? null : auditSerializationService.toJson(current);
        auditBuilderService.log(
                "reliability_metric",
                id != null ? id.toString() : null,
                action,
                AuditModule.RELIABILITY_METRIC,
                auditMessage(action),
                oldJson,
                newJson
        );
    }

    private String auditMessage(AuditAction action) {
        return switch (action) {
            case CREATE -> "Метрика надежности создана";
            case UPDATE -> "Метрика надежности обновлена";
            case DELETE -> "Метрика надежности удалена";
            default -> "Действие выполнено над метрикой надежности";
        };
    }
}
