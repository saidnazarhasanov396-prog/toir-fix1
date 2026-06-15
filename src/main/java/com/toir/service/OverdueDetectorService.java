package com.toir.service;

import com.toir.entity.equipment.CalibrationRecord;
import com.toir.repository.CalibrationRecordRepository;
import com.toir.entity.users.UserCertification;
import com.toir.repository.users.UserCertificationRepository;
import com.toir.entity.EscalationEvent;
import com.toir.repository.EscalationEventRepository;
import com.toir.enums.EscalationStatus;
import com.toir.enums.NotificationSeverity;
import com.toir.enums.NotificationStatus;
import com.toir.entity.PprTask;
import com.toir.repository.NotificationRepository;
import com.toir.repository.PprTaskRepository;
import com.toir.enums.PprTaskStatus;
import com.toir.entity.repair.RepairRequest;
import com.toir.repository.repair.RepairRequestRepository;
import com.toir.enums.RequestStatus;
import com.toir.enums.SlaTriggerType;
import com.toir.entity.users.User;
import com.toir.repository.users.UserRepository;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.repository.WorkOrderRepository;
import com.toir.enums.WorkOrderStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Scans PPR tasks, repair requests and work orders for SLA breaches,
 * marks overdue items and raises notifications + escalation events.
 *
 * Called via POST {@code /overdue/evaluate} or from an external cron.
 */
@Service
@RequiredArgsConstructor
public class OverdueDetectorService {

    private final PprTaskRepository pprTaskRepository;
    private final RepairRequestRepository repairRequestRepository;
    private final WorkOrderRepository workOrderRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;
    private final EscalationEventRepository escalationEventRepository;
    private final UserRepository userRepository;
    private final CalibrationRecordRepository calibrationRecordRepository;
    private final UserCertificationRepository userCertificationRepository;



    public EvaluationResult evaluate() {
        LocalDateTime nowLdt = LocalDateTime.now(ZoneOffset.UTC);
        Instant now = Instant.now();

        LocalDate today = LocalDate.now();
        int pprMarked = 0;
        int requestBreaches = 0;
        int workOrderBreaches = 0;
        int calibrationBreaches = 0;
        int certificationExpired = 0;
        int notificationsCreated = 0;
        int escalationsCreated = 0;

        java.util.UUID adminId = userRepository.findByUsernameAndIsDeletedFalse("admin").map(User::getId).orElse(null);

        // 1) PPR tasks overdue
        for (PprTask task : pprTaskRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()) {
            if (task.getDueDate() == null) continue;
            if (task.getStatus() == PprTaskStatus.COMPLETED
                    || task.getStatus() == PprTaskStatus.CANCELLED
                    || task.getStatus() == PprTaskStatus.OVERDUE) continue;
            if (task.getDueDate().isBefore(nowLdt)) {
                task.setStatus(PprTaskStatus.OVERDUE);
                pprMarked++;
                if (adminId != null) {
                    notificationsCreated += createNotification(
                            adminId,
                            "Просроченная задача ППР " + task.getCode(),
                            "Срок " + task.getDueDate() + " прошёл, работы не закрыты",
                            NotificationSeverity.WARNING,
                            "PprTask",
                            task.getId().toString());
                }
                escalationsCreated += raiseEscalation("PprTask", task.getId().toString(), SlaTriggerType.PPR_OVERDUE);
            }
        }

        // 2) Repair requests past targetCompletionAt
        for (RepairRequest r : repairRequestRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()) {
            if (r.getStatus() == RequestStatus.CLOSED || r.getStatus() == RequestStatus.CANCELLED) continue;
            if (r.getTargetCompletionAt() == null) continue;
            if (r.getTargetCompletionAt().isBefore(now)) {
                requestBreaches++;
                if (adminId != null) {
                    notificationsCreated += createNotification(
                            adminId,
                            "Просроченная заявка " + r.getNumber(),
                            "Плановая дата устранения " + r.getTargetCompletionAt() + " прошла",
                            NotificationSeverity.CRITICAL,
                            "RepairRequest",
                            r.getId().toString());
                }
                escalationsCreated += raiseEscalation("RepairRequest", r.getId().toString(),
                        "EMERGENCY".equals(r.getPriority().name())
                                ? SlaTriggerType.REQUEST_EMERGENCY
                                : SlaTriggerType.REQUEST_OVERDUE);
            }
        }

        // 3) Work orders past endPlannedAt
        for (WorkOrder w : workOrderRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()) {
            if (w.getStatus() == WorkOrderStatus.CLOSED || w.getStatus() == WorkOrderStatus.CANCELLED) continue;
            if (w.getEndPlannedAt() == null) continue;
            if (w.getEndPlannedAt().isBefore(now)) {
                workOrderBreaches++;
                if (adminId != null) {
                    notificationsCreated += createNotification(
                            adminId,
                            "Просроченный наряд " + w.getNumber(),
                            "Плановая дата завершения " + w.getEndPlannedAt() + " прошла",
                            NotificationSeverity.WARNING,
                            "WorkOrder",
                            w.getId().toString());
                }
                escalationsCreated += raiseEscalation("WorkOrder", w.getId().toString(),
                        SlaTriggerType.WORK_ORDER_OVERDUE);
            }
        }

        // 4) Calibration records past nextDueAt
        for (CalibrationRecord c : calibrationRecordRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()) {
            if (c.getNextDueAt() == null) continue;
            if (!c.getNextDueAt().isBefore(today)) continue;
            // Only alert once per record: use last calibration per equipment as reference
            List<CalibrationRecord> history = calibrationRecordRepository
                    .findAllByEquipmentIdAndIsDeletedFalseOrderByPerformedAtDesc(c.getEquipmentId());
            if (!history.isEmpty() && !history.get(0).getId().equals(c.getId())) continue;
            calibrationBreaches++;
            if (adminId != null) {
                notificationsCreated += createNotification(
                        adminId,
                        "Просрочена поверка " + c.getCertificateNumber(),
                        "Срок поверки оборудования " + c.getEquipmentId() + " истёк " + c.getNextDueAt(),
                        NotificationSeverity.WARNING,
                        "CalibrationRecord",
                        c.getId().toString());
            }
        }

        // 5) User certifications past expiresAt → auto-EXPIRED
        for (UserCertification uc : userCertificationRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()) {
            if (uc.getExpiresAt() == null) continue;
            if (!"ACTIVE".equals(uc.getStatus())) continue;
            if (!uc.getExpiresAt().isBefore(today)) continue;
            uc.setStatus("EXPIRED");
            certificationExpired++;
            if (adminId != null) {
                notificationsCreated += createNotification(
                        adminId,
                        "Истёк сертификат " + uc.getTypeCode(),
                        "Сертификат сотрудника " + uc.getUserId() + " истёк " + uc.getExpiresAt(),
                        NotificationSeverity.CRITICAL,
                        "UserCertification",
                        uc.getId().toString());
            }
        }

        return new EvaluationResult(pprMarked, requestBreaches, workOrderBreaches,
                calibrationBreaches, certificationExpired,
                notificationsCreated, escalationsCreated);
    }

    private int createNotification(java.util.UUID recipientId, String title, String message,
                                   NotificationSeverity severity, String entityType, String entityId) {
        // idempotent: skip if we already have an open notification for the same entity
        boolean exists = notificationRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .anyMatch(n -> entityType.equals(n.getEntityType())
                        && entityId.equals(n.getEntityId())
                        && (n.getStatus() == NotificationStatus.PENDING || n.getStatus() == NotificationStatus.SENT));
        if (exists) return 0;
        return notificationService.notifyUser(recipientId, title, message, severity, entityType, entityId)
                .map(ignored -> 1)
                .orElse(0);
    }

    private int raiseEscalation(String entityType, String entityId, SlaTriggerType trigger) {
        // idempotent: skip if already raised and still open
        List<EscalationEvent> existing = escalationEventRepository
                .findAllByEntityTypeAndEntityIdAndIsDeletedFalse(entityType, entityId);
        boolean alreadyOpen = existing.stream()
                .anyMatch(e -> e.getStatus() == EscalationStatus.OPEN
                        || e.getStatus() == EscalationStatus.ACKNOWLEDGED);
        if (alreadyOpen) return 0;
        EscalationEvent e = new EscalationEvent();
        e.setEntityType(entityType);
        e.setEntityId(entityId);
        e.setTriggerType(trigger);
        escalationEventRepository.save(e);
        return 1;
    }

    public record EvaluationResult(
            int pprMarkedOverdue,
            int repairRequestBreaches,
            int workOrderBreaches,
            int calibrationBreaches,
            int certificationExpired,
            int notificationsCreated,
            int escalationsCreated
    ) {}
}
