package com.toir.location;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LocationRepository extends JpaRepository<Location, UUID> {
    boolean existsByCode(String code);
    List<Location> findAllByParentId(UUID parentId);
    List<Location> findAllByDepartmentId(UUID departmentId);
}
