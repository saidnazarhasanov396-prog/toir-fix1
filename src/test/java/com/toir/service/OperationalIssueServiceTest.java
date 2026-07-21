package com.toir.service;

import com.toir.entity.Department;
import com.toir.entity.OperationalIssue;
import com.toir.entity.equipment.Equipment;
import com.toir.enums.NotificationSeverity;
import com.toir.enums.OperationalIssueTargetType;
import com.toir.enums.OperationalIssueStatus;
import com.toir.enums.OperationalIssueType;
import com.toir.repository.OperationalIssueRepository;
import com.toir.repository.department.DepartmentRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.security.ScopeAccessService;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OperationalIssueServiceTest {

    @Mock
    OperationalIssueRepository repository;

    @Mock
    EquipmentRepository equipmentRepository;

    @Mock
    DepartmentRepository departmentRepository;

    @Mock
    ScopeAccessService scopeAccessService;

    @Spy
    OperationalIssueI18nService i18nService = new OperationalIssueI18nService();

    @Mock
    OperationalIssueTargetResolver targetResolver;

    @InjectMocks
    OperationalIssueService service;

    @Test
    void nonAdminSearchUsesJwtDepartmentInsteadOfRequestedDepartment() {
        UUID requestedDepartmentId = UUID.randomUUID();
        UUID jwtDepartmentId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        OperationalIssue issue = issue(UUID.randomUUID(), equipmentId, jwtDepartmentId);
        Equipment equipment = equipment(equipmentId, "Pump A");
        Department department = department(jwtDepartmentId, "Production");
        when(scopeAccessService.isScopeAdmin()).thenReturn(false);
        when(scopeAccessService.currentDepartmentIdOrNull()).thenReturn(jwtDepartmentId);
        when(repository.search(
                eq(jwtDepartmentId),
                eq(OperationalIssueStatus.OPEN),
                eq(NotificationSeverity.WARNING),
                eq(OperationalIssueType.OVERDUE_WORK_ORDER),
                eq(jwtDepartmentId),
                eq(equipmentId),
                eq(null),
                eq(false),
                any(),
                any()
        )).thenReturn(new PageImpl<>(List.of(issue)));
        when(equipmentRepository.findAllByIdInAndIsDeletedFalse(any())).thenReturn(List.of(equipment));
        when(departmentRepository.findAllByIdInAndIsDeletedFalse(any())).thenReturn(List.of(department));

        var result = service.search(
                OperationalIssueStatus.OPEN,
                NotificationSeverity.WARNING,
                OperationalIssueType.OVERDUE_WORK_ORDER,
                requestedDepartmentId,
                equipmentId,
                null,
                0,
                20,
                "detectedAt",
                "desc"
        );

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().departmentId()).isEqualTo(jwtDepartmentId);
        assertThat(result.getContent().getFirst().departmentName()).isEqualTo("Production");
        assertThat(result.getContent().getFirst().equipmentName()).isEqualTo("Pump A");
    }

    @Test
    void systemAdminCanSearchRequestedDepartment() {
        UUID requestedDepartmentId = UUID.randomUUID();
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(repository.search(
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(requestedDepartmentId),
                eq(null),
                eq(null),
                eq(false),
                any(),
                any()
        )).thenReturn(org.springframework.data.domain.Page.empty());

        service.search(null, null, null, requestedDepartmentId, null, null, 0, 20, "severity", "asc");

        ArgumentCaptor<org.springframework.data.domain.Pageable> pageableCaptor =
                ArgumentCaptor.forClass(org.springframework.data.domain.Pageable.class);
        verify(repository).search(eq(null), eq(null), eq(null), eq(null), eq(requestedDepartmentId), eq(null), eq(null), eq(false), any(), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getSort().getOrderFor("severity").isAscending()).isTrue();
    }

    @Test
    void findByIdRejectsIssueFromAnotherDepartmentForNonAdmin() {
        UUID issueDepartmentId = UUID.randomUUID();
        UUID jwtDepartmentId = UUID.randomUUID();
        UUID issueId = UUID.randomUUID();
        when(scopeAccessService.isScopeAdmin()).thenReturn(false);
        when(scopeAccessService.currentDepartmentIdOrNull()).thenReturn(jwtDepartmentId);
        when(repository.findByIdAndIsDeletedFalse(issueId)).thenReturn(Optional.of(issue(issueId, null, issueDepartmentId)));

        assertThatThrownBy(() -> service.findById(issueId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("operational issue scope");
    }

    @Test
    void resolveMarksIssueResolvedWhenScoped() {
        UUID departmentId = UUID.randomUUID();
        UUID issueId = UUID.randomUUID();
        OperationalIssue issue = issue(issueId, null, departmentId);
        when(scopeAccessService.isScopeAdmin()).thenReturn(false);
        when(scopeAccessService.currentDepartmentIdOrNull()).thenReturn(departmentId);
        when(repository.findByIdAndIsDeletedFalse(issueId)).thenReturn(Optional.of(issue));
        when(repository.save(issue)).thenReturn(issue);

        var result = service.resolve(issueId);

        assertThat(result.status()).isEqualTo(OperationalIssueStatus.RESOLVED);
        assertThat(result.resolvedAt()).isNotNull();
        verify(repository).save(issue);
    }

    @Test
    void searchReturnsStructuredI18nPayloadForIssueText() {
        UUID departmentId = UUID.randomUUID();
        UUID issueId = UUID.randomUUID();
        OperationalIssue issue = issue(issueId, null, departmentId);
        issue.setType(OperationalIssueType.OVERDUE_REPAIR_REQUEST);
        issue.setSeverity(NotificationSeverity.CRITICAL);
        issue.setSourceType("RepairRequest");
        issue.setTitle("Overdue repair request RR-2026-105814");
        issue.setMessage("Target completion 2026-06-20T08:00:00Z has passed.");
        issue.setMetadata(java.util.Map.of(
                "requestNumber", "RR-2026-105814",
                "targetCompletionAt", "2026-06-20T08:00:00Z"
        ));
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(repository.search(
                eq(null),
                eq(OperationalIssueStatus.OPEN),
                eq(NotificationSeverity.CRITICAL),
                eq(OperationalIssueType.OVERDUE_REPAIR_REQUEST),
                eq(null),
                eq(null),
                eq(null),
                eq(false),
                any(),
                any()
        )).thenReturn(new PageImpl<>(List.of(issue)));

        var result = service.search(
                OperationalIssueStatus.OPEN,
                NotificationSeverity.CRITICAL,
                OperationalIssueType.OVERDUE_REPAIR_REQUEST,
                null,
                null,
                null,
                0,
                20,
                "detectedAt",
                "desc"
        );

        var dto = result.getContent().getFirst();
        assertThat(dto.titleKey()).isEqualTo("operationalIssues.titles.OVERDUE_REPAIR_REQUEST");
        assertThat(dto.titleParams()).containsEntry("requestNumber", "RR-2026-105814");
        assertThat(dto.messageKey()).isEqualTo("operationalIssues.messages.OVERDUE_REPAIR_REQUEST");
        assertThat(dto.messageParams()).containsEntry("targetCompletionAt", "2026-06-20T08:00:00Z");
    }

    @Test
    void searchReturnsSemanticNavigationTargetAlongsideLegacySourceReference() {
        UUID issueId = UUID.randomUUID();
        UUID repairRequestId = UUID.randomUUID();
        OperationalIssue issue = issue(issueId, null, null);
        issue.setType(OperationalIssueType.OVERDUE_REPAIR_REQUEST);
        issue.setSourceType("RepairRequest");
        issue.setSourceId(repairRequestId);
        var target = new com.toir.dto.operationalissue.OperationalIssueTarget(
                OperationalIssueTargetType.REPAIR_REQUEST,
                repairRequestId,
                "RR-001",
                null,
                null
        );
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(repository.search(
                eq(null), eq(null), eq(null), eq(null), eq(null), eq(null), eq(null), eq(false), any(), any()
        )).thenReturn(new PageImpl<>(List.of(issue)));
        when(targetResolver.resolveAll(List.of(issue))).thenReturn(java.util.Map.of(issueId, target));

        var dto = service.search(null, null, null, null, null, null, 0, 20, "detectedAt", "desc")
                .getContent().getFirst();

        assertThat(dto.sourceType()).isEqualTo("RepairRequest");
        assertThat(dto.sourceId()).isEqualTo(repairRequestId);
        assertThat(dto.targetType()).isEqualTo(OperationalIssueTargetType.REPAIR_REQUEST);
        assertThat(dto.targetId()).isEqualTo(repairRequestId);
        assertThat(dto.targetCode()).isEqualTo("RR-001");
    }

    @Test
    void searchNormalizesSearchTextBeforeRepositoryCall() {
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(repository.search(
                eq(null),
                eq(OperationalIssueStatus.OPEN),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq("%cobalt%"),
                eq(false),
                any(),
                any()
        )).thenReturn(org.springframework.data.domain.Page.empty());

        service.search(
                OperationalIssueStatus.OPEN,
                null,
                null,
                null,
                null,
                "  CoBalt  ",
                0,
                20,
                "detectedAt",
                "desc"
        );

        verify(repository).search(
                eq(null),
                eq(OperationalIssueStatus.OPEN),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq("%cobalt%"),
                eq(false),
                any(),
                any()
        );
    }

    @Test
    void searchExpandsRussianDefectDisplayTextToInspectionDefectAlias() {
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(repository.search(
                eq(null),
                eq(OperationalIssueStatus.OPEN),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq("%деф%"),
                eq(true),
                any(),
                any()
        )).thenReturn(org.springframework.data.domain.Page.empty());

        service.search(
                OperationalIssueStatus.OPEN,
                null,
                null,
                null,
                null,
                "  деф  ",
                0,
                20,
                "detectedAt",
                "desc"
        );

        ArgumentCaptor<Collection<OperationalIssueType>> aliasesCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(repository).search(
                eq(null),
                eq(OperationalIssueStatus.OPEN),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq("%деф%"),
                eq(true),
                aliasesCaptor.capture(),
                any()
        );
        assertThat(aliasesCaptor.getValue()).containsExactly(OperationalIssueType.INSPECTION_DEFECT);
    }

    @Test
    void searchDoesNotBroadenSpecificDefectCodeToDisplayAlias() {
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(repository.search(
                eq(null),
                eq(OperationalIssueStatus.OPEN),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq("%def-2026-0016%"),
                eq(false),
                any(),
                any()
        )).thenReturn(org.springframework.data.domain.Page.empty());

        service.search(
                OperationalIssueStatus.OPEN,
                null,
                null,
                null,
                null,
                "DEF-2026-0016",
                0,
                20,
                "detectedAt",
                "desc"
        );

        verify(repository).search(
                eq(null),
                eq(OperationalIssueStatus.OPEN),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq("%def-2026-0016%"),
                eq(false),
                any(),
                any()
        );
    }

    private OperationalIssue issue(UUID id, UUID equipmentId, UUID departmentId) {
        OperationalIssue issue = new OperationalIssue();
        ReflectionTestUtils.setField(issue, "id", id);
        issue.setType(OperationalIssueType.OVERDUE_WORK_ORDER);
        issue.setSeverity(NotificationSeverity.WARNING);
        issue.setStatus(OperationalIssueStatus.OPEN);
        issue.setEquipmentId(equipmentId);
        issue.setDepartmentId(departmentId);
        issue.setSourceType("WorkOrder");
        issue.setSourceId(UUID.randomUUID());
        issue.setTitle("Overdue work order");
        return issue;
    }

    private Equipment equipment(UUID id, String name) {
        Equipment equipment = new Equipment();
        ReflectionTestUtils.setField(equipment, "id", id);
        equipment.setName(name);
        return equipment;
    }

    private Department department(UUID id, String name) {
        Department department = new Department();
        ReflectionTestUtils.setField(department, "id", id);
        department.setName(name);
        return department;
    }
}
