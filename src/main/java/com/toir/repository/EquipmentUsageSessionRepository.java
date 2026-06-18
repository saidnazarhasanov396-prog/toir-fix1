package com.toir.repository;

import com.toir.entity.equipment.EquipmentUsageSession;
import com.toir.enums.EquipmentUsageSessionStatus;
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
public interface EquipmentUsageSessionRepository extends JpaRepository<EquipmentUsageSession, UUID> {

    Optional<EquipmentUsageSession> findByIdAndEquipmentIdAndIsDeletedFalse(UUID id, UUID equipmentId);

    Page<EquipmentUsageSession> findAllByEquipmentIdAndIsDeletedFalseOrderByStartedAtDesc(UUID equipmentId, Pageable pageable);

    boolean existsByEquipmentIdAndStatusAndIsDeletedFalse(UUID equipmentId, EquipmentUsageSessionStatus status);

    boolean existsByOperatorEmployeeIdAndStatusAndIsDeletedFalse(UUID operatorEmployeeId, EquipmentUsageSessionStatus status);

    @Query("""
            select s
            from EquipmentUsageSession s
            where s.isDeleted = false
              and s.status = com.toir.enums.EquipmentUsageSessionStatus.OPEN
              and s.returnedAt is null
              and s.dueAt is not null
              and s.dueAt < :now
              and s.overdueNotifiedAt is null
            order by s.dueAt asc
            """)
    List<EquipmentUsageSession> findOpenOverduePendingNotification(@Param("now") Instant now);
}
