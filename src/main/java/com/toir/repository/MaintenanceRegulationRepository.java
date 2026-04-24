package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.MaintenanceRegulation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;


@Repository
public interface MaintenanceRegulationRepository extends JpaRepository<MaintenanceRegulation, UUID> {
    @Query(nativeQuery = true, value = """
            select * from maintenance_regulations m where
            (:search is null or lower(m.code) like lower(concat('%', :search, '%'))
            or lower(m.name) like lower(concat('%', :search, '%'))
            or lower(m.description) like lower(concat('%', :search, '%')))
            order by m.created_at desc limit :limit offset :offset
            """)
    List<MaintenanceRegulation> searchPaginated(@Param("search") String search,
                                                @Param("offset") int offset,
                                                @Param("limit") int limit);
    @Query(value = "SELECT EXISTS(SELECT 1 FROM maintenance_regulations WHERE code = :code AND is_deleted = false)", nativeQuery = true)
    boolean existsByCode(@Param("code") String code);

    @Query(value = "SELECT * FROM maintenance_regulations WHERE equipment_type_id = :equipmentTypeId AND is_active = true AND is_deleted = false", nativeQuery = true)
    List<MaintenanceRegulation> findAllByEquipmentTypeIdAndActiveTrue(@Param("equipmentTypeId") UUID equipmentTypeId);

    @Query(nativeQuery = true, value = """
            select * from maintenance_regulations m where
            (:search is null or lower(m.code) like lower(concat('%', :search, '%'))
            or lower(m.name) like lower(concat('%', :search, '%'))
            or lower(m.description) like lower(concat('%', :search, '%')))
            order by m.created_at desc limit :limit offset :offset
            """)
    List<MaintenanceRegulation> searchPaginated(@Param("search") String search,
                                                @Param("offset") int offset,
                                                @Param("limit") int limit);
}


