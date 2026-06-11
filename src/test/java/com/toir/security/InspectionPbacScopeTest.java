package com.toir.security;

import com.toir.dto.inspection.InspectionRoundResultRequest;
import com.toir.dto.inspection.InspectionRouteRequest;
import com.toir.entity.inspection.InspectionCheckpoint;
import com.toir.entity.inspection.InspectionRound;
import com.toir.entity.inspection.InspectionRoute;
import com.toir.enums.InspectionRoundStatus;
import com.toir.exception.RestException;
import com.toir.repository.defects.DefectRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.inspection.InspectionCheckpointRepository;
import com.toir.repository.inspection.InspectionRoundRepository;
import com.toir.repository.inspection.InspectionRouteRepository;
import com.toir.repository.repair.RepairRequestRepository;
import com.toir.service.InspectionService;
import com.toir.service.UnitOfMeasurementService;
import com.toir.util.AuditBuilderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InspectionPbacScopeTest {

    @Mock
    InspectionRouteRepository routeRepo;

    @Mock
    InspectionCheckpointRepository checkpointRepo;

    @Mock
    InspectionRoundRepository roundRepo;

    @Mock
    DefectRepository defectRepo;

    @Mock
    RepairRequestRepository repairRequestRepository;

    @Mock
    EquipmentRepository equipmentRepository;

    @Mock
    UnitOfMeasurementService unitOfMeasurementService;

    @Mock
    AuditBuilderService auditBuilderService;

    @Mock
    ScopeAccessService scopeAccessService;

    @InjectMocks
    InspectionService service;

    @BeforeEach
    void setUp() {
        lenient().when(scopeAccessService.isScopeAdmin()).thenReturn(false);
        lenient().when(routeRepo.maxSequenceByCodePrefix(anyString())).thenReturn(0L);
        lenient().when(routeRepo.existsByCodeAndIsDeletedFalse(anyString())).thenReturn(false);
        lenient().when(checkpointRepo.findByIdAndIsDeletedFalse(any(UUID.class))).thenAnswer(invocation -> {
            UUID checkpointId = invocation.getArgument(0);
            InspectionCheckpoint checkpoint = new InspectionCheckpoint();
            ReflectionTestUtils.setField(checkpoint, "id", checkpointId);
            checkpoint.setTitle("Checkpoint");
            return Optional.of(checkpoint);
        });
    }

    @Test
    void routeListClampsRequestedDepartmentToCurrentDepartment() {
        UUID requestedDepartmentId = UUID.randomUUID();
        UUID currentDepartmentId = UUID.randomUUID();
        when(scopeAccessService.enforceDepartmentScope(requestedDepartmentId)).thenReturn(currentDepartmentId);
        when(scopeAccessService.currentDepartmentIdOrNull()).thenReturn(currentDepartmentId);
        when(routeRepo.findAllByDepartmentIdAndIsDeletedFalseOrderByUpdatedAtDesc(currentDepartmentId, true, "pump"))
                .thenReturn(List.of(route(UUID.randomUUID(), currentDepartmentId)));

        var result = service.findRoutes(requestedDepartmentId, true, "pump");

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().departmentId()).isEqualTo(currentDepartmentId);
        verify(routeRepo).findAllByDepartmentIdAndIsDeletedFalseOrderByUpdatedAtDesc(currentDepartmentId, true, "pump");
    }

    @Test
    void routeListWithoutDepartmentForNonAdminUsesCurrentDepartment() {
        UUID currentDepartmentId = UUID.randomUUID();
        when(scopeAccessService.enforceDepartmentScope(null)).thenReturn(currentDepartmentId);
        when(scopeAccessService.currentDepartmentIdOrNull()).thenReturn(currentDepartmentId);
        when(routeRepo.findAllByDepartmentIdAndIsDeletedFalseOrderByUpdatedAtDesc(currentDepartmentId, null, null))
                .thenReturn(List.of(route(UUID.randomUUID(), currentDepartmentId)));

        var result = service.findRoutes(null, null, null);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().departmentId()).isEqualTo(currentDepartmentId);
        verify(routeRepo).findAllByDepartmentIdAndIsDeletedFalseOrderByUpdatedAtDesc(currentDepartmentId, null, null);
    }

    @Test
    void routeListWithoutDepartmentForNonAdminWithoutDepartmentIsDenied() {
        when(scopeAccessService.enforceDepartmentScope(null)).thenReturn(null);
        when(scopeAccessService.currentDepartmentIdOrNull()).thenReturn(null);

        assertThatThrownBy(() -> service.findRoutes(null, null, null))
                .isInstanceOf(AccessDeniedException.class);

        verify(routeRepo, never()).findAllByDepartmentIdAndIsDeletedFalseOrderByUpdatedAtDesc(any(), any(), any());
    }

    @Test
    void systemAdminCanRequestGlobalRouteList() {
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(scopeAccessService.enforceDepartmentScope(null)).thenReturn(null);
        when(routeRepo.findAllByDepartmentIdAndIsDeletedFalseOrderByUpdatedAtDesc(null, null, null))
                .thenReturn(List.of());

        var result = service.findRoutes(null, null, null);

        assertThat(result).isEmpty();
        verify(routeRepo).findAllByDepartmentIdAndIsDeletedFalseOrderByUpdatedAtDesc(null, null, null);
    }

    @Test
    void routeDetailAllowsSameDepartment() {
        UUID routeId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        when(routeRepo.findByIdAndIsDeletedFalse(routeId)).thenReturn(Optional.of(route(routeId, departmentId)));
        when(scopeAccessService.canAccessDepartment(departmentId)).thenReturn(true);

        var result = service.getRoute(routeId);

        assertThat(result.id()).isEqualTo(routeId);
        assertThat(result.departmentId()).isEqualTo(departmentId);
    }

    @Test
    void routeDetailDeniesDifferentDepartment() {
        UUID routeId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        when(routeRepo.findByIdAndIsDeletedFalse(routeId)).thenReturn(Optional.of(route(routeId, departmentId)));
        denyDepartment(departmentId);

        assertThatThrownBy(() -> service.getRoute(routeId))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void systemAdminAndWildcardCanReadRouteInDifferentDepartment() {
        UUID routeId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(routeRepo.findByIdAndIsDeletedFalse(routeId)).thenReturn(Optional.of(route(routeId, departmentId)));

        var result = service.getRoute(routeId);

        assertThat(result.id()).isEqualTo(routeId);
        assertThat(result.departmentId()).isEqualTo(departmentId);
    }

    @Test
    void missingRouteRemainsNotFound() {
        UUID routeId = UUID.randomUUID();
        when(routeRepo.findByIdAndIsDeletedFalse(routeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getRoute(routeId))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Inspection route not found");
    }

    @Test
    void createRouteAllowsOwnDepartmentAndDeniesDifferentDepartment() {
        UUID ownDepartmentId = UUID.randomUUID();
        UUID otherDepartmentId = UUID.randomUUID();
        when(scopeAccessService.canAccessDepartment(ownDepartmentId)).thenReturn(true);
        denyDepartment(otherDepartmentId);
        lenient().when(routeRepo.existsByCodeAndIsDeletedFalse("IR-OWN")).thenReturn(false);
        when(routeRepo.save(any(InspectionRoute.class))).thenAnswer(invocation -> {
            InspectionRoute saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
            return saved;
        });

        assertThat(service.createRoute(routeRequest("IR-OWN", ownDepartmentId)).departmentId())
                .isEqualTo(ownDepartmentId);
        assertThatThrownBy(() -> service.createRoute(routeRequest("IR-OTHER", otherDepartmentId)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void updateAndDeleteRouteAllowOwnDepartment() {
        UUID routeId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        InspectionRoute route = route(routeId, departmentId);
        when(routeRepo.findByIdAndIsDeletedFalse(routeId)).thenReturn(Optional.of(route));
        when(scopeAccessService.canAccessDepartment(departmentId)).thenReturn(true);
        when(routeRepo.save(any(InspectionRoute.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.updateRoute(routeId, routeRequest(route.getCode(), departmentId)).departmentId())
                .isEqualTo(departmentId);
        service.deleteRoute(routeId);

        assertThat(route.isDeleted()).isTrue();
        verify(routeRepo, org.mockito.Mockito.times(2)).save(route);
    }

    @Test
    void updateAndDeleteRouteRequireRouteDepartmentScope() {
        UUID routeId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        when(routeRepo.findByIdAndIsDeletedFalse(routeId)).thenReturn(Optional.of(route(routeId, departmentId)));
        denyDepartment(departmentId);

        assertThatThrownBy(() -> service.updateRoute(routeId, routeRequest("IR-002", departmentId)))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.deleteRoute(routeId))
                .isInstanceOf(AccessDeniedException.class);

        verify(routeRepo, never()).save(any(InspectionRoute.class));
    }

    @Test
    void systemAdminAndWildcardCanMutateRouteInDifferentDepartment() {
        UUID routeId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        InspectionRoute route = route(routeId, departmentId);
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(routeRepo.findByIdAndIsDeletedFalse(routeId)).thenReturn(Optional.of(route));
        when(routeRepo.save(any(InspectionRoute.class))).thenAnswer(invocation -> {
            InspectionRoute saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
            }
            return saved;
        });

        assertThat(service.createRoute(routeRequest("IR-ADMIN", departmentId)).departmentId())
                .isEqualTo(departmentId);
        assertThat(service.updateRoute(routeId, routeRequest(route.getCode(), departmentId)).departmentId())
                .isEqualTo(departmentId);
        service.deleteRoute(routeId);

        assertThat(route.isDeleted()).isTrue();
        verify(routeRepo, org.mockito.Mockito.times(3)).save(any(InspectionRoute.class));
    }

    @Test
    void roundDetailAllowsRouteDepartmentOrPerformer() {
        UUID departmentId = UUID.randomUUID();
        UUID performerId = UUID.randomUUID();
        InspectionRound byDepartment = round(UUID.randomUUID(), route(UUID.randomUUID(), departmentId), UUID.randomUUID());
        InspectionRound byPerformer = round(UUID.randomUUID(), route(UUID.randomUUID(), UUID.randomUUID()), performerId);
        when(roundRepo.findByIdAndIsDeletedFalse(byDepartment.getId())).thenReturn(Optional.of(byDepartment));
        when(roundRepo.findByIdAndIsDeletedFalse(byPerformer.getId())).thenReturn(Optional.of(byPerformer));
        when(scopeAccessService.canAccessDepartment(departmentId)).thenReturn(true);
        when(scopeAccessService.canAccessAssignedUser(performerId)).thenReturn(true);

        assertThat(service.getRound(byDepartment.getId()).id()).isEqualTo(byDepartment.getId());
        assertThat(service.getRound(byPerformer.getId()).id()).isEqualTo(byPerformer.getId());
    }

    @Test
    void roundDetailDeniesOutOfScopeRoundAndMissingRoundRemainsNotFound() {
        UUID routeDepartmentId = UUID.randomUUID();
        InspectionRound round = round(UUID.randomUUID(), route(UUID.randomUUID(), routeDepartmentId), UUID.randomUUID());
        when(roundRepo.findByIdAndIsDeletedFalse(round.getId())).thenReturn(Optional.of(round));
        when(scopeAccessService.canAccessDepartment(routeDepartmentId)).thenReturn(false);
        when(scopeAccessService.canAccessAssignedUser(round.getPerformedBy())).thenReturn(false);

        assertThatThrownBy(() -> service.getRound(round.getId()))
                .isInstanceOf(AccessDeniedException.class);

        UUID missingRoundId = UUID.randomUUID();
        when(roundRepo.findByIdAndIsDeletedFalse(missingRoundId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getRound(missingRoundId))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Inspection round not found");
    }

    @Test
    void systemAdminAndWildcardCanReadRoundInDifferentDepartment() {
        InspectionRound round = round(UUID.randomUUID(), route(UUID.randomUUID(), UUID.randomUUID()), UUID.randomUUID());
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(roundRepo.findByIdAndIsDeletedFalse(round.getId())).thenReturn(Optional.of(round));

        assertThat(service.getRound(round.getId()).id()).isEqualTo(round.getId());
    }

    @Test
    void roundListFiltersOutOutOfScopeRounds() {
        UUID departmentId = UUID.randomUUID();
        UUID performerId = UUID.randomUUID();
        InspectionRound inDepartment = round(UUID.randomUUID(), route(UUID.randomUUID(), departmentId), UUID.randomUUID());
        InspectionRound byPerformer = round(UUID.randomUUID(), route(UUID.randomUUID(), UUID.randomUUID()), performerId);
        InspectionRound outOfScope = round(UUID.randomUUID(), route(UUID.randomUUID(), UUID.randomUUID()), UUID.randomUUID());
        when(roundRepo.findAllByRouteIdAndIsDeletedFalseOrderByStartedAtDesc(null, null, null))
                .thenReturn(List.of(inDepartment, byPerformer, outOfScope));
        when(scopeAccessService.canAccessDepartment(departmentId)).thenReturn(true);
        when(scopeAccessService.canAccessAssignedUser(performerId)).thenReturn(true);
        when(scopeAccessService.canAccessAssignedUser(outOfScope.getPerformedBy())).thenReturn(false);

        var result = service.listRounds(null, null, null);

        assertThat(result).extracting("id")
                .containsExactly(inDepartment.getId(), byPerformer.getId());
    }

    @Test
    void startRoundRequiresRouteScopeBeforeCreatingRound() {
        UUID routeId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        when(routeRepo.findByIdAndIsDeletedFalse(routeId)).thenReturn(Optional.of(route(routeId, departmentId)));
        denyDepartment(departmentId);

        assertThatThrownBy(() -> service.startRound(routeId, UUID.randomUUID()))
                .isInstanceOf(AccessDeniedException.class);

        verify(roundRepo, never()).save(any(InspectionRound.class));
    }

    @Test
    void startRoundAllowsRouteInCurrentDepartment() {
        UUID routeId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID performerId = UUID.randomUUID();
        when(routeRepo.findByIdAndIsDeletedFalse(routeId)).thenReturn(Optional.of(route(routeId, departmentId)));
        when(scopeAccessService.canAccessDepartment(departmentId)).thenReturn(true);
        when(roundRepo.save(any(InspectionRound.class))).thenAnswer(invocation -> {
            InspectionRound saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
            return saved;
        });

        var result = service.startRound(routeId, performerId);

        assertThat(result.routeId()).isEqualTo(routeId);
        assertThat(result.performedBy()).isEqualTo(performerId);
        verify(roundRepo).save(any(InspectionRound.class));
    }

    @Test
    void resultCompleteAndCancelRequireRoundScope() {
        UUID departmentId = UUID.randomUUID();
        InspectionRound round = round(UUID.randomUUID(), route(UUID.randomUUID(), departmentId), UUID.randomUUID());
        round.setStatus(InspectionRoundStatus.IN_PROGRESS);
        when(roundRepo.findByIdAndIsDeletedFalse(round.getId())).thenReturn(Optional.of(round));
        when(scopeAccessService.canAccessDepartment(departmentId)).thenReturn(false);
        when(scopeAccessService.canAccessAssignedUser(round.getPerformedBy())).thenReturn(false);

        assertThatThrownBy(() -> service.recordResult(round.getId(), resultRequest()))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.completeRound(round.getId(), "done"))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.cancelRound(round.getId(), "cancel"))
                .isInstanceOf(AccessDeniedException.class);

        verify(roundRepo, never()).save(any(InspectionRound.class));
    }

    @Test
    void resultCompleteAndCancelAllowRoundInCurrentDepartment() {
        UUID departmentId = UUID.randomUUID();
        InspectionRound resultRound = round(UUID.randomUUID(), route(UUID.randomUUID(), departmentId), UUID.randomUUID());
        InspectionRound completeRound = round(UUID.randomUUID(), route(UUID.randomUUID(), departmentId), UUID.randomUUID());
        InspectionRound cancelRound = round(UUID.randomUUID(), route(UUID.randomUUID(), departmentId), UUID.randomUUID());
        resultRound.setStatus(InspectionRoundStatus.IN_PROGRESS);
        completeRound.setStatus(InspectionRoundStatus.IN_PROGRESS);
        cancelRound.setStatus(InspectionRoundStatus.IN_PROGRESS);
        when(scopeAccessService.canAccessDepartment(departmentId)).thenReturn(true);
        when(roundRepo.findByIdAndIsDeletedFalse(resultRound.getId())).thenReturn(Optional.of(resultRound));
        when(roundRepo.findByIdAndIsDeletedFalse(completeRound.getId())).thenReturn(Optional.of(completeRound));
        when(roundRepo.findByIdAndIsDeletedFalse(cancelRound.getId())).thenReturn(Optional.of(cancelRound));
        when(roundRepo.save(any(InspectionRound.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.recordResult(resultRound.getId(), resultRequest()).roundId()).isEqualTo(resultRound.getId());
        assertThat(service.completeRound(completeRound.getId(), "done").status())
                .isEqualTo(InspectionRoundStatus.COMPLETED);
        assertThat(service.cancelRound(cancelRound.getId(), "cancel").status())
                .isEqualTo(InspectionRoundStatus.CANCELLED);
    }

    @Test
    void performerCanSubmitResultForOwnRound() {
        UUID performerId = UUID.randomUUID();
        InspectionRound round = round(UUID.randomUUID(), route(UUID.randomUUID(), UUID.randomUUID()), performerId);
        round.setStatus(InspectionRoundStatus.IN_PROGRESS);
        when(roundRepo.findByIdAndIsDeletedFalse(round.getId())).thenReturn(Optional.of(round));
        when(scopeAccessService.canAccessAssignedUser(performerId)).thenReturn(true);
        when(roundRepo.save(any(InspectionRound.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.recordResult(round.getId(), resultRequest());

        assertThat(result.roundId()).isEqualTo(round.getId());
        verify(roundRepo).save(round);
    }

    @Test
    void nonPerformerCannotUsePerformerOnlyScope() {
        UUID performerId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        InspectionRound round = round(UUID.randomUUID(), route(UUID.randomUUID(), departmentId), performerId);
        when(roundRepo.findByIdAndIsDeletedFalse(round.getId())).thenReturn(Optional.of(round));
        when(scopeAccessService.canAccessDepartment(departmentId)).thenReturn(false);
        when(scopeAccessService.canAccessAssignedUser(performerId)).thenReturn(false);

        assertThatThrownBy(() -> service.getRound(round.getId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    private void denyDepartment(UUID departmentId) {
        when(scopeAccessService.canAccessDepartment(departmentId)).thenReturn(false);
    }

    private InspectionRoute route(UUID id, UUID departmentId) {
        InspectionRoute route = new InspectionRoute();
        ReflectionTestUtils.setField(route, "id", id);
        route.setCode("IR-" + id.toString().substring(0, 8));
        route.setName("Inspection route");
        route.setDepartmentId(departmentId);
        route.setFrequency("DAILY");
        route.setActive(true);
        return route;
    }

    private InspectionRound round(UUID id, InspectionRoute route, UUID performedBy) {
        InspectionRound round = new InspectionRound();
        ReflectionTestUtils.setField(round, "id", id);
        round.setRoute(route);
        round.setPerformedBy(performedBy);
        round.setStatus(InspectionRoundStatus.IN_PROGRESS);
        return round;
    }

    private InspectionRouteRequest routeRequest(String code, UUID departmentId) {
        return new InspectionRouteRequest(
                null,
                "Route",
                departmentId,
                "DAILY",
                30,
                "desc",
                true,
                List.of()
        );
    }

    private InspectionRoundResultRequest resultRequest() {
        return new InspectionRoundResultRequest(UUID.randomUUID(), "OK", null, null, "ok", null);
    }
}
