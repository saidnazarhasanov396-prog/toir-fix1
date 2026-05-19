package com.toir.repository.inspection;

import com.toir.entity.inspection.InspectionRoute;
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

    @Query(value = """
            SELECT ir.* FROM inspection_routes ir 
            WHERE  is_deleted = false 
            AND (CAST(:departmentId as uuid) IS NULL OR ir.department_id = cast(:departmentId as uuid))
            AND (CAST(:activeOnly as bool) IS NULL OR ir.is_active = :activeOnly)
            AND (CAST(:search AS text) IS NULL OR :search = '' OR 
                             LOWER(ir.code) LIKE LOWER(CONCAT('%', CAST(:search AS text), '%')) OR 
                             LOWER(ir.name) LIKE LOWER(CONCAT('%', CAST(:search AS text), '%')))
            ORDER BY updated_at DESC""", nativeQuery = true)
    List<InspectionRoute> findAllByDepartmentIdAndIsDeletedFalseOrderByUpdatedAtDesc(
            @Param("departmentId") UUID departmentId,
            @Param("activeOnly") Boolean activeOnly,
            @Param("search")  String search
    );

    @Query(value = "SELECT * FROM inspection_routes WHERE is_active = true AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<InspectionRoute> findAllByActiveTrueAndIsDeletedFalse();
}
