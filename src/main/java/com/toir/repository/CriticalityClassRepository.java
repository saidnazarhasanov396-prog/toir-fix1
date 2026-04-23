package com.toir.repository;
import com.toir.entity.CriticalityClass;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CriticalityClassRepository extends JpaRepository<CriticalityClass, UUID> {
    boolean existsByCode(String code);
}
