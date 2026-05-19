package com.toir.service;

import com.toir.dto.inspection.*;
import com.toir.entity.defects.Defect;
import com.toir.entity.inspection.InspectionCheckpoint;
import com.toir.entity.inspection.InspectionRound;
import com.toir.entity.inspection.InspectionRoundResult;
import com.toir.entity.inspection.InspectionRoute;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.enums.DefectStatus;
import com.toir.enums.InspectionRoundStatus;
import com.toir.exception.RestException;
import com.toir.repository.defects.DefectRepository;
import com.toir.repository.inspection.InspectionCheckpointRepository;
import com.toir.repository.inspection.InspectionRoundRepository;
import com.toir.repository.inspection.InspectionRouteRepository;
import com.toir.util.AuditBuilderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InspectionService {

    private final InspectionRouteRepository routeRepo;
    private final InspectionCheckpointRepository checkpointRepo;
    private final InspectionRoundRepository roundRepo;
    private final DefectRepository defectRepo;
    private final UnitOfMeasurementService unitOfMeasurementService;
    private final AuditBuilderService auditBuilderService;



    // --- routes ---

    @Transactional(readOnly = true)
    public List<InspectionRouteDto> findRoutes(UUID departmentId, Boolean active,String search) {
        return routeRepo
                .findAllByDepartmentIdAndIsDeletedFalseOrderByUpdatedAtDesc(departmentId,active,search).
                stream()
                .map(InspectionRouteDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public InspectionRouteDto getRoute(UUID id) {
        return InspectionRouteDto.from(loadRoute(id));
    }

    @Transactional
    public InspectionRouteDto createRoute(InspectionRouteRequest r) {
        if (routeRepo.existsByCodeAndIsDeletedFalse(r.code())) {
            throw RestException.conflict("Route code already exists: " + r.code());
        }
        InspectionRoute route = new InspectionRoute();
        applyRoute(route, r);
        if (r.checkpoints() != null) {
            for (InspectionRouteRequest.CheckpointRequest cp : r.checkpoints()) {
                route.getCheckpoints().add(buildCheckpoint(route, cp));
            }
        }
        InspectionRoute saved = routeRepo.save(route);

        auditBuilderService.log(
                "inspection_route",
                saved.getId().toString(),
                AuditAction.CREATE,
                AuditModule.INSPECTION_ROUTE,
                "Маршрут осмотра создан",
                null,
                saved
        );
        return InspectionRouteDto.from(saved);
    }

    @Transactional
    public InspectionRouteDto updateRoute(UUID id, InspectionRouteRequest r) {
        InspectionRoute route = loadRoute(id);
        if (!route.getCode().equals(r.code()) && routeRepo.existsByCodeAndIsDeletedFalse(r.code())) {
            throw RestException.conflict("Route code already exists: " + r.code());
        }
        applyRoute(route, r);
        InspectionRoute saved = routeRepo.save(route);

        auditBuilderService.log(
                "inspection_route",
                saved.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.INSPECTION_ROUTE,
                "Маршрут осмотра обновлен",
                route,
                saved
        );

        return InspectionRouteDto.from(route);
    }

    @Transactional
    public void deleteRoute(UUID id) {
        var entity = loadRoute(id);
        entity.setDeleted(true);
        InspectionRoute saved = routeRepo.save(entity);

        auditBuilderService.log(
                "inspection_route",
                saved.getId().toString(),
                AuditAction.DELETE,
                AuditModule.INSPECTION_ROUTE,
                "Маршрут осмотра удален",
                saved,
                null
        );

    }

    @Transactional
    public InspectionRouteDto addCheckpoint(UUID routeId, InspectionRouteRequest.CheckpointRequest cp) {
        InspectionRoute route = loadRoute(routeId);
        InspectionCheckpoint checkpoint = buildCheckpoint(route, cp);
        route.getCheckpoints().add(checkpoint);

        auditBuilderService.log(
                "inspection_checkpoint",
                checkpoint.getId().toString(),
                AuditAction.CREATE,
                AuditModule.INSPECTION_CHECKPOINT,
                "Контрольная точка осмотра создана",
                null,
                checkpoint
        );

        InspectionRoute saved = routeRepo.save(route);

        auditBuilderService.log(
                "inspection_route",
                saved.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.INSPECTION_ROUTE,
                "Маршрут осмотра обновлен",
                route,
                saved
        );
        return InspectionRouteDto.from(route);
    }

    // --- rounds ---

    @Transactional(readOnly = true)
    public List<InspectionRoundDto> listRounds(UUID routeId, UUID performedBy,InspectionRoundStatus status) {
        return roundRepo
                .findAllByRouteIdAndIsDeletedFalseOrderByStartedAtDesc(routeId, performedBy, status)
                .stream()
                .map(InspectionRoundDto::fromSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public InspectionRoundDto getRound(UUID id) {
        return InspectionRoundDto.from(loadRound(id));
    }

    @Transactional
    public InspectionRoundDto startRound(UUID routeId, UUID performedBy) {
        InspectionRoute route = loadRoute(routeId);
        InspectionRound round = new InspectionRound();
        round.setRoute(route);
        round.setPerformedBy(performedBy);
        round.setStartedAt(Instant.now());
        round.setStatus(InspectionRoundStatus.IN_PROGRESS);
        InspectionRound saved = roundRepo.save(round);

        auditBuilderService.log(
                "inspection_round",
                saved.getId().toString(),
                AuditAction.CREATE,
                AuditModule.INSPECTION_ROUND,
                "Раунд осмотра создан",
                null,
                saved
        );


        return InspectionRoundDto.from(saved);
    }

    @Transactional
    public InspectionRoundResultDto recordResult(UUID roundId, InspectionRoundResultRequest r) {
        InspectionRound round = loadRound(roundId);
        if (round.getStatus() != InspectionRoundStatus.IN_PROGRESS) {
            throw RestException.badRequest("Cannot add results to a completed round");
        }
        InspectionRoundResult result = new InspectionRoundResult();
        result.setRound(round);
        result.setCheckpointId(r.checkpointId());
        result.setStatus(r.status());
        result.setMeasuredValue(r.measuredValue());
        result.setMeasuredUnit(unitOfMeasurementService.normalizeOptionalUnitOrNull(r.measuredUnit()));
        result.setComment(r.comment());
        result.setPhotoFileIds(r.photoFileIds());
        round.getResults().add(result);
        if ("FAIL".equals(r.status())) {
            round.setAlarmCount(round.getAlarmCount() + 1);
            round.setFindingsCount(round.getFindingsCount() + 1);
            InspectionCheckpoint cp = checkpointRepo.findByIdAndIsDeletedFalse(r.checkpointId()).orElse(null);
            if (cp != null && cp.getEquipmentId() != null) {
                Defect d = autoCreateDefect(cp, r.comment(), round.getId());
                if (d != null) result.setDefectId(d.getId());
            }
        } else if ("WARN".equals(r.status())) {
            round.setFindingsCount(round.getFindingsCount() + 1);
        }

        String resultResourceId = result.getId() != null ? result.getId().toString() : roundId.toString();

        auditBuilderService.log(
                "inspection_round_result",
                resultResourceId,
                AuditAction.CREATE,
                AuditModule.INSPECTION_ROUND_RESULT,
                "Результат осмотра создан",
                null,
                result
        );

        InspectionRound saved = roundRepo.save(round);

        auditBuilderService.log(
                "inspection_round",
                saved.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.INSPECTION_ROUND,
                "Раунд осмотра обновлен",
                null,
                saved
        );

        return InspectionRoundResultDto.from(result);
    }

    private Defect autoCreateDefect(InspectionCheckpoint cp, String comment, UUID roundId) {
        Defect d = new Defect();
        d.setCode("AUTO-INS-" + Instant.now().toEpochMilli());
        d.setTitle("Auto: " + cp.getTitle());
        d.setDescription("Обнаружено при обходе (round " + roundId + "). "
                + (comment != null ? comment : "Позиция чек-листа провалена."));
        d.setEquipmentId(cp.getEquipmentId());
        d.setCategory("INSPECTION");
        d.setSeverity("MAJOR");
        d.setStatus(DefectStatus.OPEN);
        return defectRepo.save(d);
    }

    @Transactional
    public InspectionRoundDto completeRound(UUID roundId, String notes) {
        InspectionRound round = loadRound(roundId);
        if (round.getStatus() != InspectionRoundStatus.IN_PROGRESS) {
            throw RestException.badRequest("Round is not IN_PROGRESS");
        }
        round.setStatus(InspectionRoundStatus.COMPLETED);
        round.setCompletedAt(Instant.now());
        if (notes != null) round.setNotes(notes);

        InspectionRound saved = roundRepo.save(round);

        auditBuilderService.log(
                "inspection_round",
                saved.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.INSPECTION_ROUND,
                "Раунд осмотра обновлен",
                null,
                saved
        );

        return InspectionRoundDto.from(round);
    }


    @Transactional
    public InspectionRoundDto cancelRound(UUID roundId, String reason) {
        InspectionRound round = loadRound(roundId);
        round.setStatus(InspectionRoundStatus.CANCELLED);
        round.setCompletedAt(Instant.now());
        round.setNotes(reason);
        InspectionRound saved = roundRepo.save(round);

        auditBuilderService.log(
                "inspection_round",
                saved.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.INSPECTION_ROUND,
                "Раунд осмотра обновлен",
                null,
                saved
        );

        return InspectionRoundDto.from(round);
    }

    private InspectionRoute loadRoute(UUID id) {
        return routeRepo.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Inspection route not found: " + id));
    }

    private InspectionRound loadRound(UUID id) {
        return roundRepo.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Inspection round not found: " + id));
    }

    private void applyRoute(InspectionRoute route, InspectionRouteRequest r) {
        route.setCode(r.code());
        route.setName(r.name());
        route.setDepartmentId(r.departmentId());
        if (r.frequency() != null) route.setFrequency(r.frequency());
        route.setTargetDurationMin(r.targetDurationMin());
        route.setDescription(r.description());
        if (r.active() != null) route.setActive(r.active());
    }

    private InspectionCheckpoint buildCheckpoint(InspectionRoute route, InspectionRouteRequest.CheckpointRequest cp) {
        InspectionCheckpoint c = new InspectionCheckpoint();
        c.setRoute(route);
        c.setOrderIndex(cp.orderIndex());
        c.setEquipmentId(cp.equipmentId());
        c.setLocationId(cp.locationId());
        c.setTitle(cp.title());
        c.setInstruction(cp.instruction());
        if (cp.checkType() != null) c.setCheckType(cp.checkType());
        c.setExpectedMin(cp.expectedMin());
        c.setExpectedMax(cp.expectedMax());
        c.setExpectedUnit(unitOfMeasurementService.normalizeOptionalUnitOrNull(cp.expectedUnit()));
        if (cp.mandatory() != null) c.setMandatory(cp.mandatory());
        return c;
    }
}
