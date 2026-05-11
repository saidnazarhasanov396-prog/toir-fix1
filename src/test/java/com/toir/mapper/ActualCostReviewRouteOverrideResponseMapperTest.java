package com.toir.mapper;

import com.toir.dto.actualcostrouteoverride.ActualCostReviewRouteOverrideResponseDto;
import com.toir.entity.projects.ActualCost;
import com.toir.entity.projects.ActualCostReviewRouteOverride;
import com.toir.enums.ActualCostStatus;
import com.toir.repository.CostCategoryRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.contarctor.ContractorWorkRepository;
import com.toir.repository.department.DepartmentRepository;
import com.toir.repository.projects.FinancialApprovalRuleRepository;
import com.toir.repository.users.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class ActualCostReviewRouteOverrideResponseMapperTest {

    @Mock
    WorkOrderRepository workOrderRepository;

    @Mock
    ContractorWorkRepository contractorWorkRepository;

    @Mock
    DepartmentRepository departmentRepository;

    @Mock
    CostCategoryRepository costCategoryRepository;

    @Mock
    FinancialApprovalRuleRepository financialApprovalRuleRepository;

    @Mock
    UserRepository userRepository;

    @InjectMocks
    ActualCostReviewRouteOverrideResponseMapper mapper;

    @Test
    void toResponseWithMissingActualCostReturnsStableFallback() {
        UUID overrideId = UUID.randomUUID();
        UUID actualCostId = UUID.randomUUID();

        ActualCostReviewRouteOverride override = new ActualCostReviewRouteOverride();
        ReflectionTestUtils.setField(override, "id", overrideId);
        override.setActualCostId(actualCostId);
        override.setApprovalRoleCode("FIN_MANAGER");
        override.setEscalationRoleCode(null);
        override.setComment("fallback");
        override.setThresholdHours(24);
        override.setActive(true);

        ActualCostReviewRouteOverrideResponseDto response = mapper.toResponse(override, null);

        assertThat(response.id()).isEqualTo(overrideId);
        assertThat(response.actualCost()).isNotNull();
        assertThat(response.actualCost().id()).isEqualTo(actualCostId);
        assertThat(response.actualCost().routeSource()).isEqualTo("OVERRIDE");
        assertThat(response.actualCost().department()).isNotNull();
        assertThat(response.actualCost().contractorWork()).isNotNull();
        assertThat(response.createdBy()).isNotNull();
        assertThat(response.deactivatedBy()).isNotNull();
    }

    @Test
    void toResponseWithPartialActualCostDataDoesNotThrowAndReturnsStableNestedObjects() {
        UUID overrideId = UUID.randomUUID();
        UUID actualCostId = UUID.randomUUID();

        ActualCostReviewRouteOverride override = new ActualCostReviewRouteOverride();
        ReflectionTestUtils.setField(override, "id", overrideId);
        override.setActualCostId(actualCostId);
        override.setApprovalRoleCode("FIN_MANAGER");
        override.setComment("partial");
        override.setThresholdHours(24);
        override.setActive(true);

        ActualCost actualCost = new ActualCost();
        ReflectionTestUtils.setField(actualCost, "id", actualCostId);
        actualCost.setStatus(ActualCostStatus.PENDING);
        actualCost.setCostCategoryId(null);
        actualCost.setReviewedAt(null);
        actualCost.setReviewComment(null);
        actualCost.setNotes(null);
        actualCost.setAmount(42.0);
        actualCost.setCostDate(Instant.now());

        ActualCostReviewRouteOverrideResponseDto response = mapper.toResponse(override, actualCost);

        assertThat(response.actualCost()).isNotNull();
        assertThat(response.actualCost().id()).isEqualTo(actualCostId);
        assertThat(response.actualCost().costCategory()).isNotNull();
        assertThat(response.actualCost().approvalRule()).isNotNull();
        assertThat(response.actualCost().workOrder()).isNotNull();
        assertThat(response.actualCost().contractorWork()).isNotNull();
    }
}
