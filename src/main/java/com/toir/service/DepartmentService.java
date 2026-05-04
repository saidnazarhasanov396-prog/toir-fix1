package com.toir.service;
import com.toir.entity.Department;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.enums.DepartmentType;
import com.toir.repository.DepartmentRepository;

import com.toir.exception.RestException;
import com.toir.dto.department.DepartmentDto;
import com.toir.dto.department.DepartmentRequest;
import com.toir.util.AuditBuilderService;
import com.toir.util.AuditSerializationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class DepartmentService {

    private final DepartmentRepository repository;
    private final AuditBuilderService auditBuilderService;
    private final AuditSerializationService auditSerializationService;

    @Transactional(readOnly = true)
    public List<DepartmentDto> findAll(DepartmentType type, String search) {
        return repository.findAllByIsDeletedFalseAndByType(type, search).stream()
                .map(DepartmentDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public DepartmentDto findById(UUID id) {
        return DepartmentDto.from(getOrThrow(id));
    }

    public DepartmentDto create(DepartmentRequest request) {
        if (repository.existsByCodeAndIsDeletedFalse(request.code())) {
            throw RestException.conflict("Department code already exists: " + request.code());
        }
        Department entity = new Department();
        apply(entity, request);
        Department saved = repository.save(entity);
        audit(AuditAction.CREATE, saved.getId(), null, saved);
        return DepartmentDto.from(saved);
    }

    public DepartmentDto update(UUID id, DepartmentRequest request) {
        Department entity = getOrThrow(id);
        String oldJson = auditSerializationService.toJson(entity);
        apply(entity, request);
        audit(AuditAction.UPDATE, entity.getId(), oldJson, entity);
        return DepartmentDto.from(entity);
    }

    public void delete(UUID id) {
        Department entity = getOrThrow(id);
        if (!repository.findAllByParentIdAndIsDeletedFalse(id).isEmpty()) {
            throw RestException.conflict("Department has children");
        }
        String oldJson = auditSerializationService.toJson(entity);
        entity.setDeleted(true);
        Department saved = repository.save(entity);
        audit(AuditAction.DELETE, saved.getId(), oldJson, null);
    }

    private Department getOrThrow(UUID id) {
        return repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Department not found: " + id));
    }

    private void apply(Department entity, DepartmentRequest request) {
        entity.setCode(request.code());
        entity.setName(request.name());
        entity.setType(request.type());
        entity.setParentId(request.parentId());
        entity.setDescription(request.description());
    }

    private void audit(AuditAction action, UUID id, String oldJson, Department current) {
        String newJson = current == null ? null : auditSerializationService.toJson(current);
        auditBuilderService.log(
                "department",
                id != null ? id.toString() : null,
                action,
                AuditModule.DEPARTMENT,
                auditMessage(action),
                oldJson,
                newJson
        );
    }

    private String auditMessage(AuditAction action) {
        return switch (action) {
            case CREATE -> "Подразделение создано";
            case UPDATE -> "Подразделение обновлено";
            case DELETE -> "Подразделение удалено";
            default -> "Действие выполнено над подразделением";
        };
    }
}
