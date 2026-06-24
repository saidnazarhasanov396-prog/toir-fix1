package com.toir.repository;

import com.toir.entity.OperationalIssue;
import com.toir.enums.NotificationSeverity;
import com.toir.enums.OperationalIssueStatus;
import com.toir.enums.OperationalIssueType;
import java.util.Collection;
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

    @Query(value = """
            select issue
            from OperationalIssue issue
            left join Equipment equipment on equipment.id = issue.equipmentId and equipment.isDeleted = false
            left join Department department on department.id = issue.departmentId and department.isDeleted = false
            left join Defect defect on defect.id = issue.sourceId and issue.sourceType = 'Defect' and defect.isDeleted = false
            where issue.isDeleted = false
              and (:scopeDepartmentId is null or issue.departmentId = :scopeDepartmentId)
              and (:status is null or issue.status = :status)
              and (:severity is null or issue.severity = :severity)
              and (:type is null or issue.type = :type)
              and (:departmentId is null or issue.departmentId = :departmentId)
              and (:equipmentId is null or issue.equipmentId = :equipmentId)
              and (
                    (:searchPattern is null and :searchTypeAliasesActive = false)
                    or (
                        :searchPattern is not null
                        and (
                            lower(coalesce(issue.title, '')) like :searchPattern
                            or lower(coalesce(issue.message, '')) like :searchPattern
                            or lower(coalesce(issue.sourceType, '')) like :searchPattern
                            or lower(coalesce(equipment.code, '')) like :searchPattern
                            or lower(coalesce(equipment.name, '')) like :searchPattern
                            or lower(coalesce(equipment.inventoryNumber, '')) like :searchPattern
                            or lower(coalesce(department.code, '')) like :searchPattern
                            or lower(coalesce(department.name, '')) like :searchPattern
                            or lower(coalesce(department.nameEn, '')) like :searchPattern
                            or lower(coalesce(department.nameUz, '')) like :searchPattern
                            or lower(coalesce(defect.code, '')) like :searchPattern
                            or lower(coalesce(defect.title, '')) like :searchPattern
                            or lower(coalesce(defect.description, '')) like :searchPattern
                        )
                    )
                    or (:searchTypeAliasesActive = true and issue.type in :searchTypeAliases)
                  )
            """,
            countQuery = """
            select count(issue)
            from OperationalIssue issue
            left join Equipment equipment on equipment.id = issue.equipmentId and equipment.isDeleted = false
            left join Department department on department.id = issue.departmentId and department.isDeleted = false
            left join Defect defect on defect.id = issue.sourceId and issue.sourceType = 'Defect' and defect.isDeleted = false
            where issue.isDeleted = false
              and (:scopeDepartmentId is null or issue.departmentId = :scopeDepartmentId)
              and (:status is null or issue.status = :status)
              and (:severity is null or issue.severity = :severity)
              and (:type is null or issue.type = :type)
              and (:departmentId is null or issue.departmentId = :departmentId)
              and (:equipmentId is null or issue.equipmentId = :equipmentId)
              and (
                    (:searchPattern is null and :searchTypeAliasesActive = false)
                    or (
                        :searchPattern is not null
                        and (
                            lower(coalesce(issue.title, '')) like :searchPattern
                            or lower(coalesce(issue.message, '')) like :searchPattern
                            or lower(coalesce(issue.sourceType, '')) like :searchPattern
                            or lower(coalesce(equipment.code, '')) like :searchPattern
                            or lower(coalesce(equipment.name, '')) like :searchPattern
                            or lower(coalesce(equipment.inventoryNumber, '')) like :searchPattern
                            or lower(coalesce(department.code, '')) like :searchPattern
                            or lower(coalesce(department.name, '')) like :searchPattern
                            or lower(coalesce(department.nameEn, '')) like :searchPattern
                            or lower(coalesce(department.nameUz, '')) like :searchPattern
                            or lower(coalesce(defect.code, '')) like :searchPattern
                            or lower(coalesce(defect.title, '')) like :searchPattern
                            or lower(coalesce(defect.description, '')) like :searchPattern
                        )
                    )
                    or (:searchTypeAliasesActive = true and issue.type in :searchTypeAliases)
                  )
            """)
    Page<OperationalIssue> search(@Param("scopeDepartmentId") UUID scopeDepartmentId,
                                  @Param("status") OperationalIssueStatus status,
                                  @Param("severity") NotificationSeverity severity,
                                  @Param("type") OperationalIssueType type,
                                  @Param("departmentId") UUID departmentId,
                                  @Param("equipmentId") UUID equipmentId,
                                  @Param("searchPattern") String searchPattern,
                                  @Param("searchTypeAliasesActive") boolean searchTypeAliasesActive,
                                  @Param("searchTypeAliases") Collection<OperationalIssueType> searchTypeAliases,
                                  Pageable pageable);
}
