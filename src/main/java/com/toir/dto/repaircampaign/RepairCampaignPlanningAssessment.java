package com.toir.dto.repaircampaign;
import java.util.List;
import java.util.UUID;
public record RepairCampaignPlanningAssessment(UUID campaignId,Long campaignVersion,List<String> blockers,boolean approvalInvalidationRequired) {
 public boolean ready(){return blockers==null||blockers.isEmpty();}
}
