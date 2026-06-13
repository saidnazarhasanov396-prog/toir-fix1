package com.toir.repository;

import com.toir.entity.ApprovalHistory;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ApprovalHistoryRepository extends JpaRepository<ApprovalHistory, UUID> {
    List<ApprovalHistory> findAllByApprovalIdAndIsDeletedFalseOrderByChangedAtAsc(UUID approvalId);
}
