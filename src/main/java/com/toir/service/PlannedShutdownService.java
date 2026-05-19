package com.toir.service;

import com.toir.dto.plannedshutdown.PlannedShutdownDto;
import com.toir.entity.PlannedShutdown;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.enums.PlanStatus;
import com.toir.exception.RestException;
import com.toir.repository.PlannedShutdownRepository;
import com.toir.util.AuditBuilderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PlannedShutdownService {

    private final PlannedShutdownRepository repository;
    private final AuditBuilderService auditBuilderService;


    @Transactional(readOnly = true)
    public List<PlannedShutdownDto> findAllFiltered(UUID departmentId, PlanStatus status, String search) {
        String statusStr = status != null ? status.name() : null;
        String searchPattern = (search != null && !search.isBlank()) ? "%" + search.trim().toLowerCase() + "%" : null;
        return repository.findAllFiltered(departmentId, statusStr, searchPattern).stream()
                .map(PlannedShutdownDto::from).toList();
    }

    @Transactional
    public PlannedShutdownDto create(PlannedShutdownDto r) {
        if (!r.endAt().isAfter(r.startAt())) {
            throw RestException.badRequest("End must be after start");
        }
        PlannedShutdown s = new PlannedShutdown();
        s.setName(r.name());
        s.setDepartmentId(r.departmentId());
        s.setStartAt(r.startAt());
        s.setEndAt(r.endAt());
        s.setReason(r.reason());
        PlannedShutdown saved = repository.save(s);

        auditBuilderService.log(
                "planned_shutdown",
                saved.getId().toString(),
                AuditAction.CREATE,
                AuditModule.PLANNED_SHUTDOWN,
                "Плановая остановка создана",
                null,
                saved
        );

        return PlannedShutdownDto.from(saved);
    }

    @Transactional
    public PlannedShutdownDto approve(UUID id) {
        PlannedShutdown s = repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Planned shutdown not found: " + id));
        s.setStatus(PlanStatus.APPROVED);

        PlannedShutdown saved = repository.save(s);

        auditBuilderService.log(
                "planned_shutdown",
                saved.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.PLANNED_SHUTDOWN,
                "Плановая остановка обновлена",
                s,
                saved
        );

        return PlannedShutdownDto.from(s);
    }
}
