package com.toir.service.planning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toir.dto.pprplanning.PprPlanningSelectionRequest;
import com.toir.dto.maintenanceschedule.MaintenanceScheduleCalculationRequest;
import com.toir.dto.maintenanceschedule.MaintenanceSchedulePreviewItem;
import com.toir.dto.maintenanceschedule.MaintenanceSchedulePreviewResponse;
import com.toir.dto.maintenanceschedule.MaintenanceSchedulePreviewSummary;
import com.toir.entity.PprPlan;
import com.toir.entity.maintenance.MaintenanceScheduleCalculationItem;
import com.toir.entity.planning.PprPlanningSession;
import com.toir.entity.planning.PprPlanningVariant;
import com.toir.entity.planning.PprPlanningVariantItem;
import com.toir.enums.MaintenanceKind;
import com.toir.enums.MaintenanceScheduleAnchorMode;
import com.toir.enums.MaintenanceScheduleAnchorSource;
import com.toir.enums.MaintenanceScheduleScopeType;
import com.toir.enums.PriorityLevel;
import com.toir.enums.planning.PprPlanningSessionStatus;
import com.toir.enums.planning.PprPlanningVariantStatus;
import com.toir.repository.planning.PprPlanningSessionRepository;
import com.toir.repository.planning.PprPlanningVariantItemRepository;
import com.toir.repository.planning.PprPlanningVariantRepository;
import com.toir.service.maintanance.MaintenanceScheduleService;
import com.toir.service.maintanance.MaintenanceScheduleSnapshotDraftFactory;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PprPlanningVariantServiceTest {

    @Mock
    PprPlanningSessionRepository sessionRepository;
    @Mock
    PprPlanningVariantRepository variantRepository;
    @Mock
    PprPlanningVariantItemRepository itemRepository;
    @Mock
    MaintenanceScheduleService scheduleService;
    @Mock
    MaintenanceScheduleSnapshotDraftFactory snapshotDraftFactory;

    private PprPlanningVariantService service;

    @BeforeEach
    void setUp() {
        service = new PprPlanningVariantService(
                sessionRepository,
                variantRepository,
                itemRepository,
                scheduleService,
                snapshotDraftFactory,
                new PprVariantContentHasher(),
                new ObjectMapper().findAndRegisterModules());
    }

    @Test
    void calculateCreatesNextImmutableRevision() {
        UUID sessionId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        PprPlanningSession session = session(sessionId);
        session.setStatus(PprPlanningSessionStatus.REJECTED);
        session.setSelectedVariantId(variantId);
        session.setSelectedVariantRevision(1L);
        session.setSelectedVariantHash("f".repeat(64));
        session.setSelectedVariantHashVersion(1);
        session.setApprovalRequestId(UUID.randomUUID());
        PprPlanningVariant variant = variant(variantId, session);
        MaintenanceScheduleCalculationRequest request = request(session);
        MaintenanceSchedulePreviewItem previewItem = previewItem();
        MaintenanceScheduleCalculationItem calculationItem = calculationItem(previewItem);

        when(sessionRepository.findByIdAndIsDeletedFalseForUpdate(sessionId))
                .thenReturn(Optional.of(session));
        when(variantRepository.findByIdAndSessionIdAndIsDeletedFalse(
                variantId, sessionId))
                .thenReturn(Optional.of(variant));
        when(scheduleService.preview(request.toPreviewRequest()))
                .thenReturn(new MaintenanceSchedulePreviewResponse(
                        List.of(previewItem),
                        new MaintenanceSchedulePreviewSummary(1, 1, 0, 0)));
        when(snapshotDraftFactory.create(any(PprPlan.class), eq(1L), eq(List.of(previewItem))))
                .thenReturn(List.of(calculationItem));
        when(variantRepository.saveAndFlush(any(PprPlanningVariant.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PprPlanningVariant calculated = service.calculate(sessionId, variantId, request);

        assertThat(calculated.getRevision()).isEqualTo(1);
        assertThat(calculated.getStatus()).isEqualTo(PprPlanningVariantStatus.CALCULATED);
        assertThat(calculated.getContentHash()).matches("[0-9a-f]{64}");
        assertThat(calculated.getTaskCount()).isEqualTo(1);
        assertThat(session.getStatus()).isEqualTo(PprPlanningSessionStatus.READY_FOR_SELECTION);
        assertThat(session.getSelectedVariantId()).isNull();
        assertThat(session.getSelectedVariantRevision()).isNull();
        assertThat(session.getSelectedVariantHash()).isNull();
        assertThat(session.getApprovalRequestId()).isNull();
        verify(itemRepository).saveAll(org.mockito.ArgumentMatchers.argThat(items -> {
            List<PprPlanningVariantItem> persisted = new java.util.ArrayList<>();
            items.forEach(persisted::add);
            return persisted.size() == 1
                    && persisted.getFirst().getRevision() == 1
                    && persisted.getFirst().getWorkOrderLeadDays() == 7
                    && persisted.getFirst().getRequiredEvidenceTypes().contains(
                            com.toir.enums.CompletionEvidenceType.AFTER_PHOTO);
        }));
    }

    @Test
    void selectBindsExactCalculatedRevisionAndHash() {
        UUID sessionId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        PprPlanningSession session = session(sessionId);
        session.setStatus(PprPlanningSessionStatus.READY_FOR_SELECTION);
        PprPlanningVariant selected = variant(variantId, session);
        selected.setRevision(2);
        selected.setContentHash("a".repeat(64));
        selected.setStatus(PprPlanningVariantStatus.CALCULATED);
        PprPlanningVariant alternative = variant(UUID.randomUUID(), session);
        alternative.setRevision(1);
        alternative.setContentHash("b".repeat(64));
        alternative.setStatus(PprPlanningVariantStatus.CALCULATED);

        when(sessionRepository.findByIdAndIsDeletedFalseForUpdate(sessionId))
                .thenReturn(Optional.of(session));
        when(variantRepository.findByIdAndSessionIdAndIsDeletedFalse(variantId, sessionId))
                .thenReturn(Optional.of(selected));
        when(variantRepository.findAllBySessionIdAndIsDeletedFalseOrderByCreatedAtAsc(sessionId))
                .thenReturn(List.of(selected, alternative));
        when(sessionRepository.save(any(PprPlanningSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PprPlanningSession result = service.select(
                sessionId,
                variantId,
                new PprPlanningSelectionRequest(variantId, 2, "a".repeat(64)));

        assertThat(result.getSelectedVariantId()).isEqualTo(variantId);
        assertThat(result.getStatus()).isEqualTo(PprPlanningSessionStatus.SELECTED);
        assertThat(selected.getStatus()).isEqualTo(PprPlanningVariantStatus.SELECTED);
        assertThat(alternative.getStatus()).isEqualTo(PprPlanningVariantStatus.NOT_SELECTED);
        verify(variantRepository).saveAll(List.of(selected, alternative));
    }

    private static PprPlanningSession session(UUID id) {
        PprPlanningSession session = new PprPlanningSession();
        session.setId(id);
        session.setName("Annual 2027");
        session.setYear(2027);
        session.setDepartmentId(UUID.randomUUID());
        session.setStartDate(LocalDate.of(2027, 1, 1));
        session.setEndDate(LocalDate.of(2027, 12, 31));
        session.setStatus(PprPlanningSessionStatus.DRAFT);
        return session;
    }

    private static PprPlanningVariant variant(UUID id, PprPlanningSession session) {
        PprPlanningVariant variant = new PprPlanningVariant();
        variant.setId(id);
        variant.setSession(session);
        variant.setName("Base");
        variant.setRevision(0);
        variant.setHashVersion(1);
        variant.setStatus(PprPlanningVariantStatus.DRAFT);
        return variant;
    }

    private static MaintenanceScheduleCalculationRequest request(PprPlanningSession session) {
        return new MaintenanceScheduleCalculationRequest(
                "Base",
                "Initial variant",
                session.getStartDate(),
                session.getEndDate(),
                MaintenanceScheduleScopeType.EQUIPMENT,
                List.of(UUID.randomUUID()),
                List.of(),
                session.getDepartmentId(),
                UUID.randomUUID(),
                MaintenanceScheduleAnchorMode.CURRENT,
                false,
                Set.of(),
                null);
    }

    private static MaintenanceSchedulePreviewItem previewItem() {
        return new MaintenanceSchedulePreviewItem(
                UUID.randomUUID(),
                "EQ-1",
                "Pump",
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Monthly service",
                MaintenanceKind.INSPECTION,
                com.toir.enums.PeriodicityUnit.MONTH,
                1,
                LocalDate.of(2027, 2, 1),
                MaintenanceScheduleAnchorSource.EXISTING_DUE_DATE,
                2.0,
                false);
    }

    private static MaintenanceScheduleCalculationItem calculationItem(
            MaintenanceSchedulePreviewItem preview) {
        return MaintenanceScheduleCalculationItem.builder()
                .calculationRevision(1L)
                .sourceItemKey("a".repeat(64))
                .sourceItemKeyVersion(1)
                .equipmentId(preview.equipmentId())
                .regulationId(preview.regulationId())
                .maintenanceRuleId(preview.equipmentMaintenanceRuleId())
                .maintenanceType(preview.maintenanceKind())
                .cycleOrdinal(1L)
                .plannedDate(preview.plannedDate())
                .scheduledStart(LocalDateTime.of(2027, 2, 1, 8, 0))
                .scheduledEnd(LocalDateTime.of(2027, 2, 1, 10, 0))
                .dueDate(LocalDateTime.of(2027, 2, 1, 8, 0))
                .normativeLaborHours(BigDecimal.valueOf(2))
                .priority(PriorityLevel.MEDIUM)
                .equipmentCodeSnapshot("EQ-1")
                .equipmentNameSnapshot("Pump")
                .maintenanceRuleNameSnapshot("Monthly service")
                .taskTitleSnapshot("Monthly service — EQ-1")
                .requiredEvidenceTypes(Set.of(com.toir.enums.CompletionEvidenceType.AFTER_PHOTO))
                .build();
    }
}
