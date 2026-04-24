package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.RepairCampaignStage;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;


@Repository
public interface RepairCampaignStageRepository extends JpaRepository<RepairCampaignStage, UUID> {
}
