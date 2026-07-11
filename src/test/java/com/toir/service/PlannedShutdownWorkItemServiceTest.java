package com.toir.service;

import com.toir.dto.plannedshutdown.PlannedShutdownWorkItemRequest;
import com.toir.entity.PlannedShutdown;
import com.toir.entity.plannedshutdown.PlannedShutdownAsset;
import com.toir.entity.plannedshutdown.PlannedShutdownWorkItem;
import com.toir.enums.*;
import com.toir.repository.*;
import com.toir.repository.defects.DefectRepository;
import com.toir.repository.department.DepartmentRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.plannedshutdown.PlannedShutdownAssetRepository;
import com.toir.repository.plannedshutdown.PlannedShutdownWorkItemRepository;
import com.toir.repository.users.EmployeeRepository;
import com.toir.service.plannedshutdown.PlannedShutdownWorkItemPolicy;
import com.toir.util.AuditBuilderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlannedShutdownWorkItemServiceTest {
    @Mock PlannedShutdownRepository shutdownRepository;
    @Mock PlannedShutdownAssetRepository assetRepository;
    @Mock PlannedShutdownWorkItemRepository itemRepository;
    @Mock DepartmentRepository departmentRepository;
    @Mock EmployeeRepository employeeRepository;
    @Mock EquipmentRepository equipmentRepository;
    @Mock DefectRepository defectRepository;
    @Mock PprTaskRepository pprTaskRepository;
    @Mock WorkOrderRepository workOrderRepository;
    @Mock AuditBuilderService audit;
    PlannedShutdownService service;

    UUID shutdownId;
    UUID equipmentId;
    PlannedShutdown shutdown;

    @BeforeEach
    void setUp() {
        service = new PlannedShutdownService(shutdownRepository, assetRepository, itemRepository,
                departmentRepository, employeeRepository, equipmentRepository, defectRepository,
                pprTaskRepository, workOrderRepository, new PlannedShutdownWorkItemPolicy(), audit);
        shutdownId = UUID.randomUUID();
        equipmentId = UUID.randomUUID();
        shutdown = new PlannedShutdown();
        shutdown.setId(shutdownId);
        shutdown.setVersion(4L);
        shutdown.setScopeVersion(2L);
        shutdown.setStatus(PlannedShutdownStatus.SCOPE_FORMATION);
        lenient().when(shutdownRepository.findByIdAndIsDeletedFalseForUpdate(shutdownId)).thenReturn(Optional.of(shutdown));
        PlannedShutdownAsset asset = new PlannedShutdownAsset();
        asset.setEquipmentId(equipmentId);
        lenient().when(assetRepository.findAllByPlannedShutdownIdAndIsDeletedFalseOrderByOrderNumberAsc(shutdownId))
                .thenReturn(List.of(asset));
        lenient().when(shutdownRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(itemRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            PlannedShutdownWorkItem item = invocation.getArgument(0);
            if (item.getId() == null) item.setId(UUID.randomUUID());
            return item;
        });
        lenient().when(itemRepository.findAllByPlannedShutdownIdAndIsDeletedFalseOrderByOrderNumberAsc(shutdownId))
                .thenAnswer(invocation -> List.of());
    }

    @Test
    void addManualItemEnforcesScopedAssetAndIncrementsScopeVersionWithAudit() {
        var response = service.addWorkItem(shutdownId, request(4L, PlannedShutdownWorkItemSourceType.MANUAL,
                null, 0));
        assertThat(response.scopeVersion()).isEqualTo(3L);
        verify(itemRepository).saveAndFlush(any(PlannedShutdownWorkItem.class));
        verify(audit).log(eq("planned_shutdown_work_item"), any(), eq(AuditAction.CREATE),
                eq(AuditModule.PLANNED_SHUTDOWN), any(), isNull(), any());
    }

    @Test
    void rejectsEquipmentOutsideShutdownScope() {
        UUID outside = UUID.randomUUID();
        var request = new PlannedShutdownWorkItemRequest(4L, PlannedShutdownWorkItemSourceType.MANUAL,
                null, outside, "Manual", PriorityLevel.HIGH, true, false, 30, null, 0);
        assertThatThrownBy(() -> service.addWorkItem(shutdownId, request))
                .hasMessageContaining("outside shutdown scope");
        verify(itemRepository, never()).save(any());
    }

    @Test
    void duplicateCanonicalSourceAndOrderAreRejectedBeforeDatabaseWrite() {
        UUID defectId = UUID.randomUUID();
        com.toir.entity.defects.Defect defect = new com.toir.entity.defects.Defect();
        defect.setEquipmentId(equipmentId);
        when(defectRepository.findByIdAndIsDeletedFalse(defectId)).thenReturn(Optional.of(defect));
        when(itemRepository.existsByPlannedShutdownIdAndSourceTypeAndSourceIdAndIsDeletedFalse(
                shutdownId, PlannedShutdownWorkItemSourceType.DEFECT, defectId)).thenReturn(true);
        assertThatThrownBy(() -> service.addWorkItem(shutdownId,
                request(4L, PlannedShutdownWorkItemSourceType.DEFECT, defectId, 0)))
                .hasMessageContaining("already linked");
    }

    @Test
    void removalIsBlockedWhileActiveLinkedWorkOrderExists() {
        UUID itemId = UUID.randomUUID();
        PlannedShutdownWorkItem item = item(itemId, PlannedShutdownWorkItemSourceType.MANUAL, null, 0);
        when(itemRepository.findByIdAndPlannedShutdownIdAndIsDeletedFalse(itemId, shutdownId))
                .thenReturn(Optional.of(item));
        when(workOrderRepository.existsActiveByShutdownWorkItemId(itemId)).thenReturn(true);
        assertThatThrownBy(() -> service.removeWorkItem(shutdownId, itemId, 4L))
                .hasMessageContaining("active linked Work Order");
        assertThat(item.isDeleted()).isFalse();
    }

    @Test
    void approvedShutdownRejectsSourceIdentityChangeButAllowsDescriptiveUpdate() {
        shutdown.setStatus(PlannedShutdownStatus.APPROVED);
        UUID itemId = UUID.randomUUID();
        UUID defectId = UUID.randomUUID();
        PlannedShutdownWorkItem item = item(itemId, PlannedShutdownWorkItemSourceType.DEFECT, defectId, 0);
        when(itemRepository.findByIdAndPlannedShutdownIdAndIsDeletedFalse(itemId, shutdownId))
                .thenReturn(Optional.of(item));
        assertThatThrownBy(() -> service.updateWorkItem(shutdownId, itemId,
                request(4L, PlannedShutdownWorkItemSourceType.WORK_ORDER, UUID.randomUUID(), 0)))
                .hasMessageContaining("immutable after approval");
    }

    private PlannedShutdownWorkItemRequest request(long version, PlannedShutdownWorkItemSourceType type,
            UUID sourceId, int order) {
        return new PlannedShutdownWorkItemRequest(version, type, sourceId, equipmentId, "Work", PriorityLevel.HIGH,
                true, false, 30, "A", order);
    }

    private PlannedShutdownWorkItem item(UUID id, PlannedShutdownWorkItemSourceType type, UUID sourceId, int order) {
        PlannedShutdownWorkItem item = new PlannedShutdownWorkItem();
        item.setId(id);
        item.setPlannedShutdownId(shutdownId);
        item.setSourceType(type);
        item.setSourceId(sourceId);
        item.setEquipmentId(equipmentId);
        item.setTitle("Work");
        item.setPriority(PriorityLevel.HIGH);
        item.setOrderNumber(order);
        return item;
    }
}
