package com.toir.ai.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.toir.ai.gateway.dto.WorkOrderDraftTextRequest;
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
    public static final String PATH_HEALTH = "/health";

    private final ToirAiGatewayProperties properties;
    private final ToirAiGatewayClient client;

    public ToirAiGatewayService(ToirAiGatewayProperties properties, ToirAiGatewayClient client) {
        this.properties = properties;
        this.client = client;
    }

    public JsonNode generateDraftFromText(WorkOrderDraftTextRequest request) {
        requireEnabled();
        return execute(() -> client.postJson(PATH_DRAFT_TEXT, request));
    }

    public JsonNode generateDraftFromAudio(UUID equipmentId, MultipartFile audio) {
        requireEnabled();
        byte[] bytes = readUpload(audio, "audio");
        Map<String, String> fields = Map.of("equipment_id", equipmentId.toString());
        return execute(() -> client.postMultipart(
                PATH_DRAFT_AUDIO,
                ToirAiGatewayClient.multipartFile("audio", bytes, audio.getOriginalFilename(), fields)
        ));
    }

    public JsonNode visualInspectionVideo(MultipartFile video) {
        requireEnabled();
        byte[] bytes = readUpload(video, "video");
        return execute(() -> client.postMultipart(
                PATH_VISUAL_VIDEO,
                ToirAiGatewayClient.multipartFile("video", bytes, video.getOriginalFilename(), Map.of())
        ));
    }

    public JsonNode visualInspectionImage(MultipartFile image) {
        requireEnabled();
        byte[] bytes = readUpload(image, "image");
        return execute(() -> client.postMultipart(
                PATH_VISUAL_IMAGE,
                ToirAiGatewayClient.multipartFile("image", bytes, image.getOriginalFilename(), Map.of())
        ));
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
        return execute(() -> client.getJson(path, Map.of()));
    }

    public JsonNode equipmentCauseRepairAnalysis(UUID equipmentId, UUID workOrderId) {
        requireEnabled();
        String path = PATH_CAUSE_REPAIR.replace("{equipment_id}", equipmentId.toString());
        Map<String, Object> query = new LinkedHashMap<>();
        if (workOrderId != null) {
            query.put("work_order_id", workOrderId);
        }
        return execute(() -> client.getJson(path, query));
    }

    public JsonNode health() {
        requireEnabled();
        return execute(() -> client.getJson(PATH_HEALTH, Map.of()));
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
