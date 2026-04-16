package com.toir.rcm;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface RcmSnapshotRepository extends JpaRepository<RcmSnapshot, UUID> {
    List<RcmSnapshot> findAllByEquipmentIdOrderByCapturedAtDesc(UUID equipmentId);
    List<RcmSnapshot> findAllByCapturedAtAfterOrderByCapturedAtDesc(Instant since);
}
