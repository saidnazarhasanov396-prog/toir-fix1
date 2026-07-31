package com.toir.repository.equipmentlifecycleexport;

import com.toir.entity.equipmentlifecycleexport.EquipmentLifecycleExportMembership;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EquipmentLifecycleExportMembershipRepository
        extends JpaRepository<EquipmentLifecycleExportMembership, UUID> {

    void deleteAllByJobId(UUID jobId);

    long countByJobId(UUID jobId);

    List<EquipmentLifecycleExportMembership> findByJobIdAndOrdinalGreaterThanOrderByOrdinalAsc(
            UUID jobId,
            long ordinal,
            Pageable pageable
    );
}
