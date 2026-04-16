package com.toir.repaircampaign;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RepairCampaignRepository extends JpaRepository<RepairCampaign, UUID> {
    boolean existsByCode(String code);
    List<RepairCampaign> findAllByYearOrderByStartDateAsc(int year);
    List<RepairCampaign> findAllByDepartmentIdAndYearOrderByStartDateAsc(UUID departmentId, int year);
}
