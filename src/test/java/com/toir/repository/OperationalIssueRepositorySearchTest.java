package com.toir.repository;

import com.toir.entity.OperationalIssue;
import com.toir.entity.defects.Defect;
import com.toir.enums.DefectStatus;
import com.toir.enums.NotificationSeverity;
import com.toir.enums.OperationalIssueStatus;
import com.toir.enums.OperationalIssueType;
import com.toir.repository.defects.DefectRepository;
import java.util.EnumSet;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class OperationalIssueRepositorySearchTest {

    @Autowired
    OperationalIssueRepository repository;

    @Autowired
    DefectRepository defectRepository;

    @Test
    void searchMatchesInspectionDefectAliasWhenRawTextDoesNotMatchRussianDisplayTerm() {
        Defect defect = saveDefect("DEF-SEARCH-001", "Bearing wear");
        OperationalIssue issue = saveInspectionDefectIssue(defect, "Inspection defect DEF-SEARCH-001", "Bearing wear");

        var result = repository.search(
                null,
                OperationalIssueStatus.OPEN,
                null,
                null,
                null,
                null,
                "%деф%",
                true,
                EnumSet.of(OperationalIssueType.INSPECTION_DEFECT),
                PageRequest.of(0, 10)
        );

        assertThat(result.getContent()).extracting(OperationalIssue::getId).containsExactly(issue.getId());
    }

    @Test
    void searchMatchesLinkedDefectTextWhenIssueRawTextDoesNotContainSearchTerm() {
        Defect defect = saveDefect("DEF-SEARCH-002", "Просто дефект");
        OperationalIssue issue = saveInspectionDefectIssue(defect, "Inspection defect DEF-SEARCH-002", "Legacy message");

        var result = repository.search(
                null,
                OperationalIssueStatus.OPEN,
                null,
                null,
                null,
                null,
                "%просто дефект%",
                false,
                EnumSet.allOf(OperationalIssueType.class),
                PageRequest.of(0, 10)
        );

        assertThat(result.getContent()).extracting(OperationalIssue::getId).containsExactly(issue.getId());
    }

    private Defect saveDefect(String code, String title) {
        Defect defect = new Defect();
        defect.setCode(code);
        defect.setTitle(title);
        defect.setDescription("Search integration test defect");
        defect.setEquipmentId(UUID.randomUUID());
        defect.setStatus(DefectStatus.OPEN);
        return defectRepository.save(defect);
    }

    private OperationalIssue saveInspectionDefectIssue(Defect defect, String title, String message) {
        OperationalIssue issue = new OperationalIssue();
        issue.setType(OperationalIssueType.INSPECTION_DEFECT);
        issue.setSeverity(NotificationSeverity.WARNING);
        issue.setStatus(OperationalIssueStatus.OPEN);
        issue.setEquipmentId(defect.getEquipmentId());
        issue.setSourceType("Defect");
        issue.setSourceId(defect.getId());
        issue.setTitle(title);
        issue.setMessage(message);
        return repository.save(issue);
    }
}
