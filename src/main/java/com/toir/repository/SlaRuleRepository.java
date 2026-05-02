package com.toir.repository;

import com.toir.entity.SlaRule;
import com.toir.enums.SlaEntityType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface SlaRuleRepository extends JpaRepository<SlaRule, UUID> {
    @Query(value = "SELECT * FROM sla_rules WHERE id = cast(:id as uuid) AND is_deleted = false LIMIT 1", nativeQuery = true)
    Optional<SlaRule> findByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT * FROM sla_rules WHERE is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<SlaRule> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    @Query(value = "SELECT * FROM sla_rules WHERE id IN (:ids) AND is_deleted = false", nativeQuery = true)
    List<SlaRule> findAllByIdInAndIsDeletedFalse(@Param("ids") Collection<UUID> ids);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM sla_rules WHERE id = cast(:id as uuid) AND is_deleted = false)", nativeQuery = true)
    boolean existsByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query(value = "SELECT COUNT(*) FROM sla_rules WHERE is_deleted = false", nativeQuery = true)
    long countByIsDeletedFalse();

    @Query(value = "SELECT EXISTS(SELECT 1 FROM sla_rules WHERE code = :code AND is_deleted = false)", nativeQuery = true)
    boolean existsByCodeAndIsDeletedFalse(@Param("code") String code);

    @Query(value = "SELECT * FROM sla_rules WHERE is_active = true AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<SlaRule> findAllByActiveTrueAndIsDeletedFalse();

    @Query(value = "SELECT * FROM sla_rules WHERE entity_type = :entityType AND is_active = true AND is_deleted = false ORDER BY updated_at DESC", nativeQuery = true)
    List<SlaRule> findAllByEntityTypeAndActiveTrueAndIsDeletedFalse(@Param("entityType") SlaEntityType entityType);
}
