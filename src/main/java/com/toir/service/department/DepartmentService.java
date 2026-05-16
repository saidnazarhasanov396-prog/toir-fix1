package com.toir.service.department;

import com.toir.dto.department.DepartmentDto;
import com.toir.dto.department.DepartmentRequest;
import com.toir.entity.Department;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.enums.DepartmentType;
import com.toir.exception.RestException;
import com.toir.repository.department.DepartmentRepository;
import com.toir.util.AuditBuilderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DepartmentService {

    private final DepartmentRepository repository;
    private final AuditBuilderService auditBuilderService;

    @Transactional(readOnly = true)
    public List<DepartmentDto> findAll(DepartmentType type, String search) {
        String normalizedSearch = normalizeSearch(search);
        String searchPattern = buildSearchPattern(normalizedSearch);
        return repository.findAllByIsDeletedFalseAndByType(type, searchPattern).stream()
                .map(DepartmentDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public DepartmentDto findById(UUID id) {
        return DepartmentDto.from(getOrThrow(id));
    }

    @Transactional
    public DepartmentDto create(DepartmentRequest request) {
        if (repository.existsByCodeAndIsDeletedFalse(request.code())) {
            throw RestException.conflict("Department code already exists: " + request.code());
        }
        Department entity = new Department();
        apply(entity, request);
        entity.setDeleted(false);
        Department created = repository.save(entity);

        auditBuilderService.log(
                "department",
                created.getId().toString(),
                AuditAction.CREATE,
                AuditModule.DEPARTMENT,
                "Подразделение создано",
                null,
                created
        );

        return DepartmentDto.from(created);
    }

    @Transactional
    public DepartmentDto update(UUID id, DepartmentRequest request) {
        Department entity = getOrThrow(id);
        apply(entity, request);

        Department updated = repository.save(entity);

        auditBuilderService.log(
                "department",
                updated.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.DEPARTMENT,
                "Подразделение обновлено",
                entity,
                updated
        );
        return DepartmentDto.from(updated);
    }

    @Transactional
    public void delete(UUID id) {
        Department entity = getOrThrow(id);
        if (!repository.findAllByParentIdAndIsDeletedFalse(id).isEmpty()) {
            throw RestException.conflict("Department has children");
        }
        entity.setDeleted(true);
        Department deleted = repository.save(entity);

        auditBuilderService.log(
                "department",
                deleted.getId().toString(),
                AuditAction.DELETE,
                AuditModule.DEPARTMENT,
                "Подразделение удалено",
                deleted,
                null
        );

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

    private String normalizeSearch(String search) {
        if (search == null) {
            return null;
        }
        String trimmed = search.trim();
        return trimmed.isEmpty() ? null : trimmed.toLowerCase(Locale.ROOT);
    }

    private String buildSearchPattern(String normalizedSearch) {
        return normalizedSearch == null ? null : "%" + normalizedSearch + "%";
    }
}
