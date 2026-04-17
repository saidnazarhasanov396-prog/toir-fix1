package com.toir.workexecution;

import com.toir.common.exception.RestException;
import com.toir.common.web.PaginatedResponse;
import com.toir.workexecution.dto.WorkExecutionDto;
import com.toir.workexecution.dto.ExecutionLogDto;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class WorkExecutionService {

    private final WorkExecutionRepository repository;

    public WorkExecutionService(WorkExecutionRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<WorkExecutionDto> findByWorkOrder(UUID workOrderId) {
        return repository.findAllByWorkOrderIdOrderByStartedAtAsc(workOrderId).stream()
                .map(WorkExecutionDto::from).toList();
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<ExecutionLogDto> findExecutionLogs(int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.clamp(pageSize, 1, 500);

        var result = repository.findAllByOrderByStartedAtDesc(PageRequest.of(safePage - 1, safePageSize));
        return new PaginatedResponse<>(
                result.getContent().stream().map(ExecutionLogDto::from).toList(),
                new PaginatedResponse.Meta(safePage, safePageSize, (int) result.getTotalElements())
        );
    }

    public WorkExecutionDto start(UUID workOrderId, WorkExecutionDto r) {
        WorkExecution e = new WorkExecution();
        e.setWorkOrderId(workOrderId);
        e.setPerformerId(r.performerId());
        e.setStartedAt(r.startedAt() != null ? r.startedAt() : Instant.now());
        e.setNotes(r.notes());
        return WorkExecutionDto.from(repository.save(e));
    }

    public WorkExecutionDto end(UUID id, WorkExecutionDto r) {
        WorkExecution e = repository.findById(id)
                .orElseThrow(() -> RestException.notFound("Execution not found: " + id));
        e.setEndedAt(r.endedAt() != null ? r.endedAt() : Instant.now());
        e.setResult(r.result());
        if (r.notes() != null) e.setNotes(r.notes());
        return WorkExecutionDto.from(e);
    }
}
