package com.toir.repository;
import com.toir.entity.DefectList;
import com.toir.enums.DefectListStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface DefectListRepository extends JpaRepository<DefectList, UUID> {
    boolean existsByCode(String code);
    List<DefectList> findAllByEquipmentId(UUID equipmentId);
    List<DefectList> findAllByRepairRequestId(UUID repairRequestId);
    List<DefectList> findAllByStatusOrderByCreatedAtDesc(DefectListStatus status);

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
}


