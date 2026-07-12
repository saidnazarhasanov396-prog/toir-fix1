package com.toir.service;

import com.toir.dto.workorder.WorkOrderMaterialReadinessDto;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkOrderMaterialReadinessServiceTest {

    @Mock
    WorkOrderRepository workOrderRepository;

    @Mock
    WorkOrderSparePartRequirementRepository requirementRepository;

    @Mock
    ReservationRepository reservationRepository;

    @Mock
    RepairMaterialUsageRepository materialUsageRepository;

    @Mock
    RepairMaterialReturnRepository materialReturnRepository;

    WorkOrderMaterialReadinessService service;

    UUID workOrderId;
    UUID equipmentId;
    WorkOrder workOrder;

    @BeforeEach
    void setUp() {
        service = new WorkOrderMaterialReadinessService(
                workOrderRepository,
                requirementRepository,
                reservationRepository,
                materialUsageRepository,
                materialReturnRepository
        );
        workOrderId = UUID.randomUUID();
        equipmentId = UUID.randomUUID();
        workOrder = new WorkOrder();
        workOrder.setId(workOrderId);
        workOrder.setEquipmentId(equipmentId);
    }

    @Test
    void noRequirementsAreNotRequiredAndNonBlocking() {
        givenMaterialState(List.of(), List.of(), List.of(), List.of());

        WorkOrderMaterialReadinessDto result = service.getReadiness(workOrderId);

        assertThat(result.workOrderId()).isEqualTo(workOrderId);
        assertThat(result.equipmentId()).isEqualTo(equipmentId);
        assertThat(result.overallStatus()).isEqualTo(MaterialReadinessStatus.NOT_REQUIRED);
        assertThat(result.blocking()).isFalse();
        assertThat(result.checkedAt()).isNotNull();
        assertThat(result.rows()).isEmpty();
    }

    @Test
    void fullyReservedRequirementIsReady() {
        WorkOrderSparePartRequirement requirement = requirement(10, "pcs", "Bearing", "Needs kit");
        Reservation reservation = reservation(requirement.getId(), requirement.getSparePartId(), 10);
        givenMaterialState(List.of(requirement), List.of(reservation), List.of(), List.of());

        WorkOrderMaterialReadinessDto result = service.getReadiness(workOrderId);

        assertThat(result.overallStatus()).isEqualTo(MaterialReadinessStatus.READY);
        assertThat(result.blocking()).isFalse();
        assertThat(result.rows()).singleElement().satisfies(row -> {
            assertThat(row.readinessStatus()).isEqualTo(MaterialReadinessStatus.READY);
            assertThat(row.requiredQty()).isEqualTo(10);
            assertThat(row.reservedQty()).isEqualTo(10);
            assertThat(row.shortageQty()).isZero();
            assertThat(row.blocking()).isFalse();
            assertThat(row.sparePartName()).isEqualTo("Bearing");
            assertThat(row.unit()).isEqualTo("pcs");
            assertThat(row.notes()).isEqualTo("Needs kit");
            assertThat(row.expectedDate()).isNull();
            assertThat(row.sourceSystem()).isNull();
            assertThat(row.lastSyncedAt()).isNull();
        });
    }

    @Test
    void fullyIssuedRequirementIsIssued() {
        WorkOrderSparePartRequirement requirement = requirement(5, "l", "Oil", null);
        RepairMaterialUsage usage = usage(requirement.getId(), requirement.getSparePartId(), 5);
        givenMaterialState(List.of(requirement), List.of(), List.of(usage), List.of());

        WorkOrderMaterialReadinessDto result = service.getReadiness(workOrderId);

        assertThat(result.overallStatus()).isEqualTo(MaterialReadinessStatus.ISSUED);
        assertThat(result.rows()).singleElement().satisfies(row -> {
            assertThat(row.readinessStatus()).isEqualTo(MaterialReadinessStatus.ISSUED);
            assertThat(row.issuedQty()).isEqualTo(5);
            assertThat(row.shortageQty()).isZero();
        });
    }

    @Test
    void partialCoverageIsBlockingPartial() {
        WorkOrderSparePartRequirement requirement = requirement(8, "pcs", "Seal", null);
        givenMaterialState(
                List.of(requirement),
                List.of(reservation(requirement.getId(), requirement.getSparePartId(), 2)),
                List.of(usage(requirement.getId(), requirement.getSparePartId(), 3)),
                List.of()
        );

        WorkOrderMaterialReadinessDto result = service.getReadiness(workOrderId);

        assertThat(result.overallStatus()).isEqualTo(MaterialReadinessStatus.PARTIAL);
        assertThat(result.blocking()).isTrue();
        assertThat(result.rows()).singleElement().satisfies(row -> {
            assertThat(row.readinessStatus()).isEqualTo(MaterialReadinessStatus.PARTIAL);
            assertThat(row.shortageQty()).isEqualTo(3);
            assertThat(row.blocking()).isTrue();
        });
    }

    @Test
    void noCoverageIsBlockingShortage() {
        WorkOrderSparePartRequirement requirement = requirement(4, "pcs", "Filter", null);
        givenMaterialState(List.of(requirement), List.of(), List.of(), List.of());

        WorkOrderMaterialReadinessDto result = service.getReadiness(workOrderId);

        assertThat(result.overallStatus()).isEqualTo(MaterialReadinessStatus.SHORTAGE);
        assertThat(result.rows()).singleElement().satisfies(row -> {
            assertThat(row.readinessStatus()).isEqualTo(MaterialReadinessStatus.SHORTAGE);
            assertThat(row.shortageQty()).isEqualTo(4);
            assertThat(row.blocking()).isTrue();
        });
    }

    @Test
    void returnedMaterialReducesNetIssuedAndDoesNotHideShortage() {
        WorkOrderSparePartRequirement requirement = requirement(5, "pcs", "Belt", null);
        RepairMaterialUsage usage = usage(requirement.getId(), requirement.getSparePartId(), 5);
        RepairMaterialReturn returned = returned(usage.getId(), requirement.getSparePartId(), 2);
        givenMaterialState(List.of(requirement), List.of(), List.of(usage), List.of(returned));

        WorkOrderMaterialReadinessDto result = service.getReadiness(workOrderId);

        assertThat(result.overallStatus()).isEqualTo(MaterialReadinessStatus.PARTIAL);
        assertThat(result.rows()).singleElement().satisfies(row -> {
            assertThat(row.issuedQty()).isEqualTo(5);
            assertThat(row.returnedQty()).isEqualTo(2);
            assertThat(row.shortageQty()).isEqualTo(2);
            assertThat(row.readinessStatus()).isEqualTo(MaterialReadinessStatus.PARTIAL);
        });
    }

    @Test
    void multipleRequirementsPreferShortageOverPartialForOverall() {
        WorkOrderSparePartRequirement ready = requirement(3, "pcs", "Ready part", null);
        WorkOrderSparePartRequirement partial = requirement(4, "pcs", "Partial part", null);
        WorkOrderSparePartRequirement shortage = requirement(2, "pcs", "Missing part", null);
        givenMaterialState(
                List.of(ready, partial, shortage),
                List.of(reservation(ready.getId(), ready.getSparePartId(), 3)),
                List.of(usage(partial.getId(), partial.getSparePartId(), 1)),
                List.of()
        );

        WorkOrderMaterialReadinessDto result = service.getReadiness(workOrderId);

        assertThat(result.overallStatus()).isEqualTo(MaterialReadinessStatus.SHORTAGE);
        assertThat(result.rows()).extracting("readinessStatus")
                .containsExactly(
                        MaterialReadinessStatus.READY,
                        MaterialReadinessStatus.PARTIAL,
                        MaterialReadinessStatus.SHORTAGE
                );
    }

    @Test
    void zeroRequiredQuantityIsNotRequiredAndNullSafe() {
        WorkOrderSparePartRequirement requirement = requirement(0, null, null, null);
        requirement.setSparePart(null);
        givenMaterialState(List.of(requirement), List.of(), List.of(), List.of());

        WorkOrderMaterialReadinessDto result = service.getReadiness(workOrderId);

        assertThat(result.overallStatus()).isEqualTo(MaterialReadinessStatus.NOT_REQUIRED);
        assertThat(result.rows()).singleElement().satisfies(row -> {
            assertThat(row.readinessStatus()).isEqualTo(MaterialReadinessStatus.NOT_REQUIRED);
            assertThat(row.blocking()).isFalse();
            assertThat(row.sparePartName()).isNull();
            assertThat(row.unit()).isNull();
        });
    }

    @Test
    void fallsBackToWorkOrderAndSparePartWhenRequirementLinkIsMissing() {
        WorkOrderSparePartRequirement requirement = requirement(6, "pcs", "Fallback part", null);
        givenMaterialState(
                List.of(requirement),
                List.of(reservation(null, requirement.getSparePartId(), 2)),
                List.of(usage(null, requirement.getSparePartId(), 4)),
                List.of()
        );

        WorkOrderMaterialReadinessDto result = service.getReadiness(workOrderId);

        assertThat(result.overallStatus()).isEqualTo(MaterialReadinessStatus.READY);
        assertThat(result.rows()).singleElement().satisfies(row -> {
            assertThat(row.reservedQty()).isEqualTo(2);
            assertThat(row.issuedQty()).isEqualTo(4);
            assertThat(row.shortageQty()).isZero();
            assertThat(row.readinessStatus()).isEqualTo(MaterialReadinessStatus.READY);
        });
    }

    @Test
    void missingWorkOrderThrowsNotFound() {
        UUID missingId = UUID.randomUUID();
        when(workOrderRepository.findByIdAndIsDeletedFalse(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getReadiness(missingId))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Work order not found");
    }

    private void givenMaterialState(
            List<WorkOrderSparePartRequirement> requirements,
            List<Reservation> reservations,
            List<RepairMaterialUsage> usages,
            List<RepairMaterialReturn> returns
    ) {
        when(workOrderRepository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(requirementRepository.findActiveByWorkOrderId(workOrderId)).thenReturn(requirements);
        when(reservationRepository.findAllByWorkOrderIdAndStatusAndIsDeletedFalseOrderByUpdatedAtDesc(
                workOrderId,
                ReservationStatus.ACTIVE
        )).thenReturn(reservations);
        when(materialUsageRepository.findAllByWorkOrderIdAndIsDeletedFalseOrderByUpdatedAtDesc(workOrderId))
                .thenReturn(usages);
        when(materialReturnRepository.findAllByWorkOrderIdAndIsDeletedFalseOrderByUpdatedAtDesc(workOrderId))
                .thenReturn(returns);
    }

    private WorkOrderSparePartRequirement requirement(
            double requiredQty,
            String unit,
            String sparePartName,
            String notes
    ) {
        UUID requirementId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        SparePart sparePart = new SparePart();
        sparePart.setId(sparePartId);
        sparePart.setName(sparePartName);
        WorkOrderSparePartRequirement requirement = new WorkOrderSparePartRequirement();
        requirement.setId(requirementId);
        requirement.setWorkOrder(workOrder);
        requirement.setWorkOrderId(workOrderId);
        requirement.setSparePart(sparePart);
        requirement.setSparePartId(sparePartId);
        requirement.setRequiredQty(java.math.BigDecimal.valueOf(requiredQty));
        requirement.setUnit(unit);
        requirement.setNotes(notes);
        return requirement;
    }

    private Reservation reservation(UUID requirementId, UUID sparePartId, double quantity) {
        Reservation reservation = new Reservation();
        reservation.setId(UUID.randomUUID());
        reservation.setWorkOrderId(workOrderId);
        reservation.setRequirementId(requirementId);
        reservation.setSparePartId(sparePartId);
        reservation.setQuantity(java.math.BigDecimal.valueOf(quantity));
        reservation.setStatus(ReservationStatus.ACTIVE);
        return reservation;
    }

    private RepairMaterialUsage usage(UUID requirementId, UUID sparePartId, double quantity) {
        RepairMaterialUsage usage = new RepairMaterialUsage();
        usage.setId(UUID.randomUUID());
        usage.setWorkOrderId(workOrderId);
        usage.setRequirementId(requirementId);
        usage.setSparePartId(sparePartId);
        usage.setQuantity(quantity);
        return usage;
    }

    private RepairMaterialReturn returned(UUID usageId, UUID sparePartId, double quantity) {
        RepairMaterialReturn returned = new RepairMaterialReturn();
        returned.setId(UUID.randomUUID());
        returned.setWorkOrderId(workOrderId);
        returned.setMaterialUsageId(usageId);
        returned.setSparePartId(sparePartId);
        returned.setQuantity(BigDecimal.valueOf(quantity));
        returned.setStatus(RepairMaterialReturnStatus.POSTED);
        return returned;
    }
}
