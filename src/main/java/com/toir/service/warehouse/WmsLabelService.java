package com.toir.service.warehouse;

import com.toir.dto.warehouse.WmsLabelPayloadDto;
import com.toir.dto.warehouse.WmsScanValidationRequest;
import com.toir.dto.warehouse.WmsScanValidationResultDto;
import com.toir.entity.SparePart;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.warehouse.WarehouseBin;
import com.toir.entity.warehouse.WarehouseTask;
import com.toir.entity.warehouse.WarehouseTaskLine;
import com.toir.entity.warehouse.WmsLabelEvent;
import com.toir.enums.WarehouseTaskType;
import com.toir.exception.RestException;
import com.toir.repository.SparePartRepository;
import com.toir.repository.WarehouseBinRepository;
import com.toir.repository.WarehouseTaskLineRepository;
import com.toir.repository.WmsLabelEventRepository;
import com.toir.repository.equipment.EquipmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WmsLabelService {

    private static final String PREFIX = "TOIR-WMS";
    private static final String TYPE_BIN = "BIN";
    private static final String TYPE_SPARE_PART = "SPARE_PART";
    private static final String TYPE_EQUIPMENT = "EQUIPMENT";

    private final WarehouseBinRepository binRepository;
    private final SparePartRepository sparePartRepository;
    private final EquipmentRepository equipmentRepository;
    private final WarehouseTaskLineRepository taskLineRepository;
    private final WmsLabelEventRepository labelEventRepository;

    @Transactional
    public WmsLabelPayloadDto binLabel(UUID binId) {
        WarehouseBin bin = binRepository.findByIdAndIsDeletedFalse(binId)
                .orElseThrow(() -> RestException.notFound("Warehouse bin not found: " + binId));
        String payload = PREFIX + "|type=BIN|id=%s|warehouseId=%s|code=%s"
                .formatted(bin.getId(), bin.getWarehouseId(), requireText(bin.getCode(), "Warehouse bin code is required"));
        recordLabel(TYPE_BIN, bin.getId(), bin.getCode(), bin.getWarehouseId(), bin.getId(), null, null, payload);
        return new WmsLabelPayloadDto(
                TYPE_BIN,
                bin.getId(),
                bin.getCode(),
                bin.getWarehouseId(),
                bin.getId(),
                null,
                null,
                payload
        );
    }

    @Transactional
    public WmsLabelPayloadDto sparePartLabel(UUID sparePartId) {
        SparePart sparePart = sparePartRepository.findByIdAndIsDeletedFalse(sparePartId)
                .orElseThrow(() -> RestException.notFound("Spare part not found: " + sparePartId));
        String payload = PREFIX + "|type=SPARE_PART|id=%s|code=%s"
                .formatted(sparePart.getId(), requireText(sparePart.getCode(), "Spare part code is required"));
        recordLabel(TYPE_SPARE_PART, sparePart.getId(), sparePart.getCode(), null, null, sparePart.getId(), null, payload);
        return new WmsLabelPayloadDto(
                TYPE_SPARE_PART,
                sparePart.getId(),
                sparePart.getCode(),
                null,
                null,
                sparePart.getId(),
                null,
                payload
        );
    }

    @Transactional
    public WmsLabelPayloadDto equipmentLabel(UUID equipmentId) {
        Equipment equipment = equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)
                .orElseThrow(() -> RestException.notFound("Equipment not found: " + equipmentId));
        String inventoryNumber = requireText(equipment.getInventoryNumber(), "Equipment inventory number is required");
        String payload = PREFIX + "|type=EQUIPMENT|id=%s|inventoryNumber=%s"
                .formatted(equipment.getId(), inventoryNumber);
        recordLabel(TYPE_EQUIPMENT, equipment.getId(), inventoryNumber, null, null, null, equipment.getId(), payload);
        return new WmsLabelPayloadDto(
                TYPE_EQUIPMENT,
                equipment.getId(),
                inventoryNumber,
                null,
                null,
                null,
                equipment.getId(),
                payload
        );
    }

    @Transactional(readOnly = true)
    public WmsScanValidationResultDto validateScan(WmsScanValidationRequest request) {
        if (request == null) {
            throw RestException.badRequest("scan validation request is required");
        }

        String expectedType = normalizeType(request.expectedType());
        WarehouseTaskLine taskLine = loadTaskLine(request.taskLineId());
        if (taskLine != null) {
            assertTaskLineBelongsToTask(taskLine, request.taskId());
        }

        ParsedScan parsed = parseOrNull(request.scannedPayload());
        if (expectedType == null && parsed != null) {
            expectedType = parsed.type();
        }
        if (expectedType == null) {
            throw RestException.badRequest("expectedType is required");
        }

        UUID expectedId = resolveExpectedId(expectedType, request.expectedId(), taskLine);
        if (parsed == null) {
            return validateManual(expectedType, expectedId, request.manualValue());
        }

        if (!Objects.equals(parsed.type(), expectedType)) {
            throw RestException.badRequest("Scanned type does not match expected type");
        }
        if (expectedId != null && parsed.id() != null && !Objects.equals(expectedId, parsed.id())) {
            throw RestException.badRequest(mismatchMessage(expectedType));
        }

        return new WmsScanValidationResultDto(
                true,
                expectedType,
                expectedId,
                parsed.type(),
                parsed.id(),
                null,
                "Scan is valid"
        );
    }

    private void recordLabel(String labelType,
                             UUID targetId,
                             String targetCode,
                             UUID warehouseId,
                             UUID binId,
                             UUID sparePartId,
                             UUID equipmentId,
                             String payload) {
        WmsLabelEvent event = new WmsLabelEvent();
        event.setLabelType(labelType);
        event.setTargetId(targetId);
        event.setTargetCode(targetCode);
        event.setWarehouseId(warehouseId);
        event.setBinId(binId);
        event.setSparePartId(sparePartId);
        event.setEquipmentId(equipmentId);
        event.setPayload(payload);
        labelEventRepository.save(event);
    }

    private WmsScanValidationResultDto validateManual(String expectedType, UUID expectedId, String manualValue) {
        String normalizedManual = trimToNull(manualValue);
        if (normalizedManual == null) {
            throw RestException.badRequest("scannedPayload or manualValue is required");
        }
        if (!TYPE_BIN.equals(expectedType)) {
            throw RestException.badRequest("Manual scan fallback is supported only for BIN labels");
        }
        if (expectedId == null) {
            throw RestException.badRequest("expectedId or taskLineId is required for manual bin validation");
        }
        WarehouseBin bin = binRepository.findByIdAndIsDeletedFalse(expectedId)
                .orElseThrow(() -> RestException.notFound("Warehouse bin not found: " + expectedId));
        if (!equalsIgnoreCase(normalizedManual, bin.getCode()) && !equalsIgnoreCase(normalizedManual, bin.getBarcode())) {
            throw RestException.badRequest("Manual bin value does not match expected bin code or barcode");
        }
        return new WmsScanValidationResultDto(
                true,
                expectedType,
                expectedId,
                TYPE_BIN,
                expectedId,
                normalizedManual,
                "Scan is valid"
        );
    }

    private ParsedScan parseOrNull(String scannedPayload) {
        String payload = trimToNull(scannedPayload);
        if (payload == null) {
            return null;
        }
        Map<String, String> values = parsePayload(payload);
        String type = normalizeType(values.get("type"));
        if (type == null) {
            throw RestException.badRequest("Scan payload type is required");
        }
        UUID id = parseUuid(values.get("id"), "Scan payload id is invalid");
        return new ParsedScan(type, id, values);
    }

    private Map<String, String> parsePayload(String payload) {
        String[] parts = payload.split("\\|");
        if (parts.length < 2 || !PREFIX.equals(parts[0])) {
            throw RestException.badRequest("Invalid WMS scan payload");
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (int i = 1; i < parts.length; i++) {
            int separator = parts[i].indexOf('=');
            if (separator <= 0) {
                throw RestException.badRequest("Invalid WMS scan payload segment");
            }
            String key = parts[i].substring(0, separator).trim();
            String value = parts[i].substring(separator + 1).trim();
            if (key.isEmpty()) {
                throw RestException.badRequest("Invalid WMS scan payload key");
            }
            values.put(key, value);
        }
        return values;
    }

    private WarehouseTaskLine loadTaskLine(UUID taskLineId) {
        if (taskLineId == null) {
            return null;
        }
        WarehouseTaskLine line = taskLineRepository.findById(taskLineId)
                .orElseThrow(() -> RestException.notFound("Warehouse task line not found: " + taskLineId));
        if (line.isDeleted()) {
            throw RestException.notFound("Warehouse task line not found: " + taskLineId);
        }
        return line;
    }

    private void assertTaskLineBelongsToTask(WarehouseTaskLine line, UUID taskId) {
        if (taskId == null) {
            return;
        }
        WarehouseTask task = line.getTask();
        if (task == null || !Objects.equals(task.getId(), taskId)) {
            throw RestException.badRequest("Warehouse task line does not belong to task: " + taskId);
        }
    }

    private UUID resolveExpectedId(String expectedType, UUID requestedExpectedId, WarehouseTaskLine taskLine) {
        UUID lineExpectedId = taskLine == null ? null : expectedIdFromTaskLine(expectedType, taskLine);
        if (requestedExpectedId != null && lineExpectedId != null && !Objects.equals(requestedExpectedId, lineExpectedId)) {
            throw RestException.badRequest("Expected scan target does not match warehouse task line");
        }
        return requestedExpectedId == null ? lineExpectedId : requestedExpectedId;
    }

    private UUID expectedIdFromTaskLine(String expectedType, WarehouseTaskLine line) {
        return switch (expectedType) {
            case TYPE_BIN -> expectedBinId(line);
            case TYPE_SPARE_PART -> line.getSparePartId();
            case TYPE_EQUIPMENT -> line.getEquipmentId();
            default -> throw RestException.badRequest("Unsupported expectedType: " + expectedType);
        };
    }

    private UUID expectedBinId(WarehouseTaskLine line) {
        WarehouseTask task = line.getTask();
        WarehouseTaskType taskType = task == null ? null : task.getTaskType();
        if (taskType == WarehouseTaskType.RECEIVE || taskType == WarehouseTaskType.PUTAWAY) {
            return line.getToBinId() == null ? line.getFromBinId() : line.getToBinId();
        }
        return line.getFromBinId() == null ? line.getToBinId() : line.getFromBinId();
    }

    private String mismatchMessage(String expectedType) {
        return switch (expectedType) {
            case TYPE_BIN -> "Scanned bin does not match expected bin";
            case TYPE_SPARE_PART -> "Scanned spare part does not match expected spare part";
            case TYPE_EQUIPMENT -> "Scanned equipment does not match expected equipment";
            default -> "Scanned object does not match expected object";
        };
    }

    private String normalizeType(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        String type = normalized.toUpperCase(Locale.ROOT);
        if (!TYPE_BIN.equals(type) && !TYPE_SPARE_PART.equals(type) && !TYPE_EQUIPMENT.equals(type)) {
            throw RestException.badRequest("Unsupported WMS label type: " + value);
        }
        return type;
    }

    private UUID parseUuid(String value, String message) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        try {
            return UUID.fromString(normalized);
        } catch (IllegalArgumentException ex) {
            throw RestException.badRequest(message);
        }
    }

    private String requireText(String value, String message) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw RestException.badRequest(message);
        }
        return normalized;
    }

    private boolean equalsIgnoreCase(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record ParsedScan(String type, UUID id, Map<String, String> values) {
    }
}
