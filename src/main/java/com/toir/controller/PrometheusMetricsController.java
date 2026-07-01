package com.toir.controller;

import com.toir.service.OpsMetricsService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Prometheus text-format exposition for the same metrics as {@link OpsMetricsController}.
 * Scrape agents such as Prometheus or VictoriaMetrics can consume this endpoint directly.
 */
@RestController
@RequestMapping("/api/v1/ops")
@Tag(name = "ops")
@RequiredArgsConstructor
public class PrometheusMetricsController {

    private final OpsMetricsService opsMetricsService;


    @GetMapping(value = "/prometheus", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> prometheus() {
        StringBuilder sb = new StringBuilder();
        OpsMetricsService.OpsMetricsSnapshot snapshot = opsMetricsService.snapshot();
        var counts = snapshot.counts();

        gauge(sb, "toir_equipment_total", "Total equipment records", counts.equipment());
        gauge(sb, "toir_defects_open", "Defects not yet closed", counts.defectsOpen());
        gauge(sb, "toir_repair_requests_open", "Open/in-progress repair requests", counts.repairRequestsOpen());
        gauge(sb, "toir_work_orders_open", "Open work orders", counts.workOrdersOpen());
        gauge(sb, "toir_ppr_tasks_planned", "PPR tasks planned", counts.pprTasksPlanned());
        gauge(sb, "toir_ppr_tasks_overdue", "PPR tasks overdue", counts.pprTasksOverdue());
        gauge(sb, "toir_procurement_draft", "Draft procurement requests", counts.procurementDraft());
        gauge(sb, "toir_brigades", "Brigades registered", counts.brigades());
        gauge(sb, "toir_condition_readings_total", "Condition readings recorded", counts.conditionReadings());
        gauge(sb, "toir_condition_alarms", "Active ALARM condition readings", counts.conditionAlarms());
        gauge(sb, "toir_condition_warnings", "Active WARN condition readings", snapshot.conditionWarnings());
        gauge(sb, "toir_certifications_active", "Active user certifications", counts.activeCertifications());
        gauge(sb, "toir_certifications_expired", "Expired user certifications", counts.expiredCertifications());
        gauge(sb, "toir_calibration_records", "Calibration records total", counts.calibrationRecords());
        gauge(sb, "toir_inspection_routes", "Inspection routes configured", counts.inspectionRoutes());
        gauge(sb, "toir_inspection_rounds", "Inspection rounds executed", counts.inspectionRounds());
        gauge(sb, "toir_rcm_snapshots", "RCM snapshots captured", counts.rcmSnapshots());
        gauge(sb, "toir_notifications_total", "Notifications in the system", counts.notifications());
        gauge(sb, "toir_webhook_deliveries", "Webhook delivery log entries", counts.webhookDeliveries());
        gauge(sb, "toir_maintenance_upcoming", "Maintenance due events currently upcoming", counts.maintenanceUpcoming());
        gauge(sb, "toir_maintenance_due", "Maintenance due events currently due", counts.maintenanceDue());
        gauge(sb, "toir_maintenance_overdue", "Maintenance due events currently overdue", counts.maintenanceOverdue());
        gauge(sb, "toir_maintenance_blocked", "Maintenance due events currently blocked", counts.maintenanceBlocked());
        gauge(sb, "toir_maintenance_awaiting_approval", "Maintenance due events awaiting approval", counts.maintenanceAwaitingApproval());

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
