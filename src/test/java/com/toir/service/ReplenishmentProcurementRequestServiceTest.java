package com.toir.service;

import com.toir.dto.procurement.ProcurementRequestDto;
import com.toir.dto.warehouse.InventoryReplenishmentReason;
import com.toir.dto.warehouse.InventoryReplenishmentRecommendationDto;
import com.toir.dto.warehouse.ReplenishmentProcurementItemRequest;
import com.toir.dto.warehouse.ReplenishmentProcurementRequest;
import com.toir.entity.SparePart;
import com.toir.entity.equipment.ProcurementRequestLine;
import com.toir.entity.projects.ProcurementRequest;
import com.toir.entity.warehouse.Warehouse;
import com.toir.enums.NotificationSeverity;
import com.toir.enums.PriorityLevel;
import com.toir.enums.ProcurementRequestStatus;
import com.toir.exception.RestException;
import com.toir.repository.ProcurementRequestRepository;
import com.toir.repository.SparePartRepository;
import com.toir.repository.WarehouseRepository;
import com.toir.security.ScopeAccessService;
import com.toir.util.AuditBuilderService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReplenishmentProcurementRequestServiceTest {

    @Mock
    ProcurementRequestRepository procurementRequestRepository;

    @Mock
    SparePartRepository sparePartRepository;

    @Mock
    WarehouseRepository warehouseRepository;

    @Mock
    InventoryReplenishmentRecommendationService recommendationService;

    @Mock
    ScopeAccessService scopeAccessService;

    @Mock
    AuditBuilderService auditBuilderService;

    ReplenishmentProcurementRequestService service;

    @BeforeEach
    void setUp() {
        service = new ReplenishmentProcurementRequestService(
                procurementRequestRepository,
                sparePartRepository,
                warehouseRepository,
                recommendationService,
                scopeAccessService,
                auditBuilderService
        );
    }

    @Test
    void createsDraftAutoProcurementFromSelectedRecommendation() {
        UUID sparePartId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        Instant firstDueAt = Instant.parse("2026-07-10T09:00:00Z");
        InventoryReplenishmentRecommendationDto recommendation = recommendation(
                sparePartId,
                warehouseId,
                "Main warehouse",
                7.0,
                NotificationSeverity.CRITICAL,
                InventoryReplenishmentReason.LOW_STOCK,
                firstDueAt
        );
        SparePart sparePart = sparePart(sparePartId, "PCS");
        Warehouse warehouse = warehouse(warehouseId, "Main warehouse");

        when(recommendationService.recommendationRows(30, null, null, null, true))
                .thenReturn(List.of(recommendation));
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(warehouseRepository.findByIdAndIsDeletedFalse(warehouseId)).thenReturn(Optional.of(warehouse));
        when(sparePartRepository.findByIdAndIsDeletedFalse(sparePartId)).thenReturn(Optional.of(sparePart));
        when(procurementRequestRepository.countByIsDeletedFalse()).thenReturn(0L);
        when(procurementRequestRepository.existsByNumberAndIsDeletedFalse(any())).thenReturn(false);
        when(procurementRequestRepository.existsActiveAutoForWarehouseAndSparePart(warehouseId, sparePartId))
                .thenReturn(false);
        when(procurementRequestRepository.save(any(ProcurementRequest.class))).thenAnswer(invocation -> {
            ProcurementRequest request = invocation.getArgument(0);
            request.setId(UUID.randomUUID());
            for (ProcurementRequestLine line : request.getLines()) {
                line.setId(UUID.randomUUID());
            }
            return request;
        });

        List<ProcurementRequestDto> result = service.createProcurementRequests(new ReplenishmentProcurementRequest(
                30,
                null,
                null,
                null,
                true,
                List.of(new ReplenishmentProcurementItemRequest(sparePartId, warehouseId, null, null))
        ));

        assertThat(result).hasSize(1);
        ProcurementRequestDto dto = result.getFirst();
        assertThat(dto.source()).isEqualTo("AUTO");
        assertThat(dto.status()).isEqualTo(ProcurementRequestStatus.DRAFT);
        assertThat(dto.priority()).isEqualTo(PriorityLevel.CRITICAL);
        assertThat(dto.warehouseId()).isEqualTo(warehouseId);
        assertThat(dto.requiredBy()).isEqualTo(LocalDate.of(2026, 7, 10));
        assertThat(dto.lines()).hasSize(1);
        assertThat(dto.lines().getFirst().sparePartId()).isEqualTo(sparePartId);
        assertThat(dto.lines().getFirst().quantity()).isEqualTo(7.0);
        assertThat(dto.lines().getFirst().unit()).isEqualTo("PCS");
        assertThat(dto.lines().getFirst().notes()).contains("LOW_STOCK", "available=1.0", "suggested=7.0");

        ArgumentCaptor<ProcurementRequest> captor = ArgumentCaptor.forClass(ProcurementRequest.class);
        verify(procurementRequestRepository).save(captor.capture());
        assertThat(captor.getValue().getSource()).isEqualTo("AUTO");
        assertThat(captor.getValue().getLines().getFirst().getRemainingQuantity()).isEqualTo(7.0);
    }

    @Test
    void enterpriseRecommendationRequiresTargetWarehouseBeforeCreatingProcurement() {
        UUID sparePartId = UUID.randomUUID();
        InventoryReplenishmentRecommendationDto recommendation = recommendation(
                sparePartId,
                null,
                "Enterprise",
                5.0,
                NotificationSeverity.WARNING,
                InventoryReplenishmentReason.LOW_STOCK,
                null
        );
        when(recommendationService.recommendationRows(30, null, null, null, true))
                .thenReturn(List.of(recommendation));

        assertThatThrownBy(() -> service.createProcurementRequests(new ReplenishmentProcurementRequest(
                30,
                null,
                null,
                null,
                true,
                List.of(new ReplenishmentProcurementItemRequest(sparePartId, null, null, null))
        )))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("targetWarehouseId");

        verify(procurementRequestRepository, never()).save(any());
    }

    @Test
    void rejectsDuplicateActiveAutoProcurementForSameWarehouseAndSparePart() {
        UUID sparePartId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        InventoryReplenishmentRecommendationDto recommendation = recommendation(
                sparePartId,
                warehouseId,
                "Main warehouse",
                4.0,
                NotificationSeverity.WARNING,
                InventoryReplenishmentReason.LOW_STOCK,
                null
        );
        when(recommendationService.recommendationRows(30, null, null, null, true))
                .thenReturn(List.of(recommendation));
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(warehouseRepository.findByIdAndIsDeletedFalse(warehouseId)).thenReturn(Optional.of(warehouse(warehouseId, "Main warehouse")));
        when(procurementRequestRepository.existsActiveAutoForWarehouseAndSparePart(warehouseId, sparePartId))
                .thenReturn(true);

        assertThatThrownBy(() -> service.createProcurementRequests(new ReplenishmentProcurementRequest(
                30,
                null,
                null,
                null,
                true,
                List.of(new ReplenishmentProcurementItemRequest(sparePartId, warehouseId, null, null))
        )))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("already exists");

        verify(procurementRequestRepository, never()).save(any());
    }

    @Test
    void deDuplicatesRepeatedItemsInsideOneRequestPayload() {
        UUID sparePartId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        InventoryReplenishmentRecommendationDto recommendation = recommendation(
                sparePartId,
                warehouseId,
                "Main warehouse",
                4.0,
                NotificationSeverity.WARNING,
                InventoryReplenishmentReason.LOW_STOCK,
                null
        );
        when(recommendationService.recommendationRows(30, null, null, null, true))
                .thenReturn(List.of(recommendation));
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(warehouseRepository.findByIdAndIsDeletedFalse(warehouseId)).thenReturn(Optional.of(warehouse(warehouseId, "Main warehouse")));
        when(sparePartRepository.findByIdAndIsDeletedFalse(sparePartId)).thenReturn(Optional.of(sparePart(sparePartId, "PCS")));
        when(procurementRequestRepository.countByIsDeletedFalse()).thenReturn(0L);
        when(procurementRequestRepository.existsByNumberAndIsDeletedFalse(any())).thenReturn(false);
        when(procurementRequestRepository.existsActiveAutoForWarehouseAndSparePart(warehouseId, sparePartId))
                .thenReturn(false);
        when(procurementRequestRepository.save(any(ProcurementRequest.class))).thenAnswer(invocation -> {
            ProcurementRequest request = invocation.getArgument(0);
            request.setId(UUID.randomUUID());
            request.getLines().forEach(line -> line.setId(UUID.randomUUID()));
            return request;
        });

        List<ProcurementRequestDto> result = service.createProcurementRequests(new ReplenishmentProcurementRequest(
                30,
                null,
                null,
                null,
                true,
                List.of(
                        new ReplenishmentProcurementItemRequest(sparePartId, warehouseId, null, null),
                        new ReplenishmentProcurementItemRequest(sparePartId, warehouseId, null, null)
                )
        ));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().lines()).hasSize(1);
        verify(procurementRequestRepository).lockAutoProcurementKey(warehouseId, sparePartId);
    }

    private InventoryReplenishmentRecommendationDto recommendation(UUID sparePartId,
                                                                   UUID warehouseId,
                                                                   String warehouseName,
                                                                   double suggestedQty,
                                                                   NotificationSeverity severity,
                                                                   InventoryReplenishmentReason reason,
                                                                   Instant firstDueAt) {
        return new InventoryReplenishmentRecommendationDto(
                sparePartId,
                "SP-001",
                "Bearing",
                warehouseId,
                warehouseName,
                3.0,
                2.0,
                1.0,
                5.0,
                6.0,
                null,
                0.0,
                0.0,
                1.0,
                4.0,
                suggestedQty,
                UUID.randomUUID(),
                "Best Supplier",
                LocalDate.of(2026, 7, 20),
                severity,
                reason,
                0,
                firstDueAt,
                List.of()
        );
    }

    private SparePart sparePart(UUID id, String unit) {
        SparePart sparePart = new SparePart();
        sparePart.setId(id);
        sparePart.setCode("SP-001");
        sparePart.setName("Bearing");
        sparePart.setUnit(unit);
        return sparePart;
    }

    private Warehouse warehouse(UUID id, String name) {
        Warehouse warehouse = new Warehouse();
        warehouse.setId(id);
        warehouse.setName(name);
        return warehouse;
    }
}
