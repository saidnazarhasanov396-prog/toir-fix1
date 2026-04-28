package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.WorkOrder;
import com.toir.enums.WorkOrderStatus;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;


@Repository
public interface WorkOrderRepository extends JpaRepository<WorkOrder, UUID> {
    java.util.Optional<WorkOrder> findByIdAndIsDeletedFalse(java.util.UUID id);

    java.util.List<WorkOrder> findAllByIsDeletedFalse();

    java.util.List<WorkOrder> findAllByIdInAndIsDeletedFalse(java.util.Collection<java.util.UUID> ids);

    boolean existsByIdAndIsDeletedFalse(java.util.UUID id);

    long countByIsDeletedFalse();

    boolean existsByNumberAndIsDeletedFalse(String number);

    @Query(value = "SELECT COUNT(*) FROM work_orders WHERE status = :status AND is_deleted = false", nativeQuery = true)
    long countByStatusAndIsDeletedFalse(@Param("status") String status);

    @Query("SELECT w FROM WorkOrder w WHERE w.isDeleted = false " +
            "AND (:status IS NULL OR w.status = :status) " +
            "AND (:departmentId IS NULL OR w.departmentId = :departmentId) " +
            "AND (:equipmentId IS NULL OR w.equipmentId = :equipmentId)")
    List<WorkOrder> search(@Param("status") WorkOrderStatus status,
                           @Param("departmentId") UUID departmentId,
                           @Param("equipmentId") UUID equipmentId);

    @Query(nativeQuery = true, value = """
            select * from work_orders w where
            w.is_deleted = false
            and (cast(:status as varchar) is null or w.status = cast(:status as varchar)) 
            and (cast(:departmentId as varchar) is null or w.department_id = cast(:departmentId as uuid)) 
            and (cast(:equipmentId as varchar) is null or w.equipment_id = cast(:equipmentId as uuid)) 
            and (cast(:search as varchar) is null or lower(w.number) like lower(concat('%', cast(:search as varchar), '%')) 
            or lower(w.title) like lower(concat('%', cast(:search as varchar), '%')) 
            or lower(w.summary) like lower(concat('%', cast(:search as varchar), '%')) 
            or lower(w.result) like lower(concat('%', cast(:search as varchar), '%')) 
            or lower(w.closure_notes) like lower(concat('%', cast(:search as varchar), '%'))) 
            order by w.created_at desc""", countQuery = """
            select count(*) from work_orders w where
            w.is_deleted = false
            and (cast(:status as varchar) is null or w.status = cast(:status as varchar))
            and (cast(:departmentId as varchar) is null or w.department_id = cast(:departmentId as uuid))
            and (cast(:equipmentId as varchar) is null or w.equipment_id = cast(:equipmentId as uuid))
            and (cast(:search as varchar) is null or lower(w.number) like lower(concat('%', cast(:search as varchar), '%'))
            or lower(w.title) like lower(concat('%', cast(:search as varchar), '%'))
            or lower(w.summary) like lower(concat('%', cast(:search as varchar), '%'))
            or lower(w.result) like lower(concat('%', cast(:search as varchar), '%'))
            or lower(w.closure_notes) like lower(concat('%', cast(:search as varchar), '%')))""")
    Page<WorkOrder> searchPaginated(@Param("status") WorkOrderStatus status,
                                    @Param("departmentId") UUID departmentId,
                                    @Param("equipmentId") UUID equipmentId,
                                    @Param("search") String search,
                                    Pageable pageable);

    @Query(nativeQuery = true, value = """
            select * from work_orders w where
            w.is_deleted = false
            and w.status in ('APPROVED', 'IN_PROGRESS')
            and (cast(:departmentId as varchar) is null or w.department_id = cast(:departmentId as uuid))
            and (cast(:equipmentId as varchar) is null or w.equipment_id = cast(:equipmentId as uuid))
            and (cast(:search as varchar) is null or lower(w.number) like lower(concat('%', cast(:search as varchar), '%'))
            or lower(w.title) like lower(concat('%', cast(:search as varchar), '%'))
            or lower(w.summary) like lower(concat('%', cast(:search as varchar), '%')))
            order by w.created_at desc""", countQuery = """
            select count(*) from work_orders w where
            w.is_deleted = false
            and w.status in ('APPROVED', 'IN_PROGRESS')
            and (cast(:departmentId as varchar) is null or w.department_id = cast(:departmentId as uuid))
            and (cast(:equipmentId as varchar) is null or w.equipment_id = cast(:equipmentId as uuid))
            and (cast(:search as varchar) is null or lower(w.number) like lower(concat('%', cast(:search as varchar), '%'))
            or lower(w.title) like lower(concat('%', cast(:search as varchar), '%'))
            or lower(w.summary) like lower(concat('%', cast(:search as varchar), '%')))""")
    Page<WorkOrder> searchMobileFeed(@Param("departmentId") UUID departmentId,
                                     @Param("equipmentId") UUID equipmentId,
                                     @Param("search") String search,
                                     Pageable pageable);
}
