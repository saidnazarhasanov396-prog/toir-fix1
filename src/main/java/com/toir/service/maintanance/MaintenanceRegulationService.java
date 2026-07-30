package com.toir.service.maintanance;

import com.toir.dto.maintenanceregulation.MaintenanceRegulationDto;
import com.toir.dto.maintenanceregulation.MaintenanceRegulationFilter;
import com.toir.dto.maintenanceregulation.EquipmentTypeWithRegulationsDto;
import com.toir.dto.maintenanceregulation.EquipmentWithRegulationsDto;
import com.toir.dto.maintenanceregulation.MaintenanceRegulationAttributeConditionDto;
import com.toir.dto.maintenanceregulation.MaintenanceRegulationAttributeConditionRequest;
import com.toir.dto.maintenanceregulation.MaintenanceRegulationRequest;
import com.toir.dto.maintenanceregulation.MaintenanceRegulationSparePartRequirementDto;
import com.toir.dto.maintenanceregulation.MaintenanceRegulationSparePartRequirementRequest;
import com.toir.dto.maintenanceregulation.MaintenanceRegulationSummaryDto;
import com.toir.dto.maintenanceregulation.MaintenanceRegulationStatsDto;
import com.toir.entity.SparePart;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.maintenance.EquipmentMaintenanceRule;
import com.toir.entity.maintenance.MaintenanceRegulationAttributeCondition;
import com.toir.entity.maintenance.MaintenanceRegulation;
import com.toir.entity.maintenance.MaintenanceOperation;
import com.toir.entity.maintenance.MaintenanceRegulationSparePartRequirement;
import com.toir.entity.maintenance.MaintenanceTemplate;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.enums.ApprovalResultAction;
import com.toir.enums.AutomationAction;
import com.toir.enums.DuplicatePolicy;
import com.toir.enums.MaintenanceInitialSchedulePolicy;
import com.toir.enums.MaintenanceRecalculationPolicy;
import com.toir.enums.MaintenanceRegulationConditionOperator;
import com.toir.enums.MaintenanceTriggerPolicy;
import com.toir.enums.PriorityLevel;
import com.toir.exception.RestException;
import com.toir.entity.equipment.EquipmentType;
import com.toir.repository.SparePartRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.equipment.EquipmentAttributeDefinitionRepository;
import com.toir.repository.equipment.EquipmentTypeRepository;
import com.toir.repository.maintenance.EquipmentMaintenanceRuleRepository;
import com.toir.repository.maintenance.MaintenanceOperationRepository;
import com.toir.repository.maintenance.MaintenanceRegulationAttributeConditionRepository;
import com.toir.repository.maintenance.MaintenanceRegulationRepository;
import com.toir.repository.maintenance.MaintenanceRegulationReadRepository;
import com.toir.repository.maintenance.MaintenanceRegulationSparePartRequirementRepository;
import com.toir.repository.maintenance.MaintenanceTemplateRepository;
import com.toir.security.PermissionConstants;
import com.toir.security.SecurityAccessService;
import com.toir.util.AuditBuilderService;
import com.toir.util.PaginationUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Year;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MaintenanceRegulationService {

    private final MaintenanceRegulationRepository repository;
    private final MaintenanceRegulationReadRepository readRepository;
    private final MaintenanceRegulationAttributeConditionRepository conditionRepository;
    private final EquipmentRepository equipmentRepository;
    private final EquipmentMaintenanceRuleRepository equipmentMaintenanceRuleRepository;
    private final EquipmentTypeRepository equipmentTypeRepository;
    private final EquipmentAttributeDefinitionRepository attributeDefinitionRepository;
    private final MaintenanceTemplateRepository templateRepository;
    private final MaintenanceOperationRepository operationRepository;
    private final MaintenanceRegulationSparePartRequirementRepository sparePartRequirementRepository;
    private final SparePartRepository sparePartRepository;
    private final AuditBuilderService auditBuilderService;
    private final SecurityAccessService securityAccessService;
    private final MaintenanceMeterBaselineService meterBaselineService;
    private static final int MAX_CODE_GENERATION_ATTEMPTS = 50;
    private static final String CLIENT_CODE_REJECT_MESSAGE =
            "code is generated by backend and must not be provided";


    @Transactional(readOnly = true)
    public List<MaintenanceRegulationDto> findAll() {
        return toDtoList(repository.findAllByIsDeletedFalseOrderByUpdatedAtDesc());
    }

    @Transactional(readOnly = true)
    public Page<MaintenanceRegulationDto> search(int page,
                                                 int pageSize,
                                                 MaintenanceRegulationFilter filter) {
        MaintenanceRegulationFilter safeFilter = safeFilter(filter);
        validateEquipmentTypeIfProvided(safeFilter.equipmentTypeId());
        try {
            Pageable pageable = PaginationUtils.pageRequest(page, pageSize);
            Page<MaintenanceRegulationReadRepository.GeneralKey> keyPage =
                    readRepository.findGeneral(safeFilter, pageable);
            List<MaintenanceRegulationDto> content = mapGeneralPage(keyPage.getContent());
            return new PageImpl<>(content, pageable, keyPage.getTotalElements());
        } catch (DataAccessException exception) {
            throw readFailure(exception);
        }
    }

    @Transactional(readOnly = true)
    public Page<EquipmentWithRegulationsDto> equipmentWithRegulations(
            int page,
            int size,
            MaintenanceRegulationFilter filter
    ) {
        MaintenanceRegulationFilter safeFilter = safeFilter(filter);
        validateEquipmentTypeIfProvided(safeFilter.equipmentTypeId());
        try {
            Pageable pageable = PaginationUtils.pageRequest(page, size);
            Page<UUID> equipmentPage = readRepository.findEquipmentIds(safeFilter, pageable);
            List<EquipmentWithRegulationsDto> content =
                    mapEquipmentPage(safeFilter, equipmentPage.getContent());
            return new PageImpl<>(content, pageable, equipmentPage.getTotalElements());
        } catch (DataAccessException exception) {
            throw readFailure(exception);
        }
    }

    @Transactional(readOnly = true)
    public Page<EquipmentTypeWithRegulationsDto> equipmentTypeWithRegulations(
            int page,
            int size,
            MaintenanceRegulationFilter filter
    ) {
        MaintenanceRegulationFilter safeFilter = safeFilter(filter);
        validateEquipmentTypeIfProvided(safeFilter.equipmentTypeId());
        try {
            Pageable pageable = PaginationUtils.pageRequest(page, size);
            Page<UUID> typePage = readRepository.findEquipmentTypeIds(safeFilter, pageable);
            List<EquipmentTypeWithRegulationsDto> content =
                    mapEquipmentTypePage(safeFilter, typePage.getContent());
            return new PageImpl<>(content, pageable, typePage.getTotalElements());
        } catch (DataAccessException exception) {
            throw readFailure(exception);
        }
    }

    @Transactional(readOnly = true)
    public MaintenanceRegulationStatsDto stats(MaintenanceRegulationFilter filter) {
        MaintenanceRegulationFilter safeFilter = safeFilter(filter);
        validateEquipmentTypeIfProvided(safeFilter.equipmentTypeId());
        try {
            return readRepository.stats(safeFilter);
        } catch (DataAccessException exception) {
            throw readFailure(exception);
        }
    }

    @Transactional(readOnly = true)
    public MaintenanceRegulationDto findById(UUID id) {
        return toDto(getOrThrow(id));
    }

    @Transactional(readOnly = true)
    public MaintenanceRegulationDto validateCanApprove(UUID id) {
        return toDto(getOrThrow(id));
    }

    @Transactional
    public MaintenanceRegulationDto finalizeApprovalFromApprovalRequest(UUID id) {
        MaintenanceRegulation entity = getOrThrow(id);
        MaintenanceRegulation before = copyForAudit(entity);
        entity.setActive(true);
        MaintenanceRegulation saved = repository.save(entity);
        meterBaselineService.seedForRegulation(saved);

        auditBuilderService.log(
                "maintenance_regulation",
                saved.getId().toString(),
                AuditAction.APPROVE,
                AuditModule.MAINTENANCE_REGULATION,
                "Регламент обслуживания утвержден",
                before,
                saved);

        return toDto(saved);
    }

    @Transactional
    public MaintenanceRegulationDto finalizeRejectionFromApprovalRequest(UUID id) {
        MaintenanceRegulation entity = getOrThrow(id);
        MaintenanceRegulation before = copyForAudit(entity);
        entity.setActive(false);
        MaintenanceRegulation saved = repository.save(entity);

        auditBuilderService.log(
                "maintenance_regulation",
                saved.getId().toString(),
                AuditAction.CANCEL,
                AuditModule.MAINTENANCE_REGULATION,
                "Регламент обслуживания отклонен",
                before,
                saved);

        return toDto(saved);
    }


    @Transactional(readOnly = true)
    public List<MaintenanceRegulation> findActiveByEquipmentType(UUID equipmentTypeId) {
        return repository.findAllByEquipmentTypeIdAndActiveTrueAndIsDeletedFalse(equipmentTypeId);
    }

    @Transactional
    public MaintenanceRegulationDto create(MaintenanceRegulationRequest request) {
        validateClientProvidedCode(request.code());
        validateAutomationConfigurationPermissionForCreate(request);
        List<ValidatedRegulationSparePartRequirement> sparePartRequirements =
                validateSparePartRequests(request.sparePartRequirements());
        MaintenanceRegulation saved = saveWithGeneratedCode(request);
        replaceConditions(saved.getId(), saved.getEquipmentTypeId(), request.attributeConditions());
        replaceSparePartRequirements(saved, request.sparePartRequirements(), sparePartRequirements);
        meterBaselineService.seedForRegulation(saved);

        auditBuilderService.log(
                "maintenance_regulation",
                saved.getId().toString(),
                AuditAction.CREATE,
                AuditModule.MAINTENANCE_REGULATION,
                "Регламент обслуживания создан",
                null,
                saved);

        return toDto(saved);
    }

    @Transactional
    public MaintenanceRegulationDto update(UUID id, MaintenanceRegulationRequest request) {
        validateClientProvidedCode(request.code());
        MaintenanceRegulation entity = getOrThrow(id);
        validateAutomationConfigurationPermissionForUpdate(entity, request);
        List<ValidatedRegulationSparePartRequirement> sparePartRequirements =
                validateSparePartRequests(request.sparePartRequirements());
        applyMutableFields(entity, request);

        MaintenanceRegulation save = repository.save(entity);
        replaceConditions(save.getId(), save.getEquipmentTypeId(), request.attributeConditions());
        replaceSparePartRequirements(save, request.sparePartRequirements(), sparePartRequirements);
        meterBaselineService.seedForRegulation(save);

        auditBuilderService.log(
                "maintenance_regulation",
                id != null ? id.toString() : null,
                AuditAction.UPDATE,
                AuditModule.MAINTENANCE_REGULATION,
                "Регламент обслуживания обновлен",
                entity,
                save);

        return toDto(entity);
    }

    @Transactional
    public void delete(UUID id) {
        var entity = getOrThrow(id);
        entity.setDeleted(true);
        MaintenanceRegulation saved = repository.save(entity);

        auditBuilderService.log(
                "maintenance_regulation",
                saved.getId().toString(),
                AuditAction.DELETE,
                AuditModule.MAINTENANCE_REGULATION,
                "Регламент обслуживания удален",
                saved,
                null);

    }

    private MaintenanceRegulation getOrThrow(UUID id) {
        return repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Maintenance regulation not found: " + id));
    }

    private MaintenanceRegulation copyForAudit(MaintenanceRegulation source) {
        if (source == null) {
            return null;
        }
        MaintenanceRegulation copy = MaintenanceRegulation.builder()
                .code(source.getCode())
                .name(source.getName())
                .description(source.getDescription())
                .equipmentTypeId(source.getEquipmentTypeId())
                .templateId(source.getTemplateId())
                .maintenanceKind(source.getMaintenanceKind())
                .normativeLaborHours(source.getNormativeLaborHours())
                .active(source.isActive())
                .periodicityUnit(source.getPeriodicityUnit())
                .periodicityValue(source.getPeriodicityValue())
                .toleranceDays(source.getToleranceDays())
                .requiresShutdown(source.isRequiresShutdown())
                .triggerMeterType(source.getTriggerMeterType())
                .triggerMeterInterval(source.getTriggerMeterInterval())
                .triggerPolicy(source.getTriggerPolicy())
                .recalculationPolicy(source.getRecalculationPolicy())
                .initialSchedulePolicy(source.getInitialSchedulePolicy())
                .automationAction(source.getAutomationAction())
                .approvalResultAction(source.getApprovalResultAction())
                .duplicatePolicy(source.getDuplicatePolicy())
                .leadTimeDays(source.getLeadTimeDays())
                .leadMeterPercent(source.getLeadMeterPercent())
                .defaultDepartmentId(source.getDefaultDepartmentId())
                .defaultResponsibleId(source.getDefaultResponsibleId())
                .defaultPriority(source.getDefaultPriority())
                .requiresApproval(source.isRequiresApproval())
                .approvalRole(source.getApprovalRole())
                .approvalPermission(source.getApprovalPermission())
                .build();
        copy.setId(source.getId());
        copy.setCreatedAt(source.getCreatedAt());
        copy.setUpdatedAt(source.getUpdatedAt());
        copy.setDeleted(source.isDeleted());
        return copy;
    }

    private MaintenanceRegulationFilter safeFilter(MaintenanceRegulationFilter filter) {
        return filter == null
                ? new MaintenanceRegulationFilter(null, null, null, null)
                : filter;
    }

    private RestException readFailure(DataAccessException exception) {
        log.error("Failed to load filtered maintenance regulation data", exception);
        return new RestException(
                "Unable to load maintenance regulations",
                HttpStatus.INTERNAL_SERVER_ERROR
        );
    }

    private List<MaintenanceRegulationDto> mapGeneralPage(
            List<MaintenanceRegulationReadRepository.GeneralKey> keys
    ) {
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }
        Set<UUID> regulationIds = keys.stream()
                .filter(key -> key.source() == MaintenanceRegulationReadRepository.DisplaySource.REGULATION)
                .map(MaintenanceRegulationReadRepository.GeneralKey::displayId)
                .collect(Collectors.toSet());
        Set<UUID> ruleIds = keys.stream()
                .filter(key -> key.source() == MaintenanceRegulationReadRepository.DisplaySource.EQUIPMENT_RULE)
                .map(MaintenanceRegulationReadRepository.GeneralKey::displayId)
                .collect(Collectors.toSet());

        Map<UUID, MaintenanceRegulationDto> regulationDtos = toDtoList(
                regulationIds.isEmpty()
                        ? List.of()
                        : safeList(repository.findAllByIdInAndIsDeletedFalse(regulationIds))
        ).stream().collect(Collectors.toMap(
                MaintenanceRegulationDto::id,
                item -> item,
                (left, right) -> left
        ));
        Map<UUID, MaintenanceRegulationDto> ruleDtos = standaloneRuleDtos(
                ruleIds.isEmpty()
                        ? List.of()
                        : safeList(equipmentMaintenanceRuleRepository.findAllByIdInAndIsDeletedFalse(ruleIds))
        );

        return keys.stream()
                .map(key -> key.source() == MaintenanceRegulationReadRepository.DisplaySource.REGULATION
                        ? regulationDtos.get(key.displayId())
                        : ruleDtos.get(key.displayId()))
                .filter(Objects::nonNull)
                .toList();
    }

    private Map<UUID, MaintenanceRegulationDto> standaloneRuleDtos(
            List<EquipmentMaintenanceRule> rules
    ) {
        if (rules == null || rules.isEmpty()) {
            return Map.of();
        }
        Set<UUID> equipmentIds = rules.stream()
                .map(EquipmentMaintenanceRule::getEquipmentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, Equipment> equipmentById = equipmentIds.isEmpty()
                ? Map.of()
                : safeList(equipmentRepository.findAllByIdInAndIsDeletedFalse(equipmentIds))
                .stream()
                .filter(item -> item != null && item.getId() != null)
                .collect(Collectors.toMap(Equipment::getId, item -> item, (left, right) -> left));
        Set<UUID> typeIds = equipmentById.values().stream()
                .map(Equipment::getEquipmentTypeId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, String> typeNames = typeIds.isEmpty()
                ? Map.of()
                : safeList(equipmentTypeRepository.findAllByIdInAndIsDeletedFalse(typeIds))
                .stream()
                .collect(Collectors.toMap(EquipmentType::getId, EquipmentType::getName, (left, right) -> left));
        Set<UUID> templateIds = rules.stream()
                .map(EquipmentMaintenanceRule::getTemplateId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, MaintenanceTemplate> templates = templateIds.isEmpty()
                ? Map.of()
                : safeList(templateRepository.findAllByIdInAndIsDeletedFalse(templateIds))
                .stream()
                .collect(Collectors.toMap(MaintenanceTemplate::getId, item -> item, (left, right) -> left));

        return rules.stream()
                .filter(rule -> rule != null && rule.getId() != null)
                .map(rule -> {
                    Equipment equipment = equipmentById.get(rule.getEquipmentId());
                    UUID typeId = equipment == null ? null : equipment.getEquipmentTypeId();
                    MaintenanceTemplate template = templates.get(rule.getTemplateId());
                    return MaintenanceRegulationDto.from(
                            rule,
                            typeId,
                            typeNames.get(typeId),
                            equipment == null ? null : equipment.getName(),
                            template == null ? null : template.getCode(),
                            template == null ? null : template.getName()
                    );
                })
                .collect(Collectors.toMap(
                        MaintenanceRegulationDto::id,
                        item -> item,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    private List<EquipmentWithRegulationsDto> mapEquipmentPage(
            MaintenanceRegulationFilter filter,
            List<UUID> equipmentIds
    ) {
        if (equipmentIds == null || equipmentIds.isEmpty()) {
            return List.of();
        }
        List<MaintenanceRegulationReadRepository.EquipmentMatch> matches =
                safeList(readRepository.findEquipmentMatches(filter, equipmentIds));
        if (matches.isEmpty()) {
            return List.of();
        }
        Map<UUID, LinkedHashMap<DisplayKey, MaintenanceRegulationReadRepository.EquipmentMatch>> grouped =
                new LinkedHashMap<>();
        for (MaintenanceRegulationReadRepository.EquipmentMatch match : matches) {
            if (match == null || match.equipmentId() == null || match.displayId() == null) {
                continue;
            }
            grouped.computeIfAbsent(match.equipmentId(), ignored -> new LinkedHashMap<>())
                    .putIfAbsent(new DisplayKey(match.source(), match.displayId()), match);
        }
        if (grouped.isEmpty()) {
            return List.of();
        }
        Map<UUID, Equipment> equipmentById = safeList(
                equipmentRepository.findAllByIdInAndIsDeletedFalse(grouped.keySet())
        ).stream()
                .filter(item -> item != null && item.getId() != null)
                .collect(Collectors.toMap(Equipment::getId, item -> item, (left, right) -> left));
        Map<UUID, EquipmentType> typeById = equipmentTypesById(equipmentById.values().stream()
                .map(Equipment::getEquipmentTypeId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        DisplayLookup lookup = displayLookup(matches.stream()
                .map(match -> new DisplayKey(match.source(), match.displayId()))
                .collect(Collectors.toSet()));

        return equipmentIds.stream()
                .map(equipmentId -> {
                    Equipment equipment = equipmentById.get(equipmentId);
                    LinkedHashMap<DisplayKey, MaintenanceRegulationReadRepository.EquipmentMatch> itemMatches =
                            grouped.get(equipmentId);
                    if (equipment == null || itemMatches == null || itemMatches.isEmpty()) {
                        return null;
                    }
                    List<MaintenanceRegulationSummaryDto> summaries = itemMatches.values().stream()
                            .map(match -> summary(
                                    new DisplayKey(match.source(), match.displayId()),
                                    match.effectiveActive(),
                                    lookup
                            ))
                            .filter(Objects::nonNull)
                            .toList();
                    if (summaries.isEmpty()) {
                        return null;
                    }
                    EquipmentType type = typeById.get(equipment.getEquipmentTypeId());
                    return new EquipmentWithRegulationsDto(
                            equipment.getId(),
                            equipment.getName(),
                            equipment.getCode(),
                            equipment.getEquipmentTypeId(),
                            type == null ? null : type.getName(),
                            summaries
                    );
                })
                .filter(Objects::nonNull)
                .toList();
    }

    private List<EquipmentTypeWithRegulationsDto> mapEquipmentTypePage(
            MaintenanceRegulationFilter filter,
            List<UUID> equipmentTypeIds
    ) {
        if (equipmentTypeIds == null || equipmentTypeIds.isEmpty()) {
            return List.of();
        }
        List<MaintenanceRegulationReadRepository.EquipmentTypeMatch> matches =
                safeList(readRepository.findEquipmentTypeMatches(filter, equipmentTypeIds));
        if (matches.isEmpty()) {
            return List.of();
        }
        Map<UUID, LinkedHashMap<DisplayKey, MaintenanceRegulationReadRepository.EquipmentTypeMatch>> grouped =
                new LinkedHashMap<>();
        for (MaintenanceRegulationReadRepository.EquipmentTypeMatch match : matches) {
            if (match == null || match.equipmentTypeId() == null || match.displayId() == null) {
                continue;
            }
            grouped.computeIfAbsent(match.equipmentTypeId(), ignored -> new LinkedHashMap<>())
                    .putIfAbsent(new DisplayKey(match.source(), match.displayId()), match);
        }
        Map<UUID, EquipmentType> typeById = equipmentTypesById(grouped.keySet());
        Map<UUID, Integer> equipmentCounts =
                readRepository.countMatchingEquipmentByType(filter, equipmentTypeIds);
        DisplayLookup lookup = displayLookup(matches.stream()
                .map(match -> new DisplayKey(match.source(), match.displayId()))
                .collect(Collectors.toSet()));

        return equipmentTypeIds.stream()
                .map(typeId -> {
                    EquipmentType type = typeById.get(typeId);
                    LinkedHashMap<DisplayKey, MaintenanceRegulationReadRepository.EquipmentTypeMatch> itemMatches =
                            grouped.get(typeId);
                    if (type == null || itemMatches == null || itemMatches.isEmpty()) {
                        return null;
                    }
                    List<MaintenanceRegulationSummaryDto> summaries = itemMatches.values().stream()
                            .map(match -> summary(
                                    new DisplayKey(match.source(), match.displayId()),
                                    match.effectiveActive(),
                                    lookup
                            ))
                            .filter(Objects::nonNull)
                            .toList();
                    if (summaries.isEmpty()) {
                        return null;
                    }
                    return new EquipmentTypeWithRegulationsDto(
                            type.getId(),
                            type.getCode(),
                            type.getName(),
                            type.getCategory(),
                            equipmentCounts.getOrDefault(type.getId(), 0),
                            summaries
                    );
                })
                .filter(Objects::nonNull)
                .toList();
    }

    private Map<UUID, EquipmentType> equipmentTypesById(Set<UUID> typeIds) {
        if (typeIds == null || typeIds.isEmpty()) {
            return Map.of();
        }
        return safeList(equipmentTypeRepository.findAllByIdInAndIsDeletedFalse(typeIds))
                .stream()
                .filter(item -> item != null && item.getId() != null)
                .collect(Collectors.toMap(
                        EquipmentType::getId,
                        item -> item,
                        (left, right) -> left
                ));
    }

    private DisplayLookup displayLookup(Set<DisplayKey> keys) {
        Set<UUID> regulationIds = keys.stream()
                .filter(key -> key.source() == MaintenanceRegulationReadRepository.DisplaySource.REGULATION)
                .map(DisplayKey::id)
                .collect(Collectors.toSet());
        Set<UUID> ruleIds = keys.stream()
                .filter(key -> key.source() == MaintenanceRegulationReadRepository.DisplaySource.EQUIPMENT_RULE)
                .map(DisplayKey::id)
                .collect(Collectors.toSet());
        Map<UUID, MaintenanceRegulation> regulations = regulationIds.isEmpty()
                ? Map.of()
                : safeList(repository.findAllByIdInAndIsDeletedFalse(regulationIds))
                .stream()
                .collect(Collectors.toMap(MaintenanceRegulation::getId, item -> item, (left, right) -> left));
        Map<UUID, EquipmentMaintenanceRule> rules = ruleIds.isEmpty()
                ? Map.of()
                : safeList(equipmentMaintenanceRuleRepository.findAllByIdInAndIsDeletedFalse(ruleIds))
                .stream()
                .collect(Collectors.toMap(EquipmentMaintenanceRule::getId, item -> item, (left, right) -> left));
        Set<UUID> templateIds = new java.util.HashSet<>();
        regulations.values().stream()
                .map(MaintenanceRegulation::getTemplateId)
                .filter(Objects::nonNull)
                .forEach(templateIds::add);
        rules.values().stream()
                .map(EquipmentMaintenanceRule::getTemplateId)
                .filter(Objects::nonNull)
                .forEach(templateIds::add);
        return new DisplayLookup(regulations, rules, operationSummaryByTemplateId(templateIds));
    }

    private MaintenanceRegulationSummaryDto summary(
            DisplayKey key,
            boolean effectiveActive,
            DisplayLookup lookup
    ) {
        if (key.source() == MaintenanceRegulationReadRepository.DisplaySource.REGULATION) {
            MaintenanceRegulation regulation = lookup.regulations().get(key.id());
            return regulation == null
                    ? null
                    : MaintenanceRegulationSummaryDto.from(
                            regulation,
                            operationSummary(regulation, lookup.operationSummaries()),
                            effectiveActive
                    );
        }
        EquipmentMaintenanceRule rule = lookup.rules().get(key.id());
        return rule == null
                ? null
                : MaintenanceRegulationSummaryDto.from(
                        rule,
                        operationSummary(rule, lookup.operationSummaries()),
                        effectiveActive
                );
    }

    private record DisplayKey(
            MaintenanceRegulationReadRepository.DisplaySource source,
            UUID id
    ) {}

    private record DisplayLookup(
            Map<UUID, MaintenanceRegulation> regulations,
            Map<UUID, EquipmentMaintenanceRule> rules,
            Map<UUID, MaintenanceRegulationSummaryDto.OperationSummary> operationSummaries
    ) {}

    private MaintenanceRegulationSummaryDto.OperationSummary operationSummary(
            MaintenanceRegulation regulation,
            Map<UUID, MaintenanceRegulationSummaryDto.OperationSummary> operationSummaryByTemplateId) {
        if (regulation == null || regulation.getTemplateId() == null) {
            return null;
        }
        return operationSummaryByTemplateId.get(regulation.getTemplateId());
    }

    private MaintenanceRegulationSummaryDto.OperationSummary operationSummary(
            EquipmentMaintenanceRule rule,
            Map<UUID, MaintenanceRegulationSummaryDto.OperationSummary> operationSummaryByTemplateId) {
        if (rule == null || rule.getTemplateId() == null) {
            return null;
        }
        return operationSummaryByTemplateId.get(rule.getTemplateId());
    }

    private Map<UUID, MaintenanceRegulationSummaryDto.OperationSummary> operationSummaryByTemplateId(Set<UUID> templateIds) {
        if (templateIds.isEmpty()) {
            return Map.of();
        }
        return safeList(operationRepository.findAllByTemplateIdInAndIsDeletedFalse(templateIds))
                .stream()
                .filter(Objects::nonNull)
                .filter(operation -> operation.getTemplate() != null && operation.getTemplate().getId() != null)
                .collect(Collectors.groupingBy(operation -> operation.getTemplate().getId()))
                .entrySet()
                .stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> new MaintenanceRegulationSummaryDto.OperationSummary(
                                joinDistinct(entry.getValue(), MaintenanceOperation::getRequiredSkill),
                                joinDistinct(entry.getValue(), MaintenanceOperation::getSafetyNotes),
                                joinDistinct(entry.getValue(), MaintenanceOperation::getToolsRequired),
                                joinDistinct(entry.getValue(), MaintenanceOperation::getSparePartsRequired),
                                joinDistinct(entry.getValue(), MaintenanceOperation::getConsumablesRequired)
                        )
                ));
    }

    private String joinDistinct(List<MaintenanceOperation> operations,
                                java.util.function.Function<MaintenanceOperation, String> getter) {
        String joined = operations.stream()
                .map(getter)
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .collect(Collectors.joining("; "));
        return joined.isBlank() ? null : joined;
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private void validateEquipmentTypeIfProvided(UUID equipmentTypeId) {
        if (equipmentTypeId == null) {
            return;
        }
        if (!equipmentTypeRepository.existsByIdAndIsDeletedFalse(equipmentTypeId)) {
            throw RestException.notFound("Equipment type not found: " + equipmentTypeId);
        }
    }

    private void applyMutableFields(MaintenanceRegulation entity, MaintenanceRegulationRequest request) {
        entity.setName(request.name());
        entity.setDescription(request.description());
        entity.setEquipmentTypeId(request.equipmentTypeId());
        entity.setMaintenanceKind(request.maintenanceKind());
        entity.setTemplateId(validatedTemplateId(request));
        entity.setNormativeLaborHours(request.normativeLaborHours());
        if (request.active() != null) entity.setActive(request.active());
        entity.setPeriodicityUnit(request.periodicityUnit());
        entity.setPeriodicityValue(request.periodicityValue());
        entity.setToleranceDays(request.toleranceDays());
        entity.setRequiresShutdown(request.requiresShutdown());
        entity.setTriggerMeterType(request.triggerMeterType());
        entity.setTriggerMeterInterval(request.triggerMeterInterval());
        entity.setTriggerPolicy(request.triggerPolicy() == null ? MaintenanceTriggerPolicy.ANY : request.triggerPolicy());
        entity.setRecalculationPolicy(request.recalculationPolicy() == null
                ? MaintenanceRecalculationPolicy.FROM_ACTUAL_COMPLETION
                : request.recalculationPolicy());
        entity.setInitialSchedulePolicy(request.initialSchedulePolicy() == null
                ? MaintenanceInitialSchedulePolicy.FROM_OPERATION_START
                : request.initialSchedulePolicy());
        entity.setAutomationAction(request.automationAction() == null
                ? AutomationAction.REQUIRE_APPROVAL
                : request.automationAction());
        entity.setApprovalResultAction(request.approvalResultAction() == null
                ? ApprovalResultAction.CREATE_TASK
                : request.approvalResultAction());
        entity.setDuplicatePolicy(request.duplicatePolicy() == null
                ? DuplicatePolicy.ONE_ITEM_PER_CYCLE
                : request.duplicatePolicy());
        entity.setLeadTimeDays(request.leadTimeDays());
        entity.setLeadMeterPercent(request.leadMeterPercent());
        entity.setDefaultDepartmentId(request.defaultDepartmentId());
        entity.setDefaultResponsibleId(request.defaultResponsibleId());
        entity.setDefaultPriority(request.defaultPriority());
        entity.setApprovalRole(blankToNull(request.approvalRole()));
        entity.setApprovalPermission(blankToNull(request.approvalPermission()));
        validateAutomationTemplate(entity);
    }

    private void replaceConditions(UUID regulationId,
                                   UUID equipmentTypeId,
                                   List<MaintenanceRegulationAttributeConditionRequest> requests) {
        if (requests == null) {
            return;
        }
        if (conditionRepository == null) {
            return;
        }
        List<MaintenanceRegulationAttributeCondition> existing =
                conditionRepository.findAllByRegulationIdAndIsDeletedFalse(regulationId);
        for (MaintenanceRegulationAttributeCondition condition : existing) {
            condition.setDeleted(true);
        }
        if (!existing.isEmpty()) {
            conditionRepository.saveAll(existing);
        }

        List<MaintenanceRegulationAttributeCondition> toSave = requests.stream()
                .map(request -> toCondition(regulationId, equipmentTypeId, request))
                .toList();
        if (!toSave.isEmpty()) {
            conditionRepository.saveAll(toSave);
        }
    }

    private List<ValidatedRegulationSparePartRequirement> validateSparePartRequests(
            List<MaintenanceRegulationSparePartRequirementRequest> requests) {
        if (requests == null) {
            return null;
        }
        List<ValidatedRegulationSparePartRequirement> validated = new ArrayList<>();
        Set<UUID> activeSparePartIds = new HashSet<>();
        for (MaintenanceRegulationSparePartRequirementRequest request : requests) {
            if (request == null) {
                throw RestException.badRequest("Spare part requirement is required");
            }
            if (request.sparePartId() == null) {
                throw RestException.badRequest("sparePartId is required");
            }
            if (request.quantity()==null||request.quantity().signum() <= 0
                    || request.quantity().stripTrailingZeros().scale()>4
                    || request.quantity().precision()-request.quantity().scale()>15) {
                throw RestException.badRequest("MATERIAL_QUANTITY_INVALID");
            }
            SparePart sparePart = sparePartRepository.findByIdAndIsDeletedFalse(request.sparePartId())
                    .orElseThrow(() -> RestException.notFound("Spare part not found: " + request.sparePartId()));
            String sparePartUnit = StringUtils.hasText(sparePart.getUnit()) ? sparePart.getUnit().trim() : null;
            String requestedUnit = StringUtils.hasText(request.unit()) ? request.unit().trim() : null;
            if (requestedUnit != null && !requestedUnit.equals(sparePartUnit)) {
                throw RestException.badRequest("unit must match spare part unit");
            }
            boolean active = request.active() == null || request.active();
            if (active && !activeSparePartIds.add(request.sparePartId())) {
                throw RestException.badRequest("Maintenance regulation spare part requirement already exists");
            }
            validated.add(new ValidatedRegulationSparePartRequirement(
                    request,
                    sparePart,
                    sparePartUnit,
                    active
            ));
        }
        return validated;
    }

    private void replaceSparePartRequirements(
            MaintenanceRegulation regulation,
            List<MaintenanceRegulationSparePartRequirementRequest> requests,
            List<ValidatedRegulationSparePartRequirement> validatedRequests
    ) {
        if (requests == null) {
            return;
        }
        UUID regulationId = regulation.getId();
        List<MaintenanceRegulationSparePartRequirement> existing =
                sparePartRequirementRepository.findActiveByRegulationId(regulationId);
        if (!existing.isEmpty()) {
            for (MaintenanceRegulationSparePartRequirement requirement : existing) {
                requirement.setActive(false);
                requirement.setDeleted(true);
            }
            sparePartRequirementRepository.saveAll(existing);
            sparePartRequirementRepository.flush();
        }
        if (validatedRequests == null || validatedRequests.isEmpty()) {
            return;
        }
        List<MaintenanceRegulationSparePartRequirement> toSave = validatedRequests.stream()
                .map(validated -> toSparePartRequirement(regulation, validated))
                .toList();
        if (!toSave.isEmpty()) {
            sparePartRequirementRepository.saveAll(toSave);
        }
    }

    private MaintenanceRegulationSparePartRequirement toSparePartRequirement(
            MaintenanceRegulation regulation,
            ValidatedRegulationSparePartRequirement validated
    ) {
        MaintenanceRegulationSparePartRequirement requirement =
                new MaintenanceRegulationSparePartRequirement();
        requirement.setRegulation(regulation);
        requirement.setRegulationId(regulation.getId());
        requirement.setSparePart(validated.sparePart());
        requirement.setSparePartId(validated.sparePart().getId());
        requirement.setQuantity(validated.request().quantity());
        requirement.setUnit(validated.unit());
        requirement.setCriticality(blankToNull(validated.request().criticality()));
        requirement.setNotes(blankToNull(validated.request().notes()));
        requirement.setActive(validated.active());
        return requirement;
    }

    private MaintenanceRegulationAttributeCondition toCondition(UUID regulationId,
                                                               UUID equipmentTypeId,
                                                               MaintenanceRegulationAttributeConditionRequest request) {
        if (request == null) {
            throw RestException.badRequest("Attribute condition is required");
        }
        String key = normalizeAttributeKey(request.attributeKey());
        if (!attributeDefinitionRepository.existsActiveByEquipmentTypeIdAndKey(equipmentTypeId, key)) {
            throw RestException.badRequest("Unknown equipment attribute key for regulation type: " + key);
        }
        validateConditionValue(request);

        MaintenanceRegulationAttributeCondition condition = new MaintenanceRegulationAttributeCondition();
        condition.setRegulationId(regulationId);
        condition.setAttributeKey(key);
        condition.setOperator(request.operator());
        condition.setValueText(request.valueText());
        condition.setValueNumber(request.valueNumber());
        condition.setValueDate(request.valueDate());
        condition.setValueBoolean(request.valueBoolean());
        condition.setValueOption(request.valueOption());
        return condition;
    }

    private void validateConditionValue(MaintenanceRegulationAttributeConditionRequest request) {
        if (request.operator() == null) {
            throw RestException.badRequest("Condition operator is required");
        }
        if (request.operator() == MaintenanceRegulationConditionOperator.EXISTS
                || request.operator() == MaintenanceRegulationConditionOperator.NOT_EXISTS) {
            return;
        }
        boolean hasValue = request.valueText() != null
                || request.valueNumber() != null
                || request.valueDate() != null
                || request.valueBoolean() != null
                || request.valueOption() != null;
        if (!hasValue) {
            throw RestException.badRequest("Condition value is required");
        }
    }

    private String normalizeAttributeKey(String key) {
        if (key == null || key.isBlank()) {
            throw RestException.badRequest("Attribute condition key is required");
        }
        return key.trim().toLowerCase(Locale.ROOT);
    }

    private void validateClientProvidedCode(String code) {
        if (code != null && !code.isBlank()) {
            throw RestException.badRequest(CLIENT_CODE_REJECT_MESSAGE);
        }
    }

    private void validateAutomationConfigurationPermissionForCreate(MaintenanceRegulationRequest request) {
        if (!containsNonDefaultAutomationConfiguration(request)) {
            return;
        }
        assertCanConfigureAutomation();
    }

    private void validateAutomationConfigurationPermissionForUpdate(MaintenanceRegulation existing,
                                                                    MaintenanceRegulationRequest request) {
        if (!changesAutomationConfiguration(existing, request)) {
            return;
        }
        assertCanConfigureAutomation();
    }

    private void assertCanConfigureAutomation() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!securityAccessService.hasPermission(authentication, PermissionConstants.MAINTENANCE_AUTOMATION_CONFIGURE)) {
            throw new AccessDeniedException("Missing permission: " + PermissionConstants.MAINTENANCE_AUTOMATION_CONFIGURE);
        }
    }

    private boolean containsNonDefaultAutomationConfiguration(MaintenanceRegulationRequest request) {
        return effectiveAutomationAction(request) != AutomationAction.REQUIRE_APPROVAL
                || effectiveApprovalResultAction(request) != ApprovalResultAction.CREATE_TASK
                || effectiveDuplicatePolicy(request) != DuplicatePolicy.ONE_ITEM_PER_CYCLE
                || request.leadTimeDays() != null
                || request.leadMeterPercent() != null
                || request.defaultDepartmentId() != null
                || request.defaultResponsibleId() != null
                || effectiveDefaultPriority(request) != PriorityLevel.MEDIUM
                || blankToNull(request.approvalRole()) != null
                || blankToNull(request.approvalPermission()) != null;
    }

    private boolean changesAutomationConfiguration(MaintenanceRegulation existing,
                                                   MaintenanceRegulationRequest request) {
        return effectiveAutomationAction(request) != effectiveAutomationAction(existing)
                || effectiveApprovalResultAction(request) != effectiveApprovalResultAction(existing)
                || effectiveDuplicatePolicy(request) != effectiveDuplicatePolicy(existing)
                || !Objects.equals(request.leadTimeDays(), existing.getLeadTimeDays())
                || !Objects.equals(request.leadMeterPercent(), existing.getLeadMeterPercent())
                || !Objects.equals(request.defaultDepartmentId(), existing.getDefaultDepartmentId())
                || !Objects.equals(request.defaultResponsibleId(), existing.getDefaultResponsibleId())
                || effectiveDefaultPriority(request) != effectiveDefaultPriority(existing)
                || !Objects.equals(blankToNull(request.approvalRole()), blankToNull(existing.getApprovalRole()))
                || !Objects.equals(blankToNull(request.approvalPermission()), blankToNull(existing.getApprovalPermission()));
    }

    private AutomationAction effectiveAutomationAction(MaintenanceRegulationRequest request) {
        return request.automationAction() == null ? AutomationAction.REQUIRE_APPROVAL : request.automationAction();
    }

    private AutomationAction effectiveAutomationAction(MaintenanceRegulation regulation) {
        return regulation.getAutomationAction() == null ? AutomationAction.REQUIRE_APPROVAL : regulation.getAutomationAction();
    }

    private ApprovalResultAction effectiveApprovalResultAction(MaintenanceRegulationRequest request) {
        return request.approvalResultAction() == null ? ApprovalResultAction.CREATE_TASK : request.approvalResultAction();
    }

    private ApprovalResultAction effectiveApprovalResultAction(MaintenanceRegulation regulation) {
        return regulation.getApprovalResultAction() == null
                ? ApprovalResultAction.CREATE_TASK
                : regulation.getApprovalResultAction();
    }

    private DuplicatePolicy effectiveDuplicatePolicy(MaintenanceRegulationRequest request) {
        return request.duplicatePolicy() == null ? DuplicatePolicy.ONE_ITEM_PER_CYCLE : request.duplicatePolicy();
    }

    private DuplicatePolicy effectiveDuplicatePolicy(MaintenanceRegulation regulation) {
        return regulation.getDuplicatePolicy() == null
                ? DuplicatePolicy.ONE_ITEM_PER_CYCLE
                : regulation.getDuplicatePolicy();
    }

    private PriorityLevel effectiveDefaultPriority(MaintenanceRegulationRequest request) {
        return request.defaultPriority() == null ? PriorityLevel.MEDIUM : request.defaultPriority();
    }

    private PriorityLevel effectiveDefaultPriority(MaintenanceRegulation regulation) {
        return regulation.getDefaultPriority() == null ? PriorityLevel.MEDIUM : regulation.getDefaultPriority();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private MaintenanceRegulation saveWithGeneratedCode(MaintenanceRegulationRequest request) {
        int year = Year.now().getValue();
        String codePrefix = "MR-" + year + "-";
        long sequence = repository.maxSequenceByCodePrefix(codePrefix) + 1;

        for (int attempt = 0; attempt < MAX_CODE_GENERATION_ATTEMPTS; attempt++) {
            String code = formatCode("MR", year, sequence + attempt);
            if (repository.existsByCode(code)) {
                continue;
            }

            MaintenanceRegulation entity = new MaintenanceRegulation();
            entity.setCode(code);
            applyMutableFields(entity, request);

            try {
                return repository.save(entity);
            } catch (DataIntegrityViolationException ex) {
                if (isCodeConflict(ex)) {
                    continue;
                }
                throw ex;
            }
        }

        throw RestException.conflict("Could not generate unique maintenance regulation code");
    }

    private String formatCode(String prefix, int year, long sequence) {
        return "%s-%d-%04d".formatted(prefix, year, sequence);
    }

    private boolean isCodeConflict(DataIntegrityViolationException ex) {
        Throwable root = ex.getMostSpecificCause();
        String message = root != null ? root.getMessage() : ex.getMessage();
        if (message == null) {
            return false;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        return normalized.contains("maintenance_regulations_code_key")
                || (normalized.contains("maintenance_regulations")
                && normalized.contains("duplicate")
                && normalized.contains("code"));
    }

    private MaintenanceRegulationDto toDto(MaintenanceRegulation r) {
        if (r == null) return null;
        List<MaintenanceRegulationAttributeConditionDto> conditions = conditionDtos(r.getId());
        List<MaintenanceRegulationSparePartRequirementDto> sparePartRequirements = sparePartDtos(r.getId());
        String equipmentTypeName = r.getEquipmentTypeId() == null ? null : equipmentTypeRepository.findByIdAndIsDeletedFalse(r.getEquipmentTypeId())
                .map(EquipmentType::getName)
                .orElse(null);
        MaintenanceTemplate template = r.getTemplateId() == null ? null : templateRepository.findByIdAndIsDeletedFalse(r.getTemplateId())
                .orElse(null);
        return MaintenanceRegulationDto.from(
                r,
                equipmentTypeName,
                template == null ? null : template.getCode(),
                template == null ? null : template.getName(),
                conditions,
                sparePartRequirements
        );
    }

    private UUID validatedTemplateId(MaintenanceRegulationRequest request) {
        UUID templateId = request.templateId();
        if (templateId == null) {
            return null;
        }

        MaintenanceTemplate template = templateRepository.findByIdAndIsDeletedFalse(templateId)
                .orElseThrow(() -> RestException.notFound("Maintenance template not found: " + templateId));
        boolean matchesEquipmentType = Objects.equals(template.getEquipmentTypeId(), request.equipmentTypeId())
                || (template.getEquipmentTypeIds() != null
                && template.getEquipmentTypeIds().contains(request.equipmentTypeId()));
        if (!matchesEquipmentType) {
            throw RestException.badRequest("Maintenance template equipment type must match regulation equipment type");
        }
        if (template.getMaintenanceKind() != request.maintenanceKind()) {
            throw RestException.badRequest("Maintenance template kind must match regulation maintenance kind");
        }
        if (!template.isActive()) {
            throw RestException.badRequest("Maintenance template must be active");
        }
        return templateId;
    }

    private void validateAutomationTemplate(MaintenanceRegulation entity) {
        // Template is optional for automation; template operations are copied only when present.
    }

    private List<MaintenanceRegulationDto> toDtoList(List<MaintenanceRegulation> regulations) {
        if (regulations == null || regulations.isEmpty()) return List.of();
        Set<UUID> eqTypeIds = regulations.stream()
                .map(MaintenanceRegulation::getEquipmentTypeId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, String> eqTypeNames = equipmentTypeRepository.findAllByIdInAndIsDeletedFalse(eqTypeIds).stream()
                .collect(Collectors.toMap(EquipmentType::getId, EquipmentType::getName));
        Set<UUID> templateIds = regulations.stream()
                .map(MaintenanceRegulation::getTemplateId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, MaintenanceTemplate> templates = templateIds.isEmpty()
                ? Map.of()
                : templateRepository.findAllByIdInAndIsDeletedFalse(templateIds).stream()
                .collect(Collectors.toMap(MaintenanceTemplate::getId, template -> template));
        Set<UUID> regulationIds = regulations.stream().map(MaintenanceRegulation::getId).collect(Collectors.toSet());
        Map<UUID, List<MaintenanceRegulationAttributeConditionDto>> conditionsByRegulationId =
                conditionDtosByRegulationId(regulationIds);
        Map<UUID, List<MaintenanceRegulationSparePartRequirementDto>> sparePartsByRegulationId =
                sparePartDtosByRegulationId(regulationIds);
        return regulations.stream()
                .map(r -> {
                    MaintenanceTemplate template = r.getTemplateId() == null ? null : templates.get(r.getTemplateId());
                    return MaintenanceRegulationDto.from(
                            r,
                            r.getEquipmentTypeId() == null ? null : eqTypeNames.get(r.getEquipmentTypeId()),
                            template == null ? null : template.getCode(),
                            template == null ? null : template.getName(),
                            conditionsByRegulationId.getOrDefault(r.getId(), List.of()),
                            sparePartsByRegulationId.getOrDefault(r.getId(), List.of())
                    );
                })
                .toList();
    }

    private List<MaintenanceRegulationAttributeConditionDto> conditionDtos(UUID regulationId) {
        if (conditionRepository == null) {
            return List.of();
        }
        List<MaintenanceRegulationAttributeCondition> conditions =
                conditionRepository.findAllByRegulationIdAndIsDeletedFalse(regulationId);
        if (conditions == null || conditions.isEmpty()) {
            return List.of();
        }
        return conditions.stream()
                .map(MaintenanceRegulationAttributeConditionDto::from)
                .toList();
    }

    private List<MaintenanceRegulationSparePartRequirementDto> sparePartDtos(UUID regulationId) {
        if (sparePartRequirementRepository == null) {
            return List.of();
        }
        List<MaintenanceRegulationSparePartRequirement> requirements =
                sparePartRequirementRepository.findActiveByRegulationId(regulationId);
        if (requirements == null || requirements.isEmpty()) {
            return List.of();
        }
        return requirements.stream()
                .map(MaintenanceRegulationSparePartRequirementDto::from)
                .toList();
    }

    private Map<UUID, List<MaintenanceRegulationAttributeConditionDto>> conditionDtosByRegulationId(Set<UUID> regulationIds) {
        if (conditionRepository == null || regulationIds.isEmpty()) {
            return Map.of();
        }
        List<MaintenanceRegulationAttributeCondition> conditions =
                conditionRepository.findAllByRegulationIdInAndIsDeletedFalse(regulationIds);
        if (conditions == null || conditions.isEmpty()) {
            return Map.of();
        }
        return conditions.stream()
                .collect(Collectors.groupingBy(
                        MaintenanceRegulationAttributeCondition::getRegulationId,
                        Collectors.mapping(MaintenanceRegulationAttributeConditionDto::from, Collectors.toList())
                ));
    }

    private Map<UUID, List<MaintenanceRegulationSparePartRequirementDto>> sparePartDtosByRegulationId(
            Set<UUID> regulationIds) {
        if (sparePartRequirementRepository == null || regulationIds.isEmpty()) {
            return Map.of();
        }
        List<MaintenanceRegulationSparePartRequirement> requirements =
                sparePartRequirementRepository.findAllActiveByRegulationIdIn(regulationIds);
        if (requirements == null || requirements.isEmpty()) {
            return Map.of();
        }
        return requirements.stream()
                .collect(Collectors.groupingBy(
                        MaintenanceRegulationSparePartRequirement::getRegulationId,
                        Collectors.mapping(MaintenanceRegulationSparePartRequirementDto::from, Collectors.toList())
                ));
    }

    private record ValidatedRegulationSparePartRequirement(
            MaintenanceRegulationSparePartRequirementRequest request,
            SparePart sparePart,
            String unit,
            boolean active
    ) {}
}
