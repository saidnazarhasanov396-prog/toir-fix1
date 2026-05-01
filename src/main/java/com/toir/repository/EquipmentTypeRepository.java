package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.EquipmentType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;


@Repository
public interface EquipmentTypeRepository extends JpaRepository<EquipmentType, UUID> {
    java.util.Optional<EquipmentType> findByIdAndIsDeletedFalse(java.util.UUID id);

    java.util.List<EquipmentType> findAllByIsDeletedFalse();

    java.util.List<EquipmentType> findAllByIdInAndIsDeletedFalse(java.util.Collection<java.util.UUID> ids);

    boolean existsByIdAndIsDeletedFalse(java.util.UUID id);

    long countByIsDeletedFalse();

    @Query(value = "SELECT EXISTS(SELECT 1 FROM equipment_types WHERE code = :code AND is_deleted = false)", nativeQuery = true)
    boolean existsByCodeAndIsDeletedFalse(@Param("code") String code);

    boolean existsByCode(String code);

    @Query(value = """
            SELECT COALESCE(MAX(CAST(SUBSTRING(code FROM LENGTH(:prefix) + 1) AS BIGINT)), 0)
            FROM equipment_types
            WHERE code LIKE CONCAT(:prefix, '%')
              AND SUBSTRING(code FROM LENGTH(:prefix) + 1) ~ '^[0-9]+$'
            """, nativeQuery = true)
    long maxSequenceByCodePrefix(@Param("prefix") String prefix);

    @Query("""

            select e from EquipmentType e
    where e.isDeleted = false
    and (
        :search is null or
        lower(e.code) like :search or
        lower(e.name) like :search or
        lower(e.description) like :search or
        lower(e.nameEn) like :search or
        lower(e.nameUz) like :search
    )
    and (:category is null or e.category = :category)
""")
    List<EquipmentType> findAllByIsDeletedFalseAndBySearchParam(String search, String category);
}
