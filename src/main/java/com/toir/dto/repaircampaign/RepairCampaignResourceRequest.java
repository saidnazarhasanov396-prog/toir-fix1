package com.toir.dto.repaircampaign;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;
public record RepairCampaignResourceRequest(@NotNull Long version,@NotNull UUID workItemId,UUID employeeId,UUID brigadeId,
 UUID counteragentId,@NotNull String shiftCode,@NotNull Instant plannedStartAt,@NotNull Instant plannedEndAt,String competencyRequirement) {}
