package com.toir.controller;

import com.toir.dto.pprplanning.CompletePprTaskRequest;
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
import com.toir.security.AuthenticatedUser;
import com.toir.security.ScopeAccessService;
import com.toir.service.PprGeneratorService;
import com.toir.service.PprPlanService;

import com.toir.util.PaginationUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ppr-plans")
@Tag(name = "ppr-plans")
public class PprPlanController {

    private static final String PPR_PLAN_READ_AUTH = "hasAnyAuthority('read','PPR_PLAN_READ','SYSTEM_ADMIN','*')";
    private static final String PPR_PLAN_CREATE_AUTH = "hasAnyAuthority('PPR_PLAN_CREATE','SYSTEM_ADMIN','*')";
    private static final String PPR_PLAN_UPDATE_AUTH = "hasAnyAuthority('PPR_PLAN_UPDATE','SYSTEM_ADMIN','*')";
    private static final String PPR_PLAN_DELETE_AUTH = "hasAnyAuthority('PPR_PLAN_DELETE','SYSTEM_ADMIN','*')";
    private static final String PPR_PLAN_GENERATE_AUTH = "hasAnyAuthority('PPR_PLAN_GENERATE','SYSTEM_ADMIN','*')";
    private static final String PPR_WORK_ORDER_GENERATE_AUTH = "(" + PPR_PLAN_GENERATE_AUTH + ")"
            + " and hasAnyAuthority('WORK_ORDER_CREATE','SYSTEM_ADMIN','*')"
            + " and (hasAuthority('SYSTEM_ADMIN') or (!hasAuthority('VIEWER') and !hasAuthority('CONTRACTOR')))";
    private static final String PPR_TASK_READ_AUTH = "hasAnyAuthority('PPR_TASK_READ','SYSTEM_ADMIN','*')";
    private static final String PPR_TASK_CREATE_AUTH = "hasAnyAuthority('PPR_TASK_CREATE','SYSTEM_ADMIN','*')";
    private static final String PPR_TASK_POSTPONE_AUTH = "hasAnyAuthority('PPR_TASK_POSTPONE','SYSTEM_ADMIN','*')";
    private static final String PPR_TASK_APPROVE_AUTH = "hasAnyAuthority('PPR_TASK_APPROVE','SYSTEM_ADMIN','*')";
    private static final String PPR_TASK_START_AUTH = "hasAnyAuthority('PPR_TASK_START','SYSTEM_ADMIN','*')";
    private static final String PPR_TASK_COMPLETE_AUTH = "hasAnyAuthority('PPR_TASK_COMPLETE','SYSTEM_ADMIN','*')";
    private static final String PPR_TASK_CANCEL_AUTH = "hasAnyAuthority('PPR_TASK_CANCEL','SYSTEM_ADMIN','*')";

    private final PprPlanService service;
    private final PprGeneratorService generatorService;
    private final PprPlanRepository planRepository;
    private final PprTaskRepository taskRepository;
    private final ScopeAccessService scopeAccessService;

    @Autowired
    public PprPlanController(PprPlanService service,
                             PprGeneratorService generatorService,
                             PprPlanRepository planRepository,
                             PprTaskRepository taskRepository,
                             ScopeAccessService scopeAccessService) {
        this.service = service;
        this.generatorService = generatorService;
        this.planRepository = planRepository;
        this.taskRepository = taskRepository;
        this.scopeAccessService = scopeAccessService;
    }

    @GetMapping
    @PreAuthorize(PPR_PLAN_READ_AUTH)
    @Operation(summary = "List PPR plans", description = "Always returns a paginated response wrapper with data in content. "
            + "When both page and size are provided, returns the existing paginated response. "
            + "When both are omitted, returns all matching PPR plans in the same wrapper. "
            + "Providing only one pagination parameter is rejected.")
    public ResponseEntity<Page<PprPlanDto>> list(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer day,
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) UUID equipmentId,
            @Parameter(description = "Optional page index. Must be provided together with size. Omit both page and size to return all matching plans in the same response wrapper.") @RequestParam(required = false) Integer page,
            @Parameter(description = "Optional page size. Must be provided together with page. Omit both page and size to return all matching plans in the same response wrapper.") @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false, defaultValue = "asc") String sortDir) {
        UUID scopedDepartmentId = scopedDepartment(departmentId);
        boolean sortingRequested = sortBy != null && !sortBy.isBlank();
        if (page == null && size == null) {
            if (equipmentId == null) {
                if (!sortingRequested) {
                    return ResponseEntity.ok(service.findAllUnpaged(year, month, day, scopedDepartmentId));
                }
                return ResponseEntity.ok(service.findAllUnpaged(year, month, day, scopedDepartmentId, sortBy, sortDir));
            }
            if (!sortingRequested) {
                return ResponseEntity.ok(service.findAllUnpaged(year, month, day, scopedDepartmentId, equipmentId));
            }
            return ResponseEntity.ok(service.findAllUnpaged(year, month, day, scopedDepartmentId, equipmentId, sortBy, sortDir));
        }
        if (page == null || size == null) {
            throw RestException.badRequest("Both page and size must be provided for paginated PPR plan list");
        }
        if (equipmentId == null) {
            if (!sortingRequested) {
                return ResponseEntity.ok(service.findAll(year, month, day, scopedDepartmentId, page, size));
            }
            return ResponseEntity.ok(service.findAll(year, month, day, scopedDepartmentId, page, size, sortBy, sortDir));
        }
        if (!sortingRequested) {
            return ResponseEntity.ok(service.findAll(year, month, day, scopedDepartmentId, equipmentId, page, size));
        }
        return ResponseEntity.ok(service.findAll(year, month, day, scopedDepartmentId, equipmentId, page, size, sortBy, sortDir));
    }

    @GetMapping("/stats")
    @PreAuthorize(PPR_PLAN_READ_AUTH)
    public ResponseEntity<PprPlanStatsResponse> stats(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer day,
            @RequestParam(required = false) UUID departmentId) {
        return ResponseEntity.ok(service.getStats(year, month, day, scopedDepartment(departmentId)));
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
        PprPlanRequest scopedRequest = requestWithScopedDepartment(request);
        assertCanAccessRequestedDepartment(scopedRequest.departmentId());
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(scopedRequest));
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

    @PostMapping("/{id}/generate")
    @PreAuthorize(PPR_PLAN_GENERATE_AUTH)
    public ResponseEntity<PprGeneratorService.GenerationResult> generate(@PathVariable UUID id) {
        assertCanAccessPlan(planOrThrow(id));
        return ResponseEntity.ok(generatorService.generateForPlan(id));
    }

    @PostMapping("/{id}/work-orders/generate")
    @PreAuthorize(PPR_WORK_ORDER_GENERATE_AUTH)
    public ResponseEntity<PprGeneratorService.WorkOrderGenerationResult> generateWorkOrders(
            @PathVariable UUID id,
            @RequestParam(required = false) UUID createdById) {
        assertCanAccessPlan(planOrThrow(id));
        UUID effectiveCreatedById = currentUserId();
        return ResponseEntity.ok(generatorService.generateWorkOrdersForPlan(id, effectiveCreatedById));
    }

    @GetMapping("/{id}/tasks")
    @PreAuthorize(PPR_TASK_READ_AUTH)
    public ResponseEntity<Page<PprTaskDto>> tasks(@PathVariable UUID id, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
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
    public ResponseEntity<PprTaskDto> postponeTask(@PathVariable UUID taskId,
            @Valid @RequestBody PostponeTaskRequest request) {
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
            @RequestParam(required = false) Double actualLaborHours,
            @Valid @RequestBody(required = false) CompletePprTaskRequest request) {
        assertCanAccessTask(taskOrThrow(taskId));
        CompletePprTaskRequest effectiveRequest = request == null
                ? new CompletePprTaskRequest(actualLaborHours, null)
                : new CompletePprTaskRequest(
                        request.actualLaborHours() == null ? actualLaborHours : request.actualLaborHours(),
                        request.materialUsages());
        return ResponseEntity.ok(service.completeTask(taskId, effectiveRequest));
    }

    @PostMapping("/tasks/{taskId}/cancel")
    @PreAuthorize(PPR_TASK_CANCEL_AUTH)
    public ResponseEntity<PprTaskDto> cancelTask(
            @PathVariable UUID taskId,
            @RequestParam(required = false) String reason) {
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

    private UUID currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            return null;
        }
        if (user == null || user.id() == null || user.id().isBlank()) {
            return null;
        }
        return UUID.fromString(user.id());
    }

    private PprPlanRequest requestWithScopedDepartment(PprPlanRequest request) {
        if (request.departmentId() != null || scopeAccessService.isScopeAdmin()) {
            return request;
        }
        UUID currentDepartmentId = scopeAccessService.currentDepartmentIdOrNull();
        if (currentDepartmentId == null) {
            return request;
        }
        return new PprPlanRequest(
                request.name(),
                currentDepartmentId,
                null,
                request.notes(),
                request.fromDate(),
                request.toDate(),
                request.pprType(),
                request.scheduleType(),
                request.frequency(),
                request.intervalHours(),
                request.scopeType(),
                request.equipmentIds(),
                request.equipmentTypeIds(),
                request.regulationIds()
        );
    }
}
