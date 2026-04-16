package com.toir.defectseverity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DefectSeverityRepository extends JpaRepository<DefectSeverity, UUID> {
    boolean existsByCode(String code);
}
