package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.IntegrationEndpoint;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;


@Repository
public interface IntegrationEndpointRepository extends JpaRepository<IntegrationEndpoint, UUID> {
    @Query(value = "SELECT EXISTS(SELECT 1 FROM integration_endpoints WHERE code = :code AND is_deleted = false)", nativeQuery = true)
    boolean existsByCode(@Param("code") String code);
}
