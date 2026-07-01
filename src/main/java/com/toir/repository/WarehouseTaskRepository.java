package com.toir.repository;

import com.toir.entity.warehouse.WarehouseTask;
import com.toir.enums.WarehouseTaskSourceType;
import com.toir.enums.WarehouseTaskStatus;
import com.toir.enums.WarehouseTaskType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WarehouseTaskRepository extends JpaRepository<WarehouseTask, UUID> {

    Optional<WarehouseTask> findByIdAndIsDeletedFalse(UUID id);

    Optional<WarehouseTask> findByGenerationKeyAndIsDeletedFalse(String generationKey);

    boolean existsByGenerationKeyAndIsDeletedFalse(String generationKey);

    long countByIsDeletedFalse();

    long countByWarehouseIdAndIsDeletedFalse(UUID warehouseId);

    @Query("""
            select count(task)
            from WarehouseTask task
            where task.isDeleted = false
              and (:warehouseId is null or task.warehouseId = :warehouseId)
              and task.status = :status
            """)
    long countByStatus(@Param("warehouseId") UUID warehouseId,
                       @Param("status") WarehouseTaskStatus status);

    @Query("""
            select count(task)
            from WarehouseTask task
            where task.isDeleted = false
              and (:warehouseId is null or task.warehouseId = :warehouseId)
              and task.status in (com.toir.enums.WarehouseTaskStatus.OPEN, com.toir.enums.WarehouseTaskStatus.ASSIGNED, com.toir.enums.WarehouseTaskStatus.IN_PROGRESS)
              and task.dueAt is not null
              and task.dueAt < :now
            """)
    long countOverdue(@Param("warehouseId") UUID warehouseId,
                      @Param("now") Instant now);

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

    @Query("""
            select task
            from WarehouseTask task
            where task.isDeleted = false
              and (:warehouseId is null or task.warehouseId = :warehouseId)
            order by task.updatedAt desc
            """)
    List<WarehouseTask> findRecent(@Param("warehouseId") UUID warehouseId,
                                   Pageable pageable);

    @Query("""
            select task
            from WarehouseTask task
            where task.isDeleted = false
              and task.sourceType = :sourceType
              and task.sourceId = :sourceId
            order by task.updatedAt desc
            """)
    List<WarehouseTask> findBySource(@Param("sourceType") WarehouseTaskSourceType sourceType,
                                     @Param("sourceId") UUID sourceId,
                                     Pageable pageable);
}
