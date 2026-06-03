package com.toir.service;

import com.toir.entity.ApprovalRequest;
import com.toir.entity.PprTask;
import com.toir.entity.contractors.ContractorWork;
import com.toir.entity.defects.Defect;
import com.toir.entity.equipment.CalibrationRecord;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.maintenance.MaintenanceDueEvent;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.projects.MaintenanceBudget;
import com.toir.entity.repair.RepairRequest;
import com.toir.enums.ApprovalStatus;
import com.toir.enums.BudgetStatus;
import com.toir.enums.ContractorWorkStatus;
import com.toir.enums.DefectStatus;
import com.toir.enums.EquipmentStatus;
import com.toir.enums.MaintenanceDueStatus;
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
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.maintenance.MaintenanceBudgetRepository;
import com.toir.repository.maintenance.MaintenanceDueEventRepository;
import com.toir.repository.repair.RepairRequestRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OperationalIssueScannerService {

    private static final int LIFETIME_WARNING_MONTHS = 3;
    private static final int APPROVAL_ESCALATION_DAYS = 3;

    private final OperationalIssueService issueService;
    private final WorkOrderRepository workOrderRepository;
    private final PprTaskRepository pprTaskRepository;
    private final RepairRequestRepository repairRequestRepository;
    private final CalibrationRecordRepository calibrationRecordRepository;
    private final EquipmentRepository equipmentRepository;
    private final MaintenanceDueEventRepository maintenanceDueEventRepository;
    private final ContractorWorkRepository contractorWorkRepository;
    private final MaintenanceBudgetRepository maintenanceBudgetRepository;
    private final ApprovalRequestRepository approvalRequestRepository;
    private final DefectRepository defectRepository;

    @Transactional
    public ScanResult scanAll() {
        int openedOrUpdated = 0;
        int resolved = 0;
        openedOrUpdated += scanWorkOrders();
        openedOrUpdated += scanPprTasks();
        openedOrUpdated += scanRepairRequests();
        openedOrUpdated += scanCalibrations();
        openedOrUpdated += scanEquipmentLifetime();
        openedOrUpdated += scanMaintenanceDueEvents();
        openedOrUpdated += scanContractorWorkDelays();
        openedOrUpdated += scanBudgetIssues();
        openedOrUpdated += scanApprovalEscalations();
        openedOrUpdated += scanInspectionDefects();
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
                        "Planned completion " + item.getEndPlannedAt() + " has passed."
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
                        "PPR task due date " + item.getDueDate() + " has passed."
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
                        "Target completion " + item.getTargetCompletionAt() + " has passed."
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
                    "Next calibration due date " + item.getNextDueAt() + " has passed."
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
                        "Expected lifetime ended on " + expectedEnd + "."
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
                        "Expected lifetime ends on " + expectedEnd + "."
                );
            } else {
                issueService.resolveOpen("EquipmentLifetime", item.getId());
            }
        }
        return count;
    }

    private int scanMaintenanceDueEvents() {
        int count = 0;
        for (MaintenanceDueEvent item : maintenanceDueEventRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()) {
            if (item.getResolvedAt() != null) {
                issueService.resolveOpen("MaintenanceDueEvent", item.getId());
                continue;
            }
            Equipment equipment = equipment(item.getEquipmentId()).orElse(null);
            OperationalIssueType type = switch (item.getDueStatus()) {
                case OVERDUE -> OperationalIssueType.MAINTENANCE_OVERDUE;
                case BLOCKED -> OperationalIssueType.MISSING_METERS;
                default -> OperationalIssueType.MAINTENANCE_DUE;
            };
            NotificationSeverity severity = switch (item.getDueStatus()) {
                case OVERDUE, BLOCKED -> NotificationSeverity.CRITICAL;
                case DUE -> NotificationSeverity.WARNING;
                default -> NotificationSeverity.INFO;
            };
            count += open(
                    type,
                    severity,
                    item.getEquipmentId(),
                    equipment == null ? null : effectiveDepartment(equipment),
                    "MaintenanceDueEvent",
                    item.getId(),
                    "Maintenance due: " + item.getCycleKey(),
                    item.getExplanation()
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
                        "Linked work order " + workOrder.getNumber() + " planned completion has passed."
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
                        "Actual maintenance cost exceeds planned budget."
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
                    "Approval request has been pending for more than " + APPROVAL_ESCALATION_DAYS + " days."
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
                    item.getTitle()
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
        if (sourceId == null) {
            return 0;
        }
        issueService.openOrUpdate(type, severity, equipmentId, departmentId, sourceType, sourceId, title, message);
        return 1;
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

    private SourceScope approvalScope(ApprovalRequest request) {
        if ("WorkOrder".equals(request.getDocumentType())) {
            return workOrderRepository.findByIdAndIsDeletedFalse(request.getDocumentId())
                    .map(workOrder -> new SourceScope(workOrder.getEquipmentId(), workOrder.getDepartmentId()))
                    .orElse(SourceScope.empty());
        }
        if ("RepairRequest".equals(request.getDocumentType())) {
            return repairRequestRepository.findByIdAndIsDeletedFalse(request.getDocumentId())
                    .map(repair -> new SourceScope(repair.getEquipmentId(), repair.getDepartmentId()))
                    .orElse(SourceScope.empty());
        }
        if ("MaintenanceBudget".equals(request.getDocumentType())) {
            return maintenanceBudgetRepository.findByIdAndIsDeletedFalse(request.getDocumentId())
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
