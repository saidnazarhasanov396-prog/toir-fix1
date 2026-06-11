package com.toir.repository.repair;

import com.toir.entity.repair.RepairCampaign;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface RepairCampaignRepository extends JpaRepository<RepairCampaign, UUID> {
    @Query(value = "SELECT * FROM repair_campaigns WHERE id = cast(:id as uuid) AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<RepairCampaign> findByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT * FROM repair_campaigns WHERE is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<RepairCampaign> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    @Query(value = "SELECT * FROM repair_campaigns WHERE id IN (:ids) AND is_deleted = false", nativeQuery = true)
    List<RepairCampaign> findAllByIdInAndIsDeletedFalse(@Param("ids") Collection<UUID> ids);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM repair_campaigns WHERE id = cast(:id as uuid) AND is_deleted = false)", nativeQuery = true)
    boolean existsByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT COUNT(*) FROM repair_campaigns WHERE is_deleted = false", nativeQuery = true)
    long countByIsDeletedFalse();

    @Query(value = "SELECT EXISTS(SELECT 1 FROM repair_campaigns WHERE code = :code AND is_deleted = false)", nativeQuery = true)
    boolean existsByCodeAndIsDeletedFalse(@Param("code") String code);

    @Query(value = """
            SELECT COALESCE(MAX(CAST(SUBSTRING(code FROM LENGTH(:prefix) + 1) AS BIGINT)), 0)
            FROM repair_campaigns
            WHERE code LIKE CONCAT(:prefix, '%')
              AND SUBSTRING(code FROM LENGTH(:prefix) + 1) ~ '^[0-9]+$'
            """, nativeQuery = true)
    long maxSequenceByCodePrefix(@Param("prefix") String prefix);

    @Query(value = "SELECT * FROM repair_campaigns WHERE year = :year AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<RepairCampaign> findAllByYearAndIsDeletedFalseOrderByStartDateAsc(@Param("year") int year);

    @Query(value = "SELECT * FROM repair_campaigns WHERE department_id = :departmentId AND year = :year AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<RepairCampaign> findAllByDepartmentIdAndYearAndIsDeletedFalseOrderByStartDateAsc(@Param("departmentId") UUID departmentId, @Param("year") int year);

    @Query(value = """
            SELECT * FROM repair_campaigns 
            WHERE is_deleted = false 
              AND (:year IS NULL OR year = :year)
              AND (:status IS NULL OR status = :status)
              AND (
                :searchPattern IS NULL 
                OR lower(coalesce(code, '')) LIKE :searchPattern 
                OR lower(coalesce(name, '')) LIKE :searchPattern
                OR lower(coalesce(scope, '')) LIKE :searchPattern
                OR lower(coalesce(notes, '')) LIKE :searchPattern
              )
            ORDER BY start_date ASC
            """, nativeQuery = true)
    List<RepairCampaign> findAllFiltered(
            @Param("year") Integer year, 
            @Param("status") String status,
            @Param("searchPattern") String searchPattern
    );
}
