package com.toir.repository;

import com.toir.entity.EquipmentType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface EquipmentTypeRepository extends JpaRepository<EquipmentType, UUID> {
    @Query(value = "SELECT * FROM equipment_types WHERE id = cast(:id as uuid) AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<EquipmentType> findByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT * FROM equipment_types WHERE is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<EquipmentType> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    @Query(value = "SELECT * FROM equipment_types WHERE id IN (:ids) AND is_deleted = false", nativeQuery = true)
    List<EquipmentType> findAllByIdInAndIsDeletedFalse(@Param("ids") Collection<UUID> ids);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM equipment_types WHERE id = cast(:id as uuid) AND is_deleted = false)", nativeQuery = true)
    boolean existsByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT COUNT(*) FROM equipment_types WHERE is_deleted = false", nativeQuery = true)
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
    order by e.updatedAt desc
""")
    List<EquipmentType> findAllByIsDeletedFalseAndBySearchParam(String search, String category);
}
