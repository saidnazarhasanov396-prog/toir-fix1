package com.toir.repository.plannedshutdown;

import com.toir.entity.plannedshutdown.PlannedShutdownGenerationRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PlannedShutdownGenerationRequestRepository extends JpaRepository<PlannedShutdownGenerationRequest, UUID> {
    Optional<PlannedShutdownGenerationRequest> findByPlannedShutdownIdAndIdempotencyKeyAndIsDeletedFalse(
            UUID plannedShutdownId, String idempotencyKey);

    @Query(value = "SELECT pg_advisory_xact_lock(hashtextextended(:lockKey, 0))", nativeQuery = true)
    void lockIdempotencyKey(@Param("lockKey") String lockKey);
}
