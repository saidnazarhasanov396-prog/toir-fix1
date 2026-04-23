package com.toir.service;
import com.toir.entity.Manufacturer;
import com.toir.repository.ManufacturerRepository;

import com.toir.exception.RestException;
import com.toir.dto.manufacturer.ManufacturerDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class ManufacturerService {

    private final ManufacturerRepository repository;

    public ManufacturerService(ManufacturerRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<ManufacturerDto> findAll() {
        return repository.findAll().stream().map(ManufacturerDto::from).toList();
    }

    @Transactional(readOnly = true)
    public ManufacturerDto findById(UUID id) {
        return ManufacturerDto.from(getOrThrow(id));
    }

    public ManufacturerDto create(ManufacturerDto request) {
        if (repository.existsByCode(request.code())) {
            throw RestException.conflict("Manufacturer code already exists: " + request.code());
        }
        Manufacturer entity = new Manufacturer();
        apply(entity, request);
        return ManufacturerDto.from(repository.save(entity));
    }

    public ManufacturerDto update(UUID id, ManufacturerDto request) {
        Manufacturer entity = getOrThrow(id);
        apply(entity, request);
        return ManufacturerDto.from(entity);
    }

    public void delete(UUID id) {
        repository.delete(getOrThrow(id));
    }

    private Manufacturer getOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> RestException.notFound("Manufacturer not found: " + id));
    }

    private void apply(Manufacturer entity, ManufacturerDto r) {
        entity.setCode(r.code());
        entity.setName(r.name());
        entity.setCountry(r.country());
        entity.setWebsite(r.website());
        entity.setContactInfo(r.contactInfo());
    }
}
