package com.toir.repository;
import com.toir.entity.RepairMaterialUsage;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RepairMaterialUsageRepository extends JpaRepository<RepairMaterialUsage, UUID> {
    List<RepairMaterialUsage> findAllByWorkOrderId(UUID workOrderId);
}
