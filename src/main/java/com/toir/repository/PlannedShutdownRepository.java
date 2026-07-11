package com.toir.repository;

import com.toir.entity.PlannedShutdown;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface PlannedShutdownRepository extends JpaRepository<PlannedShutdown, UUID> {
    @Query(value = "SELECT * FROM planned_shutdowns WHERE id = cast(:id as uuid) AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<PlannedShutdown> findByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from PlannedShutdown s where s.id = :id and s.isDeleted = false")
    Optional<PlannedShutdown> findByIdAndIsDeletedFalseForUpdate(@Param("id") UUID id);

    @Query("select count(s) > 0 from PlannedShutdown s where s.code = :code and s.isDeleted = false")
    boolean existsByCodeAndIsDeletedFalse(@Param("code") String code);

    @Query("select count(s) > 0 from PlannedShutdown s where s.code = :code and s.id <> :id and s.isDeleted = false")
    boolean existsByCodeAndIdNotAndIsDeletedFalse(@Param("code") String code, @Param("id") UUID id);

    @Query(value = "SELECT * FROM planned_shutdowns WHERE is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<PlannedShutdown> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    @Query(value = "SELECT * FROM planned_shutdowns WHERE id IN (:ids) AND is_deleted = false", nativeQuery = true)
    List<PlannedShutdown> findAllByIdInAndIsDeletedFalse(@Param("ids") Collection<UUID> ids);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM planned_shutdowns WHERE id = cast(:id as uuid) AND is_deleted = false)", nativeQuery = true)
    boolean existsByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT COUNT(*) FROM planned_shutdowns WHERE is_deleted = false", nativeQuery = true)
    long countByIsDeletedFalse();

    @Query(value = "SELECT * FROM planned_shutdowns WHERE department_id = :departmentId AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<PlannedShutdown> findAllByDepartmentIdAndIsDeletedFalseOrderByStartAtDesc(@Param("departmentId") UUID departmentId);

    @Query(value = """
            SELECT * FROM planned_shutdowns 
            WHERE is_deleted = false 
              AND (cast(:departmentId as uuid) IS NULL OR department_id = cast(:departmentId as uuid))
              AND (:status IS NULL OR status = :status)
              AND (
                :searchPattern IS NULL 
                OR lower(coalesce(name, '')) LIKE :searchPattern 
                OR lower(coalesce(reason, '')) LIKE :searchPattern
              )
            ORDER BY start_at DESC
            """, nativeQuery = true)
    List<PlannedShutdown> findAllFiltered(
            @Param("departmentId") UUID departmentId,
            @Param("status") String status,
            @Param("searchPattern") String searchPattern
    );
}
