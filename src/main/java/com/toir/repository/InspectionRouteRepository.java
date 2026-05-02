package com.toir.repository;

import com.toir.entity.InspectionRoute;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface InspectionRouteRepository extends JpaRepository<InspectionRoute, UUID> {
    @Query(value = "SELECT * FROM inspection_routes WHERE id = cast(:id as uuid) AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<InspectionRoute> findByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT * FROM inspection_routes WHERE is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<InspectionRoute> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    @Query(value = "SELECT * FROM inspection_routes WHERE id IN (:ids) AND is_deleted = false", nativeQuery = true)
    List<InspectionRoute> findAllByIdInAndIsDeletedFalse(@Param("ids") Collection<UUID> ids);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM inspection_routes WHERE id = cast(:id as uuid) AND is_deleted = false)", nativeQuery = true)
    boolean existsByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT COUNT(*) FROM inspection_routes WHERE is_deleted = false", nativeQuery = true)
    long countByIsDeletedFalse();

    @Query(value = "SELECT EXISTS(SELECT 1 FROM inspection_routes WHERE code = :code AND is_deleted = false)", nativeQuery = true)
    boolean existsByCodeAndIsDeletedFalse(@Param("code") String code);

    @Query(value = "SELECT * FROM inspection_routes WHERE department_id = :departmentId AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<InspectionRoute> findAllByDepartmentIdAndIsDeletedFalseOrderByUpdatedAtDesc(@Param("departmentId") UUID departmentId);

    @Query(value = "SELECT * FROM inspection_routes WHERE is_active = true AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<InspectionRoute> findAllByActiveTrueAndIsDeletedFalse();
}
