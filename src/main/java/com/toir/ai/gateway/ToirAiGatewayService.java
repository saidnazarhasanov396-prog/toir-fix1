package com.toir.ai.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.toir.ai.gateway.dto.WorkOrderDraftTextRequest;
import com.toir.ai.gateway.dto.WorkOrderTextJobRequest;
import com.toir.ai.repair.AiRepairGatewayHook;
import com.toir.ai.repair.AiRepairKind;
import com.toir.exception.RestException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class ToirAiGatewayService {

    public static final String PATH_DRAFT_TEXT = "/ai/work-order/generate_draft/text";
    public static final String PATH_DRAFT_AUDIO = "/ai/work-order/generate_draft/audio";
    public static final String PATH_VISUAL_VIDEO = "/ai/visual_inspection/visual_inspection";
    public static final String PATH_VISUAL_IMAGE = "/ai/visual_inspection/visual_inspection/image";
    public static final String PATH_FAILURE_CATEGORIES = "/ai/recurrent_failure_analysis/equipment-categories";
    public static final String PATH_FAILURE_CATEGORIES_STREAM =
            "/ai/recurrent_failure_analysis/equipment-categories/stream";
    public static final String PATH_FAILURE_EVIDENCE =
            "/ai/recurrent_failure_analysis/equipment/{equipment_id}/failure-evidence";
    public static final String PATH_CAUSE_REPAIR =
            "/ai/recurrent_failure_analysis/equipment/{equipment_id}/cause-repair-analysis";
    public static final String PATH_MAINTENANCE_KIND_INTERVALS =
            "/ai/technical-schedule-optimizer/equipment/{equipment_id}/maintenance-kind-intervals";
    public static final String PATH_HEALTH = "/health";

    public static final String PATH_JOB_DRAFT_TEXT = "/ai/jobs/work-order/generate-draft/text";
    public static final String PATH_JOB_DRAFT_AUDIO = "/ai/jobs/work-order/generate-draft/audio";
    public static final String PATH_JOB_VISUAL_VIDEO = "/ai/jobs/visual-inspection/video";
    public static final String PATH_JOB_VISUAL_IMAGE = "/ai/jobs/visual-inspection/image";
    public static final String PATH_JOB_FAILURE_CATEGORIES = "/ai/jobs/recurrent-failure/equipment-categories";
    public static final String PATH_JOB_FAILURE_EVIDENCE =
            "/ai/jobs/recurrent-failure/equipment/{equipment_id}/failure-evidence";
    public static final String PATH_JOB_CAUSE_REPAIR =
            "/ai/jobs/recurrent-failure/equipment/{equipment_id}/cause-repair-analysis";
    public static final String PATH_JOB_STATUS = "/ai/jobs/{job_id}";

    private final ToirAiGatewayProperties properties;
    private final ToirAiGatewayClient client;
    private final ObjectMapper objectMapper;
    private final ToirAiWebhookSignatureVerifier signatureVerifier;
    private final ToirAiJobCallbackStore callbackStore;
    private final AiRepairGatewayHook repairGatewayHook;

    public ToirAiGatewayService(
            ToirAiGatewayProperties properties,
            ToirAiGatewayClient client,
            ObjectMapper objectMapper,
            ToirAiWebhookSignatureVerifier signatureVerifier,
            ToirAiJobCallbackStore callbackStore,
            AiRepairGatewayHook repairGatewayHook
    ) {
        this.properties = properties;
        this.client = client;
        this.objectMapper = objectMapper;
        this.signatureVerifier = signatureVerifier;
        this.callbackStore = callbackStore;
        this.repairGatewayHook = repairGatewayHook;
    }

    public JsonNode generateDraftFromText(WorkOrderDraftTextRequest request) {
        requireEnabled();
        JsonNode payload = execute(() -> client.postJson(PATH_DRAFT_TEXT, request));
        return repairGatewayHook.afterForward(
                AiRepairKind.WORK_ORDER_DRAFT,
                request.equipmentId(),
                null,
                payload,
                null
        );
    }

    public JsonNode generateDraftFromAudio(UUID equipmentId, MultipartFile audio) {
        requireEnabled();
        byte[] bytes = readUpload(audio, "audio");
        Map<String, String> fields = Map.of("equipment_id", equipmentId.toString());
        JsonNode payload = execute(() -> client.postMultipart(
                PATH_DRAFT_AUDIO,
                ToirAiGatewayClient.multipartFile("audio", bytes, audio.getOriginalFilename(), fields)
        ));
        return repairGatewayHook.afterForward(
                AiRepairKind.WORK_ORDER_DRAFT,
                equipmentId,
                null,
                payload,
                bytes
        );
    }

    public JsonNode visualInspectionVideo(MultipartFile video, UUID equipmentId, UUID workOrderId) {
        requireEnabled();
        byte[] bytes = readUpload(video, "video");
        JsonNode payload = execute(() -> client.postMultipart(
                PATH_VISUAL_VIDEO,
                ToirAiGatewayClient.multipartFile("video", bytes, video.getOriginalFilename(), Map.of())
        ));
        return repairGatewayHook.afterForward(
                AiRepairKind.VISUAL_INSPECTION,
                equipmentId,
                workOrderId,
                payload,
                bytes
        );
    }

    public JsonNode visualInspectionImage(MultipartFile image, UUID equipmentId, UUID workOrderId) {
        requireEnabled();
        byte[] bytes = readUpload(image, "image");
        JsonNode payload = execute(() -> client.postMultipart(
                PATH_VISUAL_IMAGE,
                ToirAiGatewayClient.multipartFile("image", bytes, image.getOriginalFilename(), Map.of())
        ));
        return repairGatewayHook.afterForward(
                AiRepairKind.VISUAL_INSPECTION,
                equipmentId,
                workOrderId,
                payload,
                bytes
        );
    }

    public JsonNode equipmentFailureCategories(UUID equipmentId) {
        requireEnabled();
        Map<String, Object> query = new LinkedHashMap<>();
        if (equipmentId != null) {
            query.put("equipment_id", equipmentId);
        }
        return execute(() -> client.getJson(PATH_FAILURE_CATEGORIES, query));
    }

    public ResponseEntity<byte[]> equipmentFailureCategoriesStream(UUID equipmentId) {
        requireEnabled();
        Map<String, Object> query = new LinkedHashMap<>();
        if (equipmentId != null) {
            query.put("equipment_id", equipmentId);
        }
        return execute(() -> {
            ResponseEntity<byte[]> upstream = client.getRaw(PATH_FAILURE_CATEGORIES_STREAM, query);
            MediaType contentType = upstream.getHeaders().getContentType();
            return ResponseEntity.status(upstream.getStatusCode())
                    .contentType(contentType != null ? contentType : MediaType.APPLICATION_JSON)
                    .body(upstream.getBody());
        });
    }

    public JsonNode equipmentFailureEvidence(UUID equipmentId) {
        requireEnabled();
        String path = PATH_FAILURE_EVIDENCE.replace("{equipment_id}", equipmentId.toString());
        JsonNode payload = execute(() -> client.getJson(path, Map.of()));
        return repairGatewayHook.afterForward(
                AiRepairKind.FAILURE_EVIDENCE,
                equipmentId,
                null,
                payload,
                null
        );
    }

    public JsonNode equipmentCauseRepairAnalysis(UUID equipmentId, UUID workOrderId) {
        requireEnabled();
        String path = PATH_CAUSE_REPAIR.replace("{equipment_id}", equipmentId.toString());
        Map<String, Object> query = new LinkedHashMap<>();
        if (workOrderId != null) {
            query.put("work_order_id", workOrderId);
        }
        JsonNode payload = execute(() -> client.getJson(path, query));
        return repairGatewayHook.afterForward(
                AiRepairKind.CAUSE_REPAIR,
                equipmentId,
                workOrderId,
                payload,
                null
        );
    }

    public JsonNode maintenanceKindIntervals(UUID equipmentId) {
        requireEnabled();
        String path = PATH_MAINTENANCE_KIND_INTERVALS.replace("{equipment_id}", equipmentId.toString());
        return execute(() -> client.getJson(path, Map.of()));
    }

    public JsonNode health() {
        requireEnabled();
        return execute(() -> client.getJson(PATH_HEALTH, Map.of()));
    }

    public ResponseEntity<JsonNode> enqueueWorkOrderText(WorkOrderTextJobRequest request) {
        requireEnabled();
        ObjectNode body = objectMapper.valueToTree(request);
        ensureWebhookUrl(body);
        return rememberJob(
                execute(() -> rewriteAccepted(client.postJsonEntity(PATH_JOB_DRAFT_TEXT, body))),
                AiRepairKind.WORK_ORDER_DRAFT,
                request.equipmentId(),
                null,
                null
        );
    }

    public ResponseEntity<JsonNode> enqueueWorkOrderText(JsonNode request) {
        requireEnabled();
        ObjectNode body = request != null && request.isObject()
                ? ((ObjectNode) request).deepCopy()
                : objectMapper.createObjectNode();
        ensureWebhookUrl(body);
        UUID equipmentId = parseUuid(body.get("equipment_id"));
        if (equipmentId == null) {
            equipmentId = parseUuid(body.get("equipmentId"));
        }
        return rememberJob(
                execute(() -> rewriteAccepted(client.postJsonEntity(PATH_JOB_DRAFT_TEXT, body))),
                AiRepairKind.WORK_ORDER_DRAFT,
                equipmentId,
                null,
                null
        );
    }

    public ResponseEntity<JsonNode> enqueueWorkOrderAudio(UUID equipmentId, MultipartFile audio, String webhookUrl) {
        requireEnabled();
        byte[] bytes = readUpload(audio, "audio");
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("equipment_id", equipmentId.toString());
        fields.put("webhook_url", resolveWebhookUrl(webhookUrl));
        return rememberJob(
                execute(() -> rewriteAccepted(client.postMultipartEntity(
                        PATH_JOB_DRAFT_AUDIO,
                        ToirAiGatewayClient.multipartFile("audio", bytes, audio.getOriginalFilename(), fields)
                ))),
                AiRepairKind.WORK_ORDER_DRAFT,
                equipmentId,
                null,
                bytes
        );
    }

    public ResponseEntity<JsonNode> enqueueVisualInspectionVideo(
            MultipartFile video,
            String webhookUrl,
            UUID equipmentId,
            UUID workOrderId
    ) {
        requireEnabled();
        byte[] bytes = readUpload(video, "video");
        Map<String, String> fields = Map.of("webhook_url", resolveWebhookUrl(webhookUrl));
        return rememberJob(
                execute(() -> rewriteAccepted(client.postMultipartEntity(
                        PATH_JOB_VISUAL_VIDEO,
                        ToirAiGatewayClient.multipartFile("video", bytes, video.getOriginalFilename(), fields)
                ))),
                AiRepairKind.VISUAL_INSPECTION,
                equipmentId,
                workOrderId,
                bytes
        );
    }

    public ResponseEntity<JsonNode> enqueueVisualInspectionImage(
            MultipartFile image,
            String webhookUrl,
            UUID equipmentId,
            UUID workOrderId
    ) {
        requireEnabled();
        byte[] bytes = readUpload(image, "image");
        Map<String, String> fields = Map.of("webhook_url", resolveWebhookUrl(webhookUrl));
        return rememberJob(
                execute(() -> rewriteAccepted(client.postMultipartEntity(
                        PATH_JOB_VISUAL_IMAGE,
                        ToirAiGatewayClient.multipartFile("image", bytes, image.getOriginalFilename(), fields)
                ))),
                AiRepairKind.VISUAL_INSPECTION,
                equipmentId,
                workOrderId,
                bytes
        );
    }

    public ResponseEntity<JsonNode> enqueueRecurrentFailureCategories(JsonNode request) {
        requireEnabled();
        ObjectNode body = request != null && request.isObject()
                ? ((ObjectNode) request).deepCopy()
                : objectMapper.createObjectNode();
        ensureWebhookUrl(body);
        return execute(() -> rewriteAccepted(client.postJsonEntity(PATH_JOB_FAILURE_CATEGORIES, body)));
    }

    public ResponseEntity<JsonNode> enqueueRecurrentFailureEvidence(UUID equipmentId, JsonNode request) {
        requireEnabled();
        String path = PATH_JOB_FAILURE_EVIDENCE.replace("{equipment_id}", equipmentId.toString());
        ObjectNode body = request != null && request.isObject()
                ? ((ObjectNode) request).deepCopy()
                : objectMapper.createObjectNode();
        ensureWebhookUrl(body);
        return rememberJob(
                execute(() -> rewriteAccepted(client.postJsonEntity(path, body))),
                AiRepairKind.FAILURE_EVIDENCE,
                equipmentId,
                parseUuid(body.get("work_order_id")),
                null
        );
    }

    public ResponseEntity<JsonNode> enqueueRecurrentFailureCauseRepair(UUID equipmentId, JsonNode request) {
        requireEnabled();
        String path = PATH_JOB_CAUSE_REPAIR.replace("{equipment_id}", equipmentId.toString());
        ObjectNode body = request != null && request.isObject()
                ? ((ObjectNode) request).deepCopy()
                : objectMapper.createObjectNode();
        ensureWebhookUrl(body);
        UUID workOrderId = parseUuid(body.get("work_order_id"));
        if (workOrderId == null) {
            workOrderId = parseUuid(body.get("workOrderId"));
        }
        return rememberJob(
                execute(() -> rewriteAccepted(client.postJsonEntity(path, body))),
                AiRepairKind.CAUSE_REPAIR,
                equipmentId,
                workOrderId,
                null
        );
    }

    public ResponseEntity<JsonNode> getJobStatus(UUID jobId) {
        requireEnabled();
        String path = PATH_JOB_STATUS.replace("{job_id}", jobId.toString());
        return execute(() -> {
            JsonNode body = client.getJson(path, Map.of());
            JsonNode callback = callbackStore.get(jobId).orElse(null);
            JsonNode merged = body;
            if (callback != null && body != null && body.isObject()) {
                ObjectNode copy = ((ObjectNode) body).deepCopy();
                copy.set("callback", callback);
                merged = copy;
            }
            return ResponseEntity.ok(repairGatewayHook.afterJobPayload(jobId, merged));
        });
    }

    public ResponseEntity<Void> handleCallback(byte[] body, String... signatureHeaders) {
        if (!properties.hasWebhookSigningSecret()) {
            throw new RestException(
                    "AI job webhook signing secret is not configured",
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AI_GATEWAY_WEBHOOK_SECRET_MISSING"
            );
        }
        if (!signatureVerifier.verify(body, signatureHeaders)) {
            throw new RestException(
                    "Invalid AI job webhook signature",
                    HttpStatus.UNAUTHORIZED,
                    "AI_GATEWAY_WEBHOOK_SIGNATURE_INVALID"
            );
        }
        try {
            JsonNode payload = objectMapper.readTree(body == null ? new byte[0] : body);
            UUID jobId = extractJobId(payload);
            if (jobId == null) {
                throw RestException.badRequest("Callback payload missing job_id", "AI_GATEWAY_WEBHOOK_JOB_ID_MISSING");
            }
            callbackStore.put(jobId, payload);
            try {
                JsonNode enriched = repairGatewayHook.afterJobPayload(jobId, payload);
                callbackStore.put(jobId, enriched);
            } catch (RuntimeException exception) {
                // Keep the original callback even if repair-request side-effects fail.
            }
            return ResponseEntity.ok().build();
        } catch (RestException exception) {
            throw exception;
        } catch (IOException exception) {
            throw RestException.badRequest("Invalid callback JSON", "AI_GATEWAY_WEBHOOK_JSON_INVALID");
        }
    }

    private void ensureWebhookUrl(ObjectNode body) {
        JsonNode existing = body.get("webhook_url");
        if (existing == null || existing.isNull() || !StringUtils.hasText(existing.asText())) {
            body.put("webhook_url", resolveWebhookUrl(null));
        }
    }

    private String resolveWebhookUrl(String webhookUrl) {
        if (StringUtils.hasText(webhookUrl)) {
            return webhookUrl;
        }
        String configured = properties.getWebhookCallbackUrl();
        if (!StringUtils.hasText(configured)) {
            throw new RestException(
                    "AI job webhook callback URL is not configured",
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AI_GATEWAY_WEBHOOK_CALLBACK_MISSING"
            );
        }
        return configured;
    }

    private ResponseEntity<JsonNode> rewriteAccepted(ResponseEntity<JsonNode> upstream) {
        JsonNode body = upstream.getBody();
        if (body != null && body.isObject()) {
            ObjectNode rewritten = ((ObjectNode) body).deepCopy();
            JsonNode jobIdNode = rewritten.get("job_id");
            if (jobIdNode != null && !jobIdNode.isNull() && StringUtils.hasText(jobIdNode.asText())) {
                rewritten.put("status_url", "/api/v1/ai/jobs/" + jobIdNode.asText());
            }
            return ResponseEntity.status(upstream.getStatusCode()).body(rewritten);
        }
        return ResponseEntity.status(upstream.getStatusCode()).body(body);
    }

    private ResponseEntity<JsonNode> rememberJob(
            ResponseEntity<JsonNode> upstream,
            AiRepairKind kind,
            UUID equipmentId,
            UUID workOrderId,
            byte[] mediaBytes
    ) {
        UUID jobId = extractJobId(upstream.getBody());
        if (jobId != null) {
            repairGatewayHook.rememberJob(jobId, kind, equipmentId, workOrderId, mediaBytes);
        }
        return upstream;
    }

    private static UUID parseUuid(JsonNode node) {
        if (node == null || node.isNull() || !StringUtils.hasText(node.asText())) {
            return null;
        }
        try {
            return UUID.fromString(node.asText());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static UUID extractJobId(JsonNode payload) {
        if (payload == null || !payload.isObject()) {
            return null;
        }
        JsonNode jobIdNode = payload.get("job_id");
        if (jobIdNode == null || jobIdNode.isNull()) {
            jobIdNode = payload.get("jobId");
        }
        if (jobIdNode == null || jobIdNode.isNull() || !StringUtils.hasText(jobIdNode.asText())) {
            return null;
        }
        try {
            return UUID.fromString(jobIdNode.asText());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private void requireEnabled() {
        if (!properties.isEnabled()) {
            throw new RestException(
                    "AI gateway is disabled",
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AI_GATEWAY_DISABLED"
            );
        }
    }

    private byte[] readUpload(MultipartFile file, String fieldName) {
        if (file == null || file.isEmpty()) {
            throw RestException.badRequest(fieldName + " file is required", "AI_GATEWAY_UPLOAD_REQUIRED");
        }
        if (file.getSize() > properties.getMaxUploadBytes()) {
            throw new RestException(
                    fieldName + " exceeds configured upload limit",
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "AI_GATEWAY_UPLOAD_TOO_LARGE"
            );
        }
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw RestException.badRequest("Failed to read " + fieldName + " upload", "AI_GATEWAY_UPLOAD_READ_FAILED");
        }
    }

    private <T> T execute(AiCall<T> call) {
        try {
            return call.run();
        } catch (RestException exception) {
            throw exception;
        } catch (ResourceAccessException exception) {
            throw new RestException(
                    "AI service timed out or is unavailable",
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AI_GATEWAY_TIMEOUT"
            );
        } catch (RestClientResponseException exception) {
            throw mapUpstream(exception);
        } catch (RuntimeException exception) {
            throw new RestException(
                    "AI gateway request failed",
                    HttpStatus.BAD_GATEWAY,
                    "AI_GATEWAY_ERROR"
            );
        }
    }

    private RestException mapUpstream(RestClientResponseException exception) {
        int status = exception.getStatusCode().value();
        String body = exception.getResponseBodyAsString();
        String detail = extractDetail(body);
        String message = StringUtils.hasText(detail) ? detail : "AI service request failed";
        if (status == 413) {
            return new RestException(message, HttpStatus.PAYLOAD_TOO_LARGE, "AI_GATEWAY_UPLOAD_TOO_LARGE");
        }
        if (status == 415) {
            return new RestException(message, HttpStatus.UNSUPPORTED_MEDIA_TYPE, "AI_GATEWAY_UNSUPPORTED_MEDIA");
        }
        if (status == 422 || status == 400) {
            return new RestException(message, HttpStatus.BAD_REQUEST, "AI_GATEWAY_VALIDATION_ERROR");
        }
        if (status == 401 || status == 403) {
            return new RestException(message, HttpStatus.BAD_GATEWAY, "AI_GATEWAY_AUTH_FAILED");
        }
        if (status == 503 || status == 504) {
            return new RestException(message, HttpStatus.SERVICE_UNAVAILABLE, "AI_GATEWAY_UNAVAILABLE");
        }
        if (status >= 500) {
            return new RestException(message, HttpStatus.BAD_GATEWAY, "AI_GATEWAY_UPSTREAM_ERROR");
        }
        return new RestException(message, HttpStatus.BAD_GATEWAY, "AI_GATEWAY_HTTP_ERROR");
    }

    private static String extractDetail(String body) {
        if (!StringUtils.hasText(body)) {
            return null;
        }
        String trimmed = body.trim();
        if (trimmed.length() > 2000) {
            return trimmed.substring(0, 2000);
        }
        return trimmed;
    }

    @FunctionalInterface
    private interface AiCall<T> {
        T run();
    }
}
