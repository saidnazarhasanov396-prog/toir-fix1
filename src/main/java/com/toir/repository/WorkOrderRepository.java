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
    @Query(value = "SELECT COUNT(*) > 0 FROM work_orders WHERE number = :number AND is_deleted = false", nativeQuery = true)
    boolean existsByNumber(@Param("number") String number);

    @Query(value = "SELECT COUNT(*) FROM work_orders WHERE status = :status AND is_deleted = false", nativeQuery = true)
    long countByStatus(@Param("status") WorkOrderStatus status);

    @Query(value = "SELECT * FROM work_orders WHERE (:status IS NULL OR status = :status) " +
            "AND (:departmentId IS NULL OR department_id = :departmentId) " +
            "AND (:equipmentId IS NULL OR equipment_id = :equipmentId) AND is_deleted = false", nativeQuery = true)
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
