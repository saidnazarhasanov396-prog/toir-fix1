package com.toir.repository;

import com.toir.entity.OeeRecord;
import com.toir.entity.equipment.Equipment;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface OeeRecordRepository extends JpaRepository<OeeRecord, UUID>, JpaSpecificationExecutor<OeeRecord> {
    @Query(value = "SELECT * FROM oee_records WHERE id = cast(:id as uuid) AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<OeeRecord> findByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT * FROM oee_records WHERE is_deleted = false ORDER BY shift_start DESC", nativeQuery = true)
    List<OeeRecord> findAllByIsDeletedFalseOrderByShiftStartDesc();

    @Query(value = "SELECT * FROM oee_records WHERE id IN (:ids) AND is_deleted = false", nativeQuery = true)
    List<OeeRecord> findAllByIdInAndIsDeletedFalse(@Param("ids") Collection<UUID> ids);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM oee_records WHERE id = cast(:id as uuid) AND is_deleted = false)", nativeQuery = true)
    boolean existsByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT COUNT(*) FROM oee_records WHERE is_deleted = false", nativeQuery = true)
    long countByIsDeletedFalse();

    @Query(value = "SELECT * FROM oee_records WHERE equipment_id = :equipmentId AND is_deleted = false ORDER BY shift_start DESC", nativeQuery = true)
    List<OeeRecord> findAllByEquipmentIdAndIsDeletedFalseOrderByShiftStartDesc(@Param("equipmentId") UUID equipmentId);

    @Query(value = "SELECT * FROM oee_records WHERE equipment_id = :equipmentId AND shift_start BETWEEN :from AND :to AND is_deleted = false ORDER BY shift_start ASC", nativeQuery = true)
    List<OeeRecord> findAllByEquipmentIdAndShiftStartBetweenAndIsDeletedFalseOrderByShiftStartAsc(
            @Param("equipmentId") UUID equipmentId, @Param("from") Instant from, @Param("to") Instant to);

    @Query(value = "SELECT * FROM oee_records WHERE equipment_id IN (:equipmentIds) AND is_deleted = false ORDER BY shift_start DESC", nativeQuery = true)
    List<OeeRecord> findAllByEquipmentIdInAndIsDeletedFalseOrderByShiftStartDesc(@Param("equipmentIds") Collection<UUID> equipmentIds);

    @Query(value = "SELECT * FROM oee_records WHERE equipment_id IN (:equipmentIds) AND shift_start BETWEEN :from AND :to AND is_deleted = false ORDER BY shift_start ASC", nativeQuery = true)
    List<OeeRecord> findAllByEquipmentIdInAndShiftStartBetweenAndIsDeletedFalseOrderByShiftStartAsc(
            @Param("equipmentIds") Collection<UUID> equipmentIds, @Param("from") Instant from, @Param("to") Instant to);

    @Query(value = "SELECT * FROM oee_records WHERE shift_start BETWEEN :from AND :to AND is_deleted = false ORDER BY shift_start ASC", nativeQuery = true)
    List<OeeRecord> findAllByShiftStartBetweenAndIsDeletedFalse(@Param("from") Instant from, @Param("to") Instant to);

    default List<OeeRecord> search(
            UUID equipmentId,
            String equipmentSearch,
            UUID departmentId,
            UUID equipmentTypeId,
            Instant from,
            Instant to
    ) {
        return findAll(
                searchSpecification(equipmentId, equipmentSearch, departmentId, equipmentTypeId, from, to),
                Sort.by(Sort.Direction.DESC, "shiftStart")
        );
    }

    private static Specification<OeeRecord> searchSpecification(
            UUID equipmentId,
            String equipmentSearch,
            UUID departmentId,
            UUID equipmentTypeId,
            Instant from,
            Instant to
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get("isDeleted")));

            if (equipmentId != null) {
                predicates.add(cb.equal(root.get("equipmentId"), equipmentId));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("shiftStart"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("shiftStart"), to));
            }
            if (equipmentSearch != null) {
                predicates.add(equipmentSearchMatches(root, query, cb, equipmentSearch));
            }
            if (departmentId != null) {
                predicates.add(equipmentDepartmentMatches(root, query, cb, departmentId));
            }
            if (equipmentTypeId != null) {
                predicates.add(equipmentTypeMatches(root, query, cb, equipmentTypeId));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static Predicate equipmentSearchMatches(
            Root<OeeRecord> root,
            jakarta.persistence.criteria.CriteriaQuery<?> query,
            CriteriaBuilder cb,
            String equipmentSearch
    ) {
        Subquery<Integer> subquery = query.subquery(Integer.class);
        Root<Equipment> equipment = subquery.from(Equipment.class);
        subquery.select(cb.literal(1));
        subquery.where(
                cb.equal(equipment.get("id"), root.get("equipmentId")),
                cb.isFalse(equipment.get("isDeleted")),
                cb.or(
                        contains(cb, equipment.get("code"), equipmentSearch),
                        contains(cb, equipment.get("name"), equipmentSearch),
                        contains(cb, equipment.get("inventoryNumber"), equipmentSearch),
                        contains(cb, equipment.get("technicalNumber"), equipmentSearch),
                        contains(cb, equipment.get("serialNumber"), equipmentSearch)
                )
        );
        return cb.exists(subquery);
    }

    private static Predicate equipmentDepartmentMatches(
            Root<OeeRecord> root,
            jakarta.persistence.criteria.CriteriaQuery<?> query,
            CriteriaBuilder cb,
            UUID departmentId
    ) {
        Subquery<Integer> subquery = query.subquery(Integer.class);
        Root<Equipment> equipment = subquery.from(Equipment.class);
        subquery.select(cb.literal(1));
        subquery.where(
                cb.equal(equipment.get("id"), root.get("equipmentId")),
                cb.isFalse(equipment.get("isDeleted")),
                cb.or(
                        cb.equal(equipment.get("responsibleDepartmentId"), departmentId),
                        cb.and(
                                cb.isNull(equipment.get("responsibleDepartmentId")),
                                cb.equal(equipment.get("departmentId"), departmentId)
                        )
                )
        );
        return cb.exists(subquery);
    }

    private static Predicate equipmentTypeMatches(
            Root<OeeRecord> root,
            jakarta.persistence.criteria.CriteriaQuery<?> query,
            CriteriaBuilder cb,
            UUID equipmentTypeId
    ) {
        Subquery<Integer> subquery = query.subquery(Integer.class);
        Root<Equipment> equipment = subquery.from(Equipment.class);
        subquery.select(cb.literal(1));
        subquery.where(
                cb.equal(equipment.get("id"), root.get("equipmentId")),
                cb.isFalse(equipment.get("isDeleted")),
                cb.equal(equipment.get("equipmentTypeId"), equipmentTypeId)
        );
        return cb.exists(subquery);
    }

    private static Predicate contains(CriteriaBuilder cb, Expression<String> value, String pattern) {
        return cb.like(cb.lower(cb.coalesce(value, "")), pattern);
    }

}
