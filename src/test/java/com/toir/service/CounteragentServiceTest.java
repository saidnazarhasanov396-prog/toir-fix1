package com.toir.service;

import com.toir.dto.counteragent.CounteragentRequest;
import com.toir.entity.Counteragent;
import com.toir.enums.CounteragentStatus;
import com.toir.exception.RestException;
import com.toir.repository.CounteragentRepository;
import com.toir.repository.PurchaseOrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CounteragentServiceTest {

    @Mock
    CounteragentRepository counteragentRepository;

    @Mock
    PurchaseOrderRepository purchaseOrderRepository;

    @InjectMocks
    CounteragentService service;

    @Test
    void createStoresStructuredContactFieldsAndRejectsDuplicateInn() {
        CounteragentRequest request = request(" CA-001 ", "123456789");
        when(counteragentRepository.existsByCodeAndIsDeletedFalse("CA-001")).thenReturn(false);
        when(counteragentRepository.existsByInnAndIsDeletedFalse("123456789")).thenReturn(false);
        when(counteragentRepository.save(any(Counteragent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.create(request);

        ArgumentCaptor<Counteragent> captor = ArgumentCaptor.forClass(Counteragent.class);
        verify(counteragentRepository).save(captor.capture());
        Counteragent saved = captor.getValue();
        assertThat(saved.getCode()).isEqualTo("CA-001");
        assertThat(saved.getInn()).isEqualTo("123456789");
        assertThat(saved.getContactName()).isEqualTo("Ali Valiyev");
        assertThat(saved.getContactPosition()).isEqualTo("Supply manager");
        assertThat(saved.getContactPhone()).isEqualTo("+998901234567");
        assertThat(saved.getContactEmail()).isEqualTo("ali@example.com");
        assertThat(saved.getStatus()).isEqualTo(CounteragentStatus.ACTIVE);
    }

    @Test
    void createRejectsDuplicateFilledInn() {
        CounteragentRequest request = request("CA-002", "123456789");
        when(counteragentRepository.existsByCodeAndIsDeletedFalse("CA-002")).thenReturn(false);
        when(counteragentRepository.existsByInnAndIsDeletedFalse("123456789")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getMessage()).contains("Counteragent INN already exists: 123456789");
                });

        verify(counteragentRepository, never()).save(any());
    }

    @Test
    void updateRejectsDuplicateFilledInnOnAnotherCounteragent() {
        UUID id = UUID.randomUUID();
        Counteragent existing = new Counteragent();
        existing.setId(id);
        existing.setCode("CA-003");
        existing.setName("Old name");
        existing.setInn("111111111");
        when(counteragentRepository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(existing));
        when(counteragentRepository.existsByInnAndIdNotAndIsDeletedFalse("123456789", id)).thenReturn(true);

        assertThatThrownBy(() -> service.update(id, request(null, "123456789")))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getMessage()).contains("Counteragent INN already exists: 123456789");
                });

        verify(counteragentRepository, never()).save(any());
    }

    private CounteragentRequest request(String code, String inn) {
        return new CounteragentRequest(
                code,
                "Tashkent Service LLC",
                inn,
                "Ali Valiyev",
                "Supply manager",
                "+998901234567",
                "ali@example.com",
                "Tashkent",
                "Director",
                "Bank",
                "20208000123456789001",
                "00444",
                null
        );
    }
}
