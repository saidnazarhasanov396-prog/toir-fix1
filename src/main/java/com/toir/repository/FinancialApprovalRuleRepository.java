package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.FinancialApprovalRule;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;


@Repository
public interface FinancialApprovalRuleRepository extends JpaRepository<FinancialApprovalRule, UUID> {
    java.util.Optional<FinancialApprovalRule> findByIdAndIsDeletedFalse(java.util.UUID id);

    java.util.List<FinancialApprovalRule> findAllByIsDeletedFalse();

    java.util.List<FinancialApprovalRule> findAllByIdInAndIsDeletedFalse(java.util.Collection<java.util.UUID> ids);

    boolean existsByIdAndIsDeletedFalse(java.util.UUID id);

    long countByIsDeletedFalse();

    @Query(value = "SELECT EXISTS(SELECT 1 FROM financial_approval_rules WHERE code = :code AND is_deleted = false)", nativeQuery = true)
    boolean existsByCodeAndIsDeletedFalse(@Param("code") String code);

    @Query(value = "SELECT * FROM financial_approval_rules WHERE is_active = true AND is_deleted = false ORDER BY priority ASC", nativeQuery = true)
    List<FinancialApprovalRule> findAllByActiveTrueAndIsDeletedFalseOrderByPriorityAsc();
}
