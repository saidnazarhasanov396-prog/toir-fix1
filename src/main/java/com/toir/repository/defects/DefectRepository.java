package com.toir.repository.defects;

import com.toir.entity.defects.Defect;

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
public interface DefectRepository extends JpaRepository<Defect, UUID> {
    @Query(value = "SELECT * FROM defects WHERE id = cast(:id as uuid) AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<Defect> findByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT * FROM defects WHERE is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<Defect> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    @Query(value = "SELECT * FROM defects WHERE id IN (:ids) AND is_deleted = false", nativeQuery = true)
    List<Defect> findAllByIdInAndIsDeletedFalse(@Param("ids") Collection<UUID> ids);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM defects WHERE id = cast(:id as uuid) AND is_deleted = false)", nativeQuery = true)
    boolean existsByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT COUNT(*) FROM defects WHERE is_deleted = false", nativeQuery = true)
    long countByIsDeletedFalse();


    @Query(nativeQuery = true, value = """
            select * from defects d where
            d.is_deleted = false
            and (cast(:equipmentId as varchar) is null or d.equipment_id = cast(:equipmentId as uuid))
            and (cast(:search as varchar) is null or lower(d.code) like lower(concat('%', cast(:search as varchar), '%'))
            or lower(d.title) like lower(concat('%', cast(:search as varchar), '%'))
            or lower(d.description) like lower(concat('%', cast(:search as varchar), '%'))
            or lower(d.category) like lower(concat('%', cast(:search as varchar), '%'))
            or lower(d.severity) like lower(concat('%', cast(:search as varchar), '%'))
            or lower(d.failure_reason) like lower(concat('%', cast(:search as varchar), '%'))
            or lower(d.root_cause) like lower(concat('%', cast(:search as varchar), '%')))
            order by d.updated_at desc
            """, countQuery = """
            select count(*) from defects d where
            d.is_deleted = false
            and (cast(:equipmentId as varchar) is null or d.equipment_id = cast(:equipmentId as uuid))
            and (cast(:search as varchar) is null or lower(d.code) like lower(concat('%', cast(:search as varchar), '%'))
            or lower(d.title) like lower(concat('%', cast(:search as varchar), '%'))
            or lower(d.description) like lower(concat('%', cast(:search as varchar), '%'))
            or lower(d.category) like lower(concat('%', cast(:search as varchar), '%'))
            or lower(d.severity) like lower(concat('%', cast(:search as varchar), '%'))
            or lower(d.failure_reason) like lower(concat('%', cast(:search as varchar), '%'))
            or lower(d.root_cause) like lower(concat('%', cast(:search as varchar), '%')))
            """)
    Page<Defect> searchPaginated(@Param("equipmentId") UUID equipmentId,
                                 @Param("search") String search,
                                 Pageable pageable);

    @Query(value = "SELECT COUNT(*) > 0 FROM defects WHERE code = :code AND is_deleted = false", nativeQuery = true)
    boolean existsByCodeAndIsDeletedFalse(@Param("code") String code);

    boolean existsByCode(String code);

    @Query(value = """
            SELECT COALESCE(MAX(CAST(SUBSTRING(code FROM LENGTH(:prefix) + 1) AS BIGINT)), 0)
            FROM defects
            WHERE code LIKE CONCAT(:prefix, '%')
              AND SUBSTRING(code FROM LENGTH(:prefix) + 1) ~ '^[0-9]+$'
            """, nativeQuery = true)
    long maxSequenceByCodePrefix(@Param("prefix") String prefix);

    @Query(value = "SELECT * FROM defects WHERE equipment_id = :equipmentId AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<Defect> findAllByEquipmentIdAndIsDeletedFalse(@Param("equipmentId") UUID equipmentId);

    @Query(value = "SELECT COUNT(*) FROM defects WHERE status = :status AND is_deleted = false", nativeQuery = true)
    long countByStatusAndIsDeletedFalse(@Param("status") String status);
}
