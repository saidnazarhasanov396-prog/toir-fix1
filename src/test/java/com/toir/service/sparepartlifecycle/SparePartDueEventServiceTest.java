package com.toir.service.sparepartlifecycle;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toir.dto.sparepartlifecycle.SparePartLifecycleEvaluation;
import com.toir.dto.sparepartlifecycle.SparePartLifeLimitEvaluation;
import com.toir.entity.sparepartlifecycle.SparePartDueEvent;
import com.toir.entity.sparepartlifecycle.SparePartInstallation;
import com.toir.enums.sparepartlifecycle.SparePartDueAction;
import com.toir.enums.sparepartlifecycle.SparePartDueEventState;
import com.toir.enums.sparepartlifecycle.SparePartLifecycleEvaluationState;
import com.toir.enums.sparepartlifecycle.SparePartLifeLimitKind;
import com.toir.enums.MeterType;
import java.math.BigDecimal;
import com.toir.repository.sparepartlifecycle.SparePartDueEventRepository;
import com.toir.repository.sparepartlifecycle.SparePartInstallationRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SparePartDueEventServiceTest {

    @Mock
    SparePartDueEventRepository repository;

    @Mock
    SparePartInstallationRepository installationRepository;

    @Mock
    ApplicationEventPublisher eventPublisher;

    @Spy
    ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    SparePartDueEventService service;

    @Test
    void repeatedEvaluationUpsertsOneStableCycle() {
        SparePartInstallation installation = installation();
        String cycleKey = service.cycleKey(installation);
        when(repository.findByInstallationIdAndCycleKeyAndIsDeletedFalse(installation.getId(), cycleKey))
                .thenReturn(Optional.empty());
        when(repository.save(any(SparePartDueEvent.class))).thenAnswer(invocation -> {
            SparePartDueEvent event = invocation.getArgument(0);
            event.setId(UUID.randomUUID());
            return event;
        });

        SparePartDueEvent created = service.applyEvaluation(installation, evaluation(SparePartLifecycleEvaluationState.DUE));

        assertThat(created.getInstallationId()).isEqualTo(installation.getId());
        assertThat(created.getCycleKey()).isEqualTo("RULE:" + installation.getAppliedLifeRuleId() + ":REV:3");
        assertThat(created.getState()).isEqualTo(SparePartDueEventState.DUE);
        assertThat(created.getFirstDetectedAt()).isNotNull();

        when(repository.findByInstallationIdAndCycleKeyAndIsDeletedFalse(installation.getId(), cycleKey))
                .thenReturn(Optional.of(created));
        SparePartDueEvent repeated = service.applyEvaluation(
                installation,
                evaluation(SparePartLifecycleEvaluationState.OVERDUE)
        );

        assertThat(repeated).isSameAs(created);
        assertThat(repeated.getState()).isEqualTo(SparePartDueEventState.OVERDUE);
        verify(repository, org.mockito.Mockito.times(2)).save(created);
    }

    @Test
    void healthyEvaluationResolvesOpenEventAndResolvedCycleNeverReopens() {
        SparePartInstallation installation = installation();
        String cycleKey = service.cycleKey(installation);
        SparePartDueEvent existing = new SparePartDueEvent();
        existing.setId(UUID.randomUUID());
        existing.setInstallationId(installation.getId());
        existing.setCycleKey(cycleKey);
        existing.setState(SparePartDueEventState.WARNING);
        when(repository.findByInstallationIdAndCycleKeyAndIsDeletedFalse(installation.getId(), cycleKey))
                .thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);

        SparePartDueEvent resolved = service.applyEvaluation(
                installation,
                evaluation(SparePartLifecycleEvaluationState.OK)
        );

        assertThat(resolved.getState()).isEqualTo(SparePartDueEventState.RESOLVED);
        assertThat(resolved.getResolvedAt()).isNotNull();

        SparePartDueEvent notReopened = service.applyEvaluation(
                installation,
                evaluation(SparePartLifecycleEvaluationState.DUE)
        );

        assertThat(notReopened.getState()).isEqualTo(SparePartDueEventState.RESOLVED);
    }

    @Test
    void healthyEvaluationWithoutExistingCycleDoesNotCreateEvent() {
        SparePartInstallation installation = installation();
        String cycleKey = service.cycleKey(installation);
        when(repository.findByInstallationIdAndCycleKeyAndIsDeletedFalse(installation.getId(), cycleKey))
                .thenReturn(Optional.empty());

        SparePartDueEvent result = service.applyEvaluation(
                installation,
                evaluation(SparePartLifecycleEvaluationState.OK)
        );

        assertThat(result).isNull();
        verify(repository, never()).save(any());
    }

    @Test
    void meterEvaluationStoresThresholdTargetAndCurrentValuesForReadModels() {
        SparePartInstallation installation = installation();
        String cycleKey = service.cycleKey(installation);
        when(repository.findByInstallationIdAndCycleKeyAndIsDeletedFalse(installation.getId(), cycleKey))
                .thenReturn(Optional.empty());
        when(repository.save(any(SparePartDueEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));
        UUID meterId = UUID.randomUUID();
        SparePartLifeLimitEvaluation limit = new SparePartLifeLimitEvaluation(
                UUID.randomUUID(), SparePartLifeLimitKind.METER, SparePartLifecycleEvaluationState.WARNING,
                new BigDecimal("45"), new BigDecimal("5"), new BigDecimal("40"), null,
                meterId, MeterType.ENGINE_HOURS, new BigDecimal("145"), null);
        SparePartLifecycleEvaluation evaluation = new SparePartLifecycleEvaluation(
                installation.getId(), SparePartLifecycleEvaluationState.WARNING,
                SparePartDueAction.WARNING_ONLY, List.of(limit), null, List.of(),
                Instant.parse("2026-07-10T10:00:00Z"));

        SparePartDueEvent event = service.applyEvaluation(installation, evaluation);

        assertThat(event.getMeterId()).isEqualTo(meterId);
        assertThat(event.getCurrentMeterValue()).isEqualByComparingTo("145");
        assertThat(event.getDueMeterValue()).isEqualByComparingTo("150");
        assertThat(event.getWarningThreshold()).isEqualByComparingTo("140");
    }

    private static SparePartInstallation installation() {
        SparePartInstallation installation = new SparePartInstallation();
        installation.setId(UUID.randomUUID());
        installation.setAppliedLifeRuleId(UUID.randomUUID());
        installation.setAppliedRuleRevision(3);
        return installation;
    }

    private static SparePartLifecycleEvaluation evaluation(SparePartLifecycleEvaluationState state) {
        return new SparePartLifecycleEvaluation(
                UUID.randomUUID(),
                state,
                SparePartDueAction.MAINTENANCE_REQUIRED,
                List.of(),
                Instant.parse("2026-12-01T00:00:00Z"),
                List.of(),
                Instant.parse("2026-07-10T10:00:00Z")
        );
    }
}
