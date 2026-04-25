package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.InspectionRoute;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;


@Repository
public interface InspectionRouteRepository extends JpaRepository<InspectionRoute, UUID> {
    java.util.Optional<InspectionRoute> findByIdAndIsDeletedFalse(java.util.UUID id);

    java.util.List<InspectionRoute> findAllByIsDeletedFalse();

    java.util.List<InspectionRoute> findAllByIdInAndIsDeletedFalse(java.util.Collection<java.util.UUID> ids);

    boolean existsByIdAndIsDeletedFalse(java.util.UUID id);

    long countByIsDeletedFalse();

    @Query(value = "SELECT EXISTS(SELECT 1 FROM inspection_routes WHERE code = :code AND is_deleted = false)", nativeQuery = true)
    boolean existsByCodeAndIsDeletedFalse(@Param("code") String code);

    @Query(value = "SELECT * FROM inspection_routes WHERE department_id = :departmentId AND is_deleted = false", nativeQuery = true)
    List<InspectionRoute> findAllByDepartmentIdAndIsDeletedFalse(@Param("departmentId") UUID departmentId);

    @Query(value = "SELECT * FROM inspection_routes WHERE is_active = true AND is_deleted = false", nativeQuery = true)
    List<InspectionRoute> findAllByActiveTrueAndIsDeletedFalse();
}
