package com.toir.repository;

import com.toir.entity.maintenance.WorkOrder;
import com.toir.enums.PriorityLevel;
import com.toir.enums.WorkOrderStatus;
import com.toir.enums.WorkOrderType;
import com.toir.enums.WorkType;
import com.toir.test.RepositorySliceTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@RepositorySliceTest
class WorkOrderRepositoryCandidateSearchTest {

    @Autowired
    WorkOrderRepository repository;

    @Test
    void candidateQueryExcludesDeletedWorkOrders() {
        WorkOrder live = saveWorkOrder("WO-CAND-001", "Pump repair", false);
        saveWorkOrder("WO-CAND-002", "Deleted pump repair", true);

        Page<WorkOrder> page = repository.findRepairCampaignWorkOrderCandidates(
                null, PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(WorkOrder::getId).containsExactly(live.getId());
    }

    @Test
    void candidateQuerySearchesNumberAndTitleCaseInsensitively() {
        WorkOrder byTitle = saveWorkOrder("WO-CAND-101", "Main PUMP overhaul", false);
        WorkOrder byNumber = saveWorkOrder("WO-CAND-202", "Motor check", false);
        saveWorkOrder("WO-CAND-303", "Bearing swap", false);

        Page<WorkOrder> pumpMatches = repository.findRepairCampaignWorkOrderCandidates(
                "pump", PageRequest.of(0, 20));
        Page<WorkOrder> numberMatches = repository.findRepairCampaignWorkOrderCandidates(
                "cand-202", PageRequest.of(0, 20));

        assertThat(pumpMatches.getContent()).extracting(WorkOrder::getId)
                .containsExactly(byTitle.getId());
        assertThat(numberMatches.getContent()).extracting(WorkOrder::getId)
                .containsExactly(byNumber.getId());
    }

    @Test
    void candidateQueryPaginatesNonDeletedRows() {
        saveWorkOrder("WO-CAND-401", "Pump one", false);
        saveWorkOrder("WO-CAND-402", "Pump two", false);
        saveWorkOrder("WO-CAND-403", "Pump three", false);

        Page<WorkOrder> firstPage = repository.findRepairCampaignWorkOrderCandidates(
                "pump", PageRequest.of(0, 2));
        Page<WorkOrder> secondPage = repository.findRepairCampaignWorkOrderCandidates(
                "pump", PageRequest.of(1, 2));

        assertThat(firstPage.getTotalElements()).isEqualTo(3);
        assertThat(firstPage.getContent()).hasSize(2);
        assertThat(secondPage.getContent()).hasSize(1);
        assertThat(secondPage.isLast()).isTrue();
    }

    private WorkOrder saveWorkOrder(String number, String title, boolean deleted) {
        WorkOrder workOrder = new WorkOrder();
        workOrder.setNumber(number);
        workOrder.setTitle(title);
        workOrder.setEquipmentId(UUID.randomUUID());
        workOrder.setDepartmentId(UUID.randomUUID());
        workOrder.setStatus(WorkOrderStatus.DRAFT);
        workOrder.setType(WorkOrderType.OVERHAUL);
        workOrder.setWorkType(WorkType.REPAIR);
        workOrder.setPriority(PriorityLevel.MEDIUM);
        workOrder.setCreatedById(UUID.randomUUID());
        workOrder.setDeleted(deleted);
        return repository.save(workOrder);
    }
}
