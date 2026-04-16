package com.toir.service;
import com.toir.entity.Department;
import com.toir.repository.DepartmentRepository;

import com.toir.exception.RestException;
import com.toir.dto.department.DepartmentDto;
import com.toir.dto.department.DepartmentRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class DepartmentService {

    private final DepartmentRepository repository;

    public DepartmentService(DepartmentRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<DepartmentDto> findAll() {
        return repository.findAll().stream().map(DepartmentDto::from).toList();
    }

    @Transactional(readOnly = true)
    public DepartmentDto findById(UUID id) {
        return DepartmentDto.from(getOrThrow(id));
    }

    public DepartmentDto create(DepartmentRequest request) {
        if (repository.existsByCode(request.code())) {
            throw RestException.conflict("Department code already exists: " + request.code());
        }
        Department entity = new Department();
        apply(entity, request);
        return DepartmentDto.from(repository.save(entity));
    }

    public DepartmentDto update(UUID id, DepartmentRequest request) {
        Department entity = getOrThrow(id);
        apply(entity, request);
        return DepartmentDto.from(entity);
    }

    public void delete(UUID id) {
        Department entity = getOrThrow(id);
        if (!repository.findAllByParentId(id).isEmpty()) {
            throw RestException.conflict("Department has children");
        }
        repository.delete(entity);
    }

    private Department getOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> RestException.notFound("Department not found: " + id));
    }

    private void apply(Department entity, DepartmentRequest request) {
        entity.setCode(request.code());
        entity.setName(request.name());
        entity.setType(request.type());
        entity.setParentId(request.parentId());
        entity.setDescription(request.description());
    }
}
