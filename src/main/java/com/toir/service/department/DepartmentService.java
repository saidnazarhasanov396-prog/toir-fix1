package com.toir.service.department;

import com.toir.dto.department.DepartmentDto;
import com.toir.dto.department.DepartmentListItemDto;
import com.toir.dto.department.DepartmentRequest;
import com.toir.dto.department.DepartmentTreeDto;
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
        return findDepartments(type, search).stream()
                .map(DepartmentDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DepartmentListItemDto> findRegistry(DepartmentType type, String search) {
        return findDepartments(type, search).stream()
                .map(DepartmentListItemDto::from)
                .toList();
    }

    private List<Department> findDepartments(DepartmentType type, String search) {
        String normalizedSearch = normalizeSearch(search);
        String searchPattern = buildSearchPattern(normalizedSearch);
        return repository.findAllByIsDeletedFalseAndByType(type, searchPattern);
    }


    @Transactional(readOnly = true)
    public List<DepartmentTreeDto> findTree(DepartmentType type, String search) {
        String normalizedSearch = normalizeSearch(search);
        List<Department> departments = repository.findAllByIsDeletedFalseOrderByUpdatedAtDesc();
        Map<UUID, Department> byId = departments.stream()
                .collect(Collectors.toMap(
                        Department::getId,
                        department -> department,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));
        Map<UUID, List<Department>> childrenByParentId = departments.stream()
                .filter(department -> department.getParentId() != null)
                .collect(Collectors.groupingBy(Department::getParentId));

        Set<UUID> includedIds = resolveIncludedDepartmentIds(departments, byId, childrenByParentId, type, normalizedSearch);
        List<Department> roots = departments.stream()
                .filter(department -> includedIds.contains(department.getId()))
                .filter(department -> department.getParentId() == null
                        || !byId.containsKey(department.getParentId())
                        || !includedIds.contains(department.getParentId()))
                .sorted(this::compareDepartmentsForTree)
                .toList();

        if (roots.isEmpty() && !includedIds.isEmpty()) {
            roots = departments.stream()
                    .filter(department -> includedIds.contains(department.getId()))
                    .sorted(this::compareDepartmentsForTree)
                    .toList();
        }

        return roots.stream()
                .map(root -> toTreeNode(root, childrenByParentId, includedIds, new LinkedHashSet<>()))
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



    private Set<UUID> resolveIncludedDepartmentIds(
            List<Department> departments,
            Map<UUID, Department> byId,
            Map<UUID, List<Department>> childrenByParentId,
            DepartmentType type,
            String normalizedSearch
    ) {
        boolean hasTypeFilter = type != null;
        boolean hasSearchFilter = normalizedSearch != null;
        if (!hasTypeFilter && !hasSearchFilter) {
            return departments.stream()
                    .map(Department::getId)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }

        Set<UUID> includedIds = new LinkedHashSet<>();
        for (Department department : departments) {
            boolean matchesType = !hasTypeFilter || department.getType() == type;
            boolean matchesSearch = !hasSearchFilter || departmentMatchesSearch(department, normalizedSearch);
            if (matchesType && matchesSearch) {
                addAncestors(department, byId, includedIds);
                addDescendants(department, childrenByParentId, includedIds, new LinkedHashSet<>());
            }
        }
        return includedIds;
    }

    private void addAncestors(Department department, Map<UUID, Department> byId, Set<UUID> includedIds) {
        Department current = department;
        Set<UUID> seen = new LinkedHashSet<>();
        while (current != null && current.getId() != null && seen.add(current.getId())) {
            includedIds.add(current.getId());
            UUID parentId = current.getParentId();
            current = parentId == null ? null : byId.get(parentId);
        }
    }

    private void addDescendants(
            Department department,
            Map<UUID, List<Department>> childrenByParentId,
            Set<UUID> includedIds,
            Set<UUID> path
    ) {
        if (department == null || department.getId() == null || !path.add(department.getId())) {
            return;
        }
        includedIds.add(department.getId());
        for (Department child : childrenByParentId.getOrDefault(department.getId(), List.of())) {
            addDescendants(child, childrenByParentId, includedIds, path);
        }
        path.remove(department.getId());
    }

    private DepartmentTreeDto toTreeNode(
            Department department,
            Map<UUID, List<Department>> childrenByParentId,
            Set<UUID> includedIds,
            Set<UUID> path
    ) {
        if (department.getId() == null || !path.add(department.getId())) {
            return DepartmentTreeDto.from(department, List.of());
        }
        List<DepartmentTreeDto> children = childrenByParentId.getOrDefault(department.getId(), List.of()).stream()
                .filter(child -> includedIds.contains(child.getId()))
                .filter(child -> child.getId() != null && !path.contains(child.getId()))
                .sorted(this::compareDepartmentsForTree)
                .map(child -> toTreeNode(child, childrenByParentId, includedIds, new LinkedHashSet<>(path)))
                .toList();
        return DepartmentTreeDto.from(department, children);
    }

    private int compareDepartmentsForTree(Department left, Department right) {
        int typeCompare = Integer.compare(typeOrder(left.getType()), typeOrder(right.getType()));
        if (typeCompare != 0) {
            return typeCompare;
        }
        int codeCompare = safeText(left.getCode()).compareToIgnoreCase(safeText(right.getCode()));
        if (codeCompare != 0) {
            return codeCompare;
        }
        return safeText(left.getName()).compareToIgnoreCase(safeText(right.getName()));
    }

    private int typeOrder(DepartmentType type) {
        return type == null ? Integer.MAX_VALUE : type.ordinal();
    }

    private boolean departmentMatchesSearch(Department department, String normalizedSearch) {
        return containsNormalized(department.getCode(), normalizedSearch)
                || containsNormalized(department.getName(), normalizedSearch)
                || containsNormalized(department.getNameEn(), normalizedSearch)
                || containsNormalized(department.getNameUz(), normalizedSearch)
                || containsNormalized(department.getDescription(), normalizedSearch);
    }

    private boolean containsNormalized(String value, String normalizedSearch) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(normalizedSearch);
    }

    private String safeText(String value) {
        return value == null ? "" : value;
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
