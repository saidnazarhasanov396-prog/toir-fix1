package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.Department;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;


@Repository
public interface DepartmentRepository extends JpaRepository<Department, UUID> {
    @Query(value = "SELECT COUNT(*) > 0 FROM departments WHERE code = :code AND is_deleted = false", nativeQuery = true)
    boolean existsByCode(@Param("code") String code);

    @Query(value = "SELECT * FROM departments WHERE parent_id = :parentId AND is_deleted = false", nativeQuery = true)
    List<Department> findAllByParentId(@Param("parentId") UUID parentId);
}
