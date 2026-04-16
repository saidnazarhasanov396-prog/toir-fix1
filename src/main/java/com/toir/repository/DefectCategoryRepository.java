package com.toir.repository;
import com.toir.entity.DefectCategory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DefectCategoryRepository extends JpaRepository<DefectCategory, UUID> {
    boolean existsByCode(String code);
}
