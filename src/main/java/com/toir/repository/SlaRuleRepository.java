package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.enums.SlaEntityType;
import com.toir.entity.SlaRule;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;


@Repository
public interface SlaRuleRepository extends JpaRepository<SlaRule, UUID> {
    boolean existsByCode(String code);
    List<SlaRule> findAllByActiveTrue();
    List<SlaRule> findAllByEntityTypeAndActiveTrue(SlaEntityType entityType);
}
