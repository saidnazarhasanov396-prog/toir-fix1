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

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
}

