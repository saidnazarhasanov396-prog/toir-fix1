package com.toir.service;

import com.toir.entity.DowntimeEvent;
import com.toir.entity.ReliabilityMetric;
import com.toir.entity.defects.Defect;
import com.toir.entity.equipment.CriticalityClass;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.maintenance.MaintenanceDueEvent;
import com.toir.entity.repair.RepairRequest;
import com.toir.enums.DefectStatus;
import com.toir.enums.MaintenanceDueEventStatus;
import com.toir.enums.MaintenanceDueStatus;
import com.toir.enums.PriorityLevel;
import com.toir.enums.RequestStatus;
import com.toir.repository.CriticalityClassRepository;
import com.toir.repository.DowntimeEventRepository;
import com.toir.repository.ReliabilityMetricRepository;
import com.toir.repository.defects.DefectRepository;
import com.toir.repository.maintenance.MaintenanceDueEventRepository;
import com.toir.repository.repair.RepairRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EquipmentRiskEvidenceService {

    private static final Duration RECENT_DOWNTIME_WINDOW = Duration.ofDays(90);

    private final CriticalityClassRepository criticalityClassRepository;
    private final DefectRepository defectRepository;
    private final ReliabilityMetricRepository reliabilityMetricRepository;
    private final DowntimeEventRepository downtimeEventRepository;
    private final RepairRequestRepository repairRequestRepository;
    private final MaintenanceDueEventRepository maintenanceDueEventRepository;

    public Map<UUID, EquipmentRiskEvidence> collect(List<Equipment> equipment) {
        if (equipment == null || equipment.isEmpty()) {
            return Map.of();
        }

        List<UUID> equipmentIds = equipment.stream()
                .map(Equipment::getId)
                .filter(id -> id != null)
                .toList();
        Set<UUID> equipmentIdSet = new HashSet<>(equipmentIds);

        Map<UUID, CriticalityClass> criticalityById = criticalityClassRepository
                .findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .filter(c -> c.getId() != null)
                .collect(Collectors.toMap(CriticalityClass::getId, Function.identity(), (first, second) -> first));

        Map<UUID, List<Defect>> openDefectsByEquipment = defectRepository
                .findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .filter(defect -> equipmentIdSet.contains(defect.getEquipmentId()))
                .filter(this::isOpenDefect)
                .collect(Collectors.groupingBy(Defect::getEquipmentId));

        Map<UUID, ReliabilityMetric> metricByEquipment = latestMetricsByEquipment(equipmentIdSet);
        Map<UUID, Double> downtimeHoursByEquipment = recentDowntimeHoursByEquipment(equipmentIds);
        Map<UUID, Long> openHighRepairRequestsByEquipment = openHighRepairRequestsByEquipment(equipmentIds);
        Map<UUID, Long> overdueMaintenanceByEquipment = overdueMaintenanceByEquipment(equipmentIdSet);

        Map<UUID, EquipmentRiskEvidence> evidenceByEquipment = new HashMap<>();
        for (Equipment item : equipment) {
            CriticalityClass criticalityClass = item.getCriticalityClassId() == null
                    ? null
                    : criticalityById.get(item.getCriticalityClassId());
            List<Defect> openDefects = openDefectsByEquipment.getOrDefault(item.getId(), List.of());
            ReliabilityMetric metric = metricByEquipment.get(item.getId());

            evidenceByEquipment.put(item.getId(), new EquipmentRiskEvidence(
                    item.getId(),
                    criticalityClass == null ? null : criticalityClass.getCode(),
                    criticalityClass == null ? null : criticalityClass.getName(),
                    criticalityClass == null ? null : criticalityClass.getLevel(),
                    criticalityClass == null ? null : criticalityClass.getRepairPriority(),
                    item.getStatus(),
                    openDefects.size(),
                    openDefects.stream().filter(defect -> defect.getRecurrenceCount() > 0).count(),
                    metric != null && metric.getMtbfHours() != null ? metric.getMtbfHours() : 0,
                    metric != null && metric.getMttrHours() != null ? metric.getMttrHours() : 0,
                    downtimeHoursByEquipment.getOrDefault(item.getId(), 0.0),
                    overdueMaintenanceByEquipment.getOrDefault(item.getId(), 0L),
                    openHighRepairRequestsByEquipment.getOrDefault(item.getId(), 0L)
            ));
        }

        return evidenceByEquipment;
    }

    private Map<UUID, ReliabilityMetric> latestMetricsByEquipment(Set<UUID> equipmentIds) {
        Map<UUID, ReliabilityMetric> metricByEquipment = new HashMap<>();
        reliabilityMetricRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .filter(metric -> equipmentIds.contains(metric.getEquipmentId()))
                .forEach(metric -> metricByEquipment.merge(metric.getEquipmentId(), metric, this::latestMetric));
        return metricByEquipment;
    }

    private ReliabilityMetric latestMetric(ReliabilityMetric first, ReliabilityMetric second) {
        LocalDate firstDate = first.getMetricDate();
        LocalDate secondDate = second.getMetricDate();
        if (firstDate == null && secondDate != null) {
            return second;
        }
        if (firstDate != null && secondDate == null) {
            return first;
        }
        if (firstDate != null && secondDate != null) {
            int dateComparison = firstDate.compareTo(secondDate);
            if (dateComparison < 0) {
                return second;
            }
            if (dateComparison > 0) {
                return first;
            }
        }
        Instant firstUpdatedAt = first.getUpdatedAt();
        Instant secondUpdatedAt = second.getUpdatedAt();
        if (firstUpdatedAt == null && secondUpdatedAt != null) {
            return second;
        }
        if (firstUpdatedAt != null && secondUpdatedAt == null) {
            return first;
        }
        if (firstUpdatedAt != null && secondUpdatedAt != null && firstUpdatedAt.isBefore(secondUpdatedAt)) {
            return second;
        }
        return first;
    }

    private Map<UUID, Double> recentDowntimeHoursByEquipment(Collection<UUID> equipmentIds) {
        if (equipmentIds.isEmpty()) {
            return Map.of();
        }
        Instant cutoff = Instant.now().minus(RECENT_DOWNTIME_WINDOW);
        return downtimeEventRepository.findAllByEquipmentIdInAndIsDeletedFalse(equipmentIds).stream()
                .filter(event -> event.getStartAt() != null && !event.getStartAt().isBefore(cutoff))
                .collect(Collectors.groupingBy(
                        DowntimeEvent::getEquipmentId,
                        Collectors.summingDouble(this::durationHours)
                ));
    }

    private Map<UUID, Long> openHighRepairRequestsByEquipment(Collection<UUID> equipmentIds) {
        if (equipmentIds.isEmpty()) {
            return Map.of();
        }
        return repairRequestRepository.findAllByEquipmentIdInAndIsDeletedFalse(equipmentIds).stream()
                .filter(request -> isOpenRepairRequest(request.getStatus()))
                .filter(request -> isHighRepairPriority(request.getPriority()))
                .collect(Collectors.groupingBy(RepairRequest::getEquipmentId, Collectors.counting()));
    }

    private Map<UUID, Long> overdueMaintenanceByEquipment(Set<UUID> equipmentIds) {
        return maintenanceDueEventRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .filter(event -> equipmentIds.contains(event.getEquipmentId()))
                .filter(event -> event.getDueStatus() == MaintenanceDueStatus.OVERDUE)
                .filter(event -> isOpenMaintenanceEvent(event.getStatus()))
                .collect(Collectors.groupingBy(MaintenanceDueEvent::getEquipmentId, Collectors.counting()));
    }

    private boolean isOpenDefect(Defect defect) {
        return defect.getStatus() == DefectStatus.OPEN
                || defect.getStatus() == DefectStatus.IN_ANALYSIS
                || defect.getStatus() == DefectStatus.IN_PROGRESS;
    }

    private boolean isOpenMaintenanceEvent(MaintenanceDueEventStatus status) {
        return status == MaintenanceDueEventStatus.DETECTED
                || status == MaintenanceDueEventStatus.AWAITING_APPROVAL
                || status == MaintenanceDueEventStatus.TASK_CREATED
                || status == MaintenanceDueEventStatus.WORK_ORDER_CREATED;
    }

    private boolean isOpenRepairRequest(RequestStatus status) {
        return status == RequestStatus.OPEN
                || status == RequestStatus.REGISTERED
                || status == RequestStatus.IN_REVIEW
                || status == RequestStatus.NEEDS_CLARIFICATION
                || status == RequestStatus.APPROVED
                || status == RequestStatus.ASSIGNED
                || status == RequestStatus.IN_PROGRESS;
    }

    private boolean isHighRepairPriority(PriorityLevel priority) {
        return priority == PriorityLevel.HIGH
                || priority == PriorityLevel.CRITICAL
                || priority == PriorityLevel.EMERGENCY;
    }

    private double durationHours(DowntimeEvent event) {
        if (event.getDurationMinutes() != null && event.getDurationMinutes() > 0) {
            return event.getDurationMinutes() / 60.0;
        }
        if (event.getStartAt() != null && event.getEndAt() != null && event.getEndAt().isAfter(event.getStartAt())) {
            return Duration.between(event.getStartAt(), event.getEndAt()).toMinutes() / 60.0;
        }
        return 0;
    }
}
