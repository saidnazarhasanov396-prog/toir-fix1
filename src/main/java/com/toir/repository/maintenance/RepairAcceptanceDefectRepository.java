package com.toir.repository.maintenance;

import com.toir.entity.maintenance.RepairAcceptanceDefect;
import com.toir.enums.RepairAcceptanceDefectStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RepairAcceptanceDefectRepository extends JpaRepository<RepairAcceptanceDefect, UUID> {

    List<RepairAcceptanceDefect> findAllByAcceptance_IdAndIsDeletedFalseOrderByUpdatedAtDesc(UUID acceptanceId);

    boolean existsByAcceptance_IdAndCriticalTrueAndStatusAndIsDeletedFalse(
            UUID acceptanceId,
            RepairAcceptanceDefectStatus status
    );
}
