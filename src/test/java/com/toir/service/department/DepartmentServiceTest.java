package com.toir.service.department;

import com.toir.dto.department.DepartmentDto;
import com.toir.dto.department.DepartmentRequest;
import com.toir.entity.Department;
import com.toir.enums.DepartmentType;
import com.toir.repository.department.DepartmentRepository;
import com.toir.util.AuditBuilderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DepartmentServiceTest {

    @Mock
    DepartmentRepository repository;

    @Mock
    AuditBuilderService auditBuilderService;

    @InjectMocks
    DepartmentService service;

    @Test
    void createPersistsNonDeletedDepartment() {
        DepartmentRequest request = new DepartmentRequest(
                "UI-E2E-20260516052136",
                "Workshop",
                DepartmentType.WORKSHOP,
                null,
                "UI created"
        );
        when(repository.existsByCodeAndIsDeletedFalse(request.code())).thenReturn(false);
        when(repository.save(any(Department.class))).thenAnswer(invocation -> {
            Department saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
            return saved;
        });

        DepartmentDto created = service.create(request);

        ArgumentCaptor<Department> captor = ArgumentCaptor.forClass(Department.class);
        verify(repository).save(captor.capture());
        Department persisted = captor.getValue();
        assertThat(persisted.isDeleted()).isFalse();
        assertThat(persisted.getCode()).isEqualTo(request.code());
        assertThat(persisted.getName()).isEqualTo(request.name());
        assertThat(created.code()).isEqualTo(request.code());
        assertThat(created.name()).isEqualTo(request.name());
    }

    @Test
    void searchFindsCreatedDepartment() {
        Department department = department("UI-E2E-20260516052136", "Workshop");
        when(repository.findAllByIsDeletedFalseAndByType(null, "%ui-e2e%"))
                .thenReturn(List.of(department));

        List<DepartmentDto> results = service.findAll(null, "UI-E2E");

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().code()).isEqualTo("UI-E2E-20260516052136");
        assertThat(results.getFirst().name()).isEqualTo("Workshop");
        verify(repository).findAllByIsDeletedFalseAndByType(null, "%ui-e2e%");
    }

    @Test
    void blankSearchFallbackWorks() {
        Department department = department("UI-E2E-20260516052136", "Workshop");
        when(repository.findAllByIsDeletedFalseAndByType(null, null))
                .thenReturn(List.of(department));

        List<DepartmentDto> results = service.findAll(null, "");

        assertThat(results).hasSize(1);
        verify(repository).findAllByIsDeletedFalseAndByType(null, null);
    }

    @Test
    void whitespaceSearchFallbackWorks() {
        Department department = department("UI-E2E-20260516052136", "Workshop");
        when(repository.findAllByIsDeletedFalseAndByType(null, null))
                .thenReturn(List.of(department));

        List<DepartmentDto> results = service.findAll(null, "   ");

        assertThat(results).hasSize(1);
        verify(repository).findAllByIsDeletedFalseAndByType(null, null);
    }

    @Test
    void searchBuildsLowercasePattern() {
        Department department = department("UI-E2E-20260516052136", "Workshop");
        when(repository.findAllByIsDeletedFalseAndByType(null, "%workshop%"))
                .thenReturn(List.of(department));

        List<DepartmentDto> results = service.findAll(null, "  WoRkShOp  ");

        assertThat(results).hasSize(1);
        verify(repository).findAllByIsDeletedFalseAndByType(null, "%workshop%");
    }

    private Department department(String code, String name) {
        Department department = new Department();
        ReflectionTestUtils.setField(department, "id", UUID.randomUUID());
        department.setCode(code);
        department.setName(name);
        department.setType(DepartmentType.WORKSHOP);
        department.setDeleted(false);
        return department;
    }
}
