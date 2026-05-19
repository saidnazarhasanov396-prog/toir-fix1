package com.toir.service.maintanance;

import com.toir.dto.maintenancekpi.MaintenanceKPIDto;
import com.toir.entity.maintenance.MaintenanceKPI;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.exception.RestException;
import com.toir.repository.maintenance.MaintenanceKPIRepository;
import com.toir.util.AuditBuilderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MaintenanceKPIService {

    private final MaintenanceKPIRepository repository;
    private final AuditBuilderService auditBuilderService;


    @Transactional(readOnly = true)
    public List<MaintenanceKPIDto> findByDepartment(UUID departmentId) {
        return repository.findAllByDepartmentIdAndIsDeletedFalseOrderByPeriodStartDesc(departmentId).stream()
                .map(MaintenanceKPIDto::from).toList();
    }

    @Transactional
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

        auditBuilderService.log(
                "maintenance_kpi",
                saved.getId().toString(),
                AuditAction.CREATE,
                AuditModule.MAINTENANCE_KPI,
                "KPI обслуживания создан",
                null,
                saved
        );
        return MaintenanceKPIDto.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        MaintenanceKPI k = repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Maintenance KPI not found: " + id));
        k.setDeleted(true);
        MaintenanceKPI saved = repository.save(k);

        auditBuilderService.log(
                "maintenance_kpi",
                id != null ? id.toString() : null,
                AuditAction.DELETE,
                AuditModule.MAINTENANCE_KPI,
                "KPI обслуживания удален",
                saved,
                null
        );
    }
}
