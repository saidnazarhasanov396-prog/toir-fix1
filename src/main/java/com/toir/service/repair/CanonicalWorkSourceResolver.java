package com.toir.service.repair;

import com.toir.entity.inspection.InspectionCheckpoint;
import com.toir.enums.RepairCampaignWorkItemSourceType;
import com.toir.exception.RestException;
import com.toir.repository.PprTaskRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.defects.DefectRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.inspection.InspectionCheckpointRepository;
import com.toir.repository.inspection.InspectionRoundRepository;
import com.toir.repository.repair.RepairRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CanonicalWorkSourceResolver {

    private final DefectRepository defectRepository;
    private final PprTaskRepository pprTaskRepository;
    private final RepairRequestRepository repairRequestRepository;
    private final InspectionRoundRepository inspectionRoundRepository;
    private final InspectionCheckpointRepository inspectionCheckpointRepository;
    private final WorkOrderRepository workOrderRepository;
    private final EquipmentRepository equipmentRepository;

    public record CanonicalWorkSource(UUID sourceId, UUID equipmentId, String title) { }

    private record ScopedSource(CanonicalWorkSource source, UUID sourceDepartmentId) { }

    @Transactional(readOnly = true)
    public CanonicalWorkSource resolve(RepairCampaignWorkItemSourceType type, UUID sourceId) {
        return resolveInternal(type, sourceId, null).source();
    }

    @Transactional(readOnly = true)
    public CanonicalWorkSource resolve(
            RepairCampaignWorkItemSourceType type,
            UUID sourceId,
            UUID expectedEquipmentId,
            Set<UUID> campaignDepartmentIds
    ) {
        ScopedSource scoped = resolveInternal(type, sourceId, expectedEquipmentId);
        CanonicalWorkSource source = scoped.source();
        if (!Objects.equals(source.equipmentId(), expectedEquipmentId)) {
            throw RestException.badRequest("CAMPAIGN_WORK_SOURCE_EQUIPMENT_MISMATCH");
        }
        var equipment = equipmentRepository.findByIdAndIsDeletedFalse(source.equipmentId())
                .orElseThrow(() -> RestException.notFound("Campaign work item equipment not found: " + source.equipmentId()));
        if (campaignDepartmentIds != null && !campaignDepartmentIds.isEmpty()) {
            if (!campaignDepartmentIds.contains(equipment.getDepartmentId())
                    || (scoped.sourceDepartmentId() != null
                    && !campaignDepartmentIds.contains(scoped.sourceDepartmentId()))) {
                throw RestException.badRequest("CAMPAIGN_WORK_SOURCE_FOREIGN_DEPARTMENT");
            }
        }
        return source;
    }

    private ScopedSource resolveInternal(
            RepairCampaignWorkItemSourceType type, UUID sourceId, UUID expectedEquipmentId) {
        if (type == null || type == RepairCampaignWorkItemSourceType.MANUAL) {
            throw RestException.badRequest("CAMPAIGN_WORK_SOURCE_UNSUPPORTED");
        }
        if (sourceId == null) throw RestException.badRequest("CAMPAIGN_WORK_SOURCE_ID_REQUIRED");
        return switch (type) {
            case DEFECT -> {
                var source = defectRepository.findByIdAndIsDeletedFalse(sourceId)
                        .orElseThrow(() -> RestException.notFound("Defect not found: " + sourceId));
                yield scoped(sourceId, source.getEquipmentId(), source.getTitle(), null);
            }
            case PPR -> {
                var source = pprTaskRepository.findByIdAndIsDeletedFalseWithPlan(sourceId)
                        .orElseThrow(() -> RestException.notFound("PPR task not found: " + sourceId));
                yield scoped(sourceId, source.getEquipmentId(), source.getTitle(), source.getPlan().getDepartmentId());
            }
            case REPAIR_REQUEST -> {
                var source = repairRequestRepository.findByIdAndIsDeletedFalse(sourceId)
                        .orElseThrow(() -> RestException.notFound("Repair request not found: " + sourceId));
                yield scoped(sourceId, source.getEquipmentId(), source.getTitle(), source.getDepartmentId());
            }
            case INSPECTION_ROUND -> {
                var source = inspectionRoundRepository.findByIdAndIsDeletedFalse(sourceId)
                        .orElseThrow(() -> RestException.notFound("Inspection round not found: " + sourceId));
                Set<UUID> equipmentIds = new LinkedHashSet<>();
                inspectionCheckpointRepository
                        .findAllByRouteIdAndIsDeletedFalseOrderByOrderIndexAsc(source.getRoute().getId()).stream()
                        .map(InspectionCheckpoint::getEquipmentId).filter(Objects::nonNull).forEach(equipmentIds::add);
                UUID equipmentId = expectedEquipmentId != null && equipmentIds.contains(expectedEquipmentId)
                        ? expectedEquipmentId
                        : equipmentIds.size() == 1 ? equipmentIds.iterator().next() : null;
                if (equipmentId == null) {
                    throw RestException.badRequest("CAMPAIGN_WORK_SOURCE_INSPECTION_EQUIPMENT_AMBIGUOUS");
                }
                yield scoped(sourceId, equipmentId, source.getRoute().getName(), source.getRoute().getDepartmentId());
            }
            case WORK_ORDER -> {
                var source = workOrderRepository.findByIdAndIsDeletedFalse(sourceId)
                        .orElseThrow(() -> RestException.notFound("Work order not found: " + sourceId));
                yield scoped(sourceId, source.getEquipmentId(), source.getTitle(), source.getDepartmentId());
            }
            case MANUAL -> throw RestException.badRequest("CAMPAIGN_WORK_SOURCE_UNSUPPORTED");
        };
    }

    private static ScopedSource scoped(UUID id, UUID equipmentId, String title, UUID departmentId) {
        if (equipmentId == null) throw RestException.badRequest("CAMPAIGN_WORK_SOURCE_EQUIPMENT_REQUIRED");
        return new ScopedSource(new CanonicalWorkSource(id, equipmentId, title), departmentId);
    }
}
