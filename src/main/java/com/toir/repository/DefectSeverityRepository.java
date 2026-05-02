package com.toir.repository;

import com.toir.entity.DefectSeverity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface DefectSeverityRepository extends JpaRepository<DefectSeverity, UUID> {
    @Query(value = "SELECT * FROM defect_severities WHERE id = cast(:id as uuid) AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<DefectSeverity> findByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT * FROM defect_severities WHERE is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<DefectSeverity> findAllByIsDeletedFalseOrderByUpdatedAtDesc();
    @Query("""
            SELECT ds FROM DefectSeverity ds
            WHERE ds.isDeleted = false
                AND (cast(:search as string) IS NULL OR
                     lower(ds.code) LIKE lower(concat('%', cast(:search as string), '%')) OR
                     lower(ds.name) LIKE lower(concat('%', cast(:search as string), '%')))
            ORDER BY ds.updatedAt DESC
            """)
    List<DefectSeverity> findAllBySearch(@Param("search") String search);

    @Query(value = "SELECT * FROM defect_severities WHERE id IN (:ids) AND is_deleted = false", nativeQuery = true)
    List<DefectSeverity> findAllByIdInAndIsDeletedFalse(@Param("ids") Collection<UUID> ids);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM defect_severities WHERE id = cast(:id as uuid) AND is_deleted = false)", nativeQuery = true)
    boolean existsByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT COUNT(*) FROM defect_severities WHERE is_deleted = false", nativeQuery = true)
    long countByIsDeletedFalse();

    @Query(value = "SELECT EXISTS(SELECT 1 FROM defect_severities WHERE code = :code AND is_deleted = false)", nativeQuery = true)
    boolean existsByCodeAndIsDeletedFalse(@Param("code") String code);

    @Query(value = """
            SELECT COALESCE(MAX(CAST(SUBSTRING(code FROM LENGTH(:prefix) + 1) AS BIGINT)), 0)
            FROM defect_severities
            WHERE code LIKE CONCAT(:prefix, '%')
              AND SUBSTRING(code FROM LENGTH(:prefix) + 1) ~ '^[0-9]+$'
            """, nativeQuery = true)
    long maxSequenceByCodePrefix(@Param("prefix") String prefix);
}
