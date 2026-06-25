package com.toir.repository.repair;

import com.toir.entity.repair.RepairCampaignDepartment;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface RepairCampaignDepartmentRepository extends JpaRepository<RepairCampaignDepartment, UUID> {

    @Query(value = """
            SELECT *
            FROM repair_campaign_departments
            WHERE campaign_id = cast(:campaignId as uuid)
              AND is_deleted = false
            ORDER BY role ASC, updated_at DESC
            """, nativeQuery = true)
    List<RepairCampaignDepartment> findAllByCampaignIdAndIsDeletedFalse(@Param("campaignId") UUID campaignId);

    @Query(value = """
            SELECT *
            FROM repair_campaign_departments
            WHERE campaign_id IN (:campaignIds)
              AND is_deleted = false
            ORDER BY role ASC, updated_at DESC
            """, nativeQuery = true)
    List<RepairCampaignDepartment> findAllByCampaignIdInAndIsDeletedFalse(@Param("campaignIds") Collection<UUID> campaignIds);

    @Query(value = """
            SELECT EXISTS(
                SELECT 1
                FROM repair_campaign_departments
                WHERE campaign_id = cast(:campaignId as uuid)
                  AND department_id = cast(:departmentId as uuid)
                  AND is_deleted = false
            )
            """, nativeQuery = true)
    boolean existsByCampaignIdAndDepartmentIdAndIsDeletedFalse(
            @Param("campaignId") UUID campaignId,
            @Param("departmentId") UUID departmentId
    );
}
