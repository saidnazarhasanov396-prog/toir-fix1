package com.toir.sla;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SlaRuleRepository extends JpaRepository<SlaRule, UUID> {
    boolean existsByCode(String code);
    List<SlaRule> findAllByActiveTrue();
    List<SlaRule> findAllByEntityTypeAndActiveTrue(SlaEntityType entityType);
}
