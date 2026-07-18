package com.toir.service.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toir.dto.integration.EmployeeClearanceReceiptRequest;
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
}
