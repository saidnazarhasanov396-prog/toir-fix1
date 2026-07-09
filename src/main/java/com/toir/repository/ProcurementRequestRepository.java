package com.toir.repository;

import com.toir.entity.projects.ProcurementRequest;
import com.toir.enums.BudgetAllocationStatus;
import com.toir.enums.ProcurementRequestStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface ProcurementRequestRepository extends JpaRepository<ProcurementRequest, UUID> {
    @Query(value = "SELECT * FROM procurement_requests WHERE id = cast(:id as uuid) AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<ProcurementRequest> findByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ProcurementRequest p where p.id = :id and p.isDeleted = false")
    Optional<ProcurementRequest> findByIdAndIsDeletedFalseForUpdate(@Param("id") UUID id);

    @Query(value = "SELECT * FROM procurement_requests WHERE is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<ProcurementRequest> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    @Query(value = "SELECT * FROM procurement_requests WHERE id IN (:ids) AND is_deleted = false", nativeQuery = true)
    List<ProcurementRequest> findAllByIdInAndIsDeletedFalse(@Param("ids") Collection<UUID> ids);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM procurement_requests WHERE id = cast(:id as uuid) AND is_deleted = false)", nativeQuery = true)
    boolean existsByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT COUNT(*) FROM procurement_requests WHERE is_deleted = false", nativeQuery = true)
    long countByIsDeletedFalse();

    @Query(value = "SELECT * FROM procurement_requests WHERE status = cast(:status as varchar) AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<ProcurementRequest> findAllByStatusAndIsDeletedFalseOrderByUpdatedAtDesc(@Param("status") ProcurementRequestStatus status);

    @Query(value = "SELECT * FROM procurement_requests WHERE department_id = cast(:departmentId as uuid) AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<ProcurementRequest> findAllByDepartmentIdAndIsDeletedFalseOrderByUpdatedAtDesc(@Param("departmentId") UUID departmentId);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM procurement_requests WHERE number = cast(:number as varchar) AND is_deleted = false)", nativeQuery = true)
    boolean existsByNumberAndIsDeletedFalse(@Param("number") String number);

    @Query(value = """
            SELECT pg_advisory_xact_lock(
                hashtextextended(cast(:warehouseId as text) || ':' || cast(:sparePartId as text), 0)
            )
            """, nativeQuery = true)
    void lockAutoProcurementKey(@Param("warehouseId") UUID warehouseId,
                                @Param("sparePartId") UUID sparePartId);

    @Query(value = """
            SELECT EXISTS(
                SELECT 1
                FROM procurement_requests pr
                JOIN procurement_request_lines line ON line.request_id = pr.id
                WHERE pr.is_deleted = false
                  AND line.is_deleted = false
                  AND pr.source = 'AUTO'
                  AND pr.warehouse_id = cast(:warehouseId as uuid)
                  AND line.spare_part_id = cast(:sparePartId as uuid)
                  AND pr.status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'ORDERED', 'PARTIALLY_RECEIVED')
            )
            """, nativeQuery = true)
    boolean existsActiveAutoForWarehouseAndSparePart(@Param("warehouseId") UUID warehouseId,
                                                     @Param("sparePartId") UUID sparePartId);

    @Query(value = """
            SELECT EXISTS(
                SELECT 1
                FROM procurement_requests
                WHERE number = cast(:number as varchar)
                  AND status IN ('ORDERED', 'PARTIALLY_RECEIVED')
                  AND is_deleted = false
            )
            """, nativeQuery = true)
    boolean existsOpenReceivableByNumber(@Param("number") String number);

    @Query(value = "SELECT COUNT(*) FROM procurement_requests WHERE status = :status AND is_deleted = false", nativeQuery = true)
    long countByStatusAndIsDeletedFalse(@Param("status") String status);

    @Query("""
            select distinct p from ProcurementRequest p
            left join fetch p.lines
            where p.isDeleted = false
              and (:departmentId is null or p.departmentId = :departmentId)
              and (:status is null or p.status = :status)
              and (:allocationStatus is null or p.budgetAllocationStatus = :allocationStatus)
            order by p.updatedAt desc
            """)
    List<ProcurementRequest> findFinanceReviewQueue(
            @Param("departmentId") UUID departmentId,
            @Param("status") ProcurementRequestStatus status,
            @Param("allocationStatus") BudgetAllocationStatus allocationStatus);

    @Query(value = """
            SELECT * FROM procurement_requests
            WHERE is_deleted = false
            AND (cast(:status as varchar) IS NULL OR status = cast(:status as varchar))
            AND (cast(:departmentId as varchar) IS NULL OR department_id = cast(:departmentId as uuid))
            AND (cast(:type as varchar) IS NULL OR type = cast(:type as varchar))
            AND (cast(:sourceDefectId as varchar) IS NULL OR source_defect_id = cast(:sourceDefectId as uuid))
            AND (cast(:sourcePprTaskId as varchar) IS NULL OR source_ppr_task_id = cast(:sourcePprTaskId as uuid))
            AND (
                nullif(trim(cast(:search as varchar)), '') IS NULL
                OR lower(coalesce(number, '')) LIKE lower(concat('%', cast(:search as varchar), '%'))
                OR lower(coalesce(title, '')) LIKE lower(concat('%', cast(:search as varchar), '%'))
            )
            AND (cast(:minAmount as float8) IS NULL OR total_estimated_cost >= cast(:minAmount as float8))
            AND (cast(:maxAmount as float8) IS NULL OR total_estimated_cost <= cast(:maxAmount as float8))
            ORDER BY updated_at DESC
            """, nativeQuery = true)
    List<ProcurementRequest> search(
            @Param("search") String search,
            @Param("status") String status,
            @Param("departmentId") UUID departmentId,
            @Param("type") String type,
            @Param("sourceDefectId") UUID sourceDefectId,
            @Param("sourcePprTaskId") UUID sourcePprTaskId,
            @Param("minAmount") Double minAmount,
            @Param("maxAmount") Double maxAmount);
}
