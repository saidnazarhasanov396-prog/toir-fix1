package com.toir.actualcostrouteoverride;

import com.toir.actualcostrouteoverride.dto.ActualCostReviewRouteOverrideDto;
import com.toir.common.exception.RestException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class ActualCostReviewRouteOverrideService {

    private final ActualCostReviewRouteOverrideRepository repository;

    public ActualCostReviewRouteOverrideService(ActualCostReviewRouteOverrideRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<ActualCostReviewRouteOverrideDto> findActive() {
        return repository.findAllByActiveTrue().stream().map(ActualCostReviewRouteOverrideDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<ActualCostReviewRouteOverrideDto> findByActualCost(UUID actualCostId) {
        return repository.findAllByActualCostIdOrderByCreatedAtDesc(actualCostId).stream()
                .map(ActualCostReviewRouteOverrideDto::from).toList();
    }

    public ActualCostReviewRouteOverrideDto apply(ActualCostReviewRouteOverrideDto r) {
        if (r.comment() == null || r.comment().isBlank()) {
            throw RestException.badRequest("Comment is required");
        }
        repository.findFirstByActualCostIdAndActiveTrueOrderByCreatedAtDesc(r.actualCostId())
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
        if (r.thresholdHours() != null) o.setThresholdHours(r.thresholdHours());
        o.setComment(r.comment());
        o.setCreatedById(r.createdById());
        return ActualCostReviewRouteOverrideDto.from(repository.save(o));
    }

    public ActualCostReviewRouteOverrideDto deactivate(UUID id, UUID userId, String comment) {
        ActualCostReviewRouteOverride o = repository.findById(id)
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
