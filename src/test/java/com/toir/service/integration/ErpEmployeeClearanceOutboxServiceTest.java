package com.toir.service.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toir.dto.integration.EmployeeClearanceReceiptRequest;
import com.toir.dto.integration.EmployeeClearanceReceiptV2Request;
import com.toir.entity.integration.ErpEmployeeClearanceOutboxEvent;
import com.toir.repository.integration.ErpEmployeeClearanceOutboxRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ErpEmployeeClearanceOutboxServiceTest {
    @Test
    void queuesCanonicalReviewedClearanceForErpOffboardingGate() {
        ErpEmployeeClearanceOutboxRepository repository = mock(ErpEmployeeClearanceOutboxRepository.class);
        when(repository.existsByIdempotencyKey(any())).thenReturn(false);
        ErpEmployeeClearanceOutboxService service = new ErpEmployeeClearanceOutboxService(repository, new ObjectMapper());
        UUID employeeId = UUID.randomUUID();

        service.queue(new EmployeeClearanceReceiptRequest(employeeId, true, 9, "TOIR-OFFBOARD-9"));

        ArgumentCaptor<ErpEmployeeClearanceOutboxEvent> event = ArgumentCaptor.forClass(ErpEmployeeClearanceOutboxEvent.class);
        verify(repository).save(event.capture());
        assertThat(event.getValue().getStatus()).isEqualTo("PENDING");
        assertThat(event.getValue().getPayload()).contains(
                "\"source\":\"TOIR\"", "\"target\":\"ERP\"", "\"type\":\"toir.employee-clearance.changed.v1\"",
                "\"employeeId\":\"" + employeeId + "\"", "\"moduleCode\":\"TOIR\"", "\"cleared\":true");
    }

    @Test
    void queuesCaseBoundV2ClearanceWithCaseRevisionIdempotency() {
        ErpEmployeeClearanceOutboxRepository repository = mock(ErpEmployeeClearanceOutboxRepository.class);
        when(repository.existsByIdempotencyKey(any())).thenReturn(false);
        ErpEmployeeClearanceOutboxService service = new ErpEmployeeClearanceOutboxService(repository, new ObjectMapper());
        UUID employeeId = UUID.randomUUID();
        UUID offboardingCaseId = UUID.randomUUID();

        service.queueV2(new EmployeeClearanceReceiptV2Request(employeeId, offboardingCaseId, true, 12L, "TOIR-OFFBOARD-12"));

        ArgumentCaptor<ErpEmployeeClearanceOutboxEvent> event = ArgumentCaptor.forClass(ErpEmployeeClearanceOutboxEvent.class);
        verify(repository).save(event.capture());
        assertThat(event.getValue().getOffboardingCaseId()).isEqualTo(offboardingCaseId);
        assertThat(event.getValue().getIdempotencyKey())
                .isEqualTo("toir:employee-clearance:v2:" + offboardingCaseId + ":12");
        assertThat(event.getValue().getPayload()).contains(
                "\"source\":\"TOIR\"", "\"target\":\"ERP\"", "\"type\":\"toir.employee-clearance.changed.v2\"",
                "\"schemaVersion\":\"2.0\"", "\"aggregateType\":\"OFFBOARDING_CASE\"",
                "\"aggregateId\":\"" + offboardingCaseId + "\"", "\"employeeId\":\"" + employeeId + "\"",
                "\"offboardingCaseId\":\"" + offboardingCaseId + "\"", "\"evidenceReference\":\"TOIR-OFFBOARD-12\"");
    }

    @Test
    void skipsAlreadyQueuedV2CaseRevision() {
        ErpEmployeeClearanceOutboxRepository repository = mock(ErpEmployeeClearanceOutboxRepository.class);
        UUID employeeId = UUID.randomUUID();
        UUID offboardingCaseId = UUID.randomUUID();
        String expectedKey = "toir:employee-clearance:v2:" + offboardingCaseId + ":4";
        when(repository.existsByIdempotencyKey(expectedKey)).thenReturn(true);
        ErpEmployeeClearanceOutboxService service = new ErpEmployeeClearanceOutboxService(repository, new ObjectMapper());

        service.queueV2(new EmployeeClearanceReceiptV2Request(employeeId, offboardingCaseId, false, 4L, "TOIR-OFFBOARD-4"));

        verify(repository).existsByIdempotencyKey(expectedKey);
        verify(repository, never()).save(any());
    }
}
