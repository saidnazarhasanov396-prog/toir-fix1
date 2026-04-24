package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.Material;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;


@Repository
public interface MaterialRepository extends JpaRepository<Material, UUID> {
    boolean existsByCode(String code);
}
