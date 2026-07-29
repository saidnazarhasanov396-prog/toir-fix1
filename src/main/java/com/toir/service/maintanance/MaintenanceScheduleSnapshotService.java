package com.toir.service.maintanance;

import com.toir.entity.maintenance.MaintenanceScheduleCalculationItem;
import com.toir.exception.MaintenanceScheduleSnapshotConflictException;
import com.toir.exception.MaintenanceScheduleSnapshotConflictException.Reason;
import com.toir.exception.RestException;
import com.toir.repository.PprPlanRepository;
import com.toir.repository.maintenance.MaintenanceScheduleCalculationItemRepository;
import jakarta.persistence.EntityManager;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MaintenanceScheduleSnapshotService {

    private final MaintenanceScheduleCalculationItemRepository repository;
    private final PprPlanRepository planRepository;
    private final EntityManager entityManager;
    private final MaintenanceScheduleSourceItemKeyGenerator keyGenerator;

    @Transactional
    public List<MaintenanceScheduleCalculationItem> insertRevision(
            UUID planId,
            long calculationRevision,
            List<MaintenanceScheduleCalculationItem> items) {
        requireRevisionCoordinates(planId, calculationRevision);
        if (items == null || items.isEmpty()) {
            throw RestException.badRequest(
                    "Maintenance schedule snapshot revision requires at least one item");
        }
        planRepository.findByIdAndIsDeletedFalseForUpdate(planId)
                .orElseThrow(() -> RestException.notFound("PPR plan not found"));
        if (repository.countByPlanIdAndCalculationRevision(
                planId, calculationRevision) > 0) {
            throw new MaintenanceScheduleSnapshotConflictException(
                    Reason.REVISION_ALREADY_EXISTS);
        }

        validateCompleteRevision(planId, calculationRevision, items);
        List<MaintenanceScheduleCalculationItem> saved =
                repository.saveAll(List.copyOf(items));
        entityManager.flush();
        if (repository.countByPlanIdAndCalculationRevision(
                planId, calculationRevision) != items.size()) {
            throw RestException.conflict(
                    "Maintenance schedule snapshot revision is incomplete");
        }
        return List.copyOf(saved);
    }

    @Transactional(readOnly = true)
    public List<MaintenanceScheduleCalculationItem> readRevision(
            UUID planId,
            long calculationRevision) {
        requireRevisionCoordinates(planId, calculationRevision);
        return List.copyOf(
                repository.findAllByPlanIdAndCalculationRevisionOrderBySourceItemKey(
                        planId, calculationRevision)
        );
    }

    @Transactional(readOnly = true)
    public long countRevision(UUID planId, long calculationRevision) {
        requireRevisionCoordinates(planId, calculationRevision);
        return repository.countByPlanIdAndCalculationRevision(
                planId, calculationRevision);
    }

    public String sourceItemKey(
            MaintenanceScheduleSourceItemCoordinates coordinates) {
        return keyGenerator.generate(coordinates);
    }

    public int sourceItemKeyVersion() {
        return keyGenerator.version();
    }

    private void validateCompleteRevision(
            UUID planId,
            long calculationRevision,
            List<MaintenanceScheduleCalculationItem> items) {
        Set<String> sourceItemKeys = new HashSet<>();
        for (MaintenanceScheduleCalculationItem item : items) {
            if (item == null
                    || item.getPlan() == null
                    || !Objects.equals(item.getPlan().getId(), planId)
                    || !Objects.equals(
                            item.getCalculationRevision(), calculationRevision)) {
                throw RestException.badRequest(
                        "All snapshot items must belong to the same plan and revision");
            }
            if (item.getId() != null
                    || item.getCreatedAt() != null
                    || item.getUpdatedAt() != null) {
                throw RestException.badRequest(
                        "Snapshot revision accepts only new calculation items");
            }
            if (item.getCycleOrdinal() == null
                    || item.getPlannedDate() == null
                    || item.getTaskTitleSnapshot() == null
                    || item.getTaskTitleSnapshot().isBlank()) {
                throw RestException.badRequest(
                        "Snapshot item identity, schedule and task title are required");
            }
            String sourceItemKey = sourceItemKey(
                    new MaintenanceScheduleSourceItemCoordinates(
                            item.getEquipmentId(),
                            item.getRegulationId(),
                            item.getMaintenanceRuleId(),
                            item.getTemplateId(),
                            item.getTriggerType(),
                            item.getTriggerDiscriminator(),
                            item.getMaintenanceType(),
                            item.getPlannedDate(),
                            item.getScheduledStart(),
                            item.getCycleOrdinal()
                    )
            );
            item.setSourceItemKey(sourceItemKey);
            item.setSourceItemKeyVersion(sourceItemKeyVersion());
            if (!sourceItemKeys.add(sourceItemKey)) {
                throw new MaintenanceScheduleSnapshotConflictException(
                        Reason.DUPLICATE_SOURCE_ITEM_KEY);
            }
        }
    }

    private static void requireRevisionCoordinates(
            UUID planId,
            long calculationRevision) {
        if (planId == null || calculationRevision < 1) {
            throw RestException.badRequest(
                    "planId and calculationRevision >= 1 are required");
        }
    }
}
