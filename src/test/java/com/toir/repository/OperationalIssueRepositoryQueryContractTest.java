package com.toir.repository;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import static org.assertj.core.api.Assertions.assertThat;

class OperationalIssueRepositoryQueryContractTest {

    @Test
    void searchQueryIncludesSearchPatternAcrossIssueEquipmentAndDepartmentText() {
        Query query = Arrays.stream(OperationalIssueRepository.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("search"))
                .findFirst()
                .orElseThrow()
                .getAnnotation(Query.class);

        String value = query.value().toLowerCase();

        assertThat(value).contains(":searchpattern is null");
        assertThat(value).contains("lower(coalesce(issue.title, '')) like :searchpattern");
        assertThat(value).contains("lower(coalesce(issue.message, '')) like :searchpattern");
        assertThat(value).contains("lower(coalesce(issue.sourcetype, '')) like :searchpattern");
        assertThat(value).contains("lower(coalesce(equipment.name, '')) like :searchpattern");
        assertThat(value).contains("lower(coalesce(equipment.code, '')) like :searchpattern");
        assertThat(value).contains("lower(coalesce(department.name, '')) like :searchpattern");
        assertThat(value).contains("lower(coalesce(department.code, '')) like :searchpattern");
    }

    @Test
    void searchQueryIncludesLocalizedDefectDisplaySupport() {
        Query query = Arrays.stream(OperationalIssueRepository.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("search"))
                .findFirst()
                .orElseThrow()
                .getAnnotation(Query.class);

        String value = query.value().toLowerCase();

        assertThat(value).contains("left join defect defect");
        assertThat(value).contains("defect.id = issue.sourceid");
        assertThat(value).contains("issue.sourcetype = 'defect'");
        assertThat(value).contains(":searchtypealiasesactive = true");
        assertThat(value).contains("issue.type in :searchtypealiases");
        assertThat(value).contains("lower(coalesce(defect.code, '')) like :searchpattern");
        assertThat(value).contains("lower(coalesce(defect.title, '')) like :searchpattern");
        assertThat(value).contains("lower(coalesce(defect.description, '')) like :searchpattern");
    }
}
