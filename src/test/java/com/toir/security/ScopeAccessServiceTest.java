package com.toir.security;

import com.toir.entity.users.Employee;
import com.toir.repository.users.EmployeeRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScopeAccessServiceTest {

    @Mock
    private EmployeeRepository employeeRepository;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void systemAdminPrimaryRoleBypassesDepartmentMismatch() {
        UUID ownDepartment = UUID.randomUUID();
        UUID otherDepartment = UUID.randomUUID();
        authenticate(user(UUID.randomUUID(), ownDepartment, "SYSTEM_ADMIN", List.of()), List.of());
        ScopeAccessService service = service();

        assertThat(service.isScopeAdmin()).isTrue();
        assertThat(service.canAccessDepartment(otherDepartment)).isTrue();
        assertThat(service.enforceDepartmentScope(otherDepartment)).isEqualTo(otherDepartment);
    }

    @Test
    void systemAdminAuthorityBypassesDepartmentMismatch() {
        UUID ownDepartment = UUID.randomUUID();
        UUID otherDepartment = UUID.randomUUID();
        authenticate(user(UUID.randomUUID(), ownDepartment, "VIEWER", List.of()), List.of("SYSTEM_ADMIN"));
        ScopeAccessService service = service();

        assertThat(service.isScopeAdmin()).isTrue();
        assertThat(service.canAccessDepartment(otherDepartment)).isTrue();
    }

    @Test
    void wildcardAuthorityBypassesDepartmentMismatch() {
        UUID ownDepartment = UUID.randomUUID();
        UUID otherDepartment = UUID.randomUUID();
        authenticate(user(UUID.randomUUID(), ownDepartment, "VIEWER", List.of()), List.of(PermissionConstants.WILDCARD));
        ScopeAccessService service = service();

        assertThat(service.isScopeAdmin()).isTrue();
        assertThat(service.canAccessDepartment(otherDepartment)).isTrue();
    }

    @Test
    void normalUserCanAccessSameDepartmentOnly() {
        UUID ownDepartment = UUID.randomUUID();
        UUID otherDepartment = UUID.randomUUID();
        authenticate(user(UUID.randomUUID(), ownDepartment, "VIEWER", List.of()), List.of(PermissionConstants.READ_LEGACY));
        ScopeAccessService service = service();

        assertThat(service.canAccessDepartment(ownDepartment)).isTrue();
        assertThat(service.canAccessDepartment(otherDepartment)).isFalse();
    }

    @Test
    void nonAdminRequestedDepartmentIsOverriddenToOwnDepartment() {
        UUID ownDepartment = UUID.randomUUID();
        UUID requestedDepartment = UUID.randomUUID();
        authenticate(user(UUID.randomUUID(), ownDepartment, "VIEWER", List.of()), List.of());

        assertThat(service().enforceDepartmentScope(requestedDepartment)).isEqualTo(ownDepartment);
        assertThat(service().enforceDepartmentScope(null)).isEqualTo(ownDepartment);
    }

    @Test
    void adminRequestedDepartmentIsPreserved() {
        UUID requestedDepartment = UUID.randomUUID();
        authenticate(user(UUID.randomUUID(), UUID.randomUUID(), "SYSTEM_ADMIN", List.of()), List.of());

        assertThat(service().enforceDepartmentScope(requestedDepartment)).isEqualTo(requestedDepartment);
        assertThat(service().enforceDepartmentScope(null)).isNull();
    }

    @Test
    void currentEmployeeIdReturnsLinkedEmployee() {
        UUID userId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        authenticate(user(userId, UUID.randomUUID(), "VIEWER", List.of()), List.of());
        when(employeeRepository.findByUserIdAndIsDeletedFalse(userId)).thenReturn(Optional.of(employee(employeeId, userId)));

        assertThat(service().currentEmployeeId()).contains(employeeId);
    }

    @Test
    void currentEmployeeIdReturnsEmptyWhenNoEmployeeIsLinked() {
        UUID userId = UUID.randomUUID();
        authenticate(user(userId, UUID.randomUUID(), "VIEWER", List.of()), List.of());
        when(employeeRepository.findByUserIdAndIsDeletedFalse(userId)).thenReturn(Optional.empty());

        assertThat(service().currentEmployeeId()).isEmpty();
    }

    @Test
    void canAccessEmployeeAllowsOwnEmployeeOnlyForNonAdmin() {
        UUID userId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        authenticate(user(userId, UUID.randomUUID(), "VIEWER", List.of()), List.of());
        when(employeeRepository.findByUserIdAndIsDeletedFalse(userId)).thenReturn(Optional.of(employee(employeeId, userId)));
        ScopeAccessService service = service();

        assertThat(service.canAccessEmployee(employeeId)).isTrue();
        assertThat(service.canAccessEmployee(UUID.randomUUID())).isFalse();
    }

    @Test
    void canAccessAssignedUserAllowsCurrentUserOnlyForNonAdmin() {
        UUID userId = UUID.randomUUID();
        authenticate(user(userId, UUID.randomUUID(), "VIEWER", List.of()), List.of());
        ScopeAccessService service = service();

        assertThat(service.canAccessAssignedUser(userId)).isTrue();
        assertThat(service.canAccessAssignedUser(UUID.randomUUID())).isFalse();
    }

    @Test
    void warehouseAccessIsConservativeUntilAssignmentModelExists() {
        authenticate(user(UUID.randomUUID(), UUID.randomUUID(), "VIEWER", List.of()), List.of());

        assertThat(service().canAccessWarehouse(UUID.randomUUID())).isFalse();
    }

    @Test
    void adminAndWildcardCanAccessWarehouse() {
        authenticate(user(UUID.randomUUID(), UUID.randomUUID(), "VIEWER", List.of()), List.of(PermissionConstants.WILDCARD));

        assertThat(service().canAccessWarehouse(UUID.randomUUID())).isTrue();
    }

    @Test
    void nullAuthenticationCannotAccessScopeAndAssertDenies() {
        SecurityContextHolder.clearContext();
        ScopeAccessService service = service();

        assertThat(service.currentUser()).isEmpty();
        assertThat(service.currentUserIdOrNull()).isNull();
        assertThat(service.currentDepartmentIdOrNull()).isNull();
        assertThat(service.isScopeAdmin()).isFalse();
        assertThat(service.canAccessDepartment(UUID.randomUUID())).isFalse();
        assertThatThrownBy(() -> service.assertCanAccessDepartment(UUID.randomUUID()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void assertMethodsDenyNullTargetsForNonAdmin() {
        authenticate(user(UUID.randomUUID(), UUID.randomUUID(), "VIEWER", List.of()), List.of());
        ScopeAccessService service = service();

        assertThat(service.canAccessDepartment(null)).isFalse();
        assertThat(service.canAccessEmployee(null)).isFalse();
        assertThat(service.canAccessAssignedUser(null)).isFalse();
        assertThat(service.canAccessWarehouse(null)).isFalse();
        assertThatThrownBy(() -> service.assertCanAccessDepartment(null))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.assertCanAccessEmployee(null))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.assertCanAccessAssignedUser(null))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.assertCanAccessWarehouse(null))
                .isInstanceOf(AccessDeniedException.class);
    }

    private ScopeAccessService service() {
        return new ScopeAccessService(employeeRepository);
    }

    private void authenticate(AuthenticatedUser user, List<String> authorities) {
        SecurityContextHolder.getContext().setAuthentication(authentication(user, authorities));
    }

    private Authentication authentication(AuthenticatedUser user, List<String> authorities) {
        return new UsernamePasswordAuthenticationToken(
                user,
                null,
                authorities.stream().map(SimpleGrantedAuthority::new).toList()
        );
    }

    private AuthenticatedUser user(UUID userId, UUID departmentId, String primaryRoleCode, List<String> permissions) {
        return new AuthenticatedUser(
                userId.toString(),
                "user",
                "user@example.com",
                "User",
                departmentId.toString(),
                primaryRoleCode,
                permissions
        );
    }

    private Employee employee(UUID employeeId, UUID userId) {
        Employee employee = new Employee();
        employee.setId(employeeId);
        employee.setUserId(userId);
        employee.setPersonnelNumber("EMP-" + employeeId.toString().substring(0, 8));
        employee.setFirstName("Ali");
        employee.setLastName("Valiyev");
        employee.setPosition("Mechanic");
        employee.setHireDate(LocalDate.of(2026, 1, 1));
        return employee;
    }
}
