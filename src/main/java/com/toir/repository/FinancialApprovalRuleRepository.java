package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.entity.FinancialApprovalRule;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;


@Repository
public interface FinancialApprovalRuleRepository extends JpaRepository<FinancialApprovalRule, UUID> {
    boolean existsByCode(String code);
    List<FinancialApprovalRule> findAllByActiveTrueOrderByPriorityAsc();
}
