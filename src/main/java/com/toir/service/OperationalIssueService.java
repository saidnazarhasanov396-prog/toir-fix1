package com.toir.service;

import com.toir.entity.OperationalIssue;
import com.toir.enums.NotificationSeverity;
import com.toir.enums.OperationalIssueStatus;
import com.toir.enums.OperationalIssueType;
import com.toir.repository.OperationalIssueRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OperationalIssueService {

    private final OperationalIssueRepository repository;

    @Transactional
    public OperationalIssue openOrUpdate(OperationalIssueType type,
                                         NotificationSeverity severity,
                                         UUID equipmentId,
                                         UUID departmentId,
                                         String sourceType,
                                         UUID sourceId,
                                         String title,
                                         String message) {
        OperationalIssue issue = repository
                .findBySourceTypeAndSourceIdAndStatusAndIsDeletedFalse(sourceType, sourceId, OperationalIssueStatus.OPEN)
                .orElseGet(OperationalIssue::new);
        issue.setType(type);
        issue.setSeverity(severity == null ? NotificationSeverity.INFO : severity);
        issue.setEquipmentId(equipmentId);
        issue.setDepartmentId(departmentId);
        issue.setSourceType(sourceType);
        issue.setSourceId(sourceId);
        issue.setTitle(title);
        issue.setMessage(message);
        return repository.save(issue);
    }
}
