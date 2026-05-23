package com.toir.repository.equipment;

import com.toir.entity.equipment.EquipmentAttributeOptionSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EquipmentAttributeOptionSourceRepository extends JpaRepository<EquipmentAttributeOptionSource, UUID> {

    @Query(value = "SELECT * FROM equipment_attribute_option_sources WHERE is_deleted = false ORDER BY code ASC", nativeQuery = true)
    List<EquipmentAttributeOptionSource> findAllByIsDeletedFalseOrderByCodeAsc();

    @Query("""
            SELECT source FROM EquipmentAttributeOptionSource source
            WHERE source.isDeleted = false
                AND (cast(:search as string) IS NULL OR
                     lower(source.code) LIKE lower(concat('%', cast(:search as string), '%')) OR
                     lower(source.name) LIKE lower(concat('%', cast(:search as string), '%')) OR
                     lower(source.nameRu) LIKE lower(concat('%', cast(:search as string), '%')) OR
                     lower(source.nameUz) LIKE lower(concat('%', cast(:search as string), '%')) OR
                     lower(source.description) LIKE lower(concat('%', cast(:search as string), '%')))
            ORDER BY source.code ASC
            """)
    List<EquipmentAttributeOptionSource> findAllBySearch(@Param("search") String search);

    @Query(value = "SELECT * FROM equipment_attribute_option_sources WHERE id = cast(:id as uuid) AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<EquipmentAttributeOptionSource> findByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT * FROM equipment_attribute_option_sources WHERE code = :code AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<EquipmentAttributeOptionSource> findByCodeAndIsDeletedFalse(@Param("code") String code);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM equipment_attribute_option_sources WHERE code = :code AND is_deleted = false)", nativeQuery = true)
    boolean existsByCodeAndIsDeletedFalse(@Param("code") String code);
}
