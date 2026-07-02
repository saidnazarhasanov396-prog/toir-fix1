package com.toir.service.warehouse;

import com.toir.dto.warehouse.WarehouseTaskDto;
import com.toir.entity.PurchaseOrder;
import com.toir.entity.SparePart;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.projects.ProcurementRequest;
import com.toir.entity.repair.RepairRequest;
import com.toir.entity.users.User;
import com.toir.entity.warehouse.Warehouse;
import com.toir.entity.warehouse.WarehouseBin;
import com.toir.entity.warehouse.WarehouseTask;
import com.toir.entity.warehouse.WarehouseTaskLine;
import com.toir.enums.WarehouseStockStatus;
import com.toir.enums.WarehouseTaskLineStatus;
import com.toir.enums.WarehouseTaskPriority;
import com.toir.enums.WarehouseTaskSourceType;
import com.toir.enums.WarehouseTaskStatus;
import com.toir.enums.WarehouseTaskType;
import com.toir.repository.ProcurementRequestRepository;
import com.toir.repository.PurchaseOrderRepository;
import com.toir.repository.SparePartRepository;
import com.toir.repository.WarehouseBinRepository;
import com.toir.repository.WarehouseRepository;
import com.toir.repository.WarehouseTaskRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.repair.RepairRequestRepository;
import com.toir.repository.users.UserRepository;
import com.toir.util.AuditBuilderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WarehouseTaskServiceEnrichmentTest {

    @Mock
    WarehouseTaskRepository taskRepository;
    @Mock
    WarehouseStockMoveService stockMoveService;
    @Mock
    AuditBuilderService auditBuilderService;
    @Mock
    WarehouseBinRepository warehouseBinRepository;
    @Mock
    SparePartRepository sparePartRepository;
    @Mock
    EquipmentRepository equipmentRepository;
    @Mock
    WarehouseRepository warehouseRepository;
    @Mock
    UserRepository userRepository;
    @Mock
    WorkOrderRepository workOrderRepository;
    @Mock
    RepairRequestRepository repairRequestRepository;
    @Mock
    ProcurementRequestRepository procurementRequestRepository;
    @Mock
    PurchaseOrderRepository purchaseOrderRepository;
    @Mock
    WmsStockCoordinateValidator coordinateValidator;

    @InjectMocks
    WarehouseTaskService service;

    private UUID warehouseId;
    private UUID assigneeId;
    private UUID sourceId;
    private UUID fromBinId;
    private UUID toBinId;
    private UUID sparePartId;
    private UUID equipmentId;

    @BeforeEach
    void setUp() {
        warehouseId = UUID.randomUUID();
        assigneeId = UUID.randomUUID();
        sourceId = UUID.randomUUID();
        fromBinId = UUID.randomUUID();
        toBinId = UUID.randomUUID();
        sparePartId = UUID.randomUUID();
        equipmentId = UUID.randomUUID();
    }

    @Test
    void findAllEnrichesTaskAndLineNames() {
        UUID taskId = UUID.randomUUID();
        UUID lineId = UUID.randomUUID();
        WarehouseTask task = task(taskId, lineId, WarehouseTaskSourceType.WORK_ORDER);

        Warehouse warehouse = new Warehouse();
        warehouse.setName("Main Warehouse");

        User assignee = new User();
        assignee.setFullName("Ali Valiyev");

        WorkOrder workOrder = new WorkOrder();
        workOrder.setNumber("WO-2026-00042");

        WarehouseBin fromBin = new WarehouseBin();
        fromBin.setId(fromBinId);
        fromBin.setCode("A-01");

        WarehouseBin toBin = new WarehouseBin();
        toBin.setId(toBinId);
        toBin.setCode("B-02");

        SparePart sparePart = new SparePart();
        sparePart.setId(sparePartId);
        sparePart.setCode("SP-100");
        sparePart.setName("Bearing");

        Equipment equipment = new Equipment();
        equipment.setId(equipmentId);
        equipment.setCode("EQ-55");
        equipment.setName("Pump");

        when(taskRepository.search(any(), any(), any(), any(), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(task)));
        when(warehouseRepository.findByIdAndIsDeletedFalse(warehouseId)).thenReturn(Optional.of(warehouse));
        when(userRepository.findByIdAndIsDeletedFalse(assigneeId)).thenReturn(Optional.of(assignee));
        when(workOrderRepository.findByIdAndIsDeletedFalse(sourceId)).thenReturn(Optional.of(workOrder));
        when(warehouseBinRepository.findAllByIdInAndIsDeletedFalse(any())).thenReturn(List.of(fromBin, toBin));
        when(sparePartRepository.findAllByIdInAndIsDeletedFalse(any())).thenReturn(List.of(sparePart));
        when(equipmentRepository.findAllByIdInAndIsDeletedFalse(any())).thenReturn(List.of(equipment));

        WarehouseTaskDto dto = service.findAll(null, null, null, null, 0, 20).getContent().getFirst();

        verify(warehouseBinRepository, times(1)).findAllByIdInAndIsDeletedFalse(any());
        verify(sparePartRepository, times(1)).findAllByIdInAndIsDeletedFalse(any());
        verify(equipmentRepository, times(1)).findAllByIdInAndIsDeletedFalse(any());
        verify(warehouseBinRepository, never()).findByIdAndIsDeletedFalse(any());
        verify(sparePartRepository, never()).findByIdAndIsDeletedFalse(any());
        verify(equipmentRepository, never()).findByIdAndIsDeletedFalse(any());

        assertThat(dto.warehouseName()).isEqualTo("Main Warehouse");
        assertThat(dto.assignedToName()).isEqualTo("Ali Valiyev");
        assertThat(dto.sourceNumber()).isEqualTo("WO-2026-00042");
        assertThat(dto.lines()).hasSize(1);
        assertThat(dto.lines().getFirst().fromBinCode()).isEqualTo("A-01");
        assertThat(dto.lines().getFirst().toBinCode()).isEqualTo("B-02");
        assertThat(dto.lines().getFirst().sparePartCode()).isEqualTo("SP-100");
        assertThat(dto.lines().getFirst().sparePartName()).isEqualTo("Bearing");
        assertThat(dto.lines().getFirst().equipmentCode()).isEqualTo("EQ-55");
        assertThat(dto.lines().getFirst().equipmentName()).isEqualTo("Pump");
    }

    @Test
    void findAllEnrichesMultipleLinesWithSingleBatchQueryPerEntityType() {
        UUID taskId = UUID.randomUUID();
        UUID sharedSparePartId = UUID.randomUUID();
        UUID sharedFromBinId = UUID.randomUUID();

        WarehouseTask task = new WarehouseTask();
        task.setId(taskId);
        task.setTaskNumber("WT-2026-00002");
        task.setTaskType(WarehouseTaskType.PICK);
        task.setStatus(WarehouseTaskStatus.OPEN);
        task.setPriority(WarehouseTaskPriority.NORMAL);
        task.setWarehouseId(warehouseId);
        task.setSourceType(WarehouseTaskSourceType.MANUAL);
        task.setCreatedAt(Instant.now());
        task.setUpdatedAt(Instant.now());

        WarehouseTaskLine lineOne = line(UUID.randomUUID(), sharedSparePartId, equipmentId, sharedFromBinId, toBinId);
        WarehouseTaskLine lineTwo = line(UUID.randomUUID(), sharedSparePartId, null, sharedFromBinId, null);
        lineOne.setTask(task);
        lineTwo.setTask(task);
        task.getLines().add(lineOne);
        task.getLines().add(lineTwo);

        SparePart sparePart = new SparePart();
        sparePart.setId(sharedSparePartId);
        sparePart.setCode("SP-200");
        sparePart.setName("Filter");

        WarehouseBin fromBin = new WarehouseBin();
        fromBin.setId(sharedFromBinId);
        fromBin.setCode("C-03");

        WarehouseBin toBin = new WarehouseBin();
        toBin.setId(toBinId);
        toBin.setCode("D-04");

        Equipment equipment = new Equipment();
        equipment.setId(equipmentId);
        equipment.setCode("EQ-77");
        equipment.setName("Motor");

        when(taskRepository.search(any(), any(), any(), any(), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(task)));
        when(warehouseRepository.findByIdAndIsDeletedFalse(warehouseId)).thenReturn(Optional.empty());
        when(warehouseBinRepository.findAllByIdInAndIsDeletedFalse(any())).thenReturn(List.of(fromBin, toBin));
        when(sparePartRepository.findAllByIdInAndIsDeletedFalse(any())).thenReturn(List.of(sparePart));
        when(equipmentRepository.findAllByIdInAndIsDeletedFalse(any())).thenReturn(List.of(equipment));

        WarehouseTaskDto dto = service.findAll(null, null, null, null, 0, 20).getContent().getFirst();

        assertThat(dto.lines()).hasSize(2);
        assertThat(dto.lines().get(0).sparePartCode()).isEqualTo("SP-200");
        assertThat(dto.lines().get(1).fromBinCode()).isEqualTo("C-03");
        verify(warehouseBinRepository, times(1)).findAllByIdInAndIsDeletedFalse(any());
        verify(sparePartRepository, times(1)).findAllByIdInAndIsDeletedFalse(any());
        verify(equipmentRepository, times(1)).findAllByIdInAndIsDeletedFalse(any());
    }

    @Test
    void enrichmentReturnsNullWhenEntityMissingOrIdsNull() {
        UUID taskId = UUID.randomUUID();
        UUID lineId = UUID.randomUUID();
        WarehouseTask task = task(taskId, lineId, WarehouseTaskSourceType.MANUAL);
        task.setWarehouseId(null);
        task.setAssignedToId(null);
        task.setSourceId(null);
        task.getLines().getFirst().setFromBinId(null);
        task.getLines().getFirst().setToBinId(null);
        task.getLines().getFirst().setSparePartId(null);
        task.getLines().getFirst().setEquipmentId(null);

        when(taskRepository.search(any(), any(), any(), any(), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(task)));

        WarehouseTaskDto dto = service.findAll(null, null, null, null, 0, 20).getContent().getFirst();

        assertThat(dto.warehouseName()).isNull();
        assertThat(dto.assignedToName()).isNull();
        assertThat(dto.sourceNumber()).isNull();
        assertThat(dto.lines().getFirst().fromBinCode()).isNull();
        assertThat(dto.lines().getFirst().toBinCode()).isNull();
        assertThat(dto.lines().getFirst().sparePartCode()).isNull();
        assertThat(dto.lines().getFirst().sparePartName()).isNull();
        assertThat(dto.lines().getFirst().equipmentCode()).isNull();
        assertThat(dto.lines().getFirst().equipmentName()).isNull();
    }

    @Test
    void resolveSourceNumberForEachSupportedSourceType() {
        when(repairRequestRepository.findByIdAndIsDeletedFalse(sourceId))
                .thenReturn(Optional.of(repairRequest("RR-1")));
        when(procurementRequestRepository.findByIdAndIsDeletedFalse(sourceId))
                .thenReturn(Optional.of(procurementRequest("PR-1")));
        when(purchaseOrderRepository.findByIdAndIsDeletedFalse(sourceId))
                .thenReturn(Optional.of(purchaseOrder("PO-1")));

        assertThat(enrichedSourceNumber(WarehouseTaskSourceType.REPAIR_REQUEST)).isEqualTo("RR-1");
        assertThat(enrichedSourceNumber(WarehouseTaskSourceType.PROCUREMENT_REQUEST)).isEqualTo("PR-1");
        assertThat(enrichedSourceNumber(WarehouseTaskSourceType.PURCHASE_ORDER)).isEqualTo("PO-1");
        assertThat(enrichedSourceNumber(WarehouseTaskSourceType.INVENTORY_COUNT_SESSION)).isNull();
    }

    private String enrichedSourceNumber(WarehouseTaskSourceType sourceType) {
        UUID taskId = UUID.randomUUID();
        WarehouseTask task = task(taskId, UUID.randomUUID(), sourceType);
        when(taskRepository.search(any(), any(), any(), any(), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(task)));
        return service.findAll(null, null, null, null, 0, 20).getContent().getFirst().sourceNumber();
    }

    private WarehouseTask task(UUID taskId, UUID lineId, WarehouseTaskSourceType sourceType) {
        WarehouseTask task = new WarehouseTask();
        task.setId(taskId);
        task.setTaskNumber("WT-2026-00001");
        task.setTaskType(WarehouseTaskType.PICK);
        task.setStatus(WarehouseTaskStatus.OPEN);
        task.setPriority(WarehouseTaskPriority.NORMAL);
        task.setWarehouseId(warehouseId);
        task.setSourceType(sourceType);
        task.setSourceId(sourceId);
        task.setAssignedToId(assigneeId);
        task.setCreatedAt(Instant.now());
        task.setUpdatedAt(Instant.now());

        WarehouseTaskLine line = new WarehouseTaskLine();
        line.setId(lineId);
        line.setTask(task);
        line.setSparePartId(sparePartId);
        line.setEquipmentId(equipmentId);
        line.setFromBinId(fromBinId);
        line.setToBinId(toBinId);
        line.setStockStatus(WarehouseStockStatus.AVAILABLE);
        line.setPlannedQty(BigDecimal.ONE);
        line.setActualQty(BigDecimal.ZERO);
        line.setStatus(WarehouseTaskLineStatus.OPEN);
        task.getLines().add(line);
        return task;
    }

    private WarehouseTaskLine line(UUID lineId,
                                   UUID sparePartIdValue,
                                   UUID equipmentIdValue,
                                   UUID fromBinIdValue,
                                   UUID toBinIdValue) {
        WarehouseTaskLine line = new WarehouseTaskLine();
        line.setId(lineId);
        line.setSparePartId(sparePartIdValue);
        line.setEquipmentId(equipmentIdValue);
        line.setFromBinId(fromBinIdValue);
        line.setToBinId(toBinIdValue);
        line.setStockStatus(WarehouseStockStatus.AVAILABLE);
        line.setPlannedQty(BigDecimal.ONE);
        line.setActualQty(BigDecimal.ZERO);
        line.setStatus(WarehouseTaskLineStatus.OPEN);
        return line;
    }

    private RepairRequest repairRequest(String number) {
        RepairRequest request = new RepairRequest();
        request.setNumber(number);
        return request;
    }

    private ProcurementRequest procurementRequest(String number) {
        ProcurementRequest request = new ProcurementRequest();
        request.setNumber(number);
        return request;
    }

    private PurchaseOrder purchaseOrder(String number) {
        PurchaseOrder order = new PurchaseOrder();
        order.setNumber(number);
        return order;
    }
}
