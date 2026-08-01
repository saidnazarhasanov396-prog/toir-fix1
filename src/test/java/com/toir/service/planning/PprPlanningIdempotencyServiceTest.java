package com.toir.service.planning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toir.entity.planning.PprPlanningOperationReceipt;
import com.toir.entity.planning.PprPlanningSession;
import com.toir.exception.RestException;
import com.toir.repository.planning.PprPlanningOperationReceiptRepository;
import com.toir.repository.planning.PprPlanningSessionRepository;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class PprPlanningIdempotencyServiceTest {

    @Test
    void replaysStoredResponseWithoutExecutingOperationAgain() {
        UUID sessionId = UUID.randomUUID();
        PprPlanningSessionRepository sessions =
                Mockito.mock(PprPlanningSessionRepository.class);
        PprPlanningOperationReceiptRepository receipts =
                Mockito.mock(PprPlanningOperationReceiptRepository.class);
        AtomicReference<PprPlanningOperationReceipt> stored = new AtomicReference<>();
        when(sessions.findByIdAndIsDeletedFalseForUpdate(sessionId))
                .thenReturn(Optional.of(new PprPlanningSession()));
        when(receipts.findBySessionIdAndOperationTypeAndIdempotencyKeyAndIsDeletedFalse(
                sessionId, "SELECT", "request-1"))
                .thenAnswer(invocation -> Optional.ofNullable(stored.get()));
        when(receipts.saveAndFlush(Mockito.any()))
                .thenAnswer(invocation -> {
                    stored.set(invocation.getArgument(0));
                    return stored.get();
                });
        PprPlanningIdempotencyService service =
                new PprPlanningIdempotencyService(sessions, receipts, new ObjectMapper());
        AtomicInteger calls = new AtomicInteger();

        String first = service.execute(
                sessionId, "SELECT", "request-1", Map.of("revision", 1),
                String.class, () -> "selected-" + calls.incrementAndGet());
        String replay = service.execute(
                sessionId, "SELECT", "request-1", Map.of("revision", 1),
                String.class, () -> "selected-" + calls.incrementAndGet());

        assertThat(first).isEqualTo("selected-1");
        assertThat(replay).isEqualTo(first);
        assertThat(calls).hasValue(1);
    }

    @Test
    void rejectsSameKeyWithDifferentPayload() {
        UUID sessionId = UUID.randomUUID();
        PprPlanningSessionRepository sessions =
                Mockito.mock(PprPlanningSessionRepository.class);
        PprPlanningOperationReceiptRepository receipts =
                Mockito.mock(PprPlanningOperationReceiptRepository.class);
        when(sessions.findByIdAndIsDeletedFalseForUpdate(sessionId))
                .thenReturn(Optional.of(new PprPlanningSession()));
        PprPlanningIdempotencyService service =
                new PprPlanningIdempotencyService(sessions, receipts, new ObjectMapper());
        AtomicReference<PprPlanningOperationReceipt> stored = new AtomicReference<>();
        when(receipts.findBySessionIdAndOperationTypeAndIdempotencyKeyAndIsDeletedFalse(
                sessionId, "SELECT", "request-1"))
                .thenAnswer(invocation -> Optional.ofNullable(stored.get()));
        when(receipts.saveAndFlush(Mockito.any()))
                .thenAnswer(invocation -> {
                    stored.set(invocation.getArgument(0));
                    return stored.get();
                });

        service.execute(
                sessionId, "SELECT", "request-1", Map.of("revision", 1),
                String.class, () -> "selected");

        assertThatThrownBy(() -> service.execute(
                sessionId, "SELECT", "request-1", Map.of("revision", 2),
                String.class, () -> "must-not-run"))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("different payload");
    }
}
