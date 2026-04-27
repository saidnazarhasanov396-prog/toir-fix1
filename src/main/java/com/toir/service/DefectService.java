package com.toir.service;
import com.toir.dto.defect.DefectResponse;
import com.toir.entity.Defect;
import com.toir.entity.Equipment;
import com.toir.enums.DefectStatus;
import com.toir.repository.DefectRepository;
import com.toir.repository.EquipmentRepository;

import com.toir.exception.RestException;
import com.toir.util.PaginationUtils;
import com.toir.dto.defect.DefectDto;
import com.toir.dto.defect.DefectRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DefectService {

    private final DefectRepository repository;
    private final EquipmentRepository equipmentRepository;

    @Transactional(readOnly = true)
    public List<DefectResponse> findAll() {
        return repository.findAllByIsDeletedFalse().stream()
                .map(DefectDto::from)
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<DefectResponse> search(UUID equipmentId, int page, int size, String search) {
        var pageable = PaginationUtils.pageRequest(page, size);
        return repository.searchPaginated(
                equipmentId,
                search,
                pageable
        ).map(DefectDto::from)
         .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public DefectResponse findById(UUID id) {
        return toResponse(DefectDto.from(getOrThrow(id)));
    }

    @Transactional(readOnly = true)
    public List<DefectResponse> findByEquipment(UUID equipmentId) {
        return repository.findAllByEquipmentIdAndIsDeletedFalse(equipmentId).stream()
                .map(DefectDto::from)
                .map(this::toResponse)
                .toList();
    }

    public DefectResponse create(DefectRequest request) {
        if (repository.existsByCodeAndIsDeletedFalse(request.code())) {
            throw RestException.conflict("Defect code already exists: " + request.code());
        }
        Defect entity = new Defect();
        apply(entity, request);
        return toResponse(DefectDto.from(repository.save(entity)));
    }

    public DefectResponse update(UUID id, DefectRequest request) {
        Defect entity = getOrThrow(id);
        apply(entity, request);
        return toResponse(DefectDto.from(entity));
    }

    public DefectResponse resolve(UUID id) {
        Defect entity = getOrThrow(id);
        entity.setStatus(DefectStatus.RESOLVED);
        entity.setResolvedAt(Instant.now());
        return toResponse(DefectDto.from(entity));
    }

    public void delete(UUID id) {
        var entity = getOrThrow(id);
        entity.setDeleted(true);
        repository.save(entity);
    }

    private Defect getOrThrow(UUID id) {
        return repository.findByIdAndIsDeletedFalse(id)
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

    private DefectResponse toResponse(DefectDto dto) {
        return DefectResponse.from(dto, findEquipmentName(dto.equipmentId()));
    }

    private String findEquipmentName(UUID equipmentId) {
        if (equipmentId == null) {
            return null;
        }
        return equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)
                .map(Equipment::getName)
                .orElse(null);
    }
}
