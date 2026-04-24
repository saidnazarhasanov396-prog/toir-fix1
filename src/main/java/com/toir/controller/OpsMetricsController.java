package com.toir.controller;

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
import java.util.LinkedHashMap;
import java.util.Map;

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
    public Map<String, Object> metrics() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("timestamp", Instant.now().toString());

        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("equipment", equipmentRepository.count());
        counts.put("defectsOpen", defectRepository.findAll().stream()
                .filter(d -> d.getStatus() != DefectStatus.CLOSED).count());
        counts.put("repairRequestsOpen", repairRequestRepository.countByStatus(RequestStatus.OPEN)
                + repairRequestRepository.countByStatus(RequestStatus.IN_PROGRESS));
        counts.put("workOrdersOpen", workOrderRepository.findAll().stream()
                .filter(w -> w.getStatus() != WorkOrderStatus.CLOSED
                        && w.getStatus() != WorkOrderStatus.CANCELLED).count());
        counts.put("pprTasksPlanned", pprTaskRepository.countByStatus(PprTaskStatus.PLANNED));
        counts.put("pprTasksOverdue", pprTaskRepository.countByStatus(PprTaskStatus.OVERDUE));
        counts.put("procurementDraft", procurementRequestRepository.countByStatus(ProcurementRequestStatus.DRAFT));
        counts.put("brigades", brigadeRepository.count());
        counts.put("conditionReadings", conditionReadingRepository.count());
        counts.put("conditionAlarms", conditionReadingRepository
                .findAllBySeverityOrderByRecordedAtDesc("ALARM").size());
        counts.put("activeCertifications", userCertificationRepository.findAllByStatus("ACTIVE").size());
        counts.put("expiredCertifications", userCertificationRepository.findAllByStatus("EXPIRED").size());
        counts.put("calibrationRecords", calibrationRecordRepository.count());
        counts.put("inspectionRoutes", inspectionRouteRepository.count());
        counts.put("inspectionRounds", inspectionRoundRepository.count());
        counts.put("rcmSnapshots", rcmSnapshotRepository.count());
        counts.put("notifications", notificationRepository.count());
        counts.put("webhookDeliveries", webhookEventLogRepository.count());
        m.put("counts", counts);

        m.put("status", "UP");
        return m;
    }
}
