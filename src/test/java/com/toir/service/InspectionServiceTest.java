package com.toir.service;

import com.toir.dto.inspection.InspectionRoundDto;
import com.toir.dto.inspection.InspectionRoundResultDto;
import com.toir.dto.inspection.InspectionRoundResultRequest;
import com.toir.dto.inspection.InspectionRouteDto;
import com.toir.dto.inspection.InspectionRouteRequest;
import com.toir.entity.defects.Defect;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.inspection.InspectionCheckpoint;
import com.toir.entity.inspection.InspectionRound;
import com.toir.entity.inspection.InspectionRoundResult;
import com.toir.entity.inspection.InspectionRoute;
import com.toir.entity.repair.RepairRequest;
import com.toir.enums.DefectStatus;
import com.toir.enums.InspectionRoundStatus;
import com.toir.enums.RequestSource;
import com.toir.enums.RequestStatus;
import com.toir.exception.RestException;
import com.toir.repository.defects.DefectRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.inspection.InspectionCheckpointRepository;
import com.toir.repository.inspection.InspectionRoundRepository;
import com.toir.repository.inspection.InspectionRouteRepository;
import com.toir.repository.repair.RepairRequestRepository;
import com.toir.security.ScopeAccessService;
import com.toir.util.AuditBuilderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InspectionServiceTest {

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
    AuditBuilderService auditBuilderService;

    @Mock
    UnitOfMeasurementService unitOfMeasurementService;

    @Mock
    ScopeAccessService scopeAccessService;

    @InjectMocks
    InspectionService service;

    @BeforeEach
    void setUpScopeAdminBypass() {
        lenient().when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        lenient().when(checkpointRepo.findByIdAndIsDeletedFalse(any(UUID.class))).thenAnswer(invocation -> {
            UUID checkpointId = invocation.getArgument(0);
            return Optional.of(checkpoint(checkpointId, null, null, "Checkpoint"));
        });
    }

    @Test
    void listRoundsWithNullFiltersReturnsEmptyList() {
        when(roundRepo.findAllByRouteIdAndIsDeletedFalseOrderByStartedAtDesc(null, null, null))
                .thenReturn(List.of());

        assertThat(service.listRounds(null, null, null)).isEmpty();
        verify(roundRepo).findAllByRouteIdAndIsDeletedFalseOrderByStartedAtDesc(null, null, null);
    }

    @Test
    void listRoundsWithRouteAndStatusFiltersReturnsData() {
        UUID routeId = UUID.randomUUID();
        UUID performedBy = UUID.randomUUID();
        InspectionRound round = round(UUID.randomUUID(), InspectionRoundStatus.IN_PROGRESS);
        InspectionRoute route = new InspectionRoute();
        ReflectionTestUtils.setField(route, "id", routeId);
        round.setRoute(route);
        round.setPerformedBy(performedBy);
        when(routeRepo.findByIdAndIsDeletedFalse(routeId)).thenReturn(Optional.of(route));
        when(roundRepo.findAllByRouteIdAndIsDeletedFalseOrderByStartedAtDesc(
                eq(routeId),
                eq(performedBy),
                eq(InspectionRoundStatus.IN_PROGRESS)
        )).thenReturn(List.of(round));

        assertThat(service.listRounds(routeId, performedBy, InspectionRoundStatus.IN_PROGRESS))
                .hasSize(1)
                .allSatisfy(item -> {
                    assertThat(item.status()).isEqualTo(InspectionRoundStatus.IN_PROGRESS);
                    assertThat(item.results()).isEmpty();
                });
    }

    @Test
    void inProgressRoundCanRecordResult() {
        UUID roundId = UUID.randomUUID();
        InspectionRound round = round(roundId, InspectionRoundStatus.IN_PROGRESS);
        when(roundRepo.findByIdAndIsDeletedFalse(roundId)).thenReturn(Optional.of(round));
        when(unitOfMeasurementService.normalizeOptionalUnitOrNull("bar")).thenReturn("bar");
        when(roundRepo.save(any(InspectionRound.class))).thenAnswer(invocation -> {
            InspectionRound saved = invocation.getArgument(0);
            for (InspectionRoundResult item : saved.getResults()) {
                if (item.getId() == null) {
                    ReflectionTestUtils.setField(item, "id", UUID.randomUUID());
                }
            }
            return saved;
        });

        InspectionRoundResultDto result = service.recordResult(
                roundId,
                new InspectionRoundResultRequest(UUID.randomUUID(), "OK", 10.0, "bar", "ok", null)
        );

        assertThat(result.roundId()).isEqualTo(roundId);
        assertThat(result.status()).isEqualTo("OK");
        verify(roundRepo).save(any(InspectionRound.class));
    }

    @Test
    void inProgressRoundCanComplete() {
        UUID roundId = UUID.randomUUID();
        InspectionRound round = round(roundId, InspectionRoundStatus.IN_PROGRESS);
        when(roundRepo.findByIdAndIsDeletedFalse(roundId)).thenReturn(Optional.of(round));
        when(roundRepo.save(any(InspectionRound.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InspectionRoundDto result = service.completeRound(roundId, "done");

        assertThat(result.status()).isEqualTo(InspectionRoundStatus.COMPLETED);
        assertThat(result.completedAt()).isNotNull();
        verify(roundRepo).save(any(InspectionRound.class));
    }

    @Test
    void inProgressRoundCanRecordResultWithoutMeasuredUnit() {
        UUID roundId = UUID.randomUUID();
        InspectionRound round = round(roundId, InspectionRoundStatus.IN_PROGRESS);
        when(roundRepo.findByIdAndIsDeletedFalse(roundId)).thenReturn(Optional.of(round));
        when(roundRepo.save(any(InspectionRound.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InspectionRoundResultDto result = service.recordResult(
                roundId,
                new InspectionRoundResultRequest(UUID.randomUUID(), "OK", 11.0, null, "ok", null)
        );

        assertThat(result.status()).isEqualTo("OK");
        assertThat(result.measuredUnit()).isNull();
    }

    @Test
    void failResultCreatesInspectionDefectAndRepairRequestWhenRequiredDataExists() {
        UUID roundId = UUID.randomUUID();
        UUID checkpointId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        UUID performerId = UUID.randomUUID();
        InspectionRound round = roundWithRoute(roundId, departmentId, performerId);
        InspectionCheckpoint checkpoint = checkpoint(checkpointId, equipmentId, locationId, "Pump vibration");
        when(roundRepo.findByIdAndIsDeletedFalse(roundId)).thenReturn(Optional.of(round));
        when(checkpointRepo.findByIdAndIsDeletedFalse(checkpointId)).thenReturn(Optional.of(checkpoint));
        when(defectRepo.findByCodeAndIsDeletedFalse("INS-DEF-" + shortId(roundId) + "-" + shortId(checkpointId)))
                .thenReturn(Optional.empty());
        when(repairRequestRepository.existsByNumberAndIsDeletedFalse("INS-RR-" + shortId(roundId) + "-" + shortId(checkpointId)))
                .thenReturn(false);
        when(repairRequestRepository.save(any(RepairRequest.class))).thenAnswer(invocation -> {
            RepairRequest saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
            return saved;
        });
        when(defectRepo.save(any(Defect.class))).thenAnswer(invocation -> {
            Defect saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
            return saved;
        });
        when(roundRepo.save(any(InspectionRound.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InspectionRoundResultDto result = service.recordResult(
                roundId,
                new InspectionRoundResultRequest(checkpointId, "FAIL", null, null, "bearing noise", null)
        );

        assertThat(result.defectId()).isNotNull();
        assertThat(round.getFindingsCount()).isEqualTo(1);
        assertThat(round.getAlarmCount()).isEqualTo(1);

        ArgumentCaptor<RepairRequest> repairCaptor = ArgumentCaptor.forClass(RepairRequest.class);
        verify(repairRequestRepository).save(repairCaptor.capture());
        RepairRequest repairRequest = repairCaptor.getValue();
        assertThat(repairRequest.getNumber()).isEqualTo("INS-RR-" + shortId(roundId) + "-" + shortId(checkpointId));
        assertThat(repairRequest.getEquipmentId()).isEqualTo(equipmentId);
        assertThat(repairRequest.getDepartmentId()).isEqualTo(departmentId);
        assertThat(repairRequest.getLocationId()).isEqualTo(locationId);
        assertThat(repairRequest.getReporterId()).isEqualTo(performerId);
        assertThat(repairRequest.getSource()).isEqualTo(RequestSource.INSPECTION);
        assertThat(repairRequest.getStatus()).isEqualTo(RequestStatus.OPEN);

        ArgumentCaptor<Defect> defectCaptor = ArgumentCaptor.forClass(Defect.class);
        verify(defectRepo).save(defectCaptor.capture());
        Defect defect = defectCaptor.getValue();
        assertThat(defect.getCode()).isEqualTo("INS-DEF-" + shortId(roundId) + "-" + shortId(checkpointId));
        assertThat(defect.getEquipmentId()).isEqualTo(equipmentId);
        assertThat(defect.getCategory()).isEqualTo("INSPECTION");
        assertThat(defect.getSeverity()).isEqualTo("CRITICAL");
        assertThat(defect.getStatus()).isEqualTo(DefectStatus.OPEN);
        assertThat(defect.getRepairRequestId()).isEqualTo(repairRequest.getId());
        assertThat(defect.getDescription()).contains(roundId.toString(), checkpointId.toString(), "bearing noise");
    }

    @Test
    void failResultCreatesInspectionDefectOnlyWhenRepairRequestRequiredDataIsMissing() {
        UUID roundId = UUID.randomUUID();
        UUID checkpointId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        InspectionRound round = roundWithRoute(roundId, null, UUID.randomUUID());
        InspectionCheckpoint checkpoint = checkpoint(checkpointId, equipmentId, null, "Pump vibration");
        when(roundRepo.findByIdAndIsDeletedFalse(roundId)).thenReturn(Optional.of(round));
        when(checkpointRepo.findByIdAndIsDeletedFalse(checkpointId)).thenReturn(Optional.of(checkpoint));
        when(defectRepo.findByCodeAndIsDeletedFalse("INS-DEF-" + shortId(roundId) + "-" + shortId(checkpointId)))
                .thenReturn(Optional.empty());
        when(defectRepo.save(any(Defect.class))).thenAnswer(invocation -> {
            Defect saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
            return saved;
        });
        when(roundRepo.save(any(InspectionRound.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InspectionRoundResultDto result = service.recordResult(
                roundId,
                new InspectionRoundResultRequest(checkpointId, "FAIL", null, null, "bearing noise", null)
        );

        assertThat(result.defectId()).isNotNull();
        verify(repairRequestRepository, never()).save(any(RepairRequest.class));
        ArgumentCaptor<Defect> defectCaptor = ArgumentCaptor.forClass(Defect.class);
        verify(defectRepo).save(defectCaptor.capture());
        assertThat(defectCaptor.getValue().getRepairRequestId()).isNull();
    }

    @Test
    void repeatedFailForSameRoundAndCheckpointReusesOpenInspectionDefectAndRepairRequest() {
        UUID roundId = UUID.randomUUID();
        UUID checkpointId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        UUID repairRequestId = UUID.randomUUID();
        UUID existingDefectId = UUID.randomUUID();
        InspectionRound round = roundWithRoute(roundId, UUID.randomUUID(), UUID.randomUUID());
        InspectionCheckpoint checkpoint = checkpoint(checkpointId, equipmentId, null, "Pump vibration");
        Defect existing = new Defect();
        ReflectionTestUtils.setField(existing, "id", existingDefectId);
        existing.setCode("INS-DEF-" + shortId(roundId) + "-" + shortId(checkpointId));
        existing.setEquipmentId(equipmentId);
        existing.setCategory("INSPECTION");
        existing.setSeverity("CRITICAL");
        existing.setStatus(DefectStatus.OPEN);
        existing.setRepairRequestId(repairRequestId);
        when(roundRepo.findByIdAndIsDeletedFalse(roundId)).thenReturn(Optional.of(round));
        when(checkpointRepo.findByIdAndIsDeletedFalse(checkpointId)).thenReturn(Optional.of(checkpoint));
        when(defectRepo.findByCodeAndIsDeletedFalse(existing.getCode())).thenReturn(Optional.of(existing));
        when(roundRepo.save(any(InspectionRound.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InspectionRoundResultDto result = service.recordResult(
                roundId,
                new InspectionRoundResultRequest(checkpointId, "FAIL", null, null, "repeat fail", null)
        );

        assertThat(result.defectId()).isEqualTo(existingDefectId);
        verify(defectRepo, never()).save(any(Defect.class));
        verify(repairRequestRepository, never()).save(any(RepairRequest.class));
    }

    @Test
    void warnAndOkResultsDoNotCreateDefectOrRepairRequest() {
        UUID roundId = UUID.randomUUID();
        InspectionRound round = round(roundId, InspectionRoundStatus.IN_PROGRESS);
        when(roundRepo.findByIdAndIsDeletedFalse(roundId)).thenReturn(Optional.of(round));
        when(roundRepo.save(any(InspectionRound.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.recordResult(
                roundId,
                new InspectionRoundResultRequest(UUID.randomUUID(), "WARN", null, null, "watch", null)
        );
        service.recordResult(
                roundId,
                new InspectionRoundResultRequest(UUID.randomUUID(), "OK", null, null, "ok", null)
        );

        assertThat(round.getFindingsCount()).isEqualTo(1);
        assertThat(round.getAlarmCount()).isZero();
        verify(defectRepo, never()).save(any(Defect.class));
        verify(repairRequestRepository, never()).save(any(RepairRequest.class));
        verify(roundRepo, times(2)).save(any(InspectionRound.class));
    }

    @Test
    void completedRoundCannotRecordResultOrCompleteAgain() {
        UUID roundId = UUID.randomUUID();
        InspectionRound round = round(roundId, InspectionRoundStatus.COMPLETED);
        when(roundRepo.findByIdAndIsDeletedFalse(roundId)).thenReturn(Optional.of(round));

        assertThatThrownBy(() -> service.recordResult(
                roundId,
                new InspectionRoundResultRequest(UUID.randomUUID(), "OK", null, null, null, null)))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Cannot add results");

        assertThatThrownBy(() -> service.completeRound(roundId, "again"))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Round is not IN_PROGRESS");

        verify(roundRepo, never()).save(any(InspectionRound.class));
    }

    @Test
    void missingCheckpointRemainsNotFoundWhenRecordingResult() {
        UUID roundId = UUID.randomUUID();
        UUID checkpointId = UUID.randomUUID();
        InspectionRound round = round(roundId, InspectionRoundStatus.IN_PROGRESS);
        when(roundRepo.findByIdAndIsDeletedFalse(roundId)).thenReturn(Optional.of(round));
        when(checkpointRepo.findByIdAndIsDeletedFalse(checkpointId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.recordResult(
                roundId,
                new InspectionRoundResultRequest(checkpointId, "OK", null, null, null, null)))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Inspection checkpoint not found");

        verify(roundRepo, never()).save(any(InspectionRound.class));
    }

    @Test
    void failWithoutEquipmentIsRejectedBecauseItCannotBeTriaged() {
        UUID roundId = UUID.randomUUID();
        UUID checkpointId = UUID.randomUUID();
        InspectionRound round = roundWithRoute(roundId, UUID.randomUUID(), UUID.randomUUID());
        InspectionCheckpoint checkpoint = checkpoint(checkpointId, null, null, "Area housekeeping");
        when(roundRepo.findByIdAndIsDeletedFalse(roundId)).thenReturn(Optional.of(round));
        when(checkpointRepo.findByIdAndIsDeletedFalse(checkpointId)).thenReturn(Optional.of(checkpoint));

        assertThatThrownBy(() -> service.recordResult(
                roundId,
                new InspectionRoundResultRequest(checkpointId, "FAIL", null, null, "unsafe", null)))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("linked to equipment");

        verify(defectRepo, never()).save(any(Defect.class));
        verify(repairRequestRepository, never()).save(any(RepairRequest.class));
        verify(roundRepo, never()).save(any(InspectionRound.class));
    }

    @Test
    void cancelledRoundCannotRecordResultOrCompleteAgain() {
        UUID roundId = UUID.randomUUID();
        InspectionRound round = round(roundId, InspectionRoundStatus.CANCELLED);
        when(roundRepo.findByIdAndIsDeletedFalse(roundId)).thenReturn(Optional.of(round));

        assertThatThrownBy(() -> service.recordResult(
                roundId,
                new InspectionRoundResultRequest(UUID.randomUUID(), "OK", null, null, null, null)))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Cannot add results");

        assertThatThrownBy(() -> service.completeRound(roundId, "again"))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Round is not IN_PROGRESS");

        verify(roundRepo, never()).save(any(InspectionRound.class));
    }

    @Test
    void createRouteWithKnownCheckpointUnitNormalizesAndSucceeds() {
        when(routeRepo.existsByCodeAndIsDeletedFalse("IR-001")).thenReturn(false);
        when(unitOfMeasurementService.normalizeOptionalUnitOrNull(" bar ")).thenReturn("bar");
        when(routeRepo.save(any(InspectionRoute.class))).thenAnswer(invocation -> {
            InspectionRoute route = invocation.getArgument(0);
            ReflectionTestUtils.setField(route, "id", UUID.randomUUID());
            return route;
        });

        InspectionRouteRequest request = routeRequest(" bar ");

        InspectionRouteDto dto = service.createRoute(request);

        assertThat(dto.checkpoints()).hasSize(1);
        assertThat(dto.checkpoints().getFirst().expectedUnit()).isEqualTo("bar");
        verify(routeRepo).save(any(InspectionRoute.class));
    }

    @Test
    void createRouteWithUnknownCheckpointUnitReturnsBadRequest() {
        when(routeRepo.existsByCodeAndIsDeletedFalse("IR-001")).thenReturn(false);
        when(unitOfMeasurementService.normalizeOptionalUnitOrNull("mystery-unit"))
                .thenThrow(RestException.badRequest("Unknown unit"));

        assertThatThrownBy(() -> service.createRoute(routeRequest("mystery-unit")))
                .isInstanceOf(RestException.class)
                .extracting(ex -> ((RestException) ex).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(routeRepo, never()).save(any(InspectionRoute.class));
    }

    @Test
    void createRouteWithBlankCheckpointUnitStoresNullWhenOptional() {
        when(routeRepo.existsByCodeAndIsDeletedFalse("IR-001")).thenReturn(false);
        when(unitOfMeasurementService.normalizeOptionalUnitOrNull("   ")).thenReturn(null);
        when(routeRepo.save(any(InspectionRoute.class))).thenAnswer(invocation -> {
            InspectionRoute route = invocation.getArgument(0);
            ReflectionTestUtils.setField(route, "id", UUID.randomUUID());
            return route;
        });

        InspectionRouteDto dto = service.createRoute(routeRequest("   "));

        assertThat(dto.checkpoints()).hasSize(1);
        assertThat(dto.checkpoints().getFirst().expectedUnit()).isNull();
        verify(routeRepo).save(any(InspectionRoute.class));
    }

    private InspectionRouteRequest routeRequest(String expectedUnit) {
        InspectionRouteRequest.CheckpointRequest checkpoint = new InspectionRouteRequest.CheckpointRequest(
                1,
                null,
                null,
                "Checkpoint",
                "Instruction",
                "MEASUREMENT",
                0.0,
                10.0,
                expectedUnit,
                true
        );
        return new InspectionRouteRequest(
                "IR-001",
                "Route 1",
                null,
                "DAILY",
                15,
                "desc",
                true,
                List.of(checkpoint)
        );
    }

    private InspectionRound round(UUID id, InspectionRoundStatus status) {
        InspectionRound round = new InspectionRound();
        ReflectionTestUtils.setField(round, "id", id);
        round.setStatus(status);
        round.setPerformedBy(UUID.randomUUID());
        return round;
    }

    private InspectionRound roundWithRoute(UUID id, UUID departmentId, UUID performerId) {
        InspectionRoute route = new InspectionRoute();
        ReflectionTestUtils.setField(route, "id", UUID.randomUUID());
        route.setCode("IR-001");
        route.setName("Route");
        route.setDepartmentId(departmentId);
        InspectionRound round = round(id, InspectionRoundStatus.IN_PROGRESS);
        round.setRoute(route);
        round.setPerformedBy(performerId);
        return round;
    }

    private InspectionCheckpoint checkpoint(UUID id, UUID equipmentId, UUID locationId, String title) {
        InspectionCheckpoint checkpoint = new InspectionCheckpoint();
        ReflectionTestUtils.setField(checkpoint, "id", id);
        checkpoint.setEquipmentId(equipmentId);
        checkpoint.setLocationId(locationId);
        checkpoint.setTitle(title);
        return checkpoint;
    }

    private Equipment equipment(UUID equipmentId, UUID departmentId) {
        Equipment equipment = new Equipment();
        ReflectionTestUtils.setField(equipment, "id", equipmentId);
        equipment.setCode("EQ-001");
        equipment.setName("Pump");
        equipment.setDepartmentId(departmentId);
        return equipment;
    }

    private String shortId(UUID id) {
        return id.toString().substring(0, 8);
    }
}
