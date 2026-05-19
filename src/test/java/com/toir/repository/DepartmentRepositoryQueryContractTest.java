package com.toir.repository;

import com.toir.repository.department.DepartmentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class DepartmentRepositoryQueryContractTest {

    @Test
    void queryDoesNotUseTrimOnSearchParameter() {
        String sql = findDepartmentSearchQuery().toLowerCase();
        assertThat(sql).doesNotContain("trim(:search)");
    }

    @Test
    void queryDoesNotConcatRawSearchParameter() {
        String sql = findDepartmentSearchQuery().toLowerCase();
        assertThat(sql).doesNotContain("concat('%', :search, '%')");
        assertThat(sql).doesNotContain("lower(:search)");
    }

    @Test
    void queryUsesSearchPattern() {
        String sql = findDepartmentSearchQuery().toLowerCase();
        assertThat(sql).contains(":searchpattern is null");
        assertThat(sql).contains("like :searchpattern");
    }

    @Test
    void queryIncludesCodeNameNameEnNameUzDescription() {
        String sql = findDepartmentSearchQuery().toLowerCase();
        assertThat(sql).contains("lower(coalesce(d.code, '')) like :searchpattern");
        assertThat(sql).contains("lower(coalesce(d.name, '')) like :searchpattern");
        assertThat(sql).contains("lower(coalesce(d.nameen, '')) like :searchpattern");
        assertThat(sql).contains("lower(coalesce(d.nameuz, '')) like :searchpattern");
        assertThat(sql).contains("lower(coalesce(d.description, '')) like :searchpattern");
    }

    private String findDepartmentSearchQuery() {
        Method method = Arrays.stream(DepartmentRepository.class.getMethods())
                .filter(m -> m.getName().equals("findAllByIsDeletedFalseAndByType"))
                .findFirst()
                .orElseThrow();

        Query query = method.getAnnotation(Query.class);
        assertThat(query).isNotNull();
        return query.value();
    }
}

