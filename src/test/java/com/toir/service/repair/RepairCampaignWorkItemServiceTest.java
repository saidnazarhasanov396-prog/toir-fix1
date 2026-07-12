package com.toir.service.repair;

import com.toir.dto.repaircampaign.RepairCampaignWorkItemRequest;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.repair.RepairCampaign;
import com.toir.entity.repair.RepairCampaignWorkItem;
import com.toir.enums.RepairCampaignStatus;
import com.toir.enums.RepairCampaignWorkItemSourceType;
import com.toir.enums.RepairCampaignWorkItemStatus;
import com.toir.exception.RestException;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.repair.RepairCampaignDepartmentRepository;
import com.toir.repository.repair.RepairCampaignRepository;
import com.toir.repository.repair.RepairCampaignWorkItemRepository;
import com.toir.util.AuditBuilderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RepairCampaignWorkItemServiceTest {
    @Mock RepairCampaignRepository campaignRepository;
    @Mock RepairCampaignDepartmentRepository departmentRepository;
    @Mock RepairCampaignWorkItemRepository repository;
    @Mock EquipmentRepository equipmentRepository;
    @Mock CanonicalWorkSourceResolver sourceResolver;
    @Mock AuditBuilderService auditBuilderService;
    @Mock RepairCampaignDependencyPolicy dependencyPolicy;
    @Mock RepairCampaignResourcePolicy resourcePolicy;
    @InjectMocks RepairCampaignWorkItemService service;

    @Test
    void manualSourceRequiresNullIdentityAndCanonicalSourceRequiresIdentity() {
        RepairCampaign campaign = campaign(RepairCampaignStatus.DRAFT, 3L);
        when(campaignRepository.findLockedByIdAndIsDeletedFalse(campaign.getId())).thenReturn(Optional.of(campaign));

        assertThatThrownBy(() -> service.add(campaign.getId(), request(3L,
                RepairCampaignWorkItemSourceType.MANUAL, UUID.randomUUID(), UUID.randomUUID(), "manual", 0)))
                .isInstanceOf(RestException.class).hasMessageContaining("sourceId must be null");
        assertThatThrownBy(() -> service.add(campaign.getId(), request(3L,
                RepairCampaignWorkItemSourceType.DEFECT, null, UUID.randomUUID(), null, 0)))
                .isInstanceOf(RestException.class).hasMessageContaining("sourceId is required");
    }

    @Test
    void addUsesCanonicalIdentityIncrementsVersionAndAudits() {
        RepairCampaign campaign = campaign(RepairCampaignStatus.DRAFT, 3L);
        UUID equipmentId = UUID.randomUUID(); UUID sourceId = UUID.randomUUID();
        when(campaignRepository.findLockedByIdAndIsDeletedFalse(campaign.getId())).thenReturn(Optional.of(campaign));
        when(sourceResolver.resolve(RepairCampaignWorkItemSourceType.DEFECT, sourceId,
                new CanonicalWorkSourceResolver.ResolutionScope(equipmentId, java.util.Set.of(campaign.getDepartmentId()))))
                .thenReturn(new CanonicalWorkSourceResolver.CanonicalWorkSource(sourceId, equipmentId, "Canonical"));
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> {
            RepairCampaignWorkItem item = invocation.getArgument(0); item.setId(UUID.randomUUID()); return item;
        });
        when(campaignRepository.saveAndFlush(campaign)).thenAnswer(invocation -> {
            campaign.setVersion(4L); return campaign;
        });

        var result = service.add(campaign.getId(), request(3L, RepairCampaignWorkItemSourceType.DEFECT,
                sourceId, equipmentId, "ignored", 0));

        assertThat(result.title()).isEqualTo("Canonical");
        assertThat(result.status()).isEqualTo(RepairCampaignWorkItemStatus.PENDING);
        assertThat(result.campaignVersion()).isEqualTo(4L);
        verify(auditBuilderService).log(eq("repair_campaign_work_item"), any(), eq(com.toir.enums.AuditAction.CREATE),
                eq(com.toir.enums.AuditModule.REPAIR_CAMPAIGN), any(), isNull(), eq(result));
    }

    @Test
    void rejectsStaleVersionDuplicateSourceAndDuplicateOrder() {
        RepairCampaign campaign = campaign(RepairCampaignStatus.DRAFT, 3L);
        when(campaignRepository.findLockedByIdAndIsDeletedFalse(campaign.getId())).thenReturn(Optional.of(campaign));
        assertThatThrownBy(() -> service.add(campaign.getId(), request(2L,
                RepairCampaignWorkItemSourceType.DEFECT, UUID.randomUUID(), UUID.randomUUID(), null, 0)))
                .isInstanceOf(RestException.class).hasMessageContaining("modified");

        UUID equipmentId = UUID.randomUUID(); UUID sourceId = UUID.randomUUID();
        when(sourceResolver.resolve(any(), eq(sourceId), any()))
                .thenReturn(new CanonicalWorkSourceResolver.CanonicalWorkSource(sourceId, equipmentId, "canonical"));
        when(repository.existsByCampaignIdAndSourceTypeAndSourceIdAndIsDeletedFalse(
                campaign.getId(), RepairCampaignWorkItemSourceType.DEFECT, sourceId)).thenReturn(true);
        assertThatThrownBy(() -> service.add(campaign.getId(), request(3L,
                RepairCampaignWorkItemSourceType.DEFECT, sourceId, equipmentId, null, 0)))
                .hasMessageContaining("SOURCE_DUPLICATE");

        when(repository.existsByCampaignIdAndSourceTypeAndSourceIdAndIsDeletedFalse(
                campaign.getId(), RepairCampaignWorkItemSourceType.DEFECT, sourceId)).thenReturn(false);
        when(repository.existsByCampaignIdAndOrderNumberAndIsDeletedFalse(campaign.getId(), 0)).thenReturn(true);
        assertThatThrownBy(() -> service.add(campaign.getId(), request(3L,
                RepairCampaignWorkItemSourceType.DEFECT, sourceId, equipmentId, null, 0)))
                .hasMessageContaining("ORDER_DUPLICATE");
    }

    @Test
    void approvalFreezesAddRemoveReorderAndIdentityChangesButAllowsStatusNotesUpdate() {
        RepairCampaign campaign = campaign(RepairCampaignStatus.APPROVED, 3L);
        RepairCampaignWorkItem item = item(campaign, 0);
        when(campaignRepository.findLockedByIdAndIsDeletedFalse(campaign.getId())).thenReturn(Optional.of(campaign));

        assertThatThrownBy(() -> service.add(campaign.getId(), request(3L,
                RepairCampaignWorkItemSourceType.MANUAL, null, item.getEquipmentId(), "x", 1)))
                .hasMessageContaining("CAMPAIGN_SCOPE_FROZEN");
        assertThatThrownBy(() -> service.remove(campaign.getId(), item.getId(), 3L))
                .hasMessageContaining("CAMPAIGN_SCOPE_FROZEN");
        assertThatThrownBy(() -> service.reorder(campaign.getId(), List.of(item.getId()), 3L))
                .hasMessageContaining("CAMPAIGN_SCOPE_FROZEN");

        when(repository.findByIdAndCampaignIdAndIsDeletedFalse(item.getId(), campaign.getId()))
                .thenReturn(Optional.of(item));
        when(repository.saveAndFlush(item)).thenReturn(item);
        when(campaignRepository.saveAndFlush(campaign)).thenAnswer(inv -> { campaign.setVersion(4L); return campaign; });
        var statusOnly = request(3L, RepairCampaignWorkItemSourceType.MANUAL, null,
                item.getEquipmentId(), item.getTitle(), 0, RepairCampaignWorkItemStatus.COMPLETED, "done");
        assertThat(service.update(campaign.getId(), item.getId(), statusOnly).status())
                .isEqualTo(RepairCampaignWorkItemStatus.PENDING);

        campaign.setVersion(4L);
        assertThatThrownBy(() -> service.update(campaign.getId(), item.getId(), request(4L,
                RepairCampaignWorkItemSourceType.MANUAL, null, UUID.randomUUID(), "changed", 0)))
                .hasMessageContaining("CAMPAIGN_SCOPE_FROZEN");
    }

    @Test
    void genericPutCannotClearServerOwnedReplanRequiredStatus() {
        RepairCampaign campaign = campaign(RepairCampaignStatus.DRAFT, 3L);
        RepairCampaignWorkItem item = item(campaign, 0);
        item.setStatus(RepairCampaignWorkItemStatus.REPLAN_REQUIRED);
        when(campaignRepository.findLockedByIdAndIsDeletedFalse(campaign.getId())).thenReturn(Optional.of(campaign));
        when(repository.findByIdAndCampaignIdAndIsDeletedFalse(item.getId(), campaign.getId()))
                .thenReturn(Optional.of(item));
        Equipment equipment = new Equipment(); equipment.setDepartmentId(campaign.getDepartmentId());
        when(equipmentRepository.findByIdAndIsDeletedFalse(item.getEquipmentId())).thenReturn(Optional.of(equipment));
        when(repository.saveAndFlush(item)).thenReturn(item);
        when(campaignRepository.saveAndFlush(campaign)).thenAnswer(inv -> { campaign.setVersion(4L); return campaign; });

        var attemptedClear = request(3L, RepairCampaignWorkItemSourceType.MANUAL, null,
                item.getEquipmentId(), item.getTitle(), 0, RepairCampaignWorkItemStatus.PENDING, "keep status");
        assertThat(service.update(campaign.getId(), item.getId(), attemptedClear).status())
                .isEqualTo(RepairCampaignWorkItemStatus.REPLAN_REQUIRED);
    }

    @Test
    void namedDatabaseConstraintsMapToStableConflictCodesAndUnrelatedFailuresEscape() {
        RepairCampaign campaign = campaign(RepairCampaignStatus.DRAFT, 3L);
        UUID equipmentId = UUID.randomUUID(); Equipment equipment = new Equipment();
        equipment.setDepartmentId(campaign.getDepartmentId());
        when(campaignRepository.findLockedByIdAndIsDeletedFalse(campaign.getId())).thenReturn(Optional.of(campaign));
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        DataIntegrityViolationException sourceFailure = new DataIntegrityViolationException(
                "uq_repair_campaign_work_items_active_source");
        when(repository.saveAndFlush(any())).thenThrow(sourceFailure);
        assertThatThrownBy(() -> service.add(campaign.getId(), request(3L,
                RepairCampaignWorkItemSourceType.MANUAL, null, equipmentId, "manual", 0)))
                .hasMessageContaining("SOURCE_DUPLICATE");

        reset(repository);
        DataIntegrityViolationException unrelated = new DataIntegrityViolationException("some_other_constraint");
        when(repository.saveAndFlush(any())).thenThrow(unrelated);
        assertThatThrownBy(() -> service.add(campaign.getId(), request(3L,
                RepairCampaignWorkItemSourceType.MANUAL, null, equipmentId, "manual", 0))).isSameAs(unrelated);
    }

    private static RepairCampaign campaign(RepairCampaignStatus status, long version) {
        RepairCampaign campaign = new RepairCampaign(); campaign.setId(UUID.randomUUID());
        campaign.setDepartmentId(UUID.randomUUID()); campaign.setStatus(status); campaign.setVersion(version);
        return campaign;
    }

    private static RepairCampaignWorkItem item(RepairCampaign campaign, int order) {
        RepairCampaignWorkItem item = new RepairCampaignWorkItem(); item.setId(UUID.randomUUID()); item.setCampaign(campaign);
        item.setSourceType(RepairCampaignWorkItemSourceType.MANUAL); item.setEquipmentId(UUID.randomUUID());
        item.setTitle("manual"); item.setStatus(RepairCampaignWorkItemStatus.PENDING); item.setOrderNumber(order);
        return item;
    }

    private static RepairCampaignWorkItemRequest request(Long version, RepairCampaignWorkItemSourceType type,
            UUID sourceId, UUID equipmentId, String title, int order) {
        return request(version, type, sourceId, equipmentId, title, order, null, null);
    }

    private static RepairCampaignWorkItemRequest request(Long version, RepairCampaignWorkItemSourceType type,
            UUID sourceId, UUID equipmentId, String title, int order,
            RepairCampaignWorkItemStatus status, String notes) {
        return new RepairCampaignWorkItemRequest(version, type, sourceId, equipmentId, title, order, notes);
    }
}
