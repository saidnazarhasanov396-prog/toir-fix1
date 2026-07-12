package com.toir.dto.repaircampaign;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
public record RepairCampaignDependencyRequest(@NotNull Long version,@NotNull UUID predecessorId,@NotNull UUID successorId) {}
