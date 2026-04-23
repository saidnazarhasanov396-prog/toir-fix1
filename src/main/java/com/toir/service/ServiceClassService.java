package com.toir.service;
import com.toir.entity.ServiceClass;
import com.toir.repository.ServiceClassRepository;

import com.toir.exception.RestException;
import com.toir.dto.serviceclass.ServiceClassDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class ServiceClassService {

    private final ServiceClassRepository repository;

    public ServiceClassService(ServiceClassRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<ServiceClassDto> findAll() {
        return repository.findAll().stream().map(ServiceClassDto::from).toList();
    }

    @Transactional(readOnly = true)
    public ServiceClassDto findById(UUID id) {
        return ServiceClassDto.from(getOrThrow(id));
    }

    public ServiceClassDto create(ServiceClassDto r) {
        if (repository.existsByCode(r.code())) {
            throw RestException.conflict("Service class code already exists: " + r.code());
        }
        ServiceClass e = new ServiceClass();
        e.setCode(r.code()); e.setName(r.name()); e.setDescription(r.description());
        return ServiceClassDto.from(repository.save(e));
    }

    public ServiceClassDto update(UUID id, ServiceClassDto r) {
        ServiceClass e = getOrThrow(id);
        e.setCode(r.code()); e.setName(r.name()); e.setDescription(r.description());
        return ServiceClassDto.from(e);
    }

    public void delete(UUID id) { repository.delete(getOrThrow(id)); }

    private ServiceClass getOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> RestException.notFound("Service class not found: " + id));
    }
}
