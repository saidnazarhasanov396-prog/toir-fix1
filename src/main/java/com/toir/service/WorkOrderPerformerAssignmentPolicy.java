package com.toir.service;

import com.toir.entity.users.Brigade;
import com.toir.entity.users.BrigadeMember;
import com.toir.entity.users.Employee;
import com.toir.exception.RestException;
import com.toir.repository.projects.BrigadeMemberRepository;
import com.toir.repository.users.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WorkOrderPerformerAssignmentPolicy {
    public static final String FIELDS_CONFLICT = "WORK_ORDER_PERFORMER_FIELDS_CONFLICT";
    public static final String EMPLOYEE_NOT_FOUND = "WORK_ORDER_PERFORMER_EMPLOYEE_NOT_FOUND";
    public static final String EMPLOYEE_INACTIVE = "WORK_ORDER_PERFORMER_EMPLOYEE_INACTIVE";
    public static final String EMPLOYEE_DEPARTMENT_MISMATCH = "WORK_ORDER_PERFORMER_EMPLOYEE_DEPARTMENT_MISMATCH";
    public static final String MEMBER_NOT_FOUND = "WORK_ORDER_PERFORMER_MEMBER_NOT_FOUND";
    public static final String MEMBER_INACTIVE = "WORK_ORDER_PERFORMER_MEMBER_INACTIVE";
    public static final String BRIGADE_INACTIVE = "WORK_ORDER_PERFORMER_BRIGADE_INACTIVE";
    public static final String MEMBER_DEPARTMENT_MISMATCH = "WORK_ORDER_PERFORMER_MEMBER_DEPARTMENT_MISMATCH";
    public static final String MEMBER_EMPLOYEE_MISMATCH = "WORK_ORDER_PERFORMER_MEMBER_EMPLOYEE_MISMATCH";
    public static final String EMPLOYEE_AMBIGUOUS = "WORK_ORDER_PERFORMER_EMPLOYEE_AMBIGUOUS";
    public static final String USER_HAS_NO_EMPLOYEE = "WORK_ORDER_PERFORMER_USER_HAS_NO_EMPLOYEE";

    private final EmployeeRepository employeeRepository;
    private final BrigadeMemberRepository brigadeMemberRepository;

    public ResolvedAssignment resolve(UUID legacyMemberId, UUID employeeId, UUID memberId, UUID departmentId) {
        if (legacyMemberId != null && (employeeId != null || memberId != null)) {
            throw RestException.badRequest("performerId cannot be combined with canonical performer fields", FIELDS_CONFLICT);
        }
        if (legacyMemberId == null && employeeId == null && memberId != null) {
            throw RestException.badRequest("performerEmployeeId is required with performerBrigadeMemberId", FIELDS_CONFLICT);
        }
        if (legacyMemberId == null && employeeId == null) return ResolvedAssignment.empty();
        if (legacyMemberId != null) {
            BrigadeMember member = requireMember(legacyMemberId, departmentId);
            Employee employee = uniqueEmployeeForUser(member.getUserId());
            validateEmployee(employee, departmentId);
            validatePair(employee, member);
            return new ResolvedAssignment(employee, member);
        }
        Employee employee = requireEmployee(employeeId, departmentId);
        BrigadeMember member = memberId == null ? null : requireMember(memberId, departmentId);
        if (member != null) validatePair(employee, member);
        return new ResolvedAssignment(employee, member);
    }

    public ResolvedAssignment resolveLegacyOwner(UUID ownerUserId, UUID employeeId, UUID memberId, UUID departmentId) {
        if (ownerUserId != null && (employeeId != null || memberId != null)) {
            throw RestException.badRequest("ownerId cannot be combined with canonical performer fields", FIELDS_CONFLICT);
        }
        if (ownerUserId == null) return resolve(null, employeeId, memberId, departmentId);
        Employee employee = uniqueEmployeeForUser(ownerUserId);
        validateEmployee(employee, departmentId);
        List<BrigadeMember> eligible = brigadeMemberRepository.findAllByUserIdAndIsDeletedFalse(ownerUserId).stream()
                .filter(BrigadeMember::isActive)
                .filter(m -> m.getBrigade() != null && m.getBrigade().isActive() && !m.getBrigade().isDeleted())
                .filter(m -> Objects.equals(departmentId, m.getBrigade().getDepartmentId()))
                .toList();
        return new ResolvedAssignment(employee, eligible.size() == 1 ? eligible.getFirst() : null);
    }

    private Employee requireEmployee(UUID id, UUID departmentId) {
        Employee employee = employeeRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> notFound("Employee not found: " + id, EMPLOYEE_NOT_FOUND));
        validateEmployee(employee, departmentId);
        return employee;
    }

    private Employee uniqueEmployeeForUser(UUID userId) {
        List<Employee> employees = employeeRepository.findAllByUserIdAndIsDeletedFalse(userId);
        if (employees.isEmpty()) throw notFound("No Employee is linked to user: " + userId, USER_HAS_NO_EMPLOYEE);
        if (employees.size() > 1) throw RestException.conflict("Multiple Employees are linked to user: " + userId, EMPLOYEE_AMBIGUOUS);
        return employees.getFirst();
    }

    private void validateEmployee(Employee employee, UUID departmentId) {
        if (!employee.isActive()) throw RestException.badRequest("Employee is inactive: " + employee.getId(), EMPLOYEE_INACTIVE);
        if (!Objects.equals(departmentId, employee.getDepartmentId())) {
            throw RestException.badRequest("Employee does not belong to work order department: " + employee.getId(), EMPLOYEE_DEPARTMENT_MISMATCH);
        }
    }

    private BrigadeMember requireMember(UUID id, UUID departmentId) {
        BrigadeMember member = brigadeMemberRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> notFound("Brigade member not found: " + id, MEMBER_NOT_FOUND));
        if (!member.isActive()) throw RestException.badRequest("Brigade member is inactive: " + id, MEMBER_INACTIVE);
        Brigade brigade = member.getBrigade();
        if (brigade == null || brigade.isDeleted() || !brigade.isActive()) {
            throw RestException.badRequest("Brigade is inactive for member: " + id, BRIGADE_INACTIVE);
        }
        if (!Objects.equals(departmentId, brigade.getDepartmentId())) {
            throw RestException.badRequest("Brigade member does not belong to work order department: " + id, MEMBER_DEPARTMENT_MISMATCH);
        }
        return member;
    }

    private void validatePair(Employee employee, BrigadeMember member) {
        if (employee.getUserId() == null || !Objects.equals(employee.getUserId(), member.getUserId())) {
            throw RestException.badRequest("Brigade member does not belong to employee: " + member.getId(), MEMBER_EMPLOYEE_MISMATCH);
        }
    }

    private RestException notFound(String message, String code) {
        return new RestException(message, HttpStatus.NOT_FOUND, code);
    }

    public record ResolvedAssignment(Employee employee, BrigadeMember brigadeMember) {
        public static ResolvedAssignment empty() { return new ResolvedAssignment(null, null); }
    }
}
