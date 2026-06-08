package com.toir.service;

import com.toir.dto.repairacceptance.*;
import com.toir.entity.maintenance.RepairAcceptance;
import com.toir.entity.maintenance.RepairAcceptanceDefect;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.enums.*;
import com.toir.exception.RestException;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.maintenance.RepairAcceptanceDefectRepository;
import com.toir.repository.maintenance.RepairAcceptanceRepository;
import com.toir.security.ScopeAccessService;
import com.toir.util.AuditBuilderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RepairAcceptanceService {

    private final RepairAcceptanceRepository repository;
    private final RepairAcceptanceDefectRepository defectRepository;
    private final WorkOrderRepository workOrderRepository;
    private final ScopeAccessService scopeAccessService;
    private final AuditBuilderService auditBuilderService;

    @Transactional(readOnly = true)
    public List<RepairAcceptanceDto> list(UUID workOrderId) {
        WorkOrder workOrder = getWorkOrderForAccess(workOrderId);
        return repository.findAllByWorkOrderIdAndIsDeletedFalseOrderByUpdatedAtDesc(workOrder.getId()).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public RepairAcceptanceDto get(UUID workOrderId, UUID id) {
        getWorkOrderForAccess(workOrderId);
        return toDto(getAcceptanceForWorkOrder(workOrderId, id));
    }

    @Transactional
    public RepairAcceptanceDto create(UUID workOrderId, RepairAcceptanceRequest request) {
        WorkOrder workOrder = getWorkOrderForAccess(workOrderId);
        if (request.stage() == null) {
            throw RestException.badRequest("Acceptance stage is required");
        }
        RepairAcceptance acceptance = new RepairAcceptance();
        acceptance.setWorkOrderId(workOrder.getId());
        acceptance.setStage(request.stage());
        acceptance.setStatus(RepairAcceptanceStatus.DRAFT);
        applyRequest(acceptance, request);
        replaceDefects(acceptance, request.defects());

        RepairAcceptance saved = repository.save(acceptance);
        auditBuilderService.log(
                "repair_acceptance",
                saved.getId().toString(),
                AuditAction.CREATE,
                AuditModule.WORK_ORDER,
                "Repair acceptance created",
                null,
                saved
        );
        return toDto(saved);
    }

    @Transactional
    public RepairAcceptanceDto update(UUID workOrderId, UUID id, RepairAcceptanceRequest request) {
        getWorkOrderForAccess(workOrderId);
        RepairAcceptance acceptance = getAcceptanceForWorkOrder(workOrderId, id);
        if (acceptance.getStatus() == RepairAcceptanceStatus.ACCEPTED) {
            throw RestException.badRequest("Accepted repair acceptance cannot be updated");
        }
        RepairAcceptance before = snapshot(acceptance);
        if (request.stage() != null) {
            acceptance.setStage(request.stage());
        }
        applyRequest(acceptance, request);
        replaceDefects(acceptance, request.defects());

        RepairAcceptance saved = repository.save(acceptance);
        auditBuilderService.log(
                "repair_acceptance",
                saved.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.WORK_ORDER,
                "Repair acceptance updated",
                before,
                saved
        );
        return toDto(saved);
    }

    @Transactional
    public RepairAcceptanceDto startRunIn(UUID workOrderId, UUID id, RepairAcceptanceRunInRequest request) {
        getWorkOrderForAccess(workOrderId);
        RepairAcceptance acceptance = getAcceptanceForWorkOrder(workOrderId, id);
        ensureMutableForDecision(acceptance);
        RepairAcceptance before = snapshot(acceptance);
        acceptance.setRunInRequired(true);
        acceptance.setRunInShiftsRequired(request == null ? acceptance.getRunInShiftsRequired() : request.runInShiftsRequired());
        acceptance.setAcceptanceStartedAt(acceptance.getAcceptanceStartedAt() == null
                ? Instant.now()
                : acceptance.getAcceptanceStartedAt());
        acceptance.setRunInStartedAt(Instant.now());
        acceptance.setStatus(RepairAcceptanceStatus.IN_PROGRESS);
        if (request != null && request.remarks() != null) {
            acceptance.setRemarks(request.remarks());
        }
        return saveUpdate(before, acceptance, "Repair acceptance run-in started");
    }

    @Transactional
    public RepairAcceptanceDto completeRunIn(UUID workOrderId, UUID id, RepairAcceptanceRunInRequest request) {
        getWorkOrderForAccess(workOrderId);
        RepairAcceptance acceptance = getAcceptanceForWorkOrder(workOrderId, id);
        ensureMutableForDecision(acceptance);
        RepairAcceptance before = snapshot(acceptance);
        if (!acceptance.isRunInRequired() || acceptance.getRunInStartedAt() == null) {
            throw RestException.badRequest("Run-in must be started before it can be completed");
        }
        acceptance.setRunInCompletedAt(Instant.now());
        if (request != null && request.remarks() != null) {
            acceptance.setRemarks(request.remarks());
        }
        return saveUpdate(before, acceptance, "Repair acceptance run-in completed");
    }

    @Transactional
    public RepairAcceptanceDto accept(UUID workOrderId, UUID id, RepairAcceptanceDecisionRequest request) {
        getWorkOrderForAccess(workOrderId);
        RepairAcceptance acceptance = getAcceptanceForWorkOrder(workOrderId, id);
        ensureMutableForDecision(acceptance);
        RepairAcceptance before = snapshot(acceptance);
        if (acceptance.isRunInRequired() && acceptance.getRunInCompletedAt() == null) {
            throw RestException.badRequest("Run-in must be completed before acceptance");
        }
        if (acceptance.getStage() == RepairAcceptanceStage.FINAL
                && defectRepository.existsByAcceptance_IdAndCriticalTrueAndStatusAndIsDeletedFalse(
                acceptance.getId(),
                RepairAcceptanceDefectStatus.OPEN
        )) {
            throw RestException.badRequest("Final acceptance cannot be accepted while critical defects are open");
        }
        acceptance.setStatus(RepairAcceptanceStatus.ACCEPTED);
        acceptance.setAcceptanceStartedAt(acceptance.getAcceptanceStartedAt() == null
                ? Instant.now()
                : acceptance.getAcceptanceStartedAt());
        acceptance.setAcceptedAt(Instant.now());
        if (request != null) {
            acceptance.setAcceptedById(request.acceptedById());
            if (request.qualityGrade() != null) {
                acceptance.setQualityGrade(request.qualityGrade());
            }
            if (request.remarks() != null) {
                acceptance.setRemarks(request.remarks());
            }
        }
        return saveUpdate(before, acceptance, "Repair acceptance accepted");
    }

    @Transactional
    public RepairAcceptanceDto reject(UUID workOrderId, UUID id, RepairAcceptanceDecisionRequest request) {
        getWorkOrderForAccess(workOrderId);
        RepairAcceptance acceptance = getAcceptanceForWorkOrder(workOrderId, id);
        ensureMutableForDecision(acceptance);
        RepairAcceptance before = snapshot(acceptance);
        acceptance.setStatus(RepairAcceptanceStatus.REJECTED);
        acceptance.setAcceptanceStartedAt(acceptance.getAcceptanceStartedAt() == null
                ? Instant.now()
                : acceptance.getAcceptanceStartedAt());
        if (request != null) {
            acceptance.setAcceptedById(request.acceptedById());
            acceptance.setQualityGrade(request.qualityGrade() == null
                    ? RepairAcceptanceQualityGrade.NOT_ACCEPTED
                    : request.qualityGrade());
            acceptance.setRemarks(request.remarks());
        }
        return saveUpdate(before, acceptance, "Repair acceptance rejected");
    }

    private RepairAcceptanceDto saveUpdate(RepairAcceptance before, RepairAcceptance acceptance, String message) {
        RepairAcceptance saved = repository.save(acceptance);
        auditBuilderService.log(
                "repair_acceptance",
                saved.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.WORK_ORDER,
                message,
                before,
                saved
        );
        return toDto(saved);
    }

    private WorkOrder getWorkOrderForAccess(UUID workOrderId) {
        WorkOrder workOrder = workOrderRepository.findByIdAndIsDeletedFalse(workOrderId)
                .orElseThrow(() -> RestException.notFound("Work order not found: " + workOrderId));
        if (!scopeAccessService.isScopeAdmin() && !scopeAccessService.canAccessDepartment(workOrder.getDepartmentId())) {
            throw RestException.forbidden("Access denied by work order department scope");
        }
        return workOrder;
    }

    private RepairAcceptance getAcceptanceForWorkOrder(UUID workOrderId, UUID id) {
        RepairAcceptance acceptance = repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Repair acceptance not found: " + id));
        if (!workOrderId.equals(acceptance.getWorkOrderId())) {
            throw RestException.notFound("Repair acceptance not found for work order: " + workOrderId);
        }
        return acceptance;
    }

    private void applyRequest(RepairAcceptance acceptance, RepairAcceptanceRequest request) {
        acceptance.setAcceptedById(request.acceptedById());
        acceptance.setHandedOverById(request.handedOverById());
        acceptance.setRunInRequired(Boolean.TRUE.equals(request.runInRequired()));
        acceptance.setRunInShiftsRequired(request.runInShiftsRequired());
        acceptance.setQualityGrade(request.qualityGrade());
        acceptance.setPerformanceBefore(normalize(request.performanceBefore()));
        acceptance.setPerformanceAfter(normalize(request.performanceAfter()));
        acceptance.setQualityBefore(normalize(request.qualityBefore()));
        acceptance.setQualityAfter(normalize(request.qualityAfter()));
        acceptance.setRemarks(normalize(request.remarks()));
    }

    private void replaceDefects(RepairAcceptance acceptance, List<RepairAcceptanceDefectRequest> requests) {
        acceptance.getDefects().clear();
        if (requests == null || requests.isEmpty()) {
            return;
        }
        requests.forEach(request -> {
            RepairAcceptanceDefect defect = new RepairAcceptanceDefect();
            defect.setAcceptance(acceptance);
            defect.setDefectId(request.defectId());
            defect.setDescription(normalize(request.description()));
            defect.setCritical(request.critical());
            defect.setStatus(request.status() == null ? RepairAcceptanceDefectStatus.OPEN : request.status());
            defect.setRemarks(normalize(request.remarks()));
            if (defect.getStatus() == RepairAcceptanceDefectStatus.RESOLVED) {
                defect.setResolvedAt(Instant.now());
            }
            acceptance.getDefects().add(defect);
        });
    }

    private void ensureMutableForDecision(RepairAcceptance acceptance) {
        if (acceptance.getStatus() == RepairAcceptanceStatus.ACCEPTED
                || acceptance.getStatus() == RepairAcceptanceStatus.CANCELLED) {
            throw RestException.badRequest("Repair acceptance cannot be changed from status " + acceptance.getStatus());
        }
    }

    private RepairAcceptanceDto toDto(RepairAcceptance acceptance) {
        List<RepairAcceptanceDefectDto> defects = defectRepository
                .findAllByAcceptance_IdAndIsDeletedFalseOrderByUpdatedAtDesc(acceptance.getId())
                .stream()
                .map(RepairAcceptanceDefectDto::from)
                .toList();
        return RepairAcceptanceDto.from(acceptance, defects);
    }

    private RepairAcceptance snapshot(RepairAcceptance source) {
        RepairAcceptance copy = new RepairAcceptance();
        copy.setId(source.getId());
        copy.setWorkOrderId(source.getWorkOrderId());
        copy.setStage(source.getStage());
        copy.setStatus(source.getStatus());
        copy.setAcceptedById(source.getAcceptedById());
        copy.setHandedOverById(source.getHandedOverById());
        copy.setAcceptanceStartedAt(source.getAcceptanceStartedAt());
        copy.setAcceptedAt(source.getAcceptedAt());
        copy.setRunInRequired(source.isRunInRequired());
        copy.setRunInShiftsRequired(source.getRunInShiftsRequired());
        copy.setRunInStartedAt(source.getRunInStartedAt());
        copy.setRunInCompletedAt(source.getRunInCompletedAt());
        copy.setQualityGrade(source.getQualityGrade());
        copy.setPerformanceBefore(source.getPerformanceBefore());
        copy.setPerformanceAfter(source.getPerformanceAfter());
        copy.setQualityBefore(source.getQualityBefore());
        copy.setQualityAfter(source.getQualityAfter());
        copy.setRemarks(source.getRemarks());
        return copy;
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
