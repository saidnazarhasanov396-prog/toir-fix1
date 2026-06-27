package com.toir.repository.repair;

import com.toir.entity.repair.RepairCampaign;
import java.time.LocalDate;
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

    @Query(value = "SELECT * FROM repair_campaigns WHERE is_deleted = false ORDER BY created_at DESC, updated_at DESC", nativeQuery = true)
    List<RepairCampaign> findAllByIsDeletedFalseOrderByCreatedAtDesc();

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

    @Query(value = """
            SELECT * FROM repair_campaigns 
            WHERE is_deleted = false 
              AND (cast(:startDate as date) IS NULL OR end_date >= cast(:startDate as date))
              AND (cast(:endDate as date) IS NULL OR start_date <= cast(:endDate as date))
              AND (:status IS NULL OR status = :status)
              AND (
                :searchPattern IS NULL 
                OR lower(coalesce(code, '')) LIKE :searchPattern 
                OR lower(coalesce(name, '')) LIKE :searchPattern
                OR lower(coalesce(scope, '')) LIKE :searchPattern
                OR lower(coalesce(notes, '')) LIKE :searchPattern
              )
            ORDER BY created_at DESC, updated_at DESC
            """, nativeQuery = true)
    List<RepairCampaign> findAllFiltered(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("status") String status,
            @Param("searchPattern") String searchPattern
    );
}
