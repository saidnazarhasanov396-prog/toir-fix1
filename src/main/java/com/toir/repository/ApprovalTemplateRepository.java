package com.toir.repository;

import com.toir.entity.ApprovalTemplate;
import com.toir.enums.ApprovalTargetType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ApprovalTemplateRepository extends JpaRepository<ApprovalTemplate, UUID> {
    Optional<ApprovalTemplate> findFirstByTargetTypeAndActiveTrueAndIsDeletedFalseOrderByCreatedAtDesc(ApprovalTargetType targetType);

    Optional<ApprovalTemplate> findByCodeAndIsDeletedFalse(String code);
}
