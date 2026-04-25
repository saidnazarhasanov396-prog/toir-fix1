package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.CostCategory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;


@Repository
public interface CostCategoryRepository extends JpaRepository<CostCategory, UUID> {
    java.util.Optional<CostCategory> findByIdAndIsDeletedFalse(java.util.UUID id);

    java.util.List<CostCategory> findAllByIsDeletedFalse();

    java.util.List<CostCategory> findAllByIdInAndIsDeletedFalse(java.util.Collection<java.util.UUID> ids);

    boolean existsByIdAndIsDeletedFalse(java.util.UUID id);

    long countByIsDeletedFalse();

    @Query(value = "SELECT EXISTS(SELECT 1 FROM cost_categories WHERE code = :code AND is_deleted = false)", nativeQuery = true)
    boolean existsByCodeAndIsDeletedFalse(@Param("code") String code);
}
