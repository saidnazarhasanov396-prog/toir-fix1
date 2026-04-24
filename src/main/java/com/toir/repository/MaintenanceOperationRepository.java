package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.MaintenanceOperation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;


@Repository
public interface MaintenanceOperationRepository extends JpaRepository<MaintenanceOperation, UUID> {
}
