package com.toir.service;

import com.toir.dto.rcm.EquipmentRiskScore;
import com.toir.entity.DowntimeEvent;
import com.toir.entity.defects.Defect;
import com.toir.entity.equipment.CalibrationRecord;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.projects.ActualCost;
import com.toir.entity.repair.RepairRequest;
import com.toir.entity.users.UserCertification;
import com.toir.repository.CalibrationRecordRepository;
import com.toir.repository.DowntimeEventRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.actualCost.ActualCostRepository;
import com.toir.repository.defects.DefectRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.repair.RepairRequestRepository;
import com.toir.repository.users.UserCertificationRepository;
import com.toir.repository.users.UserRepository;
import com.toir.security.ScopeAccessService;
import com.toir.util.CsvWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportsService {

    private final EquipmentRepository equipmentRepository;
    private final RepairRequestRepository repairRequestRepository;
    private final DefectRepository defectRepository;
    private final WorkOrderRepository workOrderRepository;
    private final DowntimeEventRepository downtimeEventRepository;
    private final ActualCostRepository actualCostRepository;
    private final CalibrationRecordRepository calibrationRecordRepository;
    private final UserCertificationRepository userCertificationRepository;
    private final UserRepository userRepository;
    private final RcmService rcmService;
    private final ScopeAccessService scopeAccessService;

    @Transactional(readOnly = true)
    public CsvFile rcmRiskCsv() {
        UUID departmentId = reportsDepartmentScope();
        Map<UUID, Equipment> equipmentById = equipmentById();
        List<EquipmentRiskScore> items = rcmService.computeAll().stream()
                .filter(score -> departmentId == null || isEquipmentInDepartment(equipmentById, score.equipmentId(), departmentId))
                .toList();
        String csv = CsvWriter.build(
                List.of("equipmentId", "equipmentCode", "equipmentName", "criticalityClass",
                        "consequence", "probability", "riskScore", "repairPriority",
                        "openDefects", "mtbfHours", "mttrHours"),
                items,
                List.of(
                        EquipmentRiskScore::equipmentId,
                        EquipmentRiskScore::equipmentCode,
                        EquipmentRiskScore::equipmentName,
                        EquipmentRiskScore::criticalityClass,
                        EquipmentRiskScore::consequence,
                        EquipmentRiskScore::probability,
                        EquipmentRiskScore::riskScore,
                        EquipmentRiskScore::repairPriority,
                        EquipmentRiskScore::openDefects,
                        EquipmentRiskScore::mtbfHours,
                        EquipmentRiskScore::mttrHours
                ));
        return new CsvFile("rcm-risk.csv", csv);
    }

    @Transactional(readOnly = true)
    public CsvFile calibrationRecordsCsv() {
        UUID departmentId = reportsDepartmentScope();
        Map<UUID, Equipment> equipmentById = equipmentById();
        List<CalibrationRecord> items = calibrationRecordRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .filter(record -> departmentId == null || isEquipmentInDepartment(equipmentById, record.getEquipmentId(), departmentId))
                .toList();
        String csv = CsvWriter.build(
                List.of("id", "equipmentId", "certificateNumber", "performedBy",
                        "performedAt", "nextDueAt", "result", "tolerance",
                        "measuredError", "unit", "notes"),
                items,
                List.of(
                        CalibrationRecord::getId,
                        CalibrationRecord::getEquipmentId,
                        CalibrationRecord::getCertificateNumber,
                        CalibrationRecord::getPerformedBy,
                        CalibrationRecord::getPerformedAt,
                        CalibrationRecord::getNextDueAt,
                        CalibrationRecord::getResult,
                        CalibrationRecord::getTolerance,
                        CalibrationRecord::getMeasuredError,
                        CalibrationRecord::getUnit,
                        CalibrationRecord::getNotes
                ));
        return new CsvFile("calibration-records.csv", csv);
    }

    @Transactional(readOnly = true)
    public CsvFile userCertificationsCsv() {
        UUID departmentId = reportsDepartmentScope();
        Map<UUID, UUID> userDepartmentById = userRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .filter(user -> user.getDepartmentId() != null)
                .collect(Collectors.toMap(com.toir.entity.users.User::getId, com.toir.entity.users.User::getDepartmentId));
        List<UserCertification> items = userCertificationRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .filter(cert -> departmentId == null || departmentId.equals(userDepartmentById.get(cert.getUserId())))
                .toList();
        String csv = CsvWriter.build(
                List.of("id", "userId", "typeCode", "certificateNumber", "issuedBy",
                        "issuedAt", "expiresAt", "gradeOrLevel", "status", "notes"),
                items,
                List.of(
                        UserCertification::getId,
                        UserCertification::getUserId,
                        UserCertification::getTypeCode,
                        UserCertification::getCertificateNumber,
                        UserCertification::getIssuedBy,
                        UserCertification::getIssuedAt,
                        UserCertification::getExpiresAt,
                        UserCertification::getGradeOrLevel,
                        UserCertification::getStatus,
                        UserCertification::getNotes
                ));
        return new CsvFile("user-certifications.csv", csv);
    }

    @Transactional(readOnly = true)
    public CsvFile equipmentCsv() {
        UUID departmentId = reportsDepartmentScope();
        List<Equipment> items = equipmentRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .filter(equipment -> departmentId == null || departmentId.equals(equipment.getDepartmentId()))
                .toList();
        String csv = CsvWriter.build(
                List.of("id", "code", "name", "inventoryNumber", "serialNumber", "model",
                        "equipmentTypeId", "departmentId", "locationId", "criticalityClassId",
                        "responsibleId", "status", "commissionedAt", "manufacturer"),
                items,
                List.of(
                        Equipment::getId,
                        Equipment::getCode,
                        Equipment::getName,
                        Equipment::getInventoryNumber,
                        Equipment::getSerialNumber,
                        Equipment::getModel,
                        Equipment::getEquipmentTypeId,
                        Equipment::getDepartmentId,
                        Equipment::getLocationId,
                        Equipment::getCriticalityClassId,
                        Equipment::getResponsibleId,
                        Equipment::getStatus,
                        Equipment::getCommissionedAt,
                        Equipment::getManufacturer
                ));
        return new CsvFile("equipment.csv", csv);
    }

    @Transactional(readOnly = true)
    public CsvFile repairRequestsCsv() {
        UUID departmentId = reportsDepartmentScope();
        List<RepairRequest> items = repairRequestRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .filter(request -> departmentId == null || departmentId.equals(request.getDepartmentId()))
                .toList();
        List<Function<RepairRequest, Object>> extractors = List.of(
                RepairRequest::getId,
                RepairRequest::getNumber,
                RepairRequest::getTitle,
                RepairRequest::getEquipmentId,
                RepairRequest::getDepartmentId,
                RepairRequest::getReporterId,
                RepairRequest::getAssignedToId,
                RepairRequest::getPriority,
                RepairRequest::getCriticality,
                RepairRequest::getStatus,
                RepairRequest::getSource,
                RepairRequest::getDetectedAt,
                RepairRequest::getTargetCompletionAt,
                RepairRequest::getReactedAt,
                RepairRequest::getActualCompletionAt,
                RepairRequest::getCloseResult
        );
        String csv = CsvWriter.build(
                List.of("id", "number", "title", "equipmentId", "departmentId", "reporterId",
                        "assignedToId", "priority", "criticality", "status", "source",
                        "detectedAt", "targetCompletionAt", "reactedAt", "actualCompletionAt", "closeResult"),
                items,
                extractors
        );
        return new CsvFile("repair-requests.csv", csv);
    }

    @Transactional(readOnly = true)
    public CsvFile defectsCsv() {
        UUID departmentId = reportsDepartmentScope();
        Map<UUID, Equipment> equipmentById = equipmentById();
        List<Defect> items = defectRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .filter(defect -> departmentId == null || isEquipmentInDepartment(equipmentById, defect.getEquipmentId(), departmentId))
                .toList();
        String csv = CsvWriter.build(
                List.of("id", "code", "title", "equipmentId", "category", "severity",
                        "failureReason", "rootCause", "status", "recurrenceCount",
                        "detectedAt", "resolvedAt"),
                items,
                List.of(
                        Defect::getId,
                        Defect::getCode,
                        Defect::getTitle,
                        Defect::getEquipmentId,
                        Defect::getCategory,
                        Defect::getSeverity,
                        Defect::getFailureReason,
                        Defect::getRootCause,
                        Defect::getStatus,
                        Defect::getRecurrenceCount,
                        Defect::getDetectedAt,
                        Defect::getResolvedAt
                ));
        return new CsvFile("defects.csv", csv);
    }

    @Transactional(readOnly = true)
    public CsvFile workOrdersCsv() {
        UUID departmentId = reportsDepartmentScope();
        List<WorkOrder> items = workOrderRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .filter(workOrder -> departmentId == null || departmentId.equals(workOrder.getDepartmentId()))
                .toList();
        String csv = CsvWriter.build(
                List.of("id", "number", "title", "equipmentId", "departmentId",
                        "type", "priority", "status", "createdById", "approvedById",
                        "startPlannedAt", "endPlannedAt", "startedAt", "completedAt",
                        "result", "repairRequestId", "pprTaskId", "counteragentId"),
                items,
                List.of(
                        WorkOrder::getId,
                        WorkOrder::getNumber,
                        WorkOrder::getTitle,
                        WorkOrder::getEquipmentId,
                        WorkOrder::getDepartmentId,
                        WorkOrder::getType,
                        WorkOrder::getPriority,
                        WorkOrder::getStatus,
                        WorkOrder::getCreatedById,
                        WorkOrder::getApprovedById,
                        WorkOrder::getStartPlannedAt,
                        WorkOrder::getEndPlannedAt,
                        WorkOrder::getStartedAt,
                        WorkOrder::getCompletedAt,
                        WorkOrder::getResult,
                        WorkOrder::getRepairRequestId,
                        WorkOrder::getPprTaskId,
                        WorkOrder::getCounteragentId
                ));
        return new CsvFile("work-orders.csv", csv);
    }

    @Transactional(readOnly = true)
    public CsvFile downtimesCsv() {
        UUID departmentId = reportsDepartmentScope();
        List<DowntimeEvent> items = downtimeEventRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .filter(downtime -> departmentId == null || departmentId.equals(downtime.getDepartmentId()))
                .toList();
        String csv = CsvWriter.build(
                List.of("id", "equipmentId", "departmentId", "workOrderId",
                        "startAt", "endAt", "durationMinutes", "type", "description"),
                items,
                List.of(
                        DowntimeEvent::getId,
                        DowntimeEvent::getEquipmentId,
                        DowntimeEvent::getDepartmentId,
                        DowntimeEvent::getWorkOrderId,
                        DowntimeEvent::getStartAt,
                        DowntimeEvent::getEndAt,
                        DowntimeEvent::getDurationMinutes,
                        DowntimeEvent::getType,
                        DowntimeEvent::getDescription
                ));
        return new CsvFile("downtimes.csv", csv);
    }

    @Transactional(readOnly = true)
    public CsvFile actualCostsCsv() {
        UUID departmentId = reportsDepartmentScope();
        Map<UUID, WorkOrder> workOrderById = workOrderRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .collect(Collectors.toMap(WorkOrder::getId, Function.identity()));
        Map<UUID, RepairRequest> repairRequestById = repairRequestRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .collect(Collectors.toMap(RepairRequest::getId, Function.identity()));
        List<ActualCost> items = actualCostRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .filter(cost -> departmentId == null || actualCostInDepartment(cost, workOrderById, repairRequestById, departmentId))
                .toList();
        String csv = CsvWriter.build(
                List.of("id", "workOrderId", "repairRequestId", "contractorWorkId",
                        "costCategoryId", "budgetLineId", "status", "amount",
                        "costDate", "reviewedById", "reviewedAt", "reviewComment", "notes"),
                items,
                List.of(
                        ActualCost::getId,
                        ActualCost::getWorkOrderId,
                        ActualCost::getRepairRequestId,
                        ActualCost::getContractorWorkId,
                        ActualCost::getCostCategoryId,
                        ActualCost::getBudgetLineId,
                        ActualCost::getStatus,
                        ActualCost::getAmount,
                        ActualCost::getCostDate,
                        ActualCost::getReviewedById,
                        ActualCost::getReviewedAt,
                        ActualCost::getReviewComment,
                        ActualCost::getNotes
                ));
        return new CsvFile("actual-costs.csv", csv);
    }

    public record CsvFile(String filename, String content) {}

    private UUID reportsDepartmentScope() {
        if (scopeAccessService.isScopeAdmin()) {
            return null;
        }
        UUID currentDepartmentId = scopeAccessService.currentDepartmentIdOrNull();
        if (currentDepartmentId == null) {
            throw new AccessDeniedException("Access denied by data scope");
        }
        return currentDepartmentId;
    }

    private Map<UUID, Equipment> equipmentById() {
        return equipmentRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .collect(Collectors.toMap(Equipment::getId, Function.identity()));
    }

    private boolean isEquipmentInDepartment(Map<UUID, Equipment> equipmentById, UUID equipmentId, UUID departmentId) {
        Equipment equipment = equipmentById.get(equipmentId);
        return equipment != null && departmentId.equals(equipment.getDepartmentId());
    }

    private boolean actualCostInDepartment(ActualCost cost,
                                           Map<UUID, WorkOrder> workOrderById,
                                           Map<UUID, RepairRequest> repairRequestById,
                                           UUID departmentId) {
        if (cost.getWorkOrderId() != null) {
            WorkOrder workOrder = workOrderById.get(cost.getWorkOrderId());
            return workOrder != null && departmentId.equals(workOrder.getDepartmentId());
        }
        if (cost.getRepairRequestId() != null) {
            RepairRequest repairRequest = repairRequestById.get(cost.getRepairRequestId());
            return repairRequest != null && departmentId.equals(repairRequest.getDepartmentId());
        }
        return false;
    }
}
