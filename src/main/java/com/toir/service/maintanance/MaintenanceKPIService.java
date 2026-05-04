package com.toir.service.maintanance;
import com.toir.entity.maintenance.MaintenanceKPI;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.repository.maintenance.MaintenanceKPIRepository;

import com.toir.exception.RestException;
import com.toir.dto.maintenancekpi.MaintenanceKPIDto;
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
public class MaintenanceKPIService {

    private final MaintenanceKPIRepository repository;
    private final AuditBuilderService auditBuilderService;
    private final AuditSerializationService auditSerializationService;


    @Transactional(readOnly = true)
    public List<MaintenanceKPIDto> findByDepartment(UUID departmentId) {
        return repository.findAllByDepartmentIdAndIsDeletedFalseOrderByPeriodStartDesc(departmentId).stream()
                .map(MaintenanceKPIDto::from).toList();
    }

    public MaintenanceKPIDto record(MaintenanceKPIDto r) {
        MaintenanceKPI k = new MaintenanceKPI();
        k.setDepartmentId(r.departmentId());
        k.setPeriodStart(r.periodStart());
        k.setPeriodEnd(r.periodEnd());
        k.setPprPlannedCount(r.pprPlannedCount());
        k.setPprCompletedCount(r.pprCompletedCount());
        if (r.pprCompletionRate() != null) {
            k.setPprCompletionRate(r.pprCompletionRate());
        } else if (r.pprPlannedCount() != null && r.pprPlannedCount() > 0 && r.pprCompletedCount() != null) {
            k.setPprCompletionRate((double) r.pprCompletedCount() / r.pprPlannedCount());
        }
        k.setUnplannedRepairShare(r.unplannedRepairShare());
        k.setAverageRepairDurationHours(r.averageRepairDurationHours());
        k.setTotalCost(r.totalCost());
        MaintenanceKPI saved = repository.save(k);
        audit(AuditAction.CREATE, saved.getId(), null, saved);
        return MaintenanceKPIDto.from(saved);
    }

    public void delete(UUID id) {
        MaintenanceKPI k = repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Maintenance KPI not found: " + id));
        String oldJson = auditSerializationService.toJson(k);
        k.setDeleted(true);
        MaintenanceKPI saved = repository.save(k);
        audit(AuditAction.DELETE, saved.getId(), oldJson, null);
    }

    private void audit(AuditAction action, UUID id, String oldJson, MaintenanceKPI current) {
        String newJson = current == null ? null : auditSerializationService.toJson(current);
        auditBuilderService.log(
                "maintenance_kpi",
                id != null ? id.toString() : null,
                action,
                AuditModule.MAINTENANCE_KPI,
                auditMessage(action),
                oldJson,
                newJson
        );
    }

    private String auditMessage(AuditAction action) {
        return switch (action) {
            case CREATE -> "KPI обслуживания создан";
            case UPDATE -> "KPI обслуживания обновлен";
            case DELETE -> "KPI обслуживания удален";
            default -> "Действие выполнено над KPI обслуживания";
        };
    }
}
