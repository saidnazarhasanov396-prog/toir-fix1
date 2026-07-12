package com.toir.service;

import com.toir.dto.reservation.ReservationDto;
import com.toir.dto.reservation.ReservationRequest;
import com.toir.entity.Reservation;
import com.toir.entity.StockMovement;
import com.toir.entity.warehouse.WarehouseStock;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.enums.ReservationStatus;
import com.toir.enums.StockMovementType;
import com.toir.enums.WarehouseStockStatus;
import com.toir.exception.RestException;
import com.toir.repository.ReservationRepository;
import com.toir.repository.StockMovementRepository;
import com.toir.repository.WarehouseStockRepository;
import com.toir.repository.WarehouseRepository;
import com.toir.repository.SparePartRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.maintenance.WorkOrderSparePartRequirementRepository;
import com.toir.repository.repair.RepairCampaignMaterialRequirementRepository;
import com.toir.security.ScopeAccessService;
import com.toir.service.warehouse.ToirStockService;
import com.toir.service.warehouse.LegacyStockProjectionService;
import com.toir.util.AuditBuilderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class ReservationService {

    private final ReservationRepository repository;
    private final WarehouseStockRepository stockRepository;
    private final StockMovementRepository stockMovementRepository;
    private final AuditBuilderService auditBuilderService;
    private final LowStockRecommendationService lowStockRecommendationService;
    private final ToirStockService toirStockService;
    private final LegacyStockProjectionService legacyStockProjectionService;
    private final WorkOrderSparePartRequirementRepository requirementRepository;
    private final RepairCampaignMaterialRequirementRepository campaignRequirementRepository;
    private final WorkOrderRepository workOrderRepository;
    private final WarehouseRepository warehouseRepository;
    private final SparePartRepository sparePartRepository;
    private final ScopeAccessService scopeAccessService;


    @Transactional(readOnly = true)
    public List<ReservationDto> findByWorkOrder(UUID workOrderId) {
        authorizeWorkOrder(workOrderId);
        return repository.findAllByWorkOrderIdAndIsDeletedFalseOrderByUpdatedAtDesc(workOrderId).stream().map(ReservationDto::from).toList();
    }

    public ReservationDto reserve(ReservationRequest r) {
        validatePositiveQuantity(r.quantity());

        Reservation reservation = new Reservation();
        reservation.setWarehouseStockId(r.warehouseStockId());
        if (r.warehouseStockId() != null) {
            WarehouseStock stock = stockRepository.findByIdAndIsDeletedFalse(r.warehouseStockId())
                    .orElseThrow(() -> RestException.notFound("Stock not found: " + r.warehouseStockId()));
            reservation.setWarehouseId(stock.getWarehouseId());
            reservation.setSparePartId(stock.getSparePartId());
        } else {
            if (r.warehouseId() == null || r.sparePartId() == null) {
                throw RestException.badRequest("warehouseId and sparePartId are required for direct WMS reservation");
            }
            reservation.setWarehouseId(r.warehouseId());
            reservation.setSparePartId(r.sparePartId());
        }
        reservation.setBinId(r.binId());
        reservation.setRequirementId(r.requirementId());
        reservation.setLotNumber(trimToNull(r.lotNumber()));
        reservation.setSerialNumber(trimToNull(r.serialNumber()));
        reservation.setExpiryDate(r.expiryDate());
        reservation.setStockStatus(r.effectiveStatus());
        reservation.setWorkOrderId(r.workOrderId());
        reservation.setRepairRequestId(r.repairRequestId());
        reservation.setReservedById(r.reservedById());
        reservation.setQuantity(r.quantity());
        authorize(reservation);
        validateCanonicalRequirement(reservation);
        Reservation saved;
        try {
            saved = repository.saveAndFlush(reservation);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            if (messages(e).contains("uq_reservations_active_work_requirement_spare")) {
                throw RestException.conflict("RESERVATION_DUPLICATE");
            }
            throw e;
        }
        postCoreReserve(saved);
        WarehouseStock stock = legacyStockProjectionService.sync(saved.getWarehouseId(), saved.getSparePartId());
        stockMovementRepository.save(buildMovement(saved, StockMovementType.RESERVATION));
        lowStockRecommendationService.evaluateStockSafely(stock);

        auditBuilderService.log(
                "reservation",
                saved.getId().toString(),
                AuditAction.CREATE,
                AuditModule.RESERVATION,
                "Резерв создан",
                null,
                saved
        );

        return ReservationDto.from(saved);
    }

    public ReservationDto cancel(UUID id) {
        Reservation reservation = getLockedOrThrow(id);
        hydrateCoordinates(reservation);
        authorize(reservation);
        if (reservation.getStatus() == ReservationStatus.CANCELLED) return ReservationDto.from(reservation);
        if (reservation.getStatus() != ReservationStatus.ACTIVE) throw RestException.conflict("RESERVATION_ALREADY_FULFILLED");
        validatePositiveQuantity(reservation.getQuantity());
        ReservationDto before=ReservationDto.from(reservation);
        reservation.setStatus(ReservationStatus.CANCELLED);
        Reservation saved = repository.saveAndFlush(reservation);
        postCoreRelease(saved);
        WarehouseStock stock = legacyStockProjectionService.sync(saved.getWarehouseId(), saved.getSparePartId());
        stockMovementRepository.save(buildMovement(saved, StockMovementType.RELEASE));
        lowStockRecommendationService.evaluateStockSafely(stock);

        auditBuilderService.log(
                "reservation",
                saved.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.RESERVATION,
                "Резерв обновлен",
                before,
                ReservationDto.from(saved)
        );

        return ReservationDto.from(saved);
    }

    @Transactional
    public ReservationDto fulfill(UUID id) {
        Reservation reservation = getLockedOrThrow(id);
        hydrateCoordinates(reservation);
        authorize(reservation);
        if (reservation.getStatus() == ReservationStatus.FULFILLED) return ReservationDto.from(reservation);
        if (reservation.getStatus() != ReservationStatus.ACTIVE) throw RestException.conflict("RESERVATION_ALREADY_CANCELLED");
        validatePositiveQuantity(reservation.getQuantity());
        ReservationDto before=ReservationDto.from(reservation);
        reservation.setStatus(ReservationStatus.FULFILLED);
        Reservation saved = repository.saveAndFlush(reservation);
        postCoreFulfill(saved);
        WarehouseStock stock = legacyStockProjectionService.sync(saved.getWarehouseId(), saved.getSparePartId());
        stockMovementRepository.save(buildMovement(saved, StockMovementType.ISSUE));
        lowStockRecommendationService.evaluateStockSafely(stock);

        auditBuilderService.log(
                "reservation",
                saved.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.RESERVATION,
                "Резерв обновлен",
                before,
                ReservationDto.from(saved)
        );

        return ReservationDto.from(saved);
    }

    private Reservation getLockedOrThrow(UUID id) {
        return repository.findByIdAndIsDeletedFalseForUpdate(id)
                .orElseThrow(this::denied);
    }

    private void hydrateCoordinates(Reservation reservation) {
        if (reservation.getWarehouseId() != null && reservation.getSparePartId() != null) {
            if (reservation.getStockStatus() == null) {
                reservation.setStockStatus(WarehouseStockStatus.AVAILABLE);
            }
            return;
        }
        if (reservation.getWarehouseStockId() == null) {
            throw RestException.badRequest("Reservation warehouseId and sparePartId are required");
        }
        WarehouseStock stock = stockRepository.findByIdAndIsDeletedFalse(reservation.getWarehouseStockId())
                .orElseThrow(this::denied);
        reservation.setWarehouseId(stock.getWarehouseId());
        reservation.setSparePartId(stock.getSparePartId());
    }

    private void validatePositiveQuantity(BigDecimal quantity) {
        if (quantity == null || quantity.signum() <= 0) {
            throw RestException.badRequest("Quantity must be greater than 0");
        }
        if (quantity.stripTrailingZeros().scale() > 4 || quantity.precision() - quantity.scale() > 15) {
            throw RestException.badRequest("Quantity must fit numeric(19,4)");
        }
    }

    private void validateCanonicalRequirement(Reservation reservation) {
        if(reservation.getWorkOrderId()!=null&&reservation.getRequirementId()==null)
            throw RestException.badRequest("RESERVATION_REQUIREMENT_REQUIRED");
        if (reservation.getRequirementId() == null) return;
        if (reservation.getWorkOrderId() == null || reservation.getSparePartId() == null) {
            throw RestException.badRequest("RESERVATION_CANONICAL_IDENTITY_REQUIRED");
        }
        var requirement = requirementRepository.findByIdAndWorkOrderIdAndIsDeletedFalseForUpdate(
                        reservation.getRequirementId(), reservation.getWorkOrderId())
                .orElseThrow(() -> RestException.badRequest("RESERVATION_REQUIREMENT_INVALID"));
        UUID requiredSpare = requirement.getSparePartId() != null ? requirement.getSparePartId()
                : requirement.getSparePart() == null ? null : requirement.getSparePart().getId();
        if (!Objects.equals(requiredSpare, reservation.getSparePartId())) {
            throw RestException.badRequest("RESERVATION_SPARE_PART_MISMATCH");
        }
        if (requirement.getCampaignRequirementId() != null) {
            var campaignRequirement = campaignRequirementRepository
                    .findByIdAndIsDeletedFalse(requirement.getCampaignRequirementId())
                    .orElseThrow(() -> RestException.badRequest("RESERVATION_REQUIREMENT_INVALID"));
            if (!Objects.equals(campaignRequirement.getSparePartId(), reservation.getSparePartId())) {
                throw RestException.badRequest("RESERVATION_SPARE_PART_MISMATCH");
            }
            if (!Objects.equals(campaignRequirement.getWarehouseId(), reservation.getWarehouseId())
                    || (requirement.getWarehouseId() != null
                    && !Objects.equals(requirement.getWarehouseId(), reservation.getWarehouseId()))) {
                throw RestException.badRequest("RESERVATION_WAREHOUSE_MISMATCH");
            }
        } else if (requirement.getWarehouseId() != null
                && !Objects.equals(requirement.getWarehouseId(), reservation.getWarehouseId())) {
            throw RestException.badRequest("RESERVATION_WAREHOUSE_MISMATCH");
        }
        if (reservation.getQuantity().compareTo(requirement.getRequiredQty()) > 0) {
            throw RestException.badRequest("RESERVATION_EXCEEDS_REQUIREMENT");
        }
        if (!repository.findAllByWorkOrderIdAndRequirementIdAndSparePartIdAndStatusAndIsDeletedFalse(
                reservation.getWorkOrderId(), reservation.getRequirementId(), reservation.getSparePartId(),
                ReservationStatus.ACTIVE).isEmpty()) {
            throw RestException.conflict("RESERVATION_DUPLICATE");
        }
    }

    private void authorize(Reservation reservation){
        if(reservation.getWorkOrderId()!=null)authorizeWorkOrder(reservation.getWorkOrderId());
        var warehouse=warehouseRepository.findByIdAndIsDeletedFalse(reservation.getWarehouseId())
                .filter(com.toir.entity.warehouse.Warehouse::isActive).orElseThrow(this::denied);
        if(warehouse.getDepartmentId()==null)throw denied();
        scopeAccessService.assertCanAccessDepartment(warehouse.getDepartmentId());
        sparePartRepository.findByIdAndIsDeletedFalse(reservation.getSparePartId()).orElseThrow(this::denied);
    }

    private void authorizeWorkOrder(UUID workOrderId){
        var workOrder=workOrderRepository.findByIdAndIsDeletedFalse(workOrderId).orElseThrow(this::denied);
        if(workOrder.getDepartmentId()==null)throw denied();
        scopeAccessService.assertCanAccessDepartment(workOrder.getDepartmentId());
    }

    private AccessDeniedException denied(){return new AccessDeniedException("Access denied");}

    private String messages(Throwable error) {
        StringBuilder result = new StringBuilder();
        for (Throwable current = error; current != null; current = current.getCause()) {
            result.append(' ').append(current.getMessage());
        }
        return result.toString();
    }

    private StockMovement buildMovement(Reservation reservation, StockMovementType type) {
        StockMovement movement = new StockMovement();
        movement.setWarehouseId(reservation.getWarehouseId());
        movement.setSparePartId(reservation.getSparePartId());
        movement.setBinId(reservation.getBinId());
        movement.setWorkOrderId(reservation.getWorkOrderId());
        movement.setCreatedById(reservation.getReservedById());
        movement.setType(type);
        movement.setQuantity(reservation.getQuantity());
        movement.setLotNumber(reservation.getLotNumber());
        movement.setSerialNumber(reservation.getSerialNumber());
        movement.setExpiryDate(reservation.getExpiryDate());
        movement.setStockStatus(effectiveStatus(reservation.getStockStatus()));
        return movement;
    }

    private void postCoreReserve(Reservation reservation) {
        if (usesDetailedIdentity(reservation)) {
            toirStockService.reserve(
                    reservation.getWarehouseId(),
                    reservation.getSparePartId(),
                    reservation.getBinId(),
                    reservation.getLotNumber(),
                    reservation.getSerialNumber(),
                    reservation.getExpiryDate(),
                    effectiveStatus(reservation.getStockStatus()),
                    quantity(reservation.getQuantity()),
                    "RESERVATION",
                    reservation.getId(),
                    null,
                    "reservation-reserve:" + reservation.getId()
            );
            return;
        }
        toirStockService.reserve(
                reservation.getWarehouseId(),
                reservation.getSparePartId(),
                reservation.getBinId(),
                quantity(reservation.getQuantity()),
                "RESERVATION",
                reservation.getId(),
                null,
                "reservation-reserve:" + reservation.getId()
        );
    }

    private void postCoreRelease(Reservation reservation) {
        if (usesDetailedIdentity(reservation)) {
            toirStockService.releaseReservation(
                    reservation.getWarehouseId(),
                    reservation.getSparePartId(),
                    reservation.getBinId(),
                    reservation.getLotNumber(),
                    reservation.getSerialNumber(),
                    reservation.getExpiryDate(),
                    effectiveStatus(reservation.getStockStatus()),
                    quantity(reservation.getQuantity()),
                    "RESERVATION",
                    reservation.getId(),
                    null,
                    "reservation-cancel:" + reservation.getId()
            );
            return;
        }
        toirStockService.releaseReservation(
                reservation.getWarehouseId(),
                reservation.getSparePartId(),
                reservation.getBinId(),
                quantity(reservation.getQuantity()),
                "RESERVATION",
                reservation.getId(),
                null,
                "reservation-cancel:" + reservation.getId()
        );
    }

    private void postCoreFulfill(Reservation reservation) {
        if (usesDetailedIdentity(reservation)) {
            toirStockService.fulfillReservation(
                    reservation.getWarehouseId(),
                    reservation.getSparePartId(),
                    reservation.getBinId(),
                    reservation.getLotNumber(),
                    reservation.getSerialNumber(),
                    reservation.getExpiryDate(),
                    effectiveStatus(reservation.getStockStatus()),
                    quantity(reservation.getQuantity()),
                    "RESERVATION",
                    reservation.getId(),
                    null,
                    "reservation-fulfill:" + reservation.getId()
            );
            return;
        }
        toirStockService.fulfillReservation(
                reservation.getWarehouseId(),
                reservation.getSparePartId(),
                reservation.getBinId(),
                quantity(reservation.getQuantity()),
                "RESERVATION",
                reservation.getId(),
                null,
                "reservation-fulfill:" + reservation.getId()
        );
    }

    private boolean usesDetailedIdentity(Reservation reservation) {
        return reservation.getBinId() != null
                || trimToNull(reservation.getLotNumber()) != null
                || trimToNull(reservation.getSerialNumber()) != null
                || reservation.getExpiryDate() != null
                || (reservation.getStockStatus() != null && reservation.getStockStatus() != WarehouseStockStatus.AVAILABLE);
    }

    private WarehouseStockStatus effectiveStatus(WarehouseStockStatus status) {
        return status == null ? WarehouseStockStatus.AVAILABLE : status;
    }

    private BigDecimal quantity(BigDecimal value) {
        return value.stripTrailingZeros();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
