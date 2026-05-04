package com.toir.service;
import com.toir.entity.WorkExecution;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.enums.WorkOrderStatus;
import com.toir.repository.WorkExecutionRepository;

import com.toir.exception.RestException;
import com.toir.repository.WorkOrderRepository;
import com.toir.util.PaginationUtils;
import com.toir.dto.workexecution.ExecutionLogDto;
import com.toir.dto.workexecution.WorkExecutionDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class WorkExecutionService {

    private final WorkExecutionRepository repository;
    private final WorkOrderRepository workOrderRepository;


    @Transactional(readOnly = true)
    public List<WorkExecutionDto> findByWorkOrder(UUID workOrderId) {
        return repository.findAllByWorkOrderIdAndIsDeletedFalseOrderByStartedAtAsc(workOrderId).stream()
                .map(WorkExecutionDto::from).toList();
    }

    @Transactional(readOnly = true)
    public Page<ExecutionLogDto> findExecutionLogs(UUID workOrderId, int page, int pageSize) {
        var result = repository.findExecutionLogs(workOrderId, PaginationUtils.pageRequest(page, pageSize));
        return result.map(ExecutionLogDto::from);
    }

    @Transactional
    public WorkExecutionDto start(UUID workOrderId, WorkExecutionDto r) {
        Instant startedAt = r.startedAt() != null ? r.startedAt() : Instant.now();
        WorkOrder workOrder = workOrderRepository.findByIdAndIsDeletedFalse(workOrderId)
                .orElseThrow(() -> RestException.notFound("Work order not found: " + workOrderId));
        if (workOrder.getStatus() == WorkOrderStatus.COMPLETED || workOrder.getStatus() == WorkOrderStatus.CLOSED) {
            throw RestException.badRequest("Cannot start execution for a completed or closed work order");
        }
        if (workOrder.getStatus() != WorkOrderStatus.IN_PROGRESS) {
            workOrder.setStatus(WorkOrderStatus.IN_PROGRESS);
            workOrder.setStartedAt(startedAt);
        }

        WorkExecution e = new WorkExecution();
        e.setWorkOrderId(workOrderId);
        e.setPerformerId(r.performerId());
        e.setStartedAt(startedAt);
        e.setNotes(r.notes());
        return WorkExecutionDto.from(repository.save(e));
    }

    @Transactional
    public WorkExecutionDto end(UUID id, WorkExecutionDto r) {
        WorkExecution e = repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Execution not found: " + id));
        e.setEndedAt(r.endedAt() != null ? r.endedAt() : Instant.now());
        e.setResult(r.result());
        if (r.notes() != null) e.setNotes(r.notes());
        return WorkExecutionDto.from(e);
    }

}
