package com.toir.repository.defects;

import com.toir.entity.defects.DefectCategory;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface DefectCategoryRepository extends JpaRepository<DefectCategory, UUID> {
    @Query(value = "SELECT * FROM defect_categories WHERE id = cast(:id as uuid) AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<DefectCategory> findByIdAndIsDeletedFalse(@Param("id") UUID id);
    @Query("""
    SELECT dc FROM DefectCategory dc
    WHERE dc.isDeleted = false
        AND (cast(:search as string) IS NULL OR
                 lower(dc.code) LIKE lower(concat('%', cast(:search as string), '%')) OR
                 lower(dc.name) LIKE lower(concat('%', cast(:search as string), '%')))
""")
    List<DefectCategory> findAll(
            @Param("search") String search
    );



    @Query(value = "SELECT * FROM defect_categories WHERE id IN (:ids) AND is_deleted = false", nativeQuery = true)
    List<DefectCategory> findAllByIdInAndIsDeletedFalse(@Param("ids") Collection<UUID> ids);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM defect_categories WHERE id = cast(:id as uuid) AND is_deleted = false)", nativeQuery = true)
    boolean existsByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT COUNT(*) FROM defect_categories WHERE is_deleted = false", nativeQuery = true)
    long countByIsDeletedFalse();

    @Query(value = "SELECT EXISTS(SELECT 1 FROM defect_categories WHERE code = :code AND is_deleted = false)", nativeQuery = true)
    boolean existsByCodeAndIsDeletedFalse(@Param("code") String code);

    boolean existsByCode(String code);

    @Query(value = """
            SELECT COALESCE(MAX(CAST(SUBSTRING(code FROM LENGTH(:prefix) + 1) AS BIGINT)), 0)
            FROM defect_categories
            WHERE code LIKE CONCAT(:prefix, '%')
              AND SUBSTRING(code FROM LENGTH(:prefix) + 1) ~ '^[0-9]+$'
            """, nativeQuery = true)
    long maxSequenceByCodePrefix(@Param("prefix") String prefix);

    List<DefectCategory> findAllByIsDeletedFalseOrderByUpdatedAtDesc();
}
