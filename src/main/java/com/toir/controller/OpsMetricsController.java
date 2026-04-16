package com.toir.controller;

import com.toir.repository.BrigadeRepository;
import com.toir.repository.CalibrationRecordRepository;
import com.toir.repository.UserCertificationRepository;
import com.toir.repository.ConditionReadingRepository;
import com.toir.repository.DefectRepository;
import com.toir.entity.DefectStatus;
import com.toir.repository.EquipmentRepository;
import com.toir.repository.InspectionRoundRepository;
import com.toir.repository.InspectionRouteRepository;
import com.toir.repository.NotificationRepository;
import com.toir.repository.PprTaskRepository;
import com.toir.entity.PprTaskStatus;
import com.toir.repository.ProcurementRequestRepository;
import com.toir.entity.ProcurementRequestStatus;
import com.toir.repository.RcmSnapshotRepository;
import com.toir.repository.RepairRequestRepository;
import com.toir.entity.RequestStatus;
import com.toir.repository.WebhookEventLogRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.entity.WorkOrderStatus;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Оперативные метрики системы — лёгкий снимок состояния для мониторинга
 * и health-check'ов. Не заменяет full dashboard, но удобен для ops team.
 */
@RestController
@RequestMapping("/ops")
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
