package com.toir.repository;
import com.toir.entity.RepairCampaignStage;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RepairCampaignStageRepository extends JpaRepository<RepairCampaignStage, UUID> {
}
