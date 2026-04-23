package com.toir.repository;
import com.toir.entity.Department;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DepartmentRepository extends JpaRepository<Department, UUID> {
    boolean existsByCode(String code);
    List<Department> findAllByParentId(UUID parentId);
}
