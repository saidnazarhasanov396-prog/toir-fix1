package com.toir.service;
import com.toir.entity.Location;
import com.toir.repository.LocationRepository;

import com.toir.exception.RestException;
import com.toir.dto.location.LocationDto;
import com.toir.dto.location.LocationRequest;
import com.toir.enums.LocationType;
import com.toir.util.PaginationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LocationService {

    private final LocationRepository repository;


    @Transactional(readOnly = true)
    public List<LocationDto> findAll() {
        return repository.findAllByIsDeletedFalse().stream().map(LocationDto::from).toList();
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

    public LocationDto create(LocationRequest request) {
        if (repository.existsByCodeAndIsDeletedFalse(request.code())) {
            throw RestException.conflict("Location code already exists: " + request.code());
        }
        Location entity = new Location();
        apply(entity, request);
        return LocationDto.from(repository.save(entity));
    }

    public LocationDto update(UUID id, LocationRequest request) {
        Location entity = getOrThrow(id);
        apply(entity, request);
        return LocationDto.from(entity);
    }

    public void delete(UUID id) {
        Location entity = getOrThrow(id);
        if (!repository.findAllByParentIdAndIsDeletedFalse(id).isEmpty()) {
            throw RestException.conflict("Location has children");
        }
        entity.setDeleted(true);
        repository.save(entity);
    }

    private Location getOrThrow(UUID id) {
        return repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Location not found: " + id));
    }

    private void apply(Location entity, LocationRequest request) {
        entity.setCode(request.code());
        entity.setName(request.name());
        entity.setType(request.type());
        entity.setParentId(request.parentId());
        entity.setDepartmentId(request.departmentId());
        entity.setDescription(request.description());
    }
}
