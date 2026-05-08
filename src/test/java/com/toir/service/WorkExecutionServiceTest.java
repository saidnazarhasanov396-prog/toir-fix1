package com.toir.service;

import com.toir.dto.workexecution.WorkExecutionDto;
import com.toir.entity.WorkExecution;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.enums.WorkOrderStatus;
import com.toir.enums.WorkOrderType;
import com.toir.enums.WorkType;
import com.toir.exception.RestException;
import com.toir.repository.WorkExecutionRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.util.AuditBuilderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkExecutionServiceTest {

    @Mock
    WorkExecutionRepository repository;

    @Mock
    WorkOrderRepository workOrderRepository;

    @Mock
    AuditBuilderService auditBuilderService;

    @InjectMocks
    WorkExecutionService service;

    @Test
    void startShouldFailWhenWorkOrderIsNotInProgress() {
        UUID workOrderId = UUID.randomUUID();
        WorkOrder workOrder = workOrder(workOrderId, WorkOrderStatus.APPROVED);

        when(workOrderRepository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));

        assertThatThrownBy(() -> service.start(workOrderId, new WorkExecutionDto(null, workOrderId, UUID.randomUUID(), "notes", null, null, null)))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("IN_PROGRESS");
    }

    @Test
    void startShouldSucceedWhenWorkOrderIsInProgress() {
        UUID workOrderId = UUID.randomUUID();
        WorkOrder workOrder = workOrder(workOrderId, WorkOrderStatus.IN_PROGRESS);

        when(workOrderRepository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(repository.save(any(WorkExecution.class)))
                .thenAnswer(invocation -> {
                    WorkExecution execution = invocation.getArgument(0);
                    ReflectionTestUtils.setField(execution, "id", UUID.randomUUID());
                    return execution;
                });

        service.start(workOrderId, new WorkExecutionDto(null, workOrderId, UUID.randomUUID(), "notes", null, null, null));

        verify(repository).save(any(WorkExecution.class));
    }

    private WorkOrder workOrder(UUID id, WorkOrderStatus status) {
        WorkOrder workOrder = new WorkOrder();
        ReflectionTestUtils.setField(workOrder, "id", id);
        workOrder.setNumber("WO-EXEC-" + id);
        workOrder.setTitle("Execution test");
        workOrder.setEquipmentId(UUID.randomUUID());
        workOrder.setDepartmentId(UUID.randomUUID());
        workOrder.setType(WorkOrderType.PLANNED);
        workOrder.setWorkType(WorkType.REPLACEMENT);
        workOrder.setStatus(status);
        workOrder.setCreatedById(UUID.randomUUID());
        return workOrder;
    }
}
