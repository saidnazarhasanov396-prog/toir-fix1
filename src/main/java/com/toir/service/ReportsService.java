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
import com.toir.util.CsvWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.function.Function;

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
    private final RcmService rcmService;

    @Transactional(readOnly = true)
    public CsvFile rcmRiskCsv() {
        List<EquipmentRiskScore> items = rcmService.computeAll();
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
        List<CalibrationRecord> items = calibrationRecordRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc();
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
        List<UserCertification> items = userCertificationRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc();
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
        List<Equipment> items = equipmentRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc();
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
        List<RepairRequest> items = repairRequestRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc();
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
        List<Defect> items = defectRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc();
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
        List<WorkOrder> items = workOrderRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc();
        String csv = CsvWriter.build(
                List.of("id", "number", "title", "equipmentId", "departmentId",
                        "type", "priority", "status", "createdById", "approvedById",
                        "startPlannedAt", "endPlannedAt", "startedAt", "completedAt",
                        "result", "repairRequestId", "pprTaskId", "contractorId"),
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
                        WorkOrder::getContractorId
                ));
        return new CsvFile("work-orders.csv", csv);
    }

    @Transactional(readOnly = true)
    public CsvFile downtimesCsv() {
        List<DowntimeEvent> items = downtimeEventRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc();
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
        List<ActualCost> items = actualCostRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc();
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
}
