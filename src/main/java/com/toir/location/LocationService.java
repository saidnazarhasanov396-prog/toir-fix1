package com.toir.location;

import com.toir.common.exception.RestException;
import com.toir.location.dto.LocationDto;
import com.toir.location.dto.LocationRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class LocationService {

    private final LocationRepository repository;

    public LocationService(LocationRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<LocationDto> findAll() {
        return repository.findAll().stream().map(LocationDto::from).toList();
    }

    @Transactional(readOnly = true)
    public LocationDto findById(UUID id) {
        return LocationDto.from(getOrThrow(id));
    }

    public LocationDto create(LocationRequest request) {
        if (repository.existsByCode(request.code())) {
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
        if (!repository.findAllByParentId(id).isEmpty()) {
            throw RestException.conflict("Location has children");
        }
        repository.delete(entity);
    }

    private Location getOrThrow(UUID id) {
        return repository.findById(id)
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
