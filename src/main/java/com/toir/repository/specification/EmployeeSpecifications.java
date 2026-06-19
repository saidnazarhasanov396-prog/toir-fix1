package com.toir.repository.specification;

import com.toir.dto.hr.EmployeeFilterRequest;
import com.toir.entity.Department;
import com.toir.entity.users.Brigade;
import com.toir.entity.users.Employee;
import com.toir.entity.users.EmployeeSpecialisation;
import com.toir.entity.users.EmployeeWorkRole;
import com.toir.entity.users.EmployeeWorkRoleAssignment;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class EmployeeSpecifications {

    private EmployeeSpecifications() {
    }

    public static Specification<Employee> byFilter(EmployeeFilterRequest filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get("isDeleted")));

            if (filter == null) {
                return cb.and(predicates.toArray(Predicate[]::new));
            }

            equalIfPresent(predicates, cb, root, "departmentId", filter.departmentId());
            equalIfPresent(predicates, cb, root, "brigadeId", filter.brigadeId());
            equalIfPresent(predicates, cb, root, "userId", filter.userId());
            equalIfPresent(predicates, cb, root, "specialisationId", filter.specialisationId());
            equalIfPresent(predicates, cb, root, "active", filter.activeOnly());
            dateFrom(predicates, cb, root, "hireDate", filter.hireDateFrom());
            dateTo(predicates, cb, root, "hireDate", filter.hireDateTo());
            dateFrom(predicates, cb, root, "terminatedDate", filter.terminatedDateFrom());
            dateTo(predicates, cb, root, "terminatedDate", filter.terminatedDateTo());

            containsIfPresent(predicates, cb, root.get("personnelNumber"), filter.personnelNumber());
            containsIfPresent(predicates, cb, root.get("firstName"), filter.firstName());
            containsIfPresent(predicates, cb, root.get("lastName"), filter.lastName());
            containsIfPresent(predicates, cb, root.get("middleName"), filter.middleName());
            containsIfPresent(predicates, cb, root.get("position"), filter.position());
            containsIfPresent(predicates, cb, root.get("grade"), filter.grade());
            containsIfPresent(predicates, cb, root.get("phone"), filter.phone());
            containsIfPresent(predicates, cb, root.get("email"), filter.email());

            String workRoleCode = trimToNull(filter.workRoleCode());
            if (workRoleCode != null) {
                predicates.add(workRoleCodeExists(root, query, cb, workRoleCode));
            }

            String search = trimToNull(filter.search());
            if (search != null) {
                predicates.add(globalSearch(root, query, cb, search));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static Predicate globalSearch(
            Root<Employee> root,
            jakarta.persistence.criteria.CriteriaQuery<?> query,
            CriteriaBuilder cb,
            String search
    ) {
        Join<Employee, EmployeeSpecialisation> specialisation = root.join("specialisation", JoinType.LEFT);
        List<Predicate> searchPredicates = new ArrayList<>();
        searchPredicates.add(contains(cb, root.get("personnelNumber"), search));
        searchPredicates.add(contains(cb, root.get("firstName"), search));
        searchPredicates.add(contains(cb, root.get("lastName"), search));
        searchPredicates.add(contains(cb, root.get("middleName"), search));
        searchPredicates.add(contains(cb, root.get("position"), search));
        searchPredicates.add(contains(cb, root.get("grade"), search));
        searchPredicates.add(contains(cb, root.get("phone"), search));
        searchPredicates.add(contains(cb, root.get("email"), search));
        searchPredicates.add(contains(cb, specialisation.get("nameRu"), search));
        searchPredicates.add(contains(cb, specialisation.get("nameEn"), search));
        searchPredicates.add(contains(cb, specialisation.get("nameUz"), search));
        searchPredicates.add(departmentDisplayContains(root, query, cb, search));
        searchPredicates.add(brigadeDisplayContains(root, query, cb, search));
        searchPredicates.add(workRoleDisplayContains(root, query, cb, search));
        searchPredicates.add(namePairContains(root, cb, search));
        return cb.or(searchPredicates.toArray(Predicate[]::new));
    }

    private static Predicate departmentDisplayContains(
            Root<Employee> root,
            jakarta.persistence.criteria.CriteriaQuery<?> query,
            CriteriaBuilder cb,
            String search
    ) {
        Subquery<Integer> subquery = query.subquery(Integer.class);
        Root<Department> department = subquery.from(Department.class);
        subquery.select(cb.literal(1));
        subquery.where(
                cb.equal(department.get("id"), root.get("departmentId")),
                cb.isFalse(department.get("isDeleted")),
                cb.or(
                        contains(cb, department.get("code"), search),
                        contains(cb, department.get("name"), search),
                        contains(cb, department.get("nameEn"), search),
                        contains(cb, department.get("nameUz"), search)
                )
        );
        return cb.exists(subquery);
    }

    private static Predicate brigadeDisplayContains(
            Root<Employee> root,
            jakarta.persistence.criteria.CriteriaQuery<?> query,
            CriteriaBuilder cb,
            String search
    ) {
        Subquery<Integer> subquery = query.subquery(Integer.class);
        Root<Brigade> brigade = subquery.from(Brigade.class);
        subquery.select(cb.literal(1));
        subquery.where(
                cb.equal(brigade.get("id"), root.get("brigadeId")),
                cb.isFalse(brigade.get("isDeleted")),
                cb.or(
                        contains(cb, brigade.get("code"), search),
                        contains(cb, brigade.get("name"), search),
                        contains(cb, brigade.get("specialization"), search)
                )
        );
        return cb.exists(subquery);
    }

    private static Predicate workRoleCodeExists(
            Root<Employee> root,
            jakarta.persistence.criteria.CriteriaQuery<?> query,
            CriteriaBuilder cb,
            String workRoleCode
    ) {
        Subquery<Integer> subquery = query.subquery(Integer.class);
        Root<EmployeeWorkRoleAssignment> assignment = subquery.from(EmployeeWorkRoleAssignment.class);
        Join<EmployeeWorkRoleAssignment, EmployeeWorkRole> workRole = assignment.join("workRole", JoinType.INNER);
        subquery.select(cb.literal(1));
        subquery.where(
                cb.equal(assignment.get("employeeId"), root.get("id")),
                cb.isFalse(assignment.get("isDeleted")),
                cb.isFalse(workRole.get("isDeleted")),
                cb.isTrue(workRole.get("active")),
                cb.equal(cb.upper(coalesce(cb, workRole.get("code"))), workRoleCode.toUpperCase(Locale.ROOT))
        );
        return cb.exists(subquery);
    }

    private static Predicate workRoleDisplayContains(
            Root<Employee> root,
            jakarta.persistence.criteria.CriteriaQuery<?> query,
            CriteriaBuilder cb,
            String search
    ) {
        Subquery<Integer> subquery = query.subquery(Integer.class);
        Root<EmployeeWorkRoleAssignment> assignment = subquery.from(EmployeeWorkRoleAssignment.class);
        Join<EmployeeWorkRoleAssignment, EmployeeWorkRole> workRole = assignment.join("workRole", JoinType.INNER);
        subquery.select(cb.literal(1));
        subquery.where(
                cb.equal(assignment.get("employeeId"), root.get("id")),
                cb.isFalse(assignment.get("isDeleted")),
                cb.isFalse(workRole.get("isDeleted")),
                cb.isTrue(workRole.get("active")),
                cb.or(
                        contains(cb, workRole.get("code"), search),
                        contains(cb, workRole.get("name"), search),
                        contains(cb, workRole.get("nameEn"), search),
                        contains(cb, workRole.get("nameUz"), search)
                )
        );
        return cb.exists(subquery);
    }

    private static Predicate namePairContains(Root<Employee> root, CriteriaBuilder cb, String search) {
        String[] parts = search.split("\\s+");
        if (parts.length < 2) {
            return cb.disjunction();
        }
        String first = parts[0];
        String second = parts[1];
        return cb.or(
                cb.and(contains(cb, root.get("lastName"), first), contains(cb, root.get("firstName"), second)),
                cb.and(contains(cb, root.get("firstName"), first), contains(cb, root.get("lastName"), second)),
                cb.and(contains(cb, root.get("firstName"), first), contains(cb, root.get("middleName"), second)),
                cb.and(contains(cb, root.get("middleName"), first), contains(cb, root.get("firstName"), second)),
                cb.and(contains(cb, root.get("lastName"), first), contains(cb, root.get("middleName"), second)),
                cb.and(contains(cb, root.get("middleName"), first), contains(cb, root.get("lastName"), second))
        );
    }

    private static void containsIfPresent(
            List<Predicate> predicates,
            CriteriaBuilder cb,
            Expression<String> expression,
            String value
    ) {
        String normalized = trimToNull(value);
        if (normalized != null) {
            predicates.add(contains(cb, expression, normalized));
        }
    }

    private static Predicate contains(CriteriaBuilder cb, Expression<String> expression, String value) {
        return cb.like(cb.lower(coalesce(cb, expression)), containsPattern(value));
    }

    private static Expression<String> coalesce(CriteriaBuilder cb, Expression<String> expression) {
        return cb.coalesce(expression, "");
    }

    private static String containsPattern(String value) {
        return "%" + value.toLowerCase(Locale.ROOT) + "%";
    }

    private static void dateFrom(
            List<Predicate> predicates,
            CriteriaBuilder cb,
            Root<Employee> root,
            String field,
            LocalDate value
    ) {
        if (value != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get(field), value));
        }
    }

    private static void dateTo(
            List<Predicate> predicates,
            CriteriaBuilder cb,
            Root<Employee> root,
            String field,
            LocalDate value
    ) {
        if (value != null) {
            predicates.add(cb.lessThanOrEqualTo(root.get(field), value));
        }
    }

    private static void equalIfPresent(
            List<Predicate> predicates,
            CriteriaBuilder cb,
            Root<Employee> root,
            String field,
            Object value
    ) {
        if (value != null) {
            predicates.add(cb.equal(root.get(field), value));
        }
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
