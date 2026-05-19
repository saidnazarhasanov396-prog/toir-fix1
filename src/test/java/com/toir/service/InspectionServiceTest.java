package com.toir.service;

import com.toir.dto.inspection.InspectionRoundDto;
import com.toir.dto.inspection.InspectionRoundResultDto;
import com.toir.dto.inspection.InspectionRoundResultRequest;
import com.toir.dto.inspection.InspectionRouteDto;
import com.toir.dto.inspection.InspectionRouteRequest;
import com.toir.entity.inspection.InspectionRound;
import com.toir.entity.inspection.InspectionRoundResult;
import com.toir.entity.inspection.InspectionRoute;
import com.toir.enums.InspectionRoundStatus;
import com.toir.exception.RestException;
import com.toir.repository.defects.DefectRepository;
import com.toir.repository.inspection.InspectionCheckpointRepository;
import com.toir.repository.inspection.InspectionRoundRepository;
import com.toir.repository.inspection.InspectionRouteRepository;
import com.toir.util.AuditBuilderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InspectionServiceTest {

    @Mock
    InspectionRouteRepository routeRepo;

    @Mock
    InspectionCheckpointRepository checkpointRepo;

    @Mock
    InspectionRoundRepository roundRepo;

    @Mock
    DefectRepository defectRepo;

    @Mock
    AuditBuilderService auditBuilderService;

    @Mock
    UnitOfMeasurementService unitOfMeasurementService;

    @InjectMocks
    InspectionService service;

    @Test
    void listRoundsWithNullFiltersReturnsEmptyList() {
        when(roundRepo.findAllByRouteIdAndIsDeletedFalseOrderByStartedAtDesc(null, null, null))
                .thenReturn(List.of());

        assertThat(service.listRounds(null, null, null)).isEmpty();
        verify(roundRepo).findAllByRouteIdAndIsDeletedFalseOrderByStartedAtDesc(null, null, null);
    }

    @Test
    void listRoundsWithRouteAndStatusFiltersReturnsData() {
        UUID routeId = UUID.randomUUID();
        UUID performedBy = UUID.randomUUID();
        InspectionRound round = round(UUID.randomUUID(), InspectionRoundStatus.IN_PROGRESS);
        round.setPerformedBy(performedBy);
        when(roundRepo.findAllByRouteIdAndIsDeletedFalseOrderByStartedAtDesc(
                eq(routeId),
                eq(performedBy),
                eq(InspectionRoundStatus.IN_PROGRESS)
        )).thenReturn(List.of(round));

        assertThat(service.listRounds(routeId, performedBy, InspectionRoundStatus.IN_PROGRESS))
                .hasSize(1)
                .allSatisfy(item -> {
                    assertThat(item.status()).isEqualTo(InspectionRoundStatus.IN_PROGRESS);
                    assertThat(item.results()).isEmpty();
                });
    }

    @Test
    void inProgressRoundCanRecordResult() {
        UUID roundId = UUID.randomUUID();
        InspectionRound round = round(roundId, InspectionRoundStatus.IN_PROGRESS);
        when(roundRepo.findByIdAndIsDeletedFalse(roundId)).thenReturn(Optional.of(round));
        when(unitOfMeasurementService.normalizeOptionalUnitOrNull("bar")).thenReturn("bar");
        when(roundRepo.save(any(InspectionRound.class))).thenAnswer(invocation -> {
            InspectionRound saved = invocation.getArgument(0);
            for (InspectionRoundResult item : saved.getResults()) {
                if (item.getId() == null) {
                    ReflectionTestUtils.setField(item, "id", UUID.randomUUID());
                }
            }
            return saved;
        });

        InspectionRoundResultDto result = service.recordResult(
                roundId,
                new InspectionRoundResultRequest(UUID.randomUUID(), "OK", 10.0, "bar", "ok", null)
        );

        assertThat(result.roundId()).isEqualTo(roundId);
        assertThat(result.status()).isEqualTo("OK");
        verify(roundRepo).save(any(InspectionRound.class));
    }

    @Test
    void inProgressRoundCanComplete() {
        UUID roundId = UUID.randomUUID();
        InspectionRound round = round(roundId, InspectionRoundStatus.IN_PROGRESS);
        when(roundRepo.findByIdAndIsDeletedFalse(roundId)).thenReturn(Optional.of(round));
        when(roundRepo.save(any(InspectionRound.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InspectionRoundDto result = service.completeRound(roundId, "done");

        assertThat(result.status()).isEqualTo(InspectionRoundStatus.COMPLETED);
        assertThat(result.completedAt()).isNotNull();
        verify(roundRepo).save(any(InspectionRound.class));
    }

    @Test
    void inProgressRoundCanRecordResultWithoutMeasuredUnit() {
        UUID roundId = UUID.randomUUID();
        InspectionRound round = round(roundId, InspectionRoundStatus.IN_PROGRESS);
        when(roundRepo.findByIdAndIsDeletedFalse(roundId)).thenReturn(Optional.of(round));
        when(roundRepo.save(any(InspectionRound.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InspectionRoundResultDto result = service.recordResult(
                roundId,
                new InspectionRoundResultRequest(UUID.randomUUID(), "OK", 11.0, null, "ok", null)
        );

        assertThat(result.status()).isEqualTo("OK");
        assertThat(result.measuredUnit()).isNull();
    }

    @Test
    void completedRoundCannotRecordResultOrCompleteAgain() {
        UUID roundId = UUID.randomUUID();
        InspectionRound round = round(roundId, InspectionRoundStatus.COMPLETED);
        when(roundRepo.findByIdAndIsDeletedFalse(roundId)).thenReturn(Optional.of(round));

        assertThatThrownBy(() -> service.recordResult(
                roundId,
                new InspectionRoundResultRequest(UUID.randomUUID(), "OK", null, null, null, null)))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Cannot add results");

        assertThatThrownBy(() -> service.completeRound(roundId, "again"))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Round is not IN_PROGRESS");

        verify(roundRepo, never()).save(any(InspectionRound.class));
    }

    @Test
    void cancelledRoundCannotRecordResultOrCompleteAgain() {
        UUID roundId = UUID.randomUUID();
        InspectionRound round = round(roundId, InspectionRoundStatus.CANCELLED);
        when(roundRepo.findByIdAndIsDeletedFalse(roundId)).thenReturn(Optional.of(round));

        assertThatThrownBy(() -> service.recordResult(
                roundId,
                new InspectionRoundResultRequest(UUID.randomUUID(), "OK", null, null, null, null)))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Cannot add results");

        assertThatThrownBy(() -> service.completeRound(roundId, "again"))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Round is not IN_PROGRESS");

        verify(roundRepo, never()).save(any(InspectionRound.class));
    }

    @Test
    void createRouteWithKnownCheckpointUnitNormalizesAndSucceeds() {
        when(routeRepo.existsByCodeAndIsDeletedFalse("IR-001")).thenReturn(false);
        when(unitOfMeasurementService.normalizeOptionalUnitOrNull(" bar ")).thenReturn("bar");
        when(routeRepo.save(any(InspectionRoute.class))).thenAnswer(invocation -> {
            InspectionRoute route = invocation.getArgument(0);
            ReflectionTestUtils.setField(route, "id", UUID.randomUUID());
            return route;
        });

        InspectionRouteRequest request = routeRequest(" bar ");

        InspectionRouteDto dto = service.createRoute(request);

        assertThat(dto.checkpoints()).hasSize(1);
        assertThat(dto.checkpoints().getFirst().expectedUnit()).isEqualTo("bar");
        verify(routeRepo).save(any(InspectionRoute.class));
    }

    @Test
    void createRouteWithUnknownCheckpointUnitReturnsBadRequest() {
        when(routeRepo.existsByCodeAndIsDeletedFalse("IR-001")).thenReturn(false);
        when(unitOfMeasurementService.normalizeOptionalUnitOrNull("mystery-unit"))
                .thenThrow(RestException.badRequest("Unknown unit"));

        assertThatThrownBy(() -> service.createRoute(routeRequest("mystery-unit")))
                .isInstanceOf(RestException.class)
                .extracting(ex -> ((RestException) ex).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(routeRepo, never()).save(any(InspectionRoute.class));
    }

    @Test
    void createRouteWithBlankCheckpointUnitStoresNullWhenOptional() {
        when(routeRepo.existsByCodeAndIsDeletedFalse("IR-001")).thenReturn(false);
        when(unitOfMeasurementService.normalizeOptionalUnitOrNull("   ")).thenReturn(null);
        when(routeRepo.save(any(InspectionRoute.class))).thenAnswer(invocation -> {
            InspectionRoute route = invocation.getArgument(0);
            ReflectionTestUtils.setField(route, "id", UUID.randomUUID());
            return route;
        });

        InspectionRouteDto dto = service.createRoute(routeRequest("   "));

        assertThat(dto.checkpoints()).hasSize(1);
        assertThat(dto.checkpoints().getFirst().expectedUnit()).isNull();
        verify(routeRepo).save(any(InspectionRoute.class));
    }

    private InspectionRouteRequest routeRequest(String expectedUnit) {
        InspectionRouteRequest.CheckpointRequest checkpoint = new InspectionRouteRequest.CheckpointRequest(
                1,
                null,
                null,
                "Checkpoint",
                "Instruction",
                "MEASUREMENT",
                0.0,
                10.0,
                expectedUnit,
                true
        );
        return new InspectionRouteRequest(
                "IR-001",
                "Route 1",
                null,
                "DAILY",
                15,
                "desc",
                true,
                List.of(checkpoint)
        );
    }

    private InspectionRound round(UUID id, InspectionRoundStatus status) {
        InspectionRound round = new InspectionRound();
        ReflectionTestUtils.setField(round, "id", id);
        round.setStatus(status);
        round.setPerformedBy(UUID.randomUUID());
        return round;
    }
}
