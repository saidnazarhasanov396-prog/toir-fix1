package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.DefectList;
import com.toir.enums.DefectListStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;


@Repository
public interface DefectListRepository extends JpaRepository<DefectList, UUID> {

    @Query(nativeQuery = true, value = """
            select * from defect_lists d where
            (:equipmentId is null or d.equipment_id = cast(:equipmentId as uuid))
            and (:search is null or lower(d.code) like lower(concat('%', :search, '%'))
            or lower(d.title) like lower(concat('%', :search, '%'))
            or lower(d.notes) like lower(concat('%', :search, '%')))
            order by d.created_at desc limit :limit offset :offset
            """)
    List<DefectList> searchPaginated(@Param("equipmentId") UUID equipmentId,
                                     @Param("search") String search,
                                     @Param("offset") int offset,
                                     @Param("limit") int limit);
    @Query(value = "SELECT EXISTS(SELECT 1 FROM defect_lists WHERE code = :code AND is_deleted = false)", nativeQuery = true)
    boolean existsByCode(@Param("code") String code);

    @Query(value = "SELECT * FROM defect_lists WHERE equipment_id = :equipmentId AND is_deleted = false", nativeQuery = true)
    List<DefectList> findAllByEquipmentId(@Param("equipmentId") UUID equipmentId);

    @Query(value = "SELECT * FROM defect_lists WHERE repair_request_id = :repairRequestId AND is_deleted = false", nativeQuery = true)
    List<DefectList> findAllByRepairRequestId(@Param("repairRequestId") UUID repairRequestId);

    @Query(value = "SELECT * FROM defect_lists WHERE status = :status AND is_deleted = false ORDER BY created_at DESC", nativeQuery = true)
    List<DefectList> findAllByStatusOrderByCreatedAtDesc(@Param("status") DefectListStatus status);
}


