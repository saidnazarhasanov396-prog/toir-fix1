package com.toir.service;
import com.toir.entity.PlannedShutdown;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.repository.PlannedShutdownRepository;

import com.toir.exception.RestException;
import com.toir.dto.plannedshutdown.PlannedShutdownDto;
import lombok.RequiredArgsConstructor;
import com.toir.enums.PlanStatus;
import com.toir.util.AuditBuilderService;
import com.toir.util.AuditSerializationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class PlannedShutdownService {

    private final PlannedShutdownRepository repository;
    private final AuditBuilderService auditBuilderService;
    private final AuditSerializationService auditSerializationService;


    @Transactional(readOnly = true)
    public List<PlannedShutdownDto> findByDepartment(UUID departmentId) {
        return repository.findAllByDepartmentIdAndIsDeletedFalseOrderByStartAtDesc(departmentId).stream()
                .map(PlannedShutdownDto::from).toList();
    }

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
        audit(AuditAction.CREATE, saved.getId(), null, saved);
        return PlannedShutdownDto.from(saved);
    }

    public PlannedShutdownDto approve(UUID id) {
        PlannedShutdown s = repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Planned shutdown not found: " + id));
        String oldJson = auditSerializationService.toJson(s);
        s.setStatus(PlanStatus.APPROVED);
        audit(AuditAction.UPDATE, s.getId(), oldJson, s);
        return PlannedShutdownDto.from(s);
    }

    private void audit(AuditAction action, UUID id, String oldJson, PlannedShutdown current) {
        String newJson = current == null ? null : auditSerializationService.toJson(current);
        auditBuilderService.log(
                "planned_shutdown",
                id != null ? id.toString() : null,
                action,
                AuditModule.PLANNED_SHUTDOWN,
                auditMessage(action),
                oldJson,
                newJson
        );
    }

    private String auditMessage(AuditAction action) {
        return switch (action) {
            case CREATE -> "Плановая остановка создана";
            case UPDATE -> "Плановая остановка обновлена";
            case DELETE -> "Плановая остановка удалена";
            default -> "Действие выполнено над плановой остановкой";
        };
    }
}
