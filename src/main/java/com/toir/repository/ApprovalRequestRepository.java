package com.toir.repository;
import com.toir.entity.ApprovalRequest;
import com.toir.enums.ApprovalStatus;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, UUID> {
    List<ApprovalRequest> findAllByDocumentTypeAndDocumentId(String documentType, UUID documentId);
    List<ApprovalRequest> findAllByStatusOrderByCreatedAtDesc(ApprovalStatus status);
    List<ApprovalRequest> findAllByRequesterIdOrderByCreatedAtDesc(UUID requesterId);
}
