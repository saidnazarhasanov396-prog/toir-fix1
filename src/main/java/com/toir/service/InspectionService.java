package com.toir.service;

import com.toir.dto.inspection.*;
import com.toir.entity.defects.Defect;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.inspection.InspectionCheckpoint;
import com.toir.entity.inspection.InspectionRound;
import com.toir.entity.inspection.InspectionRoundResult;
import com.toir.entity.inspection.InspectionRoute;
import com.toir.entity.repair.RepairRequest;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.enums.CriticalityLevel;
import com.toir.enums.DefectStatus;
import com.toir.enums.InspectionRoundStatus;
import com.toir.enums.NotificationEventType;
import com.toir.enums.NotificationSeverity;
import com.toir.enums.PriorityLevel;
import com.toir.enums.RequestSource;
import com.toir.exception.RestException;
import com.toir.repository.defects.DefectRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.inspection.InspectionCheckpointRepository;
import com.toir.repository.inspection.InspectionRoundRepository;
import com.toir.repository.inspection.InspectionRouteRepository;
import com.toir.repository.repair.RepairRequestRepository;
import com.toir.security.PermissionConstants;
import com.toir.security.ScopeAccessService;
import com.toir.util.AuditBuilderService;
import com.toir.util.CodeGenerationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InspectionService {

    private final InspectionRouteRepository routeRepo;
    private final InspectionCheckpointRepository checkpointRepo;
    private final InspectionRoundRepository roundRepo;
    private final DefectRepository defectRepo;
    private final RepairRequestRepository repairRequestRepository;
    private final EquipmentRepository equipmentRepository;
    private final UnitOfMeasurementService unitOfMeasurementService;
    private final AuditBuilderService auditBuilderService;
    private final ScopeAccessService scopeAccessService;
    private final NotificationService notificationService;
    private static final String INSPECTION_DEFECT_CODE_PREFIX = "INS-DEF-";
    private static final String INSPECTION_REPAIR_REQUEST_PREFIX = "INS-RR-";
    private static final ZoneId INSPECTION_BUSINESS_ZONE = ZoneId.of("Asia/Tashkent");
    private static final Set<DefectStatus> OPEN_TRIAGE_DEFECT_STATUSES = EnumSet.of(
            DefectStatus.OPEN,
            DefectStatus.IN_ANALYSIS,
            DefectStatus.IN_PROGRESS
    );



    // --- routes ---

    @Transactional(readOnly = true)
    public List<InspectionRouteDto> findRoutes(UUID departmentId, Boolean active,String search) {
        UUID scopedDepartmentId = enforceRouteListDepartmentScope(departmentId);
        return routeRepo
                .findAllByDepartmentIdAndIsDeletedFalseOrderByUpdatedAtDesc(scopedDepartmentId,active,search).
                stream()
                .map(InspectionRouteDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public InspectionRouteDto getRoute(UUID id) {
        InspectionRoute route = loadRoute(id);
        assertCanAccessRoute(route);
        return InspectionRouteDto.from(route);
    }

    @Transactional
    public InspectionRouteDto createRoute(InspectionRouteRequest r) {
        CodeGenerationUtils.rejectClientProvidedCode(r.code());
        assertCanAccessRouteDepartment(r.departmentId());
        InspectionRoute route = new InspectionRoute();
        route.setCode(nextRouteCode());
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
        CodeGenerationUtils.rejectClientProvidedCode(r.code());
        InspectionRoute route = loadRoute(id);
        assertCanAccessRoute(route);
        assertCanAccessRouteDepartment(r.departmentId());
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
        assertCanAccessRoute(entity);
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
        assertCanAccessRoute(route);
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
        if (routeId != null) {
            assertCanAccessRoute(loadRoute(routeId));
        }
        return roundRepo
                .findAllByRouteIdAndIsDeletedFalseOrderByStartedAtDesc(routeId, performedBy, status)
                .stream()
                .filter(this::canAccessRound)
                .map(InspectionRoundDto::fromSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public InspectionRoundDto getRound(UUID id) {
        InspectionRound round = loadRound(id);
        assertCanAccessRound(round);
        return InspectionRoundDto.from(round);
    }

    @Transactional(readOnly = true)
    public InspectionDashboardSummaryDto getDashboardSummary(UUID departmentId) {
        return getDashboardSummary(departmentId, Instant.now());
    }

    InspectionDashboardSummaryDto getDashboardSummary(UUID departmentId, Instant now) {
        UUID scopedDepartmentId = enforceRouteListDepartmentScope(departmentId);
        List<InspectionRoute> routes = routeRepo
                .findAllByDepartmentIdAndIsDeletedFalseOrderByUpdatedAtDesc(scopedDepartmentId, true, null);
        Set<UUID> routeIds = routes.stream().map(InspectionRoute::getId).collect(Collectors.toSet());
        List<InspectionRound> rounds = roundRepo
                .findAllByRouteIdAndIsDeletedFalseOrderByStartedAtDesc(null, null, null)
                .stream()
                .filter(round -> round.getRoute() != null && routeIds.contains(round.getRoute().getId()))
                .filter(this::canAccessRound)
                .toList();

        ZonedDateTime businessNow = now.atZone(INSPECTION_BUSINESS_ZONE);
        LocalDate businessDate = businessNow.toLocalDate();
        Instant businessStart = businessDate.atStartOfDay(INSPECTION_BUSINESS_ZONE).toInstant();
        Instant nextBusinessStart = businessDate.plusDays(1).atStartOfDay(INSPECTION_BUSINESS_ZONE).toInstant();

        Map<UUID, List<InspectionRound>> roundsByRouteId = rounds.stream()
                .collect(Collectors.groupingBy(round -> round.getRoute().getId()));

        int completedToday = (int) rounds.stream()
                .filter(round -> round.getStatus() == InspectionRoundStatus.COMPLETED)
                .filter(round -> isWithinBusinessDay(round.getCompletedAt(), businessStart, nextBusinessStart))
                .count();
        int findingsToday = rounds.stream()
                .filter(round -> isWithinBusinessDay(round.getStartedAt(), businessStart, nextBusinessStart))
                .mapToInt(InspectionRound::getFindingsCount)
                .sum();
        int alarmsToday = rounds.stream()
                .filter(round -> isWithinBusinessDay(round.getStartedAt(), businessStart, nextBusinessStart))
                .mapToInt(InspectionRound::getAlarmCount)
                .sum();

        List<InspectionDashboardSummaryDto.AttentionRouteDto> attentionRoutes = routes.stream()
                .map(route -> buildAttentionRoute(route, roundsByRouteId.getOrDefault(route.getId(), List.of()), now, nextBusinessStart))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .sorted(Comparator
                        .comparingInt((InspectionDashboardSummaryDto.AttentionRouteDto item) -> attentionRank(item.state()))
                        .thenComparing(InspectionDashboardSummaryDto.AttentionRouteDto::nextDueAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(InspectionDashboardSummaryDto.AttentionRouteDto::routeCode, Comparator.nullsLast(String::compareTo)))
                .toList();

        int dueToday = (int) attentionRoutes.stream().filter(item -> "DUE_TODAY".equals(item.state())).count();
        int overdue = (int) attentionRoutes.stream().filter(item -> "OVERDUE".equals(item.state())).count();
        int inProgress = (int) attentionRoutes.stream().filter(item -> "IN_PROGRESS".equals(item.state())).count();

        return new InspectionDashboardSummaryDto(
                now,
                businessDate,
                routes.size(),
                dueToday,
                overdue,
                inProgress,
                completedToday,
                findingsToday,
                alarmsToday,
                attentionRoutes
        );
    }

    private Optional<InspectionDashboardSummaryDto.AttentionRouteDto> buildAttentionRoute(
            InspectionRoute route,
            List<InspectionRound> routeRounds,
            Instant now,
            Instant nextBusinessStart
    ) {
        InspectionRound activeRound = routeRounds.stream()
                .filter(round -> round.getStatus() == InspectionRoundStatus.IN_PROGRESS)
                .max(Comparator.comparing(InspectionRound::getStartedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null);
        Instant lastCompletedAt = routeRounds.stream()
                .filter(round -> round.getStatus() == InspectionRoundStatus.COMPLETED)
                .map(InspectionRound::getCompletedAt)
                .filter(completedAt -> completedAt != null)
                .max(Comparator.naturalOrder())
                .orElse(null);
        Instant nextDueAt = dueAnchor(route, lastCompletedAt).plus(frequencyInterval(route.getFrequency()));

        String state;
        if (activeRound != null) {
            state = "IN_PROGRESS";
        } else if (nextDueAt.isBefore(now)) {
            state = "OVERDUE";
        } else if (nextDueAt.isBefore(nextBusinessStart)) {
            state = "DUE_TODAY";
        } else {
            return Optional.empty();
        }

        return Optional.of(new InspectionDashboardSummaryDto.AttentionRouteDto(
                route.getId(),
                route.getCode(),
                route.getName(),
                route.getDepartmentId(),
                route.getFrequency(),
                route.getCheckpoints() == null ? 0 : route.getCheckpoints().size(),
                route.getTargetDurationMin(),
                lastCompletedAt,
                nextDueAt,
                state,
                activeRound != null ? activeRound.getId() : null,
                activeRound != null ? activeRound.getFindingsCount() : 0,
                activeRound != null ? activeRound.getAlarmCount() : 0
        ));
    }

    private Instant dueAnchor(InspectionRoute route, Instant lastCompletedAt) {
        if (lastCompletedAt != null) {
            return lastCompletedAt;
        }
        if (route.getCreatedAt() != null) {
            return route.getCreatedAt();
        }
        if (route.getUpdatedAt() != null) {
            return route.getUpdatedAt();
        }
        return Instant.EPOCH;
    }

    private Duration frequencyInterval(String frequency) {
        return switch (frequency == null ? "" : frequency) {
            case "SHIFT" -> Duration.ofHours(8);
            case "WEEKLY" -> Duration.ofDays(7);
            case "MONTHLY" -> Duration.ofDays(30);
            case "DAILY" -> Duration.ofDays(1);
            default -> Duration.ofDays(1);
        };
    }

    private boolean isWithinBusinessDay(Instant value, Instant businessStart, Instant nextBusinessStart) {
        return value != null && !value.isBefore(businessStart) && value.isBefore(nextBusinessStart);
    }

    private int attentionRank(String state) {
        return switch (state) {
            case "OVERDUE" -> 0;
            case "DUE_TODAY" -> 1;
            case "IN_PROGRESS" -> 2;
            default -> 3;
        };
    }

    @Transactional
    public InspectionRoundDto startRound(UUID routeId, UUID performedBy) {
        InspectionRoute route = loadRoute(routeId);
        assertCanAccessRoute(route);
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
        assertCanAccessRound(round);
        if (round.getStatus() != InspectionRoundStatus.IN_PROGRESS) {
            throw RestException.badRequest("Cannot add results to a completed round");
        }
        InspectionCheckpoint checkpoint = loadCheckpoint(r.checkpointId());
        InspectionRoundResult result = new InspectionRoundResult();
        result.setRound(round);
        result.setCheckpointId(checkpoint.getId());
        result.setStatus(r.status());
        result.setMeasuredValue(r.measuredValue());
        result.setMeasuredUnit(unitOfMeasurementService.normalizeOptionalUnitOrNull(r.measuredUnit()));
        result.setComment(r.comment());
        result.setPhotoFileIds(r.photoFileIds());
        round.getResults().add(result);
        if ("FAIL".equals(r.status())) {
            round.setAlarmCount(round.getAlarmCount() + 1);
            round.setFindingsCount(round.getFindingsCount() + 1);
            Defect d = createOrReuseFailureTriage(round, checkpoint, r.comment());
            result.setDefectId(d.getId());
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

    private Defect createOrReuseFailureTriage(InspectionRound round, InspectionCheckpoint checkpoint, String comment) {
        if (checkpoint.getEquipmentId() == null) {
            throw RestException.badRequest("Failed inspection checkpoint must be linked to equipment for defect triage");
        }
        String defectCode = inspectionDefectCode(round.getId(), checkpoint.getId());
        Optional<Defect> existingDefect = defectRepo.findByCodeAndIsDeletedFalse(defectCode);
        if (existingDefect.isPresent()) {
            Defect defect = existingDefect.get();
            if (OPEN_TRIAGE_DEFECT_STATUSES.contains(defect.getStatus()) && defect.getRepairRequestId() == null) {
                createRepairRequestIfPossible(round, checkpoint, comment)
                        .map(RepairRequest::getId)
                        .ifPresent(defect::setRepairRequestId);
                if (defect.getRepairRequestId() != null) {
                    Defect saved = defectRepo.save(defect);
                    auditBuilderService.log(
                            "defect",
                            saved.getId().toString(),
                            AuditAction.UPDATE,
                            AuditModule.DEFECT,
                            "Inspection failure linked to repair request",
                            defect,
                            saved
                    );
                    return saved;
                }
            }
            return defect;
        }

        Defect defect = buildInspectionDefect(round, checkpoint, comment, defectCode);
        createRepairRequestIfPossible(round, checkpoint, comment)
                .map(RepairRequest::getId)
                .ifPresent(defect::setRepairRequestId);
        Defect saved = defectRepo.save(defect);
        auditBuilderService.log(
                "defect",
                saved.getId().toString(),
                AuditAction.CREATE,
                AuditModule.DEFECT,
                "Inspection failure defect created",
                null,
                saved
        );
        notifyFailureTriage(round, checkpoint, saved);
        return saved;
    }

    private void notifyFailureTriage(InspectionRound round, InspectionCheckpoint checkpoint, Defect defect) {
        UUID departmentId = inspectionDepartmentId(round, checkpoint.getEquipmentId());
        List<?> recipients = notificationService.notifyDepartmentByPermission(
                departmentId,
                PermissionConstants.DEFECT_READ,
                "Inspection FAIL triage created",
                "Inspection failure " + defect.getCode() + " requires defect triage.",
                NotificationSeverity.WARNING,
                NotificationEventType.DEFECT_CREATED_FROM_INSPECTION,
                NotificationEntityTypes.DEFECT,
                defect.getId().toString()
        );
        if (recipients.isEmpty()) {
            notificationService.notifyDepartmentByPermission(
                    departmentId,
                    PermissionConstants.INSPECTION_READ,
                    "Inspection FAIL triage created",
                    "Inspection failure " + defect.getCode() + " requires maintenance triage.",
                    NotificationSeverity.WARNING,
                    NotificationEventType.DEFECT_CREATED_FROM_INSPECTION,
                    NotificationEntityTypes.DEFECT,
                    defect.getId().toString()
            );
        }
    }

    private Defect buildInspectionDefect(InspectionRound round,
                                         InspectionCheckpoint checkpoint,
                                         String comment,
                                         String defectCode) {
        Defect d = new Defect();
        d.setCode(defectCode);
        d.setTitle(inspectionTitle(checkpoint));
        d.setDescription(inspectionDescription(round, checkpoint, comment));
        d.setEquipmentId(checkpoint.getEquipmentId());
        d.setCategory("INSPECTION");
        d.setSeverity("CRITICAL");
        d.setStatus(DefectStatus.OPEN);
        return d;
    }

    private Optional<RepairRequest> createRepairRequestIfPossible(InspectionRound round,
                                                                  InspectionCheckpoint checkpoint,
                                                                  String comment) {
        UUID equipmentId = checkpoint.getEquipmentId();
        UUID reporterId = round.getPerformedBy();
        UUID departmentId = inspectionDepartmentId(round, equipmentId);
        if (equipmentId == null || reporterId == null || departmentId == null) {
            return Optional.empty();
        }
        String number = inspectionRepairRequestNumber(round.getId(), checkpoint.getId());
        Optional<RepairRequest> existing = repairRequestRepository.findByNumberAndIsDeletedFalse(number);
        if (existing.isPresent()) {
            return existing;
        }
        if (repairRequestRepository.existsByNumberAndIsDeletedFalse(number)) {
            return Optional.empty();
        }

        RepairRequest request = new RepairRequest();
        request.setNumber(number);
        request.setTitle(inspectionTitle(checkpoint));
        request.setDescription(inspectionDescription(round, checkpoint, comment));
        request.setEquipmentId(equipmentId);
        request.setDepartmentId(departmentId);
        request.setLocationId(checkpoint.getLocationId());
        request.setReporterId(reporterId);
        request.setPriority(PriorityLevel.HIGH);
        request.setCriticality(CriticalityLevel.CRITICAL);
        request.setSource(RequestSource.INSPECTION);
        RepairRequest saved = repairRequestRepository.save(request);
        auditBuilderService.log(
                "repair_request",
                saved.getId().toString(),
                AuditAction.CREATE,
                AuditModule.REPAIR_REQUEST,
                "Inspection failure repair request created",
                null,
                saved
        );
        return Optional.of(saved);
    }

    private UUID inspectionDepartmentId(InspectionRound round, UUID equipmentId) {
        InspectionRoute route = round.getRoute();
        if (route != null && route.getDepartmentId() != null) {
            return route.getDepartmentId();
        }
        return equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)
                .map(Equipment::getDepartmentId)
                .orElse(null);
    }

    private String inspectionTitle(InspectionCheckpoint checkpoint) {
        return "Inspection failure: " + checkpoint.getTitle();
    }

    private String inspectionDescription(InspectionRound round, InspectionCheckpoint checkpoint, String comment) {
        return "Inspection FAIL triage. roundId=%s; checkpointId=%s; checkpoint=%s. %s".formatted(
                round.getId(),
                checkpoint.getId(),
                checkpoint.getTitle(),
                comment != null && !comment.isBlank()
                        ? comment.trim()
                        : "Checkpoint failed and requires maintenance triage."
        );
    }

    private String inspectionDefectCode(UUID roundId, UUID checkpointId) {
        return INSPECTION_DEFECT_CODE_PREFIX + shortId(roundId) + "-" + shortId(checkpointId);
    }

    private String inspectionRepairRequestNumber(UUID roundId, UUID checkpointId) {
        return INSPECTION_REPAIR_REQUEST_PREFIX + shortId(roundId) + "-" + shortId(checkpointId);
    }

    private String shortId(UUID id) {
        return id.toString().substring(0, 8);
    }

    private InspectionCheckpoint loadCheckpoint(UUID id) {
        return checkpointRepo.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Inspection checkpoint not found: " + id));
    }

    @Transactional
    public InspectionRoundDto completeRound(UUID roundId, String notes) {
        InspectionRound round = loadRound(roundId);
        assertCanAccessRound(round);
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
        assertCanAccessRound(round);
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

    private UUID enforceRouteListDepartmentScope(UUID requestedDepartmentId) {
        if (scopeAccessService.isScopeAdmin()) {
            return requestedDepartmentId;
        }
        if (scopeAccessService.currentDepartmentIdOrNull() == null) {
            throwAccessDenied();
        }
        return scopeAccessService.enforceDepartmentScope(requestedDepartmentId);
    }

    private void assertCanAccessRouteDepartment(UUID departmentId) {
        if (scopeAccessService.isScopeAdmin()) {
            return;
        }
        if (departmentId == null || !scopeAccessService.canAccessDepartment(departmentId)) {
            throwAccessDenied();
        }
    }

    private void assertCanAccessRoute(InspectionRoute route) {
        if (!canAccessRoute(route)) {
            throwAccessDenied();
        }
    }

    private boolean canAccessRoute(InspectionRoute route) {
        if (route == null) {
            return false;
        }
        if (scopeAccessService.isScopeAdmin()) {
            return true;
        }
        return scopeAccessService.canAccessDepartment(route.getDepartmentId());
    }

    private void assertCanAccessRound(InspectionRound round) {
        if (!canAccessRound(round)) {
            throwAccessDenied();
        }
    }

    private boolean canAccessRound(InspectionRound round) {
        if (round == null) {
            return false;
        }
        if (scopeAccessService.isScopeAdmin()) {
            return true;
        }
        InspectionRoute route = round.getRoute();
        boolean routeInScope = route != null && scopeAccessService.canAccessDepartment(route.getDepartmentId());
        boolean performerInScope = round.getPerformedBy() != null
                && scopeAccessService.canAccessAssignedUser(round.getPerformedBy());
        return routeInScope || performerInScope;
    }

    private void throwAccessDenied() {
        throw new AccessDeniedException("Access denied by data scope");
    }

    private void applyRoute(InspectionRoute route, InspectionRouteRequest r) {
        route.setName(r.name());
        route.setDepartmentId(r.departmentId());
        if (r.frequency() != null) route.setFrequency(r.frequency());
        route.setTargetDurationMin(r.targetDurationMin());
        route.setDescription(r.description());
        if (r.active() != null) route.setActive(r.active());
    }

    private String nextRouteCode() {
        String prefix = "IR-" + java.time.Year.now().getValue() + "-";
        return CodeGenerationUtils.nextYearSequenceCode(
                "IR",
                () -> routeRepo.maxSequenceByCodePrefix(prefix),
                routeRepo::existsByCodeAndIsDeletedFalse
        );
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
