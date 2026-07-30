package com.toir.service.sparepartlifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.toir.entity.SparePart;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.sparepartlifecycle.SparePartDueEvent;
import com.toir.entity.sparepartlifecycle.SparePartInstallation;
import com.toir.enums.sparepartlifecycle.SparePartDueAction;
import com.toir.enums.sparepartlifecycle.SparePartDueEventState;
import com.toir.enums.sparepartlifecycle.SparePartInstallationStatus;
import com.toir.enums.sparepartlifecycle.SparePartLifecycleEvaluationState;
import com.toir.enums.sparepartlifecycle.SparePartLifecycleView;
import com.toir.enums.sparepartlifecycle.SparePartWarehouseVisibility;
import com.toir.repository.SparePartRepository;
import com.toir.repository.WarehouseStockRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.sparepartlifecycle.SparePartDueEventRepository;
import com.toir.repository.sparepartlifecycle.SparePartDueEventWorkOrderLinkRepository;
import com.toir.repository.sparepartlifecycle.SparePartInstallationRepository;
import com.toir.security.ScopeAccessService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SparePartLifecycleAggregateServiceTest {

    @Test
    void hiddenWarehouseIsNullAndNegativeRemainingStaysOverdue() {
        EquipmentRepository equipmentRepository = mock(EquipmentRepository.class);
        SparePartInstallationRepository installationRepository = mock(SparePartInstallationRepository.class);
        SparePartDueEventRepository dueEventRepository = mock(SparePartDueEventRepository.class);
        SparePartRepository sparePartRepository = mock(SparePartRepository.class);
        WarehouseStockRepository warehouseStockRepository = mock(WarehouseStockRepository.class);
        ScopeAccessService scopeAccessService = mock(ScopeAccessService.class);
        SparePartDueEventWorkOrderLinkRepository linkRepository =
                mock(SparePartDueEventWorkOrderLinkRepository.class);
        SparePartLifecycleAggregateService service = new SparePartLifecycleAggregateService(
                equipmentRepository, installationRepository, dueEventRepository, sparePartRepository,
                warehouseStockRepository, scopeAccessService, new SparePartLifecyclePolicy(), linkRepository,
                new ObjectMapper().findAndRegisterModules());
        UUID equipmentId = UUID.randomUUID();
        Equipment equipment = new Equipment();
        equipment.setId(equipmentId);
        SparePartInstallation installation = new SparePartInstallation();
        installation.setId(UUID.randomUUID());
        installation.setEquipmentId(equipmentId);
        installation.setSparePartId(UUID.randomUUID());
        installation.setStatus(SparePartInstallationStatus.ACTIVE);
        installation.setLifecycleEvaluationState(SparePartLifecycleEvaluationState.OVERDUE);
        installation.setInstalledAt(Instant.parse("2026-01-01T00:00:00Z"));
        SparePartDueEvent event = new SparePartDueEvent();
        event.setId(UUID.randomUUID());
        event.setInstallationId(installation.getId());
        event.setState(SparePartDueEventState.OVERDUE);
        event.setDueAction(SparePartDueAction.BLOCK_OPERATION);
        event.setCurrentMeterValue(new BigDecimal("120"));
        event.setDueMeterValue(new BigDecimal("100"));
        SparePart part = new SparePart();
        part.setId(installation.getSparePartId());
        part.setName("Bearing");
        part.setCode("BRG-1");
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(installationRepository.findAllByEquipmentIdAndIsDeletedFalseOrderByInstalledAtDesc(equipmentId))
                .thenReturn(List.of(installation));
        when(dueEventRepository.findAllByInstallationIdInAndIsDeletedFalse(List.of(installation.getId())))
                .thenReturn(List.of(event));
        when(sparePartRepository.findAllByIdInAndIsDeletedFalse(java.util.Set.of(part.getId())))
                .thenReturn(List.of(part));
        when(linkRepository.findAllByDueEventIdInAndIsDeletedFalseOrderByCreatedAtDesc(List.of(event.getId())))
                .thenReturn(List.of());

        var response = service.get(equipmentId, SparePartLifecycleView.ATTENTION, null, null, 0, 20);

        assertThat(response.summary().warehouseVisibility())
                .isEqualTo(SparePartWarehouseVisibility.HIDDEN_NO_PERMISSION);
        assertThat(response.items().getContent().getFirst().warehouseAvailability()).isNull();
        assertThat(response.items().getContent().getFirst().remainingValue()).isEqualByComparingTo("-20");
        assertThat(response.items().getContent().getFirst().status())
                .isEqualTo(SparePartLifecycleEvaluationState.OVERDUE);
        verify(warehouseStockRepository, never())
                .findAllBySparePartIdInAndIsDeletedFalseOrderByUpdatedAtDesc(java.util.Set.of(part.getId()));
    }

    @Test
    void exposesManualDueAndLifecycleCommandsOnlyFromAuthoritativePermissions() throws Exception {
        EquipmentRepository equipmentRepository = mock(EquipmentRepository.class);
        SparePartInstallationRepository installationRepository = mock(SparePartInstallationRepository.class);
        SparePartDueEventRepository dueEventRepository = mock(SparePartDueEventRepository.class);
        SparePartRepository sparePartRepository = mock(SparePartRepository.class);
        WarehouseStockRepository warehouseStockRepository = mock(WarehouseStockRepository.class);
        ScopeAccessService scopeAccessService = mock(ScopeAccessService.class);
        SparePartDueEventWorkOrderLinkRepository linkRepository =
                mock(SparePartDueEventWorkOrderLinkRepository.class);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        SparePartLifecycleAggregateService service = new SparePartLifecycleAggregateService(
                equipmentRepository, installationRepository, dueEventRepository, sparePartRepository,
                warehouseStockRepository, scopeAccessService, new SparePartLifecyclePolicy(), linkRepository,
                objectMapper);
        UUID equipmentId = UUID.randomUUID();
        Equipment equipment = new Equipment();
        equipment.setId(equipmentId);
        SparePartInstallation installation = new SparePartInstallation();
        installation.setId(UUID.randomUUID());
        installation.setEquipmentId(equipmentId);
        installation.setSparePartId(UUID.randomUUID());
        installation.setStatus(SparePartInstallationStatus.ACTIVE);
        installation.setLifecycleEvaluationState(SparePartLifecycleEvaluationState.OK);
        installation.setInstalledAt(Instant.parse("2026-01-01T00:00:00Z"));
        installation.setAppliedRuleSnapshot(objectMapper.writeValueAsString(
                new com.toir.dto.sparepartlifecycle.AppliedLifeRuleSnapshot(
                        UUID.randomUUID(), 1,
                        com.toir.enums.sparepartlifecycle.SparePartLifeCombinationMode.MANUAL,
                        SparePartDueAction.MAINTENANCE_REQUIRED, List.of())));
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(installationRepository.findAllByEquipmentIdAndIsDeletedFalseOrderByInstalledAtDesc(equipmentId))
                .thenReturn(List.of(installation));
        when(dueEventRepository.findAllByInstallationIdInAndIsDeletedFalse(List.of(installation.getId())))
                .thenReturn(List.of());
        when(sparePartRepository.findAllByIdInAndIsDeletedFalse(java.util.Set.of(installation.getSparePartId())))
                .thenReturn(List.of());
        when(scopeAccessService.hasAuthority("SPARE_PART_MANUAL_DUE")).thenReturn(true);
        when(scopeAccessService.hasAuthority("SPARE_PART_REPLACE")).thenReturn(true);
        when(scopeAccessService.hasAuthority("SPARE_PART_REMOVE")).thenReturn(true);

        var actions = service.get(equipmentId, SparePartLifecycleView.INSTALLED, null, null, 0, 20)
                .items().getContent().getFirst().availableActions();

        assertThat(actions).containsExactly("MANUAL_DUE", "REPLACE", "REMOVE");
    }
}
