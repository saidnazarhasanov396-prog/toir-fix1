package com.toir.brigade;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BrigadeRepository extends JpaRepository<Brigade, UUID> {
    boolean existsByCode(String code);
    List<Brigade> findAllByDepartmentId(UUID departmentId);
    List<Brigade> findAllByActiveTrue();
}
