package com.toir.service;
import com.toir.entity.PlannedShutdown;
import com.toir.repository.PlannedShutdownRepository;

import com.toir.exception.RestException;
import com.toir.dto.plannedshutdown.PlannedShutdownDto;
import lombok.RequiredArgsConstructor;
import com.toir.enums.PlanStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PlannedShutdownService {

    private final PlannedShutdownRepository repository;


    @Transactional(readOnly = true)
    public List<PlannedShutdownDto> findByDepartment(UUID departmentId) {
        return repository.findAllByDepartmentIdOrderByStartAtDesc(departmentId).stream()
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
        return PlannedShutdownDto.from(repository.save(s));
    }

    public PlannedShutdownDto approve(UUID id) {
        PlannedShutdown s = repository.findById(id)
                .orElseThrow(() -> RestException.notFound("Planned shutdown not found: " + id));
        s.setStatus(PlanStatus.APPROVED);
        return PlannedShutdownDto.from(s);
    }
}
