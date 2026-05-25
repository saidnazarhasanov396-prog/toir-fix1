package com.toir.service.department;

import com.toir.dto.department.DepartmentDto;
import com.toir.dto.department.DepartmentRequest;
import com.toir.dto.hr.EmployeeDto;
import com.toir.entity.Department;
import com.toir.entity.users.Brigade;
import com.toir.entity.users.Employee;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.enums.DepartmentType;
import com.toir.exception.RestException;
import com.toir.repository.department.DepartmentRepository;
import com.toir.repository.projects.BrigadeRepository;
import com.toir.repository.users.EmployeeRepository;
import com.toir.service.users.HrService;
import com.toir.util.AuditBuilderService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DepartmentService {

    private final DepartmentRepository repository;
    private final AuditBuilderService auditBuilderService;
    private final EmployeeRepository employeeRepository;
    private final BrigadeRepository brigadeRepository;
    private static final int MAX_CODE_GENERATION_ATTEMPTS = 50;

    @Transactional(readOnly = true)
    public List<DepartmentDto> findAll(DepartmentType type, String search) {
        String normalizedSearch = normalizeSearch(search);
        String searchPattern = buildSearchPattern(normalizedSearch);
        return repository.findAllByIsDeletedFalseAndByType(type, searchPattern).stream()
                .map(DepartmentDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<EmployeeDto> findEmployeesByDepartment(UUID departmentId) {
        Department department = getOrThrow(departmentId);

        List<Employee> employees = employeeRepository.findAllByDepartmentIdAndIsDeletedFalse(departmentId);

        List<UUID> brigadeIds = HrService.getBrigadeIds(employees);

        Map<UUID, String> brigadeNameById = brigadeIds.isEmpty()
                ? Map.of()
                : brigadeRepository.findAllByIdInAndIsDeletedFalse(brigadeIds)
                .stream()
                .collect(Collectors.toMap(
                        Brigade::getId,
                        Brigade::getName,
                        (a, b) -> a
                ));

        return employees.stream()
                .map(employee -> EmployeeDto.from(
                        employee,
                        department.getName(),
                        resolveName(brigadeNameById, employee.getBrigadeId())
                ))
                .toList();
    }

    private String resolveName(Map<UUID, String> namesById, UUID id) {
        if (id == null) {
            return null;
        }
        return namesById.get(id);
    }

    @Transactional(readOnly = true)
    public DepartmentDto findById(UUID id) {
        return DepartmentDto.from(getOrThrow(id));
    }

    @Transactional
    public DepartmentDto create(DepartmentRequest request) {
        Department created = saveWithGeneratedCode(request);

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
        entity.setName(request.name());
        entity.setType(request.type());
        entity.setParentId(request.parentId());
        entity.setDescription(request.description());
    }

    private Department saveWithGeneratedCode(DepartmentRequest request) {
        String prefix = codePrefix(request.type());
        long sequence = nextSequence(prefix);

        for (int attempt = 0; attempt < MAX_CODE_GENERATION_ATTEMPTS; attempt++) {
            Department entity = new Department();
            entity.setCode(formatCode(prefix, sequence + attempt));
            apply(entity, request);
            entity.setDeleted(false);

            try {
                return repository.saveAndFlush(entity);
            } catch (DataIntegrityViolationException ex) {
                if (isCodeConflict(ex)) {
                    continue;
                }
                throw ex;
            }
        }

        throw RestException.conflict("Could not generate unique department code");
    }

    private long nextSequence(String prefix) {
        String codeStart = prefix + "-";
        return repository.findCodesByPrefix(prefix).stream()
                .filter(Objects::nonNull)
                .filter(code -> code.startsWith(codeStart))
                .map(code -> code.substring(codeStart.length()))
                .filter(this::isNumeric)
                .mapToLong(Long::parseLong)
                .max()
                .orElse(0L) + 1;
    }

    private boolean isNumeric(String value) {
        if (value.isBlank()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private String formatCode(String prefix, long sequence) {
        return "%s-%03d".formatted(prefix, sequence);
    }

    private String codePrefix(DepartmentType type) {
        if (type == null) {
            return "DEP";
        }
        return switch (type) {
            case ENTERPRISE -> "ENT";
            case SITE -> "SITE";
            case WORKSHOP -> "WS";
            case SECTION -> "SEC";
            case AREA -> "AREA";
            case LINE -> "LINE";
            case SERVICE -> "SVC";
            case ADMINISTRATION -> "ADM";
        };
    }

    private boolean isCodeConflict(DataIntegrityViolationException ex) {
        Throwable root = ex.getMostSpecificCause();
        String message = root != null ? root.getMessage() : ex.getMessage();
        if (message == null) {
            return false;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        return normalized.contains("departments_code_key")
                || normalized.contains("ukl7tivi5261wxdnvo6cct9gg6t")
                || (normalized.contains("departments")
                && normalized.contains("duplicate")
                && normalized.contains("code"));
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
