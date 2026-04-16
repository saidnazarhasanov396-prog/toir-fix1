package com.toir.repository;
import com.toir.entity.ServiceClass;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ServiceClassRepository extends JpaRepository<ServiceClass, UUID> {
    boolean existsByCode(String code);
}
