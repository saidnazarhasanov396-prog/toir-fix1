package com.toir.service.faktura;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toir.dto.faktura.FakturaUzImportRequest;
import com.toir.dto.faktura.integration.FakturaUzAuthResponse;
import com.toir.entity.IntegrationEndpoint;
import com.toir.entity.IntegrationSyncLog;
import com.toir.entity.faktura.FakturaUzImportHistory;
import com.toir.enums.FakturaUzDocumentType;
import com.toir.enums.IntegrationSyncStatus;
import com.toir.exception.RestException;
import com.toir.repository.IntegrationEndpointRepository;
import com.toir.repository.IntegrationSyncLogRepository;
import com.toir.repository.faktura.FakturaUzDocumentContentRepository;
import com.toir.repository.faktura.FakturaUzDocumentRepository;
import com.toir.repository.faktura.FakturaUzDocumentType32ContentRepository;
import com.toir.repository.faktura.FakturaUzDocumentType32PartRepository;
import com.toir.repository.faktura.FakturaUzDocumentType32ServiceRepository;
import com.toir.repository.faktura.FakturaUzImportHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FakturaUzServiceTest {

    private FakturaUzClientService clientService;
    private IntegrationEndpointRepository endpointRepository;
    private IntegrationSyncLogRepository syncLogRepository;
    private FakturaUzImportHistoryRepository historyRepository;
    private FakturaUzService service;

    @BeforeEach
    void setUp() {
        clientService = mock(FakturaUzClientService.class);
        endpointRepository = mock(IntegrationEndpointRepository.class);
        syncLogRepository = mock(IntegrationSyncLogRepository.class);
        historyRepository = mock(FakturaUzImportHistoryRepository.class);
        service = new FakturaUzService(
                clientService,
                endpointRepository,
                syncLogRepository,
                mock(FakturaUzDocumentRepository.class),
                mock(FakturaUzDocumentContentRepository.class),
                mock(FakturaUzDocumentType32ContentRepository.class),
                mock(FakturaUzDocumentType32ServiceRepository.class),
                mock(FakturaUzDocumentType32PartRepository.class),
                historyRepository,
                new ObjectMapper()
        );
    }

    @Test
    void authenticationFailureIsPropagatedBeforeSyncLogCreation() {
        IntegrationEndpoint endpoint = endpoint();
        when(endpointRepository.findByIdAndIsDeletedFalse(endpoint.getId())).thenReturn(Optional.of(endpoint));
        RestException authFailure = RestException.badRequest("FakturaUz auth failed");
        when(clientService.getAuthToken(any(), any(), any(), any())).thenThrow(authFailure);

        assertThatThrownBy(() -> service.importDocuments(request(endpoint.getId())))
                .isSameAs(authFailure);

        verify(syncLogRepository, never()).save(any());
        verify(historyRepository, never()).save(any());
        verify(endpointRepository, never()).save(any());
        assertThat(endpoint.getLastSyncStatus()).isNull();
        assertThat(endpoint.getLastSyncAt()).isNull();
    }

    @Test
    void documentFetchFailureIsRecordedAndReturnedAsFailedImportResult() {
        IntegrationEndpoint endpoint = endpoint();
        when(endpointRepository.findByIdAndIsDeletedFalse(endpoint.getId())).thenReturn(Optional.of(endpoint));
        FakturaUzAuthResponse auth = new FakturaUzAuthResponse();
        auth.setAccessToken("test-token");
        when(clientService.getAuthToken(any(), any(), any(), any())).thenReturn(auth);
        when(clientService.getDocuments(any())).thenThrow(RestException.badRequest("documents unavailable"));
        when(syncLogRepository.save(any(IntegrationSyncLog.class))).thenAnswer(invocation -> {
            IntegrationSyncLog log = invocation.getArgument(0);
            if (log.getId() == null) {
                log.setId(UUID.randomUUID());
            }
            return log;
        });
        when(historyRepository.save(any(FakturaUzImportHistory.class))).thenAnswer(invocation -> {
            FakturaUzImportHistory history = invocation.getArgument(0);
            if (history.getId() == null) {
                history.setId(UUID.randomUUID());
            }
            return history;
        });
        when(endpointRepository.save(endpoint)).thenReturn(endpoint);

        var result = service.importDocuments(request(endpoint.getId()));

        assertThat(result.description()).contains("Error: documents unavailable");
        assertThat(result.savedCount()).isZero();
        assertThat(result.updatedCount()).isZero();
        assertThat(endpoint.getLastSyncStatus()).isEqualTo(IntegrationSyncStatus.FAILED);
        assertThat(endpoint.getLastError()).isEqualTo("documents unavailable");
        ArgumentCaptorSupport.assertFailedLogWasSaved(syncLogRepository);
        verify(historyRepository).save(any(FakturaUzImportHistory.class));
    }

    private IntegrationEndpoint endpoint() {
        IntegrationEndpoint endpoint = new IntegrationEndpoint();
        endpoint.setId(UUID.randomUUID());
        endpoint.setSystem("FAKTURA_UZ");
        endpoint.setActive(true);
        endpoint.setCompanyInn("000000000");
        endpoint.setUsername("test-user");
        endpoint.setPassword("test-password");
        endpoint.setClientId("test-client");
        endpoint.setClientSecret("test-secret");
        return endpoint;
    }

    private FakturaUzImportRequest request(UUID endpointId) {
        return new FakturaUzImportRequest(
                endpointId,
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 10),
                FakturaUzDocumentType.CONTRACT_ROAMING
        );
    }

    private static final class ArgumentCaptorSupport {
        private static void assertFailedLogWasSaved(IntegrationSyncLogRepository repository) {
            org.mockito.ArgumentCaptor<IntegrationSyncLog> captor =
                    org.mockito.ArgumentCaptor.forClass(IntegrationSyncLog.class);
            verify(repository, org.mockito.Mockito.atLeast(2)).save(captor.capture());
            assertThat(captor.getAllValues().getLast().getStatus()).isEqualTo(IntegrationSyncStatus.FAILED);
            assertThat(captor.getAllValues().getLast().getErrorMessage()).isEqualTo("documents unavailable");
        }
    }
}
