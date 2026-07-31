package com.toir.service;

import com.toir.dto.rcm.EquipmentRiskScore;
import com.toir.dto.rcm.autoplan.RcmAutoPlanDecision;
import com.toir.dto.rcm.autoplan.RcmAutoPlanPreviewResponse;
import com.toir.dto.rcm.autoplan.RcmAutoPlanPreviewRow;
import com.toir.entity.PprPlan;
import com.toir.entity.PprTask;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.maintenance.MaintenanceRegulation;
import com.toir.enums.PprTaskStatus;
import com.toir.enums.PriorityLevel;
import com.toir.exception.RestException;
import com.toir.repository.PprPlanRepository;
import com.toir.repository.PprTaskRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.maintenance.MaintenanceRegulationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RcmAutoPlanPreviewService {
    public static final String SOURCE_TYPE = "RCM_AUTO_PLAN";

    private final RcmService rcmService;
    private final EquipmentRepository equipmentRepository;
    private final MaintenanceRegulationRepository regulationRepository;
    private final PprPlanRepository planRepository;
    private final PprTaskRepository taskRepository;

    @Transactional(readOnly = true)
    public RcmAutoPlanPreviewResponse preview(int riskThreshold, UUID planId) {
        if (riskThreshold < 1 || riskThreshold > 100) {
            throw RestException.badRequest("riskThreshold must be between 1 and 100", "RCM_THRESHOLD_INVALID");
        }
        PprPlan plan = resolvePlan(planId);
        List<PprTask> existingTasks = taskRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc();
        LocalDateTime scheduledStart = LocalDateTime.of(LocalDate.now().plusDays(1), LocalTime.of(8, 0));

        List<EquipmentRiskScore> scores = rcmService.computeAll().stream()
                .filter(score -> score.riskScore() >= riskThreshold)
                .sorted(Comparator.comparing(EquipmentRiskScore::equipmentCode,
                                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(EquipmentRiskScore::equipmentId))
                .toList();
        List<RcmAutoPlanPreviewRow> rows = new ArrayList<>();
        for (EquipmentRiskScore score : scores) {
            Equipment equipment = equipmentRepository.findByIdAndIsDeletedFalse(score.equipmentId()).orElse(null);
            if (equipment == null || equipment.getEquipmentTypeId() == null) {
                rows.add(row(score, equipment, plan, null, scheduledStart, RcmAutoPlanDecision.SKIP,
                        null, List.of(equipment == null ? "EQUIPMENT_NOT_FOUND" : "EQUIPMENT_TYPE_MISSING")));
                continue;
            }
            List<MaintenanceRegulation> regulations = regulationRepository
                    .findAllByEquipmentTypeIdAndActiveTrueAndIsDeletedFalse(equipment.getEquipmentTypeId());
            if (plan == null) {
                rows.add(row(score, equipment, null, null, scheduledStart, RcmAutoPlanDecision.CONFLICT,
                        null, List.of("PLAN_NOT_FOUND")));
            } else if (regulations.isEmpty()) {
                rows.add(row(score, equipment, plan, null, scheduledStart, RcmAutoPlanDecision.CONFLICT,
                        null, List.of("NO_ACTIVE_REGULATION")));
            } else if (regulations.size() > 1) {
                rows.add(row(score, equipment, plan, null, scheduledStart, RcmAutoPlanDecision.CONFLICT,
                        null, List.of("AMBIGUOUS_REGULATION")));
            } else {
                MaintenanceRegulation regulation = regulations.getFirst();
                String sourceKey = sourceKey(plan.getId(), equipment.getId(), regulation.getId());
                PprTask duplicate = existingTasks.stream()
                        .filter(task -> SOURCE_TYPE.equals(task.getSourceType()))
                        .filter(task -> sourceKey.equals(task.getSourceKey()))
                        .filter(this::active)
                        .findFirst().orElse(null);
                if (duplicate != null) {
                    rows.add(row(score, equipment, plan, regulation, scheduledStart,
                            RcmAutoPlanDecision.DUPLICATE, duplicate, List.of("ACTIVE_RCM_TASK_EXISTS")));
                    continue;
                }
                LocalDateTime end = scheduledStart.plusMinutes(laborMinutes(regulation));
                PprTask overlap = existingTasks.stream()
                        .filter(this::active)
                        .filter(task -> equipment.getId().equals(task.getEquipmentId()))
                        .filter(task -> overlaps(task, scheduledStart, end))
                        .findFirst().orElse(null);
                rows.add(row(score, equipment, plan, regulation, scheduledStart,
                        overlap == null ? RcmAutoPlanDecision.CREATE : RcmAutoPlanDecision.CONFLICT,
                        overlap, overlap == null ? List.of() : List.of("OVERLAPPING_ACTIVE_TASK")));
            }
        }

        int create = count(rows, RcmAutoPlanDecision.CREATE);
        int duplicates = count(rows, RcmAutoPlanDecision.DUPLICATE);
        int conflicts = count(rows, RcmAutoPlanDecision.CONFLICT);
        int skipped = count(rows, RcmAutoPlanDecision.SKIP);
        String fingerprint = fingerprint(riskThreshold, plan, rows);
        return new RcmAutoPlanPreviewResponse(riskThreshold, plan == null ? null : plan.getId(),
                plan == null ? null : plan.getName(), Instant.now(), rows.size(), create, duplicates,
                conflicts, skipped, fingerprint, rows);
    }

    private RcmAutoPlanPreviewRow row(EquipmentRiskScore score, Equipment equipment, PprPlan plan,
                                      MaintenanceRegulation regulation, LocalDateTime start,
                                      RcmAutoPlanDecision decision, PprTask existing, List<String> codes) {
        long minutes = regulation == null ? 0 : laborMinutes(regulation);
        return new RcmAutoPlanPreviewRow(score.equipmentId(), score.equipmentCode(), score.equipmentName(),
                score.riskScore(), score.reasons(), plan == null ? null : plan.getId(),
                plan == null ? null : plan.getName(), regulation == null ? null : regulation.getId(),
                regulation == null ? null : regulation.getName(), start,
                start.plusMinutes(minutes), start.plusDays(7), priorityFor(score), decision,
                existing == null ? null : existing.getId(), existing == null ? null : existing.getCode(), codes,
                plan == null || equipment == null || regulation == null ? null
                        : sourceKey(plan.getId(), equipment.getId(), regulation.getId()));
    }

    private PprPlan resolvePlan(UUID planId) {
        if (planId != null) {
            return planRepository.findByIdAndIsDeletedFalse(planId)
                    .orElseThrow(() -> RestException.notFound("PprPlan not found: " + planId));
        }
        List<PprPlan> active = planRepository.findAllActiveOnDate(LocalDate.now());
        if (!active.isEmpty()) return active.getFirst();
        List<PprPlan> any = planRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc();
        return any.isEmpty() ? null : any.getFirst();
    }

    public static String sourceKey(UUID planId, UUID equipmentId, UUID regulationId) {
        return "RCM:" + planId + ":" + equipmentId + ":" + regulationId;
    }

    private boolean active(PprTask task) {
        return task.getStatus() != PprTaskStatus.CANCELLED && task.getStatus() != PprTaskStatus.COMPLETED;
    }

    private boolean overlaps(PprTask task, LocalDateTime start, LocalDateTime end) {
        return task.getScheduledStart() != null && task.getScheduledEnd() != null
                && task.getScheduledStart().isBefore(end) && task.getScheduledEnd().isAfter(start);
    }

    private long laborMinutes(MaintenanceRegulation regulation) {
        return Math.max(1L, Math.round(regulation.getNormativeLaborHours() * 60));
    }

    private PriorityLevel priorityFor(EquipmentRiskScore score) {
        if (score.riskScore() >= 60) return PriorityLevel.HIGH;
        if (score.riskScore() >= 30) return PriorityLevel.MEDIUM;
        return PriorityLevel.LOW;
    }

    private int count(List<RcmAutoPlanPreviewRow> rows, RcmAutoPlanDecision decision) {
        return (int) rows.stream().filter(row -> row.decision() == decision).count();
    }

    private String fingerprint(int threshold, PprPlan plan, List<RcmAutoPlanPreviewRow> rows) {
        StringBuilder canonical = new StringBuilder().append(threshold).append('|')
                .append(plan == null ? "" : plan.getId()).append('\n');
        rows.forEach(row -> canonical.append(row.equipmentId()).append('|').append(row.riskScore())
                .append('|').append(row.regulationId()).append('|').append(row.scheduledStart())
                .append('|').append(row.scheduledEnd()).append('|').append(row.priority())
                .append('|').append(row.decision()).append('|').append(row.existingTaskId())
                .append('|').append(String.join(",", row.conflictCodes())).append('\n'));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
