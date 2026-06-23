package com.toir.service;

import com.toir.dto.actualcostrouteoverride.ActualCostReviewRouteOverrideCreateRequest;
import com.toir.dto.actualcostrouteoverride.ActualCostReviewRouteOverrideDto;
import com.toir.dto.actualcostrouteoverride.ActualCostReviewRouteOverrideResponseDto;
import com.toir.entity.projects.ActualCost;
import com.toir.entity.projects.ActualCostReviewRouteOverride;
import com.toir.exception.RestException;
import com.toir.mapper.ActualCostReviewRouteOverrideResponseMapper;
import com.toir.repository.actualCost.ActualCostRepository;
import com.toir.repository.actualCost.ActualCostReviewRouteOverrideRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ActualCostReviewRouteOverrideService {

    private final ActualCostReviewRouteOverrideRepository repository;
    private final ActualCostRepository actualCostRepository;
    private final ActualCostReviewRouteOverrideResponseMapper responseMapper;
    private final FinanceScopeService financeScopeService;

    @Transactional(readOnly = true)
    public List<ActualCostReviewRouteOverrideResponseDto> findActive() {
        return financeScopeService.filterRouteOverrides(repository.findAllByActiveTrueAndIsDeletedFalse()).stream()
                .map(this::toListResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ActualCostReviewRouteOverrideResponseDto> findByActualCost(UUID actualCostId) {
        ActualCost actualCost = actualCostRepository.findByIdAndIsDeletedFalse(actualCostId)
                .orElseThrow(() -> RestException.notFound("Actual cost not found"));
        financeScopeService.assertCanReadActualCost(actualCost);
        return repository.findAllByActualCostIdAndIsDeletedFalseOrderByCreatedAtDesc(actualCostId).stream()
                .filter(override -> financeScopeService.filterRouteOverrides(List.of(override)).size() == 1)
                .map(this::toListResponse)
                .toList();
    }

    @Transactional
    public ActualCostReviewRouteOverrideResponseDto apply(
            ActualCostReviewRouteOverrideCreateRequest r
    ) {
        if (r.comment() == null || r.comment().isBlank()) {
            throw RestException.badRequest("Comment is required");
        }

        ActualCost actualCost = actualCostRepository.findByIdAndIsDeletedFalse(r.actualCostId())
                .orElseThrow(() -> RestException.notFound("Actual cost not found"));
        ActualCostReviewRouteOverride scopeCandidate = new ActualCostReviewRouteOverride();
        scopeCandidate.setActualCostId(r.actualCostId());
        scopeCandidate.setDepartmentId(r.departmentId());
        financeScopeService.assertCanApplyRouteOverride(scopeCandidate, actualCost);

        repository.findFirstByActualCostIdAndActiveTrueAndIsDeletedFalseOrderByCreatedAtDesc(r.actualCostId())
                .ifPresent(existing -> {
                    existing.setActive(false);
                    existing.setDeactivatedAt(Instant.now());
                    existing.setDeactivationComment("Replaced by new override");
                });

        ActualCostReviewRouteOverride o = new ActualCostReviewRouteOverride();
        o.setActualCostId(r.actualCostId());
        o.setDepartmentId(r.departmentId());
        o.setApprovalRoleCode(r.approvalRoleCode());
        o.setEscalationRoleCode(r.escalationRoleCode());

        if (r.thresholdHours() != null) {
            o.setThresholdHours(r.thresholdHours());
        }

        o.setComment(r.comment());

        ActualCostReviewRouteOverride saved = repository.save(o);

        return responseMapper.toResponse(saved, actualCost);
    }

    @Transactional
    public ActualCostReviewRouteOverrideDto deactivate(UUID id, UUID userId, String comment) {
        ActualCostReviewRouteOverride o = repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Route override not found: " + id));
        financeScopeService.assertCanAccessRouteOverride(o);
        if (!o.isActive()) {
            throw RestException.badRequest("Override already inactive");
        }
        if (comment == null || comment.isBlank()) {
            throw RestException.badRequest("Deactivation comment is required");
        }
        o.setActive(false);
        o.setDeactivatedAt(Instant.now());
        o.setDeactivatedById(userId);
        o.setDeactivationComment(comment);
        return ActualCostReviewRouteOverrideDto.from(o);
    }

    @Transactional
    public List<UUID> deactivateActiveForActualCost(UUID actualCostId, UUID userId, String comment) {
        if (comment == null || comment.isBlank()) {
            throw RestException.badRequest("Deactivation comment is required");
        }
        ActualCost actualCost = actualCostRepository.findByIdAndIsDeletedFalse(actualCostId)
                .orElseThrow(() -> RestException.notFound("Actual cost not found"));
        financeScopeService.assertCanReadActualCost(actualCost);
        return repository.findAllByActualCostIdAndIsDeletedFalseOrderByCreatedAtDesc(actualCostId).stream()
                .filter(ActualCostReviewRouteOverride::isActive)
                .peek(override -> {
                    financeScopeService.assertCanAccessRouteOverride(override);
                    override.setActive(false);
                    override.setDeactivatedAt(Instant.now());
                    override.setDeactivatedById(userId);
                    override.setDeactivationComment(comment);
                })
                .map(ActualCostReviewRouteOverride::getId)
                .toList();
    }

    public ActualCostReviewRouteOverrideResponseDto toResponse(ActualCostReviewRouteOverride override, ActualCost actualCost) {
        return responseMapper.toResponse(override, actualCost);
    }

    private ActualCostReviewRouteOverrideResponseDto toListResponse(ActualCostReviewRouteOverride override) {
        ActualCost actualCost = actualCostRepository
                .findByIdAndIsDeletedFalse(override.getActualCostId())
                .orElse(null);
        return responseMapper.toResponse(override, actualCost);
    }
}
