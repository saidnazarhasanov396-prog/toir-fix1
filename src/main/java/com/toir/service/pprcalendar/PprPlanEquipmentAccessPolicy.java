package com.toir.service.pprcalendar;

import com.toir.entity.PprPlan;
import com.toir.entity.equipment.Equipment;
import com.toir.exception.RestException;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.security.ScopeAccessService;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.security.access.AccessDeniedException;

/** Centralizes ownership and scope checks for PPR calendar equipment access. */
@Service
public class PprPlanEquipmentAccessPolicy {

    private final EquipmentRepository equipmentRepository;
    private final ScopeAccessService scopeAccessService;

    public PprPlanEquipmentAccessPolicy(
            EquipmentRepository equipmentRepository,
            ScopeAccessService scopeAccessService) {
        this.equipmentRepository = equipmentRepository;
        this.scopeAccessService = scopeAccessService;
    }

    public Equipment requireManualTaskEquipment(PprPlan plan, UUID equipmentId) {
        Objects.requireNonNull(plan, "plan is required");
        if (equipmentId == null) {
            throw RestException.badRequest("equipmentId is required");
        }
        if (plan.getDepartmentId() == null && !scopeAccessService.isScopeAdmin()) {
            throw new AccessDeniedException("Access denied by data scope");
        }
        Equipment equipment = equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)
                .orElseThrow(this::equipmentUnavailable);
        try {
            assertCanAccess(equipment);
        } catch (AccessDeniedException exception) {
            throw equipmentUnavailable();
        }
        if (plan.getDepartmentId() != null
                && !Objects.equals(canonicalOwner(equipment), plan.getDepartmentId())) {
            throw RestException.badRequest("Equipment is outside the PPR plan department");
        }
        return equipment;
    }

    private RestException equipmentUnavailable() {
        return RestException.notFound("Equipment is unavailable");
    }

    public void assertCanAccess(Equipment equipment) {
        Objects.requireNonNull(equipment, "equipment is required");
        scopeAccessService.assertCanAccessEquipmentScope(
                equipment.getResponsibleDepartmentId(), equipment.getDepartmentId());
    }

    public UUID canonicalOwner(Equipment equipment) {
        Objects.requireNonNull(equipment, "equipment is required");
        return equipment.getResponsibleDepartmentId() != null
                ? equipment.getResponsibleDepartmentId()
                : equipment.getDepartmentId();
    }
}
