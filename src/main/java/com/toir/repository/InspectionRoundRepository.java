package com.toir.repository;
import com.toir.entity.InspectionRound;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface InspectionRoundRepository extends JpaRepository<InspectionRound, UUID> {
    List<InspectionRound> findAllByRouteIdOrderByStartedAtDesc(UUID routeId);
    List<InspectionRound> findAllByPerformedByOrderByStartedAtDesc(UUID performedBy);
    List<InspectionRound> findAllByStartedAtAfterOrderByStartedAtDesc(Instant since);
}
