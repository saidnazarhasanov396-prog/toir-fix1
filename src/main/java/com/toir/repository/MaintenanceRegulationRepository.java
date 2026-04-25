package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.MaintenanceRegulation;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;


@Repository
public interface MaintenanceRegulationRepository extends JpaRepository<MaintenanceRegulation, UUID> {
    java.util.Optional<MaintenanceRegulation> findByIdAndIsDeletedFalse(java.util.UUID id);

    java.util.List<MaintenanceRegulation> findAllByIsDeletedFalse();

    java.util.List<MaintenanceRegulation> findAllByIdInAndIsDeletedFalse(java.util.Collection<java.util.UUID> ids);

    boolean existsByIdAndIsDeletedFalse(java.util.UUID id);

    long countByIsDeletedFalse();

    @Query(nativeQuery = true, value = """
            select * from maintenance_regulations m where
            m.is_deleted = false
            and (:search is null or lower(m.code) like lower(concat('%', :search, '%'))
            or lower(m.name) like lower(concat('%', :search, '%'))
            or lower(m.description) like lower(concat('%', :search, '%')))
            order by m.created_at desc
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

    @Query(value = "SELECT * FROM maintenance_regulations WHERE equipment_type_id = :equipmentTypeId AND is_active = true AND is_deleted = false", nativeQuery = true)
    List<MaintenanceRegulation> findAllByEquipmentTypeIdAndActiveTrueAndIsDeletedFalse(@Param("equipmentTypeId") UUID equipmentTypeId);
}
