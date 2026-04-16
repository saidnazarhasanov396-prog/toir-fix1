package com.toir.repository;
import com.toir.entity.DefectSeverity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DefectSeverityRepository extends JpaRepository<DefectSeverity, UUID> {
    boolean existsByCode(String code);
}
