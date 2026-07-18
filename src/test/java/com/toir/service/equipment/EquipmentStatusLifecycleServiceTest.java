package com.toir.service.equipment;

import com.toir.dto.equipment.EquipmentStatusChangeRequest;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.equipment.EquipmentStatusHistory;
import com.toir.enums.EquipmentStatus;
import com.toir.enums.EquipmentStatusSource;
import com.toir.exception.RestException;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.equipment.EquipmentStatusHistoryRepository;
import com.toir.service.integration.ErpEquipmentStatusOutboxService;
import com.toir.service.integration.AtilEquipmentStatusOutboxService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EquipmentStatusLifecycleServiceTest {

    @Mock
    EquipmentRepository equipmentRepository;

    @Mock
    EquipmentStatusHistoryRepository historyRepository;

    @Mock
    ErpEquipmentStatusOutboxService erpEquipmentStatusOutboxService;

    @Mock
    AtilEquipmentStatusOutboxService atilEquipmentStatusOutboxService;

    @InjectMocks
    EquipmentStatusLifecycleService service;

    @Test
    void manualStatusChange_requiresReasonAndWritesHistory() {
        UUID equipmentId = UUID.randomUUID();
        UUID changedBy = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, EquipmentStatus.ACTIVE);
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(equipmentRepository.save(any(Equipment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(historyRepository.save(any(EquipmentStatusHistory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.changeStatusManually(
                equipmentId,
                new EquipmentStatusChangeRequest(EquipmentStatus.OUT_OF_SERVICE, "Safety lockout"),
                changedBy);

        assertThat(equipment.getStatus()).isEqualTo(EquipmentStatus.OUT_OF_SERVICE);
        ArgumentCaptor<EquipmentStatusHistory> historyCaptor = ArgumentCaptor.forClass(EquipmentStatusHistory.class);
        verify(historyRepository).save(historyCaptor.capture());
        EquipmentStatusHistory history = historyCaptor.getValue();
        assertThat(history.getEquipmentId()).isEqualTo(equipmentId);
        assertThat(history.getFromStatus()).isEqualTo(EquipmentStatus.ACTIVE);
        assertThat(history.getToStatus()).isEqualTo(EquipmentStatus.OUT_OF_SERVICE);
        assertThat(history.getReason()).isEqualTo("Safety lockout");
        assertThat(history.getSource()).isEqualTo(EquipmentStatusSource.MANUAL);
        assertThat(history.getChangedBy()).isEqualTo(changedBy);
        assertThat(history.getChangedAt()).isNotNull();
        verify(erpEquipmentStatusOutboxService).queue(equipment, history);
        verify(atilEquipmentStatusOutboxService).queue(equipment, history);
    }

    @Test
    void manualStatusChange_requiresReason() {
        UUID equipmentId = UUID.randomUUID();

        assertThatThrownBy(() -> service.changeStatusManually(
                equipmentId,
                new EquipmentStatusChangeRequest(EquipmentStatus.OUT_OF_SERVICE, " "),
                UUID.randomUUID()))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("reason");
                });

        verify(equipmentRepository, never()).save(any(Equipment.class));
        verify(historyRepository, never()).save(any(EquipmentStatusHistory.class));
    }

    @Test
    void manualStatusChange_updatesEquipmentStatus() {
        UUID equipmentId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, EquipmentStatus.ACTIVE);
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(equipmentRepository.save(any(Equipment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(historyRepository.save(any(EquipmentStatusHistory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.changeStatusManually(
                equipmentId,
                new EquipmentStatusChangeRequest(EquipmentStatus.OUT_OF_SERVICE, "Safety shutdown"),
                UUID.randomUUID());

        ArgumentCaptor<Equipment> equipmentCaptor = ArgumentCaptor.forClass(Equipment.class);
        verify(equipmentRepository).save(equipmentCaptor.capture());
        assertThat(equipmentCaptor.getValue().getStatus()).isEqualTo(EquipmentStatus.OUT_OF_SERVICE);
    }

    @Test
    void manualStandbyToActive_requiresApprovedCommissioningAct() {
        UUID equipmentId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, EquipmentStatus.STANDBY);
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));

        assertThatThrownBy(() -> service.changeStatusManually(
                equipmentId,
                new EquipmentStatusChangeRequest(EquipmentStatus.ACTIVE, "Manual activation"),
                UUID.randomUUID()))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("equipment commissioning approval");

        assertThat(equipment.getStatus()).isEqualTo(EquipmentStatus.STANDBY);
        verify(equipmentRepository, never()).save(any());
        verify(historyRepository, never()).save(any());
    }

    @Test
    void decommissionedEquipment_rejectsNewWorkOrder() {
        assertDecommissionedRejected("create work order");
    }

    @Test
    void decommissionedEquipment_rejectsNewDefect() {
        assertDecommissionedRejected("create defect");
    }

    @Test
    void decommissionedEquipment_rejectsNewRepairRequest() {
        assertDecommissionedRejected("create repair request");
    }

    @Test
    void decommissionedEquipment_rejectsNewMeterReading() {
        assertDecommissionedRejected("add meter reading");
    }

    @Test
    void completeWorkOrder_doesNotOverwriteManualOutOfService() {
        UUID equipmentId = UUID.randomUUID();
        UUID workOrderId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, EquipmentStatus.OUT_OF_SERVICE);
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));

        service.recordWorkOrderReturn(workOrderId, equipmentId, "Work order closed");

        assertThat(equipment.getStatus()).isEqualTo(EquipmentStatus.OUT_OF_SERVICE);
        verify(equipmentRepository, never()).save(any(Equipment.class));
        verify(historyRepository, never()).save(any(EquipmentStatusHistory.class));
    }

    @Test
    void workOrderTransition_writesHistoryWithRelatedEntity() {
        UUID equipmentId = UUID.randomUUID();
        UUID workOrderId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, EquipmentStatus.ACTIVE);
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(equipmentRepository.save(any(Equipment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(historyRepository.save(any(EquipmentStatusHistory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.recordWorkOrderTransition(workOrderId, equipmentId, EquipmentStatus.IN_REPAIR, "Work order started");

        assertThat(equipment.getStatus()).isEqualTo(EquipmentStatus.IN_REPAIR);
        ArgumentCaptor<EquipmentStatusHistory> historyCaptor = ArgumentCaptor.forClass(EquipmentStatusHistory.class);
        verify(historyRepository).save(historyCaptor.capture());
        EquipmentStatusHistory history = historyCaptor.getValue();
        assertThat(history.getSource()).isEqualTo(EquipmentStatusSource.WORK_ORDER);
        assertThat(history.getRelatedEntityType()).isEqualTo("WORK_ORDER");
        assertThat(history.getRelatedEntityId()).isEqualTo(workOrderId);
    }

    private void assertDecommissionedRejected(String action) {
        UUID equipmentId = UUID.randomUUID();
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId))
                .thenReturn(Optional.of(equipment(equipmentId, EquipmentStatus.DECOMMISSIONED)));

        assertThatThrownBy(() -> service.assertOperationallyAllowed(equipmentId, action))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getMessage()).contains("decommissioned");
                    assertThat(ex.getMessage()).contains(action);
                });
    }

    private Equipment equipment(UUID id, EquipmentStatus status) {
        Equipment equipment = new Equipment();
        equipment.setId(id);
        equipment.setCode("EQ-2026-0001");
        equipment.setName("Pump");
        equipment.setInventoryNumber("INV-1");
        equipment.setEquipmentTypeId(UUID.randomUUID());
        equipment.setStatus(status);
        return equipment;
    }
}
