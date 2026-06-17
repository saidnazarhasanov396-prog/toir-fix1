package com.toir.service;

import com.toir.dto.approval.ApprovableDocumentDto;
import com.toir.enums.ApprovalStatus;
import com.toir.enums.ApprovalTargetType;
import com.toir.repository.ApprovableDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApprovableDocumentServiceTest {

    @Mock
    ApprovableDocumentRepository repository;

    ApprovableDocumentService service;

    @BeforeEach
    void setUp() {
        service = new ApprovableDocumentService(repository);
    }

    @Test
    void searchReturnsCorrectPageSlice() {
        List<ApprovableDocumentDto> documents = IntStream.range(0, 5)
                .mapToObj(i -> document("DOC-" + i))
                .toList();
        when(repository.searchAll(isNull(), isNull(), isNull())).thenReturn(documents);

        Page<ApprovableDocumentDto> result = service.search(null, null, null, 0, 2);

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent().get(0).code()).isEqualTo("DOC-0");
        assertThat(result.getContent().get(1).code()).isEqualTo("DOC-1");
        assertThat(result.getTotalElements()).isEqualTo(5);
        assertThat(result.getNumber()).isZero();
        assertThat(result.getSize()).isEqualTo(2);
    }

    @Test
    void searchClampsSizeToMax100() {
        List<ApprovableDocumentDto> documents = IntStream.range(0, 150)
                .mapToObj(i -> document("DOC-" + i))
                .toList();
        when(repository.searchAll(isNull(), isNull(), isNull())).thenReturn(documents);

        Page<ApprovableDocumentDto> result = service.search(null, null, null, 0, 200);

        assertThat(result.getContent()).hasSize(100);
        assertThat(result.getSize()).isEqualTo(100);
        assertThat(result.getTotalElements()).isEqualTo(150);
    }

    @Test
    void searchDefaultsNegativePageToZero() {
        List<ApprovableDocumentDto> documents = List.of(document("DOC-0"), document("DOC-1"), document("DOC-2"));
        when(repository.searchAll(isNull(), isNull(), isNull())).thenReturn(documents);

        Page<ApprovableDocumentDto> result = service.search(null, null, null, -3, 2);

        assertThat(result.getNumber()).isZero();
        assertThat(result.getContent()).extracting(ApprovableDocumentDto::code)
                .containsExactly("DOC-0", "DOC-1");
    }

    @Test
    void searchConvertsKeywordToLowercasePattern() {
        when(repository.searchAll(eq("%pump%"), isNull(), isNull())).thenReturn(List.of());

        service.search("  PUMP  ", null, null, 0, 20);

        verify(repository).searchAll("%pump%", null, null);
    }

    @Test
    void searchPassesNullPatternWhenSearchIsNull() {
        when(repository.searchAll(isNull(), isNull(), isNull())).thenReturn(List.of());

        service.search(null, null, null, 0, 20);

        verify(repository).searchAll(null, null, null);
    }

    @Test
    void searchConvertsTypeEnumToString() {
        when(repository.searchAll(isNull(), eq("WORK_ORDER"), isNull())).thenReturn(List.of());

        service.search(null, ApprovalTargetType.WORK_ORDER, null, 0, 20);

        ArgumentCaptor<String> typeCaptor = ArgumentCaptor.forClass(String.class);
        verify(repository).searchAll(isNull(), typeCaptor.capture(), isNull());
        assertThat(typeCaptor.getValue()).isEqualTo("WORK_ORDER");
    }

    @Test
    void searchConvertsStatusEnumToString() {
        when(repository.searchAll(isNull(), isNull(), eq("PENDING"))).thenReturn(List.of());

        service.search(null, null, ApprovalStatus.PENDING, 0, 20);

        ArgumentCaptor<String> statusCaptor = ArgumentCaptor.forClass(String.class);
        verify(repository).searchAll(isNull(), isNull(), statusCaptor.capture());
        assertThat(statusCaptor.getValue()).isEqualTo("PENDING");
    }

    @Test
    void searchReturnsEmptyPageWhenRepositoryIsEmpty() {
        when(repository.searchAll(isNull(), isNull(), isNull())).thenReturn(List.of());

        Page<ApprovableDocumentDto> result = service.search(null, null, null, 0, 20);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    private ApprovableDocumentDto document(String code) {
        return new ApprovableDocumentDto(
                UUID.randomUUID(),
                ApprovalTargetType.WORK_ORDER,
                code,
                "Document " + code,
                null,
                null,
                Instant.parse("2026-06-17T10:00:00Z")
        );
    }
}
