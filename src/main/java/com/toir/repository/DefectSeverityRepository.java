package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.DefectSeverity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;


@Repository
public interface DefectSeverityRepository extends JpaRepository<DefectSeverity, UUID> {
    boolean existsByCode(String code);
}
