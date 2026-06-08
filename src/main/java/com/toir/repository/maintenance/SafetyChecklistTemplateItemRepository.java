package com.toir.repository.maintenance;

import com.toir.entity.maintenance.SafetyChecklistTemplateItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SafetyChecklistTemplateItemRepository extends JpaRepository<SafetyChecklistTemplateItem, UUID> {

    Optional<SafetyChecklistTemplateItem> findByIdAndIsDeletedFalse(UUID id);

    List<SafetyChecklistTemplateItem> findAllByTemplateIdAndIsDeletedFalseOrderBySequenceAsc(UUID templateId);
}
