package com.toir.service;

import com.toir.dto.rcm.autoplan.RcmAutoPlanConfirmRequest;
import com.toir.dto.rcm.autoplan.RcmAutoPlanConfirmResult;
import com.toir.dto.rcm.autoplan.RcmAutoPlanDecision;
import com.toir.dto.rcm.autoplan.RcmAutoPlanPreviewResponse;
import com.toir.dto.rcm.autoplan.RcmAutoPlanPreviewRow;
import com.toir.entity.PprPlan;
import com.toir.entity.PprTask;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.enums.PprTaskStatus;
import com.toir.exception.RestException;
import com.toir.repository.PprPlanRepository;
import com.toir.repository.PprTaskRepository;
import com.toir.util.AuditBuilderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RcmAutoPlannerService {
    private final RcmAutoPlanPreviewService previewService;
    private final PprPlanRepository planRepository;
    private final PprTaskRepository taskRepository;
    private final AuditBuilderService auditBuilderService;

    @Transactional
    public RcmAutoPlanConfirmResult confirm(RcmAutoPlanConfirmRequest request) {
        RcmAutoPlanPreviewResponse preview = previewService.preview(request.riskThreshold(), request.planId());
        if (!fingerprintsMatch(preview.fingerprint(), request.previewFingerprint())) {
            throw RestException.conflict("RCM preview inputs have changed; review the refreshed preview", "RCM_PREVIEW_STALE");
        }
        PprPlan plan = null;
        if (preview.tasksToCreate() > 0) {
            UUID targetPlanId = preview.targetPlanId();
            plan = planRepository.findByIdAndIsDeletedFalse(targetPlanId)
                    .orElseThrow(() -> RestException.notFound("PprPlan not found: " + targetPlanId));
        }
        List<String> createdCodes = new ArrayList<>();
        for (RcmAutoPlanPreviewRow row : preview.rows()) {
            if (row.decision() != RcmAutoPlanDecision.CREATE) continue;
            if (taskRepository.findBySourceTypeAndSourceKeyAndIsDeletedFalse(
                    RcmAutoPlanPreviewService.SOURCE_TYPE, row.sourceKey()).isPresent()) continue;
            PprTask task = new PprTask();
            task.setCode("RCM-" + safeCode(row.equipmentCode()) + "-" + UUID.randomUUID().toString().substring(0, 8));
            task.setPlan(plan);
            task.setRegulationId(row.regulationId());
            task.setEquipmentId(row.equipmentId());
            task.setTitle("RCM: " + row.regulationName() + " (risk=" + row.riskScore() + ")");
            task.setScheduledStart(row.scheduledStart());
            task.setScheduledEnd(row.scheduledEnd());
            task.setDueDate(row.dueDate());
            task.setStatus(PprTaskStatus.PLANNED);
            task.setPriority(row.priority());
            task.setPlannedLaborHours(Math.max(0.01,
                    java.time.Duration.between(row.scheduledStart(), row.scheduledEnd()).toMinutes() / 60.0));
            task.setSourceType(RcmAutoPlanPreviewService.SOURCE_TYPE);
            task.setSourceKey(row.sourceKey());
            PprTask saved = taskRepository.save(task);
            auditBuilderService.log("ppr_task", saved.getId().toString(), AuditAction.CREATE,
                    AuditModule.PPR_TASK, "RCM task confirmed from preview", null, saved);
            createdCodes.add(saved.getCode());
        }
        int duplicateCount = preview.duplicates() + Math.max(0, preview.tasksToCreate() - createdCodes.size());
        return new RcmAutoPlanConfirmResult(preview.candidates(), createdCodes.size(), duplicateCount,
                preview.conflicts(), preview.skipped(), preview.fingerprint(), createdCodes);
    }

    @Deprecated
    public AutoPlanResult generate(int riskThreshold, UUID planId) {
        throw RestException.conflict("Preview and explicit confirmation are required", "RCM_PREVIEW_REQUIRED");
    }

    private boolean fingerprintsMatch(String actual, String supplied) {
        if (actual == null || supplied == null) return false;
        return MessageDigest.isEqual(actual.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8));
    }

    private String safeCode(String code) {
        return code == null || code.isBlank() ? "EQUIPMENT" : code.replaceAll("[^A-Za-z0-9_-]", "-");
    }

    public record AutoPlanResult(int candidates, int tasksCreated, int skipped, List<String> createdCodes) {}
}
