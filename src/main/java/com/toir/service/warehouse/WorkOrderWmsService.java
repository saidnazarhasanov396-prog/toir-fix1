package com.toir.service.warehouse;

import com.toir.dto.materialusage.RepairMaterialUsageDto;
import com.toir.dto.warehouse.StockReceiptCommand;
import com.toir.dto.reservation.ReservationDto;
import com.toir.dto.reservation.ReservationRequest;
import com.toir.dto.warehouse.WarehouseTaskDto;
import com.toir.dto.warehouse.WarehouseTaskLineRequest;
import com.toir.dto.warehouse.WarehouseTaskRequest;
import com.toir.dto.workorder.WorkOrderMaterialReturnDto;
import com.toir.dto.workorder.WorkOrderMaterialReturnRequest;
import com.toir.dto.workorder.WorkOrderPickConfirmLineRequest;
import com.toir.dto.workorder.WorkOrderPickConfirmRequest;
import com.toir.dto.workorder.WorkOrderPickListRequest;
import com.toir.dto.workorder.WorkOrderWmsReservationLineRequest;
import com.toir.dto.workorder.WorkOrderWmsReservationRequest;
import com.toir.entity.InventoryTransaction;
import com.toir.entity.Reservation;
import com.toir.entity.StockMovement;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.maintenance.WorkOrderSparePartRequirement;
import com.toir.entity.projects.ActualCost;
import com.toir.entity.projects.CostCategory;
import com.toir.entity.repair.RepairMaterialUsage;
import com.toir.entity.warehouse.RepairMaterialReturn;
import com.toir.entity.warehouse.WarehouseStock;
import com.toir.entity.warehouse.WarehouseStockLedger;
import com.toir.entity.warehouse.WarehouseTask;
import com.toir.entity.warehouse.WarehouseTaskLine;
import com.toir.enums.ActualCostSourceType;
import com.toir.enums.ActualCostStatus;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.enums.InventoryTransactionType;
import com.toir.enums.RepairMaterialReturnStatus;
import com.toir.enums.ReservationStatus;
import com.toir.enums.StockLedgerMovementType;
import com.toir.enums.StockMovementSourceType;
import com.toir.enums.StockMovementType;
import com.toir.enums.WarehouseStockStatus;
import com.toir.enums.WarehouseTaskLineStatus;
import com.toir.enums.WarehouseTaskSourceType;
import com.toir.enums.WarehouseTaskStatus;
import com.toir.enums.WarehouseTaskType;
import com.toir.enums.WmsDocumentOperationType;
import com.toir.enums.WorkOrderSparePartRequirementStatus;
import com.toir.enums.WorkOrderStatus;
import com.toir.exception.RestException;
import com.toir.repository.InventoryTransactionRepository;
import com.toir.repository.RepairMaterialReturnRepository;
import com.toir.repository.CostCategoryRepository;
import com.toir.repository.ReservationRepository;
import com.toir.repository.StockMovementRepository;
import com.toir.repository.WarehouseTaskRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.actualCost.ActualCostRepository;
import com.toir.repository.maintenance.WorkOrderSparePartRequirementRepository;
import com.toir.repository.repair.RepairMaterialUsageRepository;
import com.toir.service.LowStockRecommendationService;
import com.toir.service.ReservationService;
import com.toir.service.repair.RepairCampaignBudgetLineResolver;
import com.toir.util.AuditBuilderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WorkOrderWmsService {

    private static final List<WorkOrderStatus> MATERIAL_ISSUE_ALLOWED_STATUSES = List.of(
            WorkOrderStatus.APPROVED,
            WorkOrderStatus.IN_PROGRESS
    );

    private final WorkOrderRepository workOrderRepository;
    private final WorkOrderSparePartRequirementRepository requirementRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationService reservationService;
    private final WarehouseTaskService taskService;
    private final WarehouseTaskRepository taskRepository;
    private final ToirStockService toirStockService;
    private final StockMovementRepository stockMovementRepository;
    private final RepairMaterialUsageRepository materialUsageRepository;
    private final RepairMaterialReturnRepository materialReturnRepository;
    private final InventoryTransactionRepository inventoryTransactionRepository;
    private final ActualCostRepository actualCostRepository;
    private final CostCategoryRepository costCategoryRepository;
    private final RepairCampaignBudgetLineResolver budgetLineResolver;
    private final LegacyStockProjectionService legacyStockProjectionService;
    private final LowStockRecommendationService lowStockRecommendationService;
    private final WmsDocumentPolicyService documentPolicyService;
    private final WmsStockCoordinateValidator coordinateValidator;
    private final AuditBuilderService auditBuilderService;

    @Transactional
    public List<ReservationDto> reserve(UUID workOrderId, WorkOrderWmsReservationRequest request) {
        WorkOrder workOrder = loadWorkOrderForIssue(workOrderId);
        validateReservationRequest(request);
        List<Reservation> activeReservations = activeReservations(workOrderId);
        return request.lines().stream()
                .map(line -> reserveLine(workOrder, line, request, activeReservations))
                .toList();
    }

    @Transactional
    public WarehouseTaskDto createPickList(UUID workOrderId, WorkOrderPickListRequest request) {
        WorkOrder workOrder = loadWorkOrderForIssue(workOrderId);
        List<Reservation> activeReservations = activeReservations(workOrderId);
        if (activeReservations.isEmpty()) {
            throw RestException.badRequest("Work order has no active reservations to pick");
        }
        UUID warehouseId = activeReservations.stream()
                .map(Reservation::getWarehouseId)
                .filter(Objects::nonNull)
                .findFirst()
                .orElseThrow(() -> RestException.badRequest("Active reservation warehouseId is required"));
        WarehouseTaskRequest taskRequest = new WarehouseTaskRequest(
                WarehouseTaskType.PICK,
                null,
                warehouseId,
                WarehouseTaskSourceType.WORK_ORDER,
                workOrder.getId(),
                request == null ? null : request.assignedToId(),
                null,
                request == null ? null : request.comment(),
                activeReservations.stream().map(this::pickLine).toList()
        );
        return taskService.create(taskRequest);
    }

    @Transactional
    public List<RepairMaterialUsageDto> confirmPick(UUID workOrderId,
                                                    UUID pickListId,
                                                    WorkOrderPickConfirmRequest request) {
        WorkOrder workOrder = loadWorkOrderForIssue(workOrderId);
        validateConfirmRequest(request);
        documentPolicyService.validateReceiptDocuments(
                WmsDocumentOperationType.WORK_ORDER_ISSUE,
                request.documentGroups(),
                false,
                false,
                request.strictDocumentPolicy()
        );
        WarehouseTask task = taskRepository.findByIdAndIsDeletedFalse(pickListId)
                .orElseThrow(() -> RestException.notFound("Pick list not found: " + pickListId));
        if (task.getTaskType() != WarehouseTaskType.PICK || !Objects.equals(task.getSourceId(), workOrderId)) {
            throw RestException.badRequest("Warehouse task is not a pick list for this work order");
        }
        if (task.getStatus() == WarehouseTaskStatus.DONE) {
            return existingUsages(request.lines());
        }
        List<RepairMaterialUsageDto> results = request.lines().stream()
                .map(line -> confirmLine(workOrder, task, line, request))
                .toList();
        task.getLines().forEach(line -> {
            line.setActualQty(line.getPlannedQty());
            line.setStatus(WarehouseTaskLineStatus.DONE);
            line.setScanConfirmed(true);
        });
        task.setStatus(WarehouseTaskStatus.DONE);
        task.setCompletedAt(Instant.now());
        taskRepository.save(task);
        return results;
    }

    @Transactional
    public WorkOrderMaterialReturnDto returnMaterial(UUID workOrderId, WorkOrderMaterialReturnRequest request) {
        WorkOrder workOrder = loadWorkOrderForIssue(workOrderId);
        validateReturnRequest(request);
        RepairMaterialUsage usage = materialUsageForReturn(request.materialUsageId(), workOrder.getId());
        validateReturnQuantity(usage, request.quantity());
        ReturnIdentity identity = resolveReturnIdentity(request, usage);

        documentPolicyService.validateReturnDocuments(
                request.reason(),
                identity.stockStatus(),
                request.documentGroups(),
                request.strictPolicyEnabled()
        );
        if (trimToNull(request.reason()) == null) {
            throw RestException.badRequest("Material return reason is required");
        }
        coordinateValidator.assertCanReceiveOrMoveInto(identity.warehouseId(), identity.binId(), identity.stockStatus());

        StockMovement movement = stockMovementRepository.save(returnMovement(workOrder, usage, identity, request));
        toirStockService.postIncrease(
                returnReceiptCommand(usage, identity, request, movement),
                StockLedgerMovementType.RETURN
        );
        InventoryTransaction transaction = inventoryTransactionRepository.save(
                returnInventoryTransaction(workOrder, usage, identity, request, movement)
        );
        RepairMaterialReturn saved = materialReturnRepository.save(
                materialReturn(workOrder, usage, identity, request, movement, transaction)
        );
        WarehouseStock stock = legacyStockProjectionService.sync(identity.warehouseId(), identity.sparePartId());
        lowStockRecommendationService.evaluateStockSafely(stock);

        auditBuilderService.log(
                "repair_material_return",
                saved.getId().toString(),
                AuditAction.CREATE,
                AuditModule.REPAIR_MATERIAL_RETURN,
                "Возврат материала из ремонта создан",
                null,
                saved
        );

        return WorkOrderMaterialReturnDto.from(saved);
    }

    private RepairMaterialUsage materialUsageForReturn(UUID materialUsageId, UUID workOrderId) {
        if (materialUsageId == null) {
            return null;
        }
        return materialUsageRepository.findByIdAndIsDeletedFalse(materialUsageId)
                .filter(usage -> Objects.equals(usage.getWorkOrderId(), workOrderId))
                .orElseThrow(() -> RestException.notFound("Material usage not found: " + materialUsageId));
    }

    private void validateReturnRequest(WorkOrderMaterialReturnRequest request) {
        if (request == null) {
            throw RestException.badRequest("Material return request is required");
        }
        if (request.quantity() == null || request.quantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw RestException.badRequest("Return quantity must be greater than 0");
        }
    }

    private void validateReturnQuantity(RepairMaterialUsage usage, BigDecimal quantity) {
        if (usage == null) {
            return;
        }
        BigDecimal issued = BigDecimal.valueOf(usage.getQuantity());
        BigDecimal alreadyReturned = materialReturnRepository
                .findAllByMaterialUsageIdAndStatusAndIsDeletedFalse(usage.getId(), RepairMaterialReturnStatus.POSTED)
                .stream()
                .map(RepairMaterialReturn::getQuantity)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal remaining = issued.subtract(alreadyReturned);
        if (quantity.compareTo(remaining) > 0) {
            throw RestException.badRequest("Return quantity exceeds issued quantity minus previous returns");
        }
    }

    private ReturnIdentity resolveReturnIdentity(WorkOrderMaterialReturnRequest request, RepairMaterialUsage usage) {
        UUID warehouseId = firstNonNull(request.warehouseId(), usage == null ? null : usage.getWarehouseId());
        UUID sparePartId = firstNonNull(request.sparePartId(), usage == null ? null : usage.getSparePartId());
        if (warehouseId == null) {
            throw RestException.badRequest("warehouseId is required");
        }
        if (sparePartId == null) {
            throw RestException.badRequest("sparePartId is required");
        }
        if (usage != null && request.sparePartId() != null && !Objects.equals(request.sparePartId(), usage.getSparePartId())) {
            throw RestException.badRequest("Material return sparePartId does not match material usage");
        }
        return new ReturnIdentity(
                warehouseId,
                sparePartId,
                firstNonNull(request.binId(), usage == null ? null : usage.getBinId()),
                firstNonBlank(request.lotNumber(), usage == null ? null : usage.getLotNumber()),
                firstNonBlank(request.serialNumber(), usage == null ? null : usage.getSerialNumber()),
                firstNonNull(request.expiryDate(), usage == null ? null : usage.getExpiryDate()),
                request.effectiveStatus()
        );
    }

    private StockMovement returnMovement(WorkOrder workOrder,
                                         RepairMaterialUsage usage,
                                         ReturnIdentity identity,
                                         WorkOrderMaterialReturnRequest request) {
        StockMovement movement = new StockMovement();
        movement.setWarehouseId(identity.warehouseId());
        movement.setSparePartId(identity.sparePartId());
        movement.setWorkOrderId(workOrder.getId());
        movement.setType(StockMovementType.RETURN);
        movement.setQuantity(request.quantity().doubleValue());
        movement.setDocumentNumber(trimToNull(request.documentNumber()));
        movement.setSourceDocumentNo(trimToNull(request.documentNumber()));
        movement.setSourceType(StockMovementSourceType.REPAIR_MATERIAL_RETURN);
        movement.setSourceId(workOrder.getId());
        movement.setSourceLineId(usage == null ? null : usage.getId());
        movement.setResponsiblePersonId(request.responsiblePersonId());
        movement.setTakenById(request.returnedById());
        movement.setMovementDate(LocalDate.now());
        movement.setOccurredAt(Instant.now());
        movement.setBinId(identity.binId());
        movement.setDestinationBinId(identity.binId());
        movement.setLotNumber(identity.lotNumber());
        movement.setSerialNumber(identity.serialNumber());
        movement.setExpiryDate(identity.expiryDate());
        movement.setStockStatus(identity.stockStatus());
        movement.setNotes("Work order material return: " + workOrder.getNumber());
        movement.setComment(trimToNull(request.reason()));
        return movement;
    }

    private StockReceiptCommand returnReceiptCommand(RepairMaterialUsage usage,
                                                    ReturnIdentity identity,
                                                    WorkOrderMaterialReturnRequest request,
                                                    StockMovement movement) {
        return new StockReceiptCommand(
                identity.warehouseId(),
                identity.sparePartId(),
                identity.binId(),
                request.quantity(),
                unitCost(usage),
                identity.lotNumber(),
                identity.serialNumber(),
                identity.expiryDate(),
                identity.stockStatus(),
                "REPAIR_MATERIAL_RETURN",
                movement.getId(),
                trimToNull(request.documentNumber()),
                "Work order material return",
                "repair-material-return:" + movement.getId()
        );
    }

    private InventoryTransaction returnInventoryTransaction(WorkOrder workOrder,
                                                            RepairMaterialUsage usage,
                                                            ReturnIdentity identity,
                                                            WorkOrderMaterialReturnRequest request,
                                                            StockMovement movement) {
        InventoryTransaction transaction = new InventoryTransaction();
        transaction.setType(InventoryTransactionType.RETURN);
        transaction.setWarehouseId(identity.warehouseId());
        transaction.setSparePartId(identity.sparePartId());
        transaction.setQuantity(request.quantity());
        transaction.setUnitPrice(unitCost(usage));
        if (transaction.getUnitPrice() != null) {
            transaction.setTotalAmount(transaction.getUnitPrice().multiply(request.quantity()));
        }
        transaction.setTakenById(request.returnedById());
        transaction.setResponsiblePersonId(request.responsiblePersonId());
        transaction.setWorkOrderId(workOrder.getId());
        transaction.setTransactionDate(LocalDate.now());
        transaction.setDocumentNumber(trimToNull(request.documentNumber()));
        transaction.setComment(trimToNull(request.reason()));
        transaction.setBinId(identity.binId());
        transaction.setDestinationBinId(identity.binId());
        transaction.setLotNumber(identity.lotNumber());
        transaction.setSerialNumber(identity.serialNumber());
        transaction.setExpiryDate(identity.expiryDate());
        transaction.setStockStatus(identity.stockStatus());
        transaction.setSourceType(StockMovementSourceType.REPAIR_MATERIAL_RETURN.name());
        transaction.setSourceId(movement.getId());
        return transaction;
    }

    private RepairMaterialReturn materialReturn(WorkOrder workOrder,
                                                RepairMaterialUsage usage,
                                                ReturnIdentity identity,
                                                WorkOrderMaterialReturnRequest request,
                                                StockMovement movement,
                                                InventoryTransaction transaction) {
        RepairMaterialReturn materialReturn = new RepairMaterialReturn();
        materialReturn.setWorkOrderId(workOrder.getId());
        materialReturn.setMaterialUsageId(usage == null ? null : usage.getId());
        materialReturn.setWarehouseId(identity.warehouseId());
        materialReturn.setSparePartId(identity.sparePartId());
        materialReturn.setBinId(identity.binId());
        materialReturn.setLotNumber(identity.lotNumber());
        materialReturn.setSerialNumber(identity.serialNumber());
        materialReturn.setExpiryDate(identity.expiryDate());
        materialReturn.setStockStatus(identity.stockStatus());
        materialReturn.setQuantity(request.quantity());
        materialReturn.setReason(trimToNull(request.reason()));
        materialReturn.setReturnedById(request.returnedById());
        materialReturn.setResponsiblePersonId(request.responsiblePersonId());
        materialReturn.setStockMovementId(movement.getId());
        materialReturn.setInventoryTransactionId(transaction.getId());
        materialReturn.setStatus(RepairMaterialReturnStatus.POSTED);
        return materialReturn;
    }

    private ReservationDto reserveLine(WorkOrder workOrder,
                                       WorkOrderWmsReservationLineRequest line,
                                       WorkOrderWmsReservationRequest request,
                                       List<Reservation> activeReservations) {
        WorkOrderSparePartRequirement requirement = requirementForWorkOrder(line.requirementId(), workOrder.getId());
        if (!Objects.equals(requirement.getSparePartId(), line.sparePartId())) {
            throw RestException.badRequest("Reservation sparePartId does not match requirement");
        }
        BigDecimal alreadyReserved = activeReservedQuantity(requirement.getId(), activeReservations);
        BigDecimal remainingToReserve = BigDecimal.valueOf(requirement.getRequiredQty()).subtract(alreadyReserved);
        if (line.quantity().compareTo(remainingToReserve) > 0) {
            throw RestException.badRequest("Reservation quantity exceeds remaining requirement quantity");
        }
        ReservationDto reservation = reservationService.reserve(new ReservationRequest(
                null,
                line.warehouseId(),
                line.sparePartId(),
                line.binId(),
                requirement.getId(),
                line.lotNumber(),
                line.serialNumber(),
                line.expiryDate(),
                line.effectiveStatus(),
                workOrder.getId(),
                null,
                request.reservedById(),
                line.quantity().doubleValue()
        ));
        BigDecimal totalReserved = alreadyReserved.add(line.quantity());
        requirement.setStatus(totalReserved.compareTo(BigDecimal.valueOf(requirement.getRequiredQty())) >= 0
                ? WorkOrderSparePartRequirementStatus.RESERVED
                : WorkOrderSparePartRequirementStatus.PARTIALLY_RESERVED);
        requirementRepository.save(requirement);
        return reservation;
    }

    private RepairMaterialUsageDto confirmLine(WorkOrder workOrder,
                                               WarehouseTask task,
                                               WorkOrderPickConfirmLineRequest line,
                                               WorkOrderPickConfirmRequest request) {
        Reservation reservation = reservationRepository.findByIdAndIsDeletedFalse(line.reservationId())
                .orElseThrow(() -> RestException.notFound("Reservation not found: " + line.reservationId()));
        if (reservation.getStatus() == ReservationStatus.FULFILLED) {
            return existingUsage(line.requirementId())
                    .map(RepairMaterialUsageDto::from)
                    .orElseThrow(() -> RestException.badRequest("Reservation is fulfilled but material usage was not found"));
        }
        WorkOrderSparePartRequirement requirement = requirementForWorkOrder(line.requirementId(), workOrder.getId());
        WarehouseStockLedger ledger = toirStockService.fulfillReservation(
                line.warehouseId(),
                line.sparePartId(),
                line.binId(),
                line.lotNumber(),
                line.serialNumber(),
                line.expiryDate(),
                line.effectiveStatus(),
                line.quantity(),
                "WORK_ORDER_PICK",
                reservation.getId(),
                request.documentNumber(),
                "work-order-pick:" + reservation.getId()
        );
        reservation.setStatus(ReservationStatus.FULFILLED);
        reservationRepository.save(reservation);

        StockMovement movement = stockMovementRepository.save(issueMovement(workOrder, line, request));
        RepairMaterialUsage usage = materialUsageRepository.save(materialUsage(workOrder, line, request, movement, ledger));
        updateRequirementIssuedStatus(requirement);
        syncActualCost(usage);
        WarehouseStock stock = legacyStockProjectionService.sync(line.warehouseId(), line.sparePartId());
        lowStockRecommendationService.evaluateStockSafely(stock);
        return RepairMaterialUsageDto.from(usage);
    }

    private StockMovement issueMovement(WorkOrder workOrder,
                                        WorkOrderPickConfirmLineRequest line,
                                        WorkOrderPickConfirmRequest request) {
        StockMovement movement = new StockMovement();
        movement.setWarehouseId(line.warehouseId());
        movement.setSparePartId(line.sparePartId());
        movement.setWorkOrderId(workOrder.getId());
        movement.setType(StockMovementType.ISSUE);
        movement.setQuantity(line.quantity().doubleValue());
        movement.setCreatedById(request.issuedById());
        movement.setTakenById(request.takenById());
        movement.setDocumentNumber(trimToNull(request.documentNumber()));
        movement.setSourceType(StockMovementSourceType.WORK_ORDER_MATERIAL_USAGE);
        movement.setSourceId(workOrder.getId());
        movement.setSourceLineId(line.requirementId());
        movement.setBinId(line.binId());
        movement.setSourceBinId(line.binId());
        movement.setLotNumber(trimToNull(line.lotNumber()));
        movement.setSerialNumber(trimToNull(line.serialNumber()));
        movement.setExpiryDate(line.expiryDate());
        movement.setStockStatus(line.effectiveStatus());
        movement.setNotes("Work order pick: " + workOrder.getNumber());
        return movement;
    }

    private RepairMaterialUsage materialUsage(WorkOrder workOrder,
                                              WorkOrderPickConfirmLineRequest line,
                                              WorkOrderPickConfirmRequest request,
                                              StockMovement movement,
                                              WarehouseStockLedger ledger) {
        RepairMaterialUsage usage = new RepairMaterialUsage();
        usage.setWorkOrderId(workOrder.getId());
        usage.setWarehouseId(line.warehouseId());
        usage.setSparePartId(line.sparePartId());
        usage.setRequirementId(line.requirementId());
        usage.setStockMovementId(movement.getId());
        usage.setIssuedAt(Instant.now());
        usage.setIssuedById(request.issuedById());
        usage.setQuantity(line.quantity().doubleValue());
        usage.setUnitCost(ledger == null || ledger.getUnitCost() == null ? null : ledger.getUnitCost().doubleValue());
        usage.setNotes("Work order pick: " + workOrder.getNumber());
        usage.setBinId(line.binId());
        usage.setLotNumber(trimToNull(line.lotNumber()));
        usage.setSerialNumber(trimToNull(line.serialNumber()));
        usage.setExpiryDate(line.expiryDate());
        usage.setStockStatus(line.effectiveStatus());
        return usage;
    }

    private void updateRequirementIssuedStatus(WorkOrderSparePartRequirement requirement) {
        List<RepairMaterialUsage> usages = materialUsageRepository
                .findAllByRequirementIdInAndIsDeletedFalse(List.of(requirement.getId()));
        double issued = usages.stream().mapToDouble(RepairMaterialUsage::getQuantity).sum();
        requirement.setStatus(issued >= requirement.getRequiredQty()
                ? WorkOrderSparePartRequirementStatus.ISSUED
                : WorkOrderSparePartRequirementStatus.PARTIALLY_ISSUED);
        requirementRepository.save(requirement);
    }

    private void syncActualCost(RepairMaterialUsage usage) {
        if (usage.getUnitCost() == null || usage.getUnitCost() <= 0 || usage.getQuantity() <= 0 || usage.getId() == null) {
            return;
        }
        Optional<CostCategory> category = costCategoryRepository.findFirstByCodeAndIsDeletedFalse("MATERIALS");
        if (category.isEmpty()) {
            return;
        }
        ActualCost cost = actualCostRepository
                .findTopBySourceTypeAndSourceIdAndIsDeletedFalseOrderByUpdatedAtDesc(
                        ActualCostSourceType.MATERIAL_ISSUE,
                        usage.getId()
                )
                .orElseGet(ActualCost::new);
        cost.setSourceType(ActualCostSourceType.MATERIAL_ISSUE);
        cost.setSourceId(usage.getId());
        cost.setWorkOrderId(usage.getWorkOrderId());
        cost.setBudgetLineId(budgetLineResolver.resolveForWorkOrderId(usage.getWorkOrderId()));
        cost.setCostCategoryId(category.get().getId());
        cost.setAmount(usage.getQuantity() * usage.getUnitCost());
        cost.setStatus(ActualCostStatus.PENDING);
        cost.setCostDate(usage.getIssuedAt() == null ? Instant.now() : usage.getIssuedAt());
        cost.setNotes("Generated from WMS material issue %s".formatted(usage.getId()));
        actualCostRepository.save(cost);
    }

    private WarehouseTaskLineRequest pickLine(Reservation reservation) {
        return new WarehouseTaskLineRequest(
                reservation.getSparePartId(),
                null,
                reservation.getBinId(),
                null,
                reservation.getLotNumber(),
                reservation.getSerialNumber(),
                reservation.getExpiryDate(),
                effectiveStatus(reservation.getStockStatus()),
                BigDecimal.valueOf(reservation.getQuantity()),
                null
        );
    }

    private List<Reservation> activeReservations(UUID workOrderId) {
        return reservationRepository.findAllByWorkOrderIdAndStatusAndIsDeletedFalseOrderByUpdatedAtDesc(
                workOrderId,
                ReservationStatus.ACTIVE
        );
    }

    private BigDecimal activeReservedQuantity(UUID requirementId, List<Reservation> activeReservations) {
        return activeReservations.stream()
                .filter(reservation -> Objects.equals(reservation.getRequirementId(), requirementId))
                .map(reservation -> BigDecimal.valueOf(reservation.getQuantity()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<RepairMaterialUsageDto> existingUsages(List<WorkOrderPickConfirmLineRequest> lines) {
        return materialUsageRepository.findAllByRequirementIdInAndIsDeletedFalse(requirementIds(lines)).stream()
                .map(RepairMaterialUsageDto::from)
                .toList();
    }

    private Optional<RepairMaterialUsage> existingUsage(UUID requirementId) {
        return materialUsageRepository.findAllByRequirementIdInAndIsDeletedFalse(List.of(requirementId)).stream()
                .findFirst();
    }

    private Collection<UUID> requirementIds(List<WorkOrderPickConfirmLineRequest> lines) {
        return lines.stream().map(WorkOrderPickConfirmLineRequest::requirementId).distinct().toList();
    }

    private WorkOrderSparePartRequirement requirementForWorkOrder(UUID requirementId, UUID workOrderId) {
        return requirementRepository.findById(requirementId)
                .filter(requirement -> !requirement.isDeleted())
                .filter(requirement -> Objects.equals(requirement.getWorkOrderId(), workOrderId))
                .orElseThrow(() -> RestException.notFound("Requirement not found: " + requirementId));
    }

    private WorkOrder loadWorkOrderForIssue(UUID workOrderId) {
        WorkOrder workOrder = workOrderRepository.findByIdAndIsDeletedFalse(workOrderId)
                .orElseThrow(() -> RestException.notFound("Work order not found: " + workOrderId));
        if (!MATERIAL_ISSUE_ALLOWED_STATUSES.contains(workOrder.getStatus())) {
            throw RestException.badRequest("Materials can be issued only for approved or in-progress work orders");
        }
        return workOrder;
    }

    private void validateReservationRequest(WorkOrderWmsReservationRequest request) {
        if (request == null || request.lines() == null || request.lines().isEmpty()) {
            throw RestException.badRequest("At least one reservation line is required");
        }
    }

    private void validateConfirmRequest(WorkOrderPickConfirmRequest request) {
        if (request == null || request.lines() == null || request.lines().isEmpty()) {
            throw RestException.badRequest("At least one pick confirmation line is required");
        }
    }

    private WarehouseStockStatus effectiveStatus(WarehouseStockStatus status) {
        return status == null ? WarehouseStockStatus.AVAILABLE : status;
    }

    private BigDecimal unitCost(RepairMaterialUsage usage) {
        if (usage == null || usage.getUnitCost() == null) {
            return null;
        }
        return BigDecimal.valueOf(usage.getUnitCost());
    }

    private <T> T firstNonNull(T first, T second) {
        return first != null ? first : second;
    }

    private String firstNonBlank(String first, String second) {
        String value = trimToNull(first);
        return value != null ? value : trimToNull(second);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record ReturnIdentity(
            UUID warehouseId,
            UUID sparePartId,
            UUID binId,
            String lotNumber,
            String serialNumber,
            LocalDate expiryDate,
            WarehouseStockStatus stockStatus
    ) {
    }
}
