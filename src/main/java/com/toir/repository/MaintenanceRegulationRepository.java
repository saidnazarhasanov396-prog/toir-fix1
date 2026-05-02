package com.toir.repository;

import com.toir.entity.MaintenanceRegulation;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface MaintenanceRegulationRepository extends JpaRepository<MaintenanceRegulation, UUID> {
    @Query(value = "SELECT * FROM maintenance_regulations WHERE id = cast(:id as uuid) AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<MaintenanceRegulation> findByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT * FROM maintenance_regulations WHERE is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<MaintenanceRegulation> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    @Query(value = "SELECT * FROM maintenance_regulations WHERE id IN (:ids) AND is_deleted = false", nativeQuery = true)
    List<MaintenanceRegulation> findAllByIdInAndIsDeletedFalse(@Param("ids") Collection<UUID> ids);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM maintenance_regulations WHERE id = cast(:id as uuid) AND is_deleted = false)", nativeQuery = true)
    boolean existsByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT COUNT(*) FROM maintenance_regulations WHERE is_deleted = false", nativeQuery = true)
    long countByIsDeletedFalse();

    @Query(nativeQuery = true, value = """
            select * from maintenance_regulations m where
            m.is_deleted = false
            and (:search is null or lower(m.code) like lower(concat('%', :search, '%'))
            or lower(m.name) like lower(concat('%', :search, '%'))
            or lower(m.description) like lower(concat('%', :search, '%')))
            order by m.updated_at desc
            """, countQuery = """
            select count(*) from maintenance_regulations m where
            m.is_deleted = false
            and (:search is null or lower(m.code) like lower(concat('%', :search, '%'))
            or lower(m.name) like lower(concat('%', :search, '%'))
            or lower(m.description) like lower(concat('%', :search, '%')))
            """)
    Page<MaintenanceRegulation> searchPaginated(@Param("search") String search, Pageable pageable);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM maintenance_regulations WHERE code = :code AND is_deleted = false)", nativeQuery = true)
    boolean existsByCodeAndIsDeletedFalse(@Param("code") String code);

    @Query(value = "SELECT * FROM maintenance_regulations WHERE equipment_type_id = :equipmentTypeId AND is_active = true AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<MaintenanceRegulation> findAllByEquipmentTypeIdAndActiveTrueAndIsDeletedFalse(@Param("equipmentTypeId") UUID equipmentTypeId);
}
