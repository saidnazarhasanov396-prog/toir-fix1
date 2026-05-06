package com.toir.repository;

import com.toir.entity.maintenance.WorkOrder;
import com.toir.enums.WorkOrderStatus;
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
public interface WorkOrderRepository extends JpaRepository<WorkOrder, UUID> {
    @Query(value = "SELECT * FROM work_orders WHERE id = cast(:id as uuid) AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<WorkOrder> findByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT * FROM work_orders WHERE is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<WorkOrder> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    @Query(value = "SELECT * FROM work_orders WHERE id IN (:ids) AND is_deleted = false", nativeQuery = true)
    List<WorkOrder> findAllByIdInAndIsDeletedFalse(@Param("ids") Collection<UUID> ids);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM work_orders WHERE id = cast(:id as uuid) AND is_deleted = false)", nativeQuery = true)
    boolean existsByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT COUNT(*) FROM work_orders WHERE is_deleted = false", nativeQuery = true)
    long countByIsDeletedFalse();

    @Query(value = "SELECT EXISTS(SELECT 1 FROM work_orders WHERE number = cast(:number as varchar) AND is_deleted = false)", nativeQuery = true)
    boolean existsByNumberAndIsDeletedFalse(@Param("number") String number);

    @Query(value = "SELECT COUNT(*) FROM work_orders WHERE status = :status AND is_deleted = false", nativeQuery = true)
    long countByStatusAndIsDeletedFalse(@Param("status") String status);

    @Query(value = "SELECT * FROM work_orders WHERE repair_request_id = cast(:repairRequestId as uuid) AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<WorkOrder> findAllByRepairRequestIdAndIsDeletedFalseOrderByUpdatedAtDesc(@Param("repairRequestId") UUID repairRequestId);

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
            order by w.updated_at desc""", countQuery = """
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
            order by w.updated_at desc""", countQuery = """
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
