package com.toir.repository;

import com.toir.entity.ApprovalTemplate;
import com.toir.enums.ApprovalActionType;
import com.toir.enums.ApprovalTargetType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface ApprovalTemplateRepository extends JpaRepository<ApprovalTemplate, UUID> {
    Optional<ApprovalTemplate> findFirstByTargetTypeAndActiveTrueAndIsDeletedFalseOrderByCreatedAtDesc(ApprovalTargetType targetType);

    @EntityGraph(attributePaths = "steps")
    Optional<ApprovalTemplate> findFirstByTargetTypeAndActionTypeAndActiveTrueAndIsDeletedFalseOrderByCreatedAtDesc(
            ApprovalTargetType targetType,
            ApprovalActionType actionType
    );

    @EntityGraph(attributePaths = "steps")
    @Query("""
            SELECT DISTINCT template
            FROM ApprovalTemplate template
            LEFT JOIN template.steps step ON step.isDeleted = false
            WHERE template.isDeleted = false
            ORDER BY template.targetType, template.actionType, template.code
            """)
    List<ApprovalTemplate> findAllRules();

    Optional<ApprovalTemplate> findByCodeAndIsDeletedFalse(String code);
}
