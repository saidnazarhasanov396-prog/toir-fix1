package com.toir.service;

import com.toir.dto.plannedshutdown.PlannedShutdownWorkItemRequest;
import com.toir.dto.plannedshutdown.PlannedShutdownWorkItemReorderRequest;
import com.toir.dto.plannedshutdown.PlannedShutdownWorkItemAuditSnapshot;
import com.toir.entity.PprTask;
import com.toir.entity.PlannedShutdown;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.plannedshutdown.PlannedShutdownAsset;
import com.toir.entity.plannedshutdown.PlannedShutdownWorkItem;
import com.toir.enums.*;
import com.toir.repository.*;
import com.toir.repository.defects.DefectRepository;
import com.toir.repository.department.DepartmentRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.plannedshutdown.PlannedShutdownAssetRepository;
import com.toir.repository.plannedshutdown.PlannedShutdownWorkItemRepository;
import com.toir.repository.plannedshutdown.PlannedShutdownReadinessItemRepository;
import com.toir.repository.plannedshutdown.PlannedShutdownIsolationPointRepository;
import com.toir.repository.users.EmployeeRepository;
import com.toir.security.ScopeAccessService;
import com.toir.service.plannedshutdown.PlannedShutdownReadinessPolicy;
import com.toir.service.plannedshutdown.PlannedShutdownWorkItemPolicy;
import com.toir.util.AuditBuilderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.ArrayList;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
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
    @Mock WorkOrderMaterialReadinessService materialReadinessService;
    @Mock WorkOrderAssignmentEligibilityService assignmentEligibilityService;
    @Mock SafetyPermitRepository safetyPermitRepository;
    @Mock PlannedShutdownReadinessItemRepository readinessItemRepository;
    @Mock PlannedShutdownIsolationPointRepository isolationPointRepository;
    @Mock ScopeAccessService scopeAccessService;
    @Mock AuditBuilderService audit;
    PlannedShutdownService service;

    UUID shutdownId;
    UUID equipmentId;
    PlannedShutdown shutdown;

    @BeforeEach
    void setUp() {
        service = new PlannedShutdownService(shutdownRepository, assetRepository, itemRepository,
                readinessItemRepository, isolationPointRepository,
                departmentRepository, employeeRepository, equipmentRepository, defectRepository,
                pprTaskRepository, workOrderRepository, materialReadinessService, assignmentEligibilityService,
                safetyPermitRepository, new PlannedShutdownWorkItemPolicy(), new PlannedShutdownReadinessPolicy(),
                new com.toir.service.plannedshutdown.PlannedShutdownReadinessLifecyclePolicy(),
                scopeAccessService, audit);
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
    void duplicateOrderIsRejectedIndependentlyOfSourceIdentity() {
        when(itemRepository.existsByPlannedShutdownIdAndOrderNumberAndIsDeletedFalse(shutdownId, 3))
                .thenReturn(true);
        assertThatThrownBy(() -> service.addWorkItem(shutdownId,
                request(4L, PlannedShutdownWorkItemSourceType.MANUAL, null, 3)))
                .hasMessageContaining("order number is already in use");
    }

    @Test
    void pprSourceMustExistAndMatchScopedEquipment() {
        UUID sourceId = UUID.randomUUID();
        when(pprTaskRepository.findByIdAndIsDeletedFalse(sourceId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.addWorkItem(shutdownId,
                request(4L, PlannedShutdownWorkItemSourceType.PPR, sourceId, 0)))
                .hasMessageContaining("PPR task not found");

        PprTask mismatch = new PprTask();
        mismatch.setEquipmentId(UUID.randomUUID());
        when(pprTaskRepository.findByIdAndIsDeletedFalse(sourceId)).thenReturn(Optional.of(mismatch));
        assertThatThrownBy(() -> service.addWorkItem(shutdownId,
                request(4L, PlannedShutdownWorkItemSourceType.PPR, sourceId, 0)))
                .hasMessageContaining("must match source equipment");

        PprTask matching = new PprTask();
        matching.setEquipmentId(equipmentId);
        when(pprTaskRepository.findByIdAndIsDeletedFalse(sourceId)).thenReturn(Optional.of(matching));
        assertThat(service.addWorkItem(shutdownId,
                request(4L, PlannedShutdownWorkItemSourceType.PPR, sourceId, 0)).scopeVersion()).isEqualTo(3L);
    }

    @Test
    void workOrderSourceMustExistAndMatchScopedEquipment() {
        UUID sourceId = UUID.randomUUID();
        when(workOrderRepository.findByIdAndIsDeletedFalse(sourceId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.addWorkItem(shutdownId,
                request(4L, PlannedShutdownWorkItemSourceType.WORK_ORDER, sourceId, 0)))
                .hasMessageContaining("Work Order not found");

        WorkOrder mismatch = new WorkOrder();
        mismatch.setEquipmentId(UUID.randomUUID());
        when(workOrderRepository.findByIdAndIsDeletedFalse(sourceId)).thenReturn(Optional.of(mismatch));
        assertThatThrownBy(() -> service.addWorkItem(shutdownId,
                request(4L, PlannedShutdownWorkItemSourceType.WORK_ORDER, sourceId, 0)))
                .hasMessageContaining("must match source equipment");

        WorkOrder matching = new WorkOrder();
        matching.setEquipmentId(equipmentId);
        when(workOrderRepository.findByIdAndIsDeletedFalse(sourceId)).thenReturn(Optional.of(matching));
        assertThat(service.addWorkItem(shutdownId,
                request(4L, PlannedShutdownWorkItemSourceType.WORK_ORDER, sourceId, 0)).scopeVersion()).isEqualTo(3L);
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
                .hasMessageContaining("immutable once approval has begun");

        com.toir.entity.defects.Defect defect = new com.toir.entity.defects.Defect();
        defect.setEquipmentId(equipmentId);
        when(defectRepository.findByIdAndIsDeletedFalse(defectId)).thenReturn(Optional.of(defect));
        var descriptive = new PlannedShutdownWorkItemRequest(4L, PlannedShutdownWorkItemSourceType.DEFECT,
                defectId, equipmentId, "Updated description", PriorityLevel.CRITICAL, true, true, 90, "B", 0);
        assertThat(service.updateWorkItem(shutdownId, itemId, descriptive).scopeVersion()).isEqualTo(3L);
        assertThat(item.getTitle()).isEqualTo("Updated description");
    }

    @Test
    void approvalStartBlocksAddRemoveAndReorderButKeepsActiveWorkOrderGuardForMutableScope() {
        shutdown.setStatus(PlannedShutdownStatus.PENDING_APPROVAL);
        UUID itemId = UUID.randomUUID();
        PlannedShutdownWorkItem item = item(itemId, PlannedShutdownWorkItemSourceType.MANUAL, null, 0);
        assertThatThrownBy(() -> service.addWorkItem(shutdownId,
                request(4L, PlannedShutdownWorkItemSourceType.MANUAL, null, 1)))
                .hasMessageContaining("immutable once approval has begun");
        assertThatThrownBy(() -> service.removeWorkItem(shutdownId, itemId, 4L))
                .hasMessageContaining("immutable once approval has begun");
        assertThatThrownBy(() -> service.reorderWorkItems(shutdownId,
                new PlannedShutdownWorkItemReorderRequest(4L, List.of(itemId))))
                .hasMessageContaining("immutable once approval has begun");
        verify(itemRepository, never()).saveAllAndFlush(any());
        verify(workOrderRepository, never()).existsActiveByShutdownWorkItemId(any());
    }

    @Test
    void pendingApprovalAlsoFreezesSourceIdentityDuringUpdate() {
        shutdown.setStatus(PlannedShutdownStatus.PENDING_APPROVAL);
        UUID itemId = UUID.randomUUID();
        PlannedShutdownWorkItem item = item(itemId, PlannedShutdownWorkItemSourceType.MANUAL, null, 0);
        when(itemRepository.findByIdAndPlannedShutdownIdAndIsDeletedFalse(itemId, shutdownId))
                .thenReturn(Optional.of(item));
        assertThatThrownBy(() -> service.updateWorkItem(shutdownId, itemId,
                request(4L, PlannedShutdownWorkItemSourceType.DEFECT, UUID.randomUUID(), 0)))
                .hasMessageContaining("immutable once approval has begun");
    }

    @Test
    void reorderRejectsDuplicateIncompleteAndForeignSets() {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        PlannedShutdownWorkItem first = item(firstId, PlannedShutdownWorkItemSourceType.MANUAL, null, 0);
        PlannedShutdownWorkItem second = item(secondId, PlannedShutdownWorkItemSourceType.MANUAL, null, 1);
        when(itemRepository.findAllByPlannedShutdownIdAndIsDeletedFalseOrderByOrderNumberAsc(shutdownId))
                .thenReturn(List.of(first, second));

        assertThatThrownBy(() -> service.reorderWorkItems(shutdownId,
                new PlannedShutdownWorkItemReorderRequest(4L, List.of(firstId, firstId))))
                .hasMessageContaining("exactly once");
        assertThatThrownBy(() -> service.reorderWorkItems(shutdownId,
                new PlannedShutdownWorkItemReorderRequest(4L, List.of(firstId))))
                .hasMessageContaining("exactly once");
        assertThatThrownBy(() -> service.reorderWorkItems(shutdownId,
                new PlannedShutdownWorkItemReorderRequest(4L, List.of(firstId, UUID.randomUUID()))))
                .hasMessageContaining("outside this shutdown");
        verify(itemRepository, never()).saveAllAndFlush(any());
    }

    @Test
    void reorderUsesTwoFlushesAndAuditsImmutableBeforeAfterOrderedSnapshots() {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        PlannedShutdownWorkItem first = item(firstId, PlannedShutdownWorkItemSourceType.MANUAL, null, 0);
        first.setTitle("First");
        PlannedShutdownWorkItem second = item(secondId, PlannedShutdownWorkItemSourceType.MANUAL, null, 1);
        second.setTitle("Second");
        when(itemRepository.findAllByPlannedShutdownIdAndIsDeletedFalseOrderByOrderNumberAsc(shutdownId))
                .thenReturn(List.of(first, second))
                .thenAnswer(invocation -> List.of(second, first));
        List<List<Integer>> flushedOrders = new ArrayList<>();
        when(itemRepository.saveAllAndFlush(any())).thenAnswer(invocation -> {
            List<PlannedShutdownWorkItem> saved = invocation.getArgument(0);
            flushedOrders.add(saved.stream().map(PlannedShutdownWorkItem::getOrderNumber).toList());
            return saved;
        });

        service.reorderWorkItems(shutdownId,
                new PlannedShutdownWorkItemReorderRequest(4L, List.of(secondId, firstId)));

        assertThat(flushedOrders).hasSize(2);
        assertThat(flushedOrders.get(0)).allMatch(order -> order > 1);
        assertThat(flushedOrders.get(1)).containsExactly(1, 0);
        var beforeCaptor = org.mockito.ArgumentCaptor.forClass(Object.class);
        var afterCaptor = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(audit).log(eq("planned_shutdown_work_items"), eq(shutdownId.toString()), eq(AuditAction.UPDATE),
                eq(AuditModule.PLANNED_SHUTDOWN), any(), beforeCaptor.capture(), afterCaptor.capture());
        var before = (PlannedShutdownWorkItemAuditSnapshot) beforeCaptor.getValue();
        var after = (PlannedShutdownWorkItemAuditSnapshot) afterCaptor.getValue();
        assertThat(before.scopeVersion()).isEqualTo(2L);
        assertThat(before.workItems()).extracting("title", "orderNumber")
                .containsExactly(tuple("First", 0), tuple("Second", 1));
        assertThat(after.scopeVersion()).isEqualTo(3L);
        assertThat(after.workItems()).extracting("title", "orderNumber")
                .containsExactly(tuple("Second", 0), tuple("First", 1));
    }

    @Test
    void uniqueConstraintRacesMapToConflictWithoutMaskingUnrelatedIntegrityErrors() {
        DataIntegrityViolationException sourceRace = new DataIntegrityViolationException("write failed",
                new org.hibernate.exception.ConstraintViolationException("duplicate", new SQLException(),
                        "uq_planned_shutdown_work_items_active_source"));
        doThrow(sourceRace).when(itemRepository).saveAndFlush(any());
        assertThatThrownBy(() -> service.addWorkItem(shutdownId,
                request(4L, PlannedShutdownWorkItemSourceType.MANUAL, null, 0)))
                .isInstanceOfSatisfying(com.toir.exception.RestException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT));

        DataIntegrityViolationException orderRace = new DataIntegrityViolationException("write failed",
                new org.hibernate.exception.ConstraintViolationException("duplicate", new SQLException(),
                        "uq_planned_shutdown_work_items_active_order"));
        doThrow(orderRace).when(itemRepository).saveAndFlush(any());
        assertThatThrownBy(() -> service.addWorkItem(shutdownId,
                request(4L, PlannedShutdownWorkItemSourceType.MANUAL, null, 0)))
                .isInstanceOfSatisfying(com.toir.exception.RestException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT));

        DataIntegrityViolationException unrelated = new DataIntegrityViolationException("some_other_constraint");
        doThrow(unrelated).when(itemRepository).saveAndFlush(any());
        assertThatThrownBy(() -> service.addWorkItem(shutdownId,
                request(4L, PlannedShutdownWorkItemSourceType.MANUAL, null, 0))).isSameAs(unrelated);
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
