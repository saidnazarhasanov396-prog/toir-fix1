package com.toir.service.equipment;
import com.toir.entity.equipment.EquipmentKPI;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.repository.equipment.EquipmentKPIRepository;

import com.toir.exception.RestException;
import com.toir.dto.equipmentkpi.EquipmentKPIDto;
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
public class EquipmentKPIService {

    private final EquipmentKPIRepository repository;
    private final AuditBuilderService auditBuilderService;
    private final AuditSerializationService auditSerializationService;


    @Transactional(readOnly = true)
    public List<EquipmentKPIDto> findByEquipment(UUID equipmentId) {
        return repository.findAllByEquipmentIdAndIsDeletedFalseOrderByPeriodStartDesc(equipmentId).stream()
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
        EquipmentKPI saved = repository.save(k);
        audit(AuditAction.CREATE, saved.getId(), null, saved);
        return EquipmentKPIDto.from(saved);
    }

    public void delete(UUID id) {
        EquipmentKPI k = repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Equipment KPI not found: " + id));
        String oldJson = auditSerializationService.toJson(k);
        k.setDeleted(true);
        EquipmentKPI saved = repository.save(k);
        audit(AuditAction.DELETE, saved.getId(), oldJson, null);
    }

    private void audit(AuditAction action, UUID id, String oldJson, EquipmentKPI current) {
        String newJson = current == null ? null : auditSerializationService.toJson(current);
        auditBuilderService.log(
                "equipment_kpi",
                id != null ? id.toString() : null,
                action,
                AuditModule.EQUIPMENT_KPI,
                auditMessage(action),
                oldJson,
                newJson
        );
    }

    private String auditMessage(AuditAction action) {
        return switch (action) {
            case CREATE -> "KPI оборудования создан";
            case UPDATE -> "KPI оборудования обновлен";
            case DELETE -> "KPI оборудования удален";
            default -> "Действие выполнено над KPI оборудования";
        };
    }
}
