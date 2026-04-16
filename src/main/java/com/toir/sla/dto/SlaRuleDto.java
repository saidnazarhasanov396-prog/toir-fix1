package com.toir.sla.dto;

import com.toir.sla.SlaEntityType;
import com.toir.sla.SlaRule;
import com.toir.sla.SlaTriggerType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record SlaRuleDto(
        UUID id,
        @NotBlank String code,
        @NotBlank String name,
        @NotNull SlaEntityType entityType,
        @NotNull SlaTriggerType triggerType,
        @Positive int thresholdHours,
        UUID departmentId,
        Boolean active
) {
    public static SlaRuleDto from(SlaRule r) {
        return new SlaRuleDto(r.getId(), r.getCode(), r.getName(), r.getEntityType(),
                r.getTriggerType(), r.getThresholdHours(), r.getDepartmentId(), r.isActive());
    }
}
