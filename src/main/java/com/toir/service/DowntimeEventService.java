package com.toir.service;
import com.toir.entity.DowntimeEvent;
import com.toir.repository.DowntimeEventRepository;

import com.toir.exception.RestException;
import com.toir.dto.downtime.DowntimeEventDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DowntimeEventService {

    private final DowntimeEventRepository repository;


    @Transactional(readOnly = true)
    public List<DowntimeEventDto> findByEquipment(UUID equipmentId) {
        return repository.findAllByEquipmentIdOrderByStartAtDesc(equipmentId).stream()
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
        return DowntimeEventDto.from(repository.save(e));
    }

    public DowntimeEventDto close(UUID id, Instant endAt) {
        DowntimeEvent e = repository.findById(id)
                .orElseThrow(() -> RestException.notFound("Downtime event not found: " + id));
        Instant finalEnd = endAt != null ? endAt : Instant.now();
        if (finalEnd.isBefore(e.getStartAt())) {
            throw RestException.badRequest("End must be after start");
        }
        e.setEndAt(finalEnd);
        e.setDurationMinutes((int) Duration.between(e.getStartAt(), finalEnd).toMinutes());
        return DowntimeEventDto.from(e);
    }
}
