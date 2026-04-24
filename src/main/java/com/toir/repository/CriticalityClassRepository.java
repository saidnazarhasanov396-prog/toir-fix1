package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.CriticalityClass;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;


@Repository
public interface CriticalityClassRepository extends JpaRepository<CriticalityClass, UUID> {
    boolean existsByCode(String code);
}
