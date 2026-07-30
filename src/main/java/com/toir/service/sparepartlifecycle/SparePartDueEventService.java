package com.toir.service.sparepartlifecycle;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toir.dto.sparepartlifecycle.SparePartLifecycleEvaluation;
import com.toir.dto.sparepartlifecycle.SparePartLifeLimitEvaluation;
import com.toir.entity.sparepartlifecycle.SparePartDueEvent;
import com.toir.entity.sparepartlifecycle.SparePartInstallation;
import com.toir.enums.sparepartlifecycle.SparePartDueEventState;
import com.toir.enums.sparepartlifecycle.SparePartLifecycleEvaluationState;
import com.toir.repository.sparepartlifecycle.SparePartDueEventRepository;
import com.toir.repository.sparepartlifecycle.SparePartInstallationRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.security.PermissionConstants;
import com.toir.security.ScopeAccessService;
import com.toir.exception.RestException;
import com.toir.enums.sparepartlifecycle.SparePartInstallationStatus;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Comparator;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SparePartDueEventService {

    private final SparePartDueEventRepository repository;
    private final SparePartInstallationRepository installationRepository;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final EquipmentRepository equipmentRepository;
    private final ScopeAccessService scopeAccessService;

    @Transactional
    public SparePartDueEvent applyEvaluation(SparePartInstallation installation,
                                             SparePartLifecycleEvaluation evaluation) {
        String cycleKey = cycleKey(installation);
        SparePartDueEvent existing = repository
                .findByInstallationIdAndCycleKeyAndIsDeletedFalse(installation.getId(), cycleKey)
                .orElse(null);
        if (existing != null && existing.getState() == SparePartDueEventState.RESOLVED) {
            return existing;
        }
        if (evaluation.aggregateState() == SparePartLifecycleEvaluationState.ERROR) {
            return existing;
        }
        if (evaluation.aggregateState() == SparePartLifecycleEvaluationState.OK) {
            if (existing == null) {
                return null;
            }
            existing.setState(SparePartDueEventState.RESOLVED);
            existing.setResolvedAt(evaluation.evaluatedAt());
            existing.setLastEvaluatedAt(evaluation.evaluatedAt());
            existing.setReasons(reasonsJson(evaluation));
            return repository.save(existing);
        }

        SparePartDueEventState previousState = existing == null ? null : existing.getState();
        SparePartDueEvent event = existing == null ? new SparePartDueEvent() : existing;
        if (existing == null) {
            event.setInstallationId(installation.getId());
            event.setAppliedRuleId(installation.getAppliedLifeRuleId());
            event.setAppliedRuleRevision(installation.getAppliedRuleRevision());
            event.setCycleKey(cycleKey);
            event.setDueAction(evaluation.dueAction());
            event.setFirstDetectedAt(evaluation.evaluatedAt());
        }
        event.setState(toEventState(evaluation.aggregateState()));
        event.setDueAt(evaluation.nextCalendarDueAt());
        event.setLastEvaluatedAt(evaluation.evaluatedAt());
        event.setReasons(reasonsJson(evaluation));
        applyMeterDetails(event, evaluation);
        try {
            SparePartDueEvent saved = repository.save(event);
            publishTransitionIfChanged(installation, saved, previousState);
            return saved;
        } catch (DataIntegrityViolationException race) {
            return repository.findByInstallationIdAndCycleKeyAndIsDeletedFalse(installation.getId(), cycleKey)
                    .orElseThrow(() -> race);
        }
    }

    private void publishTransitionIfChanged(SparePartInstallation installation,
                                            SparePartDueEvent event,
                                            SparePartDueEventState previousState) {
        if (event.getId() != null && event.getState() != previousState) {
            eventPublisher.publishEvent(new SparePartDueTransitionEvent(
                    installation.getEquipmentId(),
                    installation.getId(),
                    event.getId(),
                    event.getState(),
                    event.getDueAction()
            ));
        }
    }

    public String cycleKey(SparePartInstallation installation) {
        if (installation.getAppliedLifeRuleId() == null || installation.getAppliedRuleRevision() == null) {
            return "INSTALLATION:" + installation.getId() + ":MANUAL";
        }
        return "RULE:" + installation.getAppliedLifeRuleId() + ":REV:" + installation.getAppliedRuleRevision();
    }

    @Transactional(readOnly = true)
    public java.util.List<SparePartDueEvent> list() {
        return repository.findAllByIsDeletedFalseOrderByUpdatedAtDesc();
    }

    @Transactional(readOnly = true)
    public java.util.List<SparePartDueEvent> listForEquipment(UUID equipmentId) {
        List<UUID> installationIds = installationRepository
                .findAllByEquipmentIdAndStatusAndIsDeletedFalseOrderByInstalledAtDesc(
                        equipmentId, SparePartInstallationStatus.ACTIVE)
                .stream()
                .map(SparePartInstallation::getId)
                .toList();
        return installationIds.isEmpty()
                ? List.of()
                : repository.findAllByInstallationIdInAndIsDeletedFalse(installationIds);
    }

    @Transactional(readOnly = true)
    public SparePartDueEvent get(UUID id) {
        return repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> com.toir.exception.RestException.notFound(
                        "Spare-part due event not found: " + id));
    }

    @Transactional
    public SparePartDueEvent acknowledge(UUID id, UUID actorId, Instant acknowledgedAt) {
        if (actorId == null) {
            throw RestException.badRequest("ACTOR_REQUIRED: actor is required");
        }
        if (!scopeAccessService.hasAuthority(PermissionConstants.WILDCARD)
                && !scopeAccessService.hasAuthority(PermissionConstants.SPARE_PART_DUE_ACKNOWLEDGE)) {
            throw RestException.forbidden("SPARE_PART_DUE_ACKNOWLEDGE permission is required");
        }
        SparePartDueEvent event = get(id);
        if (event.getState() == SparePartDueEventState.RESOLVED) {
            throw RestException.conflict("DUE_EVENT_RESOLVED: resolved event cannot be acknowledged");
        }
        SparePartInstallation installation = installationRepository
                .findByIdAndIsDeletedFalse(event.getInstallationId())
                .orElseThrow(() -> RestException.notFound(
                        "Spare-part installation not found: " + event.getInstallationId()));
        var equipment = equipmentRepository.findByIdAndIsDeletedFalse(installation.getEquipmentId())
                .orElseThrow(() -> RestException.notFound("Equipment not found: " + installation.getEquipmentId()));
        scopeAccessService.assertCanAccessEquipmentScope(
                equipment.getResponsibleDepartmentId(), equipment.getDepartmentId());
        if (event.getAcknowledgedAt() != null) {
            return event;
        }
        event.setAcknowledgedAt(acknowledgedAt == null ? Instant.now() : acknowledgedAt);
        event.setAcknowledgedBy(actorId);
        return repository.save(event);
    }

    @Transactional
    public void resolveForRemoval(UUID installationId,
                                  Instant resolvedAt,
                                  UUID resolvedBy,
                                  UUID replacementInstallationId) {
        repository.findAllByInstallationIdAndStateInAndIsDeletedFalse(
                        installationId,
                        Set.of(
                                SparePartDueEventState.UPCOMING,
                                SparePartDueEventState.WARNING,
                                SparePartDueEventState.DUE,
                                SparePartDueEventState.OVERDUE
                        )
                )
                .forEach(event -> {
                    event.setState(SparePartDueEventState.RESOLVED);
                    event.setResolvedAt(resolvedAt);
                    event.setResolvedBy(resolvedBy);
                    event.setReplacementInstallationId(replacementInstallationId);
                    event.setLastEvaluatedAt(resolvedAt);
                    repository.save(event);
                });
    }

    private static SparePartDueEventState toEventState(SparePartLifecycleEvaluationState state) {
        return switch (state) {
            case WARNING -> SparePartDueEventState.WARNING;
            case DUE -> SparePartDueEventState.DUE;
            case OVERDUE -> SparePartDueEventState.OVERDUE;
            case OK, ERROR -> throw new IllegalArgumentException("No due-event state for " + state);
        };
    }

    private static void applyMeterDetails(SparePartDueEvent event,
                                          SparePartLifecycleEvaluation evaluation) {
        SparePartLifeLimitEvaluation meter = evaluation.limits().stream()
                .filter(limit -> limit.equipmentMeterId() != null && limit.errorCode() == null)
                .max(Comparator.comparingInt(limit -> stateRank(limit.state())))
                .orElse(null);
        if (meter == null) {
            event.setMeterId(null);
            event.setMeterType(null);
            event.setCurrentMeterValue(null);
            event.setDueMeterValue(null);
            event.setWarningThreshold(null);
            return;
        }
        event.setMeterId(meter.equipmentMeterId());
        event.setMeterType(meter.meterType());
        event.setCurrentMeterValue(meter.currentMeterValue());
        event.setDueMeterValue(meter.currentMeterValue() == null || meter.remaining() == null
                ? null
                : meter.currentMeterValue().add(meter.remaining()));
        event.setWarningThreshold(meter.currentMeterValue() == null
                || meter.consumed() == null
                || meter.warningThreshold() == null
                ? null
                : meter.currentMeterValue().subtract(meter.consumed()).add(meter.warningThreshold()));
    }

    private static int stateRank(SparePartLifecycleEvaluationState state) {
        return switch (state) {
            case ERROR -> 4;
            case OVERDUE -> 3;
            case DUE -> 2;
            case WARNING -> 1;
            case OK -> 0;
        };
    }

    private String reasonsJson(SparePartLifecycleEvaluation evaluation) {
        Map<String, Object> reasons = new LinkedHashMap<>();
        reasons.put("aggregateState", evaluation.aggregateState());
        reasons.put("errors", evaluation.errors());
        reasons.put("limits", evaluation.limits());
        try {
            return objectMapper.writeValueAsString(reasons);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize spare-part due-event reasons", exception);
        }
    }
}
