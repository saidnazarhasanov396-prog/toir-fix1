package com.toir.service;
import com.toir.entity.WorkExecution;
import com.toir.repository.WorkExecutionRepository;

import com.toir.config.PaginatedResponse;
import com.toir.exception.RestException;
import com.toir.dto.workexecution.ExecutionLogDto;
import com.toir.dto.workexecution.WorkExecutionDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WorkExecutionService {

    private final WorkExecutionRepository repository;


    @Transactional(readOnly = true)
    public List<WorkExecutionDto> findByWorkOrder(UUID workOrderId) {
        return repository.findAllByWorkOrderIdAndIsDeletedFalseOrderByStartedAtAsc(workOrderId).stream()
                .map(WorkExecutionDto::from).toList();
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<ExecutionLogDto> findExecutionLogs(int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = clamp(pageSize, 1, 500);

        var result = repository.findAllByIsDeletedFalseOrderByStartedAtDesc(PageRequest.of(safePage - 1, safePageSize));
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
        WorkExecution e = repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Execution not found: " + id));
        e.setEndedAt(r.endedAt() != null ? r.endedAt() : Instant.now());
        e.setResult(r.result());
        if (r.notes() != null) e.setNotes(r.notes());
        return WorkExecutionDto.from(e);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
