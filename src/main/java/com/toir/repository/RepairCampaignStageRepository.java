package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.RepairCampaignStage;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;


@Repository
public interface RepairCampaignStageRepository extends JpaRepository<RepairCampaignStage, UUID> {
    java.util.Optional<RepairCampaignStage> findByIdAndIsDeletedFalse(java.util.UUID id);

    java.util.List<RepairCampaignStage> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    java.util.List<RepairCampaignStage> findAllByIdInAndIsDeletedFalse(java.util.Collection<java.util.UUID> ids);

    boolean existsByIdAndIsDeletedFalse(java.util.UUID id);

    long countByIsDeletedFalse();

}
