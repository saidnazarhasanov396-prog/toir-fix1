package com.toir.repository;
import com.toir.entity.Defect;
import com.toir.enums.DefectStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface DefectRepository extends JpaRepository<Defect, UUID> {
    boolean existsByCode(String code);
    List<Defect> findAllByEquipmentId(UUID equipmentId);
    long countByStatus(DefectStatus status);

    @Query(nativeQuery = true, value = """
            select * from defects d where
            (:equipmentId is null or d.equipment_id = cast(:equipmentId as uuid))
            and (:search is null or lower(d.code) like lower(concat('%', :search, '%'))
            or lower(d.title) like lower(concat('%', :search, '%'))
            or lower(d.description) like lower(concat('%', :search, '%'))
            or lower(d.category) like lower(concat('%', :search, '%'))
            or lower(d.severity) like lower(concat('%', :search, '%'))
            or lower(d.failure_reason) like lower(concat('%', :search, '%'))
            or lower(d.root_cause) like lower(concat('%', :search, '%')))
            order by d.detected_at desc limit :limit offset :offset
            """)
    List<Defect> searchPaginated(@Param("equipmentId") UUID equipmentId,
                                 @Param("search") String search,
                                 @Param("offset") int offset,
                                 @Param("limit") int limit);
}


