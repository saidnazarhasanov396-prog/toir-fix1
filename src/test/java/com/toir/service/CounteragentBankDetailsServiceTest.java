package com.toir.service;

import com.toir.dto.counteragent.CounteragentBankDetailRequest;
import com.toir.dto.counteragent.CounteragentDto;
import com.toir.dto.counteragent.CounteragentRequest;
import com.toir.entity.Counteragent;
import com.toir.entity.CounteragentBankDetail;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CounteragentBankDetailsServiceTest {

    @Mock
    CounteragentRepository counteragentRepository;

    @Mock
    PurchaseOrderRepository purchaseOrderRepository;

    @InjectMocks
    CounteragentService service;

    @Test
    void createStoresOrderedBankDetailsAndMirrorsEffectivePrimaryToLegacyFields() {
        CounteragentRequest request = request(List.of(
                bankDetail(null, " First Bank ", " 111 ", " 001 ", false),
                bankDetail(null, " Primary Bank ", " 222 ", " 002 ", true)
        ));
        stubSuccessfulCreate();

        CounteragentDto result = service.create(request);

        ArgumentCaptor<Counteragent> captor = ArgumentCaptor.forClass(Counteragent.class);
        verify(counteragentRepository).save(captor.capture());
        Counteragent saved = captor.getValue();
        assertThat(saved.getBankDetails())
                .extracting(
                        CounteragentBankDetail::getBankName,
                        CounteragentBankDetail::getBankAccount,
                        CounteragentBankDetail::getMfo,
                        CounteragentBankDetail::isPrimary,
                        CounteragentBankDetail::getDisplayOrder
                )
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("First Bank", "111", "001", false, 0),
                        org.assertj.core.groups.Tuple.tuple("Primary Bank", "222", "002", true, 1)
                );
        assertThat(saved.getBankDetails()).allSatisfy(detail ->
                assertThat(detail.getCounteragent()).isSameAs(saved));
        assertThat(saved.getBankName()).isEqualTo("Primary Bank");
        assertThat(saved.getBankAccount()).isEqualTo("222");
        assertThat(saved.getMfo()).isEqualTo("002");
        assertThat(result.bankDetails())
                .extracting(detail -> detail.displayOrder())
                .containsExactly(0, 1);
    }

    @Test
    void createRejectsMultiplePrimaryBankDetails() {
        when(counteragentRepository.existsByInnAndIsDeletedFalse("123456789")).thenReturn(false);

        assertThatThrownBy(() -> service.create(request(List.of(
                bankDetail(null, "First", "111", "001", true),
                bankDetail(null, "Second", "222", "002", true)
        ))))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("Only one bank detail can be primary");
                });

        verify(counteragentRepository, never()).save(any());
    }

    @Test
    void createValidatesMultiplePrimaryBankDetailsBeforeComparingLegacyFields() {
        when(counteragentRepository.existsByInnAndIsDeletedFalse("123456789")).thenReturn(false);
        CounteragentRequest request = request(
                List.of(
                        bankDetail(null, "First", "111", "001", true),
                        bankDetail(null, "Second", "222", "002", true)
                ),
                "Conflicting legacy bank",
                "999",
                "009"
        );

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOfSatisfying(RestException.class, ex ->
                        assertThat(ex.getMessage()).contains("Only one bank detail can be primary"));
    }

    @Test
    void createRejectsMoreThanTenBankDetails() {
        List<CounteragentBankDetailRequest> details = java.util.stream.IntStream.range(0, 11)
                .mapToObj(index -> bankDetail(
                        null,
                        "Bank " + index,
                        "Account " + index,
                        "MFO " + index,
                        index == 0
                ))
                .toList();
        when(counteragentRepository.existsByInnAndIsDeletedFalse("123456789")).thenReturn(false);

        assertThatThrownBy(() -> service.create(request(details)))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("at most 10 bank details");
                });
    }

    @Test
    void createRejectsIncompleteArrayBankDetail() {
        when(counteragentRepository.existsByInnAndIsDeletedFalse("123456789")).thenReturn(false);

        assertThatThrownBy(() -> service.create(request(List.of(
                bankDetail(null, "Bank", " ", "001", true)
        ))))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("Bank detail fields are required");
                });
    }

    @Test
    void createRejectsConflictingArrayAndLegacyBankDetails() {
        CounteragentRequest request = request(
                List.of(bankDetail(null, "Array Bank", "111", "001", true)),
                "Legacy Bank",
                "111",
                "001"
        );
        when(counteragentRepository.existsByInnAndIsDeletedFalse("123456789")).thenReturn(false);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("conflict with bankDetails");
                });
    }

    @Test
    void createAcceptsMatchingLegacyAgainstFirstArrayItemWhenPrimaryIsMissing() {
        CounteragentRequest request = request(
                List.of(
                        bankDetail(null, "First", "111", "001", false),
                        bankDetail(null, "Second", "222", "002", false)
                ),
                " First ",
                "111",
                "001"
        );
        stubSuccessfulCreate();

        service.create(request);

        ArgumentCaptor<Counteragent> captor = ArgumentCaptor.forClass(Counteragent.class);
        verify(counteragentRepository).save(captor.capture());
        assertThat(captor.getValue().getBankName()).isEqualTo("First");
        assertThat(captor.getValue().getBankDetails()).noneMatch(CounteragentBankDetail::isPrimary);
    }

    @Test
    void createAcceptsPresentEmptyArrayAsDeleteAllSemantics() {
        CounteragentRequest request = request(List.of());
        stubSuccessfulCreate();

        service.create(request);

        ArgumentCaptor<Counteragent> captor = ArgumentCaptor.forClass(Counteragent.class);
        verify(counteragentRepository).save(captor.capture());
        assertThat(captor.getValue().getBankDetails()).isEmpty();
        assertThat(captor.getValue().getBankName()).isNull();
        assertThat(captor.getValue().getBankAccount()).isNull();
        assertThat(captor.getValue().getMfo()).isNull();
    }

    @Test
    void createAcceptsPartialLegacyBankDetailsWithoutLosingAvailableValues() {
        CounteragentRequest request = request(null, " Legacy Bank ", null, " 001 ");
        stubSuccessfulCreate();

        service.create(request);

        ArgumentCaptor<Counteragent> captor = ArgumentCaptor.forClass(Counteragent.class);
        verify(counteragentRepository).save(captor.capture());
        assertThat(captor.getValue().getBankDetails())
                .singleElement()
                .satisfies(detail -> {
                    assertThat(detail.getBankName()).isEqualTo("Legacy Bank");
                    assertThat(detail.getBankAccount()).isEmpty();
                    assertThat(detail.getMfo()).isEqualTo("001");
                    assertThat(detail.isPrimary()).isTrue();
                    assertThat(detail.getDisplayOrder()).isZero();
                });
    }

    @Test
    void updateReconcilesBankDetailsByIdAndRemovesOmittedRows() {
        UUID counteragentId = UUID.randomUUID();
        UUID retainedId = UUID.randomUUID();
        UUID omittedId = UUID.randomUUID();
        Counteragent existing = counteragent(counteragentId);
        existing.getBankDetails().add(existingDetail(existing, retainedId, "Old", "111", "001", true, 0));
        existing.getBankDetails().add(existingDetail(existing, omittedId, "Remove", "222", "002", false, 1));
        when(counteragentRepository.findByIdAndIsDeletedFalse(counteragentId)).thenReturn(Optional.of(existing));
        when(counteragentRepository.existsByInnAndIdNotAndIsDeletedFalse("123456789", counteragentId))
                .thenReturn(false);
        when(counteragentRepository.save(any(Counteragent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.update(counteragentId, request(List.of(
                bankDetail(retainedId, "Updated", "333", "003", false),
                bankDetail(null, "New", "444", "004", true)
        )));

        assertThat(existing.getBankDetails())
                .extracting(
                        CounteragentBankDetail::getId,
                        CounteragentBankDetail::getBankName,
                        CounteragentBankDetail::getDisplayOrder
                )
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(retainedId, "Updated", 0),
                        org.assertj.core.groups.Tuple.tuple(null, "New", 1)
                );
        assertThat(existing.getBankName()).isEqualTo("New");
        verify(counteragentRepository).flush();
    }

    @Test
    void updateRejectsBankDetailIdOwnedByAnotherCounteragent() {
        UUID counteragentId = UUID.randomUUID();
        UUID foreignId = UUID.randomUUID();
        Counteragent existing = counteragent(counteragentId);
        when(counteragentRepository.findByIdAndIsDeletedFalse(counteragentId)).thenReturn(Optional.of(existing));
        when(counteragentRepository.existsByInnAndIdNotAndIsDeletedFalse("123456789", counteragentId))
                .thenReturn(false);

        assertThatThrownBy(() -> service.update(counteragentId, request(List.of(
                bankDetail(foreignId, "Foreign", "111", "001", true)
        ))))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("does not belong to counteragent");
                });

        verify(counteragentRepository, never()).save(any());
    }

    private void stubSuccessfulCreate() {
        when(counteragentRepository.maxSequenceByCodePrefix("CA-" + java.time.Year.now().getValue() + "-"))
                .thenReturn(0L);
        when(counteragentRepository.existsByCodeAndIsDeletedFalse("CA-" + java.time.Year.now().getValue() + "-0001"))
                .thenReturn(false);
        when(counteragentRepository.existsByInnAndIsDeletedFalse("123456789")).thenReturn(false);
        when(counteragentRepository.save(any(Counteragent.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private Counteragent counteragent(UUID id) {
        Counteragent counteragent = new Counteragent();
        counteragent.setId(id);
        counteragent.setCode("CA-001");
        counteragent.setName("Existing");
        counteragent.setBankDetails(new ArrayList<>());
        return counteragent;
    }

    private CounteragentBankDetail existingDetail(
            Counteragent counteragent,
            UUID id,
            String bankName,
            String bankAccount,
            String mfo,
            boolean primary,
            int displayOrder
    ) {
        CounteragentBankDetail detail = new CounteragentBankDetail();
        detail.setId(id);
        detail.setCounteragent(counteragent);
        detail.setBankName(bankName);
        detail.setBankAccount(bankAccount);
        detail.setMfo(mfo);
        detail.setPrimary(primary);
        detail.setDisplayOrder(displayOrder);
        return detail;
    }

    private CounteragentBankDetailRequest bankDetail(
            UUID id,
            String bankName,
            String bankAccount,
            String mfo,
            boolean primary
    ) {
        return new CounteragentBankDetailRequest(id, bankName, bankAccount, mfo, primary);
    }

    private CounteragentRequest request(List<CounteragentBankDetailRequest> bankDetails) {
        return request(bankDetails, null, null, null);
    }

    private CounteragentRequest request(
            List<CounteragentBankDetailRequest> bankDetails,
            String bankName,
            String bankAccount,
            String mfo
    ) {
        return new CounteragentRequest(
                "Tashkent Service LLC",
                "123456789",
                "Ali Valiyev",
                "Supply manager",
                "+998901234567",
                "ali@example.com",
                "Tashkent",
                "Director",
                bankDetails,
                bankName,
                bankAccount,
                mfo,
                null
        );
    }
}
