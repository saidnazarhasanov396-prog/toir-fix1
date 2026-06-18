package com.toir.repository;

import com.toir.entity.projects.ProcurementRequest;
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

    @Query(value = """
            SELECT * FROM procurement_requests
            WHERE is_deleted = false
            AND (cast(:status as varchar) IS NULL OR status = cast(:status as varchar))
            AND (cast(:departmentId as varchar) IS NULL OR department_id = cast(:departmentId as uuid))
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
            @Param("minAmount") Double minAmount,
            @Param("maxAmount") Double maxAmount);
}
