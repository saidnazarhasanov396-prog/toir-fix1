package com.toir.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.toir.entity.Defect;
import com.toir.enums.DefectStatus;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;


@Repository
public interface DefectRepository extends JpaRepository<Defect, UUID> {

    @Query(nativeQuery = true, value = """
            select * from defects d where
            (cast(:equipmentId as varchar) is null or d.equipment_id = cast(:equipmentId as uuid))
            and (cast(:search as varchar) is null or lower(d.code) like lower(concat('%', cast(:search as varchar), '%'))
            or lower(d.title) like lower(concat('%', cast(:search as varchar), '%'))
            or lower(d.description) like lower(concat('%', cast(:search as varchar), '%'))
            or lower(d.category) like lower(concat('%', cast(:search as varchar), '%'))
            or lower(d.severity) like lower(concat('%', cast(:search as varchar), '%'))
            or lower(d.failure_reason) like lower(concat('%', cast(:search as varchar), '%'))
            or lower(d.root_cause) like lower(concat('%', cast(:search as varchar), '%')))
            order by d.detected_at desc limit :limit offset :offset
            """)
    List<Defect> searchPaginated(@Param("equipmentId") UUID equipmentId,
                                 @Param("search") String search,
                                 @Param("offset") int offset,
                                 @Param("limit") int limit);
    @Query(value = "SELECT COUNT(*) > 0 FROM defects WHERE code = :code AND is_deleted = false", nativeQuery = true)
    boolean existsByCode(@Param("code") String code);

    @Query(value = "SELECT * FROM defects WHERE equipment_id = :equipmentId AND is_deleted = false", nativeQuery = true)
    List<Defect> findAllByEquipmentId(@Param("equipmentId") UUID equipmentId);

    @Query(value = "SELECT COUNT(*) FROM defects WHERE status = cast(:status as varchar) AND is_deleted = false", nativeQuery = true)
    long countByStatus(@Param("status") DefectStatus status);
}


