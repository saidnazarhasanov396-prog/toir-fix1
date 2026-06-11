package com.toir.service;

import com.toir.repository.WorkOrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkOrderSortTest {

    @Mock WorkOrderRepository repository;

    @InjectMocks WorkOrderService service;

    @Test
    void searchWithDescSortPassesUnsortedPageableToNativeRepositoryQuery() {
        Sort sort = Sort.by("updatedAt").descending();
        when(repository.searchPaginated(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        service.search(null, null, null, 0, 20, null, null, null, sort);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).searchPaginated(any(), any(), any(), any(), any(), any(), captor.capture());
        assertThat(captor.getValue().getSort().isUnsorted()).isTrue();
        assertThat(captor.getValue().getPageNumber()).isEqualTo(0);
        assertThat(captor.getValue().getPageSize()).isEqualTo(20);
    }

    @Test
    void searchWithAscSortPassesUnsortedPageableToNativeRepositoryQuery() {
        Sort sort = Sort.by("plannedStart").ascending();
        when(repository.searchPaginated(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        service.search(null, null, null, 0, 20, null, null, null, sort);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).searchPaginated(any(), any(), any(), any(), any(), any(), captor.capture());
        assertThat(captor.getValue().getSort().isUnsorted()).isTrue();
    }
}
