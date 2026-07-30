package com.toir.service.sparepartlifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.toir.dto.sparepartlifecycle.CreateDueEventWorkOrderRequest;
import com.toir.dto.workorder.WorkOrderDto;
import com.toir.dto.workorder.WorkOrderRequest;
import com.toir.entity.sparepartlifecycle.SparePartDueEventWorkOrderLink;
import com.toir.entity.sparepartlifecycle.SparePartDueEvent;
import com.toir.entity.sparepartlifecycle.SparePartInstallation;
import com.toir.entity.equipment.Equipment;
import com.toir.enums.WorkOrderStatus;
import com.toir.enums.sparepartlifecycle.SparePartDueEventState;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.sparepartlifecycle.SparePartDueEventRepository;
import com.toir.repository.sparepartlifecycle.SparePartDueEventWorkOrderLinkRepository;
import com.toir.repository.sparepartlifecycle.SparePartInstallationRepository;
import com.toir.security.PermissionConstants;
import com.toir.security.ScopeAccessService;
import com.toir.service.WorkOrderService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SparePartDueWorkOrderServiceTest {

    @Test
    void repeatedIdempotencyKeyReturnsExistingWorkOrderWithoutCreatingPprOrAnotherWorkOrder() {
        SparePartDueEventRepository eventRepository = mock(SparePartDueEventRepository.class);
        SparePartDueEventWorkOrderLinkRepository linkRepository =
                mock(SparePartDueEventWorkOrderLinkRepository.class);
        SparePartInstallationRepository installationRepository = mock(SparePartInstallationRepository.class);
        EquipmentRepository equipmentRepository = mock(EquipmentRepository.class);
        ScopeAccessService scopeAccessService = mock(ScopeAccessService.class);
        WorkOrderService workOrderService = mock(WorkOrderService.class);
        SparePartDueWorkOrderService service = new SparePartDueWorkOrderService(
                eventRepository, linkRepository, installationRepository, equipmentRepository,
                scopeAccessService, workOrderService);
        UUID eventId = UUID.randomUUID();
        UUID workOrderId = UUID.randomUUID();
        SparePartDueEventWorkOrderLink link = new SparePartDueEventWorkOrderLink();
        link.setDueEventId(eventId);
        link.setWorkOrderId(workOrderId);
        link.setIdempotencyKey("request-1");
        WorkOrderDto workOrder = mock(WorkOrderDto.class);
        SparePartDueEvent event = new SparePartDueEvent();
        event.setId(eventId);
        event.setInstallationId(UUID.randomUUID());
        event.setState(SparePartDueEventState.DUE);
        SparePartInstallation installation = new SparePartInstallation();
        installation.setId(event.getInstallationId());
        installation.setEquipmentId(UUID.randomUUID());
        Equipment equipment = new Equipment();
        equipment.setId(installation.getEquipmentId());
        when(scopeAccessService.hasAuthority(PermissionConstants.SPARE_PART_DUE_WORK_ORDER_CREATE))
                .thenReturn(true);
        when(linkRepository.findByDueEventIdAndIdempotencyKeyAndIsDeletedFalse(eventId, "request-1"))
                .thenReturn(Optional.of(link));
        when(eventRepository.findByIdAndIsDeletedFalseForUpdate(eventId)).thenReturn(Optional.of(event));
        when(installationRepository.findByIdAndIsDeletedFalse(event.getInstallationId()))
                .thenReturn(Optional.of(installation));
        when(equipmentRepository.findByIdAndIsDeletedFalse(installation.getEquipmentId()))
                .thenReturn(Optional.of(equipment));
        when(workOrderService.findById(workOrderId)).thenReturn(workOrder);

        var result = service.create(eventId, "request-1", UUID.randomUUID(), null);

        assertThat(result.replayed()).isTrue();
        assertThat(result.workOrder()).isSameAs(workOrder);
    }

    @Test
    void existingActiveLinkedWorkOrderPreventsDuplicateCreation() {
        Fixture fixture = fixture();
        UUID existingWorkOrderId = UUID.randomUUID();
        fixture.event.setLinkedWorkOrderId(existingWorkOrderId);
        WorkOrderDto existing = mock(WorkOrderDto.class);
        when(existing.status()).thenReturn(WorkOrderStatus.IN_PROGRESS);
        when(fixture.workOrderService.findById(existingWorkOrderId)).thenReturn(existing);

        var result = fixture.service.create(
                fixture.event.getId(), "request-active", fixture.actorId,
                new CreateDueEventWorkOrderRequest(null, null));

        assertThat(result.replayed()).isTrue();
        assertThat(result.workOrder()).isSameAs(existing);
        verify(fixture.workOrderService, never()).createGenerated(any(WorkOrderRequest.class), any(UUID.class));
        verify(fixture.linkRepository, never()).save(any(SparePartDueEventWorkOrderLink.class));
    }

    @Test
    void cancelledLinkedWorkOrderRemainsInHistoryAndAllowsReplacementWorkOrder() {
        Fixture fixture = fixture();
        UUID cancelledWorkOrderId = UUID.randomUUID();
        fixture.event.setLinkedWorkOrderId(cancelledWorkOrderId);
        WorkOrderDto cancelled = mock(WorkOrderDto.class);
        when(cancelled.status()).thenReturn(WorkOrderStatus.CANCELLED);
        when(fixture.workOrderService.findById(cancelledWorkOrderId)).thenReturn(cancelled);
        WorkOrderDto created = mock(WorkOrderDto.class);
        UUID createdId = UUID.randomUUID();
        when(created.id()).thenReturn(createdId);
        when(fixture.workOrderService.createGenerated(any(WorkOrderRequest.class), any(UUID.class)))
                .thenReturn(created);

        var result = fixture.service.create(
                fixture.event.getId(), "request-after-cancel", fixture.actorId,
                new CreateDueEventWorkOrderRequest("Replacement maintenance", "Cancelled predecessor"));

        assertThat(result.replayed()).isFalse();
        assertThat(result.workOrder()).isSameAs(created);
        assertThat(fixture.event.getLinkedWorkOrderId()).isEqualTo(createdId);
        verify(fixture.linkRepository).save(any(SparePartDueEventWorkOrderLink.class));
        verify(fixture.linkRepository, never()).delete(any(SparePartDueEventWorkOrderLink.class));
    }

    private static Fixture fixture() {
        SparePartDueEventRepository eventRepository = mock(SparePartDueEventRepository.class);
        SparePartDueEventWorkOrderLinkRepository linkRepository =
                mock(SparePartDueEventWorkOrderLinkRepository.class);
        SparePartInstallationRepository installationRepository = mock(SparePartInstallationRepository.class);
        EquipmentRepository equipmentRepository = mock(EquipmentRepository.class);
        ScopeAccessService scopeAccessService = mock(ScopeAccessService.class);
        WorkOrderService workOrderService = mock(WorkOrderService.class);
        SparePartDueWorkOrderService service = new SparePartDueWorkOrderService(
                eventRepository, linkRepository, installationRepository, equipmentRepository,
                scopeAccessService, workOrderService);
        UUID actorId = UUID.randomUUID();
        SparePartDueEvent event = new SparePartDueEvent();
        event.setId(UUID.randomUUID());
        event.setInstallationId(UUID.randomUUID());
        event.setState(SparePartDueEventState.DUE);
        event.setCycleKey("RULE:fixture");
        SparePartInstallation installation = new SparePartInstallation();
        installation.setId(event.getInstallationId());
        installation.setEquipmentId(UUID.randomUUID());
        Equipment equipment = new Equipment();
        equipment.setId(installation.getEquipmentId());
        equipment.setDepartmentId(UUID.randomUUID());
        when(scopeAccessService.hasAuthority(PermissionConstants.SPARE_PART_DUE_WORK_ORDER_CREATE))
                .thenReturn(true);
        when(eventRepository.findByIdAndIsDeletedFalseForUpdate(event.getId())).thenReturn(Optional.of(event));
        when(installationRepository.findByIdAndIsDeletedFalse(event.getInstallationId()))
                .thenReturn(Optional.of(installation));
        when(equipmentRepository.findByIdAndIsDeletedFalse(installation.getEquipmentId()))
                .thenReturn(Optional.of(equipment));
        return new Fixture(
                service, linkRepository, workOrderService, event, actorId);
    }

    private record Fixture(
            SparePartDueWorkOrderService service,
            SparePartDueEventWorkOrderLinkRepository linkRepository,
            WorkOrderService workOrderService,
            SparePartDueEvent event,
            UUID actorId
    ) { }
}
