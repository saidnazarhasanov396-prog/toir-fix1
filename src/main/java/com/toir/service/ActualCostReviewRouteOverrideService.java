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

    @Transactional(readOnly = true)
    public List<ActualCostReviewRouteOverrideDto> findActive() {
        return repository.findAllByActiveTrueAndIsDeletedFalse().stream().map(ActualCostReviewRouteOverrideDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<ActualCostReviewRouteOverrideDto> findByActualCost(UUID actualCostId) {
        return repository.findAllByActualCostIdAndIsDeletedFalseOrderByCreatedAtDesc(actualCostId).stream()
                .map(ActualCostReviewRouteOverrideDto::from).toList();
    }

    @Transactional
    public ActualCostReviewRouteOverrideResponseDto apply(
            ActualCostReviewRouteOverrideCreateRequest r
    ) {
        if (r.comment() == null || r.comment().isBlank()) {
            throw RestException.badRequest("Comment is required");
        }

        ActualCost actualCost = actualCostRepository.findById(r.actualCostId())
                .orElseThrow(() -> RestException.notFound("Actual cost not found"));

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
}
