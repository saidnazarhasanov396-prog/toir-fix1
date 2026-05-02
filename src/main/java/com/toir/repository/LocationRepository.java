package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.Location;
import com.toir.enums.LocationType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;


@Repository
public interface LocationRepository extends JpaRepository<Location, UUID> {
    java.util.Optional<Location> findByIdAndIsDeletedFalse(java.util.UUID id);

    java.util.List<Location> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    java.util.List<Location> findAllByIdInAndIsDeletedFalse(java.util.Collection<java.util.UUID> ids);

    boolean existsByIdAndIsDeletedFalse(java.util.UUID id);

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
            "(cast(:search as string) is null or " +
            "lower(l.code) like lower(concat('%', cast(:search as string), '%')) or " +
            "lower(l.name) like lower(concat('%', cast(:search as string), '%')) or " +
            "lower(l.nameEn) like lower(concat('%', cast(:search as string), '%')) or " +
            "lower(l.nameUz) like lower(concat('%', cast(:search as string), '%')) or " +
            "lower(l.description) like lower(concat('%', cast(:search as string), '%'))) " +
            "order by l.updatedAt desc")
    Page<Location> search(@Param("locationType") LocationType locationType,
                          @Param("search") String search,
                          Pageable pageable);
}
