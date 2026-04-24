package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.Brigade;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;


@Repository
public interface BrigadeRepository extends JpaRepository<Brigade, UUID> {
    boolean existsByCode(String code);
    List<Brigade> findAllByDepartmentId(UUID departmentId);
    List<Brigade> findAllByActiveTrue();
}
