package com.toir.repository;

import org.springframework.stereotype.Repository;
import com.toir.enums.SlaEntityType;
import com.toir.entity.SlaRule;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;


@Repository
public interface SlaRuleRepository extends JpaRepository<SlaRule, UUID> {
    java.util.Optional<SlaRule> findByIdAndIsDeletedFalse(java.util.UUID id);

    java.util.List<SlaRule> findAllByIsDeletedFalse();

    java.util.List<SlaRule> findAllByIdInAndIsDeletedFalse(java.util.Collection<java.util.UUID> ids);

    boolean existsByIdAndIsDeletedFalse(java.util.UUID id);

    long countByIsDeletedFalse();

    @Query(value = "SELECT EXISTS(SELECT 1 FROM sla_rules WHERE code = :code AND is_deleted = false)", nativeQuery = true)
    boolean existsByCodeAndIsDeletedFalse(@Param("code") String code);

    @Query(value = "SELECT * FROM sla_rules WHERE is_active = true AND is_deleted = false", nativeQuery = true)
    List<SlaRule> findAllByActiveTrueAndIsDeletedFalse();

    @Query(value = "SELECT * FROM sla_rules WHERE entity_type = :entityType AND is_active = true AND is_deleted = false", nativeQuery = true)
    List<SlaRule> findAllByEntityTypeAndActiveTrueAndIsDeletedFalse(@Param("entityType") SlaEntityType entityType);
}
