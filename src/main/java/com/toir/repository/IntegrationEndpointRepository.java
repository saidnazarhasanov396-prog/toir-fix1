package com.toir.repository;
import com.toir.entity.IntegrationEndpoint;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface IntegrationEndpointRepository extends JpaRepository<IntegrationEndpoint, UUID> {
    boolean existsByCode(String code);
}
