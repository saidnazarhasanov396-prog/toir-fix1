package com.toir.defectcategory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DefectCategoryRepository extends JpaRepository<DefectCategory, UUID> {
    boolean existsByCode(String code);
}
