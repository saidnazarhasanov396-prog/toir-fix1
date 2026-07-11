package com.toir.repository.sparepartlifecycle;

import com.toir.dto.sparepartlifecycle.SparePartLifeRuleFilter;
import com.toir.entity.sparepartlifecycle.SparePartLifeRule;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/**
 * {@link SparePartLifeRule} uchun ma'lumotlar bazasida bajariladigan filtrlar.
 * Har doim {@code isDeleted = false} shartini qo'llaydi va faqat qo'llab-quvvatlanadigan
 * maydonlar bo'yicha tenglik hamda {@code effectiveAt} yarim-ochiq interval shartini beradi.
 */
public final class SparePartLifeRuleSpecifications {

    private SparePartLifeRuleSpecifications() {
    }

    public static Specification<SparePartLifeRule> byFilter(SparePartLifeRuleFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get("isDeleted")));

            if (filter != null) {
                equalIfPresent(predicates, cb, root, "sparePartId", filter.sparePartId());
                equalIfPresent(predicates, cb, root, "equipmentId", filter.equipmentId());
                equalIfPresent(predicates, cb, root, "equipmentNodeId", filter.equipmentNodeId());
                equalIfPresent(predicates, cb, root, "scopeType", filter.scopeType());
                equalIfPresent(predicates, cb, root, "active", filter.active());
                addEffectiveAtPredicate(predicates, cb, root, filter.effectiveAt());
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static void addEffectiveAtPredicate(
            List<Predicate> predicates,
            jakarta.persistence.criteria.CriteriaBuilder cb,
            Root<SparePartLifeRule> root,
            Instant effectiveAt
    ) {
        if (effectiveAt == null) {
            return;
        }
        // Boshlanish inklyuziv (effectiveFrom <= at), tugash ekskluziv (effectiveTo > at).
        // Null qiymatlar ochiq interval sifatida qaraladi.
        predicates.add(cb.or(
                cb.isNull(root.get("effectiveFrom")),
                cb.lessThanOrEqualTo(root.get("effectiveFrom"), effectiveAt)
        ));
        predicates.add(cb.or(
                cb.isNull(root.get("effectiveTo")),
                cb.greaterThan(root.get("effectiveTo"), effectiveAt)
        ));
    }

    private static void equalIfPresent(
            List<Predicate> predicates,
            jakarta.persistence.criteria.CriteriaBuilder cb,
            Root<SparePartLifeRule> root,
            String field,
            Object value
    ) {
        if (value != null) {
            predicates.add(cb.equal(root.get(field), value));
        }
    }
}
