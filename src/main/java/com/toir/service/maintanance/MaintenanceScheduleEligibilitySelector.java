package com.toir.service.maintanance;

import com.toir.dto.maintenanceschedule.MaintenanceScheduleOption;
import com.toir.dto.maintenanceschedule.MaintenanceSchedulePreviewRequest;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.equipment.EquipmentType;
import com.toir.enums.EquipmentStatus;
import com.toir.enums.MaintenanceScheduleScopeType;
import com.toir.exception.RestException;
import com.toir.repository.equipment.EquipmentRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MaintenanceScheduleEligibilitySelector {

    private final EntityManager entityManager;
    private final EquipmentRepository equipmentRepository;

    public Page<MaintenanceScheduleOption> findOptions(
            MaintenanceScheduleScopeType scopeType,
            UUID departmentId,
            String search,
            Pageable pageable
    ) {
        if (scopeType == MaintenanceScheduleScopeType.EQUIPMENT) {
            return findEquipmentOptions(departmentId, search, pageable);
        }
        return findEquipmentTypeOptions(departmentId, search, pageable);
    }

    public List<Equipment> selectForPreview(MaintenanceSchedulePreviewRequest request) {
        if (request.scopeType() == MaintenanceScheduleScopeType.EQUIPMENT) {
            rejectUnknownEquipment(request.equipmentIds());
            return findEligibleEquipment(
                    request.departmentId(),
                    request.equipmentIds(),
                    null
            );
        }
        return findEligibleEquipment(
                request.departmentId(),
                null,
                request.equipmentTypeIds()
        );
    }

    private Page<MaintenanceScheduleOption> findEquipmentOptions(
            UUID departmentId,
            String search,
            Pageable pageable
    ) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Equipment> query = builder.createQuery(Equipment.class);
        Root<Equipment> equipment = query.from(Equipment.class);
        List<Predicate> predicates = eligibilityPredicates(builder, equipment, departmentId);
        addEquipmentSearch(builder, predicates, equipment, search);
        query.select(equipment)
                .where(predicates.toArray(Predicate[]::new))
                .orderBy(equipmentOrder(builder, equipment));

        List<MaintenanceScheduleOption> content = page(
                entityManager.createQuery(query), pageable
        ).stream().map(item -> new MaintenanceScheduleOption(
                item.getId(),
                item.getCode(),
                item.getName(),
                MaintenanceScheduleScopeType.EQUIPMENT,
                1
        )).toList();

        CriteriaQuery<Long> countQuery = builder.createQuery(Long.class);
        Root<Equipment> countEquipment = countQuery.from(Equipment.class);
        List<Predicate> countPredicates = eligibilityPredicates(
                builder, countEquipment, departmentId
        );
        addEquipmentSearch(builder, countPredicates, countEquipment, search);
        countQuery.select(builder.count(countEquipment))
                .where(countPredicates.toArray(Predicate[]::new));
        long total = entityManager.createQuery(countQuery).getSingleResult();
        return new PageImpl<>(content, pageable, total);
    }

    private Page<MaintenanceScheduleOption> findEquipmentTypeOptions(
            UUID departmentId,
            String search,
            Pageable pageable
    ) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = builder.createTupleQuery();
        Root<EquipmentType> type = query.from(EquipmentType.class);
        Root<Equipment> equipment = query.from(Equipment.class);
        List<Predicate> predicates = typeOptionPredicates(
                builder, type, equipment, departmentId, search
        );
        query.multiselect(
                        type.get("id").alias("id"),
                        type.get("code").alias("code"),
                        type.get("name").alias("name"),
                        builder.countDistinct(equipment.get("id")).alias("eligibleEquipmentCount")
                )
                .where(predicates.toArray(Predicate[]::new))
                .groupBy(type.get("id"), type.get("code"), type.get("name"))
                .orderBy(
                        builder.asc(builder.lower(type.get("code"))),
                        builder.asc(builder.lower(type.get("name"))),
                        builder.asc(type.get("id"))
                );

        List<MaintenanceScheduleOption> content = page(
                entityManager.createQuery(query), pageable
        ).stream().map(row -> new MaintenanceScheduleOption(
                row.get("id", UUID.class),
                row.get("code", String.class),
                row.get("name", String.class),
                MaintenanceScheduleScopeType.EQUIPMENT_TYPE,
                row.get("eligibleEquipmentCount", Long.class)
        )).toList();

        CriteriaQuery<Long> countQuery = builder.createQuery(Long.class);
        Root<EquipmentType> countType = countQuery.from(EquipmentType.class);
        Root<Equipment> countEquipment = countQuery.from(Equipment.class);
        List<Predicate> countPredicates = typeOptionPredicates(
                builder,
                countType,
                countEquipment,
                departmentId,
                search
        );
        countQuery.select(builder.countDistinct(countType.get("id")))
                .where(countPredicates.toArray(Predicate[]::new));
        long total = entityManager.createQuery(countQuery).getSingleResult();
        return new PageImpl<>(content, pageable, total);
    }

    private List<Equipment> findEligibleEquipment(
            UUID departmentId,
            Collection<UUID> equipmentIds,
            Collection<UUID> equipmentTypeIds
    ) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Equipment> query = builder.createQuery(Equipment.class);
        Root<Equipment> equipment = query.from(Equipment.class);
        List<Predicate> predicates = eligibilityPredicates(builder, equipment, departmentId);
        if (equipmentIds != null) {
            predicates.add(equipment.get("id").in(equipmentIds));
        }
        if (equipmentTypeIds != null) {
            predicates.add(equipment.get("equipmentTypeId").in(equipmentTypeIds));
        }
        query.select(equipment)
                .where(predicates.toArray(Predicate[]::new))
                .orderBy(equipmentOrder(builder, equipment));
        return entityManager.createQuery(query).getResultList();
    }

    private List<Predicate> typeOptionPredicates(
            CriteriaBuilder builder,
            Root<EquipmentType> type,
            Root<Equipment> equipment,
            UUID departmentId,
            String search
    ) {
        List<Predicate> predicates = eligibilityPredicates(builder, equipment, departmentId);
        predicates.add(builder.isFalse(type.get("isDeleted")));
        predicates.add(builder.equal(equipment.get("equipmentTypeId"), type.get("id")));
        String pattern = searchPattern(search);
        if (pattern != null) {
            predicates.add(builder.or(
                    builder.like(builder.lower(type.get("code")), pattern),
                    builder.like(builder.lower(type.get("name")), pattern)
            ));
        }
        return predicates;
    }

    private List<Predicate> eligibilityPredicates(
            CriteriaBuilder builder,
            Root<Equipment> equipment,
            UUID departmentId
    ) {
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(builder.isFalse(equipment.get("isDeleted")));
        predicates.add(builder.equal(equipment.get("status"), EquipmentStatus.ACTIVE));
        if (departmentId != null) {
            predicates.add(builder.equal(
                    builder.coalesce(
                            equipment.get("responsibleDepartmentId"),
                            equipment.get("departmentId")
                    ),
                    departmentId
            ));
        }
        return predicates;
    }

    private void addEquipmentSearch(
            CriteriaBuilder builder,
            List<Predicate> predicates,
            Root<Equipment> equipment,
            String search
    ) {
        String pattern = searchPattern(search);
        if (pattern != null) {
            predicates.add(builder.or(
                    builder.like(builder.lower(equipment.get("code")), pattern),
                    builder.like(builder.lower(equipment.get("name")), pattern)
            ));
        }
    }

    private List<Order> equipmentOrder(CriteriaBuilder builder, Root<Equipment> equipment) {
        return List.of(
                builder.asc(builder.lower(equipment.get("code"))),
                builder.asc(builder.lower(equipment.get("name"))),
                builder.asc(equipment.get("id"))
        );
    }

    private <T> List<T> page(TypedQuery<T> query, Pageable pageable) {
        return query.setFirstResult(Math.toIntExact(pageable.getOffset()))
                .setMaxResults(pageable.getPageSize())
                .getResultList();
    }

    private String searchPattern(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        return "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
    }

    private void rejectUnknownEquipment(List<UUID> requestedIds) {
        Set<UUID> resolvedIds = equipmentRepository
                .findAllByIdInAndIsDeletedFalse(requestedIds)
                .stream()
                .map(Equipment::getId)
                .collect(Collectors.toSet());
        requestedIds.stream()
                .filter(id -> !resolvedIds.contains(id))
                .findFirst()
                .ifPresent(id -> {
                    throw RestException.badRequest("Equipment not found: " + id);
                });
    }
}
