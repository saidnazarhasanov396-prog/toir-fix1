package com.toir.service;

import com.toir.dto.operationalissue.OperationalIssueTarget;
import com.toir.entity.OperationalIssue;
import com.toir.entity.PprPlan;
import com.toir.entity.PprTask;
import com.toir.entity.contractors.ContractorWork;
import com.toir.enums.OperationalIssueTargetType;
import com.toir.enums.OperationalIssueType;
import com.toir.repository.PprTaskRepository;
import com.toir.repository.contarctor.ContractorWorkRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OperationalIssueTargetResolverTest {

    @Mock
    PprTaskRepository pprTaskRepository;

    @Mock
    ContractorWorkRepository contractorWorkRepository;

    @InjectMocks
    OperationalIssueTargetResolver resolver;

    @Test
    void resolvesRepairRequestAndWorkOrderFromStructuredSourceReferences() {
        UUID repairRequestId = UUID.randomUUID();
        UUID workOrderId = UUID.randomUUID();
        OperationalIssue repairRequest = issue(
                OperationalIssueType.OVERDUE_REPAIR_REQUEST,
                "RepairRequest",
                repairRequestId,
                Map.of("requestNumber", "RR-001")
        );
        OperationalIssue workOrder = issue(
                OperationalIssueType.OVERDUE_WORK_ORDER,
                "WorkOrder",
                workOrderId,
                Map.of("workOrderNumber", "WO-001")
        );

        Map<UUID, OperationalIssueTarget> targets = resolver.resolveAll(List.of(repairRequest, workOrder));

        assertThat(targets.get(repairRequest.getId())).isEqualTo(new OperationalIssueTarget(
                OperationalIssueTargetType.REPAIR_REQUEST,
                repairRequestId,
                "RR-001",
                null,
                null
        ));
        assertThat(targets.get(workOrder.getId())).isEqualTo(new OperationalIssueTarget(
                OperationalIssueTargetType.WORK_ORDER,
                workOrderId,
                "WO-001",
                null,
                null
        ));
    }

    @Test
    void resolvesPprTaskWithItsParentPlan() {
        UUID taskId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        OperationalIssue issue = issue(
                OperationalIssueType.OVERDUE_PPR_TASK,
                "PprTask",
                taskId,
                Map.of("pprTaskCode", "PPR-TASK-001")
        );
        PprPlan plan = new PprPlan();
        ReflectionTestUtils.setField(plan, "id", planId);
        PprTask task = new PprTask();
        ReflectionTestUtils.setField(task, "id", taskId);
        task.setPlan(plan);
        when(pprTaskRepository.findAllByIdInAndIsDeletedFalseWithPlan(List.of(taskId)))
                .thenReturn(List.of(task));

        OperationalIssueTarget target = resolver.resolveAll(List.of(issue)).get(issue.getId());

        assertThat(target).isEqualTo(new OperationalIssueTarget(
                OperationalIssueTargetType.PPR_TASK,
                taskId,
                "PPR-TASK-001",
                OperationalIssueTargetType.PPR_PLAN,
                planId
        ));
    }

    @Test
    void resolvesContractorDelayThroughItsStructuredWorkOrderRelation() {
        UUID contractorWorkId = UUID.randomUUID();
        UUID workOrderId = UUID.randomUUID();
        OperationalIssue issue = issue(
                OperationalIssueType.CONTRACTOR_WORK_DELAY,
                "ContractorWork",
                contractorWorkId,
                Map.of("workOrderNumber", "WO-002")
        );
        ContractorWork contractorWork = new ContractorWork();
        ReflectionTestUtils.setField(contractorWork, "id", contractorWorkId);
        contractorWork.setWorkOrderId(workOrderId);
        when(contractorWorkRepository.findAllByIdInAndIsDeletedFalse(List.of(contractorWorkId)))
                .thenReturn(List.of(contractorWork));

        OperationalIssueTarget target = resolver.resolveAll(List.of(issue)).get(issue.getId());

        assertThat(target).isEqualTo(new OperationalIssueTarget(
                OperationalIssueTargetType.WORK_ORDER,
                workOrderId,
                "WO-002",
                null,
                null
        ));
    }

    @Test
    void leavesUnknownAndMissingSourcesWithoutNavigationTargets() {
        OperationalIssue unknown = issue(
                OperationalIssueType.AUTOMATION_FAILURE,
                "UnknownSource",
                UUID.randomUUID(),
                Map.of()
        );
        OperationalIssue missing = issue(
                OperationalIssueType.OVERDUE_WORK_ORDER,
                "WorkOrder",
                null,
                Map.of()
        );

        Map<UUID, OperationalIssueTarget> targets = resolver.resolveAll(List.of(unknown, missing));

        assertThat(targets).doesNotContainKeys(unknown.getId(), missing.getId());
    }

    private OperationalIssue issue(OperationalIssueType type,
                                   String sourceType,
                                   UUID sourceId,
                                   Map<String, Object> metadata) {
        OperationalIssue issue = new OperationalIssue();
        ReflectionTestUtils.setField(issue, "id", UUID.randomUUID());
        issue.setType(type);
        issue.setSourceType(sourceType);
        issue.setSourceId(sourceId);
        issue.setMetadata(metadata);
        return issue;
    }
}
