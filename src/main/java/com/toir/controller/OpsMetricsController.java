package com.toir.controller;

import com.toir.dto.ops.OpsMetricsResponse;
import com.toir.repository.BrigadeRepository;
import com.toir.repository.CalibrationRecordRepository;
import com.toir.repository.UserCertificationRepository;
import com.toir.repository.ConditionReadingRepository;
import com.toir.repository.DefectRepository;
import com.toir.enums.DefectStatus;
import com.toir.repository.EquipmentRepository;
import com.toir.repository.InspectionRoundRepository;
import com.toir.repository.InspectionRouteRepository;
import com.toir.repository.NotificationRepository;
import com.toir.repository.PprTaskRepository;
import com.toir.enums.PprTaskStatus;
import com.toir.repository.ProcurementRequestRepository;
import com.toir.enums.ProcurementRequestStatus;
import com.toir.repository.RcmSnapshotRepository;
import com.toir.repository.RepairRequestRepository;
import com.toir.enums.RequestStatus;
import com.toir.repository.WebhookEventLogRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.enums.WorkOrderStatus;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * ÐžÐ¿ÐµÑ€Ð°Ñ‚Ð¸Ð²Ð½Ñ‹Ðµ Ð¼ÐµÑ‚Ñ€Ð¸ÐºÐ¸ ÑÐ¸ÑÑ‚ÐµÐ¼Ñ‹ â€” Ð»Ñ‘Ð³ÐºÐ¸Ð¹ ÑÐ½Ð¸Ð¼Ð¾Ðº ÑÐ¾ÑÑ‚Ð¾ÑÐ½Ð¸Ñ Ð´Ð»Ñ Ð¼Ð¾Ð½Ð¸Ñ‚Ð¾Ñ€Ð¸Ð½Ð³Ð°
 * Ð¸ health-check'Ð¾Ð². ÐÐµ Ð·Ð°Ð¼ÐµÐ½ÑÐµÑ‚ full dashboard, Ð½Ð¾ ÑƒÐ´Ð¾Ð±ÐµÐ½ Ð´Ð»Ñ ops team.
 */
@RestController
@RequestMapping("/api/v1/ops")
@Tag(name = "ops")
public class OpsMetricsController {

    private final EquipmentRepository equipmentRepository;
    private final DefectRepository defectRepository;
    private final RepairRequestRepository repairRequestRepository;
    private final WorkOrderRepository workOrderRepository;
    private final PprTaskRepository pprTaskRepository;
    private final ProcurementRequestRepository procurementRequestRepository;
    private final BrigadeRepository brigadeRepository;
    private final ConditionReadingRepository conditionReadingRepository;
    private final UserCertificationRepository userCertificationRepository;
    private final CalibrationRecordRepository calibrationRecordRepository;
    private final InspectionRouteRepository inspectionRouteRepository;
    private final InspectionRoundRepository inspectionRoundRepository;
    private final RcmSnapshotRepository rcmSnapshotRepository;
    private final NotificationRepository notificationRepository;
    private final WebhookEventLogRepository webhookEventLogRepository;

    public OpsMetricsController(EquipmentRepository equipmentRepository,
                                DefectRepository defectRepository,
                                RepairRequestRepository repairRequestRepository,
                                WorkOrderRepository workOrderRepository,
                                PprTaskRepository pprTaskRepository,
                                ProcurementRequestRepository procurementRequestRepository,
                                BrigadeRepository brigadeRepository,
                                ConditionReadingRepository conditionReadingRepository,
                                UserCertificationRepository userCertificationRepository,
                                CalibrationRecordRepository calibrationRecordRepository,
                                InspectionRouteRepository inspectionRouteRepository,
                                InspectionRoundRepository inspectionRoundRepository,
                                RcmSnapshotRepository rcmSnapshotRepository,
                                NotificationRepository notificationRepository,
                                WebhookEventLogRepository webhookEventLogRepository) {
        this.equipmentRepository = equipmentRepository;
        this.defectRepository = defectRepository;
        this.repairRequestRepository = repairRequestRepository;
        this.workOrderRepository = workOrderRepository;
        this.pprTaskRepository = pprTaskRepository;
        this.procurementRequestRepository = procurementRequestRepository;
        this.brigadeRepository = brigadeRepository;
        this.conditionReadingRepository = conditionReadingRepository;
        this.userCertificationRepository = userCertificationRepository;
        this.calibrationRecordRepository = calibrationRecordRepository;
        this.inspectionRouteRepository = inspectionRouteRepository;
        this.inspectionRoundRepository = inspectionRoundRepository;
        this.rcmSnapshotRepository = rcmSnapshotRepository;
        this.notificationRepository = notificationRepository;
        this.webhookEventLogRepository = webhookEventLogRepository;
    }

    @GetMapping("/metrics")
    public OpsMetricsResponse metrics() {
        return new OpsMetricsResponse(
                Instant.now().toString(),
                new OpsMetricsResponse.Counts(
                        equipmentRepository.countByIsDeletedFalse(),
                        defectRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                                .filter(d -> d.getStatus() != DefectStatus.CLOSED).count(),
                        repairRequestRepository.countByStatusAndIsDeletedFalse(RequestStatus.OPEN.name())
                                + repairRequestRepository.countByStatusAndIsDeletedFalse(RequestStatus.IN_PROGRESS.name()),
                        workOrderRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                                .filter(w -> w.getStatus() != WorkOrderStatus.CLOSED
                                        && w.getStatus() != WorkOrderStatus.CANCELLED).count(),
                        pprTaskRepository.countByStatusAndIsDeletedFalse(PprTaskStatus.PLANNED.name()),
                        pprTaskRepository.countByStatusAndIsDeletedFalse(PprTaskStatus.OVERDUE.name()),
                        procurementRequestRepository.countByStatusAndIsDeletedFalse(ProcurementRequestStatus.DRAFT.name()),
                        brigadeRepository.countByIsDeletedFalse(),
                        conditionReadingRepository.countByIsDeletedFalse(),
                        conditionReadingRepository.findAllBySeverityAndIsDeletedFalseOrderByRecordedAtDesc("ALARM").size(),
                        userCertificationRepository.findAllByStatusAndIsDeletedFalse("ACTIVE").size(),
                        userCertificationRepository.findAllByStatusAndIsDeletedFalse("EXPIRED").size(),
                        calibrationRecordRepository.countByIsDeletedFalse(),
                        inspectionRouteRepository.countByIsDeletedFalse(),
                        inspectionRoundRepository.countByIsDeletedFalse(),
                        rcmSnapshotRepository.countByIsDeletedFalse(),
                        notificationRepository.countByIsDeletedFalse(),
                        webhookEventLogRepository.countByIsDeletedFalse()
                ),
                "UP"
        );
    }
}
