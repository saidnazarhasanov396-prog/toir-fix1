package com.toir.dto.repaircampaign;
import java.time.Instant;
import java.util.UUID;
public record RepairCampaignResourceResponse(UUID id,UUID campaignId,UUID workItemId,UUID employeeId,UUID brigadeId,
 UUID counteragentId,String shiftCode,Instant plannedStartAt,Instant plannedEndAt,String competencyRequirement,Long campaignVersion,boolean active) {}
