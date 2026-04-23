package com.toir.repository;
import com.toir.entity.MaintenanceTemplate;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MaintenanceTemplateRepository extends JpaRepository<MaintenanceTemplate, UUID> {
    boolean existsByCode(String code);
}
