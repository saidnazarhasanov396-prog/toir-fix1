package com.toir.ai.repair;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.toir.entity.ai.AiJobContext;
import com.toir.repository.ai.AiJobContextRepository;
import com.toir.security.ScopeAccessService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

@Service
public class AiRepairGatewayHook {

    private static final Logger log = LoggerFactory.getLogger(AiRepairGatewayHook.class);

    private final AiRepairRequestOrchestrator orchestrator;
    private final AiJobContextRepository jobContextRepository;
    private final ScopeAccessService scopeAccessService;
    private final ObjectMapper objectMapper;

    public AiRepairGatewayHook(
            AiRepairRequestOrchestrator orchestrator,
            AiJobContextRepository jobContextRepository,
            ScopeAccessService scopeAccessService,
            ObjectMapper objectMapper
    ) {
        this.orchestrator = orchestrator;
        this.jobContextRepository = jobContextRepository;
        this.scopeAccessService = scopeAccessService;
        this.objectMapper = objectMapper;
    }

    public JsonNode afterForward(
            AiRepairKind kind,
            UUID equipmentId,
            UUID workOrderId,
            JsonNode payload,
            byte[] mediaBytes
    ) {
        AiRepairApplyResult result = safeApply(
                kind,
                equipmentId,
                workOrderId,
                scopeAccessService.currentUserIdOrNull(),
                payload,
                sha256(mediaBytes),
                null
        );
        return enrich(payload, result);
    }

    @Transactional
    public void rememberJob(
            UUID jobId,
            AiRepairKind kind,
            UUID equipmentId,
            UUID workOrderId,
            byte[] mediaBytes
    ) {
        if (jobId == null || kind == null) {
            return;
        }
        try {
            if (jobContextRepository.findByJobIdAndIsDeletedFalse(jobId).isPresent()) {
                return;
            }
            AiJobContext context = new AiJobContext();
            context.setJobId(jobId);
            context.setKind(kind);
            context.setEquipmentId(equipmentId);
            context.setWorkOrderId(workOrderId);
            context.setReporterId(scopeAccessService.currentUserIdOrNull());
            context.setMediaSha256(sha256(mediaBytes));
            jobContextRepository.save(context);
        } catch (RuntimeException exception) {
            log.warn("Failed to store AI job context {}", jobId, exception);
        }
    }

    public JsonNode afterJobPayload(UUID jobId, JsonNode payload) {
        if (jobId == null || payload == null) {
            return payload;
        }
        try {
            if (!isReadyForRepair(payload)) {
                return payload;
            }
            AiJobContext context = jobContextRepository.findByJobIdAndIsDeletedFalse(jobId).orElse(null);
            if (context == null) {
                return payload;
            }
            JsonNode conclusionPayload = extractConclusionPayload(payload);
            AiRepairApplyResult result = safeApply(
                    context.getKind(),
                    context.getEquipmentId(),
                    context.getWorkOrderId(),
                    context.getReporterId(),
                    conclusionPayload,
                    context.getMediaSha256(),
                    jobId
            );
            context.setProcessedAt(Instant.now());
            context.setRepairRequestId(result.repairRequestId());
            context.setAction(result.action());
            context.setSkippedReason(result.skippedReason());
            jobContextRepository.save(context);
            return enrich(payload, result);
        } catch (RuntimeException exception) {
            log.warn("AI job repair hook failed for {}", jobId, exception);
            return payload;
        }
    }

    private AiRepairApplyResult safeApply(
            AiRepairKind kind,
            UUID equipmentId,
            UUID workOrderId,
            UUID reporterId,
            JsonNode payload,
            String mediaSha256,
            UUID jobId
    ) {
        try {
            return orchestrator.apply(kind, equipmentId, workOrderId, reporterId, payload, mediaSha256, jobId);
        } catch (RuntimeException exception) {
            log.warn("AI repair request hook failed", exception);
            return AiRepairApplyResult.skipped(AiRepairSkipReason.CREATE_FAILED);
        }
    }

    JsonNode enrich(JsonNode payload, AiRepairApplyResult result) {
        ObjectNode root;
        if (payload != null && payload.isObject()) {
            root = ((ObjectNode) payload).deepCopy();
        } else {
            root = objectMapper.createObjectNode();
            if (payload != null && !payload.isNull()) {
                root.set("result", payload);
            }
        }
        ObjectNode toir = objectMapper.createObjectNode();
        toir.put("action", result.action().name());
        if (result.repairRequestId() != null) {
            toir.put("repairRequestId", result.repairRequestId().toString());
        }
        if (StringUtils.hasText(result.repairRequestNumber())) {
            toir.put("repairRequestNumber", result.repairRequestNumber());
        }
        if (result.skippedReason() != null) {
            toir.put("skippedReason", result.skippedReason().name());
        }
        root.set("toir", toir);
        return root;
    }

    static boolean isReadyForRepair(JsonNode payload) {
        if (payload == null || payload.isNull()) {
            return false;
        }
        String status = statusOf(payload);
        if (status != null) {
            if (status.equals("FAILED") || status.equals("ERROR") || status.equals("CANCELLED")) {
                return false;
            }
            if (status.equals("PENDING") || status.equals("QUEUED") || status.equals("RUNNING")
                    || status.equals("PROCESSING") || status.equals("ACCEPTED")) {
                return false;
            }
            if (status.equals("COMPLETED") || status.equals("SUCCESS") || status.equals("DONE")) {
                return true;
            }
        }
        return looksLikeConclusion(payload);
    }

    static JsonNode extractConclusionPayload(JsonNode payload) {
        if (payload == null || !payload.isObject()) {
            return payload;
        }
        JsonNode result = payload.get("result");
        if (result != null && !result.isNull() && (result.isObject() || result.isArray())) {
            return result;
        }
        JsonNode callback = payload.get("callback");
        if (callback != null && callback.isObject()) {
            JsonNode callbackResult = callback.get("result");
            if (callbackResult != null && !callbackResult.isNull()) {
                return callbackResult;
            }
            return callback;
        }
        return payload;
    }

    private static boolean looksLikeConclusion(JsonNode payload) {
        if (payload == null || !payload.isObject()) {
            return false;
        }
        return payload.has("inspection_report")
                || payload.has("inspectionReport")
                || payload.has("defects")
                || payload.has("recommendations")
                || payload.has("cause")
                || payload.has("repair_action")
                || payload.has("repairAction");
    }

    private static String statusOf(JsonNode payload) {
        if (payload == null || !payload.isObject()) {
            return null;
        }
        JsonNode status = payload.get("status");
        if (status == null || status.isNull() || !status.isTextual()) {
            return null;
        }
        String value = status.asText();
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : null;
    }

    static String sha256(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            return HexFormat.of().formatHex(Integer.toHexString(bytes.length).getBytes(StandardCharsets.UTF_8));
        }
    }
}
