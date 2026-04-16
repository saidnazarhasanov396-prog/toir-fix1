package com.toir.costcategory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CostCategoryRepository extends JpaRepository<CostCategory, UUID> {
    boolean existsByCode(String code);
}
