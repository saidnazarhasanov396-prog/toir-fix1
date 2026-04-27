package com.toir.service;
import com.toir.entity.ServiceClass;
import com.toir.repository.ServiceClassRepository;

import com.toir.exception.RestException;
import com.toir.dto.serviceclass.ServiceClassDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class ServiceClassService {

    private final ServiceClassRepository repository;


    @Transactional(readOnly = true)
    public List<ServiceClassDto> findAll() {
        return repository.findAllByIsDeletedFalse().stream().map(ServiceClassDto::from).toList();
    }

    @Transactional(readOnly = true)
    public ServiceClassDto findById(UUID id) {
        return ServiceClassDto.from(getOrThrow(id));
    }

    public ServiceClassDto create(ServiceClassDto r) {
        if (repository.existsByCodeAndIsDeletedFalse(r.code())) {
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

    public void delete(UUID id) { var entity = getOrThrow(id);
        entity.setDeleted(true);
        repository.save(entity); }

    private ServiceClass getOrThrow(UUID id) {
        return repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Service class not found: " + id));
    }
}
