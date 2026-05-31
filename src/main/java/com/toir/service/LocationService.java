package com.toir.service;

import com.toir.dto.location.LocationDto;
import com.toir.dto.location.LocationRequest;
import com.toir.entity.Department;
import com.toir.entity.Location;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.enums.LocationType;
import com.toir.exception.RestException;
import com.toir.repository.LocationRepository;
import com.toir.repository.department.DepartmentRepository;
import com.toir.util.AuditBuilderService;
import com.toir.util.PaginationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class LocationService {

    private final LocationRepository repository;
    private final DepartmentRepository departmentRepository;
    private final AuditBuilderService auditBuilderService;
    private static final int MAX_CODE_GENERATION_ATTEMPTS = 50;


    @Transactional(readOnly = true)
    public List<LocationDto> findAll() {
        return toDtos(repository.findAllByIsDeletedFalseOrderByUpdatedAtDesc());
    }

    @Transactional(readOnly = true)
    public Page<LocationDto> search(LocationType locationType, String search, int page, int pageSize) {
        var pageable = PaginationUtils.pageRequest(page, pageSize);
        Page<Location> locations = repository.search(locationType, search, pageable);
        return new PageImpl<>(
                toDtos(locations.getContent()),
                locations.getPageable(),
                locations.getTotalElements()
        );
    }

    @Transactional(readOnly = true)
    public LocationDto findById(UUID id) {
        return toDto(getOrThrow(id));
    }

    @Transactional
    public LocationDto create(LocationRequest request) {
        Location saved = saveWithGeneratedCode(request);

        auditBuilderService.log(
                "location",
                saved.getId().toString(),
                AuditAction.CREATE,
                AuditModule.LOCATION,
                "Локация создана",
                null,
                saved
        );


        return toDto(saved);
    }

    @Transactional
    public LocationDto update(UUID id, LocationRequest request) {
        Location entity = getOrThrow(id);
        apply(entity, request);

        Location saved = repository.save(entity);

        auditBuilderService.log(
                "location",
                saved.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.LOCATION,
                "Локация обновлена",
                entity,
                saved
        );

        return toDto(entity);
    }

    @Transactional
    public void delete(UUID id) {
        Location entity = getOrThrow(id);
        if (!repository.findAllByParentIdAndIsDeletedFalse(id).isEmpty()) {
            throw RestException.conflict("Location has children");
        }
        entity.setDeleted(true);
        Location saved = repository.save(entity);

        auditBuilderService.log(
                "location",
                saved.getId().toString(),
                AuditAction.DELETE,
                AuditModule.LOCATION,
                "Локация удалена",
                entity,
                saved
        );


    }

    private Location getOrThrow(UUID id) {
        return repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Location not found: " + id));
    }

    private LocationDto toDto(Location location) {
        return LocationDto.from(location, departmentName(location.getDepartmentId()));
    }

    private List<LocationDto> toDtos(List<Location> locations) {
        Map<UUID, String> departmentNameById = departmentNames(locations);
        return locations.stream()
                .map(location -> LocationDto.from(
                        location,
                        departmentNameById.get(location.getDepartmentId())
                ))
                .toList();
    }

    private String departmentName(UUID departmentId) {
        if (departmentId == null) {
            return null;
        }
        return departmentRepository.findByIdAndIsDeletedFalse(departmentId)
                .map(Department::getName)
                .orElse(null);
    }

    private Map<UUID, String> departmentNames(List<Location> locations) {
        List<UUID> departmentIds = locations.stream()
                .map(Location::getDepartmentId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        if (departmentIds.isEmpty()) {
            return Map.of();
        }
        return departmentRepository.findAllByIdInAndIsDeletedFalse(departmentIds).stream()
                .collect(Collectors.toMap(Department::getId, Department::getName));
    }

    private void apply(Location entity, LocationRequest request) {
        entity.setName(request.name());
        entity.setNameUz(request.nameUz());
        entity.setNameEn(request.nameEn());
        entity.setType(request.type());
        entity.setParentId(request.parentId());
        entity.setDepartmentId(request.departmentId());
        entity.setDescription(request.description());
    }

    private String formatCode(String prefix, int year, long sequence) {
        return "%s-%d-%04d".formatted(prefix, year, sequence);
    }

    private Location saveWithGeneratedCode(LocationRequest request) {
        int year = Year.now().getValue();
        String codePrefix = "LOC-" + year + "-";
        long sequence = repository.maxSequenceByCodePrefix(codePrefix) + 1;

        for (int attempt = 0; attempt < MAX_CODE_GENERATION_ATTEMPTS; attempt++) {
            String code = formatCode("LOC", year, sequence + attempt);
            if (repository.existsByCode(code)) {
                continue;
            }

            Location entity = new Location();
            entity.setCode(code);
            apply(entity, request);

            try {
                return repository.save(entity);
            } catch (DataIntegrityViolationException ex) {
                if (isCodeConflict(ex)) {
                    continue;
                }
                throw ex;
            }
        }

        throw RestException.conflict("Could not generate unique location code");
    }

    private boolean isCodeConflict(DataIntegrityViolationException ex) {
        Throwable root = ex.getMostSpecificCause();
        String message = root != null ? root.getMessage() : ex.getMessage();
        if (message == null) {
            return false;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        return normalized.contains("locations_code_key")
                || (normalized.contains("locations")
                && normalized.contains("duplicate")
                && normalized.contains("code"));
    }
}
