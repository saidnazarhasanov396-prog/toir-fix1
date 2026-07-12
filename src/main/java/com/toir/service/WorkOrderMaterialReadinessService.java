package com.toir.service;

import com.toir.dto.workorder.WorkOrderMaterialReadinessDto;
import com.toir.dto.workorder.WorkOrderMaterialReadinessRowDto;
import com.toir.entity.Reservation;
import com.toir.entity.SparePart;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.maintenance.WorkOrderSparePartRequirement;
import com.toir.entity.repair.RepairMaterialUsage;
import com.toir.entity.warehouse.RepairMaterialReturn;
import com.toir.enums.MaterialReadinessStatus;
import com.toir.enums.RepairMaterialReturnStatus;
import com.toir.enums.ReservationStatus;
import com.toir.exception.RestException;
import com.toir.repository.RepairMaterialReturnRepository;
import com.toir.repository.ReservationRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.maintenance.WorkOrderSparePartRequirementRepository;
import com.toir.repository.repair.RepairMaterialUsageRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WorkOrderMaterialReadinessService {

    private final WorkOrderRepository workOrderRepository;
    private final WorkOrderSparePartRequirementRepository requirementRepository;
    private final ReservationRepository reservationRepository;
    private final RepairMaterialUsageRepository materialUsageRepository;
    private final RepairMaterialReturnRepository materialReturnRepository;

    @Transactional(readOnly = true)
    public WorkOrderMaterialReadinessDto getReadiness(UUID workOrderId) {
        WorkOrder workOrder = workOrderRepository.findByIdAndIsDeletedFalse(workOrderId)
                .orElseThrow(() -> RestException.notFound("Work order not found: " + workOrderId));
        List<WorkOrderSparePartRequirement> requirements =
                requirementRepository.findActiveByWorkOrderId(workOrderId);
        List<Reservation> reservations =
                reservationRepository.findAllByWorkOrderIdAndStatusAndIsDeletedFalseOrderByUpdatedAtDesc(
                        workOrderId,
                        ReservationStatus.ACTIVE
                );
        List<RepairMaterialUsage> usages =
                materialUsageRepository.findAllByWorkOrderIdAndIsDeletedFalseOrderByUpdatedAtDesc(workOrderId);
        List<RepairMaterialReturn> returns =
                materialReturnRepository.findAllByWorkOrderIdAndIsDeletedFalseOrderByUpdatedAtDesc(workOrderId);

        Map<UUID, RepairMaterialUsage> usageById = usages.stream()
                .filter(usage -> usage.getId() != null)
                .collect(Collectors.toMap(RepairMaterialUsage::getId, Function.identity(), (left, right) -> left));

        List<WorkOrderMaterialReadinessRowDto> rows = requirements.stream()
                .map(requirement -> row(requirement, reservations, usages, returns, usageById))
                .toList();
        MaterialReadinessStatus overallStatus = overallStatus(rows);
        boolean blocking = rows.stream().anyMatch(WorkOrderMaterialReadinessRowDto::blocking);

        return new WorkOrderMaterialReadinessDto(
                workOrderId,
                workOrder.getEquipmentId(),
                overallStatus,
                blocking,
                Instant.now(),
                rows
        );
    }

    private WorkOrderMaterialReadinessRowDto row(
            WorkOrderSparePartRequirement requirement,
            List<Reservation> reservations,
            List<RepairMaterialUsage> usages,
            List<RepairMaterialReturn> returns,
            Map<UUID, RepairMaterialUsage> usageById
    ) {
        UUID requirementId = requirement.getId();
        UUID sparePartId = sparePartId(requirement);
        double requiredQty = positive(requirement.getRequiredQty() == null ? 0 : requirement.getRequiredQty().doubleValue());
        double reservedQty = reservations.stream()
                .filter(reservation -> matchesRequirementOrFallback(
                        reservation.getRequirementId(),
                        reservation.getWorkOrderId(),
                        reservation.getSparePartId(),
                        requirement
                ))
                .mapToDouble(reservation -> positive(reservation.getQuantity() == null ? 0 : reservation.getQuantity().doubleValue()))
                .sum();
        double issuedQty = usages.stream()
                .filter(usage -> matchesRequirementOrFallback(
                        usage.getRequirementId(),
                        usage.getWorkOrderId(),
                        usage.getSparePartId(),
                        requirement
                ))
                .mapToDouble(usage -> positive(usage.getQuantity()))
                .sum();
        double returnedQty = returns.stream()
                .filter(returned -> returned.getStatus() == null
                        || returned.getStatus() == RepairMaterialReturnStatus.POSTED)
                .filter(returned -> matchesReturn(returned, requirement, usageById))
                .mapToDouble(returned -> positive(returned.getQuantity()))
                .sum();

        double netIssued = Math.max(issuedQty - returnedQty, 0);
        double coveredQty = reservedQty + netIssued;
        double shortageQty = Math.max(requiredQty - coveredQty, 0);
        MaterialReadinessStatus status = rowStatus(requiredQty, reservedQty, netIssued, shortageQty, coveredQty);
        boolean blocking = status == MaterialReadinessStatus.SHORTAGE || status == MaterialReadinessStatus.PARTIAL;
        SparePart sparePart = requirement.getSparePart();

        return new WorkOrderMaterialReadinessRowDto(
                requirementId,
                sparePartId,
                sparePart == null ? null : sparePart.getName(),
                requirement.getUnit(),
                requiredQty,
                reservedQty,
                issuedQty,
                returnedQty,
                shortageQty,
                status,
                blocking,
                null,
                null,
                null,
                requirement.getNotes()
        );
    }

    private boolean matchesRequirementOrFallback(
            UUID actualRequirementId,
            UUID actualWorkOrderId,
            UUID actualSparePartId,
            WorkOrderSparePartRequirement requirement
    ) {
        if (actualRequirementId != null) {
            return actualRequirementId.equals(requirement.getId());
        }
        return Objects.equals(actualWorkOrderId, requirement.getWorkOrderId())
                && Objects.equals(actualSparePartId, sparePartId(requirement));
    }

    private boolean matchesReturn(
            RepairMaterialReturn returned,
            WorkOrderSparePartRequirement requirement,
            Map<UUID, RepairMaterialUsage> usageById
    ) {
        RepairMaterialUsage usage = returned.getMaterialUsageId() == null
                ? null
                : usageById.get(returned.getMaterialUsageId());
        if (usage != null) {
            return matchesRequirementOrFallback(
                    usage.getRequirementId(),
                    usage.getWorkOrderId(),
                    usage.getSparePartId(),
                    requirement
            );
        }
        return Objects.equals(returned.getWorkOrderId(), requirement.getWorkOrderId())
                && Objects.equals(returned.getSparePartId(), sparePartId(requirement));
    }

    private MaterialReadinessStatus rowStatus(
            double requiredQty,
            double reservedQty,
            double netIssued,
            double shortageQty,
            double coveredQty
    ) {
        if (requiredQty <= 0) {
            return MaterialReadinessStatus.NOT_REQUIRED;
        }
        if (netIssued >= requiredQty) {
            return MaterialReadinessStatus.ISSUED;
        }
        if (reservedQty >= requiredQty || coveredQty >= requiredQty) {
            return MaterialReadinessStatus.READY;
        }
        if (shortageQty > 0 && coveredQty > 0) {
            return MaterialReadinessStatus.PARTIAL;
        }
        if (shortageQty > 0) {
            return MaterialReadinessStatus.SHORTAGE;
        }
        return MaterialReadinessStatus.UNKNOWN;
    }

    private MaterialReadinessStatus overallStatus(List<WorkOrderMaterialReadinessRowDto> rows) {
        if (rows.isEmpty()) {
            return MaterialReadinessStatus.NOT_REQUIRED;
        }
        if (rows.stream().anyMatch(row -> row.blocking()
                && row.readinessStatus() == MaterialReadinessStatus.SHORTAGE)) {
            return MaterialReadinessStatus.SHORTAGE;
        }
        if (rows.stream().anyMatch(row -> row.blocking()
                && row.readinessStatus() == MaterialReadinessStatus.PARTIAL)) {
            return MaterialReadinessStatus.PARTIAL;
        }
        if (rows.stream().allMatch(row -> row.readinessStatus() == MaterialReadinessStatus.ISSUED)) {
            return MaterialReadinessStatus.ISSUED;
        }
        boolean allReadyIssuedOrNotRequired = rows.stream().allMatch(row ->
                row.readinessStatus() == MaterialReadinessStatus.READY
                        || row.readinessStatus() == MaterialReadinessStatus.ISSUED
                        || row.readinessStatus() == MaterialReadinessStatus.NOT_REQUIRED);
        boolean anyReadyOrIssued = rows.stream().anyMatch(row ->
                row.readinessStatus() == MaterialReadinessStatus.READY
                        || row.readinessStatus() == MaterialReadinessStatus.ISSUED);
        if (allReadyIssuedOrNotRequired && anyReadyOrIssued) {
            return MaterialReadinessStatus.READY;
        }
        if (rows.stream().allMatch(row -> row.readinessStatus() == MaterialReadinessStatus.NOT_REQUIRED)) {
            return MaterialReadinessStatus.NOT_REQUIRED;
        }
        if (rows.stream().anyMatch(row -> row.readinessStatus() == MaterialReadinessStatus.WAITING_WMS)) {
            return MaterialReadinessStatus.WAITING_WMS;
        }
        return MaterialReadinessStatus.UNKNOWN;
    }

    private UUID sparePartId(WorkOrderSparePartRequirement requirement) {
        if (requirement.getSparePartId() != null) {
            return requirement.getSparePartId();
        }
        return requirement.getSparePart() == null ? null : requirement.getSparePart().getId();
    }

    private double positive(double value) {
        return Math.max(value, 0);
    }

    private double positive(BigDecimal value) {
        return value == null ? 0 : Math.max(value.doubleValue(), 0);
    }
}
