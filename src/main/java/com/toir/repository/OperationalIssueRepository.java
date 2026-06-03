package com.toir.repository;

import com.toir.entity.OperationalIssue;
import com.toir.enums.NotificationSeverity;
import com.toir.enums.OperationalIssueStatus;
import com.toir.enums.OperationalIssueType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface OperationalIssueRepository extends JpaRepository<OperationalIssue, UUID> {
    Optional<OperationalIssue> findBySourceTypeAndSourceIdAndStatusAndIsDeletedFalse(
            String sourceType,
            UUID sourceId,
            OperationalIssueStatus status
    );

    Optional<OperationalIssue> findByIdAndIsDeletedFalse(UUID id);

    @Query("""
            select issue
            from OperationalIssue issue
            where issue.isDeleted = false
              and (:scopeDepartmentId is null or issue.departmentId = :scopeDepartmentId)
              and (:status is null or issue.status = :status)
              and (:severity is null or issue.severity = :severity)
              and (:type is null or issue.type = :type)
              and (:departmentId is null or issue.departmentId = :departmentId)
              and (:equipmentId is null or issue.equipmentId = :equipmentId)
            """)
    Page<OperationalIssue> search(@Param("scopeDepartmentId") UUID scopeDepartmentId,
                                  @Param("status") OperationalIssueStatus status,
                                  @Param("severity") NotificationSeverity severity,
                                  @Param("type") OperationalIssueType type,
                                  @Param("departmentId") UUID departmentId,
                                  @Param("equipmentId") UUID equipmentId,
                                  Pageable pageable);
}
