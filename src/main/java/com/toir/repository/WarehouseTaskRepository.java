package com.toir.repository;

import com.toir.entity.warehouse.WarehouseTask;
import com.toir.enums.WarehouseTaskStatus;
import com.toir.enums.WarehouseTaskType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WarehouseTaskRepository extends JpaRepository<WarehouseTask, UUID> {

    Optional<WarehouseTask> findByIdAndIsDeletedFalse(UUID id);

    Optional<WarehouseTask> findByGenerationKeyAndIsDeletedFalse(String generationKey);

    boolean existsByGenerationKeyAndIsDeletedFalse(String generationKey);

    long countByIsDeletedFalse();

    @Query("""
            select task
            from WarehouseTask task
            where task.isDeleted = false
              and (:warehouseId is null or task.warehouseId = :warehouseId)
              and (:status is null or task.status = :status)
              and (:taskType is null or task.taskType = :taskType)
              and (:assignedToId is null or task.assignedToId = :assignedToId)
            order by task.createdAt desc
            """)
    Page<WarehouseTask> search(@Param("warehouseId") UUID warehouseId,
                               @Param("status") WarehouseTaskStatus status,
                               @Param("taskType") WarehouseTaskType taskType,
                               @Param("assignedToId") UUID assignedToId,
                               Pageable pageable);
}
