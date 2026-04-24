package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.RepairCampaign;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;


@Repository
public interface RepairCampaignRepository extends JpaRepository<RepairCampaign, UUID> {
    boolean existsByCode(String code);
    List<RepairCampaign> findAllByYearOrderByStartDateAsc(int year);
    List<RepairCampaign> findAllByDepartmentIdAndYearOrderByStartDateAsc(UUID departmentId, int year);
}
