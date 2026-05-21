package com.toir.controller;

import com.toir.dto.pprplanning.PostponeTaskRequest;
import com.toir.dto.pprplanning.PprPlanDto;
import com.toir.dto.pprplanning.PprPlanRequest;
import com.toir.dto.pprplanning.PprPlanStatsResponse;
import com.toir.dto.pprplanning.PprTaskDto;
import com.toir.dto.pprplanning.PprTaskRequest;
import com.toir.entity.PprPlan;
import com.toir.entity.PprTask;
import com.toir.exception.RestException;
import com.toir.repository.PprPlanRepository;
import com.toir.repository.PprTaskRepository;
import com.toir.security.ScopeAccessService;
import com.toir.service.PprGeneratorService;
import com.toir.service.PprPlanService;
import com.toir.service.ApprovalService;
import com.toir.util.PaginationUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ppr-plans")
@Tag(name = "ppr-plans")
@RequiredArgsConstructor
public class PprPlanController {

    private static final String PPR_PLAN_READ_AUTH =
            "hasAnyAuthority('read','PPR_PLAN_READ','SYSTEM_ADMIN','*')";
    private static final String PPR_PLAN_CREATE_AUTH =
            "hasAnyAuthority('PPR_PLAN_CREATE','SYSTEM_ADMIN','*')";
    private static final String PPR_PLAN_UPDATE_AUTH =
            "hasAnyAuthority('PPR_PLAN_UPDATE','SYSTEM_ADMIN','*')";
    private static final String PPR_PLAN_DELETE_AUTH =
            "hasAnyAuthority('PPR_PLAN_DELETE','SYSTEM_ADMIN','*')";
    private static final String PPR_PLAN_APPROVE_AUTH =
            "hasAnyAuthority('PPR_PLAN_APPROVE','SYSTEM_ADMIN','*')";
    private static final String PPR_PLAN_GENERATE_AUTH =
            "hasAnyAuthority('PPR_PLAN_GENERATE','SYSTEM_ADMIN','*')";
    private static final String PPR_TASK_READ_AUTH =
            "hasAnyAuthority('PPR_TASK_READ','SYSTEM_ADMIN','*')";
    private static final String PPR_TASK_CREATE_AUTH =
            "hasAnyAuthority('PPR_TASK_CREATE','SYSTEM_ADMIN','*')";
    private static final String PPR_TASK_POSTPONE_AUTH =
            "hasAnyAuthority('PPR_TASK_POSTPONE','SYSTEM_ADMIN','*')";
    private static final String PPR_TASK_APPROVE_AUTH =
            "hasAnyAuthority('PPR_TASK_APPROVE','SYSTEM_ADMIN','*')";
    private static final String PPR_TASK_START_AUTH =
            "hasAnyAuthority('PPR_TASK_START','SYSTEM_ADMIN','*')";
    private static final String PPR_TASK_COMPLETE_AUTH =
            "hasAnyAuthority('PPR_TASK_COMPLETE','SYSTEM_ADMIN','*')";
    private static final String PPR_TASK_CANCEL_AUTH =
            "hasAnyAuthority('PPR_TASK_CANCEL','SYSTEM_ADMIN','*')";

    private final PprPlanService service;
    private final ApprovalService approvalService;
    private final PprGeneratorService generatorService;
    private final PprPlanRepository planRepository;
    private final PprTaskRepository taskRepository;
    private final ScopeAccessService scopeAccessService;

    @GetMapping
    @PreAuthorize(PPR_PLAN_READ_AUTH)
    public ResponseEntity<Page<PprPlanDto>> list(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(service.findAll(year, month, scopedDepartment(departmentId), page, size));
    }

    @GetMapping("/stats")
    @PreAuthorize(PPR_PLAN_READ_AUTH)
    public ResponseEntity<PprPlanStatsResponse> stats(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) UUID departmentId
    ) {
        return ResponseEntity.ok(service.getStats(year, month, scopedDepartment(departmentId)));
    }

    @GetMapping("/{id}")
    @PreAuthorize(PPR_PLAN_READ_AUTH)
    public ResponseEntity<PprPlanDto> get(@PathVariable UUID id) {
        assertCanAccessPlan(planOrThrow(id));
        return ResponseEntity.ok(service.findById(id));
    }

    @PostMapping
    @PreAuthorize(PPR_PLAN_CREATE_AUTH)
    public ResponseEntity<PprPlanDto> create(@Valid @RequestBody PprPlanRequest request) {
        assertCanAccessRequestedDepartment(request.departmentId());
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PatchMapping("/{id}")
    @PreAuthorize(PPR_PLAN_UPDATE_AUTH)
    public ResponseEntity<PprPlanDto> update(@PathVariable UUID id, @Valid @RequestBody PprPlanRequest request) {
        assertCanAccessPlan(planOrThrow(id));
        assertCanAccessRequestedDepartment(request.departmentId());
        return ResponseEntity.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(PPR_PLAN_DELETE_AUTH)
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        assertCanAccessPlan(planOrThrow(id));
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize(PPR_PLAN_APPROVE_AUTH)
    public ResponseEntity<PprPlanDto> approve(@PathVariable UUID id, @RequestParam UUID approverId) {
        PprPlan plan = planOrThrow(id);
        assertCanAccessPlan(plan);
        if (plan.getStatus() != com.toir.enums.PlanStatus.DRAFT
                && plan.getStatus() != com.toir.enums.PlanStatus.GENERATED) {
            throw RestException.badRequest("Only DRAFT/GENERATED plans can be approved");
        }
        approvalService.createOrReuseApprovalForDocument(
                "PPR_PLAN",
                id,
                approverId,
                approverId,
                "PPR_PLAN_APPROVER",
                "PPR plan approval: " + plan.getCode(),
                "Approval workflow request for PPR plan " + plan.getCode()
        );
        return ResponseEntity.ok(service.findById(id));
    }

    @PostMapping("/{id}/generate")
    @PreAuthorize(PPR_PLAN_GENERATE_AUTH)
    public ResponseEntity<PprGeneratorService.GenerationResult> generate(@PathVariable UUID id) {
        assertCanAccessPlan(planOrThrow(id));
        return ResponseEntity.ok(generatorService.generateForPlan(id));
    }

    @GetMapping("/{id}/tasks")
    @PreAuthorize(PPR_TASK_READ_AUTH)
    public ResponseEntity<Page<PprTaskDto>> tasks(@PathVariable UUID id, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        assertCanAccessPlan(planOrThrow(id));
        return ResponseEntity.ok(PaginationUtils.page(service.findTasksByPlan(id), page, size));
    }

    @PostMapping("/{id}/tasks")
    @PreAuthorize(PPR_TASK_CREATE_AUTH)
    public ResponseEntity<PprTaskDto> addTask(@PathVariable UUID id, @Valid @RequestBody PprTaskRequest request) {
        assertCanAccessPlan(planOrThrow(id));
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addTask(id, request));
    }

    @PostMapping("/tasks/{taskId}/postpone")
    @PreAuthorize(PPR_TASK_POSTPONE_AUTH)
    public ResponseEntity<PprTaskDto> postponeTask(@PathVariable UUID taskId, @Valid @RequestBody PostponeTaskRequest request) {
        assertCanAccessTask(taskOrThrow(taskId));
        return ResponseEntity.ok(service.postponeTask(taskId, request));
    }

    @PostMapping("/tasks/{taskId}/approve")
    @PreAuthorize(PPR_TASK_APPROVE_AUTH)
    public ResponseEntity<PprTaskDto> approveTask(@PathVariable UUID taskId) {
        assertCanAccessTask(taskOrThrow(taskId));
        return ResponseEntity.ok(service.approveTask(taskId));
    }

    @PostMapping("/tasks/{taskId}/start")
    @PreAuthorize(PPR_TASK_START_AUTH)
    public ResponseEntity<PprTaskDto> startTask(@PathVariable UUID taskId) {
        assertCanAccessTask(taskOrThrow(taskId));
        return ResponseEntity.ok(service.startTask(taskId));
    }

    @PostMapping("/tasks/{taskId}/complete")
    @PreAuthorize(PPR_TASK_COMPLETE_AUTH)
    public ResponseEntity<PprTaskDto> completeTask(
            @PathVariable UUID taskId,
            @RequestParam(required = false) Double actualLaborHours
    ) {
        assertCanAccessTask(taskOrThrow(taskId));
        return ResponseEntity.ok(service.completeTask(taskId, actualLaborHours));
    }

    @PostMapping("/tasks/{taskId}/cancel")
    @PreAuthorize(PPR_TASK_CANCEL_AUTH)
    public ResponseEntity<PprTaskDto> cancelTask(
            @PathVariable UUID taskId,
            @RequestParam(required = false) String reason
    ) {
        assertCanAccessTask(taskOrThrow(taskId));
        return ResponseEntity.ok(service.cancelTask(taskId, reason));
    }

    private UUID scopedDepartment(UUID requestedDepartmentId) {
        UUID scopedDepartmentId = scopeAccessService.enforceDepartmentScope(requestedDepartmentId);
        if (!scopeAccessService.isScopeAdmin() && scopeAccessService.currentDepartmentIdOrNull() == null) {
            throw new AccessDeniedException("Access denied by PPR department scope");
        }
        return scopedDepartmentId;
    }

    private PprPlan planOrThrow(UUID id) {
        return planRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("PPR plan not found: " + id));
    }

    private PprTask taskOrThrow(UUID taskId) {
        return taskRepository.findByIdAndIsDeletedFalseWithPlan(taskId)
                .orElseThrow(() -> RestException.notFound("PPR task not found: " + taskId));
    }

    private void assertCanAccessTask(PprTask task) {
        assertCanAccessPlan(task.getPlan());
    }

    private void assertCanAccessPlan(PprPlan plan) {
        if (plan.getDepartmentId() == null) {
            if (!scopeAccessService.isScopeAdmin()) {
                throw new AccessDeniedException("Access denied by PPR department scope");
            }
            return;
        }
        scopeAccessService.assertCanAccessDepartment(plan.getDepartmentId());
    }

    private void assertCanAccessRequestedDepartment(UUID departmentId) {
        if (departmentId == null) {
            if (!scopeAccessService.isScopeAdmin()) {
                throw new AccessDeniedException("Access denied by PPR department scope");
            }
            return;
        }
        scopeAccessService.assertCanAccessDepartment(departmentId);
    }
}
