package com.toir.service.repair;

import com.toir.dto.repaircampaign.RepairCampaignRiskCreateRequest;
import com.toir.dto.repaircampaign.RepairCampaignRiskUpdateRequest;
import com.toir.entity.repair.RepairCampaign;
import com.toir.entity.repair.RepairCampaignRisk;
import com.toir.entity.users.User;
import com.toir.enums.RepairCampaignRiskLevel;
import com.toir.enums.RepairCampaignRiskStatus;
import com.toir.exception.RestException;
import com.toir.repository.repair.RepairCampaignRepository;
import com.toir.repository.repair.RepairCampaignRiskRepository;
import com.toir.repository.users.UserRepository;
import com.toir.security.ScopeAccessService;
import com.toir.util.AuditBuilderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RepairCampaignRiskServiceTest {

    @Mock RepairCampaignRiskRepository repository;
    @Mock RepairCampaignRepository campaignRepository;
    @Mock UserRepository userRepository;
    @Mock ScopeAccessService scopeAccessService;
    @Mock AuditBuilderService auditBuilderService;
    @InjectMocks RepairCampaignRiskService service;

    @Test
    void listReturnsCreatedRisksWhenOwnerFullNameIsMissing() {
        UUID campaignId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        RepairCampaignRisk risk = risk(UUID.randomUUID(), campaignId, RepairCampaignRiskStatus.OPEN);
        risk.setOwnerId(ownerId);
        when(campaignRepository.findByIdAndIsDeletedFalse(campaignId)).thenReturn(Optional.of(campaign(campaignId)));
        when(repository.findAllByCampaignIdAndIsDeletedFalseOrderByCreatedAtDesc(campaignId))
                .thenReturn(List.of(risk));
        when(userRepository.findAllById(any())).thenReturn(List.of(user(ownerId, null)));

        var result = service.list(campaignId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).ownerName()).isEqualTo("user-" + ownerId);
    }

    @Test
    void listReturnsEmptyCollectionWhenCampaignHasNoRisks() {
        UUID campaignId = UUID.randomUUID();
        when(campaignRepository.findByIdAndIsDeletedFalse(campaignId)).thenReturn(Optional.of(campaign(campaignId)));
        when(repository.findAllByCampaignIdAndIsDeletedFalseOrderByCreatedAtDesc(campaignId))
                .thenReturn(List.of());

        var result = service.list(campaignId);

        assertThat(result).isEmpty();
    }

    @Test
    void createThenListReturnsRiskWithoutOwnerAndNullableFields() {
        UUID campaignId = UUID.randomUUID();
        when(campaignRepository.findByIdAndIsDeletedFalse(campaignId)).thenReturn(Optional.of(campaign(campaignId)));
        when(repository.save(any())).thenAnswer(invocation -> persisted(invocation.getArgument(0)));

        var created = service.create(campaignId, new RepairCampaignRiskCreateRequest(
                "Schedule risk", "", RepairCampaignRiskLevel.LOW,
                RepairCampaignRiskLevel.CRITICAL, null, "", null));

        RepairCampaignRisk stored = risk(created.id(), campaignId, created.status());
        stored.setTitle(created.title());
        stored.setDescription(created.description());
        stored.setLikelihood(created.likelihood());
        stored.setImpact(created.impact());
        stored.setMitigationPlan(created.mitigationPlan());
        stored.setDueDate(created.dueDate());
        when(repository.findAllByCampaignIdAndIsDeletedFalseOrderByCreatedAtDesc(campaignId))
                .thenReturn(List.of(stored));

        var listed = service.list(campaignId);

        assertThat(listed).hasSize(1);
        assertThat(listed.get(0).id()).isEqualTo(created.id());
        assertThat(listed.get(0).ownerId()).isNull();
        assertThat(listed.get(0).ownerName()).isNull();
        assertThat(listed.get(0).description()).isNull();
        assertThat(listed.get(0).mitigationPlan()).isNull();
        assertThat(listed.get(0).likelihood()).isEqualTo(RepairCampaignRiskLevel.LOW);
        assertThat(listed.get(0).impact()).isEqualTo(RepairCampaignRiskLevel.CRITICAL);
        assertThat(listed.get(0).status()).isEqualTo(RepairCampaignRiskStatus.OPEN);
    }

    @Test
    void createForcesOpenAndResolvesOwnerName() {
        UUID campaignId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        when(campaignRepository.findByIdAndIsDeletedFalse(campaignId)).thenReturn(Optional.of(campaign(campaignId)));
        when(userRepository.findByIdAndIsDeletedFalse(ownerId)).thenReturn(Optional.of(user(ownerId, "Ivanov I.")));
        when(repository.save(any())).thenAnswer(invocation -> persisted(invocation.getArgument(0)));

        var result = service.create(campaignId, new RepairCampaignRiskCreateRequest(
                " Supplier delay ", "parts", RepairCampaignRiskLevel.MEDIUM,
                RepairCampaignRiskLevel.HIGH, ownerId, "alternative supplier", LocalDate.of(2026, 7, 20)));

        assertThat(result.status()).isEqualTo(RepairCampaignRiskStatus.OPEN);
        assertThat(result.title()).isEqualTo("Supplier delay");
        assertThat(result.ownerName()).isEqualTo("Ivanov I.");
    }

    @Test
    void createUsesUsernameWhenOwnerFullNameIsMissing() {
        UUID campaignId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        when(campaignRepository.findByIdAndIsDeletedFalse(campaignId)).thenReturn(Optional.of(campaign(campaignId)));
        when(userRepository.findByIdAndIsDeletedFalse(ownerId)).thenReturn(Optional.of(user(ownerId, null)));
        when(repository.save(any())).thenAnswer(invocation -> persisted(invocation.getArgument(0)));

        var result = service.create(campaignId, new RepairCampaignRiskCreateRequest(
                "Owner fallback", null, RepairCampaignRiskLevel.MEDIUM,
                RepairCampaignRiskLevel.HIGH, ownerId, null, null));

        assertThat(result.ownerName()).isEqualTo("user-" + ownerId);
    }

    @Test
    void createAssignsIdentifierBeforeAuditWhenRepositoryReturnsSameEntity() {
        UUID campaignId = UUID.randomUUID();
        when(campaignRepository.findByIdAndIsDeletedFalse(campaignId)).thenReturn(Optional.of(campaign(campaignId)));
        when(repository.save(any())).thenAnswer(invocation -> {
            RepairCampaignRisk risk = invocation.getArgument(0);
            risk.setCreatedAt(Instant.now());
            risk.setUpdatedAt(Instant.now());
            return risk;
        });

        var result = service.create(campaignId, new RepairCampaignRiskCreateRequest(
                "Delivery risk", null, RepairCampaignRiskLevel.MEDIUM,
                RepairCampaignRiskLevel.HIGH, null, null, null));

        assertThat(result.id()).isNotNull();
        verify(auditBuilderService).log(
                org.mockito.ArgumentMatchers.eq("repair_campaign_risk"),
                org.mockito.ArgumentMatchers.eq(result.id().toString()),
                org.mockito.ArgumentMatchers.eq(com.toir.enums.AuditAction.CREATE),
                org.mockito.ArgumentMatchers.eq(com.toir.enums.AuditModule.REPAIR_CAMPAIGN),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsOpenToClosedTransition() {
        UUID campaignId = UUID.randomUUID();
        UUID riskId = UUID.randomUUID();
        when(campaignRepository.findByIdAndIsDeletedFalse(campaignId)).thenReturn(Optional.of(campaign(campaignId)));
        when(repository.findByIdAndCampaignIdAndIsDeletedFalse(riskId, campaignId))
                .thenReturn(Optional.of(risk(riskId, campaignId, RepairCampaignRiskStatus.OPEN)));

        assertThatThrownBy(() -> service.update(campaignId, riskId,
                new RepairCampaignRiskUpdateRequest(null, null, null, null, null, null, null,
                        RepairCampaignRiskStatus.CLOSED)))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("transition");
    }

    @Test
    void advancesOpenRiskToMitigating() {
        UUID campaignId = UUID.randomUUID();
        UUID riskId = UUID.randomUUID();
        RepairCampaignRisk risk = risk(riskId, campaignId, RepairCampaignRiskStatus.OPEN);
        when(campaignRepository.findByIdAndIsDeletedFalse(campaignId)).thenReturn(Optional.of(campaign(campaignId)));
        when(repository.findByIdAndCampaignIdAndIsDeletedFalse(riskId, campaignId)).thenReturn(Optional.of(risk));
        when(repository.save(risk)).thenReturn(risk);

        var result = service.update(campaignId, riskId,
                new RepairCampaignRiskUpdateRequest(null, null, null, null, null, null, null,
                        RepairCampaignRiskStatus.MITIGATING));

        assertThat(result.status()).isEqualTo(RepairCampaignRiskStatus.MITIGATING);
    }

    @Test
    void softDeletesRiskInsideCampaign() {
        UUID campaignId = UUID.randomUUID();
        UUID riskId = UUID.randomUUID();
        RepairCampaignRisk risk = risk(riskId, campaignId, RepairCampaignRiskStatus.OPEN);
        when(campaignRepository.findByIdAndIsDeletedFalse(campaignId)).thenReturn(Optional.of(campaign(campaignId)));
        when(repository.findByIdAndCampaignIdAndIsDeletedFalse(riskId, campaignId)).thenReturn(Optional.of(risk));

        service.delete(campaignId, riskId);

        assertThat(risk.isDeleted()).isTrue();
        verify(repository).save(risk);
    }

    private RepairCampaign campaign(UUID id) {
        RepairCampaign campaign = new RepairCampaign();
        campaign.setId(id);
        campaign.setDepartmentId(UUID.randomUUID());
        campaign.setScopeVersion(0L);
        return campaign;
    }

    private User user(UUID id, String name) {
        User user = new User();
        user.setId(id);
        user.setUsername("user-" + id);
        user.setFullName(name);
        return user;
    }

    private RepairCampaignRisk risk(UUID id, UUID campaignId, RepairCampaignRiskStatus status) {
        RepairCampaignRisk risk = new RepairCampaignRisk();
        risk.setId(id);
        risk.setCampaignId(campaignId);
        risk.setTitle("Delay");
        risk.setLikelihood(RepairCampaignRiskLevel.MEDIUM);
        risk.setImpact(RepairCampaignRiskLevel.HIGH);
        risk.setStatus(status);
        risk.setCreatedAt(Instant.now());
        risk.setUpdatedAt(Instant.now());
        return risk;
    }

    private RepairCampaignRisk persisted(RepairCampaignRisk risk) {
        risk.setId(UUID.randomUUID());
        risk.setCreatedAt(Instant.now());
        risk.setUpdatedAt(Instant.now());
        return risk;
    }
}
