package com.toir.controller;

import com.toir.repository.projects.BrigadeRepository;
import com.toir.repository.CalibrationRecordRepository;
import com.toir.repository.users.UserCertificationRepository;
import com.toir.repository.ConditionReadingRepository;
import com.toir.repository.defects.DefectRepository;
import com.toir.enums.DefectStatus;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.inspection.InspectionRoundRepository;
import com.toir.repository.inspection.InspectionRouteRepository;
import com.toir.repository.NotificationRepository;
import com.toir.repository.PprTaskRepository;
import com.toir.enums.PprTaskStatus;
import com.toir.repository.ProcurementRequestRepository;
import com.toir.enums.ProcurementRequestStatus;
import com.toir.repository.RcmSnapshotRepository;
import com.toir.repository.repair.RepairRequestRepository;
import com.toir.enums.RequestStatus;
import com.toir.repository.WebhookEventLogRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.enums.WorkOrderStatus;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
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


    @GetMapping(value = "/prometheus", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> prometheus() {
        StringBuilder sb = new StringBuilder();

        long defectsOpen = defectRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .filter(d -> d.getStatus() != DefectStatus.CLOSED).count();
        long workOrdersOpen = workOrderRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .filter(w -> w.getStatus() != WorkOrderStatus.CLOSED
                        && w.getStatus() != WorkOrderStatus.CANCELLED).count();

        gauge(sb, "toir_equipment_total", "Total equipment records", equipmentRepository.countByIsDeletedFalse());
        gauge(sb, "toir_defects_open", "Defects not yet closed", defectsOpen);
        gauge(sb, "toir_repair_requests_open", "Open/in-progress repair requests",
                repairRequestRepository.countByStatusAndIsDeletedFalse(RequestStatus.OPEN.toString())
                        + repairRequestRepository.countByStatusAndIsDeletedFalse(RequestStatus.IN_PROGRESS.toString()));
        gauge(sb, "toir_work_orders_open", "Open work orders", workOrdersOpen);
        gauge(sb, "toir_ppr_tasks_planned", "PPR tasks planned",
                pprTaskRepository.countByStatusAndIsDeletedFalse(PprTaskStatus.PLANNED.toString()));
        gauge(sb, "toir_ppr_tasks_overdue", "PPR tasks overdue",
                pprTaskRepository.countByStatusAndIsDeletedFalse(PprTaskStatus.OVERDUE.toString()));
        gauge(sb, "toir_procurement_draft", "Draft procurement requests",
                procurementRequestRepository.countByStatusAndIsDeletedFalse(ProcurementRequestStatus.DRAFT.toString()));
        gauge(sb, "toir_brigades", "Brigades registered", brigadeRepository.countByIsDeletedFalse());
        gauge(sb, "toir_condition_readings_total", "Condition readings recorded",
                conditionReadingRepository.countByIsDeletedFalse());
        gauge(sb, "toir_condition_alarms", "Active ALARM condition readings",
                conditionReadingRepository.findAllBySeverityAndIsDeletedFalseOrderByRecordedAtDesc("ALARM").size());
        gauge(sb, "toir_condition_warnings", "Active WARN condition readings",
                conditionReadingRepository.findAllBySeverityAndIsDeletedFalseOrderByRecordedAtDesc("WARN").size());
        gauge(sb, "toir_certifications_active", "Active user certifications",
                userCertificationRepository.findAllByStatusAndIsDeletedFalse("ACTIVE").size());
        gauge(sb, "toir_certifications_expired", "Expired user certifications",
                userCertificationRepository.findAllByStatusAndIsDeletedFalse("EXPIRED").size());
        gauge(sb, "toir_calibration_records", "Calibration records total",
                calibrationRecordRepository.countByIsDeletedFalse());
        gauge(sb, "toir_inspection_routes", "Inspection routes configured",
                inspectionRouteRepository.countByIsDeletedFalse());
        gauge(sb, "toir_inspection_rounds", "Inspection rounds executed",
                inspectionRoundRepository.countByIsDeletedFalse());
        gauge(sb, "toir_rcm_snapshots", "RCM snapshots captured",
                rcmSnapshotRepository.countByIsDeletedFalse());
        gauge(sb, "toir_notifications_total", "Notifications in the system",
                notificationRepository.countByIsDeletedFalse());
        gauge(sb, "toir_webhook_deliveries", "Webhook delivery log entries",
                webhookEventLogRepository.countByIsDeletedFalse());

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
