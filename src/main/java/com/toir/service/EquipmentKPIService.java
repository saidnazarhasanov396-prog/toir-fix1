package com.toir.service;
import com.toir.entity.Equipment;
import com.toir.entity.EquipmentKPI;
import com.toir.repository.EquipmentKPIRepository;

import com.toir.exception.RestException;
import com.toir.dto.equipmentkpi.EquipmentKPIDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class EquipmentKPIService {

    private final EquipmentKPIRepository repository;

    public EquipmentKPIService(EquipmentKPIRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<EquipmentKPIDto> findByEquipment(UUID equipmentId) {
        return repository.findAllByEquipmentIdOrderByPeriodStartDesc(equipmentId).stream()
                .map(EquipmentKPIDto::from).toList();
    }

    public EquipmentKPIDto record(EquipmentKPIDto r) {
        EquipmentKPI k = new EquipmentKPI();
        k.setEquipmentId(r.equipmentId());
        k.setPeriodStart(r.periodStart());
        k.setPeriodEnd(r.periodEnd());
        k.setOperatingHours(r.operatingHours());
        k.setDowntimeHours(r.downtimeHours());
        k.setFailureCount(r.failureCount());
        k.setRepairCount(r.repairCount());

        if (r.mtbfHours() != null) {
            k.setMtbfHours(r.mtbfHours());
        } else if (r.operatingHours() != null && r.failureCount() != null && r.failureCount() > 0) {
            k.setMtbfHours(r.operatingHours() / r.failureCount());
        }
        if (r.mttrHours() != null) {
            k.setMttrHours(r.mttrHours());
        } else if (r.downtimeHours() != null && r.repairCount() != null && r.repairCount() > 0) {
            k.setMttrHours(r.downtimeHours() / r.repairCount());
        }
        if (r.availability() != null) {
            k.setAvailability(r.availability());
        } else if (r.operatingHours() != null && r.downtimeHours() != null) {
            double total = r.operatingHours() + r.downtimeHours();
            if (total > 0) k.setAvailability(r.operatingHours() / total);
        }
        return EquipmentKPIDto.from(repository.save(k));
    }

    public void delete(UUID id) {
        EquipmentKPI k = repository.findById(id)
                .orElseThrow(() -> RestException.notFound("Equipment KPI not found: " + id));
        repository.delete(k);
    }
}
