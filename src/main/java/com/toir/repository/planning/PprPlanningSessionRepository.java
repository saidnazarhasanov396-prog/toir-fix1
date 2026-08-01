package com.toir.repository.planning;

import com.toir.entity.planning.PprPlanningSession;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PprPlanningSessionRepository extends JpaRepository<PprPlanningSession, UUID> {

    Optional<PprPlanningSession> findByIdAndIsDeletedFalse(UUID id);

    java.util.List<PprPlanningSession> findAllByIsDeletedFalseOrderByCreatedAtDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from PprPlanningSession session where session.id = :id and session.isDeleted = false")
    Optional<PprPlanningSession> findByIdAndIsDeletedFalseForUpdate(@Param("id") UUID id);
}
