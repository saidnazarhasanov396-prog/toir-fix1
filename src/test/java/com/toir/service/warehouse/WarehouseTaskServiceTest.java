package com.toir.service.warehouse;

import com.toir.dto.warehouse.WarehouseStockMoveRequest;
import com.toir.dto.warehouse.WarehouseStockMoveResponse;
import com.toir.dto.warehouse.WarehouseTaskAssignRequest;
import com.toir.dto.warehouse.WarehouseTaskCompleteRequest;
import com.toir.dto.warehouse.WarehouseTaskLineRequest;
import com.toir.dto.warehouse.WarehouseTaskRequest;
import com.toir.dto.warehouse.WarehouseTaskScanConfirmRequest;
import com.toir.entity.warehouse.WarehouseTask;
import com.toir.entity.warehouse.WarehouseTaskLine;
import com.toir.enums.WarehouseStockStatus;
import com.toir.enums.WarehouseTaskLineStatus;
import com.toir.enums.WarehouseTaskPriority;
import com.toir.enums.WarehouseTaskSourceType;
import com.toir.enums.WarehouseTaskStatus;
import com.toir.enums.WarehouseTaskType;
import com.toir.exception.RestException;
import com.toir.repository.WarehouseTaskRepository;
import com.toir.util.AuditBuilderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WarehouseTaskServiceTest {

    @Mock
    WarehouseTaskRepository taskRepository;

    @Mock
    WarehouseStockMoveService stockMoveService;

    @Mock
    AuditBuilderService auditBuilderService;

    WarehouseTaskService service;

    @BeforeEach
    void setUp() {
        service = new WarehouseTaskService(taskRepository, stockMoveService, auditBuilderService);
    }

    @Test
    void createAssignStartScanAndCompletePutawayTask() {
        UUID taskId = UUID.randomUUID();
        UUID lineId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        UUID fromBinId = UUID.randomUUID();
        UUID toBinId = UUID.randomUUID();
        UUID assigneeId = UUID.randomUUID();
        UUID movementId = UUID.randomUUID();
        LocalDate expiryDate = LocalDate.of(2027, 3, 15);
        AtomicReference<WarehouseTask> storedTask = new AtomicReference<>();

        when(taskRepository.countByIsDeletedFalse()).thenReturn(4L);
        when(taskRepository.save(any(WarehouseTask.class))).thenAnswer(invocation -> {
            WarehouseTask task = invocation.getArgument(0);
            if (task.getId() == null) {
                task.setId(taskId);
            }
            task.getLines().forEach(line -> {
                if (line.getId() == null) {
                    line.setId(lineId);
                }
            });
            storedTask.set(task);
            return task;
        });
        when(taskRepository.findByIdAndIsDeletedFalse(taskId)).thenAnswer(invocation -> Optional.ofNullable(storedTask.get()));
        when(stockMoveService.move(any(WarehouseStockMoveRequest.class))).thenReturn(new WarehouseStockMoveResponse(
                movementId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                warehouseId,
                sparePartId,
                fromBinId,
                toBinId,
                new BigDecimal("5.0000")
        ));

        var created = service.create(new WarehouseTaskRequest(
                WarehouseTaskType.PUTAWAY,
                null,
                warehouseId,
                WarehouseTaskSourceType.PURCHASE_ORDER,
                UUID.randomUUID(),
                null,
                null,
                "putaway received stock",
                List.of(new WarehouseTaskLineRequest(
                        sparePartId,
                        null,
                        fromBinId,
                        toBinId,
                        "LOT-7",
                        "SN-8",
                        expiryDate,
                        WarehouseStockStatus.AVAILABLE,
                        new BigDecimal("5.0000"),
                        "pcs"
                ))
        ));

        assertThat(created.status()).isEqualTo(WarehouseTaskStatus.OPEN);
        assertThat(created.priority()).isEqualTo(WarehouseTaskPriority.NORMAL);
        assertThat(created.taskNumber()).isEqualTo("WT-2026-00005");
        assertThat(created.lines()).hasSize(1);
        assertThat(created.lines().getFirst().status()).isEqualTo(WarehouseTaskLineStatus.OPEN);

        var assigned = service.assign(taskId, new WarehouseTaskAssignRequest(assigneeId));
        assertThat(assigned.status()).isEqualTo(WarehouseTaskStatus.ASSIGNED);
        assertThat(assigned.assignedToId()).isEqualTo(assigneeId);

        var started = service.start(taskId);
        assertThat(started.status()).isEqualTo(WarehouseTaskStatus.IN_PROGRESS);
        assertThat(started.startedAt()).isNotNull();

        var scanConfirmed = service.scanConfirm(taskId, lineId, new WarehouseTaskScanConfirmRequest(
                toBinId,
                sparePartId,
                null,
                "LOT-7",
                "SN-8"
        ));
        assertThat(scanConfirmed.lines().getFirst().scanConfirmed()).isTrue();

        var completed = service.complete(taskId, new WarehouseTaskCompleteRequest(
                List.of(new WarehouseTaskCompleteRequest.Line(lineId, new BigDecimal("5.0000"), null)),
                "done"
        ));
        assertThat(completed.status()).isEqualTo(WarehouseTaskStatus.DONE);
        assertThat(completed.completedAt()).isNotNull();

        ArgumentCaptor<WarehouseStockMoveRequest> moveCaptor = ArgumentCaptor.forClass(WarehouseStockMoveRequest.class);
        verify(stockMoveService).move(moveCaptor.capture());
        WarehouseStockMoveRequest move = moveCaptor.getValue();
        assertThat(move.warehouseId()).isEqualTo(warehouseId);
        assertThat(move.sparePartId()).isEqualTo(sparePartId);
        assertThat(move.fromBinId()).isEqualTo(fromBinId);
        assertThat(move.toBinId()).isEqualTo(toBinId);
        assertThat(move.quantity()).isEqualByComparingTo("5.0000");
        assertThat(move.lotNumber()).isEqualTo("LOT-7");
        assertThat(move.serialNumber()).isEqualTo("SN-8");
        assertThat(move.expiryDate()).isEqualTo(expiryDate);
        assertThat(move.stockStatus()).isEqualTo(WarehouseStockStatus.AVAILABLE);
    }

    @Test
    void completeRejectsVarianceWithoutExceptionReason() {
        UUID taskId = UUID.randomUUID();
        UUID lineId = UUID.randomUUID();
        WarehouseTask task = savedTask(
                taskId,
                lineId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                null
        );
        task.setStatus(WarehouseTaskStatus.IN_PROGRESS);
        when(taskRepository.findByIdAndIsDeletedFalse(taskId)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> service.complete(taskId, new WarehouseTaskCompleteRequest(
                List.of(new WarehouseTaskCompleteRequest.Line(lineId, new BigDecimal("4.0000"), null)),
                null
        )))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("exception reason");

        verify(stockMoveService, never()).move(any());
    }

    @Test
    void scanConfirmRejectsWrongBinBeforeUpdatingLine() {
        UUID taskId = UUID.randomUUID();
        UUID lineId = UUID.randomUUID();
        UUID expectedToBinId = UUID.randomUUID();
        WarehouseTask task = savedTask(
                taskId,
                lineId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                expectedToBinId,
                null
        );
        task.setStatus(WarehouseTaskStatus.IN_PROGRESS);
        when(taskRepository.findByIdAndIsDeletedFalse(taskId)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> service.scanConfirm(taskId, lineId, new WarehouseTaskScanConfirmRequest(
                UUID.randomUUID(),
                task.getLines().getFirst().getSparePartId(),
                null,
                null,
                null
        )))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("bin");

        assertThat(task.getLines().getFirst().isScanConfirmed()).isFalse();
    }

    private WarehouseTask savedTask(UUID taskId,
                                    UUID lineId,
                                    UUID warehouseId,
                                    UUID sparePartId,
                                    UUID fromBinId,
                                    UUID toBinId,
                                    LocalDate expiryDate) {
        WarehouseTask task = new WarehouseTask();
        task.setId(taskId);
        task.setTaskNumber("WT-2026-00005");
        task.setTaskType(WarehouseTaskType.PUTAWAY);
        task.setStatus(WarehouseTaskStatus.OPEN);
        task.setPriority(WarehouseTaskPriority.NORMAL);
        task.setWarehouseId(warehouseId);
        task.setSourceType(WarehouseTaskSourceType.PURCHASE_ORDER);
        task.setSourceId(UUID.randomUUID());
        task.setCreatedAt(Instant.now());
        task.setUpdatedAt(Instant.now());

        WarehouseTaskLine line = new WarehouseTaskLine();
        line.setId(lineId);
        line.setTask(task);
        line.setSparePartId(sparePartId);
        line.setFromBinId(fromBinId);
        line.setToBinId(toBinId);
        line.setLotNumber("LOT-7");
        line.setSerialNumber("SN-8");
        line.setExpiryDate(expiryDate);
        line.setStockStatus(WarehouseStockStatus.AVAILABLE);
        line.setPlannedQty(new BigDecimal("5.0000"));
        line.setActualQty(BigDecimal.ZERO);
        line.setUnit("pcs");
        line.setStatus(WarehouseTaskLineStatus.OPEN);
        line.setCreatedAt(Instant.now());
        line.setUpdatedAt(Instant.now());
        task.getLines().add(line);
        return task;
    }
}
