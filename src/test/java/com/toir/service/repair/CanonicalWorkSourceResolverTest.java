package com.toir.service.repair;

import com.toir.entity.PprPlan;
import com.toir.entity.PprTask;
import com.toir.entity.defects.Defect;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.inspection.InspectionCheckpoint;
import com.toir.entity.inspection.InspectionRound;
import com.toir.entity.inspection.InspectionRoute;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.repair.RepairRequest;
import com.toir.enums.RepairCampaignWorkItemSourceType;
import com.toir.exception.RestException;
import com.toir.repository.PprTaskRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.defects.DefectRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.inspection.InspectionCheckpointRepository;
import com.toir.repository.inspection.InspectionRoundRepository;
import com.toir.repository.repair.RepairRequestRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CanonicalWorkSourceResolverTest {

    @Mock DefectRepository defectRepository;
    @Mock PprTaskRepository pprTaskRepository;
    @Mock RepairRequestRepository repairRequestRepository;
    @Mock InspectionRoundRepository inspectionRoundRepository;
    @Mock InspectionCheckpointRepository checkpointRepository;
    @Mock WorkOrderRepository workOrderRepository;
    @Mock EquipmentRepository equipmentRepository;
    @InjectMocks CanonicalWorkSourceResolver resolver;

    @Test
    void resolvesEveryBackedCanonicalSource() {
        UUID equipmentId = UUID.randomUUID(); UUID departmentId = UUID.randomUUID();
        Equipment equipment = new Equipment(); equipment.setId(equipmentId); equipment.setDepartmentId(departmentId);
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));

        UUID defectId = UUID.randomUUID(); Defect defect = new Defect();
        defect.setEquipmentId(equipmentId); defect.setTitle("Defect");
        when(defectRepository.findByIdAndIsDeletedFalse(defectId)).thenReturn(Optional.of(defect));

        UUID pprId = UUID.randomUUID(); PprPlan plan = new PprPlan(); plan.setDepartmentId(departmentId);
        PprTask ppr = new PprTask(); ppr.setEquipmentId(equipmentId); ppr.setTitle("PPR"); ppr.setPlan(plan);
        when(pprTaskRepository.findByIdAndIsDeletedFalseWithPlan(pprId)).thenReturn(Optional.of(ppr));

        UUID requestId = UUID.randomUUID(); RepairRequest request = new RepairRequest();
        request.setEquipmentId(equipmentId); request.setDepartmentId(departmentId); request.setTitle("Request");
        when(repairRequestRepository.findByIdAndIsDeletedFalse(requestId)).thenReturn(Optional.of(request));

        UUID roundId = UUID.randomUUID(); InspectionRoute route = new InspectionRoute();
        route.setId(UUID.randomUUID()); route.setDepartmentId(departmentId); route.setName("Round");
        InspectionRound round = new InspectionRound(); round.setRoute(route);
        InspectionCheckpoint checkpoint = new InspectionCheckpoint(); checkpoint.setEquipmentId(equipmentId);
        when(inspectionRoundRepository.findByIdAndIsDeletedFalse(roundId)).thenReturn(Optional.of(round));
        when(checkpointRepository.findAllByRouteIdAndIsDeletedFalseOrderByOrderIndexAsc(route.getId()))
                .thenReturn(List.of(checkpoint));

        UUID workOrderId = UUID.randomUUID(); WorkOrder workOrder = new WorkOrder();
        workOrder.setEquipmentId(equipmentId); workOrder.setDepartmentId(departmentId); workOrder.setTitle("WO");
        when(workOrderRepository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));

        assertThat(resolver.resolve(RepairCampaignWorkItemSourceType.DEFECT, defectId, equipmentId, Set.of(departmentId)).title()).isEqualTo("Defect");
        assertThat(resolver.resolve(RepairCampaignWorkItemSourceType.PPR, pprId, equipmentId, Set.of(departmentId)).title()).isEqualTo("PPR");
        assertThat(resolver.resolve(RepairCampaignWorkItemSourceType.REPAIR_REQUEST, requestId, equipmentId, Set.of(departmentId)).title()).isEqualTo("Request");
        assertThat(resolver.resolve(RepairCampaignWorkItemSourceType.INSPECTION_ROUND, roundId, equipmentId, Set.of(departmentId)).title()).isEqualTo("Round");
        assertThat(resolver.resolve(RepairCampaignWorkItemSourceType.WORK_ORDER, workOrderId, equipmentId, Set.of(departmentId)).title()).isEqualTo("WO");
    }

    @Test
    void rejectsMissingDeletedUnsupportedEquipmentMismatchAndForeignDepartment() {
        UUID missing = UUID.randomUUID();
        for (RepairCampaignWorkItemSourceType type : List.of(RepairCampaignWorkItemSourceType.DEFECT,
                RepairCampaignWorkItemSourceType.PPR, RepairCampaignWorkItemSourceType.REPAIR_REQUEST,
                RepairCampaignWorkItemSourceType.INSPECTION_ROUND, RepairCampaignWorkItemSourceType.WORK_ORDER)) {
            assertThatThrownBy(() -> resolver.resolve(type, missing)).isInstanceOf(RestException.class);
        }
        assertThatThrownBy(() -> resolver.resolve(RepairCampaignWorkItemSourceType.MANUAL, null))
                .isInstanceOf(RestException.class).hasMessageContaining("UNSUPPORTED");

        UUID sourceId = UUID.randomUUID(); UUID equipmentId = UUID.randomUUID(); UUID departmentId = UUID.randomUUID();
        RepairRequest request = new RepairRequest(); request.setEquipmentId(equipmentId);
        request.setDepartmentId(departmentId); request.setTitle("Request");
        when(repairRequestRepository.findByIdAndIsDeletedFalse(sourceId)).thenReturn(Optional.of(request));
        assertThatThrownBy(() -> resolver.resolve(RepairCampaignWorkItemSourceType.REPAIR_REQUEST,
                sourceId, UUID.randomUUID(), Set.of(departmentId)))
                .isInstanceOf(RestException.class).hasMessageContaining("EQUIPMENT_MISMATCH");

        Equipment equipment = new Equipment(); equipment.setDepartmentId(UUID.randomUUID());
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        assertThatThrownBy(() -> resolver.resolve(RepairCampaignWorkItemSourceType.REPAIR_REQUEST,
                sourceId, equipmentId, Set.of(departmentId)))
                .isInstanceOf(RestException.class).hasMessageContaining("FOREIGN_DEPARTMENT");
    }

    @Test
    void inspectionRoundRequiresRequestedEquipmentToBelongToItsRoute() {
        UUID roundId = UUID.randomUUID(); InspectionRoute route = new InspectionRoute();
        route.setId(UUID.randomUUID()); route.setName("multi");
        InspectionRound round = new InspectionRound(); round.setRoute(route);
        InspectionCheckpoint first = new InspectionCheckpoint(); first.setEquipmentId(UUID.randomUUID());
        InspectionCheckpoint second = new InspectionCheckpoint(); second.setEquipmentId(UUID.randomUUID());
        when(inspectionRoundRepository.findByIdAndIsDeletedFalse(roundId)).thenReturn(Optional.of(round));
        when(checkpointRepository.findAllByRouteIdAndIsDeletedFalseOrderByOrderIndexAsc(route.getId()))
                .thenReturn(List.of(first, second));

        assertThatThrownBy(() -> resolver.resolve(RepairCampaignWorkItemSourceType.INSPECTION_ROUND,
                roundId, UUID.randomUUID(), Set.of()))
                .isInstanceOf(RestException.class).hasMessageContaining("AMBIGUOUS");
    }
}
