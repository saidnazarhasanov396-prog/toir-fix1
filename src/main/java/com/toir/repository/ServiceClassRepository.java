package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.ServiceClass;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;


@Repository
public interface ServiceClassRepository extends JpaRepository<ServiceClass, UUID> {
    boolean existsByCode(String code);
}
