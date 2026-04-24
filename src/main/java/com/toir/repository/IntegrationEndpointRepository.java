package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.IntegrationEndpoint;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;


@Repository
public interface IntegrationEndpointRepository extends JpaRepository<IntegrationEndpoint, UUID> {
    boolean existsByCode(String code);
}
