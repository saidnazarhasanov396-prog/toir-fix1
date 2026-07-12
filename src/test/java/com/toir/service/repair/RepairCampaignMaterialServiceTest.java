package com.toir.service.repair;

import com.toir.dto.repaircampaign.RepairCampaignMaterialRequirementRequest;
import com.toir.entity.Reservation;
import com.toir.entity.SparePart;
import com.toir.entity.maintenance.WorkOrderSparePartRequirement;
import com.toir.entity.repair.RepairCampaign;
import com.toir.entity.repair.RepairCampaignMaterialRequirement;
import com.toir.entity.repair.RepairCampaignWorkItem;
import com.toir.entity.warehouse.Warehouse;
import com.toir.entity.warehouse.WarehouseStockBalance;
import com.toir.enums.RepairCampaignPriority;
import com.toir.enums.RepairCampaignStatus;
import com.toir.repository.ReservationRepository;
import com.toir.repository.SparePartRepository;
import com.toir.repository.WarehouseRepository;
import com.toir.repository.WarehouseStockBalanceRepository;
import com.toir.repository.maintenance.WorkOrderSparePartRequirementRepository;
import com.toir.repository.repair.RepairCampaignMaterialRequirementRepository;
import com.toir.repository.repair.RepairCampaignRepository;
import com.toir.repository.repair.RepairCampaignWorkItemRepository;
import com.toir.security.ScopeAccessService;
import com.toir.util.AuditBuilderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RepairCampaignMaterialServiceTest {
    @Mock RepairCampaignRepository campaigns;
    @Mock RepairCampaignWorkItemRepository items;
    @Mock RepairCampaignMaterialRequirementRepository requirements;
    @Mock SparePartRepository spareParts;
    @Mock WarehouseRepository warehouses;
    @Mock WarehouseStockBalanceRepository balances;
    @Mock WorkOrderSparePartRequirementRepository workRequirements;
    @Mock ReservationRepository reservations;
    @Mock ScopeAccessService scope;
    @Mock AuditBuilderService audit;
    @Mock RepairCampaignMutationImpactService mutationImpactService;
    @InjectMocks RepairCampaignMaterialService service;

    UUID campaignId=UUID.randomUUID(),itemId=UUID.randomUUID(),spareId=UUID.randomUUID(),warehouseId=UUID.randomUUID();
    RepairCampaign campaign;

    @BeforeEach void setup(){campaign=new RepairCampaign();campaign.setId(campaignId);campaign.setVersion(1L);campaign.setStatus(RepairCampaignStatus.DRAFT);campaign.setDepartmentId(UUID.randomUUID());org.mockito.Mockito.lenient().when(campaigns.findLockedByIdAndIsDeletedFalse(campaignId)).thenReturn(Optional.of(campaign));org.mockito.Mockito.lenient().when(campaigns.findByIdAndIsDeletedFalse(campaignId)).thenReturn(Optional.of(campaign));RepairCampaignWorkItem item=new RepairCampaignWorkItem();item.setId(itemId);org.mockito.Mockito.lenient().when(items.findByIdAndCampaignIdAndIsDeletedFalse(itemId,campaignId)).thenReturn(Optional.of(item));SparePart spare=new SparePart();spare.setId(spareId);org.mockito.Mockito.lenient().when(spareParts.findByIdAndIsDeletedFalse(spareId)).thenReturn(Optional.of(spare));Warehouse warehouse=new Warehouse();warehouse.setId(warehouseId);warehouse.setActive(true);org.mockito.Mockito.lenient().when(warehouses.findByIdAndIsDeletedFalse(warehouseId)).thenReturn(Optional.of(warehouse));}

    @Test void demandPreservesFourDecimalPrecisionAndNeverReserves(){when(requirements.saveAndFlush(any())).thenAnswer(i->{RepairCampaignMaterialRequirement r=i.getArgument(0);r.setId(UUID.randomUUID());return r;});when(campaigns.saveAndFlush(campaign)).thenAnswer(i->{campaign.setVersion(2L);return campaign;});var result=service.add(campaignId,new RepairCampaignMaterialRequirementRequest(1L,itemId,spareId,warehouseId,new BigDecimal("0.1234"),true,false));assertThat(result.requiredQuantity()).isEqualByComparingTo("0.1234");ArgumentCaptor<RepairCampaignMaterialRequirement> saved=ArgumentCaptor.forClass(RepairCampaignMaterialRequirement.class);verify(requirements).saveAndFlush(saved.capture());assertThat(saved.getValue().getRequiredQuantity()).isEqualByComparingTo("0.1234");verify(reservations,never()).save(any());}

    @Test void removalConstraintIsTranslatedToTypedConflict(){
        UUID id=UUID.randomUUID();RepairCampaignMaterialRequirement requirement=critical(id,spareId,warehouseId,"1.0000");
        when(requirements.findByIdAndRepairCampaignIdAndIsDeletedFalse(id,campaignId)).thenReturn(Optional.of(requirement));
        when(requirements.saveAndFlush(requirement)).thenThrow(new org.springframework.dao.DataIntegrityViolationException("constraint",new java.sql.SQLException("RC_MATERIAL_REQUIREMENT_IN_USE","23514")));
        assertThatThrownBy(()->service.remove(campaignId,id,1L))
                .isInstanceOf(com.toir.exception.RestException.class)
                .hasMessage("RC_MATERIAL_REQUIREMENT_IN_USE")
                .extracting("status").isEqualTo(org.springframework.http.HttpStatus.CONFLICT);
    }

    @Test void removalJpaSystemConstraintIsTranslatedButUnrelatedIntegrityIsNotSwallowed(){
        UUID id=UUID.randomUUID();RepairCampaignMaterialRequirement requirement=critical(id,spareId,warehouseId,"1.0000");
        when(requirements.findByIdAndRepairCampaignIdAndIsDeletedFalse(id,campaignId)).thenReturn(Optional.of(requirement));
        when(requirements.saveAndFlush(requirement)).thenThrow(new org.springframework.orm.jpa.JpaSystemException(
                new RuntimeException(new java.sql.SQLException("RC_MATERIAL_REQUIREMENT_IN_USE","23514"))));
        assertThatThrownBy(()->service.remove(campaignId,id,1L))
                .isInstanceOf(com.toir.exception.RestException.class).hasMessage("RC_MATERIAL_REQUIREMENT_IN_USE");

        requirement.setDeleted(false);
        var unrelated=new org.springframework.dao.DataIntegrityViolationException("other",
                new java.sql.SQLException("RC_MATERIAL_REQUIREMENT_IN_USE","23505"));
        org.mockito.Mockito.doThrow(unrelated).when(requirements).saveAndFlush(requirement);
        assertThatThrownBy(()->service.remove(campaignId,id,1L)).isSameAs(unrelated);
    }

    @Test void removalPrecheckRejectsActiveCanonicalWorkRequirementBeforeMutation(){
        UUID id=UUID.randomUUID();RepairCampaignMaterialRequirement requirement=critical(id,spareId,warehouseId,"1.0000");
        WorkOrderSparePartRequirement linked=workRequirement(id,spareId);
        when(requirements.findByIdAndRepairCampaignIdAndIsDeletedFalse(id,campaignId)).thenReturn(Optional.of(requirement));
        when(workRequirements.findAllByCampaignRequirementIdInAndIsDeletedFalse(List.of(id))).thenReturn(List.of(linked));
        assertThatThrownBy(()->service.remove(campaignId,id,1L))
                .isInstanceOf(com.toir.exception.RestException.class)
                .hasMessage("RC_MATERIAL_REQUIREMENT_IN_USE")
                .extracting("status").isEqualTo(org.springframework.http.HttpStatus.CONFLICT);
        assertThat(requirement.isDeleted()).isFalse();
        verify(requirements,never()).saveAndFlush(any());
    }

    @Test void assessmentReturnsExactDeterministicMissingDeficitDuplicateAndProcurementBlockers(){UUID missingItem=UUID.randomUUID(),requirementId=UUID.randomUUID(),workRequirementId=UUID.randomUUID();RepairCampaignWorkItem missing=new RepairCampaignWorkItem();missing.setId(missingItem);missing.setPriority(RepairCampaignPriority.CRITICAL);RepairCampaignMaterialRequirement requirement=new RepairCampaignMaterialRequirement();requirement.setId(requirementId);requirement.setRepairCampaignId(campaignId);requirement.setWorkItemId(itemId);requirement.setSparePartId(spareId);requirement.setWarehouseId(warehouseId);requirement.setRequiredQuantity(new BigDecimal("5.0000"));requirement.setCritical(true);requirement.setProcurementRequired(true);WarehouseStockBalance balance=new WarehouseStockBalance();balance.setQtyOnHand(new BigDecimal("3.0000"));balance.setQtyReserved(new BigDecimal("1.0000"));WorkOrderSparePartRequirement workRequirement=new WorkOrderSparePartRequirement();workRequirement.setId(workRequirementId);workRequirement.setWorkOrderId(UUID.randomUUID());workRequirement.setCampaignRequirementId(requirementId);workRequirement.setSparePartId(spareId);when(items.findAllByCampaignIdAndIsDeletedFalseOrderByOrderNumberAsc(campaignId)).thenReturn(List.of(missing));when(requirements.findAllByRepairCampaignIdAndIsDeletedFalseOrderByWorkItemIdAscSparePartIdAsc(campaignId)).thenReturn(List.of(requirement));when(balances.findAllByWarehouseIdAndSparePartIdAndIsDeletedFalse(warehouseId,spareId)).thenReturn(List.of(balance));when(workRequirements.findAllByCampaignRequirementIdInAndIsDeletedFalse(List.of(requirementId))).thenReturn(List.of(workRequirement));when(reservations.findAllByWorkOrderIdAndRequirementIdAndSparePartIdAndStatusAndIsDeletedFalse(any(),any(),any(),any())).thenReturn(List.of(new Reservation(),new Reservation()));assertThat(service.blockers(campaignId)).containsExactly("CRITICAL_MATERIAL_DEFICIT:"+requirementId,"MATERIAL_REQUIREMENT_MISSING:"+missingItem,"PROCUREMENT_REQUIRED:"+requirementId,"RESERVATION_DUPLICATE:"+workRequirementId);}

    @Test void fullyReservedCanonicalRequirementHasNoFalseDeficit(){
        UUID requirementId=UUID.randomUUID();
        RepairCampaignMaterialRequirement requirement=critical(requirementId,spareId,warehouseId,"5.0000");
        WorkOrderSparePartRequirement workRequirement=workRequirement(requirementId,spareId);
        Reservation reservation=new Reservation(); reservation.setQuantity(new BigDecimal("5.0000"));
        when(requirements.findAllByRepairCampaignIdAndIsDeletedFalseOrderByWorkItemIdAscSparePartIdAsc(campaignId)).thenReturn(List.of(requirement));
        when(workRequirements.findAllByCampaignRequirementIdInAndIsDeletedFalse(List.of(requirementId))).thenReturn(List.of(workRequirement));
        when(balances.findAllByWarehouseIdAndSparePartIdAndIsDeletedFalse(warehouseId,spareId)).thenReturn(List.of());
        when(reservations.findAllByWorkOrderIdAndRequirementIdAndSparePartIdAndStatusAndIsDeletedFalse(workRequirement.getWorkOrderId(),workRequirement.getId(),spareId,com.toir.enums.ReservationStatus.ACTIVE)).thenReturn(List.of(reservation));
        assertThat(service.blockers(campaignId)).doesNotContain("CRITICAL_MATERIAL_DEFICIT:"+requirementId);
    }

    @Test void sharedAvailableStockIsConsumedOnlyOnceAcrossCanonicalRequirements(){
        UUID firstId=new UUID(0,1),secondId=new UUID(0,2);
        var first=critical(firstId,spareId,warehouseId,"4.0000");
        var second=critical(secondId,spareId,warehouseId,"4.0000");
        WarehouseStockBalance balance=new WarehouseStockBalance(); balance.setQtyOnHand(new BigDecimal("5.0000")); balance.setQtyReserved(BigDecimal.ZERO);
        when(requirements.findAllByRepairCampaignIdAndIsDeletedFalseOrderByWorkItemIdAscSparePartIdAsc(campaignId)).thenReturn(List.of(second,first));
        when(workRequirements.findAllByCampaignRequirementIdInAndIsDeletedFalse(List.of(firstId,secondId))).thenReturn(List.of());
        when(balances.findAllByWarehouseIdAndSparePartIdAndIsDeletedFalse(warehouseId,spareId)).thenReturn(List.of(balance));
        assertThat(service.blockers(campaignId)).containsExactly("CRITICAL_MATERIAL_DEFICIT:"+secondId);
    }

    @Test void reservationForAnotherCanonicalRequirementDoesNotCreditDeficit(){
        UUID requirementId=UUID.randomUUID(), otherRequirementId=UUID.randomUUID();
        var requirement=critical(requirementId,spareId,warehouseId,"5.0000");
        var other=workRequirement(otherRequirementId,spareId);
        Reservation reservation=new Reservation(); reservation.setQuantity(new BigDecimal("9.0000"));
        when(requirements.findAllByRepairCampaignIdAndIsDeletedFalseOrderByWorkItemIdAscSparePartIdAsc(campaignId)).thenReturn(List.of(requirement));
        when(workRequirements.findAllByCampaignRequirementIdInAndIsDeletedFalse(List.of(requirementId))).thenReturn(List.of());
        when(balances.findAllByWarehouseIdAndSparePartIdAndIsDeletedFalse(warehouseId,spareId)).thenReturn(List.of());
        org.mockito.Mockito.lenient().when(reservations.findAllByWorkOrderIdAndRequirementIdAndSparePartIdAndStatusAndIsDeletedFalse(other.getWorkOrderId(),other.getId(),spareId,com.toir.enums.ReservationStatus.ACTIVE)).thenReturn(List.of(reservation));
        assertThat(service.blockers(campaignId)).containsExactly("CRITICAL_MATERIAL_DEFICIT:"+requirementId);
    }

    private RepairCampaignMaterialRequirement critical(UUID id,UUID spare,UUID warehouse,String quantity){RepairCampaignMaterialRequirement r=new RepairCampaignMaterialRequirement();r.setId(id);r.setRepairCampaignId(campaignId);r.setWorkItemId(itemId);r.setSparePartId(spare);r.setWarehouseId(warehouse);r.setRequiredQuantity(new BigDecimal(quantity));r.setCritical(true);return r;}
    private WorkOrderSparePartRequirement workRequirement(UUID campaignRequirementId,UUID spare){WorkOrderSparePartRequirement r=new WorkOrderSparePartRequirement();r.setId(UUID.randomUUID());r.setWorkOrderId(UUID.randomUUID());r.setCampaignRequirementId(campaignRequirementId);r.setSparePartId(spare);return r;}
}
