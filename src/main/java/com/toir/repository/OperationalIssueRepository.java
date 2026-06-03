package com.toir.repository;

import com.toir.entity.OperationalIssue;
import com.toir.enums.OperationalIssueStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OperationalIssueRepository extends JpaRepository<OperationalIssue, UUID> {
    Optional<OperationalIssue> findBySourceTypeAndSourceIdAndStatusAndIsDeletedFalse(
            String sourceType,
            UUID sourceId,
            OperationalIssueStatus status
    );
}
