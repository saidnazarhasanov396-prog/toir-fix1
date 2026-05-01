package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.DefectCategory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;


@Repository
public interface DefectCategoryRepository extends JpaRepository<DefectCategory, UUID> {
    java.util.Optional<DefectCategory> findByIdAndIsDeletedFalse(java.util.UUID id);
    @Query("""
    SELECT dc FROM DefectCategory dc
    WHERE dc.isDeleted = false
        AND (:code IS NULL OR dc.code = :code)
        AND (:name IS NULL OR dc.name = :name)
        AND (:search IS NULL OR 
             lower(dc.code) LIKE lower(CONCAT('%', :search, '%')) OR
             lower(dc.name) LIKE lower(CONCAT('%', :search, '%')))
""")
    List<DefectCategory> findAll(
            @Param("search") String search,
            @Param("code") String code,
            @Param("name") String name
    );
    
    

    java.util.List<DefectCategory> findAllByIdInAndIsDeletedFalse(java.util.Collection<java.util.UUID> ids);

    boolean existsByIdAndIsDeletedFalse(java.util.UUID id);

    long countByIsDeletedFalse();

    @Query(value = "SELECT EXISTS(SELECT 1 FROM defect_categories WHERE code = :code AND is_deleted = false)", nativeQuery = true)
    boolean existsByCodeAndIsDeletedFalse(@Param("code") String code);

    java.util.List<DefectCategory> findAllByIsDeletedFalse();
}
