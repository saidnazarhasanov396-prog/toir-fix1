package com.toir.service;

import com.toir.dto.downtime.DowntimeEventDto;
import com.toir.entity.DowntimeEvent;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.exception.RestException;
import com.toir.repository.DowntimeEventRepository;
import com.toir.util.AuditBuilderService;
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


        auditBuilderService.log(
                "downtime_event",
                saved.getId().toString(),
                AuditAction.CREATE,
                AuditModule.DOWNTIME_EVENT,
                "Событие простоя создано",
                null,
                saved
        );


        return DowntimeEventDto.from(saved);
    }

    public DowntimeEventDto close(UUID id, Instant endAt) {
        DowntimeEvent e = repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Downtime event not found: " + id));
        Instant finalEnd = endAt != null ? endAt : Instant.now();
        if (finalEnd.isBefore(e.getStartAt())) {
            throw RestException.badRequest("End must be after start");
        }
        e.setEndAt(finalEnd);
        e.setDurationMinutes((int) Duration.between(e.getStartAt(), finalEnd).toMinutes());

        DowntimeEvent saved = repository.save(e);

        auditBuilderService.log(
                "downtime_event",
                saved.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.DOWNTIME_EVENT,
                "Событие простоя обновлено",
                e,
                saved
        );

        return DowntimeEventDto.from(e);
    }
}
