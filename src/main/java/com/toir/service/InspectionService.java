package com.toir.service;
import com.toir.entity.InspectionCheckpoint;
import com.toir.entity.InspectionRound;
import com.toir.entity.InspectionRoundResult;
import com.toir.entity.InspectionRoute;
import com.toir.repository.InspectionCheckpointRepository;
import com.toir.repository.InspectionRoundRepository;
import com.toir.repository.InspectionRouteRepository;

import com.toir.exception.RestException;
import com.toir.entity.Defect;
import com.toir.repository.DefectRepository;
import com.toir.enums.DefectStatus;
import com.toir.dto.inspection.InspectionRoundDto;
import com.toir.dto.inspection.InspectionRoundResultDto;
import com.toir.dto.inspection.InspectionRoundResultRequest;
import com.toir.dto.inspection.InspectionRouteDto;
import com.toir.dto.inspection.InspectionRouteRequest;
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



    // --- routes ---

    @Transactional(readOnly = true)
    public List<InspectionRouteDto> findRoutes(UUID departmentId, Boolean activeOnly) {
        List<InspectionRoute> list;
        if (departmentId != null) list = routeRepo.findAllByDepartmentId(departmentId);
        else if (Boolean.TRUE.equals(activeOnly)) list = routeRepo.findAllByActiveTrue();
        else list = routeRepo.findAll();
        return list.stream().map(InspectionRouteDto::from).toList();
    }

    @Transactional(readOnly = true)
    public InspectionRouteDto getRoute(UUID id) {
        return InspectionRouteDto.from(loadRoute(id));
    }

    public InspectionRouteDto createRoute(InspectionRouteRequest r) {
        if (routeRepo.existsByCode(r.code())) {
            throw RestException.conflict("Route code already exists: " + r.code());
        }
        InspectionRoute route = new InspectionRoute();
        applyRoute(route, r);
        if (r.checkpoints() != null) {
            for (InspectionRouteRequest.CheckpointRequest cp : r.checkpoints()) {
                route.getCheckpoints().add(buildCheckpoint(route, cp));
            }
        }
        return InspectionRouteDto.from(routeRepo.save(route));
    }

    public InspectionRouteDto updateRoute(UUID id, InspectionRouteRequest r) {
        InspectionRoute route = loadRoute(id);
        if (!route.getCode().equals(r.code()) && routeRepo.existsByCode(r.code())) {
            throw RestException.conflict("Route code already exists: " + r.code());
        }
        applyRoute(route, r);
        return InspectionRouteDto.from(route);
    }

    public void deleteRoute(UUID id) {
        routeRepo.delete(loadRoute(id));
    }

    public InspectionRouteDto addCheckpoint(UUID routeId, InspectionRouteRequest.CheckpointRequest cp) {
        InspectionRoute route = loadRoute(routeId);
        route.getCheckpoints().add(buildCheckpoint(route, cp));
        return InspectionRouteDto.from(route);
    }

    // --- rounds ---

    @Transactional(readOnly = true)
    public List<InspectionRoundDto> listRounds(UUID routeId, UUID performedBy) {
        List<InspectionRound> list;
        if (routeId != null) list = roundRepo.findAllByRouteIdOrderByStartedAtDesc(routeId);
        else if (performedBy != null) list = roundRepo.findAllByPerformedByOrderByStartedAtDesc(performedBy);
        else list = roundRepo.findAll();
        return list.stream().map(InspectionRoundDto::from).toList();
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
        round.setStatus("IN_PROGRESS");
        return InspectionRoundDto.from(roundRepo.save(round));
    }

    public InspectionRoundResultDto recordResult(UUID roundId, InspectionRoundResultRequest r) {
        InspectionRound round = loadRound(roundId);
        if (!"IN_PROGRESS".equals(round.getStatus())) {
            throw RestException.badRequest("Cannot add results to a completed round");
        }
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
            InspectionCheckpoint cp = checkpointRepo.findById(r.checkpointId()).orElse(null);
            if (cp != null && cp.getEquipmentId() != null) {
                Defect d = autoCreateDefect(cp, r.comment(), round.getId());
                if (d != null) result.setDefectId(d.getId());
            }
        } else if ("WARN".equals(r.status())) {
            round.setFindingsCount(round.getFindingsCount() + 1);
        }
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
        round.setStatus("COMPLETED");
        round.setCompletedAt(Instant.now());
        if (notes != null) round.setNotes(notes);
        return InspectionRoundDto.from(round);
    }

    public InspectionRoundDto cancelRound(UUID roundId, String reason) {
        InspectionRound round = loadRound(roundId);
        round.setStatus("CANCELLED");
        round.setCompletedAt(Instant.now());
        round.setNotes(reason);
        return InspectionRoundDto.from(round);
    }

    private InspectionRoute loadRoute(UUID id) {
        return routeRepo.findById(id)
                .orElseThrow(() -> RestException.notFound("Inspection route not found: " + id));
    }

    private InspectionRound loadRound(UUID id) {
        return roundRepo.findById(id)
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
}
