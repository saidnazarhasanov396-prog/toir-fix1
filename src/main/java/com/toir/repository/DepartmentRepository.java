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
    java.util.Optional<Department> findByIdAndIsDeletedFalse(java.util.UUID id);

    java.util.List<Department> findAllByIsDeletedFalse();

    java.util.List<Department> findAllByIdInAndIsDeletedFalse(java.util.Collection<java.util.UUID> ids);

    boolean existsByIdAndIsDeletedFalse(java.util.UUID id);

    long countByIsDeletedFalse();

    @Query(value = "SELECT COUNT(*) > 0 FROM departments WHERE code = :code AND is_deleted = false", nativeQuery = true)
    boolean existsByCodeAndIsDeletedFalse(@Param("code") String code);

    @Query(value = "SELECT * FROM departments WHERE parent_id = :parentId AND is_deleted = false", nativeQuery = true)
    List<Department> findAllByParentIdAndIsDeletedFalse(@Param("parentId") UUID parentId);
}
