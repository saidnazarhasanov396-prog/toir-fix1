package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.ServiceClass;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;


@Repository
public interface ServiceClassRepository extends JpaRepository<ServiceClass, UUID> {
    @Query(value = "SELECT EXISTS(SELECT 1 FROM service_classes WHERE code = :code AND is_deleted = false)", nativeQuery = true)
    boolean existsByCode(@Param("code") String code);
}
