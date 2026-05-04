package com.toir.service;
import com.toir.entity.DowntimeEvent;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.repository.DowntimeEventRepository;

import com.toir.exception.RestException;
import com.toir.dto.downtime.DowntimeEventDto;
import com.toir.util.AuditBuilderService;
import com.toir.util.AuditSerializationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class DowntimeEventService {

    private final DowntimeEventRepository repository;
    private final AuditBuilderService auditBuilderService;
    private final AuditSerializationService auditSerializationService;


    @Transactional(readOnly = true)
    public List<DowntimeEventDto> findByEquipment(UUID equipmentId) {
        return repository.findAllByEquipmentIdAndIsDeletedFalseOrderByStartAtDesc(equipmentId).stream()
                .map(DowntimeEventDto::from).toList();
    }

    public DowntimeEventDto register(DowntimeEventDto r) {
        DowntimeEvent e = new DowntimeEvent();
        e.setEquipmentId(r.equipmentId());
        e.setDepartmentId(r.departmentId());
        e.setWorkOrderId(r.workOrderId());
        e.setStartAt(r.startAt());
        e.setType(r.type());
        e.setDescription(r.description());
        DowntimeEvent saved = repository.save(e);
        audit(AuditAction.CREATE, saved.getId(), null, saved);
        return DowntimeEventDto.from(saved);
    }

    public DowntimeEventDto close(UUID id, Instant endAt) {
        DowntimeEvent e = repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Downtime event not found: " + id));
        Instant finalEnd = endAt != null ? endAt : Instant.now();
        if (finalEnd.isBefore(e.getStartAt())) {
            throw RestException.badRequest("End must be after start");
        }
        String oldJson = auditSerializationService.toJson(e);
        e.setEndAt(finalEnd);
        e.setDurationMinutes((int) Duration.between(e.getStartAt(), finalEnd).toMinutes());
        audit(AuditAction.UPDATE, e.getId(), oldJson, e);
        return DowntimeEventDto.from(e);
    }

    private void audit(AuditAction action, UUID id, String oldJson, DowntimeEvent current) {
        String newJson = current == null ? null : auditSerializationService.toJson(current);
        auditBuilderService.log(
                "downtime_event",
                id != null ? id.toString() : null,
                action,
                AuditModule.DOWNTIME_EVENT,
                auditMessage(action),
                oldJson,
                newJson
        );
    }

    private String auditMessage(AuditAction action) {
        return switch (action) {
            case CREATE -> "Событие простоя создано";
            case UPDATE -> "Событие простоя обновлено";
            case DELETE -> "Событие простоя удалено";
            default -> "Действие выполнено над событием простоя";
        };
    }
}
