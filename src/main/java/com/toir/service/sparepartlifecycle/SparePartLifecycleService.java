package com.toir.service.sparepartlifecycle;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toir.dto.sparepartlifecycle.AppliedLifeLimitSnapshot;
import com.toir.dto.sparepartlifecycle.AppliedLifeRuleSnapshot;
import com.toir.dto.sparepartlifecycle.CurrentMeterValue;
import com.toir.dto.sparepartlifecycle.InstallSparePartCommand;
import com.toir.dto.sparepartlifecycle.RemoveSparePartCommand;
import com.toir.dto.sparepartlifecycle.ReplaceSparePartCommand;
import com.toir.dto.sparepartlifecycle.SparePartLifecycleEvaluation;
import com.toir.dto.sparepartlifecycle.SparePartLifecycleEvaluationInput;
import com.toir.dto.sparepartlifecycle.SparePartLifecycleResult;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.equipment.EquipmentMeter;
import com.toir.entity.equipment.EquipmentNode;
import com.toir.entity.equipment.EquipmentSparePart;
import com.toir.entity.equipment.MeterReading;
import com.toir.entity.repair.RepairMaterialUsage;
import com.toir.entity.sparepartlifecycle.SparePartInstallation;
import com.toir.entity.sparepartlifecycle.SparePartInstallationMaterialAllocation;
import com.toir.entity.sparepartlifecycle.SparePartInstallationMeterBaseline;
import com.toir.entity.sparepartlifecycle.SparePartLifeLimit;
import com.toir.entity.sparepartlifecycle.SparePartLifeRule;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.enums.sparepartlifecycle.SparePartInstallationStatus;
import com.toir.enums.sparepartlifecycle.SparePartLifecycleCommandType;
import com.toir.enums.sparepartlifecycle.SparePartRemovalDisposition;
import com.toir.exception.RestException;
import com.toir.repository.MeterReadingRepository;
import com.toir.repository.SparePartRepository;
import com.toir.repository.equipment.EquipmentNodeRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.equipment.EquipmentSparePartRepository;
import com.toir.repository.repair.RepairMaterialUsageRepository;
import com.toir.repository.sparepartlifecycle.SparePartInstallationMaterialAllocationRepository;
import com.toir.repository.sparepartlifecycle.SparePartInstallationMeterBaselineRepository;
import com.toir.repository.sparepartlifecycle.SparePartInstallationRepository;
import com.toir.repository.sparepartlifecycle.SparePartLifeLimitRepository;
import com.toir.security.PermissionConstants;
import com.toir.security.ScopeAccessService;
import com.toir.util.AuditBuilderService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SparePartLifecycleService {

    private final SparePartLifecycleCommandCoordinator commandCoordinator;
    private final EquipmentRepository equipmentRepository;
    private final EquipmentNodeRepository equipmentNodeRepository;
    private final SparePartRepository sparePartRepository;
    private final EquipmentSparePartRepository equipmentSparePartRepository;
    private final SparePartInstallationRepository installationRepository;
    private final SparePartLifeRuleResolver ruleResolver;
    private final SparePartLifeLimitRepository limitRepository;
    private final CanonicalEquipmentMeterService canonicalMeterService;
    private final MeterReadingRepository meterReadingRepository;
    private final SparePartInstallationMeterBaselineRepository baselineRepository;
    private final RepairMaterialUsageRepository materialUsageRepository;
    private final SparePartInstallationMaterialAllocationRepository allocationRepository;
    private final SparePartLifecycleEvaluator evaluator;
    private final SparePartDueEventService dueEventService;
    private final ScopeAccessService scopeAccessService;
    private final SparePartSlotNormalizer slotNormalizer;
    private final AuditBuilderService auditBuilderService;
    private final ObjectMapper objectMapper;
    private final SparePartLifecycleOperationGuard operationGuard;

    @Transactional(readOnly = true)
    public SparePartInstallation get(UUID id) {
        return installationRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Spare-part installation not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<SparePartInstallation> current(UUID equipmentId) {
        return installationRepository.findAllByEquipmentIdAndStatusAndIsDeletedFalseOrderByInstalledAtDesc(
                equipmentId,
                SparePartInstallationStatus.ACTIVE
        );
    }

    @Transactional(readOnly = true)
    public List<SparePartInstallation> history(UUID equipmentId) {
        return installationRepository.findAllByEquipmentIdAndIsDeletedFalseOrderByInstalledAtDesc(equipmentId);
    }

    @Transactional
    public SparePartLifecycleResult install(UUID equipmentId,
                                            String idempotencyKey,
                                            UUID actorId,
                                            InstallSparePartCommand request) {
        validateInstallRequest(equipmentId, actorId, request);
        var handle = commandCoordinator.acquire(
                idempotencyKey,
                SparePartLifecycleCommandType.INSTALL,
                request,
                actorId,
                request.workOrderId()
        );
        if (handle.replay()) {
            SparePartInstallation replay = installationRepository
                    .findByIdAndIsDeletedFalse(handle.command().getResultInstallationId())
                    .orElseThrow(() -> RestException.conflict(
                            "IDEMPOTENCY_RESULT_MISSING: lifecycle command result no longer exists"));
            return new SparePartLifecycleResult(replay, null, true);
        }

        Equipment equipment = equipmentRepository.findByIdAndIsDeletedFalseForUpdate(equipmentId)
                .orElseThrow(() -> RestException.notFound("Equipment not found: " + equipmentId));
        scopeAccessService.assertCanAccessEquipmentScope(
                equipment.getResponsibleDepartmentId(),
                equipment.getDepartmentId()
        );
        operationGuard.assertAllowed(
                equipmentId,
                com.toir.enums.sparepartlifecycle.SparePartLifecycleOperation.SPARE_PART_INSTALL
        );
        validateNode(equipmentId, request.equipmentNodeId());
        String normalizedSlot = slotNormalizer.normalizeRequired(request.slotCode());
        String positionKey = slotNormalizer.positionKey(request.equipmentNodeId(), normalizedSlot);
        installationRepository.findActiveByEquipmentIdAndPositionKeyForUpdate(equipmentId, positionKey)
                .ifPresent(existing -> {
                    throw RestException.conflict("INSTALLATION_POSITION_OCCUPIED: an active part already occupies the position");
                });
        sparePartRepository.findByIdAndIsDeletedFalse(request.sparePartId())
                .orElseThrow(() -> RestException.notFound("Spare part not found: " + request.sparePartId()));
        String serialNumber = normalizeText(request.serialNumber());
        String lotNumber = normalizeText(request.lotNumber());
        validateSerial(request.sparePartId(), request.quantity(), serialNumber);
        validateBom(equipmentId, request.sparePartId(), request.externalSourceReason());
        RepairMaterialUsage materialUsage = validateMaterialUsage(request, serialNumber, lotNumber);

        Instant installedAt = request.installedAt() == null ? Instant.now() : request.installedAt();
        SparePartLifeRule rule = ruleResolver.resolve(
                request.sparePartId(),
                equipmentId,
                request.equipmentNodeId(),
                normalizedSlot,
                installedAt
        ).orElse(null);
        List<SparePartLifeLimit> limits = rule == null
                ? List.of()
                : limitRepository.findAllByRuleIdAndIsDeletedFalseOrderBySequenceAsc(rule.getId());
        CapturedRule captured = captureRule(equipmentId, rule, limits, installedAt);

        SparePartInstallation installation = new SparePartInstallation();
        installation.setEquipmentId(equipmentId);
        installation.setEquipmentNodeId(request.equipmentNodeId());
        installation.setNormalizedSlotCode(normalizedSlot);
        installation.setPositionKey(positionKey);
        installation.setPositionLabelSnapshot(normalizeText(request.positionLabel()));
        installation.setSparePartId(request.sparePartId());
        installation.setQuantity(request.quantity());
        installation.setSerialNumberSnapshot(serialNumber);
        installation.setLotNumberSnapshot(lotNumber);
        installation.setStatus(SparePartInstallationStatus.ACTIVE);
        installation.setInstalledAt(installedAt);
        installation.setInstallWorkOrderId(request.workOrderId());
        installation.setSourceMaterialUsageId(request.sourceMaterialUsageId());
        installation.setInstalledBy(actorId);
        installation.setAppliedLifeRuleId(rule == null ? null : rule.getId());
        installation.setAppliedRuleRevision(rule == null ? null : rule.getRevision());
        installation.setAppliedRuleSnapshot(captured.snapshot() == null ? null : toJson(captured.snapshot()));

        SparePartInstallation saved;
        try {
            saved = installationRepository.save(installation);
        } catch (DataIntegrityViolationException conflict) {
            throw RestException.conflict("INSTALLATION_CONFLICT: position, serial, or idempotency constraint was won concurrently");
        }
        UUID savedInstallationId = saved.getId();
        captured.baselines().forEach(baseline -> baseline.setInstallationId(savedInstallationId));
        if (!captured.baselines().isEmpty()) {
            baselineRepository.saveAll(captured.baselines());
        }
        if (materialUsage != null) {
            SparePartInstallationMaterialAllocation allocation = new SparePartInstallationMaterialAllocation();
            allocation.setInstallationId(saved.getId());
            allocation.setRepairMaterialUsageId(materialUsage.getId());
            allocation.setAllocatedQuantity(request.quantity());
            allocation.setSerialNumberSnapshot(serialNumber);
            allocation.setLotNumberSnapshot(lotNumber);
            allocationRepository.save(allocation);
        }

        SparePartLifecycleEvaluation evaluation = evaluator.evaluate(new SparePartLifecycleEvaluationInput(
                saved.getId(),
                installedAt,
                captured.snapshot(),
                captured.baselineValues(),
                captured.currentMeters(),
                installedAt,
                false
        ));
        saved.setLifecycleEvaluationState(evaluation.aggregateState());
        saved.setNextCalendarDueAt(evaluation.nextCalendarDueAt());
        saved.setLastEvaluatedAt(evaluation.evaluatedAt());
        saved.setEvaluationDetails(toJson(evaluation));
        saved = installationRepository.save(saved);
        dueEventService.applyEvaluation(saved, evaluation);

        auditBuilderService.log(
                "spare_part_installation",
                saved.getId().toString(),
                AuditAction.CREATE,
                AuditModule.SPARE_PART,
                "Spare part installed",
                null,
                saved
        );
        commandCoordinator.complete(handle.command(), saved.getId(), null);
        return new SparePartLifecycleResult(saved, null, false);
    }

    @Transactional
    public SparePartLifecycleResult remove(String idempotencyKey,
                                           UUID actorId,
                                           RemoveSparePartCommand request) {
        if (request == null || request.installationId() == null || actorId == null || request.disposition() == null) {
            throw RestException.badRequest(
                    "REMOVE_COMMAND_INVALID: installation, actor and disposition are required");
        }
        if (request.disposition() == SparePartRemovalDisposition.RETURN_TO_STOCK) {
            throw RestException.conflict(
                    "No safe transactional WMS return path is configured",
                    com.toir.exception.SparePartLifecycleErrorCodes.RETURN_TO_STOCK_UNSUPPORTED);
        }
        if (request.disposition() == SparePartRemovalDisposition.UNKNOWN
                && (request.reason() == null || request.reason().isBlank()
                || !scopeAccessService.hasAuthority(PermissionConstants.SPARE_PART_EXPIRY_OVERRIDE))) {
            throw RestException.conflict(
                    "UNKNOWN requires override permission and reason",
                    com.toir.exception.SparePartLifecycleErrorCodes.UNKNOWN_DISPOSITION_FORBIDDEN);
        }
        var handle = commandCoordinator.acquire(
                idempotencyKey,
                SparePartLifecycleCommandType.REMOVE,
                request,
                actorId,
                request.workOrderId()
        );
        if (handle.replay()) {
            SparePartInstallation replay = installationRepository
                    .findByIdAndIsDeletedFalse(handle.command().getResultRemovedInstallationId())
                    .orElseThrow(() -> RestException.conflict(
                            "IDEMPOTENCY_RESULT_MISSING: lifecycle command result no longer exists"));
            return new SparePartLifecycleResult(null, replay, true);
        }

        SparePartInstallation installation = installationRepository
                .findByIdAndIsDeletedFalseForUpdate(request.installationId())
                .orElseThrow(() -> RestException.notFound(
                        "Spare-part installation not found: " + request.installationId()));
        Equipment equipment = equipmentRepository.findByIdAndIsDeletedFalseForUpdate(installation.getEquipmentId())
                .orElseThrow(() -> RestException.notFound("Equipment not found: " + installation.getEquipmentId()));
        scopeAccessService.assertCanAccessEquipmentScope(
                equipment.getResponsibleDepartmentId(),
                equipment.getDepartmentId()
        );
        if (installation.getStatus() != SparePartInstallationStatus.ACTIVE) {
            throw RestException.conflict("INSTALLATION_NOT_ACTIVE: only an active installation can be removed");
        }
        Instant removedAt = request.removedAt() == null ? Instant.now() : request.removedAt();
        if (removedAt.isBefore(installation.getInstalledAt())) {
            throw RestException.badRequest("REMOVAL_TIME_INVALID: removedAt cannot precede installedAt");
        }
        installation.setStatus(SparePartInstallationStatus.REMOVED);
        installation.setRemovedAt(removedAt);
        installation.setRemoveWorkOrderId(request.workOrderId());
        installation.setRemovedBy(actorId);
        installation.setRemovalDisposition(request.disposition());
        installation.setRemovalReason(normalizeText(request.reason()));
        SparePartInstallation saved = installationRepository.save(installation);
        dueEventService.resolveForRemoval(saved.getId(), removedAt, actorId, null);
        auditBuilderService.log(
                "spare_part_installation",
                saved.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.SPARE_PART,
                "Spare part removed",
                null,
                saved
        );
        commandCoordinator.complete(handle.command(), null, saved.getId());
        return new SparePartLifecycleResult(null, saved, false);
    }

    @Transactional
    public SparePartLifecycleResult replace(String idempotencyKey,
                                            UUID actorId,
                                            ReplaceSparePartCommand request) {
        validateReplaceRequest(actorId, request);
        var handle = commandCoordinator.acquire(
                idempotencyKey,
                SparePartLifecycleCommandType.REPLACE,
                request,
                actorId,
                request.workOrderId()
        );
        if (handle.replay()) {
            SparePartInstallation replacement = installationRepository
                    .findByIdAndIsDeletedFalse(handle.command().getResultInstallationId())
                    .orElseThrow(() -> RestException.conflict(
                            "IDEMPOTENCY_RESULT_MISSING: replacement installation no longer exists"));
            SparePartInstallation replaced = installationRepository
                    .findByIdAndIsDeletedFalse(handle.command().getResultRemovedInstallationId())
                    .orElseThrow(() -> RestException.conflict(
                            "IDEMPOTENCY_RESULT_MISSING: replaced installation no longer exists"));
            return new SparePartLifecycleResult(replacement, replaced, true);
        }

        SparePartInstallation old = installationRepository
                .findByIdAndIsDeletedFalseForUpdate(request.oldInstallationId())
                .orElseThrow(() -> RestException.notFound(
                        "Spare-part installation not found: " + request.oldInstallationId()));
        UUID equipmentId = old.getEquipmentId();
        Equipment equipment = equipmentRepository.findByIdAndIsDeletedFalseForUpdate(equipmentId)
                .orElseThrow(() -> RestException.notFound("Equipment not found: " + equipmentId));
        scopeAccessService.assertCanAccessEquipmentScope(
                equipment.getResponsibleDepartmentId(),
                equipment.getDepartmentId()
        );
        if (old.getStatus() != SparePartInstallationStatus.ACTIVE) {
            throw RestException.conflict("INSTALLATION_NOT_ACTIVE: only an active installation can be replaced");
        }
        Instant replacedAt = request.replacedAt() == null ? Instant.now() : request.replacedAt();
        if (replacedAt.isBefore(old.getInstalledAt())) {
            throw RestException.badRequest("REPLACEMENT_TIME_INVALID: replacedAt cannot precede installedAt");
        }
        var newPart = request.newPart();
        sparePartRepository.findByIdAndIsDeletedFalse(newPart.sparePartId())
                .orElseThrow(() -> RestException.notFound("Spare part not found: " + newPart.sparePartId()));
        String serialNumber = normalizeText(newPart.serialNumber());
        String lotNumber = normalizeText(newPart.lotNumber());
        validateSerial(newPart.sparePartId(), newPart.quantity(), serialNumber);
        validateBom(old.getEquipmentId(), newPart.sparePartId(), newPart.externalSourceReason());
        InstallSparePartCommand materialCommand = new InstallSparePartCommand(
                old.getEquipmentNodeId(),
                old.getNormalizedSlotCode(),
                old.getPositionLabelSnapshot(),
                newPart.sparePartId(),
                newPart.quantity(),
                serialNumber,
                lotNumber,
                replacedAt,
                request.workOrderId(),
                newPart.sourceMaterialUsageId(),
                newPart.externalSourceReason(),
                request.notes()
        );
        RepairMaterialUsage materialUsage = validateMaterialUsage(materialCommand, serialNumber, lotNumber);
        SparePartLifeRule rule = ruleResolver.resolve(
                newPart.sparePartId(),
                old.getEquipmentId(),
                old.getEquipmentNodeId(),
                old.getNormalizedSlotCode(),
                replacedAt
        ).orElse(null);
        List<SparePartLifeLimit> limits = rule == null
                ? List.of()
                : limitRepository.findAllByRuleIdAndIsDeletedFalseOrderBySequenceAsc(rule.getId());
        CapturedRule captured = captureRule(old.getEquipmentId(), rule, limits, replacedAt);
        UUID correlationId = UUID.randomUUID();

        old.setStatus(SparePartInstallationStatus.REPLACED);
        old.setRemovedAt(replacedAt);
        old.setRemoveWorkOrderId(request.workOrderId());
        old.setRemovedBy(actorId);
        old.setRemovalDisposition(request.oldPartDisposition());
        old.setRemovalReason(normalizeText(request.reason()));
        old.setReplacementCorrelationId(correlationId);
        installationRepository.save(old);
        installationRepository.flush();

        SparePartInstallation replacement = new SparePartInstallation();
        replacement.setEquipmentId(old.getEquipmentId());
        replacement.setEquipmentNodeId(old.getEquipmentNodeId());
        replacement.setNormalizedSlotCode(old.getNormalizedSlotCode());
        replacement.setPositionKey(old.getPositionKey());
        replacement.setPositionLabelSnapshot(old.getPositionLabelSnapshot());
        replacement.setSparePartId(newPart.sparePartId());
        replacement.setQuantity(newPart.quantity());
        replacement.setSerialNumberSnapshot(serialNumber);
        replacement.setLotNumberSnapshot(lotNumber);
        replacement.setStatus(SparePartInstallationStatus.ACTIVE);
        replacement.setInstalledAt(replacedAt);
        replacement.setInstallWorkOrderId(request.workOrderId());
        replacement.setSourceMaterialUsageId(newPart.sourceMaterialUsageId());
        replacement.setInstalledBy(actorId);
        replacement.setReplacesInstallationId(old.getId());
        replacement.setReplacementCorrelationId(correlationId);
        replacement.setAppliedLifeRuleId(rule == null ? null : rule.getId());
        replacement.setAppliedRuleRevision(rule == null ? null : rule.getRevision());
        replacement.setAppliedRuleSnapshot(toJson(captured.snapshot()));
        try {
            replacement = installationRepository.save(replacement);
        } catch (DataIntegrityViolationException conflict) {
            throw RestException.conflict(
                    "REPLACEMENT_CONFLICT: installation was replaced concurrently or the new serial was already used");
        }
        UUID replacementId = replacement.getId();
        captured.baselines().forEach(baseline -> baseline.setInstallationId(replacementId));
        if (!captured.baselines().isEmpty()) baselineRepository.saveAll(captured.baselines());
        if (materialUsage != null) {
            SparePartInstallationMaterialAllocation allocation = new SparePartInstallationMaterialAllocation();
            allocation.setInstallationId(replacementId);
            allocation.setRepairMaterialUsageId(materialUsage.getId());
            allocation.setAllocatedQuantity(newPart.quantity());
            allocation.setSerialNumberSnapshot(serialNumber);
            allocation.setLotNumberSnapshot(lotNumber);
            allocationRepository.save(allocation);
        }
        SparePartLifecycleEvaluation evaluation = evaluator.evaluate(new SparePartLifecycleEvaluationInput(
                replacementId,
                replacedAt,
                captured.snapshot(),
                captured.baselineValues(),
                captured.currentMeters(),
                replacedAt,
                false
        ));
        replacement.setLifecycleEvaluationState(evaluation.aggregateState());
        replacement.setNextCalendarDueAt(evaluation.nextCalendarDueAt());
        replacement.setLastEvaluatedAt(evaluation.evaluatedAt());
        replacement.setEvaluationDetails(toJson(evaluation));
        replacement = installationRepository.save(replacement);
        old.setReplacedByInstallationId(replacement.getId());
        old = installationRepository.save(old);
        dueEventService.resolveForRemoval(old.getId(), replacedAt, actorId, replacement.getId());
        dueEventService.applyEvaluation(replacement, evaluation);
        auditBuilderService.log(
                "spare_part_installation",
                old.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.SPARE_PART,
                "Spare part replaced",
                null,
                old
        );
        auditBuilderService.log(
                "spare_part_installation",
                replacement.getId().toString(),
                AuditAction.CREATE,
                AuditModule.SPARE_PART,
                "Replacement spare part installed",
                null,
                replacement
        );
        commandCoordinator.complete(handle.command(), replacement.getId(), old.getId());
        return new SparePartLifecycleResult(replacement, old, false);
    }

    private void validateReplaceRequest(UUID actorId, ReplaceSparePartCommand request) {
        if (actorId == null || request == null || request.oldInstallationId() == null
                || request.newPart() == null || request.newPart().sparePartId() == null
                || request.newPart().quantity() == null
                || request.newPart().quantity().compareTo(BigDecimal.ZERO) <= 0
                || request.oldPartDisposition() == null) {
            throw RestException.badRequest(
                    "REPLACE_COMMAND_INVALID: old installation, new part, positive quantity, actor and disposition are required");
        }
        if (request.oldPartDisposition() == SparePartRemovalDisposition.RETURN_TO_STOCK) {
            throw RestException.conflict(
                    "No safe transactional WMS return path is configured",
                    com.toir.exception.SparePartLifecycleErrorCodes.RETURN_TO_STOCK_UNSUPPORTED);
        }
        if (request.oldPartDisposition() == SparePartRemovalDisposition.UNKNOWN
                && (request.reason() == null || request.reason().isBlank()
                || !scopeAccessService.hasAuthority(PermissionConstants.SPARE_PART_EXPIRY_OVERRIDE))) {
            throw RestException.conflict(
                    "UNKNOWN requires override permission and reason",
                    com.toir.exception.SparePartLifecycleErrorCodes.UNKNOWN_DISPOSITION_FORBIDDEN);
        }
    }

    private void validateInstallRequest(UUID equipmentId, UUID actorId, InstallSparePartCommand request) {
        if (equipmentId == null || actorId == null || request == null || request.sparePartId() == null
                || request.quantity() == null || request.quantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw RestException.badRequest("INSTALL_COMMAND_INVALID: equipment, actor, part and positive quantity are required");
        }
    }

    private void validateNode(UUID equipmentId, UUID equipmentNodeId) {
        if (equipmentNodeId == null) {
            return;
        }
        EquipmentNode node = equipmentNodeRepository.findByIdAndIsDeletedFalse(equipmentNodeId)
                .orElseThrow(() -> RestException.notFound("Equipment node not found: " + equipmentNodeId));
        if (!Objects.equals(node.getEquipmentId(), equipmentId)) {
            throw RestException.conflict("INSTALLATION_NODE_MISMATCH: node does not belong to equipment");
        }
    }

    private void validateSerial(UUID sparePartId, BigDecimal quantity, String serialNumber) {
        if (serialNumber == null) {
            return;
        }
        if (quantity.compareTo(BigDecimal.ONE) != 0) {
            throw RestException.badRequest("SERIAL_QUANTITY_INVALID: serial-tracked installation quantity must equal one");
        }
        if (installationRepository.existsBySparePartIdAndSerialNumberSnapshotIgnoreCaseAndIsDeletedFalse(
                sparePartId, serialNumber)) {
            throw RestException.conflict("SERIAL_REUSE_UNSUPPORTED: serial was already used by an installation");
        }
    }

    private void validateBom(UUID equipmentId, UUID sparePartId, String externalSourceReason) {
        List<EquipmentSparePart> bom = equipmentSparePartRepository
                .findAllByEquipmentIdAndIsDeletedFalse(equipmentId);
        if (!bom.isEmpty() && bom.stream().noneMatch(item -> Objects.equals(item.getSparePartId(), sparePartId))) {
            if (!scopeAccessService.hasAuthority(PermissionConstants.SPARE_PART_EXPIRY_OVERRIDE)
                    || externalSourceReason == null || externalSourceReason.isBlank()) {
                throw RestException.conflict(
                        "BOM_INCOMPATIBLE: ad hoc installation requires override permission and a reason");
            }
        }
    }

    private RepairMaterialUsage validateMaterialUsage(InstallSparePartCommand request,
                                                       String serialNumber,
                                                       String lotNumber) {
        if (request.sourceMaterialUsageId() == null) {
            if (request.externalSourceReason() == null || request.externalSourceReason().isBlank()) {
                throw RestException.badRequest(
                        "INSTALLATION_SOURCE_REQUIRED: material usage or external source reason is required");
            }
            return null;
        }
        RepairMaterialUsage usage = materialUsageRepository
                .findByIdAndIsDeletedFalseForUpdate(request.sourceMaterialUsageId())
                .orElseThrow(() -> RestException.notFound(
                        "Repair material usage not found: " + request.sourceMaterialUsageId()));
        if (!Objects.equals(usage.getSparePartId(), request.sparePartId())
                || !Objects.equals(usage.getWorkOrderId(), request.workOrderId())
                || usage.getQuantity().compareTo(request.quantity()) < 0
                || (serialNumber != null && !Objects.equals(serialNumber, normalizeText(usage.getSerialNumber())))
                || (lotNumber != null && !Objects.equals(lotNumber, normalizeText(usage.getLotNumber())))) {
            throw RestException.conflict("MATERIAL_USAGE_MISMATCH: part, work order, quantity, serial or lot does not match");
        }
        if (allocationRepository.existsByRepairMaterialUsageIdAndIsDeletedFalse(usage.getId())) {
            throw RestException.conflict("MATERIAL_USAGE_ALREADY_ALLOCATED: issued material is already linked to an installation");
        }
        return usage;
    }

    private CapturedRule captureRule(UUID equipmentId,
                                     SparePartLifeRule rule,
                                     List<SparePartLifeLimit> limits,
                                     Instant capturedAt) {
        if (rule == null) {
            AppliedLifeRuleSnapshot manual = new AppliedLifeRuleSnapshot(
                    null,
                    0,
                    com.toir.enums.sparepartlifecycle.SparePartLifeCombinationMode.MANUAL,
                    com.toir.enums.sparepartlifecycle.SparePartDueAction.WARNING_ONLY,
                    List.of()
            );
            return new CapturedRule(manual, new ArrayList<>(), Map.of(), Map.of());
        }
        List<AppliedLifeLimitSnapshot> snapshots = new ArrayList<>();
        List<SparePartInstallationMeterBaseline> baselines = new ArrayList<>();
        Map<UUID, BigDecimal> baselineValues = new LinkedHashMap<>();
        Map<UUID, CurrentMeterValue> currentMeters = new LinkedHashMap<>();
        for (SparePartLifeLimit limit : limits) {
            UUID resolvedMeterId = null;
            if (limit.getLimitKind() == com.toir.enums.sparepartlifecycle.SparePartLifeLimitKind.METER) {
                EquipmentMeter meter = canonicalMeterService.resolve(
                        equipmentId,
                        limit.getMeterType(),
                        limit.getExplicitEquipmentMeterId()
                );
                resolvedMeterId = meter.getId();
                BigDecimal baselineValue = BigDecimal.valueOf(meter.getCurrentValue());
                MeterReading reading = meterReadingRepository
                        .findTopByMeterIdAndIsDeletedFalseOrderByReadAtDesc(meter.getId())
                        .orElse(null);
                SparePartInstallationMeterBaseline baseline = new SparePartInstallationMeterBaseline();
                baseline.setEquipmentMeterId(meter.getId());
                baseline.setMeterType(meter.getMeterType());
                baseline.setBaselineValue(baselineValue);
                baseline.setBaselineRecordedAt(capturedAt);
                baseline.setBaselineReadingId(reading == null ? null : reading.getId());
                baseline.setRolloverContext(meter.getRolloverValue() == null
                        ? null
                        : "{\"rolloverValue\":" + meter.getRolloverValue() + "}");
                baselines.add(baseline);
                baselineValues.put(meter.getId(), baselineValue);
                currentMeters.put(meter.getId(), new CurrentMeterValue(
                        baselineValue,
                        meter.isActive(),
                        meter.getRolloverValue() == null ? null : BigDecimal.valueOf(meter.getRolloverValue())
                ));
            }
            snapshots.add(new AppliedLifeLimitSnapshot(
                    limit.getId(),
                    limit.getLimitKind(),
                    limit.getCalendarUnit(),
                    limit.getMeterType(),
                    resolvedMeterId,
                    limit.getLimitValue(),
                    limit.getWarningBeforeValue(),
                    limit.getSequence()
            ));
        }
        return new CapturedRule(
                new AppliedLifeRuleSnapshot(
                        rule.getId(),
                        rule.getRevision(),
                        rule.getCombinationMode(),
                        rule.getDueAction(),
                        snapshots
                ),
                baselines,
                baselineValues,
                currentMeters
        );
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize spare-part lifecycle snapshot", exception);
        }
    }

    private static String normalizeText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record CapturedRule(AppliedLifeRuleSnapshot snapshot,
                                List<SparePartInstallationMeterBaseline> baselines,
                                Map<UUID, BigDecimal> baselineValues,
                                Map<UUID, CurrentMeterValue> currentMeters) {
    }
}
