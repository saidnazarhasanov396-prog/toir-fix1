package com.toir.service.approval;

import com.toir.entity.ApprovalRequest;
import com.toir.enums.ApprovalTargetType;
import com.toir.enums.NotificationSeverity;
import com.toir.repository.ApprovalTemplateRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class ApprovalSlaPolicyService {

    private final long defaultHours;
    private final long procurementHours;
    private final long workOrderHours;
    private final long maintenanceDueEventHours;
    private final NotificationSeverity escalationSeverity;
    private final ApprovalTemplateRepository templateRepository;

    public ApprovalSlaPolicyService(
            @Value("${toir.approvals.sla.default-hours:24}") long defaultHours,
            @Value("${toir.approvals.sla.procurement-request-hours:48}") long procurementHours,
            @Value("${toir.approvals.sla.work-order-hours:24}") long workOrderHours,
            @Value("${toir.approvals.sla.maintenance-due-event-hours:12}") long maintenanceDueEventHours,
            @Value("${toir.approvals.escalation.severity:WARNING}") NotificationSeverity escalationSeverity,
            ApprovalTemplateRepository templateRepository) {
        this.defaultHours = defaultHours;
        this.procurementHours = procurementHours;
        this.workOrderHours = workOrderHours;
        this.maintenanceDueEventHours = maintenanceDueEventHours;
        this.escalationSeverity = escalationSeverity;
        this.templateRepository = templateRepository;
    }

    public Duration slaFor(ApprovalRequest request) {
        ApprovalTargetType targetType = request == null ? null : request.getTargetType();
        if (targetType != null) {
            var template = templateRepository.findFirstByTargetTypeAndActiveTrueAndIsDeletedFalseOrderByCreatedAtDesc(targetType);
            if (template.isPresent()) {
                return Duration.ofHours(Math.max(1, template.get().getSlaHours()));
            }
        }
        long hours = switch (targetType == null ? ApprovalTargetType.OTHER : targetType) {
            case PROCUREMENT_REQUEST, PROCUREMENT -> procurementHours;
            case WORK_ORDER -> workOrderHours;
            case MAINTENANCE_DUE_EVENT -> maintenanceDueEventHours;
            default -> defaultHours;
        };
        return Duration.ofHours(Math.max(1, hours));
    }

    public NotificationSeverity escalationSeverity() {
        return escalationSeverity == null ? NotificationSeverity.WARNING : escalationSeverity;
    }

    public NotificationSeverity escalationSeverityFor(ApprovalRequest request) {
        ApprovalTargetType targetType = request == null ? null : request.getTargetType();
        if (targetType != null) {
            return templateRepository.findFirstByTargetTypeAndActiveTrueAndIsDeletedFalseOrderByCreatedAtDesc(targetType)
                    .map(template -> template.getEscalationSeverity() == null
                            ? escalationSeverity()
                            : template.getEscalationSeverity())
                    .orElseGet(this::escalationSeverity);
        }
        return escalationSeverity();
    }
}
