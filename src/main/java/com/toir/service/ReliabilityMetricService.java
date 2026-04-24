package com.toir.service;
import com.toir.entity.ReliabilityMetric;
import com.toir.repository.ReliabilityMetricRepository;

import com.toir.dto.reliability.ReliabilityMetricDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReliabilityMetricService {

    private final ReliabilityMetricRepository repository;


    @Transactional(readOnly = true)
    public List<ReliabilityMetricDto> findByEquipment(UUID equipmentId) {
        return repository.findAllByEquipmentIdOrderByMetricDateDesc(equipmentId).stream()
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
        return ReliabilityMetricDto.from(repository.save(m));
    }
}
