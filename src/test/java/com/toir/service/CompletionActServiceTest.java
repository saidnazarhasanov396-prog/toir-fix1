package com.toir.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.toir.entity.CompletionAct;
import com.toir.repository.CompletionActRepository;
import com.toir.repository.maintenance.RepairAcceptanceRepository;
import com.toir.security.ScopeAccessService;
import com.toir.util.AuditBuilderService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CompletionActServiceTest {

    @Mock CompletionActRepository repository;
    @Mock RepairAcceptanceRepository acceptances;
    @Mock AuditBuilderService audit;
    @Mock ScopeAccessService scopeAccessService;

    private CompletionActService service;

    @BeforeEach
    void setUp() {
        service = new CompletionActService(repository, acceptances, audit, scopeAccessService);
    }

    @Test
    void createsDeterministicActAfterFinalAcceptanceAndReusesItOnRetry() {
        UUID workOrderId = UUID.randomUUID();
        UUID acceptanceId = UUID.randomUUID();
        when(repository.findByWorkOrderIdAndIsDeletedFalse(workOrderId))
                .thenReturn(Optional.empty());
        when(acceptances.existsAcceptedFinalByWorkOrderId(workOrderId)).thenReturn(true);
        when(repository.save(any(CompletionAct.class))).thenAnswer(invocation -> {
            CompletionAct act = invocation.getArgument(0);
            act.setId(UUID.randomUUID());
            return act;
        });

        var created = service.ensureForAcceptedFinal(workOrderId, acceptanceId, "Работы приняты");

        assertThat(created.workOrderId()).isEqualTo(workOrderId);
        assertThat(created.repairAcceptanceId()).isEqualTo(acceptanceId);
        assertThat(created.actNumber()).isEqualTo("ACT-" + workOrderId);
        verify(repository).save(any(CompletionAct.class));
    }

    @Test
    void signsAsAuthenticatedUserWithoutClientSuppliedSigner() {
        UUID actorId = UUID.randomUUID();
        UUID workOrderId = UUID.randomUUID();
        CompletionAct act = new CompletionAct();
        act.setId(UUID.randomUUID());
        act.setWorkOrderId(workOrderId);
        when(repository.findByIdAndIsDeletedFalse(act.getId())).thenReturn(Optional.of(act));
        when(acceptances.existsAcceptedFinalByWorkOrderId(workOrderId)).thenReturn(true);
        when(scopeAccessService.currentUserIdOrNull()).thenReturn(actorId);
        when(repository.save(any(CompletionAct.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var signed = service.sign(act.getId());

        assertThat(signed.signedById()).isEqualTo(actorId);
    }
}
