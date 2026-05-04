package com.toir.service;
import com.toir.entity.Location;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.repository.LocationRepository;

import com.toir.exception.RestException;
import com.toir.dto.location.LocationDto;
import com.toir.dto.location.LocationRequest;
import com.toir.enums.LocationType;
import com.toir.util.AuditBuilderService;
import com.toir.util.AuditSerializationService;
import com.toir.util.PaginationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class LocationService {

    private final LocationRepository repository;
    private final AuditBuilderService auditBuilderService;
    private final AuditSerializationService auditSerializationService;


    @Transactional(readOnly = true)
    public List<LocationDto> findAll() {
        return repository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream().map(LocationDto::from).toList();
    }

    @Transactional(readOnly = true)
    public Page<LocationDto> search(LocationType locationType, String search, int page, int pageSize) {
        var pageable = PaginationUtils.pageRequest(page, pageSize);
        return repository.search(locationType, search, pageable).map(LocationDto::from);
    }

    @Transactional(readOnly = true)
    public LocationDto findById(UUID id) {
        return LocationDto.from(getOrThrow(id));
    }

    @Transactional
    public LocationDto create(LocationRequest request) {
        Location entity = new Location();
        entity.setCode(nextCode());
        apply(entity, request);
        Location saved = repository.save(entity);
        audit(AuditAction.CREATE, saved.getId(), null, saved);
        return LocationDto.from(saved);
    }

    @Transactional
    public LocationDto update(UUID id, LocationRequest request) {
        Location entity = getOrThrow(id);
        String oldJson = auditSerializationService.toJson(entity);
        apply(entity, request);
        audit(AuditAction.UPDATE, entity.getId(), oldJson, entity);
        return LocationDto.from(entity);
    }

    @Transactional
    public void delete(UUID id) {
        Location entity = getOrThrow(id);
        if (!repository.findAllByParentIdAndIsDeletedFalse(id).isEmpty()) {
            throw RestException.conflict("Location has children");
        }
        String oldJson = auditSerializationService.toJson(entity);
        entity.setDeleted(true);
        Location saved = repository.save(entity);
        audit(AuditAction.DELETE, saved.getId(), oldJson, null);
    }

    private Location getOrThrow(UUID id) {
        return repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Location not found: " + id));
    }

    private void apply(Location entity, LocationRequest request) {
        entity.setName(request.name());
        entity.setType(request.type());
        entity.setParentId(request.parentId());
        entity.setDepartmentId(request.departmentId());
        entity.setDescription(request.description());
    }

    private String nextCode() {
        int year = Year.now().getValue();
        String codePrefix = "LOC-" + year + "-";
        long sequence = repository.maxSequenceByCodePrefix(codePrefix) + 1;
        String code = formatCode("LOC", year, sequence);
        while (repository.existsByCode(code)) {
            sequence++;
            code = formatCode("LOC", year, sequence);
        }
        return code;
    }

    private String formatCode(String prefix, int year, long sequence) {
        return "%s-%d-%04d".formatted(prefix, year, sequence);
    }

    private void audit(AuditAction action, UUID id, String oldJson, Location current) {
        String newJson = current == null ? null : auditSerializationService.toJson(current);
        auditBuilderService.log(
                "location",
                id != null ? id.toString() : null,
                action,
                AuditModule.LOCATION,
                auditMessage(action),
                oldJson,
                newJson
        );
    }

    private String auditMessage(AuditAction action) {
        return switch (action) {
            case CREATE -> "Локация создана";
            case UPDATE -> "Локация обновлена";
            case DELETE -> "Локация удалена";
            default -> "Действие выполнено над локацией";
        };
    }
}
