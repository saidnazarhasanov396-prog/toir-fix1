package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.Location;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;


@Repository
public interface LocationRepository extends JpaRepository<Location, UUID> {
    boolean existsByCode(String code);
    List<Location> findAllByParentId(UUID parentId);
    List<Location> findAllByDepartmentId(UUID departmentId);
}
