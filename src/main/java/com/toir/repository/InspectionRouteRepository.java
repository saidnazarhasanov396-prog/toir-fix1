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
    @Query(value = "SELECT EXISTS(SELECT 1 FROM inspection_routes WHERE code = :code AND is_deleted = false)", nativeQuery = true)
    boolean existsByCode(@Param("code") String code);

    @Query(value = "SELECT * FROM inspection_routes WHERE department_id = :departmentId AND is_deleted = false", nativeQuery = true)
    List<InspectionRoute> findAllByDepartmentId(@Param("departmentId") UUID departmentId);

    @Query(value = "SELECT * FROM inspection_routes WHERE is_active = true AND is_deleted = false", nativeQuery = true)
    List<InspectionRoute> findAllByActiveTrue();
}
