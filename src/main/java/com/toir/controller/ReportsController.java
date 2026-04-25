package com.toir.controller;
import com.toir.util.CsvWriter;

import com.toir.entity.ActualCost;
import com.toir.repository.ActualCostRepository;
import com.toir.entity.CalibrationRecord;
import com.toir.repository.CalibrationRecordRepository;
import com.toir.entity.UserCertification;
import com.toir.repository.UserCertificationRepository;
import com.toir.entity.Defect;
import com.toir.repository.DefectRepository;
import com.toir.entity.DowntimeEvent;
import com.toir.repository.DowntimeEventRepository;
import com.toir.entity.Equipment;
import com.toir.repository.EquipmentRepository;
import com.toir.dto.rcm.EquipmentRiskScore;
import com.toir.service.RcmService;
import com.toir.entity.RepairRequest;
import com.toir.repository.RepairRequestRepository;
import com.toir.entity.WorkOrder;
import com.toir.repository.WorkOrderRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.function.Function;

/**
 * CSV export of core operational datasets â€” Ð¢Ð— Â§4.2.16.
 */
@RestController
@RequestMapping("/api/v1/reports")
@Tag(name = "reports")
public class ReportsController {

    private final EquipmentRepository equipmentRepository;
    private final RepairRequestRepository repairRequestRepository;
    private final DefectRepository defectRepository;
    private final WorkOrderRepository workOrderRepository;
    private final DowntimeEventRepository downtimeEventRepository;
    private final ActualCostRepository actualCostRepository;
    private final CalibrationRecordRepository calibrationRecordRepository;
    private final UserCertificationRepository userCertificationRepository;
    private final RcmService rcmService;

    public ReportsController(EquipmentRepository equipmentRepository,
                             RepairRequestRepository repairRequestRepository,
                             DefectRepository defectRepository,
                             WorkOrderRepository workOrderRepository,
                             DowntimeEventRepository downtimeEventRepository,
                             ActualCostRepository actualCostRepository,
                             CalibrationRecordRepository calibrationRecordRepository,
                             UserCertificationRepository userCertificationRepository,
                             RcmService rcmService) {
        this.equipmentRepository = equipmentRepository;
        this.repairRequestRepository = repairRequestRepository;
        this.defectRepository = defectRepository;
        this.workOrderRepository = workOrderRepository;
        this.downtimeEventRepository = downtimeEventRepository;
        this.actualCostRepository = actualCostRepository;
        this.calibrationRecordRepository = calibrationRecordRepository;
        this.userCertificationRepository = userCertificationRepository;
        this.rcmService = rcmService;
    }

    @GetMapping(value = "/rcm-risk.csv", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<String> rcmRiskCsv() {
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
        return csvResponse("rcm-risk.csv", csv);
    }

    @GetMapping(value = "/calibration-records.csv", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<String> calibrationRecordsCsv() {
        List<CalibrationRecord> items = calibrationRecordRepository.findAllByIsDeletedFalse();
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
        return csvResponse("calibration-records.csv", csv);
    }

    @GetMapping(value = "/user-certifications.csv", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<String> userCertificationsCsv() {
        List<UserCertification> items = userCertificationRepository.findAllByIsDeletedFalse();
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
        return csvResponse("user-certifications.csv", csv);
    }

    @GetMapping(value = "/equipment.csv", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<String> equipmentCsv() {
        List<Equipment> items = equipmentRepository.findAllByIsDeletedFalse();
        String csv = CsvWriter.build(
                List.of("id", "code", "name", "inventoryNumber", "serialNumber", "model",
                        "equipmentTypeId", "departmentId", "locationId", "criticalityClassId",
                        "responsibleId", "status", "commissionedAt", "manufacturer"),
                items,
                List.of(
                        e -> e.getId(),
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
        return csvResponse("equipment.csv", csv);
    }

    @GetMapping(value = "/repair-requests.csv", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<String> repairRequestsCsv() {
        List<RepairRequest> items = repairRequestRepository.findAllByIsDeletedFalse();
        List<Function<RepairRequest, Object>> ex = List.of(
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
                items, ex);
        return csvResponse("repair-requests.csv", csv);
    }

    @GetMapping(value = "/defects.csv", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<String> defectsCsv() {
        List<Defect> items = defectRepository.findAllByIsDeletedFalse();
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
        return csvResponse("defects.csv", csv);
    }

    @GetMapping(value = "/work-orders.csv", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<String> workOrdersCsv() {
        List<WorkOrder> items = workOrderRepository.findAllByIsDeletedFalse();
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
        return csvResponse("work-orders.csv", csv);
    }

    @GetMapping(value = "/downtimes.csv", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<String> downtimesCsv() {
        List<DowntimeEvent> items = downtimeEventRepository.findAllByIsDeletedFalse();
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
        return csvResponse("downtimes.csv", csv);
    }

    @GetMapping(value = "/actual-costs.csv", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<String> actualCostsCsv() {
        List<ActualCost> items = actualCostRepository.findAllByIsDeletedFalse();
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
        return csvResponse("actual-costs.csv", csv);
    }

    private ResponseEntity<String> csvResponse(String filename, String body) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(body);
    }
}
