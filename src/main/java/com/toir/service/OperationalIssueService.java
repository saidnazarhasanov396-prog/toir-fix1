package com.toir.service;

import com.toir.entity.OperationalIssue;
import com.toir.dto.operationalissue.OperationalIssueDto;
import com.toir.entity.Department;
import com.toir.entity.equipment.Equipment;
import com.toir.enums.NotificationSeverity;
import com.toir.enums.OperationalIssueStatus;
import com.toir.enums.OperationalIssueType;
import com.toir.exception.RestException;
import com.toir.repository.OperationalIssueRepository;
import com.toir.repository.department.DepartmentRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.security.ScopeAccessService;
import com.toir.util.PaginationUtils;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OperationalIssueService {

    private final OperationalIssueRepository repository;
    private final EquipmentRepository equipmentRepository;
    private final DepartmentRepository departmentRepository;
    private final ScopeAccessService scopeAccessService;

    @Transactional(readOnly = true)
    public Page<OperationalIssueDto> search(OperationalIssueStatus status,
                                            NotificationSeverity severity,
                                            OperationalIssueType type,
                                            UUID requestedDepartmentId,
                                            UUID equipmentId,
                                            int page,
                                            int size,
                                            String sort,
                                            String direction) {
        UUID effectiveDepartmentId = effectiveDepartmentFilter(requestedDepartmentId);
        Page<OperationalIssue> result = repository.search(
                scopeAccessService.isScopeAdmin() ? null : effectiveDepartmentId,
                status,
                severity,
                type,
                scopeAccessService.isScopeAdmin() ? requestedDepartmentId : effectiveDepartmentId,
                equipmentId,
                pageRequest(page, size, sort, direction)
        );
        return toDtoPage(result);
    }

    @Transactional(readOnly = true)
    public OperationalIssueDto findById(UUID id) {
        OperationalIssue issue = getScopedIssue(id);
        return toDto(issue);
    }

    @Transactional
    public OperationalIssueDto resolve(UUID id) {
        OperationalIssue issue = getScopedIssue(id);
        issue.setStatus(OperationalIssueStatus.RESOLVED);
        issue.setResolvedAt(Instant.now());
        return toDto(repository.save(issue));
    }

    @Transactional
    public OperationalIssue openOrUpdate(OperationalIssueType type,
                                         NotificationSeverity severity,
                                         UUID equipmentId,
                                         UUID departmentId,
                                         String sourceType,
                                         UUID sourceId,
                                         String title,
                                         String message) {
        return openOrUpdate(type, severity, equipmentId, departmentId, sourceType, sourceId, title, message, null);
    }

    @Transactional
    public OperationalIssue openOrUpdate(OperationalIssueType type,
                                         NotificationSeverity severity,
                                         UUID equipmentId,
                                         UUID departmentId,
                                         String sourceType,
                                         UUID sourceId,
                                         String title,
                                         String message,
                                         Map<String, Object> metadata) {
        OperationalIssue issue = repository
                .findBySourceTypeAndSourceIdAndStatusAndIsDeletedFalse(sourceType, sourceId, OperationalIssueStatus.OPEN)
                .orElseGet(OperationalIssue::new);
        issue.setType(type);
        issue.setSeverity(severity == null ? NotificationSeverity.INFO : severity);
        issue.setEquipmentId(equipmentId);
        issue.setDepartmentId(departmentId);
        issue.setSourceType(sourceType);
        issue.setSourceId(sourceId);
        issue.setTitle(title);
        issue.setMessage(message);
        issue.setMetadata(metadata);
        issue.setStatus(OperationalIssueStatus.OPEN);
        issue.setResolvedAt(null);
        return repository.save(issue);
    }

    @Transactional
    public void resolveOpen(String sourceType, UUID sourceId) {
        resolveOpen(sourceType, sourceId, null);
    }

    @Transactional
    public void resolveOpen(String sourceType, UUID sourceId, String resolutionMessage) {
        repository.findBySourceTypeAndSourceIdAndStatusAndIsDeletedFalse(sourceType, sourceId, OperationalIssueStatus.OPEN)
                .ifPresent(issue -> {
                    issue.setStatus(OperationalIssueStatus.RESOLVED);
                    issue.setResolvedAt(Instant.now());
                    if (resolutionMessage != null && !resolutionMessage.isBlank()) {
                        issue.setMessage(resolutionMessage);
                    }
                    repository.save(issue);
                });
    }

    private OperationalIssue getScopedIssue(UUID id) {
        OperationalIssue issue = repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Operational issue not found: " + id));
        assertCanRead(issue);
        return issue;
    }

    private void assertCanRead(OperationalIssue issue) {
        if (scopeAccessService.isScopeAdmin()) {
            return;
        }
        UUID currentDepartmentId = scopeAccessService.currentDepartmentIdOrNull();
        if (currentDepartmentId == null || !currentDepartmentId.equals(issue.getDepartmentId())) {
            throw new org.springframework.security.access.AccessDeniedException("Access denied by operational issue scope");
        }
    }

    private UUID effectiveDepartmentFilter(UUID requestedDepartmentId) {
        if (scopeAccessService.isScopeAdmin()) {
            return requestedDepartmentId;
        }
        return scopeAccessService.currentDepartmentIdOrNull();
    }

    private Page<OperationalIssueDto> toDtoPage(Page<OperationalIssue> page) {
        List<OperationalIssue> issues = page.getContent();
        Set<UUID> equipmentIds = issues.stream()
                .map(OperationalIssue::getEquipmentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<UUID> departmentIds = issues.stream()
                .map(OperationalIssue::getDepartmentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, Equipment> equipmentById = equipmentIds.isEmpty()
                ? Map.of()
                : equipmentRepository.findAllByIdInAndIsDeletedFalse(equipmentIds).stream()
                .collect(Collectors.toMap(Equipment::getId, Function.identity(), (left, right) -> left));
        Map<UUID, Department> departmentById = departmentIds.isEmpty()
                ? Map.of()
                : departmentRepository.findAllByIdInAndIsDeletedFalse(departmentIds).stream()
                .collect(Collectors.toMap(Department::getId, Function.identity(), (left, right) -> left));
        return page.map(issue -> OperationalIssueDto.from(
                issue,
                issue.getEquipmentId() == null ? null : equipmentById.get(issue.getEquipmentId()),
                issue.getDepartmentId() == null ? null : departmentById.get(issue.getDepartmentId())
        ));
    }

    private OperationalIssueDto toDto(OperationalIssue issue) {
        Equipment equipment = issue.getEquipmentId() == null
                ? null
                : equipmentRepository.findByIdAndIsDeletedFalse(issue.getEquipmentId()).orElse(null);
        Department department = issue.getDepartmentId() == null
                ? null
                : departmentRepository.findByIdAndIsDeletedFalse(issue.getDepartmentId()).orElse(null);
        return OperationalIssueDto.from(issue, equipment, department);
    }

    private PageRequest pageRequest(int page, int size, String sort, String direction) {
        String property = switch (sort == null ? "" : sort) {
            case "severity" -> "severity";
            case "status" -> "status";
            case "type" -> "type";
            case "resolvedAt" -> "resolvedAt";
            case "createdAt" -> "createdAt";
            default -> "detectedAt";
        };
        Sort.Direction sortDirection = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return PageRequest.of(
                PaginationUtils.pageRequest(page, size).getPageNumber(),
                PaginationUtils.pageRequest(page, size).getPageSize(),
                Sort.by(sortDirection, property)
        );
    }
}
