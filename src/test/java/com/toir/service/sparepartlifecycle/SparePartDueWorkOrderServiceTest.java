package com.toir.service.sparepartlifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.toir.dto.workorder.WorkOrderDto;
import com.toir.entity.sparepartlifecycle.SparePartDueEventWorkOrderLink;
import com.toir.entity.sparepartlifecycle.SparePartDueEvent;
import com.toir.entity.sparepartlifecycle.SparePartInstallation;
import com.toir.entity.equipment.Equipment;
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
}
