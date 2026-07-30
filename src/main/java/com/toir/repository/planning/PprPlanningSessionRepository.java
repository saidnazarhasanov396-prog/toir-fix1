package com.toir.repository.planning;

import com.toir.entity.planning.PprPlanningSession;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PprPlanningSessionRepository extends JpaRepository<PprPlanningSession, UUID> {

    Optional<PprPlanningSession> findByIdAndIsDeletedFalse(UUID id);
}
