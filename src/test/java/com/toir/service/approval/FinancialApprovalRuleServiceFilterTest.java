package com.toir.service.approval;

import com.toir.dto.financialapprovalrule.FinancialApprovalRuleDto;
import com.toir.entity.projects.FinancialApprovalRule;
import com.toir.repository.projects.FinancialApprovalRuleRepository;
import com.toir.service.FinancialApprovalRuleService;
import com.toir.util.AuditBuilderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Covers the departmentId / role / status / search filters added to
 * FinancialApprovalRuleService#findAll for GET /api/v1/budgets/approval-rules.
 */
@ExtendWith(MockitoExtension.class)
class FinancialApprovalRuleServiceFilterTest {

    @Mock
    FinancialApprovalRuleRepository repository;

    @Mock
    AuditBuilderService auditBuilderService;

    FinancialApprovalRuleService service;

    UUID financeDeptId;
    UUID logisticsDeptId;

    FinancialApprovalRule financeManagerRule;
    FinancialApprovalRule logisticsHeadRule;
    FinancialApprovalRule inactiveGlobalRule;

    @BeforeEach
    void setUp() {
        service = new FinancialApprovalRuleService(repository, auditBuilderService);

        financeDeptId = UUID.randomUUID();
        logisticsDeptId = UUID.randomUUID();

        financeManagerRule = rule("FAR-2026-0001", "Finance small spend", financeDeptId,
                "FINANCE_MANAGER", "CFO", true, "Standard finance approval");

        logisticsHeadRule = rule("FAR-2026-0002", "Logistics purchase", logisticsDeptId,
                "LOGISTICS_HEAD", "FINANCE_MANAGER", true, "Warehouse related notes");

        inactiveGlobalRule = rule("FAR-2026-0003", "Legacy global rule", null,
                "SYSTEM_ADMIN", null, false, null);

        when(repository.findAllByIsDeletedFalseOrderByUpdatedAtDesc())
                .thenReturn(List.of(financeManagerRule, logisticsHeadRule, inactiveGlobalRule));
    }

    private FinancialApprovalRule rule(String code, String name, UUID departmentId,
                                        String requiredRoleCode, String escalateToRoleCode,
                                        boolean active, String notes) {
        FinancialApprovalRule r = new FinancialApprovalRule();
        r.setId(UUID.randomUUID());
        r.setCode(code);
        r.setName(name);
        r.setDepartmentId(departmentId);
        r.setRequiredRoleCode(requiredRoleCode);
        r.setEscalateToRoleCode(escalateToRoleCode);
        r.setActive(active);
        r.setNotes(notes);
        r.setPriority(100);
        return r;
    }

    @Test
    void noFiltersReturnsEverything() {
        List<FinancialApprovalRuleDto> result = service.findAll(null, null, null, null);

        assertThat(result).hasSize(3);
    }

    @Test
    void filtersByDepartmentId() {
        List<FinancialApprovalRuleDto> result = service.findAll(financeDeptId, null, null, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).code()).isEqualTo("FAR-2026-0001");
    }

    @Test
    void departmentFilterExcludesRulesWithoutMatchingDepartment() {
        List<FinancialApprovalRuleDto> result = service.findAll(logisticsDeptId, null, null, null);

        assertThat(result).extracting(FinancialApprovalRuleDto::code)
                .containsExactly("FAR-2026-0002");
    }

    @Test
    void filtersByRoleMatchingRequiredRoleCode() {
        List<FinancialApprovalRuleDto> result = service.findAll(null, "logistics_head", null, null);

        assertThat(result).extracting(FinancialApprovalRuleDto::code)
                .containsExactly("FAR-2026-0002");
    }

    @Test
    void filtersByRoleMatchingEscalateToRoleCodeCaseInsensitively() {
        List<FinancialApprovalRuleDto> result = service.findAll(null, "cfo", null, null);

        assertThat(result).extracting(FinancialApprovalRuleDto::code)
                .containsExactly("FAR-2026-0001");
    }

    @Test
    void filtersByActiveStatusTrue() {
        List<FinancialApprovalRuleDto> result = service.findAll(null, null, true, null);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(FinancialApprovalRuleDto::code)
                .containsExactlyInAnyOrder("FAR-2026-0001", "FAR-2026-0002");
    }

    @Test
    void filtersByActiveStatusFalse() {
        List<FinancialApprovalRuleDto> result = service.findAll(null, null, false, null);

        assertThat(result).extracting(FinancialApprovalRuleDto::code)
                .containsExactly("FAR-2026-0003");
    }

    @Test
    void searchMatchesNameCaseInsensitively() {
        List<FinancialApprovalRuleDto> result = service.findAll(null, null, null, "LOGISTICS");

        assertThat(result).extracting(FinancialApprovalRuleDto::code)
                .containsExactly("FAR-2026-0002");
    }

    @Test
    void searchMatchesCode() {
        List<FinancialApprovalRuleDto> result = service.findAll(null, null, null, "0003");

        assertThat(result).extracting(FinancialApprovalRuleDto::code)
                .containsExactly("FAR-2026-0003");
    }

    @Test
    void searchMatchesNotes() {
        List<FinancialApprovalRuleDto> result = service.findAll(null, null, null, "warehouse");

        assertThat(result).extracting(FinancialApprovalRuleDto::code)
                .containsExactly("FAR-2026-0002");
    }

    @Test
    void blankSearchIsTreatedAsNoFilter() {
        List<FinancialApprovalRuleDto> result = service.findAll(null, null, null, "   ");

        assertThat(result).hasSize(3);
    }

    @Test
    void combinesMultipleFiltersWithAndSemantics() {
        List<FinancialApprovalRuleDto> result =
                service.findAll(financeDeptId, "finance_manager", true, "finance");

        assertThat(result).extracting(FinancialApprovalRuleDto::code)
                .containsExactly("FAR-2026-0001");
    }

    @Test
    void combinedFiltersReturnEmptyWhenNothingMatches() {
        List<FinancialApprovalRuleDto> result =
                service.findAll(financeDeptId, "logistics_head", null, null);

        assertThat(result).isEmpty();
    }

    @Test
    void noArgFindAllStillReturnsUnfilteredList() {
        List<FinancialApprovalRuleDto> result = service.findAll();

        assertThat(result).hasSize(3);
    }
}
