package com.toir.repository.projects;

import com.toir.entity.projects.FinancialApprovalRule;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface FinancialApprovalRuleRepository extends JpaRepository<FinancialApprovalRule, UUID> {
    @Query(value = "SELECT * FROM financial_approval_rules WHERE id = cast(:id as uuid) AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<FinancialApprovalRule> findByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT * FROM financial_approval_rules WHERE is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<FinancialApprovalRule> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    @Query(value = "SELECT * FROM financial_approval_rules WHERE id IN (:ids) AND is_deleted = false", nativeQuery = true)
    List<FinancialApprovalRule> findAllByIdInAndIsDeletedFalse(@Param("ids") Collection<UUID> ids);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM financial_approval_rules WHERE id = cast(:id as uuid) AND is_deleted = false)", nativeQuery = true)
    boolean existsByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT COUNT(*) FROM financial_approval_rules WHERE is_deleted = false", nativeQuery = true)
    long countByIsDeletedFalse();

    @Query(value = "SELECT EXISTS(SELECT 1 FROM financial_approval_rules WHERE code = :code AND is_deleted = false)", nativeQuery = true)
    boolean existsByCodeAndIsDeletedFalse(@Param("code") String code);

    @Query(value = """
            SELECT COALESCE(MAX(CAST(SUBSTRING(code FROM LENGTH(:prefix) + 1) AS BIGINT)), 0)
            FROM financial_approval_rules
            WHERE code LIKE CONCAT(:prefix, '%')
              AND SUBSTRING(code FROM LENGTH(:prefix) + 1) ~ '^[0-9]+$'
            """, nativeQuery = true)
    long maxSequenceByCodePrefix(@Param("prefix") String prefix);

    @Query(value = "SELECT * FROM financial_approval_rules WHERE is_active = true AND is_deleted = false ORDER BY priority ASC", nativeQuery = true)
    List<FinancialApprovalRule> findAllByActiveTrueAndIsDeletedFalseOrderByPriorityAsc();

    @Query("""
            select r
            from FinancialApprovalRule r
            where r.active = true
              and r.isDeleted = false
              and (:departmentId is null or r.departmentId = :departmentId or r.departmentId is null)
              and (r.minAmount is null or r.minAmount <= :amount)
              and (r.maxAmount is null or r.maxAmount >= :amount)
            order by
              case when r.departmentId = :departmentId then 0 else 1 end,
              r.priority asc
            limit 1
            """)
    Optional<FinancialApprovalRule> findFirstMatchingRule(
            UUID departmentId,
            Double amount
    );
}
