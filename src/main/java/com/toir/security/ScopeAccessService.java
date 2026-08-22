package com.toir.security;

import com.toir.repository.users.EmployeeRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ScopeAccessService {

    private static final String SYSTEM_ADMIN = "SYSTEM_ADMIN";

    private final EmployeeRepository employeeRepository;
    private final com.toir.repository.WarehouseRepository warehouseRepository;

    public ScopeAccessService(EmployeeRepository employeeRepository, com.toir.repository.WarehouseRepository warehouseRepository) {
        this.employeeRepository = employeeRepository;
        this.warehouseRepository = warehouseRepository;
    }

    public Optional<AuthenticatedUser> currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            return Optional.empty();
        }
        return Optional.of(user);
    }

    public UUID currentUserIdOrNull() {
        return currentUser()
                .map(AuthenticatedUser::id)
                .filter(StringUtils::hasText)
                .flatMap(this::parseUuid)
                .orElse(null);
    }

    public UUID currentDepartmentIdOrNull() {
        return currentUser()
                .map(AuthenticatedUser::departmentId)
                .filter(StringUtils::hasText)
                .flatMap(this::parseUuid)
                .orElse(null);
    }

    public boolean isScopeAdmin() {
        return isScopeAdmin(SecurityContextHolder.getContext().getAuthentication());
    }

    public boolean isScopeAdmin(Authentication authentication) {
        if (authentication == null) {
            return false;
        }
        if (authentication.getPrincipal() instanceof AuthenticatedUser user) {
            if (SYSTEM_ADMIN.equals(user.primaryRoleCode()) || hasWildcardPermission(user)) {
                return true;
            }
        }
        Set<String> authorities = authorityNames(authentication);
        return authorities.contains(SYSTEM_ADMIN) || authorities.contains(PermissionConstants.WILDCARD);
    }

    public boolean hasAuthority(String authority) {
        if (!StringUtils.hasText(authority)) {
            return false;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        if (authentication.getPrincipal() instanceof AuthenticatedUser user
                && user.permissions() != null
                && user.permissions().stream().anyMatch(authority::equals)) {
            return true;
        }
        return authorityNames(authentication).contains(authority);
    }

    public boolean canAccessDepartment(UUID departmentId) {
        if (departmentId == null) {
            return false;
        }
        if (isScopeAdmin()) {
            return true;
        }
        UUID currentDepartmentId = currentDepartmentIdOrNull();
        return departmentId.equals(currentDepartmentId);
    }

    public UUID enforceDepartmentScope(UUID requestedDepartmentId) {
        if (isScopeAdmin()) {
            return requestedDepartmentId;
        }
        UUID currentDepartmentId = currentDepartmentIdOrNull();
        return currentDepartmentId != null ? currentDepartmentId : requestedDepartmentId;
    }

    public void assertCanAccessDepartment(UUID departmentId) {
        if (!canAccessDepartment(departmentId)) {
            throwAccessDenied();
        }
    }

    public boolean canAccessEquipmentScope(UUID responsibleDepartmentId, UUID physicalDepartmentId) {
        if (isScopeAdmin()) {
            return true;
        }
        UUID scopeDepartmentId = responsibleDepartmentId != null ? responsibleDepartmentId : physicalDepartmentId;
        return canAccessDepartment(scopeDepartmentId);
    }

    public void assertCanAccessEquipmentScope(UUID responsibleDepartmentId, UUID physicalDepartmentId) {
        if (!canAccessEquipmentScope(responsibleDepartmentId, physicalDepartmentId)) {
            throwAccessDenied();
        }
    }

    public Optional<UUID> currentEmployeeId() {
        UUID userId = currentUserIdOrNull();
        if (userId == null) {
            return Optional.empty();
        }
        return employeeRepository.findByUserIdAndIsDeletedFalse(userId)
                .map(com.toir.entity.users.Employee::getId);
    }

    public boolean canAccessEmployee(UUID employeeId) {
        if (employeeId == null) {
            return false;
        }
        if (isScopeAdmin()) {
            return true;
        }
        return currentEmployeeId()
                .map(employeeId::equals)
                .orElse(false);
    }

    public void assertCanAccessEmployee(UUID employeeId) {
        if (!canAccessEmployee(employeeId)) {
            throwAccessDenied();
        }
    }

    public boolean canAccessAssignedUser(UUID assignedUserId) {
        if (assignedUserId == null) {
            return false;
        }
        if (isScopeAdmin()) {
            return true;
        }
        return assignedUserId.equals(currentUserIdOrNull());
    }

    public void assertCanAccessAssignedUser(UUID assignedUserId) {
        if (!canAccessAssignedUser(assignedUserId)) {
            throwAccessDenied();
        }
    }

    public boolean canAccessWarehouse(UUID warehouseId) {
        if (warehouseId == null) {
            return false;
        }
        if (isScopeAdmin()) {
            return true;
        }
        return warehouseRepository.findByIdAndIsDeletedFalse(warehouseId)
                .map(warehouse -> {
                    if (warehouse.getDepartmentId() != null && canAccessDepartment(warehouse.getDepartmentId())) {
                        return true;
                    }
                    if (warehouse.getResponsibleId() != null && canAccessEmployee(warehouse.getResponsibleId())) {
                        return true;
                    }
                    return false;
                })
                .orElse(false);
    }

    public void assertCanAccessWarehouse(UUID warehouseId) {
        if (!canAccessWarehouse(warehouseId)) {
            throwAccessDenied();
        }
    }

    private Optional<UUID> parseUuid(String value) {
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private boolean hasWildcardPermission(AuthenticatedUser user) {
        return user.permissions() != null
                && user.permissions().stream()
                .filter(StringUtils::hasText)
                .anyMatch(PermissionConstants.WILDCARD::equals);
    }

    private Set<String> authorityNames(Authentication authentication) {
        if (authentication.getAuthorities() == null) {
            return Set.of();
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
    }

    private void throwAccessDenied() {
        throw new AccessDeniedException("Access denied by data scope");
    }
}
