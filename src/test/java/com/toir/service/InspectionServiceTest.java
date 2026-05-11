package com.toir.service;

import com.toir.dto.inspection.InspectionRoundDto;
import com.toir.dto.inspection.InspectionRoundResultDto;
import com.toir.dto.inspection.InspectionRoundResultRequest;
import com.toir.entity.inspection.InspectionRound;
import com.toir.entity.inspection.InspectionRoundResult;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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

    @InjectMocks
    InspectionService service;

    @Test
    void inProgressRoundCanRecordResult() {
        UUID roundId = UUID.randomUUID();
        InspectionRound round = round(roundId, InspectionRoundStatus.IN_PROGRESS);
        when(roundRepo.findByIdAndIsDeletedFalse(roundId)).thenReturn(Optional.of(round));
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

    private InspectionRound round(UUID id, InspectionRoundStatus status) {
        InspectionRound round = new InspectionRound();
        ReflectionTestUtils.setField(round, "id", id);
        round.setStatus(status);
        round.setPerformedBy(UUID.randomUUID());
        return round;
    }
}

