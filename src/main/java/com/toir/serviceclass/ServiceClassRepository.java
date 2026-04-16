package com.toir.serviceclass;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ServiceClassRepository extends JpaRepository<ServiceClass, UUID> {
    boolean existsByCode(String code);
}
