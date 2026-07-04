package com.toir.controller;

import com.toir.dto.procurement.ProcurementRequestDto;
import com.toir.dto.procurement.ProcurementRequestLineDto;
import com.toir.enums.ProcurementRequestStatus;
import com.toir.enums.ProcurementRequestType;
import com.toir.service.ProcurementRequestService;
import com.toir.service.PurchaseOrderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcurementRequestControllerSortTest {

    @Mock
    ProcurementRequestService service;

    @Mock
    PurchaseOrderService purchaseOrderService;

    @Test
    void listSortsBySource() {
        ProcurementRequestDto manual = request("manual", "MANUAL", "ALLOCATED", 1);
        ProcurementRequestDto automatic = request("auto", "AUTO", "UNALLOCATED", 1);
        when(service.findAll(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(List.of(manual, automatic));

        var result = controller().list(null, null, null, null, null, null, null, null, 0, 20, "source", "asc").getBody();

        assertThat(result).isNotNull();
        assertThat(result.getContent()).extracting(ProcurementRequestDto::id)
                .containsExactly(automatic.id(), manual.id());
    }

    @Test
    void listSortsByLinesCount() {
        ProcurementRequestDto oneLine = request("one", "MANUAL", "ALLOCATED", 1);
        ProcurementRequestDto threeLines = request("three", "MANUAL", "ALLOCATED", 3);
        when(service.findAll(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(List.of(oneLine, threeLines));

        var result = controller().list(null, null, null, null, null, null, null, null, 0, 20, "linesCount", "desc").getBody();

        assertThat(result).isNotNull();
        assertThat(result.getContent()).extracting(ProcurementRequestDto::id)
                .containsExactly(threeLines.id(), oneLine.id());
    }

    @Test
    void listSortsByBudgetAllocationStatus() {
        ProcurementRequestDto allocated = request("allocated", "MANUAL", "ALLOCATED", 1);
        ProcurementRequestDto unallocated = request("unallocated", "MANUAL", "UNALLOCATED", 1);
        when(service.findAll(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(List.of(unallocated, allocated));

        var result = controller().list(null, null, null, null, null, null, null, null, 0, 20, "budgetAllocationStatus", "asc").getBody();

        assertThat(result).isNotNull();
        assertThat(result.getContent()).extracting(ProcurementRequestDto::id)
                .containsExactly(allocated.id(), unallocated.id());
    }

    private ProcurementRequestController controller() {
        return new ProcurementRequestController(service, purchaseOrderService);
    }

    private static ProcurementRequestDto request(String title, String source, String budgetStatus, int lineCount) {
        UUID id = UUID.randomUUID();
        List<ProcurementRequestLineDto> lines = java.util.stream.IntStream.range(0, lineCount)
                .mapToObj(index -> new ProcurementRequestLineDto(UUID.randomUUID(), id, UUID.randomUUID(), 1, 0, 1, "pcs", 10.0, 10.0, null))
                .toList();
        return new ProcurementRequestDto(
                id,
                "PR-" + title,
                title,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                ProcurementRequestType.SPARE_PART,
                null,
                null,
                null,
                null,
                ProcurementRequestStatus.DRAFT,
                source,
                null,
                100.0,
                null,
                null,
                null,
                null,
                null,
                lines,
                null,
                budgetStatus,
                null,
                null
        );
    }
}
