package com.toir.ai.repair;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toir.entity.ai.AiAnalysisRun;
import com.toir.entity.defects.Defect;
import com.toir.entity.repair.RepairRequest;
import com.toir.enums.RequestSource;
import com.toir.enums.RequestStatus;
import com.toir.exception.RestException;
import com.toir.repository.ai.AiAnalysisRunRepository;
import com.toir.repository.defects.DefectRepository;
import com.toir.repository.repair.RepairRequestRepository;
import com.toir.service.repair.RepairRequestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class AiRepairRequestOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AiRepairRequestOrchestrator.class);
    private static final DateTimeFormatter NUMBER_DATE = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);
    private static final Set<RequestStatus> ACTIVE_STATUSES = EnumSet.of(
            RequestStatus.OPEN,
            RequestStatus.REGISTERED,
            RequestStatus.IN_REVIEW,
            RequestStatus.NEEDS_CLARIFICATION,
            RequestStatus.APPROVED,
            RequestStatus.ASSIGNED,
            RequestStatus.IN_PROGRESS
    );
    private static final Set<RequestStatus> EDITABLE_STATUSES = EnumSet.of(
            RequestStatus.OPEN,
            RequestStatus.REGISTERED,
            RequestStatus.IN_REVIEW,
            RequestStatus.NEEDS_CLARIFICATION
    );
    private static final Set<RequestStatus> APPEND_STATUSES = EnumSet.of(
            RequestStatus.APPROVED,
            RequestStatus.ASSIGNED,
            RequestStatus.IN_PROGRESS
    );

    private final AiRepairRequestProperties properties;
    private final AiRepairConclusionParser parser;
    private final RepairRequestService repairRequestService;
    private final RepairRequestRepository repairRequestRepository;
    private final DefectRepository defectRepository;
    private final AiAnalysisRunRepository analysisRunRepository;
    private final ObjectMapper objectMapper;

    public AiRepairRequestOrchestrator(
            AiRepairRequestProperties properties,
            AiRepairConclusionParser parser,
            RepairRequestService repairRequestService,
            RepairRequestRepository repairRequestRepository,
            DefectRepository defectRepository,
            AiAnalysisRunRepository analysisRunRepository,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.parser = parser;
        this.repairRequestService = repairRequestService;
        this.repairRequestRepository = repairRequestRepository;
        this.defectRepository = defectRepository;
        this.analysisRunRepository = analysisRunRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AiRepairApplyResult apply(
            AiRepairKind kind,
            UUID equipmentId,
            UUID workOrderId,
            UUID reporterId,
            JsonNode payload,
            String mediaSha256,
            UUID jobId
    ) {
        if (kind == null) {
            return persist(kind, equipmentId, workOrderId, reporterId, payload, mediaSha256, jobId,
                    AiRepairApplyResult.skipped(AiRepairSkipReason.KIND_NOT_APPLICABLE), null);
        }
        if (!properties.isEnabled()) {
            return persist(kind, equipmentId, workOrderId, reporterId, payload, mediaSha256, jobId,
                    AiRepairApplyResult.skipped(AiRepairSkipReason.FEATURE_DISABLED), null);
        }
        if (jobId != null) {
            var existingJob = analysisRunRepository.findByJobIdAndIsDeletedFalse(jobId);
            if (existingJob.isPresent()) {
                AiAnalysisRun run = existingJob.get();
                String number = null;
                if (run.getRepairRequestId() != null) {
                    number = repairRequestRepository.findByIdAndIsDeletedFalse(run.getRepairRequestId())
                            .map(RepairRequest::getNumber)
                            .orElse(null);
                }
                return new AiRepairApplyResult(run.getAction(), run.getRepairRequestId(), number, run.getSkippedReason());
            }
        }
        if (equipmentId == null) {
            return persist(kind, null, workOrderId, reporterId, payload, mediaSha256, jobId,
                    AiRepairApplyResult.skipped(AiRepairSkipReason.EQUIPMENT_MISSING), null);
        }
        if (reporterId == null) {
            return persist(kind, equipmentId, workOrderId, null, payload, mediaSha256, jobId,
                    AiRepairApplyResult.skipped(AiRepairSkipReason.REPORTER_MISSING), null);
        }
        if (StringUtils.hasText(mediaSha256)) {
            var existingMedia = analysisRunRepository.findByEquipmentMedia(equipmentId, mediaSha256, kind)
                    .stream()
                    .filter(run -> run.getRepairRequestId() != null)
                    .findFirst();
            if (existingMedia.isPresent()) {
                AiAnalysisRun run = existingMedia.get();
                RepairRequest existing = repairRequestRepository.findByIdAndIsDeletedFalse(run.getRepairRequestId())
                        .orElse(null);
                return persist(kind, equipmentId, workOrderId, reporterId, payload, mediaSha256, jobId,
                        new AiRepairApplyResult(
                                run.getAction() == AiRepairAction.SKIPPED ? AiRepairAction.SKIPPED : AiRepairAction.UPDATED,
                                run.getRepairRequestId(),
                                existing != null ? existing.getNumber() : null,
                                run.getAction() == AiRepairAction.SKIPPED ? run.getSkippedReason() : null
                        ),
                        existingMedia.get().getProblemKey());
            }
        }

        AiRepairConclusion conclusion = parser.parse(kind, payload);
        if (conclusion.insufficientMedia() && !conclusion.broken()) {
            return persist(kind, equipmentId, workOrderId, reporterId, payload, mediaSha256, jobId,
                    AiRepairApplyResult.skipped(AiRepairSkipReason.INSUFFICIENT_MEDIA), conclusion.problemKey());
        }
        if (!conclusion.broken()) {
            return persist(kind, equipmentId, workOrderId, reporterId, payload, mediaSha256, jobId,
                    AiRepairApplyResult.skipped(AiRepairSkipReason.NO_REPAIR_CONCLUSION), conclusion.problemKey());
        }

        try {
            List<RepairRequest> active = repairRequestRepository
                    .findByEquipmentIdAndStatusInAndIsDeletedFalse(equipmentId, ACTIVE_STATUSES);
            List<UUID> ids = active.stream().map(RepairRequest::getId).toList();
            List<Defect> linkedDefects = ids.isEmpty()
                    ? List.of()
                    : defectRepository.findAllByRepairRequestIdInAndIsDeletedFalseOrderByUpdatedAtDesc(ids);
            RepairRequest match = AiRepairProblemMatcher.findMatch(active, linkedDefects, conclusion);
            AiRepairApplyResult result;
            if (match == null) {
                result = create(equipmentId, reporterId, conclusion);
            } else if (EDITABLE_STATUSES.contains(match.getStatus())) {
                result = update(match.getId(), conclusion, false);
            } else if (APPEND_STATUSES.contains(match.getStatus())) {
                result = update(match.getId(), conclusion, true);
            } else {
                result = create(equipmentId, reporterId, conclusion);
            }
            return persist(kind, equipmentId, workOrderId, reporterId, payload, mediaSha256, jobId,
                    result, conclusion.problemKey());
        } catch (RestException exception) {
            if (exception.getStatus() == HttpStatus.BAD_REQUEST
                    && exception.getMessage() != null
                    && exception.getMessage().toLowerCase().contains("decommission")) {
                return persist(kind, equipmentId, workOrderId, reporterId, payload, mediaSha256, jobId,
                        AiRepairApplyResult.skipped(AiRepairSkipReason.EQUIPMENT_NOT_OPERATIONAL),
                        conclusion.problemKey());
            }
            log.warn("AI repair request apply failed: {}", exception.getMessage());
            return persist(kind, equipmentId, workOrderId, reporterId, payload, mediaSha256, jobId,
                    AiRepairApplyResult.skipped(AiRepairSkipReason.CREATE_FAILED), conclusion.problemKey());
        } catch (RuntimeException exception) {
            log.warn("AI repair request apply failed", exception);
            return persist(kind, equipmentId, workOrderId, reporterId, payload, mediaSha256, jobId,
                    AiRepairApplyResult.skipped(AiRepairSkipReason.CREATE_FAILED), conclusion.problemKey());
        }
    }

    private AiRepairApplyResult create(UUID equipmentId, UUID reporterId, AiRepairConclusion conclusion) {
        var request = new com.toir.dto.repairrequest.RepairRequestRequest(
                nextNumber(),
                conclusion.title(),
                conclusion.description(),
                null,
                null,
                conclusion.defects().stream()
                        .map(defect -> new com.toir.dto.repairrequest.RepairRequestRequest.InlineDefectRequest(
                                defect.title(),
                                defect.description(),
                                defect.category(),
                                defect.severity(),
                                defect.failureReason(),
                                defect.rootCause()
                        ))
                        .toList(),
                equipmentId,
                null,
                null,
                reporterId,
                conclusion.priority(),
                conclusion.criticality(),
                RequestSource.AI,
                null,
                null,
                null,
                null
        );
        var created = repairRequestService.create(request);
        repairRequestService.assignAiProblemKey(created.id(), conclusion.problemKey());
        return AiRepairApplyResult.applied(AiRepairAction.CREATED, created.id(), created.number());
    }

    private AiRepairApplyResult update(UUID repairRequestId, AiRepairConclusion conclusion, boolean appendOnly) {
        var updated = repairRequestService.applyAiConclusion(repairRequestId, conclusion, appendOnly);
        return AiRepairApplyResult.applied(
                appendOnly ? AiRepairAction.APPENDED : AiRepairAction.UPDATED,
                updated.id(),
                updated.number()
        );
    }

    private String nextNumber() {
        for (int attempt = 0; attempt < 8; attempt++) {
            String number = "AI-RR-" + NUMBER_DATE.format(Instant.now()) + "-"
                    + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
            if (!repairRequestRepository.existsByNumberAndIsDeletedFalse(number)) {
                return number;
            }
        }
        return "AI-RR-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
    }

    private AiRepairApplyResult persist(
            AiRepairKind kind,
            UUID equipmentId,
            UUID workOrderId,
            UUID reporterId,
            JsonNode payload,
            String mediaSha256,
            UUID jobId,
            AiRepairApplyResult result,
            String problemKey
    ) {
        AiAnalysisRun run = new AiAnalysisRun();
        run.setKind(kind);
        run.setEquipmentId(equipmentId);
        run.setWorkOrderId(workOrderId);
        run.setReporterId(reporterId);
        run.setJobId(jobId);
        run.setMediaSha256(mediaSha256);
        run.setProblemKey(problemKey);
        run.setAction(result.action());
        run.setSkippedReason(result.skippedReason());
        run.setRepairRequestId(result.repairRequestId());
        run.setPayload(writePayload(payload));
        try {
            analysisRunRepository.save(run);
            return result;
        } catch (DataIntegrityViolationException exception) {
            if (jobId == null) {
                return result;
            }
            return analysisRunRepository.findByJobIdAndIsDeletedFalse(jobId)
                    .map(existing -> new AiRepairApplyResult(
                            existing.getAction(),
                            existing.getRepairRequestId(),
                            result.repairRequestNumber(),
                            existing.getSkippedReason()
                    ))
                    .orElse(result);
        }
    }

    private String writePayload(JsonNode payload) {
        if (payload == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception exception) {
            return null;
        }
    }
}
