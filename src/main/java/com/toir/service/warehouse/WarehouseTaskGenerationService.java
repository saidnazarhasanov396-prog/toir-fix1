package com.toir.service.warehouse;

import com.toir.dto.warehouse.WarehouseTaskDto;
import com.toir.dto.warehouse.WarehouseTaskLineRequest;
import com.toir.dto.warehouse.WarehouseTaskRequest;
import com.toir.entity.equipment.ProcurementRequestLine;
import com.toir.entity.projects.ProcurementRequest;
import com.toir.enums.WarehouseStockStatus;
import com.toir.enums.WarehouseTaskSourceType;
import com.toir.enums.WarehouseTaskType;
import com.toir.exception.RestException;
import com.toir.repository.WarehouseTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WarehouseTaskGenerationService {

    private final WarehouseTaskRepository taskRepository;
    private final WarehouseTaskService taskService;
    private final WarehouseBinSuggestionService binSuggestionService;

    @Transactional
    public Optional<WarehouseTaskDto> generateReceiveForApprovedProcurement(ProcurementRequest request) {
        if (request == null || request.getId() == null || request.getWarehouseId() == null) {
            return Optional.empty();
        }
        String generationKey = "procurement-receive:" + request.getId();
        if (taskRepository.existsByGenerationKeyAndIsDeletedFalse(generationKey)) {
            return Optional.empty();
        }
        List<ProcurementRequestLine> procurementLines = receivableSparePartLines(request);
        if (procurementLines.isEmpty()) {
            return Optional.empty();
        }
        UUID receivingBinId = binSuggestionService.suggestReceivingBin(request.getWarehouseId())
                .orElseThrow(() -> RestException.badRequest(
                        "No active receiving bin is configured for this warehouse"
                ));
        List<WarehouseTaskLineRequest> lines = receiveLines(procurementLines, receivingBinId);
        WarehouseTaskRequest taskRequest = new WarehouseTaskRequest(
                WarehouseTaskType.RECEIVE,
                null,
                request.getWarehouseId(),
                WarehouseTaskSourceType.PROCUREMENT_REQUEST,
                request.getId(),
                null,
                null,
                "Receive approved procurement request: " + request.getNumber(),
                lines
        );
        return Optional.of(taskService.createGenerated(taskRequest, generationKey));
    }

    @Transactional
    public Optional<WarehouseTaskDto> generatePutawayForReceipt(ReceiptPutawayCommand command) {
        if (!canGenerate(command)) {
            return Optional.empty();
        }
        String generationKey = command.generationKey();
        if (taskRepository.existsByGenerationKeyAndIsDeletedFalse(generationKey)) {
            return Optional.empty();
        }
        WarehouseStockStatus stockStatus = effectiveStatus(command.stockStatus());
        Optional<UUID> suggestedToBinId = binSuggestionService.suggestPutawayBin(
                command.warehouseId(),
                command.fromBinId(),
                stockStatus
        );
        WarehouseTaskLineRequest line = new WarehouseTaskLineRequest(
                command.sparePartId(),
                null,
                command.fromBinId(),
                suggestedToBinId.orElse(null),
                command.lotNumber(),
                command.serialNumber(),
                command.expiryDate(),
                stockStatus,
                command.quantity(),
                command.unit()
        );
        WarehouseTaskRequest request = new WarehouseTaskRequest(
                WarehouseTaskType.PUTAWAY,
                null,
                command.warehouseId(),
                command.sourceType(),
                command.sourceId(),
                null,
                null,
                suggestedToBinId.isPresent()
                        ? command.comment()
                        : appendComment(command.comment(), "Destination bin could not be suggested automatically."),
                List.of(line)
        );
        return Optional.of(taskService.createGenerated(request, generationKey));
    }

    private boolean canGenerate(ReceiptPutawayCommand command) {
        return command != null
                && command.generationKey() != null
                && !command.generationKey().isBlank()
                && command.warehouseId() != null
                && command.sparePartId() != null
                && command.fromBinId() != null
                && command.sourceType() != null
                && command.sourceId() != null
                && command.quantity() != null
                && command.quantity().compareTo(BigDecimal.ZERO) > 0;
    }

    private WarehouseStockStatus effectiveStatus(WarehouseStockStatus status) {
        return status == null ? WarehouseStockStatus.AVAILABLE : status;
    }

    private List<ProcurementRequestLine> receivableSparePartLines(ProcurementRequest request) {
        if (request.getLines() == null || request.getLines().isEmpty()) {
            return List.of();
        }
        List<ProcurementRequestLine> lines = new ArrayList<>();
        for (ProcurementRequestLine line : request.getLines()) {
            if (line == null || line.isDeleted() || line.getSparePartId() == null) {
                continue;
            }
            BigDecimal quantity = receiveQuantity(line);
            if (quantity.compareTo(BigDecimal.ZERO) > 0) {
                lines.add(line);
            }
        }
        return lines;
    }

    private List<WarehouseTaskLineRequest> receiveLines(List<ProcurementRequestLine> procurementLines,
                                                        UUID receivingBinId) {
        List<WarehouseTaskLineRequest> lines = new ArrayList<>();
        for (ProcurementRequestLine line : procurementLines) {
            lines.add(new WarehouseTaskLineRequest(
                    line.getSparePartId(),
                    null,
                    null,
                    receivingBinId,
                    null,
                    null,
                    null,
                    WarehouseStockStatus.AVAILABLE,
                    receiveQuantity(line),
                    line.getUnit()
            ));
        }
        return lines;
    }

    private BigDecimal receiveQuantity(ProcurementRequestLine line) {
        return BigDecimal.valueOf(line.getRemainingQuantity() > 0
                ? line.getRemainingQuantity()
                : line.getQuantity());
    }

    private String appendComment(String comment, String suffix) {
        if (comment == null || comment.isBlank()) {
            return suffix;
        }
        return comment.strip() + " " + suffix;
    }

    public record ReceiptPutawayCommand(
            String generationKey,
            UUID warehouseId,
            UUID sparePartId,
            UUID fromBinId,
            BigDecimal quantity,
            String unit,
            String lotNumber,
            String serialNumber,
            LocalDate expiryDate,
            WarehouseStockStatus stockStatus,
            WarehouseTaskSourceType sourceType,
            UUID sourceId,
            String comment
    ) {
    }
}
