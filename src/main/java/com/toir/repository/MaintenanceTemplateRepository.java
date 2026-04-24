package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.MaintenanceTemplate;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;


@Repository
public interface MaintenanceTemplateRepository extends JpaRepository<MaintenanceTemplate, UUID> {
    boolean existsByCode(String code);
}
