package com.toir.service.defects;

import com.toir.dto.defectlist.DefectListStatsResponse;
import com.toir.enums.DefectListStatus;
import com.toir.repository.defects.DefectListLineRepository;
import com.toir.repository.defects.DefectListRepository;
import com.toir.repository.projection.DefectListStatsProjection;
import com.toir.util.AuditBuilderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefectListServiceStatsTest {

    @Mock
    DefectListRepository repository;

    @Mock
    DefectListLineRepository lineRepository;

    @Mock
    AuditBuilderService auditBuilderService;

    @InjectMocks
    DefectListService service;

    @Test
    void getStatsWithoutFiltersReturnsDefectListStats() {
        DefectListStatsProjection projection = statsProjection(24L, 6L, 2L, 1L);

        when(repository.getDefectListStats(
                null,
                null,
                DefectListStatus.DRAFT.name(),
                DefectListStatus.APPROVED.name(),
                DefectListStatus.CLOSED.name()
        )).thenReturn(projection);

        DefectListStatsResponse result = service.getStats(null, null);

        assertThat(result.totalDefectLists()).isEqualTo(24);
        assertThat(result.draft()).isEqualTo(6);
        assertThat(result.approved()).isEqualTo(2);
        assertThat(result.closed()).isEqualTo(1);

        verify(repository).getDefectListStats(
                null,
                null,
                DefectListStatus.DRAFT.name(),
                DefectListStatus.APPROVED.name(),
                DefectListStatus.CLOSED.name()
        );
    }

    @Test
    void getStatsWithFiltersPassesEquipmentAndNormalizedSearchPattern() {
        UUID equipmentId = UUID.randomUUID();

        DefectListStatsProjection projection = statsProjection(10L, 4L, 3L, 1L);

        when(repository.getDefectListStats(
                equipmentId,
                "%pump%",
                DefectListStatus.DRAFT.name(),
                DefectListStatus.APPROVED.name(),
                DefectListStatus.CLOSED.name()
        )).thenReturn(projection);

        DefectListStatsResponse result = service.getStats(equipmentId, "  PuMp  ");

        assertThat(result.totalDefectLists()).isEqualTo(10);
        assertThat(result.draft()).isEqualTo(4);
        assertThat(result.approved()).isEqualTo(3);
        assertThat(result.closed()).isEqualTo(1);

        verify(repository).getDefectListStats(
                equipmentId,
                "%pump%",
                DefectListStatus.DRAFT.name(),
                DefectListStatus.APPROVED.name(),
                DefectListStatus.CLOSED.name()
        );
    }

    @Test
    void getStatsWithBlankSearchPassesNullSearchPattern() {
        DefectListStatsProjection projection = statsProjection(7L, 3L, 2L, 1L);

        when(repository.getDefectListStats(
                null,
                null,
                DefectListStatus.DRAFT.name(),
                DefectListStatus.APPROVED.name(),
                DefectListStatus.CLOSED.name()
        )).thenReturn(projection);

        DefectListStatsResponse result = service.getStats(null, "   ");

        assertThat(result.totalDefectLists()).isEqualTo(7);
        assertThat(result.draft()).isEqualTo(3);
        assertThat(result.approved()).isEqualTo(2);
        assertThat(result.closed()).isEqualTo(1);
    }

    @Test
    void getStatsMapsNullProjectionValuesToZero() {
        DefectListStatsProjection projection = statsProjection(null, null, null, null);

        when(repository.getDefectListStats(
                null,
                null,
                DefectListStatus.DRAFT.name(),
                DefectListStatus.APPROVED.name(),
                DefectListStatus.CLOSED.name()
        )).thenReturn(projection);

        DefectListStatsResponse result = service.getStats(null, null);

        assertThat(result.totalDefectLists()).isZero();
        assertThat(result.draft()).isZero();
        assertThat(result.approved()).isZero();
        assertThat(result.closed()).isZero();
    }

    private DefectListStatsProjection statsProjection(
            Long totalDefectLists,
            Long draft,
            Long approved,
            Long closed
    ) {
        return new DefectListStatsProjection() {
            @Override
            public Long getTotalDefectLists() {
                return totalDefectLists;
            }

            @Override
            public Long getDraft() {
                return draft;
            }

            @Override
            public Long getApproved() {
                return approved;
            }

            @Override
            public Long getClosed() {
                return closed;
            }
        };
    }
}