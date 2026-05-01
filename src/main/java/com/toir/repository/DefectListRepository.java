package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.DefectList;
import com.toir.enums.DefectListStatus;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;


@Repository
public interface DefectListRepository extends JpaRepository<DefectList, UUID> {
    java.util.Optional<DefectList> findByIdAndIsDeletedFalse(java.util.UUID id);

    java.util.List<DefectList> findAllByIsDeletedFalse();

    java.util.List<DefectList> findAllByIdInAndIsDeletedFalse(java.util.Collection<java.util.UUID> ids);

    boolean existsByIdAndIsDeletedFalse(java.util.UUID id);

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

    @Query(value = "SELECT * FROM defect_lists WHERE equipment_id = :equipmentId AND is_deleted = false", nativeQuery = true)
    List<DefectList> findAllByEquipmentIdAndIsDeletedFalse(@Param("equipmentId") UUID equipmentId);

    @Query(value = "SELECT * FROM defect_lists WHERE repair_request_id = :repairRequestId AND is_deleted = false", nativeQuery = true)
    List<DefectList> findAllByRepairRequestIdAndIsDeletedFalse(@Param("repairRequestId") UUID repairRequestId);

    @Query(value = "SELECT * FROM defect_lists WHERE status = :status AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<DefectList> findAllByStatusAndIsDeletedFalseOrderByCreatedAtDesc(@Param("status") DefectListStatus status);
}
