package com.toir.inspection;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface InspectionRouteRepository extends JpaRepository<InspectionRoute, UUID> {
    boolean existsByCode(String code);
    List<InspectionRoute> findAllByDepartmentId(UUID departmentId);
    List<InspectionRoute> findAllByActiveTrue();
}
