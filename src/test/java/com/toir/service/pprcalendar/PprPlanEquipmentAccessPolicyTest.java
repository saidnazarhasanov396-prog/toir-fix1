package com.toir.service.pprcalendar;

import com.toir.entity.PprPlan;
import com.toir.entity.equipment.Equipment;
import com.toir.exception.RestException;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.security.ScopeAccessService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PprPlanEquipmentAccessPolicyTest {

    @Test
    void allowsScopeAuthorizedEquipmentWhenPlanHasNoDepartment() {
        EquipmentRepository equipmentRepository = mock(EquipmentRepository.class);
        ScopeAccessService scopeAccessService = mock(ScopeAccessService.class);
        PprPlanEquipmentAccessPolicy policy = new PprPlanEquipmentAccessPolicy(
                equipmentRepository, scopeAccessService);
        UUID equipmentId = UUID.randomUUID();
        UUID equipmentOwnerId = UUID.randomUUID();
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        PprPlan plan = new PprPlan();
        Equipment equipment = new Equipment();
        equipment.setId(equipmentId);
        equipment.setResponsibleDepartmentId(equipmentOwnerId);
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId))
                .thenReturn(Optional.of(equipment));

        assertThat(policy.requireManualTaskEquipment(plan, equipmentId)).isSameAs(equipment);
        verify(scopeAccessService).assertCanAccessEquipmentScope(equipmentOwnerId, null);
    }

    @Test
    void deniesNullDepartmentPlanForANonScopeAdminBeforeEquipmentLookup() {
        EquipmentRepository equipmentRepository = mock(EquipmentRepository.class);
        ScopeAccessService scopeAccessService = mock(ScopeAccessService.class);
        PprPlanEquipmentAccessPolicy policy = new PprPlanEquipmentAccessPolicy(
                equipmentRepository, scopeAccessService);
        UUID equipmentId = UUID.randomUUID();

        assertThatThrownBy(() -> policy.requireManualTaskEquipment(new PprPlan(), equipmentId))
                .isInstanceOf(AccessDeniedException.class);
        verify(equipmentRepository, never()).findByIdAndIsDeletedFalse(equipmentId);
    }

    @Test
    void rejectsManualTaskEquipmentOwnedByAnotherDepartmentBeforeTaskPersistence() {
        EquipmentRepository equipmentRepository = mock(EquipmentRepository.class);
        ScopeAccessService scopeAccessService = mock(ScopeAccessService.class);
        PprPlanEquipmentAccessPolicy policy = new PprPlanEquipmentAccessPolicy(
                equipmentRepository, scopeAccessService);
        UUID equipmentId = UUID.randomUUID();
        UUID planDepartmentId = UUID.randomUUID();
        UUID equipmentOwnerId = UUID.randomUUID();
        PprPlan plan = new PprPlan();
        plan.setDepartmentId(planDepartmentId);
        Equipment equipment = new Equipment();
        equipment.setId(equipmentId);
        equipment.setDepartmentId(equipmentOwnerId);
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));

        assertThatThrownBy(() -> policy.requireManualTaskEquipment(plan, equipmentId))
                .isInstanceOfSatisfying(RestException.class, exception ->
                        assertThat(exception.getMessage()).isEqualTo("Equipment is outside the PPR plan department"));
        verify(scopeAccessService).assertCanAccessEquipmentScope(null, equipmentOwnerId);
    }

    @Test
    void rejectsMissingEquipmentIdWithoutQueryingEquipment() {
        EquipmentRepository equipmentRepository = mock(EquipmentRepository.class);
        ScopeAccessService scopeAccessService = mock(ScopeAccessService.class);
        PprPlanEquipmentAccessPolicy policy = new PprPlanEquipmentAccessPolicy(
                equipmentRepository, scopeAccessService);

        assertThatThrownBy(() -> policy.requireManualTaskEquipment(new PprPlan(), null))
                .isInstanceOfSatisfying(RestException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.getMessage()).isEqualTo("equipmentId is required");
                });
        verify(equipmentRepository, never()).findByIdAndIsDeletedFalse(
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void normalizesMissingAndAccessDeniedEquipmentToTheSamePublicError() {
        EquipmentRepository equipmentRepository = mock(EquipmentRepository.class);
        ScopeAccessService scopeAccessService = mock(ScopeAccessService.class);
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        PprPlanEquipmentAccessPolicy policy = new PprPlanEquipmentAccessPolicy(
                equipmentRepository, scopeAccessService);
        UUID missingId = UUID.randomUUID();
        UUID deniedId = UUID.randomUUID();
        Equipment deniedEquipment = new Equipment();
        deniedEquipment.setDepartmentId(UUID.randomUUID());

        when(equipmentRepository.findByIdAndIsDeletedFalse(missingId)).thenReturn(Optional.empty());
        when(equipmentRepository.findByIdAndIsDeletedFalse(deniedId)).thenReturn(Optional.of(deniedEquipment));
        doThrow(new AccessDeniedException("Access denied"))
                .when(scopeAccessService)
                .assertCanAccessEquipmentScope(null, deniedEquipment.getDepartmentId());

        RestException missing = captureFailure(policy, missingId);
        RestException denied = captureFailure(policy, deniedId);

        assertThat(missing.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(denied.getStatus()).isEqualTo(missing.getStatus());
        assertThat(denied.getMessage()).isEqualTo(missing.getMessage());
        assertThat(denied.getMessage()).isEqualTo("Equipment is unavailable");
    }

    private RestException captureFailure(PprPlanEquipmentAccessPolicy policy, UUID equipmentId) {
        try {
            policy.requireManualTaskEquipment(new PprPlan(), equipmentId);
            throw new AssertionError("Expected equipment validation to fail");
        } catch (RestException exception) {
            return exception;
        }
    }
}
