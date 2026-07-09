package com.toir.dto.defect;

import com.toir.entity.defects.Defect;
import com.toir.enums.DefectStatus;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record DefectDto(
        UUID id,
        String code,
        String title,
        String description,
        UUID equipmentId,
        UUID equipmentNodeId,
        UUID repairRequestId,
        String category,
        String severity,
        String failureReason,
        String rootCause,
        DefectStatus status,
        Instant detectedAt,
        Instant resolvedAt,
        int recurrenceCount,
        String titleKey,
        Map<String, Object> titleParams,
        String messageKey,
        Map<String, Object> messageParams
) {
    public DefectDto(UUID id,
                     String code,
                     String title,
                     String description,
                     UUID equipmentId,
                     UUID repairRequestId,
                     String category,
                     String severity,
                     String failureReason,
                     String rootCause,
                     DefectStatus status,
                     Instant detectedAt,
                     Instant resolvedAt,
                     int recurrenceCount) {
        this(id, code, title, description, equipmentId, null, repairRequestId, category, severity,
                failureReason, rootCause, status, detectedAt, resolvedAt, recurrenceCount,
                null, null, null, null);
    }

    private static final String INSPECTION_TITLE_PREFIX = "Inspection failure: ";
    private static final String NO_COMMENT_FALLBACK = "Checkpoint failed and requires maintenance triage.";
    private static final Pattern INSPECTION_DESCRIPTION_PATTERN = Pattern.compile(
            "^Inspection FAIL triage\\. roundId=([^;]+); checkpointId=([^;]+); checkpoint=(.*?)\\. (.*)$",
            Pattern.DOTALL);

    public static DefectDto from(Defect d) {
        Map<String, Object> titleParams = null;
        String titleKey = null;
        String messageKey = null;
        Map<String, Object> messageParams = null;

        String title = d.getTitle();
        if (title != null && title.startsWith(INSPECTION_TITLE_PREFIX)) {
            titleKey = "defects.inspectionAutoDefect.title";
            titleParams = Map.of("checkpointTitle", title.substring(INSPECTION_TITLE_PREFIX.length()));

            Matcher matcher = d.getDescription() == null
                    ? null
                    : INSPECTION_DESCRIPTION_PATTERN.matcher(d.getDescription());
            if (matcher != null && matcher.matches()) {
                String comment = matcher.group(4);
                Map<String, Object> params = new LinkedHashMap<>();
                params.put("roundId", matcher.group(1));
                params.put("checkpointId", matcher.group(2));
                params.put("checkpointTitle", matcher.group(3));
                if (NO_COMMENT_FALLBACK.equals(comment)) {
                    messageKey = "defects.inspectionAutoDefect.messageNoComment";
                } else {
                    messageKey = "defects.inspectionAutoDefect.messageWithComment";
                    params.put("comment", comment);
                }
                messageParams = params;
            }
        }

        return new DefectDto(
                d.getId(), d.getCode(), d.getTitle(), d.getDescription(),
                d.getEquipmentId(), d.getEquipmentNodeId(), d.getRepairRequestId(),
                d.getCategory(), d.getSeverity(), d.getFailureReason(), d.getRootCause(),
                d.getStatus(), d.getDetectedAt(), d.getResolvedAt(), d.getRecurrenceCount(),
                titleKey, titleParams, messageKey, messageParams
        );
    }
}
