package com.toir.repository;
import com.toir.entity.MaintenanceRegulation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface MaintenanceRegulationRepository extends JpaRepository<MaintenanceRegulation, UUID> {
    boolean existsByCode(String code);
    List<MaintenanceRegulation> findAllByEquipmentTypeIdAndActiveTrue(UUID equipmentTypeId);

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


