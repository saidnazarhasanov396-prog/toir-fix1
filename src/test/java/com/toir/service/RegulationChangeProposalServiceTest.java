package com.toir.service;

import com.toir.dto.regulationchangeproposal.RegulationChangeProposalRequest;
import com.toir.dto.regulationchangeproposal.RegulationChangeProposalReviewRequest;
import com.toir.entity.maintenance.MaintenanceRegulation;
import com.toir.entity.maintenance.RegulationChangeProposal;
import com.toir.enums.PeriodicityUnit;
import com.toir.enums.RegulationChangeProposalStatus;
import com.toir.exception.RestException;
import com.toir.repository.maintenance.MaintenanceRegulationRepository;
import com.toir.repository.maintenance.RegulationChangeProposalRepository;
import com.toir.security.SecurityScope;
import com.toir.util.AuditBuilderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegulationChangeProposalServiceTest {

    @Mock RegulationChangeProposalRepository repository;
    @Mock MaintenanceRegulationRepository regulationRepository;
    @Mock SecurityScope securityScope;
    @Mock AuditBuilderService auditBuilderService;

    @InjectMocks
    RegulationChangeProposalService service;

    // --- create ---

    @Test
    void create_regulationNotFound_throws404() {
        UUID regulationId = UUID.randomUUID();
        when(regulationRepository.findByIdAndIsDeletedFalse(regulationId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(new RegulationChangeProposalRequest(
                regulationId, null, "Test", null, null, null, null, null
        ))).isInstanceOf(RestException.class);

        verify(repository, never()).save(any());
    }

    @Test
    void create_validRequest_savedAsDraft() {
        UUID regulationId = UUID.randomUUID();
        when(regulationRepository.findByIdAndIsDeletedFalse(regulationId))
                .thenReturn(Optional.of(regulation(regulationId)));
        when(repository.save(any())).thenAnswer(i -> { RegulationChangeProposal p = i.getArgument(0); if (p.getId() == null) p.setId(UUID.randomUUID()); return p; });

        var result = service.create(new RegulationChangeProposalRequest(
                regulationId, null, "Test title", "desc", null, null, null, "reason"
        ));

        assertThat(result.status()).isEqualTo(RegulationChangeProposalStatus.DRAFT);
        assertThat(result.title()).isEqualTo("Test title");
    }

    // --- submit ---

    @Test
    void submit_draftProposal_becomesSubmitted() {
        UUID id = UUID.randomUUID();
        RegulationChangeProposal proposal = proposal(id, RegulationChangeProposalStatus.DRAFT);
        when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(proposal));
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        var result = service.submit(id);

        assertThat(result.status()).isEqualTo(RegulationChangeProposalStatus.SUBMITTED);
    }

    @Test
    void submit_nonDraftProposal_throws400() {
        UUID id = UUID.randomUUID();
        RegulationChangeProposal proposal = proposal(id, RegulationChangeProposalStatus.SUBMITTED);
        when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(proposal));

        assertThatThrownBy(() -> service.submit(id))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("DRAFT");

        verify(repository, never()).save(any());
    }

    // --- approve ---

    @Test
    void approve_submittedProposal_becomesApproved() {
        UUID id = UUID.randomUUID();
        UUID regulationId = UUID.randomUUID();
        RegulationChangeProposal proposal = proposal(id, RegulationChangeProposalStatus.SUBMITTED);
        proposal.setRegulationId(regulationId);

        when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(proposal));
        when(regulationRepository.findByIdAndIsDeletedFalse(regulationId))
                .thenReturn(Optional.of(regulation(regulationId)));
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(securityScope.currentUser()).thenReturn(null);

        var result = service.approve(id, new RegulationChangeProposalReviewRequest("approved"));

        assertThat(result.status()).isEqualTo(RegulationChangeProposalStatus.APPROVED);
        assertThat(result.reviewComment()).isEqualTo("approved");
    }

    @Test
    void approve_draftProposal_throws400() {
        UUID id = UUID.randomUUID();
        RegulationChangeProposal proposal = proposal(id, RegulationChangeProposalStatus.DRAFT);
        when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(proposal));

        assertThatThrownBy(() -> service.approve(id, null))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("SUBMITTED");

        verify(repository, never()).save(any());
    }

    // --- reject ---

    @Test
    void reject_submittedProposal_becomesRejected() {
        UUID id = UUID.randomUUID();
        RegulationChangeProposal proposal = proposal(id, RegulationChangeProposalStatus.SUBMITTED);
        when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(proposal));
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(securityScope.currentUser()).thenReturn(null);

        var result = service.reject(id, new RegulationChangeProposalReviewRequest("rejected"));

        assertThat(result.status()).isEqualTo(RegulationChangeProposalStatus.REJECTED);
    }

    @Test
    void approve_updatesRegulationPeriodicity() {
        UUID id = UUID.randomUUID();
        UUID regulationId = UUID.randomUUID();
        RegulationChangeProposal proposal = proposal(id, RegulationChangeProposalStatus.SUBMITTED);
        proposal.setRegulationId(regulationId);
        proposal.setProposedPeriodicityValue(30);
        proposal.setProposedPeriodicityUnit("DAY");

        MaintenanceRegulation regulation = regulation(regulationId);

        when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(proposal));
        when(regulationRepository.findByIdAndIsDeletedFalse(regulationId))
                .thenReturn(Optional.of(regulation));
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(regulationRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(securityScope.currentUser()).thenReturn(null);

        service.approve(id, null);

        assertThat(regulation.getPeriodicityValue()).isEqualTo(30);
        assertThat(regulation.getPeriodicityUnit()).isEqualTo(PeriodicityUnit.DAY);
        verify(regulationRepository).save(regulation);
    }

    // --- helpers ---

    private RegulationChangeProposal proposal(UUID id, RegulationChangeProposalStatus status) {
        RegulationChangeProposal p = new RegulationChangeProposal();
        p.setId(id);  // ← shu qo'shildi
        p.setStatus(status);
        p.setTitle("Test proposal");
        p.setRegulationId(UUID.randomUUID());
        return p;
    }

    private MaintenanceRegulation regulation(UUID id) {
        MaintenanceRegulation r = new MaintenanceRegulation();
        r.setId(id);  // ← shu qo'shildi
        r.setPeriodicityValue(7);
        r.setPeriodicityUnit(PeriodicityUnit.DAY);
        return r;
    }
}
