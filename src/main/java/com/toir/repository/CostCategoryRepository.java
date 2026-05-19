package com.toir.repository;

import com.toir.entity.projects.CostCategory;
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
public interface CostCategoryRepository extends JpaRepository<CostCategory, UUID> {
    @Query(value = "SELECT * FROM cost_categories WHERE id = cast(:id as uuid) AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<CostCategory> findByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT * FROM cost_categories WHERE is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<CostCategory> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    @Query(value = "SELECT * FROM cost_categories WHERE id IN (:ids) AND is_deleted = false", nativeQuery = true)
    List<CostCategory> findAllByIdInAndIsDeletedFalse(@Param("ids") Collection<UUID> ids);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM cost_categories WHERE id = cast(:id as uuid) AND is_deleted = false)", nativeQuery = true)
    boolean existsByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT COUNT(*) FROM cost_categories WHERE is_deleted = false", nativeQuery = true)
    long countByIsDeletedFalse();

    @Query(value = "SELECT EXISTS(SELECT 1 FROM cost_categories WHERE code = :code AND is_deleted = false)", nativeQuery = true)
    boolean existsByCodeAndIsDeletedFalse(@Param("code") String code);

    @Query(value = """
            SELECT COALESCE(MAX(CAST(SUBSTRING(code FROM LENGTH(:prefix) + 1) AS BIGINT)), 0)
            FROM cost_categories
            WHERE code LIKE CONCAT(:prefix, '%')
              AND SUBSTRING(code FROM LENGTH(:prefix) + 1) ~ '^[0-9]+$'
            """, nativeQuery = true)
    long maxSequenceByCodePrefix(@Param("prefix") String prefix);

    @Query(value = """ 
            SELECT cr.* FROM cost_categories cr  
            WHERE is_deleted = false 
            AND (CAST(:search AS text) IS NULL OR :search = '' OR
                            LOWER(cr.name ) LIKE LOWER(CONCAT('%',CAST(:search AS text),'%')) OR 
                            LOWER(cr.code ) LIKE LOWER(CONCAT('%',CAST(:search AS text),'%'))
            ) 
            order by cr.updated_at
            """, nativeQuery = true)
    Page<CostCategory> findAll(
            @Param("search") String search,
            Pageable pageable
    );
}
