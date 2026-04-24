package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.RepairCampaign;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;


@Repository
public interface RepairCampaignRepository extends JpaRepository<RepairCampaign, UUID> {
    @Query(value = "SELECT EXISTS(SELECT 1 FROM repair_campaigns WHERE code = :code AND is_deleted = false)", nativeQuery = true)
    boolean existsByCode(@Param("code") String code);

    @Query(value = "SELECT * FROM repair_campaigns WHERE year = :year AND is_deleted = false ORDER BY start_date ASC", nativeQuery = true)
    List<RepairCampaign> findAllByYearOrderByStartDateAsc(@Param("year") int year);

    @Query(value = "SELECT * FROM repair_campaigns WHERE department_id = :departmentId AND year = :year AND is_deleted = false ORDER BY start_date ASC", nativeQuery = true)
    List<RepairCampaign> findAllByDepartmentIdAndYearOrderByStartDateAsc(@Param("departmentId") UUID departmentId, @Param("year") int year);
}
