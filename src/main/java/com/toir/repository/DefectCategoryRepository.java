package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.DefectCategory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;


@Repository
public interface DefectCategoryRepository extends JpaRepository<DefectCategory, UUID> {
    boolean existsByCode(String code);
}
