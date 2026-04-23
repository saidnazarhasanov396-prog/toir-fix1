package com.toir.repository;
import com.toir.entity.Material;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MaterialRepository extends JpaRepository<Material, UUID> {
    boolean existsByCode(String code);
}
