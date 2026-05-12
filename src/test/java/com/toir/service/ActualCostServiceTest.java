package com.toir.service;

import com.toir.dto.actualcost.ActualCostDto;
import com.toir.entity.projects.ActualCost;
import com.toir.enums.ActualCostStatus;
import com.toir.exception.RestException;
import com.toir.repository.actualCost.ActualCostRepository;
import com.toir.util.AuditBuilderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActualCostServiceTest {

    @Mock
    ActualCostRepository repository;

    @Mock
    AuditBuilderService auditBuilderService;

    @InjectMocks
    ActualCostService service;

    @Test
    void rejectWithoutCommentShouldReturnBadRequest() {
        assertThatThrownBy(() -> service.review(UUID.randomUUID(), false, UUID.randomUUID(), null))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Rejection comment is required");

        verifyNoInteractions(repository);
    }

    @Test
    void rejectWithBlankCommentShouldReturnBadRequest() {
        assertThatThrownBy(() -> service.review(UUID.randomUUID(), false, UUID.randomUUID(), "   "))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Rejection comment is required");

        verifyNoInteractions(repository);
    }

    @Test
    void rejectWithValidCommentShouldKeepExistingSuccessBehavior() {
        UUID id = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();

        ActualCost actualCost = new ActualCost();
        ReflectionTestUtils.setField(actualCost, "id", id);
        actualCost.setStatus(ActualCostStatus.PENDING);
        actualCost.setCostCategoryId(UUID.randomUUID());
        actualCost.setAmount(100);

        when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(actualCost));
        when(repository.save(any(ActualCost.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ActualCostDto result = service.review(id, false, reviewerId, "Invalid supporting documents");

        assertThat(result.status()).isEqualTo(ActualCostStatus.REJECTED);
        assertThat(result.reviewedById()).isEqualTo(reviewerId);
        assertThat(result.reviewComment()).isEqualTo("Invalid supporting documents");
        assertThat(result.reviewedAt()).isNotNull();
    }

    @Test
    void findByFiltersShouldSupportBusinessSearchAndKeepWorkOrderFilter() {
        UUID workOrderId = UUID.randomUUID();
        ActualCost actualCost = new ActualCost();
        actualCost.setWorkOrderId(workOrderId);
        actualCost.setCostCategoryId(UUID.randomUUID());
        actualCost.setStatus(ActualCostStatus.PENDING);
        actualCost.setAmount(400);
        actualCost.setCostDate(Instant.parse("2026-05-10T10:00:00Z"));

        when(repository.findAllByFiltersOrderByUpdatedAtDesc(workOrderId, "WO-2026-1"))
                .thenReturn(List.of(actualCost));

        List<ActualCostDto> result = service.findByFilters(workOrderId, "WO-2026-1");

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().workOrderId()).isEqualTo(workOrderId);
        verify(repository).findAllByFiltersOrderByUpdatedAtDesc(workOrderId, "WO-2026-1");
    }
}
