package com.toir.financialapprovalrule;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FinancialApprovalRuleRepository extends JpaRepository<FinancialApprovalRule, UUID> {
    boolean existsByCode(String code);
    List<FinancialApprovalRule> findAllByActiveTrueOrderByPriorityAsc();
}
