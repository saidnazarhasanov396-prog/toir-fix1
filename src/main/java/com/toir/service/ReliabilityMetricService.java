package com.toir.service;

import com.toir.dto.reliability.ReliabilityMetricDto;
import com.toir.entity.ReliabilityMetric;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.repository.ReliabilityMetricRepository;
import com.toir.util.AuditBuilderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReliabilityMetricService {

    private final ReliabilityMetricRepository repository;
    private final AuditBuilderService auditBuilderService;


    @Transactional(readOnly = true)
    public List<ReliabilityMetricDto> findByEquipment(UUID equipmentId) {
        return repository.findAllByEquipmentIdAndIsDeletedFalseOrderByMetricDateDesc(equipmentId).stream()
                .map(ReliabilityMetricDto::from).toList();
    }

    @Transactional
    public ReliabilityMetricDto record(ReliabilityMetricDto r) {
        ReliabilityMetric m = new ReliabilityMetric();
        m.setEquipmentId(r.equipmentId());
        m.setMetricDate(r.metricDate());
        m.setMtbfHours(r.mtbfHours());
        m.setMttrHours(r.mttrHours());
        m.setAvailability(r.availability());
        m.setFailureRate(r.failureRate());
        ReliabilityMetric saved = repository.save(m);
        auditBuilderService.log(
                "reliability_metric",
                saved.getId().toString(),
                AuditAction.CREATE,
                AuditModule.RELIABILITY_METRIC,
                "Метрика надежности создана",
                null,
                saved
        );

        return ReliabilityMetricDto.from(saved);
    }
}
