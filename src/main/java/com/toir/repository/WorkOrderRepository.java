package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.WorkOrder;
import com.toir.enums.WorkOrderStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;


@Repository
public interface WorkOrderRepository extends JpaRepository<WorkOrder, UUID> {
    boolean existsByNumber(String number);

    long countByStatus(WorkOrderStatus status);

    @Query("SELECT w FROM WorkOrder w WHERE (:status IS NULL OR w.status = :status) " +
            "AND (:departmentId IS NULL OR w.departmentId = :departmentId) " +
            "AND (:equipmentId IS NULL OR w.equipmentId = :equipmentId)")
    List<WorkOrder> search(@Param("status") WorkOrderStatus status,
                           @Param("departmentId") UUID departmentId,
                           @Param("equipmentId") UUID equipmentId);

    @Query(nativeQuery = true, value = """
            select * from work_orders w where
            (:status is null or w.status = cast(:status as varchar)) 
            and (:departmentId is null or w.department_id = cast(:departmentId as uuid)) 
            and (:equipmentId is null or w.equipment_id = cast(:equipmentId as uuid)) 
            and (:search is null or lower(w.number) like lower(concat('%', :search, '%')) 
            or lower(w.title) like lower(concat('%', :search, '%')) 
            or lower(w.summary) like lower(concat('%', :search, '%')) 
            or lower(w.result) like lower(concat('%', :search, '%')) 
            or lower(w.closure_notes) like lower(concat('%', :search, '%'))) 
            order by w.created_at desc limit :limit offset :offset""")
    List<WorkOrder> searchPaginated(@Param("status") WorkOrderStatus status,
                                    @Param("departmentId") UUID departmentId,
                                    @Param("equipmentId") UUID equipmentId,
                                    @Param("search") String search,
                                    @Param("offset") int offset,
                                    @Param("limit") int limit);
}
