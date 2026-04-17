package com.toir.common.ops;

import com.toir.brigade.BrigadeRepository;
import com.toir.calibration.CalibrationRecordRepository;
import com.toir.certification.UserCertificationRepository;
import com.toir.conditionreading.ConditionReadingRepository;
import com.toir.defect.DefectRepository;
import com.toir.defect.DefectStatus;
import com.toir.equipment.EquipmentRepository;
import com.toir.inspection.InspectionRoundRepository;
import com.toir.inspection.InspectionRouteRepository;
import com.toir.notification.NotificationRepository;
import com.toir.pprplanning.PprTaskRepository;
import com.toir.pprplanning.PprTaskStatus;
import com.toir.procurement.ProcurementRequestRepository;
import com.toir.procurement.ProcurementRequestStatus;
import com.toir.rcm.RcmSnapshotRepository;
import com.toir.repairrequest.RepairRequestRepository;
import com.toir.repairrequest.RequestStatus;
import com.toir.webhook.WebhookEventLogRepository;
import com.toir.workorder.WorkOrderRepository;
import com.toir.workorder.WorkOrderStatus;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Prometheus text-format exposition â€” Ð¿ÑƒÐ±Ð»Ð¸ÐºÑƒÐµÑ‚ Ñ‚Ðµ Ð¶Ðµ Ð¼ÐµÑ‚Ñ€Ð¸ÐºÐ¸ Ñ‡Ñ‚Ð¾ {@link OpsMetricsController},
 * Ð½Ð¾ Ð² Ñ„Ð¾Ñ€Ð¼Ð°Ñ‚Ðµ, ÐºÐ¾Ñ‚Ð¾Ñ€Ñ‹Ð¹ Ð¿Ð°Ñ€ÑÐ¸Ñ‚ scrape-Ð°Ð³ÐµÐ½Ñ‚ Prometheus / VictoriaMetrics.
 */
@RestController
@RequestMapping("/api/v1/ops")
@Tag(name = "ops")
public class PrometheusMetricsController {

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

    public PrometheusMetricsController(EquipmentRepository equipmentRepository,
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

    @GetMapping(value = "/prometheus", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> prometheus() {
        StringBuilder sb = new StringBuilder();

        long defectsOpen = defectRepository.findAll().stream()
                .filter(d -> d.getStatus() != DefectStatus.CLOSED).count();
        long workOrdersOpen = workOrderRepository.findAll().stream()
                .filter(w -> w.getStatus() != WorkOrderStatus.CLOSED
                        && w.getStatus() != WorkOrderStatus.CANCELLED).count();

        gauge(sb, "toir_equipment_total", "Total equipment records", equipmentRepository.count());
        gauge(sb, "toir_defects_open", "Defects not yet closed", defectsOpen);
        gauge(sb, "toir_repair_requests_open", "Open/in-progress repair requests",
                repairRequestRepository.countByStatus(RequestStatus.OPEN)
                        + repairRequestRepository.countByStatus(RequestStatus.IN_PROGRESS));
        gauge(sb, "toir_work_orders_open", "Open work orders", workOrdersOpen);
        gauge(sb, "toir_ppr_tasks_planned", "PPR tasks planned",
                pprTaskRepository.countByStatus(PprTaskStatus.PLANNED));
        gauge(sb, "toir_ppr_tasks_overdue", "PPR tasks overdue",
                pprTaskRepository.countByStatus(PprTaskStatus.OVERDUE));
        gauge(sb, "toir_procurement_draft", "Draft procurement requests",
                procurementRequestRepository.countByStatus(ProcurementRequestStatus.DRAFT));
        gauge(sb, "toir_brigades", "Brigades registered", brigadeRepository.count());
        gauge(sb, "toir_condition_readings_total", "Condition readings recorded",
                conditionReadingRepository.count());
        gauge(sb, "toir_condition_alarms", "Active ALARM condition readings",
                conditionReadingRepository.findAllBySeverityOrderByRecordedAtDesc("ALARM").size());
        gauge(sb, "toir_condition_warnings", "Active WARN condition readings",
                conditionReadingRepository.findAllBySeverityOrderByRecordedAtDesc("WARN").size());
        gauge(sb, "toir_certifications_active", "Active user certifications",
                userCertificationRepository.findAllByStatus("ACTIVE").size());
        gauge(sb, "toir_certifications_expired", "Expired user certifications",
                userCertificationRepository.findAllByStatus("EXPIRED").size());
        gauge(sb, "toir_calibration_records", "Calibration records total",
                calibrationRecordRepository.count());
        gauge(sb, "toir_inspection_routes", "Inspection routes configured",
                inspectionRouteRepository.count());
        gauge(sb, "toir_inspection_rounds", "Inspection rounds executed",
                inspectionRoundRepository.count());
        gauge(sb, "toir_rcm_snapshots", "RCM snapshots captured",
                rcmSnapshotRepository.count());
        gauge(sb, "toir_notifications_total", "Notifications in the system",
                notificationRepository.count());
        gauge(sb, "toir_webhook_deliveries", "Webhook delivery log entries",
                webhookEventLogRepository.count());

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .body(sb.toString());
    }

    private void gauge(StringBuilder sb, String name, String help, long value) {
        sb.append("# HELP ").append(name).append(' ').append(help).append('\n');
        sb.append("# TYPE ").append(name).append(" gauge\n");
        sb.append(name).append(' ').append(value).append('\n');
    }
}
