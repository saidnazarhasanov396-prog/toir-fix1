package com.toir.dto.repairrequest;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.toir.enums.CriticalityLevel;
import com.toir.enums.PriorityLevel;
import com.toir.enums.RequestSource;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RepairRequestRequest(
        @NotBlank String number,
        @NotBlank String title,
        @NotBlank String description,
        @Schema(description = "Optional defect to link to the repair request", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        UUID defectId,
        @Schema(description = "Optional inline defect to create with the repair request", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        InlineDefectRequest defect,
        @Schema(description = "Optional inline defects to create with the repair request", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        List<InlineDefectRequest> defects,
        @NotNull UUID equipmentId,
        @Schema(description = "Optional; when omitted it is inferred from selected equipment", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        UUID departmentId,
        UUID locationId,
        @NotNull UUID reporterId,
        PriorityLevel priority,
        CriticalityLevel criticality,
        RequestSource source,
        Instant targetCompletionAt,
        @Schema(description = "Optional maintenance template to use as repair context", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        UUID templateId,
        @Schema(description = "Optional maintenance templates to use as repair context", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        List<UUID> templateIds,
        @Schema(description = "Optional selected template actions for the repair request", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        List<TemplateSelectionRequest> templateSelections
) {
    public RepairRequestRequest(
            String number,
            String title,
            String description,
            UUID defectId,
            InlineDefectRequest defect,
            List<InlineDefectRequest> defects,
            UUID equipmentId,
            UUID departmentId,
            UUID locationId,
            UUID reporterId,
            PriorityLevel priority,
            CriticalityLevel criticality,
            RequestSource source,
            Instant targetCompletionAt,
            UUID templateId
    ) {
        this(
                number,
                title,
                description,
                defectId,
                defect,
                defects,
                equipmentId,
                departmentId,
                locationId,
                reporterId,
                priority,
                criticality,
                source,
                targetCompletionAt,
                templateId,
                null,
                null
        );
    }

    public RepairRequestRequest(
            String number,
            String title,
            String description,
            UUID defectId,
            InlineDefectRequest defect,
            List<InlineDefectRequest> defects,
            UUID equipmentId,
            UUID departmentId,
            UUID locationId,
            UUID reporterId,
            PriorityLevel priority,
            CriticalityLevel criticality,
            RequestSource source,
            Instant targetCompletionAt
    ) {
        this(
                number,
                title,
                description,
                defectId,
                defect,
                defects,
                equipmentId,
                departmentId,
                locationId,
                reporterId,
                priority,
                criticality,
                source,
                targetCompletionAt,
                null,
                null,
                null
        );
    }

    public RepairRequestRequest(
            String number,
            String title,
            String description,
            UUID defectId,
            UUID equipmentId,
            UUID departmentId,
            UUID locationId,
            UUID reporterId,
            PriorityLevel priority,
            CriticalityLevel criticality,
            RequestSource source,
            Instant targetCompletionAt
    ) {
        this(
                number,
                title,
                description,
                defectId,
                null,
                null,
                equipmentId,
                departmentId,
                locationId,
                reporterId,
                priority,
                criticality,
                source,
                targetCompletionAt,
                null,
                null,
                null
        );
    }

    public record InlineDefectRequest(
            String title,
            String description,
            String category,
            String severity,
            @JsonAlias("failure_reason") String failureReason,
            @JsonAlias("root_cause") String rootCause
    ) {}

    public record TemplateSelectionRequest(
            @NotNull UUID templateId,
            List<ActionSelectionRequest> actions
    ) {}

    public record ActionSelectionRequest(
            UUID operationId,
            UUID actionId,
            UUID specialistId,
            UUID specialisationId,
            String customName
    ) {}
}
