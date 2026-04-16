package com.toir.maintenancekpi;

import com.toir.common.exception.RestException;
import com.toir.maintenancekpi.dto.MaintenanceKPIDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class MaintenanceKPIService {

    private final MaintenanceKPIRepository repository;

    public MaintenanceKPIService(MaintenanceKPIRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<MaintenanceKPIDto> findByDepartment(UUID departmentId) {
        return repository.findAllByDepartmentIdOrderByPeriodStartDesc(departmentId).stream()
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
        return MaintenanceKPIDto.from(repository.save(k));
    }

    public void delete(UUID id) {
        MaintenanceKPI k = repository.findById(id)
                .orElseThrow(() -> RestException.notFound("Maintenance KPI not found: " + id));
        repository.delete(k);
    }
}
