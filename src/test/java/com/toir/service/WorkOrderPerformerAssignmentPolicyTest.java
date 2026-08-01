package com.toir.service;

import com.toir.entity.users.Brigade;
import com.toir.entity.users.BrigadeMember;
import com.toir.entity.users.Employee;
import com.toir.exception.RestException;
import com.toir.repository.projects.BrigadeMemberRepository;
import com.toir.repository.users.EmployeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkOrderPerformerAssignmentPolicyTest {

    @Mock EmployeeRepository employeeRepository;
    @Mock BrigadeMemberRepository brigadeMemberRepository;

    private WorkOrderPerformerAssignmentPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new WorkOrderPerformerAssignmentPolicy(employeeRepository, brigadeMemberRepository);
    }

    @Test
    void employeeOnlyAssignmentDoesNotGuessMembership() {
        UUID departmentId = UUID.randomUUID();
        Employee employee = employee(departmentId, null);
        when(employeeRepository.findByIdAndIsDeletedFalse(employee.getId())).thenReturn(Optional.of(employee));

        var resolved = policy.resolve(null, employee.getId(), null, departmentId);

        assertThat(resolved.employee()).isSameAs(employee);
        assertThat(resolved.brigadeMember()).isNull();
    }

    @Test
    void explicitMembershipMustBelongToEmployeeUser() {
        UUID departmentId = UUID.randomUUID();
        Employee employee = employee(departmentId, UUID.randomUUID());
        BrigadeMember member = member(departmentId, UUID.randomUUID());
        when(employeeRepository.findByIdAndIsDeletedFalse(employee.getId())).thenReturn(Optional.of(employee));
        when(brigadeMemberRepository.findByIdAndIsDeletedFalse(member.getId())).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> policy.resolve(null, employee.getId(), member.getId(), departmentId))
                .isInstanceOf(RestException.class)
                .satisfies(error -> assertThat(((RestException) error).getErrorCode())
                        .isEqualTo("WORK_ORDER_PERFORMER_MEMBER_EMPLOYEE_MISMATCH"));
    }

    @Test
    void legacyMemberIdResolvesOnlyOneEmployeeForItsUser() {
        UUID departmentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        BrigadeMember member = member(departmentId, userId);
        Employee first = employee(departmentId, userId);
        Employee second = employee(departmentId, userId);
        when(brigadeMemberRepository.findByIdAndIsDeletedFalse(member.getId())).thenReturn(Optional.of(member));
        when(employeeRepository.findAllByUserIdAndIsDeletedFalse(userId)).thenReturn(List.of(first, second));

        assertThatThrownBy(() -> policy.resolve(member.getId(), null, null, departmentId))
                .isInstanceOf(RestException.class)
                .satisfies(error -> assertThat(((RestException) error).getErrorCode())
                        .isEqualTo("WORK_ORDER_PERFORMER_EMPLOYEE_AMBIGUOUS"));
    }

    @Test
    void legacyOwnerWithMultipleMembershipsKeepsEmployeeOnlyContext() {
        UUID departmentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Employee employee = employee(departmentId, userId);
        when(employeeRepository.findAllByUserIdAndIsDeletedFalse(userId)).thenReturn(List.of(employee));
        when(brigadeMemberRepository.findAllByUserIdAndIsDeletedFalse(userId))
                .thenReturn(List.of(member(departmentId, userId), member(departmentId, userId)));

        var resolved = policy.resolveLegacyOwner(userId, null, null, departmentId);

        assertThat(resolved.employee()).isSameAs(employee);
        assertThat(resolved.brigadeMember()).isNull();
    }

    @Test
    void conflictingLegacyAndCanonicalFieldsAreRejected() {
        assertThatThrownBy(() -> policy.resolve(UUID.randomUUID(), UUID.randomUUID(), null, UUID.randomUUID()))
                .isInstanceOf(RestException.class)
                .satisfies(error -> assertThat(((RestException) error).getErrorCode())
                        .isEqualTo("WORK_ORDER_PERFORMER_FIELDS_CONFLICT"));
    }

    private Employee employee(UUID departmentId, UUID userId) {
        Employee employee = new Employee();
        employee.setId(UUID.randomUUID());
        employee.setDepartmentId(departmentId);
        employee.setUserId(userId);
        employee.setActive(true);
        employee.setFirstName("Ali");
        employee.setLastName("Valiyev");
        return employee;
    }

    private BrigadeMember member(UUID departmentId, UUID userId) {
        Brigade brigade = new Brigade();
        brigade.setId(UUID.randomUUID());
        brigade.setDepartmentId(departmentId);
        brigade.setActive(true);
        BrigadeMember member = new BrigadeMember();
        member.setId(UUID.randomUUID());
        member.setUserId(userId);
        member.setActive(true);
        member.setBrigade(brigade);
        return member;
    }
}
