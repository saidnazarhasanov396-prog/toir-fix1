package com.toir.service;
import com.toir.entity.Defect;
import com.toir.enums.DefectStatus;
import com.toir.repository.DefectRepository;

import com.toir.exception.RestException;
import com.toir.dto.defect.DefectDto;
import com.toir.dto.defect.DefectRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DefectService {

    private final DefectRepository repository;

    @Transactional(readOnly = true)
    public List<DefectDto> findAll() {
        return repository.findAll().stream().map(DefectDto::from).toList();
    }

    @Transactional(readOnly = true)
    public DefectDto findById(UUID id) {
        return DefectDto.from(getOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<DefectDto> findByEquipment(UUID equipmentId) {
        return repository.findAllByEquipmentId(equipmentId).stream().map(DefectDto::from).toList();
    }

    public DefectDto create(DefectRequest request) {
        if (repository.existsByCode(request.code())) {
            throw RestException.conflict("Defect code already exists: " + request.code());
        }
        Defect entity = new Defect();
        apply(entity, request);
        return DefectDto.from(repository.save(entity));
    }

    public DefectDto update(UUID id, DefectRequest request) {
        Defect entity = getOrThrow(id);
        apply(entity, request);
        return DefectDto.from(entity);
    }

    public DefectDto resolve(UUID id) {
        Defect entity = getOrThrow(id);
        entity.setStatus(DefectStatus.RESOLVED);
        entity.setResolvedAt(Instant.now());
        return DefectDto.from(entity);
    }

    public void delete(UUID id) {
        repository.delete(getOrThrow(id));
    }

    private Defect getOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> RestException.notFound("Defect not found: " + id));
    }

    private void apply(Defect entity, DefectRequest request) {
        entity.setCode(request.code());
        entity.setTitle(request.title());
        entity.setDescription(request.description());
        entity.setEquipmentId(request.equipmentId());
        entity.setRequestId(request.requestId());
        entity.setWorkOrderId(request.workOrderId());
        entity.setCategory(request.category());
        entity.setSeverity(request.severity());
        entity.setFailureReason(request.failureReason());
        entity.setRootCause(request.rootCause());
    }
}
