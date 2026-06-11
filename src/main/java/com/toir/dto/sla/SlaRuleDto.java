package com.toir.dto.sla;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.toir.enums.SlaEntityType;
import com.toir.entity.SlaRule;
import com.toir.enums.SlaTriggerType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;
import java.util.UUID;

public record SlaRuleDto(
        UUID id,
        String code,
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

    @JsonProperty("ruleThreshold")
    public int ruleThreshold() {
        return thresholdHours;
    }

    @JsonProperty("ruleEscalations")
    public List<Object> ruleEscalations() {
        return List.of();
    }
}
