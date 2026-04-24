package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.CostCategory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;


@Repository
public interface CostCategoryRepository extends JpaRepository<CostCategory, UUID> {
    boolean existsByCode(String code);
}
