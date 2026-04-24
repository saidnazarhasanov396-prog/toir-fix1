package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.InspectionRoute;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;


@Repository
public interface InspectionRouteRepository extends JpaRepository<InspectionRoute, UUID> {
    boolean existsByCode(String code);
    List<InspectionRoute> findAllByDepartmentId(UUID departmentId);
    List<InspectionRoute> findAllByActiveTrue();
}
