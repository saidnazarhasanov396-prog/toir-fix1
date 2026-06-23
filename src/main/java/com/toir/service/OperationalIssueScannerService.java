package com.toir.service;

import com.toir.entity.ApprovalRequest;
import com.toir.entity.PprTask;
import com.toir.entity.contractors.ContractorWork;
import com.toir.entity.defects.Defect;
import com.toir.entity.equipment.CalibrationRecord;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.equipment.EquipmentMeter;
import com.toir.entity.maintenance.MaintenanceDueEvent;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.projects.MaintenanceBudget;
import com.toir.entity.repair.RepairRequest;
import com.toir.enums.ApprovalStatus;
import com.toir.enums.ApprovalTargetType;
import com.toir.enums.BudgetStatus;
import com.toir.enums.ContractorWorkStatus;
import com.toir.enums.DefectStatus;
import com.toir.enums.EquipmentRiskLevel;
import com.toir.enums.EquipmentStatus;
import com.toir.enums.MaintenanceDueStatus;
import com.toir.enums.MeterType;
import com.toir.enums.NotificationSeverity;
import com.toir.enums.OperationalIssueType;
import com.toir.enums.PprTaskStatus;
import com.toir.enums.RequestStatus;
import com.toir.enums.WorkOrderStatus;
import com.toir.repository.ApprovalRequestRepository;
import com.toir.repository.CalibrationRecordRepository;
import com.toir.repository.PprTaskRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.contarctor.ContractorWorkRepository;
import com.toir.repository.defects.DefectRepository;
import com.toir.repository.equipment.EquipmentMeterRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.maintenance.MaintenanceBudgetRepository;
import com.toir.repository.maintenance.MaintenanceDueEventRepository;
import com.toir.repository.repair.RepairRequestRepository;
import com.toir.dto.rcm.EquipmentRiskScore;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class    OperationalIssueScannerService {

    private static final int LIFETIME_WARNING_MONTHS = 3;
    private static final double LIFETIME_WARNING_HOURS_RATIO = 0.1;
    private static final int APPROVAL_ESCALATION_DAYS = 3;
    private static final int LIFECYCLE_RISK_CRITICAL_THRESHOLD = 60;
    private static final int LIFECYCLE_RISK_WARNING_THRESHOLD = 30;

    private final OperationalIssueService issueService;
    private final WorkOrderRepository workOrderRepository;
    private final PprTaskRepository pprTaskRepository;
    private final RepairRequestRepository repairRequestRepository;
    private final CalibrationRecordRepository calibrationRecordRepository;
    private final EquipmentRepository equipmentRepository;
    private final EquipmentMeterRepository equipmentMeterRepository;
    private final MaintenanceDueEventRepository maintenanceDueEventRepository;
    private final ContractorWorkRepository contractorWorkRepository;
    private final MaintenanceBudgetRepository maintenanceBudgetRepository;
    private final ApprovalRequestRepository approvalRequestRepository;
    private final DefectRepository defectRepository;
    private final LowStockRecommendationService lowStockRecommendationService;
    private final RcmService rcmService;

    @Transactional
    public ScanResult scanAll() {
        int openedOrUpdated = 0;
        int resolved = 0;
        openedOrUpdated += scanWorkOrders();
        openedOrUpdated += scanPprTasks();
        openedOrUpdated += scanRepairRequests();
        openedOrUpdated += scanCalibrations();
        openedOrUpdated += scanEquipmentLifetime();
        openedOrUpdated += scanEquipmentLifecycle();
        openedOrUpdated += scanMaintenanceDueEvents();
        openedOrUpdated += scanContractorWorkDelays();
        openedOrUpdated += scanBudgetIssues();
        openedOrUpdated += scanApprovalEscalations();
        openedOrUpdated += scanInspectionDefects();
        var lowStock = lowStockRecommendationService.evaluateAll();
        openedOrUpdated += lowStock.openedCount() + lowStock.updatedCount();
        resolved += lowStock.resolvedCount();
        return new ScanResult(openedOrUpdated, resolved);
    }

    private int scanWorkOrders() {
        int count = 0;
        Instant now = Instant.now();
        for (WorkOrder item : workOrderRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()) {
            if (isClosed(item.getStatus())) {
                issueService.resolveOpen("WorkOrder", item.getId());
                continue;
            }
            if (item.getEndPlannedAt() != null && item.getEndPlannedAt().isBefore(now)) {
                count += open(
                        OperationalIssueType.OVERDUE_WORK_ORDER,
                        NotificationSeverity.WARNING,
                        item.getEquipmentId(),
                        item.getDepartmentId(),
                        "WorkOrder",
                        item.getId(),
                        "Overdue work order " + item.getNumber(),
                        "Planned completion " + item.getEndPlannedAt() + " has passed.",
                        metadata(
                                "workOrderNumber", item.getNumber(),
                                "plannedCompletionAt", item.getEndPlannedAt()
                        )
                );
            }
        }
        return count;
    }

    private int scanPprTasks() {
        int count = 0;
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        for (PprTask item : pprTaskRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()) {
            if (item.getStatus() == PprTaskStatus.COMPLETED || item.getStatus() == PprTaskStatus.CANCELLED) {
                issueService.resolveOpen("PprTask", item.getId());
                continue;
            }
            if (item.getStatus() == PprTaskStatus.OVERDUE
                    || (item.getDueDate() != null && item.getDueDate().isBefore(now))) {
                count += open(
                        OperationalIssueType.OVERDUE_PPR_TASK,
                        NotificationSeverity.WARNING,
                        item.getEquipmentId(),
                        equipmentDepartment(item.getEquipmentId()),
                        "PprTask",
                        item.getId(),
                        "Overdue PPR task " + item.getCode(),
                        "PPR task due date " + item.getDueDate() + " has passed.",
                        metadata(
                                "pprTaskCode", item.getCode(),
                                "dueDate", item.getDueDate()
                        )
                );
            }
        }
        return count;
    }

    private int scanRepairRequests() {
        int count = 0;
        Instant now = Instant.now();
        for (RepairRequest item : repairRequestRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()) {
            if (item.getStatus() == RequestStatus.CLOSED || item.getStatus() == RequestStatus.CANCELLED) {
                issueService.resolveOpen("RepairRequest", item.getId());
                continue;
            }
            if (item.getTargetCompletionAt() != null && item.getTargetCompletionAt().isBefore(now)) {
                count += open(
                        OperationalIssueType.OVERDUE_REPAIR_REQUEST,
                        NotificationSeverity.CRITICAL,
                        item.getEquipmentId(),
                        item.getDepartmentId(),
                        "RepairRequest",
                        item.getId(),
                        "Overdue repair request " + item.getNumber(),
                        "Target completion " + item.getTargetCompletionAt() + " has passed.",
                        metadata(
                                "requestNumber", item.getNumber(),
                                "targetCompletionAt", item.getTargetCompletionAt()
                        )
                );
            }
        }
        return count;
    }

    private int scanCalibrations() {
        int count = 0;
        LocalDate today = LocalDate.now();
        for (CalibrationRecord item : calibrationRecordRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()) {
            if (item.getNextDueAt() == null || !item.getNextDueAt().isBefore(today)) {
                issueService.resolveOpen("CalibrationRecord", item.getId());
                continue;
            }
            UUID departmentId = equipmentDepartment(item.getEquipmentId());
            count += open(
                    OperationalIssueType.OVERDUE_CALIBRATION,
                    NotificationSeverity.WARNING,
                    item.getEquipmentId(),
                    departmentId,
                    "CalibrationRecord",
                    item.getId(),
                    "Overdue calibration " + nullToDash(item.getCertificateNumber()),
                    "Next calibration due date " + item.getNextDueAt() + " has passed.",
                    metadata(
                            "certificateNumber", nullToDash(item.getCertificateNumber()),
                            "nextDueAt", item.getNextDueAt()
                    )
            );
        }
        return count;
    }

    private int scanEquipmentLifetime() {
        int count = 0;
        LocalDate today = LocalDate.now();
        for (Equipment item : equipmentRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()) {
            if (item.getStatus() == EquipmentStatus.DECOMMISSIONED) {
                issueService.resolveOpen("EquipmentLifetime", item.getId());
                continue;
            }
            Optional<MeterLifetimeSnapshot> meterLifetime = meterLifetime(item);
            if (meterLifetime.isPresent()) {
                MeterLifetimeSnapshot snapshot = meterLifetime.get();
                if (snapshot.remainingValue() <= 0) {
                    count += open(
                            OperationalIssueType.EQUIPMENT_LIFETIME_EXPIRED,
                            NotificationSeverity.CRITICAL,
                            item.getId(),
                            effectiveDepartment(item),
                            "EquipmentLifetime",
                            item.getId(),
                            "Equipment lifetime expired: " + item.getCode(),
                            expiredLifetimeDetails(item, snapshot),
                            lifetimeMetadata(item, snapshot)
                    );
                } else if (snapshot.remainingValue() <= snapshot.warningThreshold()) {
                    count += open(
                            OperationalIssueType.EQUIPMENT_LIFETIME_WARNING,
                            NotificationSeverity.WARNING,
                            item.getId(),
                            effectiveDepartment(item),
                            "EquipmentLifetime",
                            item.getId(),
                            "Equipment lifetime expiring soon: " + item.getCode(),
                            warningLifetimeDetails(snapshot),
                            lifetimeMetadata(item, snapshot)
                    );
                } else {
                    issueService.resolveOpen("EquipmentLifetime", item.getId());
                }
                continue;
            }
            LocalDate expectedEnd = expectedEndDate(item);
            if (expectedEnd == null) {
                continue;
            }
            if (expectedEnd.isBefore(today)) {
                count += open(
                        OperationalIssueType.EQUIPMENT_LIFETIME_EXPIRED,
                        NotificationSeverity.CRITICAL,
                        item.getId(),
                        effectiveDepartment(item),
                        "EquipmentLifetime",
                        item.getId(),
                        "Equipment lifetime expired: " + item.getCode(),
                        "Expected lifetime ended on " + expectedEnd + ".",
                        calendarLifetimeMetadata(item, expectedEnd)
                );
            } else if (!expectedEnd.isAfter(today.plusMonths(LIFETIME_WARNING_MONTHS))) {
                count += open(
                        OperationalIssueType.EQUIPMENT_LIFETIME_WARNING,
                        NotificationSeverity.WARNING,
                        item.getId(),
                        effectiveDepartment(item),
                        "EquipmentLifetime",
                        item.getId(),
                        "Equipment lifetime expiring soon: " + item.getCode(),
                        "Expected lifetime ends on " + expectedEnd + ".",
                        calendarLifetimeMetadata(item, expectedEnd)
                );
            } else {
                issueService.resolveOpen("EquipmentLifetime", item.getId());
            }
        }
        return count;
    }

    private int scanEquipmentLifecycle() {
        int count = 0;
        Map<UUID, EquipmentRiskScore> riskScoreByEquipment = rcmService.computeAll().stream()
                .collect(Collectors.toMap(EquipmentRiskScore::equipmentId, Function.identity(), (left, right) -> left));
        for (Equipment item : equipmentRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()) {
            EquipmentRiskScore riskScore = riskScoreByEquipment.get(item.getId());
            int risk = riskScore == null ? 0 : riskScore.riskScore();
            NotificationSeverity severity = lifecycleSeverity(item.getStatus(), risk);
            EquipmentRiskLevel riskLevel = toEquipmentRiskLevel(item.getStatus(), risk);
            if (severity == NotificationSeverity.INFO) {
                issueService.resolveOpen("EquipmentLifecycle", item.getId());
            } else {
                issueService.openOrUpdate(
                        OperationalIssueType.EQUIPMENT_LIFECYCLE,
                        severity,
                        riskLevel,
                        item.getId(),
                        effectiveDepartment(item),
                        "EquipmentLifecycle",
                        item.getId(),
                        "Equipment lifecycle risk: " + item.getCode(),
                        lifecycleMessage(item, risk, riskScore),
                        lifecycleMetadata(item, risk, riskScore)
                );
                count++;
            }
        }
        return count;
    }

    private NotificationSeverity lifecycleSeverity(EquipmentStatus status, int risk) {
        if (status == EquipmentStatus.DECOMMISSIONED) {
            return NotificationSeverity.CRITICAL;
        }
        if (risk >= LIFECYCLE_RISK_CRITICAL_THRESHOLD) {
            return NotificationSeverity.CRITICAL;
        }
        if (risk >= LIFECYCLE_RISK_WARNING_THRESHOLD) {
            return NotificationSeverity.WARNING;
        }
        // Equipment under repair with low risk is not critical by itself — surface it as a
        // warning so it stays visible without inflating the critical count.
        if (status == EquipmentStatus.IN_REPAIR) {
            return NotificationSeverity.WARNING;
        }
        return NotificationSeverity.INFO;
    }

    private EquipmentRiskLevel toEquipmentRiskLevel(EquipmentStatus status, int risk) {
        if (status == EquipmentStatus.DECOMMISSIONED) {
            return EquipmentRiskLevel.CRITICAL;
        }
        if (risk >= LIFECYCLE_RISK_CRITICAL_THRESHOLD) return EquipmentRiskLevel.CRITICAL;
        if (risk >= LIFECYCLE_RISK_WARNING_THRESHOLD) return EquipmentRiskLevel.HIGH;
        if (status == EquipmentStatus.IN_REPAIR) return EquipmentRiskLevel.MEDIUM;
        if (risk >= 15) return EquipmentRiskLevel.MEDIUM;
        return EquipmentRiskLevel.LOW;
    }

    private String lifecycleMessage(Equipment equipment, int risk, EquipmentRiskScore riskScore) {
        StringBuilder message = new StringBuilder();
        message.append("Equipment ").append(equipment.getCode());
        if (equipment.getName() != null && !equipment.getName().isBlank()) {
            message.append(" (").append(equipment.getName()).append(")");
        }
        message.append(" has status ").append(equipment.getStatus())
                .append(" and RCM risk score ").append(risk).append("/100");
        if (riskScore != null) {
            message.append(" (consequence ").append(riskScore.consequence())
                    .append(" x probability ").append(riskScore.probability())
                    .append(", open defects: ").append(riskScore.openDefects())
                    .append(")");
        }
        message.append(".");
        if (equipment.getStatus() == EquipmentStatus.IN_REPAIR
                || equipment.getStatus() == EquipmentStatus.DECOMMISSIONED) {
            message.append(" Equipment status ").append(equipment.getStatus())
                    .append(" requires critical lifecycle attention.");
        }
        return message.toString();
    }

    private Map<String, Object> lifecycleMetadata(Equipment equipment, int risk, EquipmentRiskScore riskScore) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("equipmentCode", equipment.getCode());
        metadata.put("equipmentName", equipment.getName());
        metadata.put("status", equipment.getStatus() == null ? null : equipment.getStatus().name());
        metadata.put("riskScore", risk);
        if (riskScore != null) {
            metadata.put("criticalityClass", riskScore.criticalityClass());
            metadata.put("consequence", riskScore.consequence());
            metadata.put("probability", riskScore.probability());
            metadata.put("repairPriority", riskScore.repairPriority());
            metadata.put("openDefects", riskScore.openDefects());
            metadata.put("mtbfHours", riskScore.mtbfHours());
            metadata.put("mttrHours", riskScore.mttrHours());
        }
        return metadata;
    }

    private int scanMaintenanceDueEvents() {
        int count = 0;
        for (MaintenanceDueEvent item : maintenanceDueEventRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()) {
            if (item.getResolvedAt() != null) {
                issueService.resolveOpen("MaintenanceDueEvent", item.getId());
                continue;
            }
            if (item.getDueStatus() != MaintenanceDueStatus.OVERDUE
                    && item.getDueStatus() != MaintenanceDueStatus.BLOCKED) {
                issueService.resolveOpen("MaintenanceDueEvent", item.getId());
                continue;
            }
            Equipment equipment = equipment(item.getEquipmentId()).orElse(null);
            OperationalIssueType type = switch (item.getDueStatus()) {
                case OVERDUE -> OperationalIssueType.MAINTENANCE_OVERDUE;
                case BLOCKED -> OperationalIssueType.MISSING_METERS;
                default -> throw new IllegalStateException("Unsupported maintenance issue status: " + item.getDueStatus());
            };
            NotificationSeverity severity = switch (item.getDueStatus()) {
                case OVERDUE, BLOCKED -> NotificationSeverity.CRITICAL;
                default -> throw new IllegalStateException("Unsupported maintenance issue status: " + item.getDueStatus());
            };
            count += open(
                    type,
                    severity,
                    item.getEquipmentId(),
                    equipment == null ? null : effectiveDepartment(equipment),
                    "MaintenanceDueEvent",
                    item.getId(),
                    "Maintenance due: " + item.getCycleKey(),
                    item.getExplanation(),
                    metadata(
                            "cycleKey", item.getCycleKey(),
                            "explanation", item.getExplanation(),
                            "dueStatus", item.getDueStatus() == null ? null : item.getDueStatus().name()
                    )
            );
        }
        return count;
    }

    private int scanContractorWorkDelays() {
        int count = 0;
        Instant now = Instant.now();
        for (ContractorWork item : contractorWorkRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()) {
            if (item.getStatus() == ContractorWorkStatus.COMPLETED
                    || item.getStatus() == ContractorWorkStatus.ACCEPTED
                    || item.getStatus() == ContractorWorkStatus.CANCELLED) {
                issueService.resolveOpen("ContractorWork", item.getId());
                continue;
            }
            WorkOrder workOrder = item.getWorkOrderId() == null
                    ? null
                    : workOrderRepository.findByIdAndIsDeletedFalse(item.getWorkOrderId()).orElse(null);
            if (workOrder != null && workOrder.getEndPlannedAt() != null && workOrder.getEndPlannedAt().isBefore(now)) {
                count += open(
                        OperationalIssueType.CONTRACTOR_WORK_DELAY,
                        NotificationSeverity.WARNING,
                        workOrder.getEquipmentId(),
                        workOrder.getDepartmentId(),
                        "ContractorWork",
                        item.getId(),
                        "Contractor work delay",
                        "Linked work order " + workOrder.getNumber() + " planned completion has passed.",
                        metadata(
                                "workOrderNumber", workOrder.getNumber(),
                                "plannedCompletionAt", workOrder.getEndPlannedAt()
                        )
                );
            }
        }
        return count;
    }

    private int scanBudgetIssues() {
        int count = 0;
        for (MaintenanceBudget item : maintenanceBudgetRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()) {
            if (item.getStatus() == BudgetStatus.CLOSED) {
                issueService.resolveOpen("MaintenanceBudget", item.getId());
                continue;
            }
            if (item.getTotalPlanned() > 0 && item.getTotalActual() > item.getTotalPlanned()) {
                count += open(
                        OperationalIssueType.BUDGET_REVIEW_ISSUE,
                        NotificationSeverity.WARNING,
                        null,
                        item.getDepartmentId(),
                        "MaintenanceBudget",
                        item.getId(),
                        "Budget review issue " + item.getYear(),
                        "Actual maintenance cost exceeds planned budget.",
                        metadata(
                                "year", item.getYear(),
                                "totalPlanned", item.getTotalPlanned(),
                                "totalActual", item.getTotalActual()
                        )
                );
            }
        }
        return count;
    }

    private int scanApprovalEscalations() {
        int count = 0;
        Instant threshold = Instant.now().minus(java.time.Duration.ofDays(APPROVAL_ESCALATION_DAYS));
        for (ApprovalRequest item : approvalRequestRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()) {
            if (item.getStatus() != ApprovalStatus.PENDING) {
                issueService.resolveOpen("ApprovalRequest", item.getId());
                continue;
            }
            if (item.getCreatedAt() != null && item.getCreatedAt().isAfter(threshold)) {
                continue;
            }
            SourceScope scope = approvalScope(item);
            count += open(
                    OperationalIssueType.APPROVAL_ESCALATION,
                    NotificationSeverity.WARNING,
                    scope.equipmentId(),
                    scope.departmentId(),
                    "ApprovalRequest",
                    item.getId(),
                    "Approval escalation: " + item.getTitle(),
                    "Approval request has been pending for more than " + APPROVAL_ESCALATION_DAYS + " days.",
                    metadata(
                            "approvalTitle", item.getTitle(),
                            "ageDays", APPROVAL_ESCALATION_DAYS
                    )
            );
        }
        return count;
    }

    private int scanInspectionDefects() {
        int count = 0;
        for (Defect item : defectRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()) {
            if (item.getStatus() == DefectStatus.RESOLVED
                    || item.getStatus() == DefectStatus.CLOSED
                    || item.getStatus() == DefectStatus.CANCELLED) {
                issueService.resolveOpen("Defect", item.getId());
                continue;
            }
            count += open(
                    OperationalIssueType.INSPECTION_DEFECT,
                    NotificationSeverity.WARNING,
                    item.getEquipmentId(),
                    equipmentDepartment(item.getEquipmentId()),
                    "Defect",
                    item.getId(),
                    "Inspection defect " + item.getCode(),
                    item.getTitle(),
                    metadata(
                            "defectCode", item.getCode(),
                            "defectTitle", item.getTitle()
                    )
            );
        }
        return count;
    }

    private int open(OperationalIssueType type,
                     NotificationSeverity severity,
                     UUID equipmentId,
                     UUID departmentId,
                     String sourceType,
                     UUID sourceId,
                     String title,
                     String message) {
        return open(type, severity, equipmentId, departmentId, sourceType, sourceId, title, message, Map.of());
    }

    private int open(OperationalIssueType type,
                     NotificationSeverity severity,
                     UUID equipmentId,
                     UUID departmentId,
                     String sourceType,
                     UUID sourceId,
                     String title,
                     String message,
                     Map<String, Object> metadata) {
        if (sourceId == null) {
            return 0;
        }
        issueService.openOrUpdate(type, severity, equipmentId, departmentId, sourceType, sourceId, title, message, metadata);
        return 1;
    }

    private Map<String, Object> lifetimeMetadata(Equipment equipment, MeterLifetimeSnapshot snapshot) {
        return metadata(
                "equipmentCode", equipment.getCode(),
                "equipmentName", equipment.getName(),
                "counterType", snapshot.counterType() == null ? null : snapshot.counterType().name(),
                "unit", snapshot.unit(),
                "limitValue", formatLifetimeValue(snapshot.limitValue()),
                "currentValue", formatLifetimeValue(snapshot.currentValue()),
                "remainingValue", formatLifetimeValue(snapshot.remainingValue()),
                "warningThreshold", formatLifetimeValue(snapshot.warningThreshold()),
                "expectedLifetimeHours", equipment.getExpectedLifetimeHours()
        );
    }

    private Map<String, Object> calendarLifetimeMetadata(Equipment equipment, LocalDate expectedEnd) {
        return metadata(
                "equipmentCode", equipment.getCode(),
                "equipmentName", equipment.getName(),
                "expectedEndDate", expectedEnd,
                "expectedLifetimeMonths", effectiveLifetimeMonths(equipment)
        );
    }

    private Map<String, Object> metadata(Object... pairs) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            Object key = pairs[i];
            Object value = pairs[i + 1];
            if (key == null || value == null) {
                continue;
            }
            metadata.put(String.valueOf(key), metadataValue(value));
        }
        return metadata;
    }

    private Object metadataValue(Object value) {
        if (value instanceof Instant instant) {
            return instant.toString();
        }
        if (value instanceof LocalDate date) {
            return date.toString();
        }
        if (value instanceof java.time.LocalDateTime dateTime) {
            return dateTime.toString();
        }
        if (value instanceof Enum<?> enumValue) {
            return enumValue.name();
        }
        return value;
    }

    private boolean isClosed(WorkOrderStatus status) {
        return status == WorkOrderStatus.COMPLETED
                || status == WorkOrderStatus.CLOSED
                || status == WorkOrderStatus.CANCELLED;
    }

    private UUID equipmentDepartment(UUID equipmentId) {
        return equipment(equipmentId).map(this::effectiveDepartment).orElse(null);
    }

    private Optional<Equipment> equipment(UUID equipmentId) {
        return equipmentId == null ? Optional.empty() : equipmentRepository.findByIdAndIsDeletedFalse(equipmentId);
    }

    private UUID effectiveDepartment(Equipment equipment) {
        return equipment.getResponsibleDepartmentId() != null
                ? equipment.getResponsibleDepartmentId()
                : equipment.getDepartmentId();
    }

    private LocalDate expectedEndDate(Equipment equipment) {
        LocalDate start = equipment.getOperationStartDate() != null
                ? equipment.getOperationStartDate()
                : equipment.getCommissionedAt();
        Integer months = effectiveLifetimeMonths(equipment);
        return start == null || months == null || months <= 0 ? null : start.plusMonths(months);
    }

    private Integer effectiveLifetimeMonths(Equipment equipment) {
        if (equipment.getExpectedLifetimeMonths() != null && equipment.getExpectedLifetimeMonths() > 0) {
            return equipment.getExpectedLifetimeMonths();
        }
        if (equipment.getExpectedLifetimeYears() != null && equipment.getExpectedLifetimeYears() > 0) {
            return equipment.getExpectedLifetimeYears() * 12;
        }
        return null;
    }

    private Optional<MeterLifetimeSnapshot> meterLifetime(Equipment equipment) {
        MeterType counterType = effectiveLifetimeCounterType(equipment);
        Double limitValue = effectiveLifetimeLimitValue(equipment);
        if (counterType == null || limitValue == null || limitValue <= 0) {
            return Optional.empty();
        }
        return lifetimeMeter(equipment, counterType)
                .map(meter -> {
                    double baselineValue = equipment.getLifetimeBaselineValue() != null
                            ? equipment.getLifetimeBaselineValue()
                            : 0.0;
                    double targetValue = baselineValue + limitValue;
                    double remainingValue = targetValue - meter.getCurrentValue();
                    double warningPercent = equipment.getLifetimeWarningPercent() != null
                            && equipment.getLifetimeWarningPercent() > 0
                            ? equipment.getLifetimeWarningPercent()
                            : 10.0;
                    double warningThreshold = Math.max(1.0, limitValue * warningPercent / 100.0);
                    boolean legacyHours = counterType == MeterType.ENGINE_HOURS
                            && equipment.getExpectedLifetimeHours() != null
                            && equipment.getExpectedLifetimeHours() > 0
                            && (equipment.getLifetimeLimitValue() == null
                            || equipment.getLifetimeLimitValue().longValue() == equipment.getExpectedLifetimeHours());
                    return new MeterLifetimeSnapshot(
                            counterType,
                            lifetimeUnit(counterType, meter),
                            limitValue,
                            meter.getCurrentValue(),
                            remainingValue,
                            warningThreshold,
                            legacyHours
                    );
                });
    }

    private Optional<EquipmentMeter> lifetimeMeter(Equipment equipment, MeterType counterType) {
        if (equipment.getId() == null) {
            return Optional.empty();
        }
        return equipmentMeterRepository.findAllByEquipmentIdAndActiveTrueAndIsDeletedFalse(equipment.getId())
                .stream()
                .filter(meter -> equipment.getLifetimeMeterId() == null
                        ? meter.getMeterType() == counterType
                        : equipment.getLifetimeMeterId().equals(meter.getId()))
                .max(java.util.Comparator.comparingDouble(EquipmentMeter::getCurrentValue));
    }

    private MeterType effectiveLifetimeCounterType(Equipment equipment) {
        if (equipment.getLifetimeCounterType() != null) {
            return equipment.getLifetimeCounterType();
        }
        return equipment.getExpectedLifetimeHours() != null && equipment.getExpectedLifetimeHours() > 0
                ? MeterType.ENGINE_HOURS
                : null;
    }

    private Double effectiveLifetimeLimitValue(Equipment equipment) {
        if (equipment.getLifetimeLimitValue() != null && equipment.getLifetimeLimitValue() > 0) {
            return equipment.getLifetimeLimitValue();
        }
        return equipment.getExpectedLifetimeHours() != null && equipment.getExpectedLifetimeHours() > 0
                ? equipment.getExpectedLifetimeHours().doubleValue()
                : null;
    }

    private String expiredLifetimeDetails(Equipment equipment, MeterLifetimeSnapshot snapshot) {
        if (snapshot.legacyHours()) {
            return "Expected lifetime of " + equipment.getExpectedLifetimeHours()
                    + " operating hours has been reached. Remaining lifetime hours: "
                    + formatLifetimeValue(snapshot.remainingValue()) + ".";
        }
        return "Expected lifetime of " + formatLifetimeValue(snapshot.limitValue()) + " "
                + snapshot.unit() + " has been reached. Current value: "
                + formatLifetimeValue(snapshot.currentValue()) + " " + snapshot.unit()
                + ". Remaining lifetime: " + formatLifetimeValue(snapshot.remainingValue())
                + " " + snapshot.unit() + ".";
    }

    private String warningLifetimeDetails(MeterLifetimeSnapshot snapshot) {
        if (snapshot.legacyHours()) {
            return "Remaining lifetime hours: " + formatLifetimeValue(snapshot.remainingValue()) + ".";
        }
        return "Remaining lifetime: " + formatLifetimeValue(snapshot.remainingValue()) + " "
                + snapshot.unit() + ".";
    }

    private String lifetimeUnit(MeterType counterType, EquipmentMeter meter) {
        if (meter.getUnit() != null && !meter.getUnit().isBlank()) {
            return meter.getUnit();
        }
        return switch (counterType) {
            case ENGINE_HOURS -> "h";
            case MILEAGE_KM -> "km";
            case CYCLES -> "cycle";
            case TONS_PRODUCED -> "t";
            case KWH_CONSUMED -> "kWh";
            case CUSTOM -> "unit";
        };
    }

    private String formatLifetimeValue(double value) {
        return Math.rint(value) == value ? "%.0f".formatted(value) : "%.2f".formatted(value);
    }

    private record MeterLifetimeSnapshot(
            MeterType counterType,
            String unit,
            double limitValue,
            double currentValue,
            double remainingValue,
            double warningThreshold,
            boolean legacyHours
    ) {}

    private SourceScope approvalScope(ApprovalRequest request) {
        ApprovalTargetType targetType = request.getTargetType() == null
                ? ApprovalTargetType.fromDocumentType(request.getDocumentType())
                : request.getTargetType();
        UUID targetId = request.getTargetId() == null ? request.getDocumentId() : request.getTargetId();
        if (targetType == ApprovalTargetType.WORK_ORDER) {
            return workOrderRepository.findByIdAndIsDeletedFalse(targetId)
                    .map(workOrder -> new SourceScope(workOrder.getEquipmentId(), workOrder.getDepartmentId()))
                    .orElse(SourceScope.empty());
        }
        if (targetType == ApprovalTargetType.REPAIR_REQUEST) {
            return repairRequestRepository.findByIdAndIsDeletedFalse(targetId)
                    .map(repair -> new SourceScope(repair.getEquipmentId(), repair.getDepartmentId()))
                    .orElse(SourceScope.empty());
        }
        if (targetType == ApprovalTargetType.MAINTENANCE_BUDGET || targetType == ApprovalTargetType.BUDGET) {
            return maintenanceBudgetRepository.findByIdAndIsDeletedFalse(targetId)
                    .map(budget -> new SourceScope(null, budget.getDepartmentId()))
                    .orElse(SourceScope.empty());
        }
        return SourceScope.empty();
    }

    private String nullToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private record SourceScope(UUID equipmentId, UUID departmentId) {
        static SourceScope empty() {
            return new SourceScope(null, null);
        }
    }

    public record ScanResult(int openedOrUpdated, int resolved) {
    }
}
