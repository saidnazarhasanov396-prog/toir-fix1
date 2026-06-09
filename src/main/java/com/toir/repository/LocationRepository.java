package com.toir.repository;

import com.toir.entity.Location;
import com.toir.enums.LocationType;
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
public interface LocationRepository extends JpaRepository<Location, UUID> {
    @Query(value = "SELECT * FROM locations WHERE id = cast(:id as uuid) AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<Location> findByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT * FROM locations WHERE is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<Location> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    @Query(value = "SELECT * FROM locations WHERE id IN (:ids) AND is_deleted = false", nativeQuery = true)
    List<Location> findAllByIdInAndIsDeletedFalse(@Param("ids") Collection<UUID> ids);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM locations WHERE id = cast(:id as uuid) AND is_deleted = false)", nativeQuery = true)
    boolean existsByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT COUNT(*) FROM locations WHERE is_deleted = false", nativeQuery = true)
    long countByIsDeletedFalse();

    @Query(value = "SELECT EXISTS(SELECT 1 FROM locations WHERE code = :code AND is_deleted = false)", nativeQuery = true)
    boolean existsByCodeAndIsDeletedFalse(@Param("code") String code);

    boolean existsByCode(String code);

    @Query(value = """
            SELECT COALESCE(MAX(CAST(SUBSTRING(code FROM LENGTH(:prefix) + 1) AS BIGINT)), 0)
            FROM locations
            WHERE code LIKE CONCAT(:prefix, '%')
              AND SUBSTRING(code FROM LENGTH(:prefix) + 1) ~ '^[0-9]+$'
            """, nativeQuery = true)
    long maxSequenceByCodePrefix(@Param("prefix") String prefix);

    @Query(value = "SELECT * FROM locations WHERE parent_id = :parentId AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<Location> findAllByParentIdAndIsDeletedFalse(@Param("parentId") UUID parentId);

    @Query(value = "SELECT * FROM locations WHERE department_id = :departmentId AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<Location> findAllByDepartmentIdAndIsDeletedFalse(@Param("departmentId") UUID departmentId);

    @Query("select l from Location l where " +
            "l.isDeleted = false and " +
            "(:locationType is null or l.type = :locationType) and " +
            "(:departmentId is null or l.departmentId = :departmentId) and " +
            "(cast(:search as string) is null or " +
            "lower(l.code) like lower(concat('%', cast(:search as string), '%')) or " +
            "lower(l.name) like lower(concat('%', cast(:search as string), '%')) or " +
            "lower(l.nameEn) like lower(concat('%', cast(:search as string), '%')) or " +
            "lower(l.nameUz) like lower(concat('%', cast(:search as string), '%')) or " +
            "lower(l.description) like lower(concat('%', cast(:search as string), '%'))) " +
            "order by l.updatedAt desc")
    Page<Location> search(@Param("locationType") LocationType locationType,
                          @Param("search") String search,
                          @Param("departmentId") UUID departmentId,
                          Pageable pageable);
}
