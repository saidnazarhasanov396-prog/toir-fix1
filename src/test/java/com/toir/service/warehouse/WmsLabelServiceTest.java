package com.toir.service.warehouse;

import com.toir.dto.warehouse.WmsScanValidationRequest;
import com.toir.entity.SparePart;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.warehouse.WarehouseBin;
import com.toir.entity.warehouse.WarehouseTask;
import com.toir.entity.warehouse.WarehouseTaskLine;
import com.toir.enums.WarehouseTaskType;
import com.toir.exception.RestException;
import com.toir.repository.SparePartRepository;
import com.toir.repository.WarehouseBinRepository;
import com.toir.repository.WarehouseTaskLineRepository;
import com.toir.repository.equipment.EquipmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WmsLabelServiceTest {

    @Mock
    WarehouseBinRepository binRepository;

    @Mock
    SparePartRepository sparePartRepository;

    @Mock
    EquipmentRepository equipmentRepository;

    @Mock
    WarehouseTaskLineRepository taskLineRepository;

    WmsLabelService service;

    @BeforeEach
    void setUp() {
        service = new WmsLabelService(binRepository, sparePartRepository, equipmentRepository, taskLineRepository);
    }

    @Test
    void binLabelPayloadContainsTypeWarehouseBinAndCode() {
        UUID binId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        WarehouseBin bin = new WarehouseBin();
        bin.setId(binId);
        bin.setWarehouseId(warehouseId);
        bin.setCode("A-01-02");
        when(binRepository.findByIdAndIsDeletedFalse(binId)).thenReturn(Optional.of(bin));

        var result = service.binLabel(binId);

        assertThat(result.type()).isEqualTo("BIN");
        assertThat(result.id()).isEqualTo(binId);
        assertThat(result.binId()).isEqualTo(binId);
        assertThat(result.warehouseId()).isEqualTo(warehouseId);
        assertThat(result.code()).isEqualTo("A-01-02");
        assertThat(result.payload()).isEqualTo("TOIR-WMS|type=BIN|id=%s|warehouseId=%s|code=A-01-02"
                .formatted(binId, warehouseId));
    }

    @Test
    void sparePartLabelPayloadContainsTypeSparePartAndCode() {
        UUID sparePartId = UUID.randomUUID();
        SparePart sparePart = new SparePart();
        sparePart.setId(sparePartId);
        sparePart.setCode("SP-100");
        when(sparePartRepository.findByIdAndIsDeletedFalse(sparePartId)).thenReturn(Optional.of(sparePart));

        var result = service.sparePartLabel(sparePartId);

        assertThat(result.type()).isEqualTo("SPARE_PART");
        assertThat(result.id()).isEqualTo(sparePartId);
        assertThat(result.sparePartId()).isEqualTo(sparePartId);
        assertThat(result.code()).isEqualTo("SP-100");
        assertThat(result.payload()).isEqualTo("TOIR-WMS|type=SPARE_PART|id=%s|code=SP-100"
                .formatted(sparePartId));
    }

    @Test
    void equipmentLabelPayloadContainsTypeEquipmentAndInventoryNumber() {
        UUID equipmentId = UUID.randomUUID();
        Equipment equipment = new Equipment();
        equipment.setId(equipmentId);
        equipment.setInventoryNumber("INV-7788");
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));

        var result = service.equipmentLabel(equipmentId);

        assertThat(result.type()).isEqualTo("EQUIPMENT");
        assertThat(result.id()).isEqualTo(equipmentId);
        assertThat(result.equipmentId()).isEqualTo(equipmentId);
        assertThat(result.code()).isEqualTo("INV-7788");
        assertThat(result.payload()).isEqualTo("TOIR-WMS|type=EQUIPMENT|id=%s|inventoryNumber=INV-7788"
                .formatted(equipmentId));
    }

    @Test
    void scanValidationRejectsWrongBinForTaskLine() {
        UUID taskId = UUID.randomUUID();
        UUID taskLineId = UUID.randomUUID();
        UUID expectedBinId = UUID.randomUUID();
        UUID wrongBinId = UUID.randomUUID();
        WarehouseTaskLine line = taskLine(taskId, taskLineId, null, expectedBinId, null, null);
        when(taskLineRepository.findById(taskLineId)).thenReturn(Optional.of(line));

        assertThatThrownBy(() -> service.validateScan(new WmsScanValidationRequest(
                "BIN",
                expectedBinId,
                taskId,
                taskLineId,
                "TOIR-WMS|type=BIN|id=%s|warehouseId=%s|code=B-WRONG"
                        .formatted(wrongBinId, UUID.randomUUID()),
                null
        )))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("bin");
    }

    @Test
    void scanValidationAcceptsManualFallbackEqualToBinCodeOrBarcode() {
        UUID taskId = UUID.randomUUID();
        UUID taskLineId = UUID.randomUUID();
        UUID binId = UUID.randomUUID();
        WarehouseTaskLine line = taskLine(taskId, taskLineId, null, binId, null, null);
        WarehouseBin bin = new WarehouseBin();
        bin.setId(binId);
        bin.setCode("A-01-02");
        bin.setBarcode("BC-A-01-02");
        when(taskLineRepository.findById(taskLineId)).thenReturn(Optional.of(line));
        when(binRepository.findByIdAndIsDeletedFalse(binId)).thenReturn(Optional.of(bin));

        var byCode = service.validateScan(new WmsScanValidationRequest(
                "BIN",
                null,
                taskId,
                taskLineId,
                null,
                "A-01-02"
        ));
        var byBarcode = service.validateScan(new WmsScanValidationRequest(
                "BIN",
                null,
                taskId,
                taskLineId,
                null,
                "BC-A-01-02"
        ));

        assertThat(byCode.valid()).isTrue();
        assertThat(byCode.scannedId()).isEqualTo(binId);
        assertThat(byBarcode.valid()).isTrue();
        assertThat(byBarcode.scannedId()).isEqualTo(binId);
    }

    private WarehouseTaskLine taskLine(UUID taskId,
                                       UUID lineId,
                                       UUID fromBinId,
                                       UUID toBinId,
                                       UUID sparePartId,
                                       UUID equipmentId) {
        WarehouseTask task = new WarehouseTask();
        task.setId(taskId);
        task.setTaskType(WarehouseTaskType.PUTAWAY);

        WarehouseTaskLine line = new WarehouseTaskLine();
        line.setId(lineId);
        line.setTask(task);
        line.setFromBinId(fromBinId);
        line.setToBinId(toBinId);
        line.setSparePartId(sparePartId);
        line.setEquipmentId(equipmentId);
        return line;
    }
}
