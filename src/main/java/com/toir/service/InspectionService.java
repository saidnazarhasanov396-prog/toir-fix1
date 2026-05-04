package com.toir.service;
import com.toir.entity.inspection.InspectionCheckpoint;
import com.toir.entity.inspection.InspectionRound;
import com.toir.entity.inspection.InspectionRoundResult;
import com.toir.entity.inspection.InspectionRoute;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.enums.InspectionRoundStatus;
import com.toir.repository.inspection.InspectionCheckpointRepository;
import com.toir.repository.inspection.InspectionRoundRepository;
import com.toir.repository.inspection.InspectionRouteRepository;

import com.toir.exception.RestException;
import com.toir.entity.defects.Defect;
import com.toir.repository.defects.DefectRepository;
import com.toir.enums.DefectStatus;
import com.toir.dto.inspection.InspectionRoundDto;
import com.toir.dto.inspection.InspectionRoundResultDto;
import com.toir.dto.inspection.InspectionRoundResultRequest;
import com.toir.dto.inspection.InspectionRouteDto;
import com.toir.dto.inspection.InspectionRouteRequest;
import com.toir.util.AuditBuilderService;
import com.toir.util.AuditSerializationService;
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
    private final AuditBuilderService auditBuilderService;
    private final AuditSerializationService auditSerializationService;



    // --- routes ---

    @Transactional(readOnly = true)
    public List<InspectionRouteDto> findRoutes(UUID departmentId, Boolean active,String search) {
        List<InspectionRouteDto> list= routeRepo.findAllByDepartmentIdAndIsDeletedFalseOrderByUpdatedAtDesc(departmentId,active,search).
                stream().map(InspectionRouteDto::from).toList();
        return list;
    }

    @Transactional(readOnly = true)
    public InspectionRouteDto getRoute(UUID id) {
        return InspectionRouteDto.from(loadRoute(id));
    }

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
        auditRoute(AuditAction.CREATE, saved.getId(), null, saved);
        return InspectionRouteDto.from(saved);
    }

    public InspectionRouteDto updateRoute(UUID id, InspectionRouteRequest r) {
        InspectionRoute route = loadRoute(id);
        if (!route.getCode().equals(r.code()) && routeRepo.existsByCodeAndIsDeletedFalse(r.code())) {
            throw RestException.conflict("Route code already exists: " + r.code());
        }
        String oldJson = auditSerializationService.toJson(route);
        applyRoute(route, r);
        auditRoute(AuditAction.UPDATE, route.getId(), oldJson, route);
        return InspectionRouteDto.from(route);
    }

    public void deleteRoute(UUID id) {
        var entity = loadRoute(id);
        String oldJson = auditSerializationService.toJson(entity);
        entity.setDeleted(true);
        InspectionRoute saved = routeRepo.save(entity);
        auditRoute(AuditAction.DELETE, saved.getId(), oldJson, null);
    }

    public InspectionRouteDto addCheckpoint(UUID routeId, InspectionRouteRequest.CheckpointRequest cp) {
        InspectionRoute route = loadRoute(routeId);
        String oldJson = auditSerializationService.toJson(route);
        InspectionCheckpoint checkpoint = buildCheckpoint(route, cp);
        route.getCheckpoints().add(checkpoint);
        auditCheckpoint(AuditAction.CREATE, checkpoint.getId(), null, checkpoint);
        auditRoute(AuditAction.UPDATE, route.getId(), oldJson, route);
        return InspectionRouteDto.from(route);
    }

    // --- rounds ---

    @Transactional(readOnly = true)
    public List<InspectionRoundDto> listRounds(UUID routeId, UUID performedBy,InspectionRoundStatus status) {
        String statusStr = status == null ? null : status.toString();
        List<InspectionRoundDto> list = roundRepo.findAllByRouteIdAndIsDeletedFalseOrderByStartedAtDesc(routeId,performedBy,statusStr)
                .stream().map(InspectionRoundDto::from).toList();
        return list;
    }

    @Transactional(readOnly = true)
    public InspectionRoundDto getRound(UUID id) {
        return InspectionRoundDto.from(loadRound(id));
    }

    public InspectionRoundDto startRound(UUID routeId, UUID performedBy) {
        InspectionRoute route = loadRoute(routeId);
        InspectionRound round = new InspectionRound();
        round.setRoute(route);
        round.setPerformedBy(performedBy);
        round.setStartedAt(Instant.now());
        round.setStatus(InspectionRoundStatus.IN_PROGRESS);
        InspectionRound saved = roundRepo.save(round);
        auditRound(AuditAction.CREATE, saved.getId(), null, saved);
        return InspectionRoundDto.from(saved);
    }

    public InspectionRoundResultDto recordResult(UUID roundId, InspectionRoundResultRequest r) {
        InspectionRound round = loadRound(roundId);
        if (!"IN_PROGRESS".equals(round.getStatus())) {
            throw RestException.badRequest("Cannot add results to a completed round");
        }
        String oldRoundJson = auditSerializationService.toJson(round);
        InspectionRoundResult result = new InspectionRoundResult();
        result.setRound(round);
        result.setCheckpointId(r.checkpointId());
        result.setStatus(r.status());
        result.setMeasuredValue(r.measuredValue());
        result.setMeasuredUnit(r.measuredUnit());
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
        auditResult(AuditAction.CREATE, result.getId(), null, result);
        auditRound(AuditAction.UPDATE, round.getId(), oldRoundJson, round);
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

    public InspectionRoundDto completeRound(UUID roundId, String notes) {
        InspectionRound round = loadRound(roundId);
        if (!"IN_PROGRESS".equals(round.getStatus())) {
            throw RestException.badRequest("Round is not IN_PROGRESS");
        }
        String oldJson = auditSerializationService.toJson(round);
        round.setStatus(InspectionRoundStatus.COMPLETED);
        round.setCompletedAt(Instant.now());
        if (notes != null) round.setNotes(notes);
        auditRound(AuditAction.UPDATE, round.getId(), oldJson, round);
        return InspectionRoundDto.from(round);
    }

    public InspectionRoundDto cancelRound(UUID roundId, String reason) {
        InspectionRound round = loadRound(roundId);
        String oldJson = auditSerializationService.toJson(round);
        round.setStatus(InspectionRoundStatus.CANCELLED);
        round.setCompletedAt(Instant.now());
        round.setNotes(reason);
        auditRound(AuditAction.UPDATE, round.getId(), oldJson, round);
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
        c.setExpectedUnit(cp.expectedUnit());
        if (cp.mandatory() != null) c.setMandatory(cp.mandatory());
        return c;
    }

    private void auditRoute(AuditAction action, UUID id, String oldJson, InspectionRoute current) {
        String newJson = current == null ? null : auditSerializationService.toJson(current);
        auditBuilderService.log(
                "inspection_route",
                id != null ? id.toString() : null,
                action,
                AuditModule.INSPECTION_ROUTE,
                auditRouteMessage(action),
                oldJson,
                newJson
        );
    }

    private void auditCheckpoint(AuditAction action, UUID id, String oldJson, InspectionCheckpoint current) {
        String newJson = current == null ? null : auditSerializationService.toJson(current);
        auditBuilderService.log(
                "inspection_checkpoint",
                id != null ? id.toString() : null,
                action,
                AuditModule.INSPECTION_CHECKPOINT,
                auditCheckpointMessage(action),
                oldJson,
                newJson
        );
    }

    private void auditRound(AuditAction action, UUID id, String oldJson, InspectionRound current) {
        String newJson = current == null ? null : auditSerializationService.toJson(current);
        auditBuilderService.log(
                "inspection_round",
                id != null ? id.toString() : null,
                action,
                AuditModule.INSPECTION_ROUND,
                auditRoundMessage(action),
                oldJson,
                newJson
        );
    }

    private void auditResult(AuditAction action, UUID id, String oldJson, InspectionRoundResult current) {
        String newJson = current == null ? null : auditSerializationService.toJson(current);
        auditBuilderService.log(
                "inspection_round_result",
                id != null ? id.toString() : null,
                action,
                AuditModule.INSPECTION_ROUND_RESULT,
                auditResultMessage(action),
                oldJson,
                newJson
        );
    }

    private String auditRouteMessage(AuditAction action) {
        return switch (action) {
            case CREATE -> "Маршрут осмотра создан";
            case UPDATE -> "Маршрут осмотра обновлен";
            case DELETE -> "Маршрут осмотра удален";
            default -> "Действие выполнено над маршрутом осмотра";
        };
    }

    private String auditCheckpointMessage(AuditAction action) {
        return switch (action) {
            case CREATE -> "Контрольная точка осмотра создана";
            case UPDATE -> "Контрольная точка осмотра обновлена";
            case DELETE -> "Контрольная точка осмотра удалена";
            default -> "Действие выполнено над контрольной точкой осмотра";
        };
    }

    private String auditRoundMessage(AuditAction action) {
        return switch (action) {
            case CREATE -> "Раунд осмотра создан";
            case UPDATE -> "Раунд осмотра обновлен";
            case DELETE -> "Раунд осмотра удален";
            default -> "Действие выполнено над раундом осмотра";
        };
    }

    private String auditResultMessage(AuditAction action) {
        return switch (action) {
            case CREATE -> "Результат осмотра создан";
            case UPDATE -> "Результат осмотра обновлен";
            case DELETE -> "Результат осмотра удален";
            default -> "Действие выполнено над результатом осмотра";
        };
    }
}
