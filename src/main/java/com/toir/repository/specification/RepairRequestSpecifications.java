package com.toir.repository.specification;

import com.toir.dto.repairrequest.RepairRequestFilterRequest;
import com.toir.entity.Department;
import com.toir.entity.Location;
import com.toir.entity.defects.Defect;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.repair.RepairRequest;
import com.toir.entity.users.User;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class RepairRequestSpecifications {

    private RepairRequestSpecifications() {
    }

    public static Specification<RepairRequest> byFilter(RepairRequestFilterRequest filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get("isDeleted")));

            if (filter == null) {
                return cb.and(predicates.toArray(Predicate[]::new));
            }

            equalIfPresent(predicates, cb, root, "status", filter.status());
            equalIfPresent(predicates, cb, root, "departmentId", filter.departmentId());
            equalIfPresent(predicates, cb, root, "equipmentId", filter.equipmentId());
            equalIfPresent(predicates, cb, root, "priority", filter.priority());
            equalIfPresent(predicates, cb, root, "templateId", filter.templateId());
            equalIfPresent(predicates, cb, root, "locationId", filter.locationId());
            equalIfPresent(predicates, cb, root, "reporterId", filter.reporterId());
            equalIfPresent(predicates, cb, root, "assignedToId", filter.assignedToId());
            equalIfPresent(predicates, cb, root, "criticality", filter.criticality());
            equalIfPresent(predicates, cb, root, "source", filter.source());

            instantFrom(predicates, cb, root, "detectedAt", filter.detectedAtFrom());
            instantTo(predicates, cb, root, "detectedAt", filter.detectedAtTo());
            instantFrom(predicates, cb, root, "targetCompletionAt", filter.targetCompletionAtFrom());
            instantTo(predicates, cb, root, "targetCompletionAt", filter.targetCompletionAtTo());
            instantFrom(predicates, cb, root, "actualCompletionAt", filter.actualCompletionAtFrom());
            instantTo(predicates, cb, root, "actualCompletionAt", filter.actualCompletionAtTo());
            instantFrom(predicates, cb, root, "reactedAt", filter.reactedAtFrom());
            instantTo(predicates, cb, root, "reactedAt", filter.reactedAtTo());

            containsIfPresent(predicates, cb, root.get("number"), filter.number());
            containsIfPresent(predicates, cb, root.get("title"), filter.title());
            containsIfPresent(predicates, cb, root.get("description"), filter.description());
            containsIfPresent(predicates, cb, root.get("rejectionReason"), filter.rejectionReason());
            containsIfPresent(predicates, cb, root.get("clarificationReason"), filter.clarificationReason());
            containsIfPresent(predicates, cb, root.get("closeResult"), filter.closeResult());

            if (filter.hasLinkedDefects() != null) {
                Predicate exists = linkedDefectExists(root, query, cb);
                predicates.add(filter.hasLinkedDefects() ? exists : cb.not(exists));
            }
            if (filter.hasLinkedWorkOrders() != null) {
                Predicate exists = linkedWorkOrderExists(root, query, cb);
                predicates.add(filter.hasLinkedWorkOrders() ? exists : cb.not(exists));
            }

            String search = trimToNull(filter.search());
            if (search != null) {
                predicates.add(globalSearch(root, query, cb, search));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static Predicate globalSearch(
            Root<RepairRequest> root,
            jakarta.persistence.criteria.CriteriaQuery<?> query,
            CriteriaBuilder cb,
            String search
    ) {
        return cb.or(
                contains(cb, root.get("number"), search),
                contains(cb, root.get("title"), search),
                contains(cb, root.get("description"), search),
                contains(cb, root.get("rejectionReason"), search),
                contains(cb, root.get("clarificationReason"), search),
                contains(cb, root.get("closeResult"), search),
                equipmentDisplayContains(root, query, cb, search),
                departmentDisplayContains(root, query, cb, search),
                locationDisplayContains(root, query, cb, search),
                userDisplayContains(root, query, cb, "reporterId", search),
                userDisplayContains(root, query, cb, "assignedToId", search)
        );
    }

    private static Predicate equipmentDisplayContains(
            Root<RepairRequest> root,
            jakarta.persistence.criteria.CriteriaQuery<?> query,
            CriteriaBuilder cb,
            String search
    ) {
        Subquery<Integer> subquery = query.subquery(Integer.class);
        Root<Equipment> equipment = subquery.from(Equipment.class);
        subquery.select(cb.literal(1));
        subquery.where(
                cb.equal(equipment.get("id"), root.get("equipmentId")),
                cb.isFalse(equipment.get("isDeleted")),
                cb.or(
                        contains(cb, equipment.get("code"), search),
                        contains(cb, equipment.get("name"), search),
                        contains(cb, equipment.get("inventoryNumber"), search),
                        contains(cb, equipment.get("technicalNumber"), search),
                        contains(cb, equipment.get("serialNumber"), search),
                        contains(cb, equipment.get("model"), search),
                        contains(cb, equipment.get("manufacturer"), search),
                        contains(cb, equipment.get("description"), search)
                )
        );
        return cb.exists(subquery);
    }

    private static Predicate departmentDisplayContains(
            Root<RepairRequest> root,
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

    private static Predicate locationDisplayContains(
            Root<RepairRequest> root,
            jakarta.persistence.criteria.CriteriaQuery<?> query,
            CriteriaBuilder cb,
            String search
    ) {
        Subquery<Integer> subquery = query.subquery(Integer.class);
        Root<Location> location = subquery.from(Location.class);
        subquery.select(cb.literal(1));
        subquery.where(
                cb.equal(location.get("id"), root.get("locationId")),
                cb.isFalse(location.get("isDeleted")),
                cb.or(
                        contains(cb, location.get("code"), search),
                        contains(cb, location.get("name"), search),
                        contains(cb, location.get("nameEn"), search),
                        contains(cb, location.get("nameUz"), search),
                        contains(cb, location.get("description"), search)
                )
        );
        return cb.exists(subquery);
    }

    private static Predicate userDisplayContains(
            Root<RepairRequest> root,
            jakarta.persistence.criteria.CriteriaQuery<?> query,
            CriteriaBuilder cb,
            String userIdField,
            String search
    ) {
        Subquery<Integer> subquery = query.subquery(Integer.class);
        Root<User> user = subquery.from(User.class);
        subquery.select(cb.literal(1));
        subquery.where(
                cb.equal(user.get("id"), root.get(userIdField)),
                cb.isFalse(user.get("isDeleted")),
                cb.or(
                        contains(cb, user.get("username"), search),
                        contains(cb, user.get("email"), search),
                        contains(cb, user.get("fullName"), search),
                        contains(cb, user.get("phone"), search)
                )
        );
        return cb.exists(subquery);
    }

    private static Predicate linkedDefectExists(
            Root<RepairRequest> root,
            jakarta.persistence.criteria.CriteriaQuery<?> query,
            CriteriaBuilder cb
    ) {
        Subquery<Integer> subquery = query.subquery(Integer.class);
        Root<Defect> defect = subquery.from(Defect.class);
        subquery.select(cb.literal(1));
        subquery.where(
                cb.equal(defect.get("repairRequestId"), root.get("id")),
                cb.isFalse(defect.get("isDeleted"))
        );
        return cb.exists(subquery);
    }

    private static Predicate linkedWorkOrderExists(
            Root<RepairRequest> root,
            jakarta.persistence.criteria.CriteriaQuery<?> query,
            CriteriaBuilder cb
    ) {
        Subquery<Integer> subquery = query.subquery(Integer.class);
        Root<WorkOrder> workOrder = subquery.from(WorkOrder.class);
        subquery.select(cb.literal(1));
        subquery.where(
                cb.equal(workOrder.get("repairRequestId"), root.get("id")),
                cb.isFalse(workOrder.get("isDeleted"))
        );
        return cb.exists(subquery);
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
            Root<RepairRequest> root,
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
            Root<RepairRequest> root,
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
            Root<RepairRequest> root,
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
