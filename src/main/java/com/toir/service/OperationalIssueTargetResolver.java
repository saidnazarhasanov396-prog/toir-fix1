package com.toir.service;

import com.toir.dto.operationalissue.OperationalIssueTarget;
import com.toir.entity.OperationalIssue;
import com.toir.entity.PprTask;
import com.toir.entity.contractors.ContractorWork;
import com.toir.enums.OperationalIssueTargetType;
import com.toir.repository.PprTaskRepository;
import com.toir.repository.contarctor.ContractorWorkRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OperationalIssueTargetResolver {

    private final PprTaskRepository pprTaskRepository;
    private final ContractorWorkRepository contractorWorkRepository;

    public Map<UUID, OperationalIssueTarget> resolveAll(List<OperationalIssue> issues) {
        List<UUID> pprTaskIds = issues.stream()
                .filter(issue -> "PprTask".equals(issue.getSourceType()))
                .map(OperationalIssue::getSourceId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<UUID, PprTask> pprTasksById = pprTaskIds.isEmpty()
                ? Map.of()
                : pprTaskRepository.findAllByIdInAndIsDeletedFalseWithPlan(pprTaskIds).stream()
                .collect(Collectors.toMap(PprTask::getId, Function.identity(), (left, right) -> left));
        List<UUID> contractorWorkIds = issues.stream()
                .filter(issue -> "ContractorWork".equals(issue.getSourceType()))
                .map(OperationalIssue::getSourceId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<UUID, ContractorWork> contractorWorksById = contractorWorkIds.isEmpty()
                ? Map.of()
                : contractorWorkRepository.findAllByIdInAndIsDeletedFalse(contractorWorkIds).stream()
                .collect(Collectors.toMap(ContractorWork::getId, Function.identity(), (left, right) -> left));

        Map<UUID, OperationalIssueTarget> targets = new LinkedHashMap<>();
        for (OperationalIssue issue : issues) {
            OperationalIssueTarget target = resolve(issue, pprTasksById, contractorWorksById);
            if (issue.getId() != null && target != null) {
                targets.put(issue.getId(), target);
            }
        }
        return targets;
    }

    private OperationalIssueTarget resolve(OperationalIssue issue,
                                           Map<UUID, PprTask> pprTasksById,
                                           Map<UUID, ContractorWork> contractorWorksById) {
        UUID sourceId = issue.getSourceId();
        String sourceType = issue.getSourceType();
        if (sourceId == null || sourceType == null) {
            return null;
        }
        return switch (sourceType) {
            case "RepairRequest" -> target(
                    OperationalIssueTargetType.REPAIR_REQUEST,
                    sourceId,
                    metadataString(issue, "requestNumber")
            );
            case "WorkOrder" -> target(
                    OperationalIssueTargetType.WORK_ORDER,
                    sourceId,
                    metadataString(issue, "workOrderNumber")
            );
            case "PprTask" -> pprTaskTarget(issue, pprTasksById.get(sourceId));
            case "EquipmentLifetime", "EquipmentLifecycle" -> target(
                    OperationalIssueTargetType.EQUIPMENT,
                    sourceId,
                    metadataString(issue, "equipmentCode")
            );
            case "CalibrationRecord" -> issue.getEquipmentId() == null
                    ? null
                    : target(OperationalIssueTargetType.EQUIPMENT, issue.getEquipmentId(), null);
            case "MaintenanceDueEvent" -> target(
                    OperationalIssueTargetType.MAINTENANCE_DUE_EVENT,
                    sourceId,
                    metadataString(issue, "cycleKey")
            );
            case "Defect" -> target(
                    OperationalIssueTargetType.DEFECT,
                    sourceId,
                    metadataString(issue, "defectCode")
            );
            case "ContractorWork" -> contractorWorkTarget(issue, contractorWorksById.get(sourceId));
            case "MaintenanceBudget" -> target(
                    OperationalIssueTargetType.MAINTENANCE_BUDGET,
                    sourceId,
                    metadataString(issue, "year")
            );
            case "LOW_STOCK", "SPARE_PART_FORECAST" -> sparePartTarget(issue);
            case "ApprovalRequest" -> target(
                    OperationalIssueTargetType.APPROVAL_REQUEST,
                    sourceId,
                    metadataString(issue, "approvalTitle")
            );
            default -> null;
        };
    }

    private OperationalIssueTarget contractorWorkTarget(OperationalIssue issue, ContractorWork work) {
        if (work == null || work.getWorkOrderId() == null) {
            return null;
        }
        return target(
                OperationalIssueTargetType.WORK_ORDER,
                work.getWorkOrderId(),
                metadataString(issue, "workOrderNumber")
        );
    }

    private OperationalIssueTarget pprTaskTarget(OperationalIssue issue, PprTask task) {
        UUID planId = task == null || task.getPlan() == null ? null : task.getPlan().getId();
        return new OperationalIssueTarget(
                OperationalIssueTargetType.PPR_TASK,
                issue.getSourceId(),
                metadataString(issue, "pprTaskCode"),
                planId == null ? null : OperationalIssueTargetType.PPR_PLAN,
                planId
        );
    }

    private OperationalIssueTarget sparePartTarget(OperationalIssue issue) {
        UUID sparePartId = metadataUuid(issue, "sparePartId");
        if (sparePartId == null) {
            return null;
        }
        UUID warehouseId = metadataUuid(issue, "warehouseId");
        return new OperationalIssueTarget(
                OperationalIssueTargetType.SPARE_PART,
                sparePartId,
                metadataString(issue, "sparePartCode"),
                warehouseId == null ? null : OperationalIssueTargetType.WAREHOUSE,
                warehouseId
        );
    }

    private OperationalIssueTarget target(OperationalIssueTargetType type, UUID id, String code) {
        return new OperationalIssueTarget(type, id, code, null, null);
    }

    private String metadataString(OperationalIssue issue, String key) {
        Object value = issue.getMetadata() == null ? null : issue.getMetadata().get(key);
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private UUID metadataUuid(OperationalIssue issue, String key) {
        String value = metadataString(issue, key);
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
