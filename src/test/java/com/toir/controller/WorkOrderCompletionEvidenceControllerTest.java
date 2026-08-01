package com.toir.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.toir.entity.WorkOrderCompletionEvidence;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.enums.CompletionEvidenceType;
import com.toir.repository.WorkOrderCompletionEvidenceRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.security.ScopeAccessService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkOrderCompletionEvidenceControllerTest {

    @Mock WorkOrderCompletionEvidenceRepository evidence;
    @Mock WorkOrderRepository workOrders;
    @Mock ScopeAccessService scopeAccessService;

    @Test
    void listsPersistedEvidenceAfterCheckingWorkOrderScope() {
        UUID workOrderId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        WorkOrder workOrder = new WorkOrder();
        workOrder.setId(workOrderId);
        workOrder.setDepartmentId(departmentId);
        WorkOrderCompletionEvidence item = new WorkOrderCompletionEvidence();
        item.setId(UUID.randomUUID());
        item.setWorkOrderId(workOrderId);
        item.setEvidenceType(CompletionEvidenceType.AFTER_PHOTO);
        item.setFileAssetId(fileId);
        item.setCapturedAt(Instant.parse("2026-08-01T08:00:00Z"));
        when(workOrders.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(evidence.findAllByWorkOrderIdAndIsDeletedFalse(workOrderId)).thenReturn(List.of(item));

        var response = new WorkOrderCompletionEvidenceController(evidence, workOrders, scopeAccessService)
                .list(workOrderId);

        verify(scopeAccessService).assertCanAccessDepartment(departmentId);
        assertThat(response.getBody()).singleElement().satisfies(dto -> {
            assertThat(dto.type()).isEqualTo(CompletionEvidenceType.AFTER_PHOTO);
            assertThat(dto.fileAssetId()).isEqualTo(fileId);
        });
    }
}
