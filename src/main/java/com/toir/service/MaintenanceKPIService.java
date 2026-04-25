package com.toir.service;
import com.toir.entity.MaintenanceKPI;
import com.toir.repository.MaintenanceKPIRepository;

import com.toir.exception.RestException;
import com.toir.dto.maintenancekpi.MaintenanceKPIDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MaintenanceKPIService {

    private final MaintenanceKPIRepository repository;


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
        return MaintenanceKPIDto.from(repository.save(k));
    }

    public void delete(UUID id) {
        MaintenanceKPI k = repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Maintenance KPI not found: " + id));
        k.setDeleted(true);
        repository.save(k);
    }
}
