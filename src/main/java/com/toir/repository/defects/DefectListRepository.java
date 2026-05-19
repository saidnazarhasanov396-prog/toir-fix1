package com.toir.repository.defects;

import com.toir.entity.defects.DefectList;
import com.toir.enums.DefectListStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.toir.repository.projection.DefectListStatsProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface DefectListRepository extends JpaRepository<DefectList, UUID> {
    @Query(value = "SELECT * FROM defect_lists WHERE id = cast(:id as uuid) AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<DefectList> findByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT * FROM defect_lists WHERE is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<DefectList> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    @Query(value = "SELECT * FROM defect_lists WHERE id IN (:ids) AND is_deleted = false", nativeQuery = true)
    List<DefectList> findAllByIdInAndIsDeletedFalse(@Param("ids") Collection<UUID> ids);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM defect_lists WHERE id = cast(:id as uuid) AND is_deleted = false)", nativeQuery = true)
    boolean existsByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT COUNT(*) FROM defect_lists WHERE is_deleted = false", nativeQuery = true)
    long countByIsDeletedFalse();


    @Query(nativeQuery = true, value = """
            select * from defect_lists d where
            d.is_deleted = false
            and (:equipmentId is null or d.equipment_id = cast(:equipmentId as uuid))
            and (:search is null or lower(d.code) like lower(concat('%', :search, '%'))
            or lower(d.title) like lower(concat('%', :search, '%'))
            or lower(d.notes) like lower(concat('%', :search, '%')))
            order by d.updated_at desc
            """, countQuery = """
            select count(*) from defect_lists d where
            d.is_deleted = false
            and (:equipmentId is null or d.equipment_id = cast(:equipmentId as uuid))
            and (:search is null or lower(d.code) like lower(concat('%', :search, '%'))
            or lower(d.title) like lower(concat('%', :search, '%'))
            or lower(d.notes) like lower(concat('%', :search, '%')))
            """)
    Page<DefectList> searchPaginated(@Param("equipmentId") UUID equipmentId,
                                     @Param("search") String search,
                                     Pageable pageable);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM defect_lists WHERE code = :code AND is_deleted = false)", nativeQuery = true)
    boolean existsByCodeAndIsDeletedFalse(@Param("code") String code);

    boolean existsByCode(String code);

    @Query(value = """
            SELECT COALESCE(MAX(CAST(SUBSTRING(code FROM LENGTH(:prefix) + 1) AS BIGINT)), 0)
            FROM defect_lists
            WHERE code LIKE CONCAT(:prefix, '%')
              AND SUBSTRING(code FROM LENGTH(:prefix) + 1) ~ '^[0-9]+$'
            """, nativeQuery = true)
    long maxSequenceByCodePrefix(@Param("prefix") String prefix);

    @Query(value = "SELECT * FROM defect_lists WHERE equipment_id = :equipmentId AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<DefectList> findAllByEquipmentIdAndIsDeletedFalse(@Param("equipmentId") UUID equipmentId);

    @Query(value = "SELECT * FROM defect_lists WHERE repair_request_id = :repairRequestId AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<DefectList> findAllByRepairRequestIdAndIsDeletedFalse(@Param("repairRequestId") UUID repairRequestId);

    @Query(value = "SELECT * FROM defect_lists WHERE status = :status AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<DefectList> findAllByStatusAndIsDeletedFalseOrderByCreatedAtDesc(@Param("status") DefectListStatus status);

    @Query(nativeQuery = true, value = """
        select
            count(d.id) as totalDefectLists,

            count(*) filter (
                where d.status = cast(:draftStatus as text)
            ) as draft,

            count(*) filter (
                where d.status = cast(:approvedStatus as text)
            ) as approved,

            count(*) filter (
                where d.status = cast(:closedStatus as text)
            ) as closed

        from defect_lists d
        where d.is_deleted = false
          and (cast(:equipmentId as uuid) is null or d.equipment_id = cast(:equipmentId as uuid))
          and (
              cast(:searchPattern as varchar) is null
              or lower(coalesce(d.code, '')) like cast(:searchPattern as varchar)
              or lower(coalesce(d.title, '')) like cast(:searchPattern as varchar)
              or lower(coalesce(d.notes, '')) like cast(:searchPattern as varchar)
          )
        """)
    DefectListStatsProjection getDefectListStats(
            @Param("equipmentId") UUID equipmentId,
            @Param("searchPattern") String searchPattern,
            @Param("draftStatus") String draftStatus,
            @Param("approvedStatus") String approvedStatus,
            @Param("closedStatus") String closedStatus
    );

}
