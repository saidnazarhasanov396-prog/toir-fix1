package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.Location;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;


@Repository
public interface LocationRepository extends JpaRepository<Location, UUID> {
    java.util.Optional<Location> findByIdAndIsDeletedFalse(java.util.UUID id);

    java.util.List<Location> findAllByIsDeletedFalse();

    java.util.List<Location> findAllByIdInAndIsDeletedFalse(java.util.Collection<java.util.UUID> ids);

    boolean existsByIdAndIsDeletedFalse(java.util.UUID id);

    long countByIsDeletedFalse();

    @Query(value = "SELECT EXISTS(SELECT 1 FROM locations WHERE code = :code AND is_deleted = false)", nativeQuery = true)
    boolean existsByCodeAndIsDeletedFalse(@Param("code") String code);

    @Query(value = "SELECT * FROM locations WHERE parent_id = :parentId AND is_deleted = false", nativeQuery = true)
    List<Location> findAllByParentIdAndIsDeletedFalse(@Param("parentId") UUID parentId);

    @Query(value = "SELECT * FROM locations WHERE department_id = :departmentId AND is_deleted = false", nativeQuery = true)
    List<Location> findAllByDepartmentIdAndIsDeletedFalse(@Param("departmentId") UUID departmentId);
}
