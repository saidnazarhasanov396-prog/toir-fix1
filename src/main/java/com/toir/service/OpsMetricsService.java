package com.toir.service;

import com.toir.dto.ops.OpsMetricsResponse;
import com.toir.enums.DefectStatus;
import com.toir.enums.PprTaskStatus;
import com.toir.enums.ProcurementRequestStatus;
import com.toir.enums.RequestStatus;
import com.toir.enums.WorkOrderStatus;
import com.toir.repository.CalibrationRecordRepository;
import com.toir.repository.ConditionReadingRepository;
import com.toir.repository.NotificationRepository;
import com.toir.repository.PprTaskRepository;
import com.toir.repository.ProcurementRequestRepository;
import com.toir.repository.RcmSnapshotRepository;
import com.toir.repository.WebhookEventLogRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.defects.DefectRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.inspection.InspectionRoundRepository;
import com.toir.repository.inspection.InspectionRouteRepository;
import com.toir.repository.projects.BrigadeRepository;
import com.toir.repository.repair.RepairRequestRepository;
import com.toir.repository.users.UserCertificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class OpsMetricsService {

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

    @Transactional(readOnly = true)
    public OpsMetricsSnapshot snapshot() {
        long defectsOpen = defectRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .filter(d -> d.getStatus() != DefectStatus.CLOSED).count();
        long workOrdersOpen = workOrderRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .filter(w -> w.getStatus() != WorkOrderStatus.CLOSED && w.getStatus() != WorkOrderStatus.CANCELLED).count();
        long conditionWarnings = conditionReadingRepository.findAllBySeverityAndIsDeletedFalseOrderByRecordedAtDesc("WARN").size();

        OpsMetricsResponse.Counts counts = new OpsMetricsResponse.Counts(
                equipmentRepository.countByIsDeletedFalse(),
                defectsOpen,
                repairRequestRepository.countByStatusAndIsDeletedFalse(RequestStatus.OPEN.name())
                        + repairRequestRepository.countByStatusAndIsDeletedFalse(RequestStatus.IN_PROGRESS.name()),
                workOrdersOpen,
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
        );
        return new OpsMetricsSnapshot(Instant.now(), counts, conditionWarnings);
    }

    public record OpsMetricsSnapshot(
            Instant timestamp,
            OpsMetricsResponse.Counts counts,
            long conditionWarnings
    ) {}
}
