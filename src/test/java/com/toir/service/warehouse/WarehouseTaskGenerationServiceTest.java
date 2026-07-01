package com.toir.service.warehouse;

import com.toir.dto.warehouse.WarehouseTaskDto;
import com.toir.dto.warehouse.WarehouseTaskRequest;
import com.toir.enums.WarehouseStockStatus;
import com.toir.enums.WarehouseTaskSourceType;
import com.toir.enums.WarehouseTaskStatus;
import com.toir.enums.WarehouseTaskType;
import com.toir.repository.WarehouseTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WarehouseTaskGenerationServiceTest {

    @Mock
    WarehouseTaskRepository taskRepository;

    @Mock
    WarehouseTaskService taskService;

    @Mock
    WarehouseBinSuggestionService binSuggestionService;

    WarehouseTaskGenerationService service;

    @BeforeEach
    void setUp() {
        service = new WarehouseTaskGenerationService(taskRepository, taskService, binSuggestionService);
    }

    @Test
    void generatePutawayForReceiptBuildsGeneratedTask() {
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        UUID fromBinId = UUID.randomUUID();
        UUID toBinId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        LocalDate expiryDate = LocalDate.of(2028, 1, 31);
        when(taskRepository.existsByGenerationKeyAndIsDeletedFalse("receipt:1")).thenReturn(false);
        when(binSuggestionService.suggestPutawayBin(warehouseId, fromBinId, WarehouseStockStatus.AVAILABLE))
                .thenReturn(Optional.of(toBinId));
        when(taskService.createGenerated(any(WarehouseTaskRequest.class), eq("receipt:1")))
                .thenReturn(new WarehouseTaskDto(
                        UUID.randomUUID(),
                        "WT-2026-00010",
                        WarehouseTaskType.PUTAWAY,
                        WarehouseTaskStatus.OPEN,
                        null,
                        warehouseId,
                        WarehouseTaskSourceType.PURCHASE_ORDER,
                        sourceId,
                        "receipt:1",
                        true,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "comment",
                        List.of(),
                        null,
                        null
                ));

        var result = service.generatePutawayForReceipt(new WarehouseTaskGenerationService.ReceiptPutawayCommand(
                "receipt:1",
                warehouseId,
                sparePartId,
                fromBinId,
                new BigDecimal("3.5"),
                "pcs",
                "LOT",
                "SN",
                expiryDate,
                WarehouseStockStatus.AVAILABLE,
                WarehouseTaskSourceType.PURCHASE_ORDER,
                sourceId,
                "comment"
        ));

        assertThat(result).isPresent();
        ArgumentCaptor<WarehouseTaskRequest> requestCaptor = ArgumentCaptor.forClass(WarehouseTaskRequest.class);
        verify(taskService).createGenerated(requestCaptor.capture(), eq("receipt:1"));
        WarehouseTaskRequest request = requestCaptor.getValue();
        assertThat(request.taskType()).isEqualTo(WarehouseTaskType.PUTAWAY);
        assertThat(request.warehouseId()).isEqualTo(warehouseId);
        assertThat(request.sourceType()).isEqualTo(WarehouseTaskSourceType.PURCHASE_ORDER);
        assertThat(request.sourceId()).isEqualTo(sourceId);
        assertThat(request.lines()).hasSize(1);
        assertThat(request.lines().getFirst().sparePartId()).isEqualTo(sparePartId);
        assertThat(request.lines().getFirst().fromBinId()).isEqualTo(fromBinId);
        assertThat(request.lines().getFirst().toBinId()).isEqualTo(toBinId);
        assertThat(request.lines().getFirst().plannedQty()).isEqualByComparingTo("3.5");
        assertThat(request.lines().getFirst().expiryDate()).isEqualTo(expiryDate);
    }

    @Test
    void generatePutawaySkipsDuplicateGenerationKey() {
        when(taskRepository.existsByGenerationKeyAndIsDeletedFalse("receipt:1")).thenReturn(true);

        var result = service.generatePutawayForReceipt(command("receipt:1"));

        assertThat(result).isEmpty();
        verify(binSuggestionService, never()).suggestPutawayBin(any(), any(), any());
        verify(taskService, never()).createGenerated(any(), any());
    }

    @Test
    void generatePutawayCreatesTaskWhenNoDestinationBinIsSuggested() {
        UUID warehouseId = UUID.randomUUID();
        UUID fromBinId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        when(taskRepository.existsByGenerationKeyAndIsDeletedFalse("receipt:1")).thenReturn(false);
        when(binSuggestionService.suggestPutawayBin(warehouseId, fromBinId, WarehouseStockStatus.AVAILABLE))
                .thenReturn(Optional.empty());
        when(taskService.createGenerated(any(WarehouseTaskRequest.class), eq("receipt:1")))
                .thenReturn(new WarehouseTaskDto(
                        UUID.randomUUID(),
                        "WT-2026-00011",
                        WarehouseTaskType.PUTAWAY,
                        WarehouseTaskStatus.OPEN,
                        null,
                        warehouseId,
                        WarehouseTaskSourceType.PURCHASE_ORDER,
                        sourceId,
                        "receipt:1",
                        true,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "comment",
                        List.of(),
                        null,
                        null
                ));

        var result = service.generatePutawayForReceipt(new WarehouseTaskGenerationService.ReceiptPutawayCommand(
                "receipt:1",
                warehouseId,
                sparePartId,
                fromBinId,
                BigDecimal.ONE,
                "pcs",
                null,
                null,
                null,
                WarehouseStockStatus.AVAILABLE,
                WarehouseTaskSourceType.PURCHASE_ORDER,
                sourceId,
                "comment"
        ));

        assertThat(result).isPresent();
        ArgumentCaptor<WarehouseTaskRequest> requestCaptor = ArgumentCaptor.forClass(WarehouseTaskRequest.class);
        verify(taskService).createGenerated(requestCaptor.capture(), eq("receipt:1"));
        WarehouseTaskRequest request = requestCaptor.getValue();
        assertThat(request.lines()).hasSize(1);
        assertThat(request.lines().getFirst().sparePartId()).isEqualTo(sparePartId);
        assertThat(request.lines().getFirst().fromBinId()).isEqualTo(fromBinId);
        assertThat(request.lines().getFirst().toBinId()).isNull();
        assertThat(request.comment()).contains("Destination bin could not be suggested automatically.");
    }

    private WarehouseTaskGenerationService.ReceiptPutawayCommand command(String generationKey) {
        return new WarehouseTaskGenerationService.ReceiptPutawayCommand(
                generationKey,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                BigDecimal.ONE,
                "pcs",
                null,
                null,
                null,
                WarehouseStockStatus.AVAILABLE,
                WarehouseTaskSourceType.PURCHASE_ORDER,
                UUID.randomUUID(),
                null
        );
    }
}
