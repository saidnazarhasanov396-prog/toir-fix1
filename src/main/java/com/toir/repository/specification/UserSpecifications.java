package com.toir.repository.specification;

import com.toir.dto.user.UserFilterRequest;
import com.toir.entity.Department;
import com.toir.entity.users.Role;
import com.toir.entity.users.User;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.SetJoin;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class UserSpecifications {

    private UserSpecifications() {
    }

    public static Specification<User> byFilter(UserFilterRequest filter) {
        return (root, query, cb) -> {
            query.distinct(true);
            Join<User, Department> department = root.join("department", JoinType.LEFT);
            Join<User, Role> primaryRole = root.join("primaryRole", JoinType.LEFT);
            SetJoin<User, Role> assignedRole = root.joinSet("roles", JoinType.LEFT);

            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get("isDeleted")));

            if (filter == null) {
                return cb.and(predicates.toArray(Predicate[]::new));
            }

            equalIfPresent(predicates, cb, root, "status", filter.status());
            equalIfPresent(predicates, cb, root, "departmentId", filter.departmentId());
            containsIfPresent(predicates, cb, root.get("username"), filter.username());
            containsIfPresent(predicates, cb, root.get("email"), filter.email());
            containsIfPresent(predicates, cb, root.get("fullName"), filter.fullName());
            containsIfPresent(predicates, cb, root.get("position"), filter.position());
            containsIfPresent(predicates, cb, root.get("phone"), filter.phone());
            instantFrom(predicates, cb, root, "lastLoginAt", filter.lastLoginFrom());
            instantTo(predicates, cb, root, "lastLoginAt", filter.lastLoginTo());

            if (filter.primaryRoleId() != null) {
                predicates.add(cb.and(
                        cb.isFalse(primaryRole.get("isDeleted")),
                        cb.equal(primaryRole.get("id"), filter.primaryRoleId())
                ));
            }

            String primaryRoleCode = trimToNull(filter.primaryRoleCode());
            if (primaryRoleCode != null) {
                predicates.add(cb.and(
                        cb.isFalse(primaryRole.get("isDeleted")),
                        cb.equal(cb.upper(cb.coalesce(primaryRole.get("code"), "")), primaryRoleCode.toUpperCase(Locale.ROOT))
                ));
            }

            if (filter.roleId() != null) {
                predicates.add(cb.and(
                        cb.isFalse(assignedRole.get("isDeleted")),
                        cb.equal(assignedRole.get("id"), filter.roleId())
                ));
            }

            String roleCode = trimToNull(filter.roleCode());
            if (roleCode != null) {
                predicates.add(cb.and(
                        cb.isFalse(assignedRole.get("isDeleted")),
                        cb.equal(cb.upper(cb.coalesce(assignedRole.get("code"), "")), roleCode.toUpperCase(Locale.ROOT))
                ));
            }

            String search = trimToNull(filter.search());
            if (search != null) {
                predicates.add(globalSearch(root, cb, department, primaryRole, assignedRole, search));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static Predicate globalSearch(
            Root<User> root,
            CriteriaBuilder cb,
            Join<User, Department> department,
            Join<User, Role> primaryRole,
            SetJoin<User, Role> assignedRole,
            String search
    ) {
        return cb.or(
                contains(cb, root.get("username"), search),
                contains(cb, root.get("email"), search),
                contains(cb, root.get("fullName"), search),
                contains(cb, root.get("position"), search),
                contains(cb, root.get("phone"), search),
                cb.and(
                        cb.isFalse(department.get("isDeleted")),
                        cb.or(
                                contains(cb, department.get("code"), search),
                                contains(cb, department.get("name"), search),
                                contains(cb, department.get("nameEn"), search),
                                contains(cb, department.get("nameUz"), search)
                        )
                ),
                cb.and(
                        cb.isFalse(primaryRole.get("isDeleted")),
                        cb.or(
                                contains(cb, primaryRole.get("code"), search),
                                contains(cb, primaryRole.get("name"), search),
                                contains(cb, primaryRole.get("nameEn"), search),
                                contains(cb, primaryRole.get("nameUz"), search)
                        )
                ),
                cb.and(
                        cb.isFalse(assignedRole.get("isDeleted")),
                        cb.or(
                                contains(cb, assignedRole.get("code"), search),
                                contains(cb, assignedRole.get("name"), search),
                                contains(cb, assignedRole.get("nameEn"), search),
                                contains(cb, assignedRole.get("nameUz"), search)
                        )
                )
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
        return cb.like(cb.lower(cb.coalesce(expression, "")), containsPattern(value));
    }

    private static String containsPattern(String value) {
        return "%" + value.toLowerCase(Locale.ROOT) + "%";
    }

    private static void instantFrom(
            List<Predicate> predicates,
            CriteriaBuilder cb,
            Root<User> root,
            String field,
            Instant value
    ) {
        if (value != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get(field), value));
        }
    }

    private static void instantTo(
            List<Predicate> predicates,
            CriteriaBuilder cb,
            Root<User> root,
            String field,
            Instant value
    ) {
        if (value != null) {
            predicates.add(cb.lessThanOrEqualTo(root.get(field), value));
        }
    }

    private static void equalIfPresent(
            List<Predicate> predicates,
            CriteriaBuilder cb,
            Root<User> root,
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
