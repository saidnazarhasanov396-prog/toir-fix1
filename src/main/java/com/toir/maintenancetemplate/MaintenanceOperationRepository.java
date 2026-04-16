package com.toir.maintenancetemplate;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MaintenanceOperationRepository extends JpaRepository<MaintenanceOperation, UUID> {
}
