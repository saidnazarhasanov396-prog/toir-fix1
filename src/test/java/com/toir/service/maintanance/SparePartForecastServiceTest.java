package com.toir.service.maintanance;

import com.toir.dto.sparepartforecast.SparePartForecastRequest;
import com.toir.entity.Department;
import com.toir.entity.OperationalIssue;
import com.toir.entity.SparePart;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.maintenance.MaintenanceDueEvent;
import com.toir.entity.maintenance.MaintenanceRegulation;
import com.toir.entity.maintenance.MaintenanceRegulationSparePartRequirement;
import com.toir.entity.maintenance.MaintenanceTemplate;
import com.toir.entity.maintenance.MaintenanceTemplateSparePartRequirement;
import com.toir.entity.warehouse.Warehouse;
import com.toir.entity.warehouse.WarehouseStock;
import com.toir.enums.EquipmentLocationType;
import com.toir.enums.MaintenanceDueEventStatus;
import com.toir.enums.MaintenanceDueStatus;
import com.toir.enums.MaintenanceTriggerSource;
import com.toir.enums.NotificationSeverity;
import com.toir.enums.OperationalIssueStatus;
import com.toir.enums.OperationalIssueType;
import com.toir.repository.OperationalIssueRepository;
import com.toir.repository.SparePartRepository;
import com.toir.repository.WarehouseRepository;
import com.toir.repository.WarehouseStockRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.maintenance.MaintenanceDueEventRepository;
import com.toir.repository.maintenance.MaintenanceRegulationSparePartRequirementRepository;
import com.toir.repository.maintenance.MaintenanceTemplateSparePartRequirementRepository;
import com.toir.security.ScopeAccessService;
import com.toir.service.OperationalIssueService;
import com.toir.service.warehouse.LegacyStockProjectionService;
import com.toir.service.warehouse.WmsStockSnapshot;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SparePartForecastServiceTest {

    @Mock
    private MaintenanceDueEventRepository eventRepository;
    @Mock
    private MaintenanceTemplateSparePartRequirementRepository requirementRepository;
    @Mock
    private MaintenanceRegulationSparePartRequirementRepository regulationRequirementRepository;
    @Mock
    private WarehouseStockRepository stockRepository;
    @Mock
    private WarehouseRepository warehouseRepository;
    @Mock
    private SparePartRepository sparePartRepository;
    @Mock
    private EquipmentRepository equipmentRepository;
    @Mock
    private com.toir.repository.department.DepartmentRepository departmentRepository;
    @Mock
    private OperationalIssueRepository operationalIssueRepository;
    @Mock
    private OperationalIssueService operationalIssueService;
    @Mock
    private ScopeAccessService scopeAccessService;
    @Mock
    private LegacyStockProjectionService legacyStockProjectionService;

    @InjectMocks
    private SparePartForecastService service;
    private Map<LegacyStockProjectionService.StockKey, WmsStockSnapshot> wmsSnapshots;

    @BeforeEach
    void setUpScope() {
        wmsSnapshots = new HashMap<>();
        lenient().when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        lenient().when(legacyStockProjectionService.currentAll()).thenAnswer(invocation -> wmsSnapshots);
        lenient().when(legacyStockProjectionService.snapshot(any(), any(), any())).thenAnswer(invocation ->
                wmsSnapshots.getOrDefault(
                        new LegacyStockProjectionService.StockKey(invocation.getArgument(1), invocation.getArgument(2)),
                        new WmsStockSnapshot(invocation.getArgument(1), invocation.getArgument(2),
                                BigDecimal.ZERO, BigDecimal.ZERO)
                ));
    }

    @Test
    void forecastDueEventAndRequirementCalculatesShortage() {
        UUID templateId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        UUID requirementId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        Instant now = Instant.parse("2026-06-04T00:00:00Z");
        Instant dueAt = Instant.parse("2026-06-10T09:00:00Z");

        when(eventRepository.findForecastCandidates(
                now,
                now.plusSeconds(30L * 24 * 60 * 60),
                null,
                null,
                null,
                List.of(
                        MaintenanceDueEventStatus.DETECTED,
                        MaintenanceDueEventStatus.AWAITING_APPROVAL,
                        MaintenanceDueEventStatus.TASK_CREATED,
                        MaintenanceDueEventStatus.WORK_ORDER_CREATED
                ),
                List.of(MaintenanceDueStatus.UPCOMING, MaintenanceDueStatus.DUE, MaintenanceDueStatus.OVERDUE)
        )).thenReturn(List.of(event(eventId, templateId, equipmentId, dueAt)));
        when(requirementRepository.findAllActiveByTemplateIdIn(List.of(templateId)))
                .thenReturn(List.of(requirement(requirementId, templateId, sparePartId, 5)));
        when(stockRepository.findAllBySparePartIdInAndWarehouseIdAndIsDeletedFalseOrderByUpdatedAtDesc(
                List.of(sparePartId),
                warehouseId
        )).thenReturn(List.of(stock(warehouseId, sparePartId, 3, 1)));
        when(sparePartRepository.findAllByIdInAndIsDeletedFalse(List.of(sparePartId)))
                .thenReturn(List.of(sparePart(sparePartId)));
        when(warehouseRepository.findAllByIdInAndIsDeletedFalse(List.of(warehouseId)))
                .thenReturn(List.of(warehouse(warehouseId)));
        when(equipmentRepository.findAllByIdInAndIsDeletedFalse(List.of(equipmentId)))
                .thenReturn(List.of(equipment(equipmentId)));

        var summary = service.forecast(new SparePartForecastRequest(
                30,
                now,
                null,
                warehouseId,
                null,
                null,
                null,
                false
        ));

        assertThat(summary.periodStart()).isEqualTo(now);
        assertThat(summary.items()).hasSize(1);
        var item = summary.items().get(0);
        assertThat(item.sparePartId()).isEqualTo(sparePartId);
        assertThat(item.warehouseId()).isEqualTo(warehouseId);
        assertThat(item.requiredQty()).isEqualTo(5);
        assertThat(item.availableQty()).isEqualTo(2);
        assertThat(item.reservedQty()).isEqualTo(1);
        assertThat(item.shortageQty()).isEqualTo(3);
        assertThat(item.severity()).isEqualTo(NotificationSeverity.WARNING);
        assertThat(item.firstDueAt()).isEqualTo(dueAt);
        assertThat(item.sourceCount()).isEqualTo(1);
        assertThat(item.sources().get(0).maintenanceDueEventId()).isEqualTo(eventId);
        assertThat(item.sources().get(0).requiredQty()).isEqualTo(5);
    }

    @Test
    void forecastDueEventAndRegulationRequirementCalculatesShortageWithoutTemplate() {
        UUID regulationId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        UUID requirementId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        Instant now = Instant.parse("2026-06-04T00:00:00Z");
        Instant dueAt = Instant.parse("2026-06-10T09:00:00Z");
        MaintenanceDueEvent event = event(eventId, null, equipmentId, dueAt);
        event.setRegulationId(regulationId);

        when(eventRepository.findForecastCandidates(
                now,
                now.plusSeconds(30L * 24 * 60 * 60),
                null,
                null,
                null,
                List.of(
                        MaintenanceDueEventStatus.DETECTED,
                        MaintenanceDueEventStatus.AWAITING_APPROVAL,
                        MaintenanceDueEventStatus.TASK_CREATED,
                        MaintenanceDueEventStatus.WORK_ORDER_CREATED
                ),
                List.of(MaintenanceDueStatus.UPCOMING, MaintenanceDueStatus.DUE, MaintenanceDueStatus.OVERDUE)
        )).thenReturn(List.of(event));
        lenient().when(requirementRepository.findAllActiveByTemplateIdIn(List.of())).thenReturn(List.of());
        when(regulationRequirementRepository.findAllActiveByRegulationIdIn(List.of(regulationId)))
                .thenReturn(List.of(regulationRequirement(requirementId, regulationId, sparePartId, 6)));
        when(stockRepository.findAllBySparePartIdInAndWarehouseIdAndIsDeletedFalseOrderByUpdatedAtDesc(
                List.of(sparePartId),
                warehouseId
        )).thenReturn(List.of(stock(warehouseId, sparePartId, 2, 0)));
        when(sparePartRepository.findAllByIdInAndIsDeletedFalse(List.of(sparePartId)))
                .thenReturn(List.of(sparePart(sparePartId)));
        when(warehouseRepository.findAllByIdInAndIsDeletedFalse(List.of(warehouseId)))
                .thenReturn(List.of(warehouse(warehouseId)));
        when(equipmentRepository.findAllByIdInAndIsDeletedFalse(List.of(equipmentId)))
                .thenReturn(List.of(equipment(equipmentId)));

        var summary = service.forecast(new SparePartForecastRequest(
                30,
                now,
                null,
                warehouseId,
                null,
                null,
                null,
                false
        ));

        assertThat(summary.items()).hasSize(1);
        var item = summary.items().get(0);
        assertThat(item.sparePartId()).isEqualTo(sparePartId);
        assertThat(item.requiredQty()).isEqualTo(6);
        assertThat(item.availableQty()).isEqualTo(2);
        assertThat(item.shortageQty()).isEqualTo(4);
        assertThat(item.sources().get(0).maintenanceDueEventId()).isEqualTo(eventId);
        assertThat(item.sources().get(0).templateId()).isNull();
        assertThat(item.sources().get(0).requiredQty()).isEqualTo(6);
    }

    @Test
    void onlyDeficitFiltersNonShortageItems() {
        UUID templateId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        Instant now = Instant.parse("2026-06-04T00:00:00Z");

        when(eventRepository.findForecastCandidates(
                eq(now),
                eq(now.plusSeconds(30L * 24 * 60 * 60)),
                isNull(),
                isNull(),
                isNull(),
                eq(List.of(
                        MaintenanceDueEventStatus.DETECTED,
                        MaintenanceDueEventStatus.AWAITING_APPROVAL,
                        MaintenanceDueEventStatus.TASK_CREATED,
                        MaintenanceDueEventStatus.WORK_ORDER_CREATED
                )),
                eq(List.of(MaintenanceDueStatus.UPCOMING, MaintenanceDueStatus.DUE, MaintenanceDueStatus.OVERDUE))
        )).thenReturn(List.of(event(UUID.randomUUID(), templateId, UUID.randomUUID(), now.plusSeconds(3600))));
        when(requirementRepository.findAllActiveByTemplateIdIn(List.of(templateId)))
                .thenReturn(List.of(requirement(UUID.randomUUID(), templateId, sparePartId, 2)));
        when(stockRepository.findAllBySparePartIdInAndWarehouseIdAndIsDeletedFalseOrderByUpdatedAtDesc(
                List.of(sparePartId),
                warehouseId
        )).thenReturn(List.of(stock(warehouseId, sparePartId, 10, 1)));
        when(sparePartRepository.findAllByIdInAndIsDeletedFalse(List.of(sparePartId)))
                .thenReturn(List.of(sparePart(sparePartId)));
        when(warehouseRepository.findAllByIdInAndIsDeletedFalse(List.of(warehouseId)))
                .thenReturn(List.of(warehouse(warehouseId)));

        var summary = service.forecast(new SparePartForecastRequest(
                30,
                now,
                null,
                warehouseId,
                null,
                null,
                null,
                true
        ));

        assertThat(summary.items()).isEmpty();
    }

    @Test
    void evaluateCreatesIssueAndDoesNotCreateDuplicateOpenIssue() {
        UUID templateId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        Instant now = Instant.parse("2026-06-04T00:00:00Z");

        when(eventRepository.findForecastCandidates(
                eq(now),
                eq(now.plusSeconds(30L * 24 * 60 * 60)),
                isNull(),
                isNull(),
                isNull(),
                eq(List.of(
                        MaintenanceDueEventStatus.DETECTED,
                        MaintenanceDueEventStatus.AWAITING_APPROVAL,
                        MaintenanceDueEventStatus.TASK_CREATED,
                        MaintenanceDueEventStatus.WORK_ORDER_CREATED
                )),
                eq(List.of(MaintenanceDueStatus.UPCOMING, MaintenanceDueStatus.DUE, MaintenanceDueStatus.OVERDUE))
        )).thenReturn(List.of(event(UUID.randomUUID(), templateId, equipmentId, now.plusSeconds(3600))));
        when(requirementRepository.findAllActiveByTemplateIdIn(List.of(templateId)))
                .thenReturn(List.of(requirement(UUID.randomUUID(), templateId, sparePartId, 4)));
        when(stockRepository.findAllBySparePartIdInAndWarehouseIdAndIsDeletedFalseOrderByUpdatedAtDesc(
                List.of(sparePartId),
                warehouseId
        )).thenReturn(List.of(stock(warehouseId, sparePartId, 1, 0)));
        when(sparePartRepository.findAllByIdInAndIsDeletedFalse(List.of(sparePartId)))
                .thenReturn(List.of(sparePart(sparePartId)));
        when(warehouseRepository.findAllByIdInAndIsDeletedFalse(List.of(warehouseId)))
                .thenReturn(List.of(warehouse(warehouseId)));
        when(equipmentRepository.findAllByIdInAndIsDeletedFalse(List.of(equipmentId)))
                .thenReturn(List.of(equipment(equipmentId)));
        when(operationalIssueRepository.findBySourceTypeAndSourceIdAndStatusAndIsDeletedFalse(
                eq("SPARE_PART_FORECAST"),
                org.mockito.ArgumentMatchers.any(UUID.class),
                eq(OperationalIssueStatus.OPEN)
        )).thenReturn(Optional.empty(), Optional.of(new OperationalIssue()));

        var first = service.evaluateAndCreateIssues(new SparePartForecastRequest(
                30,
                now,
                null,
                warehouseId,
                null,
                null,
                null,
                true
        ));
        var second = service.evaluateAndCreateIssues(new SparePartForecastRequest(
                30,
                now,
                null,
                warehouseId,
                null,
                null,
                null,
                true
        ));

        assertThat(first.createdIssueCount()).isEqualTo(1);
        assertThat(first.updatedIssueCount()).isZero();
        assertThat(second.createdIssueCount()).isZero();
        assertThat(second.updatedIssueCount()).isEqualTo(1);
        verify(operationalIssueService, times(2)).openOrUpdate(
                eq(OperationalIssueType.SPARE_PART_SHORTAGE_FORECAST),
                eq(NotificationSeverity.WARNING),
                any(),
                any(),
                eq("SPARE_PART_FORECAST"),
                org.mockito.ArgumentMatchers.any(UUID.class),
                startsWith("Spare part shortage forecast: Bearing"),
                org.mockito.ArgumentMatchers.contains("shortageQty=3.0"),
                any()
        );
    }

    @Test
    void evaluateDefaultDaysTwiceDoesNotCreateDuplicateIssue() {
        UUID templateId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        Set<UUID> openSourceIds = new HashSet<>();

        when(eventRepository.findForecastCandidates(
                any(Instant.class),
                any(Instant.class),
                isNull(),
                isNull(),
                isNull(),
                eq(List.of(
                        MaintenanceDueEventStatus.DETECTED,
                        MaintenanceDueEventStatus.AWAITING_APPROVAL,
                        MaintenanceDueEventStatus.TASK_CREATED,
                        MaintenanceDueEventStatus.WORK_ORDER_CREATED
                )),
                eq(List.of(MaintenanceDueStatus.UPCOMING, MaintenanceDueStatus.DUE, MaintenanceDueStatus.OVERDUE))
        )).thenReturn(List.of(event(UUID.randomUUID(), templateId, equipmentId, Instant.now().plusSeconds(3600))));
        when(requirementRepository.findAllActiveByTemplateIdIn(List.of(templateId)))
                .thenReturn(List.of(requirement(UUID.randomUUID(), templateId, sparePartId, 4)));
        when(stockRepository.findAllBySparePartIdInAndWarehouseIdAndIsDeletedFalseOrderByUpdatedAtDesc(
                List.of(sparePartId),
                warehouseId
        )).thenReturn(List.of(stock(warehouseId, sparePartId, 1, 0)));
        when(sparePartRepository.findAllByIdInAndIsDeletedFalse(List.of(sparePartId)))
                .thenReturn(List.of(sparePart(sparePartId)));
        when(warehouseRepository.findAllByIdInAndIsDeletedFalse(List.of(warehouseId)))
                .thenReturn(List.of(warehouse(warehouseId)));
        when(equipmentRepository.findAllByIdInAndIsDeletedFalse(List.of(equipmentId)))
                .thenReturn(List.of(equipment(equipmentId)));
        when(operationalIssueRepository.findBySourceTypeAndSourceIdAndStatusAndIsDeletedFalse(
                eq("SPARE_PART_FORECAST"),
                any(UUID.class),
                eq(OperationalIssueStatus.OPEN)
        )).thenAnswer(invocation -> openSourceIds.contains(invocation.getArgument(1))
                ? Optional.of(new OperationalIssue())
                : Optional.empty());
        when(operationalIssueService.openOrUpdate(
                eq(OperationalIssueType.SPARE_PART_SHORTAGE_FORECAST),
                any(),
                any(),
                any(),
                eq("SPARE_PART_FORECAST"),
                any(UUID.class),
                any(),
                any(),
                any()
        )).thenAnswer(invocation -> {
            openSourceIds.add(invocation.getArgument(5));
            return new OperationalIssue();
        });

        var first = service.evaluateAndCreateIssues(new SparePartForecastRequest(
                30,
                null,
                null,
                warehouseId,
                null,
                null,
                null,
                true
        ));
        var second = service.evaluateAndCreateIssues(new SparePartForecastRequest(
                30,
                null,
                null,
                warehouseId,
                null,
                null,
                null,
                true
        ));

        assertThat(first.createdIssueCount()).isEqualTo(1);
        assertThat(second.createdIssueCount()).isZero();
        assertThat(second.updatedIssueCount()).isEqualTo(1);
        assertThat(openSourceIds).hasSize(1);
    }

    @Test
    void evaluateUpdatesExistingIssueWhenShortageChanges() {
        UUID templateId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        Instant now = Instant.parse("2026-06-04T00:00:00Z");

        when(eventRepository.findForecastCandidates(
                eq(now),
                eq(now.plusSeconds(30L * 24 * 60 * 60)),
                isNull(),
                isNull(),
                isNull(),
                eq(List.of(
                        MaintenanceDueEventStatus.DETECTED,
                        MaintenanceDueEventStatus.AWAITING_APPROVAL,
                        MaintenanceDueEventStatus.TASK_CREATED,
                        MaintenanceDueEventStatus.WORK_ORDER_CREATED
                )),
                eq(List.of(MaintenanceDueStatus.UPCOMING, MaintenanceDueStatus.DUE, MaintenanceDueStatus.OVERDUE))
        )).thenReturn(List.of(event(UUID.randomUUID(), templateId, equipmentId, now.plusSeconds(3600))));
        when(requirementRepository.findAllActiveByTemplateIdIn(List.of(templateId)))
                .thenReturn(List.of(requirement(UUID.randomUUID(), templateId, sparePartId, 10)));
        when(stockRepository.findAllBySparePartIdInAndWarehouseIdAndIsDeletedFalseOrderByUpdatedAtDesc(
                List.of(sparePartId),
                warehouseId
        )).thenReturn(List.of(stock(warehouseId, sparePartId, 4, 0)));
        when(sparePartRepository.findAllByIdInAndIsDeletedFalse(List.of(sparePartId)))
                .thenReturn(List.of(sparePart(sparePartId)));
        when(warehouseRepository.findAllByIdInAndIsDeletedFalse(List.of(warehouseId)))
                .thenReturn(List.of(warehouse(warehouseId)));
        when(equipmentRepository.findAllByIdInAndIsDeletedFalse(List.of(equipmentId)))
                .thenReturn(List.of(equipment(equipmentId)));
        when(operationalIssueRepository.findBySourceTypeAndSourceIdAndStatusAndIsDeletedFalse(
                eq("SPARE_PART_FORECAST"),
                any(UUID.class),
                eq(OperationalIssueStatus.OPEN)
        )).thenReturn(Optional.of(new OperationalIssue()));

        var response = service.evaluateAndCreateIssues(new SparePartForecastRequest(
                30,
                now,
                null,
                warehouseId,
                null,
                null,
                null,
                true
        ));

        assertThat(response.createdIssueCount()).isZero();
        assertThat(response.updatedIssueCount()).isEqualTo(1);
        verify(operationalIssueService).openOrUpdate(
                eq(OperationalIssueType.SPARE_PART_SHORTAGE_FORECAST),
                eq(NotificationSeverity.WARNING),
                any(),
                any(),
                eq("SPARE_PART_FORECAST"),
                any(UUID.class),
                startsWith("Spare part shortage forecast: Bearing"),
                org.mockito.ArgumentMatchers.contains("shortageQty=6.0"),
                any()
        );
    }

    @Test
    void evaluateResolvesOpenIssueWhenShortageRecovers() {
        UUID templateId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        Instant now = Instant.parse("2026-06-04T00:00:00Z");
        OperationalIssue openIssue = new OperationalIssue();

        when(eventRepository.findForecastCandidates(
                eq(now),
                eq(now.plusSeconds(30L * 24 * 60 * 60)),
                isNull(),
                isNull(),
                isNull(),
                eq(List.of(
                        MaintenanceDueEventStatus.DETECTED,
                        MaintenanceDueEventStatus.AWAITING_APPROVAL,
                        MaintenanceDueEventStatus.TASK_CREATED,
                        MaintenanceDueEventStatus.WORK_ORDER_CREATED
                )),
                eq(List.of(MaintenanceDueStatus.UPCOMING, MaintenanceDueStatus.DUE, MaintenanceDueStatus.OVERDUE))
        )).thenReturn(List.of(event(UUID.randomUUID(), templateId, equipmentId, now.plusSeconds(3600))));
        when(requirementRepository.findAllActiveByTemplateIdIn(List.of(templateId)))
                .thenReturn(List.of(requirement(UUID.randomUUID(), templateId, sparePartId, 4)));
        when(stockRepository.findAllBySparePartIdInAndWarehouseIdAndIsDeletedFalseOrderByUpdatedAtDesc(
                List.of(sparePartId),
                warehouseId
        )).thenReturn(List.of(stock(warehouseId, sparePartId, 10, 0)));
        when(sparePartRepository.findAllByIdInAndIsDeletedFalse(List.of(sparePartId)))
                .thenReturn(List.of(sparePart(sparePartId)));
        when(warehouseRepository.findAllByIdInAndIsDeletedFalse(List.of(warehouseId)))
                .thenReturn(List.of(warehouse(warehouseId)));
        when(equipmentRepository.findAllByIdInAndIsDeletedFalse(List.of(equipmentId)))
                .thenReturn(List.of(equipment(equipmentId)));
        when(operationalIssueRepository.findBySourceTypeAndSourceIdAndStatusAndIsDeletedFalse(
                eq("SPARE_PART_FORECAST"),
                any(UUID.class),
                eq(OperationalIssueStatus.OPEN)
        )).thenReturn(Optional.of(openIssue));

        var response = service.evaluateAndCreateIssues(new SparePartForecastRequest(
                30,
                now,
                null,
                warehouseId,
                null,
                null,
                null,
                true
        ));

        assertThat(response.createdIssueCount()).isZero();
        assertThat(response.updatedIssueCount()).isZero();
        assertThat(response.resolvedIssueCount()).isEqualTo(1);
        verify(operationalIssueService).resolveOpen(
                eq("SPARE_PART_FORECAST"),
                any(UUID.class),
                org.mockito.ArgumentMatchers.contains("Spare part shortage recovered")
        );
    }

    @Test
    void forecastClampsNegativeAvailableToZero() {
        UUID templateId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        Instant now = Instant.parse("2026-06-04T00:00:00Z");

        when(eventRepository.findForecastCandidates(
                eq(now),
                eq(now.plusSeconds(30L * 24 * 60 * 60)),
                isNull(),
                isNull(),
                isNull(),
                eq(List.of(
                        MaintenanceDueEventStatus.DETECTED,
                        MaintenanceDueEventStatus.AWAITING_APPROVAL,
                        MaintenanceDueEventStatus.TASK_CREATED,
                        MaintenanceDueEventStatus.WORK_ORDER_CREATED
                )),
                eq(List.of(MaintenanceDueStatus.UPCOMING, MaintenanceDueStatus.DUE, MaintenanceDueStatus.OVERDUE))
        )).thenReturn(List.of(event(UUID.randomUUID(), templateId, UUID.randomUUID(), now.plusSeconds(3600))));
        when(requirementRepository.findAllActiveByTemplateIdIn(List.of(templateId)))
                .thenReturn(List.of(requirement(UUID.randomUUID(), templateId, sparePartId, 2)));
        when(stockRepository.findAllBySparePartIdInAndWarehouseIdAndIsDeletedFalseOrderByUpdatedAtDesc(
                List.of(sparePartId),
                warehouseId
        )).thenReturn(List.of(stock(warehouseId, sparePartId, 5, 10)));
        when(sparePartRepository.findAllByIdInAndIsDeletedFalse(List.of(sparePartId)))
                .thenReturn(List.of(sparePart(sparePartId)));
        when(warehouseRepository.findAllByIdInAndIsDeletedFalse(List.of(warehouseId)))
                .thenReturn(List.of(warehouse(warehouseId)));

        var summary = service.forecast(new SparePartForecastRequest(
                30,
                now,
                null,
                warehouseId,
                null,
                null,
                null,
                false
        ));

        assertThat(summary.items()).hasSize(1);
        assertThat(summary.items().get(0).availableQty()).isZero();
        assertThat(summary.items().get(0).shortageQty()).isEqualTo(2);
    }

    @Test
    void forecastDeniesOtherDepartmentForScopedUser() {
        UUID currentDepartmentId = UUID.randomUUID();
        UUID requestedDepartmentId = UUID.randomUUID();
        lenient().when(scopeAccessService.isScopeAdmin()).thenReturn(false);
        when(scopeAccessService.currentDepartmentIdOrNull()).thenReturn(currentDepartmentId);

        assertThatThrownBy(() -> service.forecast(new SparePartForecastRequest(
                30,
                null,
                null,
                null,
                requestedDepartmentId,
                null,
                null,
                false
        ))).isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("department scope");
    }

    @Test
    void evaluateDoesNotCreateIssueWhenThereIsNoShortage() {
        UUID templateId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        Instant now = Instant.parse("2026-06-04T00:00:00Z");

        when(eventRepository.findForecastCandidates(
                eq(now),
                eq(now.plusSeconds(30L * 24 * 60 * 60)),
                isNull(),
                isNull(),
                isNull(),
                eq(List.of(
                        MaintenanceDueEventStatus.DETECTED,
                        MaintenanceDueEventStatus.AWAITING_APPROVAL,
                        MaintenanceDueEventStatus.TASK_CREATED,
                        MaintenanceDueEventStatus.WORK_ORDER_CREATED
                )),
                eq(List.of(MaintenanceDueStatus.UPCOMING, MaintenanceDueStatus.DUE, MaintenanceDueStatus.OVERDUE))
        )).thenReturn(List.of(event(UUID.randomUUID(), templateId, UUID.randomUUID(), now.plusSeconds(3600))));
        when(requirementRepository.findAllActiveByTemplateIdIn(List.of(templateId)))
                .thenReturn(List.of(requirement(UUID.randomUUID(), templateId, sparePartId, 1)));
        when(stockRepository.findAllBySparePartIdInAndWarehouseIdAndIsDeletedFalseOrderByUpdatedAtDesc(
                List.of(sparePartId),
                warehouseId
        )).thenReturn(List.of(stock(warehouseId, sparePartId, 5, 0)));
        when(sparePartRepository.findAllByIdInAndIsDeletedFalse(List.of(sparePartId)))
                .thenReturn(List.of(sparePart(sparePartId)));
        when(warehouseRepository.findAllByIdInAndIsDeletedFalse(List.of(warehouseId)))
                .thenReturn(List.of(warehouse(warehouseId)));

        var response = service.evaluateAndCreateIssues(new SparePartForecastRequest(
                30,
                now,
                null,
                warehouseId,
                null,
                null,
                null,
                true
        ));

        assertThat(response.createdIssueCount()).isZero();
        assertThat(response.updatedIssueCount()).isZero();
        verify(operationalIssueService, never()).openOrUpdate(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void forecastWithoutWarehouseFilterResolvesRealWarehouseFromEquipmentDepartment() {
        UUID templateId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID resolvedWarehouseId = UUID.randomUUID();
        Instant now = Instant.parse("2026-06-04T00:00:00Z");
        Equipment equipment = equipment(equipmentId, departmentId, EquipmentLocationType.DEPARTMENT, null);

        when(eventRepository.findForecastCandidates(
                now,
                now.plusSeconds(30L * 24 * 60 * 60),
                null,
                null,
                null,
                List.of(
                        MaintenanceDueEventStatus.DETECTED,
                        MaintenanceDueEventStatus.AWAITING_APPROVAL,
                        MaintenanceDueEventStatus.TASK_CREATED,
                        MaintenanceDueEventStatus.WORK_ORDER_CREATED
                ),
                List.of(MaintenanceDueStatus.UPCOMING, MaintenanceDueStatus.DUE, MaintenanceDueStatus.OVERDUE)
        )).thenReturn(List.of(event(UUID.randomUUID(), templateId, equipmentId, now.plusSeconds(3600))));
        when(requirementRepository.findAllActiveByTemplateIdIn(List.of(templateId)))
                .thenReturn(List.of(requirement(UUID.randomUUID(), templateId, sparePartId, 5)));
        when(equipmentRepository.findAllByIdInAndIsDeletedFalse(List.of(equipmentId)))
                .thenReturn(List.of(equipment));
        when(warehouseRepository.findAllByDepartmentIdInAndActiveTrueAndIsDeletedFalse(Set.of(departmentId)))
                .thenReturn(List.of(warehouse(resolvedWarehouseId, departmentId)));
        when(stockRepository.findAllBySparePartIdInAndIsDeletedFalseOrderByUpdatedAtDesc(List.of(sparePartId)))
                .thenReturn(List.of(stock(resolvedWarehouseId, sparePartId, 2, 0)));
        when(sparePartRepository.findAllByIdInAndIsDeletedFalse(List.of(sparePartId)))
                .thenReturn(List.of(sparePart(sparePartId)));
        when(warehouseRepository.findAllByIdInAndIsDeletedFalse(List.of(resolvedWarehouseId)))
                .thenReturn(List.of(warehouse(resolvedWarehouseId, departmentId)));

        var summary = service.forecast(new SparePartForecastRequest(30, now, null, null, null, null, null, false));

        assertThat(summary.items()).hasSize(1);
        var item = summary.items().getFirst();
        assertThat(item.warehouseId()).isEqualTo(resolvedWarehouseId);
        assertThat(item.warehouseName()).isEqualTo("Main warehouse");
        assertThat(item.departmentId()).isNull();
        assertThat(item.departmentName()).isNull();
        assertThat(item.availableQty()).isEqualTo(2);
        assertThat(item.shortageQty()).isEqualTo(3);
    }

    @Test
    void forecastWithoutWarehouseFilterPrefersEquipmentCurrentWarehouseWhenPhysicallyStored() {
        UUID templateId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        UUID currentWarehouseId = UUID.randomUUID();
        Instant now = Instant.parse("2026-06-04T00:00:00Z");
        Equipment equipment = equipment(equipmentId, null, EquipmentLocationType.WAREHOUSE, currentWarehouseId);

        when(eventRepository.findForecastCandidates(
                now,
                now.plusSeconds(30L * 24 * 60 * 60),
                null,
                null,
                null,
                List.of(
                        MaintenanceDueEventStatus.DETECTED,
                        MaintenanceDueEventStatus.AWAITING_APPROVAL,
                        MaintenanceDueEventStatus.TASK_CREATED,
                        MaintenanceDueEventStatus.WORK_ORDER_CREATED
                ),
                List.of(MaintenanceDueStatus.UPCOMING, MaintenanceDueStatus.DUE, MaintenanceDueStatus.OVERDUE)
        )).thenReturn(List.of(event(UUID.randomUUID(), templateId, equipmentId, now.plusSeconds(3600))));
        when(requirementRepository.findAllActiveByTemplateIdIn(List.of(templateId)))
                .thenReturn(List.of(requirement(UUID.randomUUID(), templateId, sparePartId, 3)));
        when(equipmentRepository.findAllByIdInAndIsDeletedFalse(List.of(equipmentId)))
                .thenReturn(List.of(equipment));
        when(stockRepository.findAllBySparePartIdInAndIsDeletedFalseOrderByUpdatedAtDesc(List.of(sparePartId)))
                .thenReturn(List.of(stock(currentWarehouseId, sparePartId, 1, 0)));
        when(sparePartRepository.findAllByIdInAndIsDeletedFalse(List.of(sparePartId)))
                .thenReturn(List.of(sparePart(sparePartId)));
        when(warehouseRepository.findAllByIdInAndIsDeletedFalse(List.of(currentWarehouseId)))
                .thenReturn(List.of(warehouse(currentWarehouseId)));

        var summary = service.forecast(new SparePartForecastRequest(30, now, null, null, null, null, null, false));

        assertThat(summary.items()).hasSize(1);
        assertThat(summary.items().getFirst().warehouseId()).isEqualTo(currentWarehouseId);
        verify(warehouseRepository, never()).findAllByDepartmentIdInAndActiveTrueAndIsDeletedFalse(any());
    }

    @Test
    void forecastWithoutWarehouseFilterFallsBackToDepartmentWhenMultipleWarehousesServeIt() {
        UUID templateId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID warehouseA = UUID.randomUUID();
        UUID warehouseB = UUID.randomUUID();
        Instant now = Instant.parse("2026-06-04T00:00:00Z");
        Equipment equipment = equipment(equipmentId, departmentId, EquipmentLocationType.DEPARTMENT, null);
        Department department = department(departmentId, "Mechanical shop");

        when(eventRepository.findForecastCandidates(
                now,
                now.plusSeconds(30L * 24 * 60 * 60),
                null,
                null,
                null,
                List.of(
                        MaintenanceDueEventStatus.DETECTED,
                        MaintenanceDueEventStatus.AWAITING_APPROVAL,
                        MaintenanceDueEventStatus.TASK_CREATED,
                        MaintenanceDueEventStatus.WORK_ORDER_CREATED
                ),
                List.of(MaintenanceDueStatus.UPCOMING, MaintenanceDueStatus.DUE, MaintenanceDueStatus.OVERDUE)
        )).thenReturn(List.of(event(UUID.randomUUID(), templateId, equipmentId, now.plusSeconds(3600))));
        when(requirementRepository.findAllActiveByTemplateIdIn(List.of(templateId)))
                .thenReturn(List.of(requirement(UUID.randomUUID(), templateId, sparePartId, 5)));
        when(equipmentRepository.findAllByIdInAndIsDeletedFalse(List.of(equipmentId)))
                .thenReturn(List.of(equipment));
        when(warehouseRepository.findAllByDepartmentIdInAndActiveTrueAndIsDeletedFalse(Set.of(departmentId)))
                .thenReturn(List.of(warehouse(warehouseA, departmentId), warehouse(warehouseB, departmentId)));
        when(stockRepository.findAllBySparePartIdInAndIsDeletedFalseOrderByUpdatedAtDesc(List.of(sparePartId)))
                .thenReturn(List.of(stock(warehouseA, sparePartId, 2, 0), stock(warehouseB, sparePartId, 1, 0)));
        when(sparePartRepository.findAllByIdInAndIsDeletedFalse(List.of(sparePartId)))
                .thenReturn(List.of(sparePart(sparePartId)));
        when(warehouseRepository.findAllByIdInAndIsDeletedFalse(any())).thenReturn(List.of());
        when(departmentRepository.findAllByIdInAndIsDeletedFalse(Set.of(departmentId)))
                .thenReturn(List.of(department));

        var summary = service.forecast(new SparePartForecastRequest(30, now, null, null, null, null, null, false));

        assertThat(summary.items()).hasSize(1);
        var item = summary.items().getFirst();
        assertThat(item.warehouseId()).isNull();
        assertThat(item.warehouseName()).isNull();
        assertThat(item.departmentId()).isEqualTo(departmentId);
        assertThat(item.departmentName()).isEqualTo("Mechanical shop");
        assertThat(item.availableQty()).isEqualTo(3);
        assertThat(item.shortageQty()).isEqualTo(2);
    }

    private MaintenanceDueEvent event(UUID id, UUID templateId, UUID equipmentId, Instant dueAt) {
        MaintenanceDueEvent event = new MaintenanceDueEvent();
        event.setId(id);
        event.setTemplateId(templateId);
        event.setEquipmentId(equipmentId);
        event.setStatus(MaintenanceDueEventStatus.DETECTED);
        event.setDueStatus(MaintenanceDueStatus.UPCOMING);
        event.setTriggerSource(MaintenanceTriggerSource.CALENDAR_JOB);
        event.setDueAt(dueAt);
        event.setCycleKey(id.toString());
        return event;
    }

    private MaintenanceTemplateSparePartRequirement requirement(UUID id, UUID templateId, UUID sparePartId, double quantity) {
        MaintenanceTemplate template = new MaintenanceTemplate();
        template.setId(templateId);
        SparePart sparePart = sparePart(sparePartId);
        MaintenanceTemplateSparePartRequirement requirement = new MaintenanceTemplateSparePartRequirement();
        requirement.setId(id);
        requirement.setTemplate(template);
        requirement.setTemplateId(templateId);
        requirement.setSparePart(sparePart);
        requirement.setSparePartId(sparePartId);
        requirement.setQuantity(quantity);
        requirement.setUnit("pcs");
        requirement.setActive(true);
        return requirement;
    }

    private MaintenanceRegulationSparePartRequirement regulationRequirement(
            UUID id,
            UUID regulationId,
            UUID sparePartId,
            double quantity
    ) {
        MaintenanceRegulation regulation = new MaintenanceRegulation();
        regulation.setId(regulationId);
        SparePart sparePart = sparePart(sparePartId);
        MaintenanceRegulationSparePartRequirement requirement =
                new MaintenanceRegulationSparePartRequirement();
        requirement.setId(id);
        requirement.setRegulation(regulation);
        requirement.setRegulationId(regulationId);
        requirement.setSparePart(sparePart);
        requirement.setSparePartId(sparePartId);
        requirement.setQuantity(quantity);
        requirement.setUnit("pcs");
        requirement.setActive(true);
        return requirement;
    }

    private WarehouseStock stock(UUID warehouseId, UUID sparePartId, double quantity, double reservedQty) {
        WarehouseStock stock = new WarehouseStock();
        stock.setId(UUID.randomUUID());
        stock.setWarehouseId(warehouseId);
        stock.setSparePartId(sparePartId);
        stock.setQuantity(quantity);
        stock.setReservedQty(reservedQty);
        stock.setMinQty(0);
        wmsSnapshots.put(
                new LegacyStockProjectionService.StockKey(warehouseId, sparePartId),
                new WmsStockSnapshot(warehouseId, sparePartId,
                        BigDecimal.valueOf(quantity), BigDecimal.valueOf(reservedQty))
        );
        return stock;
    }

    private SparePart sparePart(UUID id) {
        SparePart sparePart = new SparePart();
        sparePart.setId(id);
        sparePart.setCode("BRG-001");
        sparePart.setName("Bearing");
        sparePart.setUnit("pcs");
        return sparePart;
    }

    private Warehouse warehouse(UUID id) {
        Warehouse warehouse = new Warehouse();
        warehouse.setId(id);
        warehouse.setCode("WH-1");
        warehouse.setName("Main warehouse");
        return warehouse;
    }

    private Warehouse warehouse(UUID id, UUID departmentId) {
        Warehouse warehouse = warehouse(id);
        warehouse.setDepartmentId(departmentId);
        warehouse.setActive(true);
        return warehouse;
    }

    private Equipment equipment(UUID id) {
        Equipment equipment = new Equipment();
        equipment.setId(id);
        equipment.setCode("EQ-1");
        equipment.setName("Pump");
        return equipment;
    }

    private Equipment equipment(UUID id,
                                UUID departmentId,
                                EquipmentLocationType locationType,
                                UUID currentWarehouseId) {
        Equipment equipment = equipment(id);
        equipment.setDepartmentId(departmentId);
        equipment.setCurrentLocationType(locationType);
        equipment.setCurrentWarehouseId(currentWarehouseId);
        return equipment;
    }

    private Department department(UUID id, String name) {
        Department department = new Department();
        department.setId(id);
        department.setName(name);
        return department;
    }
}
