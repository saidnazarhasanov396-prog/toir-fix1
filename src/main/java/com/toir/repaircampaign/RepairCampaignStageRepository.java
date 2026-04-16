package com.toir.repaircampaign;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RepairCampaignStageRepository extends JpaRepository<RepairCampaignStage, UUID> {
}
