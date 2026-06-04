package com.toir.service;

import com.toir.dto.workorder.CloseWorkOrderRequest;
import com.toir.dto.workorder.CompleteWorkOrderRequest;
import com.toir.dto.workorder.WorkOrderDto;
import com.toir.dto.workorder.WorkOrderRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toir.dto.materialusage.RepairMaterialUsageDto;
import com.toir.entity.CompletionAct;
import com.toir.entity.Department;
import com.toir.entity.PprPlan;
import com.toir.entity.PprTask;
import com.toir.entity.SafetyPermit;
import com.toir.entity.defects.Defect;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.maintenance.MaintenanceCompletionAnchor;
import com.toir.entity.maintenance.MaintenanceDueEvent;
import com.toir.entity.equipment.EquipmentNode;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.maintenance.WorkOrderTask;
import com.toir.entity.repair.RepairRequest;
import com.toir.entity.warehouse.Warehouse;
import com.toir.entity.warehouse.WarehouseEquipmentItem;
import com.toir.enums.EquipmentNodeType;
import com.toir.enums.MaintenanceDueEventStatus;
import com.toir.enums.MaintenanceDueStatus;
import com.toir.enums.MaintenanceTriggerSource;
import com.toir.enums.MeterType;
import com.toir.enums.PlanStatus;
import com.toir.enums.DefectStatus;
import com.toir.enums.PprTaskStatus;
import com.toir.enums.PriorityLevel;
import com.toir.enums.RequestStatus;
import com.toir.enums.SafetyPermitStatus;
import com.toir.enums.TaskExecutionStatus;
import com.toir.enums.WarehouseEquipmentStatus;
import com.toir.enums.WorkOrderStatus;
import com.toir.enums.WorkOrderType;
import com.toir.enums.WorkType;
import com.toir.repository.CompletionActRepository;
import com.toir.repository.PprPlanRepository;
import com.toir.exception.RestException;
import com.toir.repository.PprTaskRepository;
import com.toir.repository.SafetyPermitRepository;
import com.toir.repository.WarehouseEquipmentItemRepository;
import com.toir.repository.WarehouseRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.WorkExecutionRepository;
import com.toir.repository.department.DepartmentRepository;
import com.toir.repository.defects.DefectRepository;
import com.toir.repository.equipment.EquipmentNodeRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.maintenance.MaintenanceCompletionAnchorRepository;
import com.toir.repository.projection.WorkOrderCountProjection;
import com.toir.repository.repair.RepairMaterialUsageRepository;
import com.toir.repository.repair.RepairRequestRepository;
import com.toir.service.equipment.EquipmentStatusLifecycleService;
import com.toir.service.maintanance.MaintenanceAutomationService;
import com.toir.service.maintanance.MaintenanceDueEventService;
import com.toir.service.repair.RepairMaterialUsageService;
import com.toir.util.AuditBuilderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
class WorkOrderServiceTest {

    @Mock
    WorkOrderRepository repository;

    @Mock
    EquipmentRepository equipmentRepository;

    @Mock
    EquipmentNodeRepository equipmentNodeRepository;

    @Mock
    DepartmentRepository departmentRepository;

    @Mock
    AuditBuilderService auditBuilderService;

    @Mock
    PprTaskRepository pprTaskRepository;

    @Mock
    PprPlanRepository pprPlanRepository;

    @Mock
    RepairRequestRepository repairRequestRepository;

    @Mock
    DefectRepository defectRepository;

    @Mock
    WorkExecutionRepository workExecutionRepository;

    @Mock
    RepairMaterialUsageRepository repairMaterialUsageRepository;

    @Mock
    WarehouseRepository warehouseRepository;

    @Mock
    WarehouseEquipmentItemRepository warehouseEquipmentItemRepository;

    @Mock
    WarehouseEquipmentItemService warehouseEquipmentItemService;

    @Mock
    SafetyPermitRepository safetyPermitRepository;

    @Mock
    CompletionActRepository completionActRepository;

    @Mock
    EquipmentStatusLifecycleService equipmentStatusLifecycleService;

    @Mock
    RepairMaterialUsageService repairMaterialUsageService;

    @Mock
    MaintenanceCompletionAnchorRepository maintenanceCompletionAnchorRepository;

    @Mock
    MaintenanceDueEventService maintenanceDueEventService;

    @Mock
    ObjectProvider<MaintenanceAutomationService> maintenanceAutomationServiceProvider;

    @Mock
    MaintenanceAutomationService maintenanceAutomationService;

    @Mock
    ObjectMapper objectMapper;

    @InjectMocks
    WorkOrderService service;

    @Test
    void createOldStyleWorkOrderWithoutWorkTypeShouldSucceedAndPersistRepair() {

        when(repository.save(any(WorkOrder.class)))
                .thenAnswer(invocation -> {
                    WorkOrder workOrder = invocation.getArgument(0);
                    ReflectionTestUtils.setField(workOrder, "id", UUID.randomUUID());
                    return workOrder;
                });

        WorkOrderRequest request = request(WorkOrderType.PLANNED, null, null, null);

        mockSuccessfulCreateDependencies(request);

        WorkOrderDto result = service.create(request);

        ArgumentCaptor<com.toir.entity.maintenance.WorkOrder> captor = ArgumentCaptor.forClass(com.toir.entity.maintenance.WorkOrder.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getWorkType()).isEqualTo(WorkType.REPAIR);
        assertThat(captor.getValue().getRepairRequestId()).isNull();
        assertThat(captor.getValue().getDefectId()).isNull();
        assertThat(result.workType()).isEqualTo(WorkType.REPAIR);
        assertThat(result.repairRequestId()).isNull();
        assertThat(result.defectId()).isNull();
    }

    @Test
    void createOldStyleWorkOrderWithoutWorkTypeAndWithoutReplacementFieldsShouldSucceed() {

        when(repository.save(any(WorkOrder.class)))
                .thenAnswer(invocation -> {
                    WorkOrder workOrder = invocation.getArgument(0);
                    ReflectionTestUtils.setField(workOrder, "id", UUID.randomUUID());
                    return workOrder;
                });

        WorkOrderRequest request = request(WorkOrderType.PLANNED, null, null, null);

        mockSuccessfulCreateDependencies(request);

        WorkOrderDto result = service.create(request);

        assertThat(result.warehouseId()).isNull();
        assertThat(result.replacementEquipmentId()).isNull();
        assertThat(result.workType()).isEqualTo(WorkType.REPAIR);
    }

    @Test
    void createWithoutRepairRequestAndDefectKeepsExistingBehavior() {
        when(repository.save(any(WorkOrder.class)))
                .thenAnswer(invocation -> {
                    WorkOrder workOrder = invocation.getArgument(0);
                    ReflectionTestUtils.setField(workOrder, "id", UUID.randomUUID());
                    return workOrder;
                });
        WorkOrderRequest request = requestWithLinks(null, null);
        mockSuccessfulCreateDependencies(request);

        WorkOrderDto result = service.create(request);

        assertThat(result.repairRequestId()).isNull();
        assertThat(result.defectId()).isNull();
        verifyNoInteractions(repairRequestRepository, defectRepository);
    }

    @Test
    void createEmergencyWorkOrderWithoutRepairRequestReturns400() {
        WorkOrderRequest request = request(WorkOrderType.EMERGENCY, WorkType.REPAIR, null, null);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("repairRequestId is required");
                });

        verify(repository, never()).save(any(WorkOrder.class));
    }

    @Test
    void createDefectWorkOrderWithoutDefectReturns400() {
        WorkOrderRequest request = request(WorkOrderType.DEFECT, WorkType.REPAIR, null, null);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("defectId is required");
                });

        verify(repository, never()).save(any(WorkOrder.class));
    }

    @Test
    void createWithValidRepairRequestSucceeds() {
        when(repository.save(any(WorkOrder.class)))
                .thenAnswer(invocation -> {
                    WorkOrder workOrder = invocation.getArgument(0);
                    ReflectionTestUtils.setField(workOrder, "id", UUID.randomUUID());
                    return workOrder;
                });
        UUID repairRequestId = UUID.randomUUID();
        WorkOrderRequest request = requestWithLinks(repairRequestId, null);
        mockSuccessfulCreateDependencies(request);
        when(repairRequestRepository.findByIdAndIsDeletedFalse(repairRequestId))
                .thenReturn(Optional.of(repairRequest(repairRequestId, RequestStatus.OPEN)));

        WorkOrderDto result = service.create(request);

        assertThat(result.repairRequestId()).isEqualTo(repairRequestId);
        assertThat(result.repairRequest()).isNotNull();
        assertThat(result.repairRequest().id()).isEqualTo(repairRequestId);
        verify(repairRequestRepository, atLeastOnce()).findByIdAndIsDeletedFalse(repairRequestId);
    }

    @Test
    void createWithUnknownRepairRequestReturns404() {
        UUID repairRequestId = UUID.randomUUID();
        WorkOrderRequest request = requestWithLinks(repairRequestId, null);
        when(repository.existsByNumberAndIsDeletedFalse(request.number())).thenReturn(false);
        when(repairRequestRepository.findByIdAndIsDeletedFalse(repairRequestId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(ex.getMessage()).contains("Repair request not found");
                });
    }

    @Test
    void createWithRejectedRepairRequestReturns400() {
        UUID repairRequestId = UUID.randomUUID();
        WorkOrderRequest request = requestWithLinks(repairRequestId, null);
        when(repository.existsByNumberAndIsDeletedFalse(request.number())).thenReturn(false);
        when(repairRequestRepository.findByIdAndIsDeletedFalse(repairRequestId))
                .thenReturn(Optional.of(repairRequest(repairRequestId, RequestStatus.REJECTED)));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("Cannot create work order for repair request");
                });
    }

    @Test
    void createWithValidDefectSucceeds() {
        when(repository.save(any(WorkOrder.class)))
                .thenAnswer(invocation -> {
                    WorkOrder workOrder = invocation.getArgument(0);
                    ReflectionTestUtils.setField(workOrder, "id", UUID.randomUUID());
                    return workOrder;
                });
        UUID defectId = UUID.randomUUID();
        WorkOrderRequest request = requestWithLinks(null, defectId);
        mockSuccessfulCreateDependencies(request);
        when(defectRepository.findByIdAndIsDeletedFalse(defectId))
                .thenReturn(Optional.of(defect(defectId, null)));

        WorkOrderDto result = service.create(request);

        assertThat(result.defectId()).isEqualTo(defectId);
        assertThat(result.repairRequestId()).isNull();
        assertThat(result.defect()).isNotNull();
        assertThat(result.defect().id()).isEqualTo(defectId);
        verify(defectRepository, atLeastOnce()).findByIdAndIsDeletedFalse(defectId);
    }

    @Test
    void createWorkOrder_withEquipmentNode_setsNodeTarget() {
        UUID equipmentId = UUID.randomUUID();
        UUID nodeId = UUID.randomUUID();
        WorkOrderRequest request = requestWithNode(equipmentId, nodeId);
        when(repository.save(any(WorkOrder.class)))
                .thenAnswer(invocation -> {
                    WorkOrder workOrder = invocation.getArgument(0);
                    ReflectionTestUtils.setField(workOrder, "id", UUID.randomUUID());
                    return workOrder;
                });
        mockSuccessfulCreateDependencies(request);
        when(equipmentNodeRepository.findByIdAndIsDeletedFalse(nodeId))
                .thenReturn(Optional.of(equipmentNode(nodeId, equipmentId, "BRG-01", "Bearing")));

        WorkOrderDto result = service.create(request);

        ArgumentCaptor<WorkOrder> captor = ArgumentCaptor.forClass(WorkOrder.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getEquipmentNodeId()).isEqualTo(nodeId);
        assertThat(result.equipmentNodeId()).isEqualTo(nodeId);
        assertThat(result.equipmentNodeCode()).isEqualTo("BRG-01");
        assertThat(result.equipmentNodeName()).isEqualTo("Bearing");
        assertThat(result.equipmentNodeType()).isEqualTo(EquipmentNodeType.COMPONENT);
    }

    @Test
    void createWorkOrder_withNodeFromDifferentEquipment_returnsBadRequest() {
        UUID equipmentId = UUID.randomUUID();
        UUID nodeId = UUID.randomUUID();
        WorkOrderRequest request = requestWithNode(equipmentId, nodeId);
        when(repository.existsByNumberAndIsDeletedFalse(request.number())).thenReturn(false);
        when(equipmentNodeRepository.findByIdAndIsDeletedFalse(nodeId))
                .thenReturn(Optional.of(equipmentNode(nodeId, UUID.randomUUID(), "BRG-01", "Bearing")));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("different equipment");
                });
        verify(repository, never()).save(any(WorkOrder.class));
    }

    @Test
    void createWorkOrder_withoutNode_stillWorks() {
        WorkOrderRequest request = request(WorkOrderType.PLANNED, WorkType.REPAIR, null, null);
        when(repository.save(any(WorkOrder.class)))
                .thenAnswer(invocation -> {
                    WorkOrder workOrder = invocation.getArgument(0);
                    ReflectionTestUtils.setField(workOrder, "id", UUID.randomUUID());
                    return workOrder;
                });
        mockSuccessfulCreateDependencies(request);

        WorkOrderDto result = service.create(request);

        assertThat(result.equipmentNodeId()).isNull();
        verifyNoInteractions(equipmentNodeRepository);
    }

    @Test
    void createWorkOrder_fromDefectWithNode_inheritsNodeIfImplemented() {
        UUID equipmentId = UUID.randomUUID();
        UUID defectId = UUID.randomUUID();
        UUID nodeId = UUID.randomUUID();
        WorkOrderRequest request = requestWithLinks(null, defectId, equipmentId);
        Defect defect = defect(defectId, null, equipmentId);
        defect.setEquipmentNodeId(nodeId);
        when(repository.save(any(WorkOrder.class)))
                .thenAnswer(invocation -> {
                    WorkOrder workOrder = invocation.getArgument(0);
                    ReflectionTestUtils.setField(workOrder, "id", UUID.randomUUID());
                    return workOrder;
                });
        mockSuccessfulCreateDependencies(request);
        when(defectRepository.findByIdAndIsDeletedFalse(defectId)).thenReturn(Optional.of(defect));
        when(equipmentNodeRepository.findByIdAndIsDeletedFalse(nodeId))
                .thenReturn(Optional.of(equipmentNode(nodeId, equipmentId, "BRG-01", "Bearing")));

        WorkOrderDto result = service.create(request);

        assertThat(result.equipmentNodeId()).isEqualTo(nodeId);
        assertThat(result.equipmentNodeCode()).isEqualTo("BRG-01");
    }

    @Test
    void createWithUnknownDefectReturns404() {
        UUID defectId = UUID.randomUUID();
        WorkOrderRequest request = requestWithLinks(null, defectId);
        when(repository.existsByNumberAndIsDeletedFalse(request.number())).thenReturn(false);
        when(defectRepository.findByIdAndIsDeletedFalse(defectId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(ex.getMessage()).contains("Defect not found");
                });
    }

    @Test
    void createWithPprTaskFromDraftPlanReturns400() {
        UUID taskId = UUID.randomUUID();
        WorkOrderRequest request = requestWithPprTask(taskId);
        PprPlan plan = pprPlan(UUID.randomUUID(), PlanStatus.DRAFT);
        PprTask task = pprTask(taskId, plan, PprTaskStatus.APPROVED);
        task.setEquipmentId(request.equipmentId());

        when(repository.existsByNumberAndIsDeletedFalse(request.number())).thenReturn(false);
        when(pprTaskRepository.findByIdAndIsDeletedFalseWithPlan(taskId)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("parent PPR plan is approved");
                });

        verify(repository, never()).save(any(WorkOrder.class));
    }

    @Test
    void createWithPlannedPprTaskReturns400() {
        UUID taskId = UUID.randomUUID();
        WorkOrderRequest request = requestWithPprTask(taskId);
        PprPlan plan = pprPlan(UUID.randomUUID(), PlanStatus.APPROVED);
        PprTask task = pprTask(taskId, plan, PprTaskStatus.PLANNED);
        task.setEquipmentId(request.equipmentId());

        when(repository.existsByNumberAndIsDeletedFalse(request.number())).thenReturn(false);
        when(pprTaskRepository.findByIdAndIsDeletedFalseWithPlan(taskId)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("Only APPROVED PPR tasks can generate work orders");
                });

        verify(repository, never()).save(any(WorkOrder.class));
    }

    @Test
    void createWithApprovedPprPlanAndTaskSucceeds() {
        UUID taskId = UUID.randomUUID();
        WorkOrderRequest request = requestWithPprTask(taskId);
        PprPlan plan = pprPlan(UUID.randomUUID(), PlanStatus.APPROVED);
        PprTask task = pprTask(taskId, plan, PprTaskStatus.APPROVED);
        task.setEquipmentId(request.equipmentId());

        when(repository.save(any(WorkOrder.class)))
                .thenAnswer(invocation -> {
                    WorkOrder workOrder = invocation.getArgument(0);
                    ReflectionTestUtils.setField(workOrder, "id", UUID.randomUUID());
                    return workOrder;
                });
        mockSuccessfulCreateDependencies(request);
        when(pprTaskRepository.findByIdAndIsDeletedFalseWithPlan(taskId)).thenReturn(Optional.of(task));

        WorkOrderDto result = service.create(request);

        assertThat(result.pprTaskId()).isEqualTo(taskId);
    }

    @Test
    void createWithPprTaskForDifferentEquipmentReturns400() {
        UUID taskId = UUID.randomUUID();
        WorkOrderRequest request = requestWithPprTask(taskId);
        PprPlan plan = pprPlan(UUID.randomUUID(), PlanStatus.APPROVED);
        PprTask task = pprTask(taskId, plan, PprTaskStatus.APPROVED);
        task.setEquipmentId(UUID.randomUUID());

        when(repository.existsByNumberAndIsDeletedFalse(request.number())).thenReturn(false);
        when(pprTaskRepository.findByIdAndIsDeletedFalseWithPlan(taskId)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("PPR task belongs to a different equipment");
                });

        verify(repository, never()).save(any(WorkOrder.class));
    }

    @Test
    void createWithRepairRequestAndMatchingDefectSucceeds() {
        when(repository.save(any(WorkOrder.class)))
                .thenAnswer(invocation -> {
                    WorkOrder workOrder = invocation.getArgument(0);
                    ReflectionTestUtils.setField(workOrder, "id", UUID.randomUUID());
                    return workOrder;
                });
        UUID repairRequestId = UUID.randomUUID();
        UUID defectId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        WorkOrderRequest request = requestWithLinks(repairRequestId, defectId, equipmentId);
        mockSuccessfulCreateDependencies(request);
        when(repairRequestRepository.findByIdAndIsDeletedFalse(repairRequestId))
                .thenReturn(Optional.of(repairRequest(repairRequestId, RequestStatus.OPEN, equipmentId)));
        when(defectRepository.findByIdAndIsDeletedFalse(defectId))
                .thenReturn(Optional.of(defect(defectId, repairRequestId, equipmentId)));

        WorkOrderDto result = service.create(request);

        assertThat(result.repairRequestId()).isEqualTo(repairRequestId);
        assertThat(result.defectId()).isEqualTo(defectId);
    }

    @Test
    void createWithRepairRequestAndDifferentDefectRequestReturns400() {
        UUID repairRequestId = UUID.randomUUID();
        UUID otherRepairRequestId = UUID.randomUUID();
        UUID defectId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        WorkOrderRequest request = requestWithLinks(repairRequestId, defectId, equipmentId);
        when(repository.existsByNumberAndIsDeletedFalse(request.number())).thenReturn(false);
        when(repairRequestRepository.findByIdAndIsDeletedFalse(repairRequestId))
                .thenReturn(Optional.of(repairRequest(repairRequestId, RequestStatus.OPEN, equipmentId)));
        when(defectRepository.findByIdAndIsDeletedFalse(defectId))
                .thenReturn(Optional.of(defect(defectId, otherRepairRequestId, equipmentId)));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("belongs to a different repair request");
                });
    }

    @Test
    void createWithDefectFromDifferentEquipmentReturns400() {
        UUID repairRequestId = UUID.randomUUID();
        UUID defectId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        UUID otherEquipmentId = UUID.randomUUID();
        WorkOrderRequest request = requestWithLinks(repairRequestId, defectId, equipmentId);
        when(repository.existsByNumberAndIsDeletedFalse(request.number())).thenReturn(false);
        when(repairRequestRepository.findByIdAndIsDeletedFalse(repairRequestId))
                .thenReturn(Optional.of(repairRequest(repairRequestId, RequestStatus.OPEN, equipmentId)));
        when(defectRepository.findByIdAndIsDeletedFalse(defectId))
                .thenReturn(Optional.of(defect(defectId, repairRequestId, otherEquipmentId)));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("belongs to a different equipment");
                });
    }

    @Test
    void createWithRepairRequestFromDifferentEquipmentReturns400() {
        UUID repairRequestId = UUID.randomUUID();
        UUID defectId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        UUID otherEquipmentId = UUID.randomUUID();
        WorkOrderRequest request = requestWithLinks(repairRequestId, defectId, equipmentId);
        when(repository.existsByNumberAndIsDeletedFalse(request.number())).thenReturn(false);
        when(repairRequestRepository.findByIdAndIsDeletedFalse(repairRequestId))
                .thenReturn(Optional.of(repairRequest(repairRequestId, RequestStatus.OPEN, otherEquipmentId)));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("Repair request belongs to a different equipment");
                });
    }

    @Test
    void createWithDefectLinkedToRepairRequestRequiresRepairRequestId() {
        UUID repairRequestId = UUID.randomUUID();
        UUID defectId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        WorkOrderRequest request = requestWithLinks(null, defectId, equipmentId);
        when(repository.existsByNumberAndIsDeletedFalse(request.number())).thenReturn(false);
        when(defectRepository.findByIdAndIsDeletedFalse(defectId))
                .thenReturn(Optional.of(defect(defectId, repairRequestId, equipmentId)));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("repairRequestId is required");
                });
    }

    @Test
    void responseIncludesRepairRequestObject() {
        UUID workOrderId = UUID.randomUUID();
        UUID repairRequestId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.DRAFT, null, null);
        workOrder.setRepairRequestId(repairRequestId);
        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(repairRequestRepository.findByIdAndIsDeletedFalse(repairRequestId))
                .thenReturn(Optional.of(repairRequest(repairRequestId, RequestStatus.OPEN)));
        stubLifecycleDtoLookups(workOrder);

        WorkOrderDto response = service.findById(workOrderId);

        assertThat(response.repairRequest()).isNotNull();
        assertThat(response.repairRequest().id()).isEqualTo(repairRequestId);
        assertThat(response.repairRequest().number()).isEqualTo("RR-2026-1001");
    }

    @Test
    void responseIncludesDefectObject() {
        UUID workOrderId = UUID.randomUUID();
        UUID defectId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.DRAFT, null, null);
        workOrder.setDefectId(defectId);
        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(defectRepository.findByIdAndIsDeletedFalse(defectId))
                .thenReturn(Optional.of(defect(defectId, null)));
        stubLifecycleDtoLookups(workOrder);

        WorkOrderDto response = service.findById(workOrderId);

        assertThat(response.defect()).isNotNull();
        assertThat(response.defect().id()).isEqualTo(defectId);
        assertThat(response.defect().code()).isEqualTo("DEF-2026-1001");
    }

    @Test
    void responseWithoutLinksReturnsNullObjects() {
        UUID workOrderId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.DRAFT, null, null);
        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        stubLifecycleDtoLookups(workOrder);

        WorkOrderDto response = service.findById(workOrderId);

        assertThat(response.repairRequest()).isNull();
        assertThat(response.defect()).isNull();
    }

    @Test
    void detailIncludesOperationsAndMaterialsCounts() {
        UUID workOrderId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.DRAFT, null, null);
        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(workExecutionRepository.countByWorkOrderIds(List.of(workOrderId)))
                .thenReturn(List.of(countProjection(workOrderId, 2)));
        when(repairMaterialUsageRepository.countByWorkOrderIds(List.of(workOrderId)))
                .thenReturn(List.of(countProjection(workOrderId, 4)));
        stubLifecycleDtoLookups(workOrder);

        WorkOrderDto response = service.findById(workOrderId);

        assertThat(response.operationsCount()).isEqualTo(2);
        assertThat(response.materialsCount()).isEqualTo(4);
    }

    @Test
    void detailIncludesMaterialUsageLines() {
        UUID workOrderId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.DRAFT, null, null);
        RepairMaterialUsageDto usage = new RepairMaterialUsageDto(
                UUID.randomUUID(),
                workOrderId,
                "WO-100",
                "Repair pump",
                warehouseId,
                "Main warehouse",
                sparePartId,
                "Bearing",
                "BRG-1",
                null,
                2,
                15.0,
                30.0,
                java.time.Instant.parse("2026-06-04T09:00:00Z"),
                UUID.randomUUID(),
                "Technician",
                UUID.randomUUID(),
                "Installed"
        );

        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(workExecutionRepository.countByWorkOrderIds(List.of(workOrderId))).thenReturn(List.of());
        when(repairMaterialUsageRepository.countByWorkOrderIds(List.of(workOrderId))).thenReturn(List.of(countProjection(workOrderId, 1)));
        when(repairMaterialUsageService.findByWorkOrder(workOrderId)).thenReturn(List.of(usage));
        stubLifecycleDtoLookups(workOrder);

        WorkOrderDto response = service.findById(workOrderId);

        assertThat(response.materialsCount()).isEqualTo(1);
        assertThat(response.materialUsages()).containsExactly(usage);
    }

    @Test
    void detailIncludesUpdatedAt() {
        UUID workOrderId = UUID.randomUUID();
        java.time.Instant updatedAt = java.time.Instant.parse("2026-05-31T12:00:00Z");
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.DRAFT, null, null);
        workOrder.setUpdatedAt(updatedAt);
        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(workExecutionRepository.countByWorkOrderIds(List.of(workOrderId))).thenReturn(List.of());
        when(repairMaterialUsageRepository.countByWorkOrderIds(List.of(workOrderId))).thenReturn(List.of());
        stubLifecycleDtoLookups(workOrder);

        WorkOrderDto response = service.findById(workOrderId);

        assertThat(response.updatedAt()).isEqualTo(updatedAt);
    }

    @Test
    void listBatchEnrichmentDoesNotNPlusOne() {
        UUID repairRequestId = UUID.randomUUID();
        UUID defectId = UUID.randomUUID();
        WorkOrder first = lifecycleWorkOrder(UUID.randomUUID(), WorkType.REPAIR, WorkOrderStatus.APPROVED, null, null);
        first.setRepairRequestId(repairRequestId);
        first.setDefectId(defectId);
        WorkOrder second = lifecycleWorkOrder(UUID.randomUUID(), WorkType.REPAIR, WorkOrderStatus.IN_PROGRESS, null, null);
        second.setRepairRequestId(repairRequestId);
        second.setDefectId(defectId);

        when(repository.search(null, null, null)).thenReturn(java.util.List.of(first, second));
        when(workExecutionRepository.countByWorkOrderIds(List.of(first.getId(), second.getId())))
                .thenReturn(List.of());
        when(repairMaterialUsageRepository.countByWorkOrderIds(List.of(first.getId(), second.getId())))
                .thenReturn(List.of());
        when(repairRequestRepository.findAllByIdInAndIsDeletedFalse(java.util.List.of(repairRequestId)))
                .thenReturn(java.util.List.of(repairRequest(repairRequestId, RequestStatus.OPEN)));
        when(defectRepository.findAllByIdInAndIsDeletedFalse(java.util.List.of(defectId)))
                .thenReturn(java.util.List.of(defect(defectId, repairRequestId)));
        stubLifecycleDtoLookups(first);
        stubLifecycleDtoLookups(second);

        java.util.List<WorkOrderDto> results = service.search(null, null, null);

        assertThat(results).hasSize(2);
        assertThat(results).allSatisfy(dto -> {
            assertThat(dto.repairRequest()).isNotNull();
            assertThat(dto.defect()).isNotNull();
        });
        verify(repairRequestRepository).findAllByIdInAndIsDeletedFalse(java.util.List.of(repairRequestId));
        verify(defectRepository).findAllByIdInAndIsDeletedFalse(java.util.List.of(defectId));
        verify(repairRequestRepository, never()).findByIdAndIsDeletedFalse(repairRequestId);
        verify(defectRepository, never()).findByIdAndIsDeletedFalse(defectId);
    }

    @Test
    void listIncludesOperationsAndMaterialsCounts() {
        WorkOrder workOrder = lifecycleWorkOrder(UUID.randomUUID(), WorkType.REPAIR, WorkOrderStatus.APPROVED, null, null);
        when(repository.searchPaginated(null, null, null, null, PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(List.of(workOrder), PageRequest.of(0, 10), 1));
        when(workExecutionRepository.countByWorkOrderIds(List.of(workOrder.getId())))
                .thenReturn(List.of(countProjection(workOrder.getId(), 3)));
        when(repairMaterialUsageRepository.countByWorkOrderIds(List.of(workOrder.getId())))
                .thenReturn(List.of(countProjection(workOrder.getId(), 5)));
        stubLifecycleDtoLookups(workOrder);

        org.springframework.data.domain.Page<WorkOrderDto> result =
                service.search(null, null, null, 0, 10, null);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().operationsCount()).isEqualTo(3);
        assertThat(result.getContent().getFirst().materialsCount()).isEqualTo(5);
    }

    @Test
    void listReturnsZeroCountsWhenNoOperationsOrMaterials() {
        WorkOrder workOrder = lifecycleWorkOrder(UUID.randomUUID(), WorkType.REPAIR, WorkOrderStatus.APPROVED, null, null);
        when(repository.searchPaginated(null, null, null, null, PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(List.of(workOrder), PageRequest.of(0, 10), 1));
        when(workExecutionRepository.countByWorkOrderIds(List.of(workOrder.getId())))
                .thenReturn(List.of());
        when(repairMaterialUsageRepository.countByWorkOrderIds(List.of(workOrder.getId())))
                .thenReturn(List.of());
        stubLifecycleDtoLookups(workOrder);

        org.springframework.data.domain.Page<WorkOrderDto> result =
                service.search(null, null, null, 0, 10, null);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().operationsCount()).isZero();
        assertThat(result.getContent().getFirst().materialsCount()).isZero();
    }

    @Test
    void listBatchLoadsOperationAndMaterialCountsOnce() {
        WorkOrder first = lifecycleWorkOrder(UUID.randomUUID(), WorkType.REPAIR, WorkOrderStatus.APPROVED, null, null);
        WorkOrder second = lifecycleWorkOrder(UUID.randomUUID(), WorkType.REPAIR, WorkOrderStatus.IN_PROGRESS, null, null);
        when(repository.search(null, null, null)).thenReturn(List.of(first, second));
        when(workExecutionRepository.countByWorkOrderIds(List.of(first.getId(), second.getId())))
                .thenReturn(List.of());
        when(repairMaterialUsageRepository.countByWorkOrderIds(List.of(first.getId(), second.getId())))
                .thenReturn(List.of());
        stubLifecycleDtoLookups(first);
        stubLifecycleDtoLookups(second);

        List<WorkOrderDto> result = service.search(null, null, null);

        assertThat(result).hasSize(2);
        verify(workExecutionRepository).countByWorkOrderIds(List.of(first.getId(), second.getId()));
        verify(repairMaterialUsageRepository).countByWorkOrderIds(List.of(first.getId(), second.getId()));
    }

    @Test
    void listEnrichmentNullSafeForMissingLinks() {
        UUID repairRequestId = UUID.randomUUID();
        UUID defectId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(UUID.randomUUID(), WorkType.REPAIR, WorkOrderStatus.APPROVED, null, null);
        workOrder.setRepairRequestId(repairRequestId);
        workOrder.setDefectId(defectId);

        when(repository.searchPaginated(null, null, null, null, PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(java.util.List.of(workOrder), PageRequest.of(0, 10), 1));
        when(workExecutionRepository.countByWorkOrderIds(List.of(workOrder.getId())))
                .thenReturn(List.of());
        when(repairMaterialUsageRepository.countByWorkOrderIds(List.of(workOrder.getId())))
                .thenReturn(List.of());
        when(repairRequestRepository.findAllByIdInAndIsDeletedFalse(java.util.List.of(repairRequestId)))
                .thenReturn(java.util.List.of());
        when(defectRepository.findAllByIdInAndIsDeletedFalse(java.util.List.of(defectId)))
                .thenReturn(java.util.List.of());
        stubLifecycleDtoLookups(workOrder);

        org.springframework.data.domain.Page<WorkOrderDto> result =
                service.search(null, null, null, 0, 10, null);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent()).hasSize(1);
        WorkOrderDto dto = result.getContent().get(0);
        assertThat(dto.repairRequestId()).isEqualTo(repairRequestId);
        assertThat(dto.defectId()).isEqualTo(defectId);
        assertThat(dto.repairRequest()).isNull();
        assertThat(dto.defect()).isNull();
    }

    @Test
    void workOrderWithoutIdDefaultsCountsToZero() {
        WorkOrder workOrder = new WorkOrder();
        workOrder.setNumber("WO-NO-ID");
        workOrder.setTitle("No id");
        workOrder.setEquipmentId(UUID.randomUUID());
        workOrder.setDepartmentId(UUID.randomUUID());
        workOrder.setStatus(WorkOrderStatus.APPROVED);
        workOrder.setType(WorkOrderType.PLANNED);
        workOrder.setWorkType(WorkType.REPAIR);
        when(repository.search(null, null, null)).thenReturn(List.of(workOrder));
        stubLifecycleDtoLookups(workOrder);

        List<WorkOrderDto> result = service.search(null, null, null);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().operationsCount()).isZero();
        assertThat(result.getFirst().materialsCount()).isZero();
        verifyNoInteractions(workExecutionRepository, repairMaterialUsageRepository);
    }

    @Test
    void listBlankSearchDoesNotFail() {
        WorkOrder workOrder = lifecycleWorkOrder(UUID.randomUUID(), WorkType.REPAIR, WorkOrderStatus.APPROVED, null, null);
        when(repository.searchPaginated(eq(null), eq(null), eq(null), eq(null), eq(PageRequest.of(0, 10))))
                .thenReturn(new PageImpl<>(java.util.List.of(workOrder), PageRequest.of(0, 10), 1));
        when(workExecutionRepository.countByWorkOrderIds(List.of(workOrder.getId())))
                .thenReturn(List.of());
        when(repairMaterialUsageRepository.countByWorkOrderIds(List.of(workOrder.getId())))
                .thenReturn(List.of());
        stubLifecycleDtoLookups(workOrder);

        org.springframework.data.domain.Page<WorkOrderDto> result =
                service.search(null, null, null, 0, 10, "   ");

        assertThat(result.getContent()).hasSize(1);
        verify(repository).searchPaginated(eq(null), eq(null), eq(null), eq(null), eq(PageRequest.of(0, 10)));
    }

    @Test
    void createReplacementWorkOrderWithoutWarehouseIdShouldFail() {
        WorkOrderRequest request = request(WorkOrderType.PLANNED, WorkType.REPLACEMENT, null, UUID.randomUUID());

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("warehouseId is required");
    }

    @Test
    void createReplacementWorkOrderWithoutReplacementEquipmentIdShouldFail() {
        WorkOrderRequest request = request(WorkOrderType.PLANNED, WorkType.REPLACEMENT, UUID.randomUUID(), null);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("replacementEquipmentId is required");
    }

    @Test
    void createReplacementWorkOrderWithEquipmentNotBelongingToWarehouseShouldFail() {
        UUID warehouseId = UUID.randomUUID();
        UUID replacementEquipmentId = UUID.randomUUID();
        WorkOrderRequest request = request(WorkOrderType.PLANNED, WorkType.REPLACEMENT, warehouseId, replacementEquipmentId);

        when(warehouseRepository.findByIdAndIsDeletedFalse(warehouseId)).thenReturn(Optional.of(new Warehouse()));
        when(equipmentRepository.findByIdAndIsDeletedFalse(replacementEquipmentId)).thenReturn(Optional.of(new Equipment()));
        when(warehouseEquipmentItemRepository.findByWarehouseIdAndEquipmentIdAndActiveTrueAndIsDeletedFalse(warehouseId, replacementEquipmentId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("does not belong to selected warehouse");
    }

    @Test
    void createReplacementWorkOrderWithNonAvailableEquipmentShouldFail() {
        UUID warehouseId = UUID.randomUUID();
        UUID replacementEquipmentId = UUID.randomUUID();
        WorkOrderRequest request = request(WorkOrderType.PLANNED, WorkType.REPLACEMENT, warehouseId, replacementEquipmentId);

        WarehouseEquipmentItem item = new WarehouseEquipmentItem();
        item.setWarehouseId(warehouseId);
        item.setEquipmentId(replacementEquipmentId);
        item.setActive(true);
        item.setStatus(WarehouseEquipmentStatus.RESERVED);

        when(warehouseRepository.findByIdAndIsDeletedFalse(warehouseId)).thenReturn(Optional.of(new Warehouse()));
        when(equipmentRepository.findByIdAndIsDeletedFalse(replacementEquipmentId)).thenReturn(Optional.of(new Equipment()));
        when(warehouseEquipmentItemRepository.findByWarehouseIdAndEquipmentIdAndActiveTrueAndIsDeletedFalse(warehouseId, replacementEquipmentId))
                .thenReturn(Optional.of(item));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("must be AVAILABLE");
    }

    @Test
    void createReplacementWorkOrderWithAlreadyAssignedReplacementEquipmentShouldFail() {
        UUID warehouseId = UUID.randomUUID();
        UUID replacementEquipmentId = UUID.randomUUID();
        WorkOrderRequest request = request(WorkOrderType.PLANNED, WorkType.REPLACEMENT, warehouseId, replacementEquipmentId);

        WarehouseEquipmentItem item = new WarehouseEquipmentItem();
        item.setWarehouseId(warehouseId);
        item.setEquipmentId(replacementEquipmentId);
        item.setActive(true);
        item.setStatus(WarehouseEquipmentStatus.AVAILABLE);

        when(warehouseRepository.findByIdAndIsDeletedFalse(warehouseId)).thenReturn(Optional.of(new Warehouse()));
        when(equipmentRepository.findByIdAndIsDeletedFalse(replacementEquipmentId)).thenReturn(Optional.of(new Equipment()));
        when(warehouseEquipmentItemRepository.findByWarehouseIdAndEquipmentIdAndActiveTrueAndIsDeletedFalse(warehouseId, replacementEquipmentId))
                .thenReturn(Optional.of(item));
        when(repository.existsActiveReplacementAssignment(eq(replacementEquipmentId), eq(WorkType.REPLACEMENT), any()))
                .thenReturn(true);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("already assigned to another active work order");
    }

    @Test
    void createValidReplacementWorkOrderShouldPersistSuccessfully() {
        when(repository.save(any(WorkOrder.class)))
                .thenAnswer(invocation -> {
                    WorkOrder workOrder = invocation.getArgument(0);
                    ReflectionTestUtils.setField(workOrder, "id", UUID.randomUUID());
                    return workOrder;
                });

        UUID warehouseId = UUID.randomUUID();
        UUID replacementEquipmentId = UUID.randomUUID();
        WorkOrderRequest request = request(WorkOrderType.PLANNED, WorkType.REPLACEMENT, warehouseId, replacementEquipmentId);

        WarehouseEquipmentItem item = new WarehouseEquipmentItem();
        item.setWarehouseId(warehouseId);
        item.setEquipmentId(replacementEquipmentId);
        item.setActive(true);
        item.setStatus(WarehouseEquipmentStatus.AVAILABLE);

        Equipment sourceEquipment = new Equipment();
        sourceEquipment.setId(request.equipmentId());
        sourceEquipment.setName("Source Equipment");

        Equipment replacementEquipment = new Equipment();
        replacementEquipment.setId(replacementEquipmentId);
        replacementEquipment.setName("Replacement Equipment");

        Department department = new Department();
        department.setId(request.departmentId());
        department.setName("Maintenance");

        when(warehouseRepository.findByIdAndIsDeletedFalse(warehouseId)).thenReturn(Optional.of(new Warehouse()));
        when(equipmentRepository.findByIdAndIsDeletedFalse(replacementEquipmentId)).thenReturn(Optional.of(replacementEquipment));
        when(warehouseEquipmentItemRepository.findByWarehouseIdAndEquipmentIdAndActiveTrueAndIsDeletedFalse(warehouseId, replacementEquipmentId))
                .thenReturn(Optional.of(item));
        when(warehouseEquipmentItemRepository.save(any(WarehouseEquipmentItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.existsActiveReplacementAssignment(eq(replacementEquipmentId), eq(WorkType.REPLACEMENT), any()))
                .thenReturn(false);
        when(repository.existsByNumberAndIsDeletedFalse(request.number())).thenReturn(false);
        when(equipmentRepository.findById(request.equipmentId())).thenReturn(Optional.of(sourceEquipment));
        when(equipmentRepository.findById(replacementEquipmentId)).thenReturn(Optional.of(replacementEquipment));
        when(departmentRepository.findById(request.departmentId())).thenReturn(Optional.of(department));

        WorkOrderDto result = service.create(request);

        ArgumentCaptor<com.toir.entity.maintenance.WorkOrder> captor = ArgumentCaptor.forClass(com.toir.entity.maintenance.WorkOrder.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getWarehouseId()).isEqualTo(warehouseId);
        assertThat(captor.getValue().getReplacementEquipmentId()).isEqualTo(replacementEquipmentId);
        assertThat(result.warehouseId()).isEqualTo(warehouseId);
        assertThat(result.replacementEquipmentId()).isEqualTo(replacementEquipmentId);
        assertThat(result.replacementEquipmentName()).isEqualTo("Replacement Equipment");
        assertThat(item.getStatus()).isEqualTo(WarehouseEquipmentStatus.RESERVED);
        verify(warehouseEquipmentItemRepository).save(item);
    }

    @Test
    void createNonReplacementWorkOrderWithReplacementFieldsShouldFail() {
        WorkOrderRequest request = request(WorkOrderType.PLANNED, WorkType.REPAIR, UUID.randomUUID(), UUID.randomUUID());

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("must be null when workType is not REPLACEMENT");
    }

    @Test
    void createWorkOrderWithNullWorkTypeAndReplacementFieldsShouldFail() {
        WorkOrderRequest request = request(WorkOrderType.PLANNED, null, UUID.randomUUID(), UUID.randomUUID());

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("must be null when workType is not REPLACEMENT");
    }

    @Test
    void approveReplacementWorkOrderShouldReserveReplacementEquipment() {
        UUID workOrderId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID replacementEquipmentId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPLACEMENT, WorkOrderStatus.DRAFT, warehouseId, replacementEquipmentId);
        WarehouseEquipmentItem item = warehouseItem(warehouseId, replacementEquipmentId, WarehouseEquipmentStatus.AVAILABLE);

        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(warehouseEquipmentItemRepository.findByWarehouseIdAndEquipmentIdAndActiveTrueAndIsDeletedFalse(warehouseId, replacementEquipmentId))
                .thenReturn(Optional.of(item));
        when(warehouseEquipmentItemRepository.save(any(WarehouseEquipmentItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.save(any(WorkOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        stubLifecycleDtoLookups(workOrder);

        service.approve(workOrderId, UUID.randomUUID());

        assertThat(item.getStatus()).isEqualTo(WarehouseEquipmentStatus.RESERVED);
    }

    @Test
    void approveReplacementWorkOrderWithReservedItemShouldSucceedWithoutStatusChange() {
        UUID workOrderId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID replacementEquipmentId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPLACEMENT, WorkOrderStatus.DRAFT, warehouseId, replacementEquipmentId);
        WarehouseEquipmentItem item = warehouseItem(warehouseId, replacementEquipmentId, WarehouseEquipmentStatus.RESERVED);

        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(warehouseEquipmentItemRepository.findByWarehouseIdAndEquipmentIdAndActiveTrueAndIsDeletedFalse(warehouseId, replacementEquipmentId))
                .thenReturn(Optional.of(item));
        when(repository.save(any(WorkOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        stubLifecycleDtoLookups(workOrder);

        service.approve(workOrderId, UUID.randomUUID());

        assertThat(item.getStatus()).isEqualTo(WarehouseEquipmentStatus.RESERVED);
        verify(warehouseEquipmentItemRepository, never()).save(any(WarehouseEquipmentItem.class));
    }

    @Test
    void approveReplacementWorkOrderWithInstalledItemShouldFail() {
        UUID workOrderId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID replacementEquipmentId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPLACEMENT, WorkOrderStatus.DRAFT, warehouseId, replacementEquipmentId);
        WarehouseEquipmentItem item = warehouseItem(warehouseId, replacementEquipmentId, WarehouseEquipmentStatus.INSTALLED);

        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(warehouseEquipmentItemRepository.findByWarehouseIdAndEquipmentIdAndActiveTrueAndIsDeletedFalse(warehouseId, replacementEquipmentId))
                .thenReturn(Optional.of(item));

        assertThatThrownBy(() -> service.approve(workOrderId, UUID.randomUUID()))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("must be AVAILABLE or RESERVED");
    }

    @Test
    void approveReplacementWorkOrderWithOutOfServiceItemShouldFail() {
        UUID workOrderId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID replacementEquipmentId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPLACEMENT, WorkOrderStatus.DRAFT, warehouseId, replacementEquipmentId);
        WarehouseEquipmentItem item = warehouseItem(warehouseId, replacementEquipmentId, WarehouseEquipmentStatus.OUT_OF_SERVICE);

        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(warehouseEquipmentItemRepository.findByWarehouseIdAndEquipmentIdAndActiveTrueAndIsDeletedFalse(warehouseId, replacementEquipmentId))
                .thenReturn(Optional.of(item));

        assertThatThrownBy(() -> service.approve(workOrderId, UUID.randomUUID()))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("must be AVAILABLE or RESERVED");
    }

    @Test
    void startApprovedReplacementWorkOrderShouldReserveReplacementEquipment() {
        UUID workOrderId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID replacementEquipmentId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPLACEMENT, WorkOrderStatus.APPROVED, warehouseId, replacementEquipmentId);
        WarehouseEquipmentItem item = warehouseItem(warehouseId, replacementEquipmentId, WarehouseEquipmentStatus.AVAILABLE);

        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(warehouseEquipmentItemRepository.findByWarehouseIdAndEquipmentIdAndActiveTrueAndIsDeletedFalse(warehouseId, replacementEquipmentId))
                .thenReturn(Optional.of(item));
        when(warehouseEquipmentItemRepository.save(any(WarehouseEquipmentItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.save(any(WorkOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        stubLifecycleDtoLookups(workOrder);

        service.start(workOrderId);

        assertThat(item.getStatus()).isEqualTo(WarehouseEquipmentStatus.RESERVED);
    }

    @Test
    void startWithLinkedRepairRequestMovesRequestToInProgress() {
        UUID workOrderId = UUID.randomUUID();
        UUID repairRequestId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.APPROVED, null, null);
        workOrder.setRepairRequestId(repairRequestId);
        RepairRequest repairRequest = repairRequest(repairRequestId, RequestStatus.OPEN);

        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(repository.save(any(WorkOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(repairRequestRepository.findByIdAndIsDeletedFalse(repairRequestId)).thenReturn(Optional.of(repairRequest));
        when(repairRequestRepository.save(any(RepairRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        stubLifecycleDtoLookups(workOrder);

        WorkOrderDto response = service.start(workOrderId);

        assertThat(response.status()).isEqualTo(WorkOrderStatus.IN_PROGRESS);
        assertThat(repairRequest.getStatus()).isEqualTo(RequestStatus.IN_PROGRESS);
        assertThat(response.repairRequest()).isNotNull();
        assertThat(response.repairRequest().status()).isEqualTo(RequestStatus.IN_PROGRESS);
        verify(repairRequestRepository).save(repairRequest);
    }

    @Test
    void startWithLinkedDefectMovesDefectToInProgress() {
        UUID workOrderId = UUID.randomUUID();
        UUID defectId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.APPROVED, null, null);
        workOrder.setDefectId(defectId);
        Defect defect = defect(defectId, null, DefectStatus.OPEN);

        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(repository.save(any(WorkOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(defectRepository.findByIdAndIsDeletedFalse(defectId)).thenReturn(Optional.of(defect));
        when(defectRepository.save(any(Defect.class))).thenAnswer(invocation -> invocation.getArgument(0));
        stubLifecycleDtoLookups(workOrder);

        WorkOrderDto response = service.start(workOrderId);

        assertThat(response.status()).isEqualTo(WorkOrderStatus.IN_PROGRESS);
        assertThat(defect.getStatus()).isEqualTo(DefectStatus.IN_PROGRESS);
        assertThat(response.defect()).isNotNull();
        assertThat(response.defect().status()).isEqualTo(DefectStatus.IN_PROGRESS);
        verify(defectRepository).save(defect);
    }

    @Test
    void startDoesNotReopenClosedRepairRequest() {
        UUID workOrderId = UUID.randomUUID();
        UUID repairRequestId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.APPROVED, null, null);
        workOrder.setRepairRequestId(repairRequestId);
        RepairRequest repairRequest = repairRequest(repairRequestId, RequestStatus.CLOSED);

        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(repository.save(any(WorkOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(repairRequestRepository.findByIdAndIsDeletedFalse(repairRequestId)).thenReturn(Optional.of(repairRequest));
        stubLifecycleDtoLookups(workOrder);

        WorkOrderDto response = service.start(workOrderId);

        assertThat(repairRequest.getStatus()).isEqualTo(RequestStatus.CLOSED);
        assertThat(response.repairRequest()).isNotNull();
        assertThat(response.repairRequest().status()).isEqualTo(RequestStatus.CLOSED);
        verify(repairRequestRepository, never()).save(any(RepairRequest.class));
    }

    @Test
    void startDoesNotReopenClosedDefect() {
        UUID workOrderId = UUID.randomUUID();
        UUID defectId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.APPROVED, null, null);
        workOrder.setDefectId(defectId);
        Defect defect = defect(defectId, null, DefectStatus.CLOSED);

        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(repository.save(any(WorkOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(defectRepository.findByIdAndIsDeletedFalse(defectId)).thenReturn(Optional.of(defect));
        stubLifecycleDtoLookups(workOrder);

        WorkOrderDto response = service.start(workOrderId);

        assertThat(defect.getStatus()).isEqualTo(DefectStatus.CLOSED);
        assertThat(response.defect()).isNotNull();
        assertThat(response.defect().status()).isEqualTo(DefectStatus.CLOSED);
        verify(defectRepository, never()).save(any(Defect.class));
    }

    @Test
    void startFromDraftShouldFail() {
        UUID workOrderId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.DRAFT, null, null);
        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));

        assertThatThrownBy(() -> service.start(workOrderId))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Only approved work orders can be started");
    }

    @Test
    void startFromClosedShouldFail() {
        UUID workOrderId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.CLOSED, null, null);
        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));

        assertThatThrownBy(() -> service.start(workOrderId))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Only approved work orders can be started");
    }

    @Test
    void completeReplacementWorkOrderShouldSetReplacementEquipmentInstalled() {
        UUID workOrderId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID replacementEquipmentId = UUID.randomUUID();
        UUID oldEquipmentReturnWarehouseId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPLACEMENT, WorkOrderStatus.IN_PROGRESS, warehouseId, replacementEquipmentId);
        UUID workOrderDepartmentId = UUID.randomUUID();
        workOrder.setDepartmentId(workOrderDepartmentId);
        WarehouseEquipmentItem item = warehouseItem(warehouseId, replacementEquipmentId, WarehouseEquipmentStatus.RESERVED);
        Equipment replacementEquipment = new Equipment();
        replacementEquipment.setId(replacementEquipmentId);
        replacementEquipment.setDepartmentId(UUID.randomUUID());
        Warehouse returnWarehouse = new Warehouse();
        returnWarehouse.setId(oldEquipmentReturnWarehouseId);
        returnWarehouse.setActive(true);

        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(warehouseEquipmentItemRepository.findByWarehouseIdAndEquipmentIdAndActiveTrueAndIsDeletedFalse(warehouseId, replacementEquipmentId))
                .thenReturn(Optional.of(item));
        when(warehouseRepository.findByIdAndIsDeletedFalse(oldEquipmentReturnWarehouseId)).thenReturn(Optional.of(returnWarehouse));
        when(equipmentRepository.findByIdAndIsDeletedFalse(replacementEquipmentId)).thenReturn(Optional.of(replacementEquipment));
        when(equipmentRepository.save(any(Equipment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(warehouseEquipmentItemRepository.save(any(WarehouseEquipmentItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.save(any(WorkOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        stubLifecycleDtoLookups(workOrder);

        service.complete(workOrderId, new CompleteWorkOrderRequest("done", "summary", oldEquipmentReturnWarehouseId));

        assertThat(item.getStatus()).isEqualTo(WarehouseEquipmentStatus.INSTALLED);
        assertThat(replacementEquipment.getDepartmentId()).isEqualTo(workOrderDepartmentId);
        verify(equipmentRepository).save(replacementEquipment);
        verify(warehouseEquipmentItemService).transferEquipmentToWarehouse(
                workOrder.getEquipmentId(),
                oldEquipmentReturnWarehouseId,
                WarehouseEquipmentStatus.OUT_OF_SERVICE
        );
    }

    @Test
    void completeResolvesDefectWhenNoActiveLinkedWorkOrdersRemain() {
        UUID workOrderId = UUID.randomUUID();
        UUID defectId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.IN_PROGRESS, null, null);
        workOrder.setDefectId(defectId);
        Defect defect = defect(defectId, null, DefectStatus.IN_PROGRESS);

        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(repository.save(any(WorkOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.findAllByDefectIdAndIsDeletedFalseOrderByUpdatedAtDesc(defectId))
                .thenReturn(java.util.List.of(workOrder));
        when(defectRepository.findByIdAndIsDeletedFalse(defectId)).thenReturn(Optional.of(defect));
        when(defectRepository.save(any(Defect.class))).thenAnswer(invocation -> invocation.getArgument(0));
        stubLifecycleDtoLookups(workOrder);

        WorkOrderDto response = service.complete(workOrderId, new CompleteWorkOrderRequest("done", "summary", null));

        assertThat(response.status()).isEqualTo(WorkOrderStatus.COMPLETED);
        assertThat(defect.getStatus()).isEqualTo(DefectStatus.RESOLVED);
        assertThat(defect.getResolvedAt()).isNotNull();
        assertThat(response.defect()).isNotNull();
        assertThat(response.defect().status()).isEqualTo(DefectStatus.RESOLVED);
        verify(defectRepository).save(defect);
    }

    @Test
    void completeDoesNotResolveDefectWhenAnotherActiveLinkedWorkOrderExists() {
        UUID workOrderId = UUID.randomUUID();
        UUID defectId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.IN_PROGRESS, null, null);
        workOrder.setDefectId(defectId);
        WorkOrder anotherActiveWorkOrder = lifecycleWorkOrder(UUID.randomUUID(), WorkType.REPAIR, WorkOrderStatus.APPROVED, null, null);
        anotherActiveWorkOrder.setDefectId(defectId);
        Defect defect = defect(defectId, null, DefectStatus.IN_PROGRESS);

        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(repository.save(any(WorkOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.findAllByDefectIdAndIsDeletedFalseOrderByUpdatedAtDesc(defectId))
                .thenReturn(java.util.List.of(workOrder, anotherActiveWorkOrder));
        when(defectRepository.findByIdAndIsDeletedFalse(defectId)).thenReturn(Optional.of(defect));
        stubLifecycleDtoLookups(workOrder);

        WorkOrderDto response = service.complete(workOrderId, new CompleteWorkOrderRequest("done", "summary", null));

        assertThat(response.status()).isEqualTo(WorkOrderStatus.COMPLETED);
        assertThat(defect.getStatus()).isEqualTo(DefectStatus.IN_PROGRESS);
        assertThat(defect.getResolvedAt()).isNull();
        assertThat(response.defect()).isNotNull();
        assertThat(response.defect().status()).isEqualTo(DefectStatus.IN_PROGRESS);
        verify(defectRepository, never()).save(any(Defect.class));
    }

    @Test
    void completeMovesRepairRequestToCompletedWhenAllWorkOrdersDoneAndDefectsResolved() {
        UUID workOrderId = UUID.randomUUID();
        UUID repairRequestId = UUID.randomUUID();
        UUID defectId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.IN_PROGRESS, null, null);
        workOrder.setRepairRequestId(repairRequestId);
        workOrder.setDefectId(defectId);
        RepairRequest repairRequest = repairRequest(repairRequestId, RequestStatus.OPEN);
        Defect defect = defect(defectId, repairRequestId, DefectStatus.IN_PROGRESS);

        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(repository.save(any(WorkOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.findAllByDefectIdAndIsDeletedFalseOrderByUpdatedAtDesc(defectId))
                .thenReturn(java.util.List.of(workOrder));
        when(repository.findAllByRepairRequestIdAndIsDeletedFalseOrderByUpdatedAtDesc(repairRequestId))
                .thenReturn(java.util.List.of(workOrder));
        when(repairRequestRepository.findByIdAndIsDeletedFalse(repairRequestId)).thenReturn(Optional.of(repairRequest));
        when(repairRequestRepository.save(any(RepairRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(defectRepository.findByIdAndIsDeletedFalse(defectId)).thenReturn(Optional.of(defect));
        when(defectRepository.findAllByRepairRequestIdAndIsDeletedFalseOrderByUpdatedAtDesc(repairRequestId))
                .thenReturn(java.util.List.of(defect));
        when(defectRepository.save(any(Defect.class))).thenAnswer(invocation -> invocation.getArgument(0));
        stubLifecycleDtoLookups(workOrder);

        WorkOrderDto response = service.complete(workOrderId, new CompleteWorkOrderRequest("done", "summary", null));

        assertThat(defect.getStatus()).isEqualTo(DefectStatus.RESOLVED);
        assertThat(repairRequest.getStatus()).isEqualTo(RequestStatus.COMPLETED);
        assertThat(response.repairRequest()).isNotNull();
        assertThat(response.repairRequest().status()).isEqualTo(RequestStatus.COMPLETED);
        verify(repairRequestRepository).save(repairRequest);
    }

    @Test
    void completeDoesNotCompleteRepairRequestWhenAnyDefectStillOpen() {
        UUID workOrderId = UUID.randomUUID();
        UUID repairRequestId = UUID.randomUUID();
        UUID linkedDefectId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.IN_PROGRESS, null, null);
        workOrder.setRepairRequestId(repairRequestId);
        workOrder.setDefectId(linkedDefectId);
        RepairRequest repairRequest = repairRequest(repairRequestId, RequestStatus.OPEN);
        Defect linkedDefect = defect(linkedDefectId, repairRequestId, DefectStatus.IN_PROGRESS);
        Defect stillOpenDefect = defect(UUID.randomUUID(), repairRequestId, DefectStatus.OPEN);

        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(repository.save(any(WorkOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.findAllByDefectIdAndIsDeletedFalseOrderByUpdatedAtDesc(linkedDefectId))
                .thenReturn(java.util.List.of(workOrder));
        when(repository.findAllByRepairRequestIdAndIsDeletedFalseOrderByUpdatedAtDesc(repairRequestId))
                .thenReturn(java.util.List.of(workOrder));
        when(repairRequestRepository.findByIdAndIsDeletedFalse(repairRequestId)).thenReturn(Optional.of(repairRequest));
        when(defectRepository.findByIdAndIsDeletedFalse(linkedDefectId)).thenReturn(Optional.of(linkedDefect));
        when(defectRepository.findAllByRepairRequestIdAndIsDeletedFalseOrderByUpdatedAtDesc(repairRequestId))
                .thenReturn(java.util.List.of(linkedDefect, stillOpenDefect));
        when(defectRepository.save(any(Defect.class))).thenAnswer(invocation -> invocation.getArgument(0));
        stubLifecycleDtoLookups(workOrder);

        WorkOrderDto response = service.complete(workOrderId, new CompleteWorkOrderRequest("done", "summary", null));

        assertThat(linkedDefect.getStatus()).isEqualTo(DefectStatus.RESOLVED);
        assertThat(repairRequest.getStatus()).isEqualTo(RequestStatus.OPEN);
        assertThat(response.repairRequest()).isNotNull();
        assertThat(response.repairRequest().status()).isEqualTo(RequestStatus.OPEN);
        verify(repairRequestRepository, never()).save(any(RepairRequest.class));
    }

    @Test
    void completeWithLinkedPprTaskShouldCompleteTaskAndMovePlanToInProgress() {
        UUID workOrderId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        UUID linkedTaskId = UUID.randomUUID();

        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.IN_PROGRESS, null, null);
        workOrder.setPprTaskId(linkedTaskId);

        PprPlan plan = pprPlan(planId, PlanStatus.DRAFT);
        PprTask linkedTask = pprTask(linkedTaskId, plan, com.toir.enums.PprTaskStatus.IN_PROGRESS);
        PprTask plannedTask = pprTask(UUID.randomUUID(), plan, com.toir.enums.PprTaskStatus.PLANNED);

        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(pprTaskRepository.findByIdAndIsDeletedFalse(linkedTaskId)).thenReturn(Optional.of(linkedTask));
        when(pprTaskRepository.findAllByPlanIdAndIsDeletedFalseOrderByUpdatedAtDesc(planId))
                .thenReturn(java.util.List.of(linkedTask, plannedTask));
        when(pprTaskRepository.save(any(PprTask.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(pprPlanRepository.save(any(PprPlan.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.save(any(WorkOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        stubLifecycleDtoLookups(workOrder);

        WorkOrderDto result = service.complete(workOrderId, new CompleteWorkOrderRequest("done", "summary", null));

        assertThat(result.status()).isEqualTo(WorkOrderStatus.COMPLETED);
        assertThat(linkedTask.getStatus()).isEqualTo(com.toir.enums.PprTaskStatus.COMPLETED);
        assertThat(plan.getStatus()).isEqualTo(PlanStatus.IN_PROGRESS);
        assertThat(plan.getStatus()).isNotEqualTo(PlanStatus.DRAFT);
        verify(pprTaskRepository).save(any(PprTask.class));
        verify(pprPlanRepository).save(any(PprPlan.class));
    }

    @Test
    void completeWithLinkedPprTaskShouldClosePlanWhenAllTasksCompleted() {
        UUID workOrderId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        UUID linkedTaskId = UUID.randomUUID();

        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.IN_PROGRESS, null, null);
        workOrder.setPprTaskId(linkedTaskId);

        PprPlan plan = pprPlan(planId, PlanStatus.DRAFT);
        PprTask linkedTask = pprTask(linkedTaskId, plan, com.toir.enums.PprTaskStatus.IN_PROGRESS);
        PprTask completedTask = pprTask(UUID.randomUUID(), plan, com.toir.enums.PprTaskStatus.COMPLETED);

        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(pprTaskRepository.findByIdAndIsDeletedFalse(linkedTaskId)).thenReturn(Optional.of(linkedTask));
        when(pprTaskRepository.findAllByPlanIdAndIsDeletedFalseOrderByUpdatedAtDesc(planId))
                .thenReturn(java.util.List.of(linkedTask, completedTask));
        when(pprTaskRepository.save(any(PprTask.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(pprPlanRepository.save(any(PprPlan.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.save(any(WorkOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        stubLifecycleDtoLookups(workOrder);

        service.complete(workOrderId, new CompleteWorkOrderRequest("done", "summary", null));

        assertThat(linkedTask.getStatus()).isEqualTo(com.toir.enums.PprTaskStatus.COMPLETED);
        assertThat(plan.getStatus()).isEqualTo(PlanStatus.CLOSED);
    }

    @Test
    void closeWithLinkedAlreadyCompletedTaskShouldRollupPlanStatus() {
        UUID workOrderId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        UUID linkedTaskId = UUID.randomUUID();

        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.COMPLETED, null, null);
        workOrder.setPprTaskId(linkedTaskId);

        PprPlan plan = pprPlan(planId, PlanStatus.DRAFT);
        PprTask linkedTask = pprTask(linkedTaskId, plan, com.toir.enums.PprTaskStatus.COMPLETED);
        PprTask plannedTask = pprTask(UUID.randomUUID(), plan, com.toir.enums.PprTaskStatus.PLANNED);

        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(pprTaskRepository.findByIdAndIsDeletedFalse(linkedTaskId)).thenReturn(Optional.of(linkedTask));
        when(pprTaskRepository.findAllByPlanIdAndIsDeletedFalseOrderByUpdatedAtDesc(planId))
                .thenReturn(java.util.List.of(linkedTask, plannedTask));
        when(pprPlanRepository.save(any(PprPlan.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.save(any(WorkOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        stubLifecycleDtoLookups(workOrder);

        WorkOrderDto result = service.close(workOrderId, new CloseWorkOrderRequest("closed", "notes"));

        assertThat(result.status()).isEqualTo(WorkOrderStatus.CLOSED);
        assertThat(plan.getStatus()).isEqualTo(PlanStatus.IN_PROGRESS);
        verify(pprTaskRepository, never()).save(any(PprTask.class));
        verify(pprPlanRepository).save(any(PprPlan.class));
    }

    @Test
    void recalculateLinkedPprPlanForHistoricalClosedWorkOrderShouldMoveDraftPlanOutOfDraft() {
        UUID workOrderId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        UUID linkedTaskId = UUID.randomUUID();

        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.CLOSED, null, null);
        workOrder.setPprTaskId(linkedTaskId);

        PprPlan plan = pprPlan(planId, PlanStatus.DRAFT);
        PprTask linkedTask = pprTask(linkedTaskId, plan, com.toir.enums.PprTaskStatus.COMPLETED);

        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(pprTaskRepository.findByIdAndIsDeletedFalse(linkedTaskId)).thenReturn(Optional.of(linkedTask));
        when(pprTaskRepository.findAllByPlanIdAndIsDeletedFalseOrderByUpdatedAtDesc(planId))
                .thenReturn(java.util.List.of(linkedTask));
        when(pprPlanRepository.save(any(PprPlan.class))).thenAnswer(invocation -> invocation.getArgument(0));
        stubLifecycleDtoLookups(workOrder);

        WorkOrderDto result = service.recalculateLinkedPprPlanForWorkOrder(workOrderId);

        assertThat(result.id()).isEqualTo(workOrderId);
        assertThat(plan.getStatus()).isEqualTo(PlanStatus.CLOSED);
        assertThat(plan.getStatus()).isNotEqualTo(PlanStatus.DRAFT);
        verify(pprPlanRepository).save(any(PprPlan.class));
    }

    @Test
    void completeWithoutLinkedPprTaskShouldKeepExistingBehavior() {
        UUID workOrderId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.IN_PROGRESS, null, null);

        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(repository.save(any(WorkOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        stubLifecycleDtoLookups(workOrder);

        WorkOrderDto result = service.complete(workOrderId, new CompleteWorkOrderRequest("done", "summary", null));

        assertThat(result.status()).isEqualTo(WorkOrderStatus.COMPLETED);
        verifyNoInteractions(pprTaskRepository, pprPlanRepository);
    }

    @Test
    void completeAutoWorkOrderCreatesAnchorFromDueEventAndTriggersRecalculationOnce() throws Exception {
        UUID workOrderId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        UUID dueEventId = UUID.randomUUID();
        UUID regulationId = UUID.randomUUID();
        UUID ruleId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.IN_PROGRESS, null, null);
        workOrder.setEquipmentId(equipmentId);
        workOrder.setMaintenanceDueEventId(dueEventId);
        workOrder.setCycleKey("EQ:RULE:METER:ENGINE_HOURS:500");
        MaintenanceDueEvent event = dueEvent(dueEventId, equipmentId, regulationId, ruleId);

        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(repository.save(any(WorkOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(maintenanceDueEventService.getOrThrow(dueEventId)).thenReturn(event);
        when(maintenanceCompletionAnchorRepository.findByMaintenanceDueEventIdAndIsDeletedFalse(dueEventId))
                .thenReturn(Optional.empty());
        when(maintenanceCompletionAnchorRepository.findByWorkOrderIdAndIsDeletedFalse(workOrderId))
                .thenReturn(Optional.empty());
        when(maintenanceCompletionAnchorRepository.save(any(MaintenanceCompletionAnchor.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(objectMapper.writeValueAsString(any()))
                .thenReturn("[{\"meterType\":\"ENGINE_HOURS\",\"value\":520.0}]");
        when(maintenanceDueEventService.completeFromWorkOrder(event, "Work order completed"))
                .thenReturn(event);
        when(maintenanceAutomationServiceProvider.getIfAvailable()).thenReturn(maintenanceAutomationService);
        when(maintenanceAutomationService.evaluateEquipment(equipmentId, MaintenanceTriggerSource.WORK_ORDER_COMPLETED))
                .thenReturn(new MaintenanceAutomationService.EvaluationResult(1, 0, 0, 0, 0));
        stubLifecycleDtoLookups(workOrder);

        WorkOrderDto result = service.complete(workOrderId, new CompleteWorkOrderRequest("done", "summary", null));

        assertThat(result.status()).isEqualTo(WorkOrderStatus.COMPLETED);
        ArgumentCaptor<MaintenanceCompletionAnchor> anchorCaptor =
                ArgumentCaptor.forClass(MaintenanceCompletionAnchor.class);
        verify(maintenanceCompletionAnchorRepository).save(anchorCaptor.capture());
        MaintenanceCompletionAnchor anchor = anchorCaptor.getValue();
        assertThat(anchor.getEquipmentId()).isEqualTo(equipmentId);
        assertThat(anchor.getRegulationId()).isEqualTo(regulationId);
        assertThat(anchor.getEquipmentMaintenanceRuleId()).isEqualTo(ruleId);
        assertThat(anchor.getSource()).isEqualTo("WORK_ORDER");
        assertThat(anchor.getWorkOrderId()).isEqualTo(workOrderId);
        assertThat(anchor.getMaintenanceDueEventId()).isEqualTo(dueEventId);
        assertThat(anchor.getPerformedAt()).isEqualTo(workOrder.getCompletedAt());
        assertThat(anchor.getPlannedDueAt()).isEqualTo(event.getDueAt());
        assertThat(anchor.getPlannedMeterValue()).isEqualByComparingTo("500.0");
        assertThat(anchor.getMeterSnapshots()).contains("ENGINE_HOURS").contains("520.0");
        verify(maintenanceDueEventService).completeFromWorkOrder(event, "Work order completed");
        verify(maintenanceAutomationService, times(1))
                .evaluateEquipment(equipmentId, MaintenanceTriggerSource.WORK_ORDER_COMPLETED);
    }

    @Test
    void completeAutoWorkOrderReusesExistingDueEventAnchor() throws Exception {
        UUID workOrderId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        UUID dueEventId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.IN_PROGRESS, null, null);
        workOrder.setEquipmentId(equipmentId);
        workOrder.setMaintenanceDueEventId(dueEventId);
        MaintenanceDueEvent event = dueEvent(dueEventId, equipmentId, UUID.randomUUID(), UUID.randomUUID());
        MaintenanceCompletionAnchor existing = new MaintenanceCompletionAnchor();
        ReflectionTestUtils.setField(existing, "id", UUID.randomUUID());

        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(repository.save(any(WorkOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(maintenanceDueEventService.getOrThrow(dueEventId)).thenReturn(event);
        when(maintenanceCompletionAnchorRepository.findByMaintenanceDueEventIdAndIsDeletedFalse(dueEventId))
                .thenReturn(Optional.of(existing));
        when(maintenanceCompletionAnchorRepository.save(any(MaintenanceCompletionAnchor.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(objectMapper.writeValueAsString(any()))
                .thenReturn("[{\"meterType\":\"ENGINE_HOURS\",\"value\":520.0}]");
        when(maintenanceDueEventService.completeFromWorkOrder(event, "Work order completed"))
                .thenReturn(event);
        stubLifecycleDtoLookups(workOrder);

        service.complete(workOrderId, new CompleteWorkOrderRequest("done", "summary", null));

        ArgumentCaptor<MaintenanceCompletionAnchor> anchorCaptor =
                ArgumentCaptor.forClass(MaintenanceCompletionAnchor.class);
        verify(maintenanceCompletionAnchorRepository, times(1)).save(anchorCaptor.capture());
        assertThat(anchorCaptor.getValue()).isSameAs(existing);
        assertThat(existing.getMaintenanceDueEventId()).isEqualTo(dueEventId);
        assertThat(existing.getWorkOrderId()).isEqualTo(workOrderId);
    }

    @Test
    void manualWorkOrderWithoutEventCompletesWithoutMaintenanceAnchor() {
        UUID workOrderId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.IN_PROGRESS, null, null);

        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(repository.save(any(WorkOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        stubLifecycleDtoLookups(workOrder);

        WorkOrderDto result = service.complete(workOrderId, new CompleteWorkOrderRequest("done", "summary", null));

        assertThat(result.status()).isEqualTo(WorkOrderStatus.COMPLETED);
        verify(maintenanceCompletionAnchorRepository, never()).save(any(MaintenanceCompletionAnchor.class));
        verifyNoInteractions(maintenanceDueEventService, maintenanceAutomationServiceProvider);
    }

    @Test
    void manualRegulatedWorkOrderStillCreatesCompletionAnchor() {
        UUID workOrderId = UUID.randomUUID();
        UUID regulationId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.IN_PROGRESS, null, null);

        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(repository.save(any(WorkOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(maintenanceCompletionAnchorRepository.findByWorkOrderIdAndIsDeletedFalse(workOrderId))
                .thenReturn(Optional.empty());
        when(maintenanceCompletionAnchorRepository.save(any(MaintenanceCompletionAnchor.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        stubLifecycleDtoLookups(workOrder);

        service.complete(workOrderId, new CompleteWorkOrderRequest(
                "done",
                "summary",
                null,
                regulationId,
                null,
                null,
                null,
                null,
                null
        ));

        ArgumentCaptor<MaintenanceCompletionAnchor> anchorCaptor =
                ArgumentCaptor.forClass(MaintenanceCompletionAnchor.class);
        verify(maintenanceCompletionAnchorRepository).save(anchorCaptor.capture());
        assertThat(anchorCaptor.getValue().getRegulationId()).isEqualTo(regulationId);
        assertThat(anchorCaptor.getValue().getMaintenanceDueEventId()).isNull();
        verifyNoInteractions(maintenanceDueEventService, maintenanceAutomationServiceProvider);
    }

    @Test
    void completeFromApprovedShouldSucceed() {
        UUID workOrderId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.APPROVED, null, null);
        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(repository.save(any(WorkOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        stubLifecycleDtoLookups(workOrder);

        WorkOrderDto result = service.complete(workOrderId, new CompleteWorkOrderRequest("done", "summary", null));

        assertThat(result.status()).isEqualTo(WorkOrderStatus.COMPLETED);
        assertThat(result.result()).isEqualTo("done");
    }

    @Test
    void completeWithMaterialUsagesIssuesMaterialsBeforeCompletion() {
        UUID workOrderId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.IN_PROGRESS, null, null);
        RepairMaterialUsageDto usage = new RepairMaterialUsageDto(
                null,
                null,
                warehouseId,
                sparePartId,
                2,
                12.5
        );

        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(repository.save(any(WorkOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(repairMaterialUsageService.register(workOrderId, usage)).thenReturn(usage);
        stubLifecycleDtoLookups(workOrder);

        WorkOrderDto result = service.complete(workOrderId, new CompleteWorkOrderRequest(
                "done",
                "summary",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(usage)
        ));

        assertThat(result.status()).isEqualTo(WorkOrderStatus.COMPLETED);
        verify(repairMaterialUsageService).register(workOrderId, usage);
        verify(repository).save(workOrder);
    }

    @Test
    void completeWithMaterialUsageFailureDoesNotSaveCompletedWorkOrder() {
        UUID workOrderId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.IN_PROGRESS, null, null);
        RepairMaterialUsageDto usage = new RepairMaterialUsageDto(
                null,
                null,
                warehouseId,
                sparePartId,
                99,
                12.5
        );

        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(repairMaterialUsageService.register(workOrderId, usage))
                .thenThrow(RestException.badRequest("Cannot write off more than available"));

        assertThatThrownBy(() -> service.complete(workOrderId, new CompleteWorkOrderRequest(
                "done",
                "summary",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(usage)
        )))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Cannot write off more than available");

        assertThat(workOrder.getStatus()).isEqualTo(WorkOrderStatus.IN_PROGRESS);
        verify(repository, never()).save(any(WorkOrder.class));
    }

    @Test
    void completeFromDraftShouldFail() {
        UUID workOrderId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.DRAFT, null, null);
        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));

        assertThatThrownBy(() -> service.complete(workOrderId, new CompleteWorkOrderRequest("done", "summary", null)))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Only APPROVED or IN_PROGRESS work orders can be completed");
    }

    @Test
    void completeFromPlannedShouldFail() {
        UUID workOrderId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.PLANNED, null, null);
        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));

        assertThatThrownBy(() -> service.complete(workOrderId, new CompleteWorkOrderRequest("done", "summary", null)))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Only APPROVED or IN_PROGRESS work orders can be completed");
    }

    @Test
    void completeFromClosedShouldFail() {
        UUID workOrderId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.CLOSED, null, null);
        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));

        assertThatThrownBy(() -> service.complete(workOrderId, new CompleteWorkOrderRequest("done", "summary", null)))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Only APPROVED or IN_PROGRESS work orders can be completed");
    }

    @Test
    void completeFromCancelledShouldFail() {
        UUID workOrderId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.CANCELLED, null, null);
        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));

        assertThatThrownBy(() -> service.complete(workOrderId, new CompleteWorkOrderRequest("done", "summary", null)))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Only APPROVED or IN_PROGRESS work orders can be completed");
    }

    @Test
    void completeReplacementWorkOrderWithoutReturnWarehouseShouldFail() {
        UUID workOrderId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID replacementEquipmentId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPLACEMENT, WorkOrderStatus.IN_PROGRESS, warehouseId, replacementEquipmentId);
        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));

        assertThatThrownBy(() -> service.complete(workOrderId, new CompleteWorkOrderRequest("done", "summary", null)))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("oldEquipmentReturnWarehouseId is required");
    }

    @Test
    void completeReplacementWorkOrderWithUnknownReturnWarehouseShouldFail() {
        UUID workOrderId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID replacementEquipmentId = UUID.randomUUID();
        UUID oldEquipmentReturnWarehouseId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPLACEMENT, WorkOrderStatus.IN_PROGRESS, warehouseId, replacementEquipmentId);
        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(warehouseRepository.findByIdAndIsDeletedFalse(oldEquipmentReturnWarehouseId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.complete(workOrderId, new CompleteWorkOrderRequest("done", "summary", oldEquipmentReturnWarehouseId)))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Warehouse not found");
    }

    @Test
    void completeNonReplacementWithReturnWarehouseShouldFail() {
        UUID workOrderId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.IN_PROGRESS, null, null);
        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));

        assertThatThrownBy(() -> service.complete(workOrderId, new CompleteWorkOrderRequest("done", "summary", UUID.randomUUID())))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("oldEquipmentReturnWarehouseId must be null");
    }

    @Test
    void closeReplacementWorkOrderShouldSetReplacementEquipmentInstalled() {
        UUID workOrderId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID replacementEquipmentId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPLACEMENT, WorkOrderStatus.COMPLETED, warehouseId, replacementEquipmentId);
        WarehouseEquipmentItem item = warehouseItem(warehouseId, replacementEquipmentId, WarehouseEquipmentStatus.RESERVED);

        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(warehouseEquipmentItemRepository.findByWarehouseIdAndEquipmentIdAndActiveTrueAndIsDeletedFalse(warehouseId, replacementEquipmentId))
                .thenReturn(Optional.of(item));
        when(warehouseEquipmentItemRepository.save(any(WarehouseEquipmentItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.save(any(WorkOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        stubLifecycleDtoLookups(workOrder);

        service.close(workOrderId, new CloseWorkOrderRequest("closed", "notes"));

        assertThat(item.getStatus()).isEqualTo(WarehouseEquipmentStatus.INSTALLED);
    }

    @Test
    void closeFromDraftShouldFail() {
        UUID workOrderId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.DRAFT, null, null);
        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));

        assertThatThrownBy(() -> service.close(workOrderId, new CloseWorkOrderRequest("closed", "notes")))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Only completed work orders can be closed");
    }

    @Test
    void closeFromApprovedShouldFail() {
        UUID workOrderId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.APPROVED, null, null);
        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));

        assertThatThrownBy(() -> service.close(workOrderId, new CloseWorkOrderRequest("closed", "notes")))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Only completed work orders can be closed");
    }

    @Test
    void closeFromCompletedShouldSucceed() {
        UUID workOrderId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.COMPLETED, null, null);
        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(repository.save(any(WorkOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(safetyPermitRepository.findByWorkOrderIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.empty());
        when(completionActRepository.findByWorkOrderIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.empty());
        stubLifecycleDtoLookups(workOrder);

        WorkOrderDto result = service.close(workOrderId, new CloseWorkOrderRequest("closed", "notes"));

        assertThat(result.status()).isEqualTo(WorkOrderStatus.CLOSED);
    }

    @Test
    void closeWithBlankResultShouldFail() {
        UUID workOrderId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.COMPLETED, null, null);
        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));

        assertThatThrownBy(() -> service.close(workOrderId, new CloseWorkOrderRequest(" ", "notes")))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Result is required to close a work order");
    }

    @Test
    void closeMissingWorkOrderShouldRemainNotFound() {
        UUID workOrderId = UUID.randomUUID();
        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.close(workOrderId, new CloseWorkOrderRequest("closed", "notes")))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(ex.getMessage()).contains("Work order not found");
                });
        verify(repository, never()).save(any(WorkOrder.class));
    }

    @Test
    void closeWithIncompleteTaskShouldFailWithEvidenceMessage() {
        UUID workOrderId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.COMPLETED, null, null);
        workOrder.getTasks().add(workOrderTask(workOrder, "Lockout checklist", TaskExecutionStatus.IN_PROGRESS));
        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));

        assertThatThrownBy(() -> service.close(workOrderId, new CloseWorkOrderRequest("closed", "notes")))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Cannot close work order; missing evidence")
                .hasMessageContaining("Incomplete tasks/checklist items: Lockout checklist");
        verify(repository, never()).save(any(WorkOrder.class));
    }

    @Test
    void closeWithExistingNonClosedSafetyPermitShouldFailWithEvidenceMessage() {
        UUID workOrderId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.COMPLETED, null, null);
        SafetyPermit permit = safetyPermit(workOrderId, SafetyPermitStatus.ISSUED);
        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(safetyPermitRepository.findByWorkOrderIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(permit));
        when(completionActRepository.findByWorkOrderIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.close(workOrderId, new CloseWorkOrderRequest("closed", "notes")))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Cannot close work order; missing evidence")
                .hasMessageContaining("Safety permit must be CLOSED");
        verify(repository, never()).save(any(WorkOrder.class));
    }

    @Test
    void closeWithExistingUnsignedCompletionActShouldFailWithEvidenceMessage() {
        UUID workOrderId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.COMPLETED, null, null);
        CompletionAct act = completionAct(workOrderId, false);
        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(safetyPermitRepository.findByWorkOrderIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.empty());
        when(completionActRepository.findByWorkOrderIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(act));

        assertThatThrownBy(() -> service.close(workOrderId, new CloseWorkOrderRequest("closed", "notes")))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Cannot close work order; missing evidence")
                .hasMessageContaining("Completion act must be signed");
        verify(repository, never()).save(any(WorkOrder.class));
    }

    @Test
    void closeClosesDefectWhenAllLinkedWorkOrdersTerminalAndDefectResolved() {
        UUID workOrderId = UUID.randomUUID();
        UUID defectId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.COMPLETED, null, null);
        workOrder.setDefectId(defectId);
        Defect defect = defect(defectId, null, DefectStatus.RESOLVED);

        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(repository.save(any(WorkOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.findAllByDefectIdAndIsDeletedFalseOrderByUpdatedAtDesc(defectId))
                .thenReturn(java.util.List.of(workOrder));
        when(defectRepository.findByIdAndIsDeletedFalse(defectId)).thenReturn(Optional.of(defect));
        when(defectRepository.save(any(Defect.class))).thenAnswer(invocation -> invocation.getArgument(0));
        stubLifecycleDtoLookups(workOrder);

        WorkOrderDto response = service.close(workOrderId, new CloseWorkOrderRequest("closed", "notes"));

        assertThat(response.status()).isEqualTo(WorkOrderStatus.CLOSED);
        assertThat(defect.getStatus()).isEqualTo(DefectStatus.CLOSED);
        assertThat(response.defect()).isNotNull();
        assertThat(response.defect().status()).isEqualTo(DefectStatus.CLOSED);
        verify(defectRepository).save(defect);
    }

    @Test
    void closeDoesNotCloseRepairRequestWhenAnyDefectStillOpen() {
        UUID workOrderId = UUID.randomUUID();
        UUID repairRequestId = UUID.randomUUID();
        UUID linkedDefectId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.COMPLETED, null, null);
        workOrder.setRepairRequestId(repairRequestId);
        workOrder.setDefectId(linkedDefectId);
        RepairRequest repairRequest = repairRequest(repairRequestId, RequestStatus.OPEN);
        Defect linkedDefect = defect(linkedDefectId, repairRequestId, DefectStatus.RESOLVED);
        Defect stillOpenDefect = defect(UUID.randomUUID(), repairRequestId, DefectStatus.OPEN);

        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(repository.save(any(WorkOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.findAllByDefectIdAndIsDeletedFalseOrderByUpdatedAtDesc(linkedDefectId))
                .thenReturn(java.util.List.of(workOrder));
        when(repository.findAllByRepairRequestIdAndIsDeletedFalseOrderByUpdatedAtDesc(repairRequestId))
                .thenReturn(java.util.List.of(workOrder));
        when(repairRequestRepository.findByIdAndIsDeletedFalse(repairRequestId)).thenReturn(Optional.of(repairRequest));
        when(defectRepository.findByIdAndIsDeletedFalse(linkedDefectId)).thenReturn(Optional.of(linkedDefect));
        when(defectRepository.findAllByRepairRequestIdAndIsDeletedFalseOrderByUpdatedAtDesc(repairRequestId))
                .thenReturn(java.util.List.of(linkedDefect, stillOpenDefect));
        when(defectRepository.save(any(Defect.class))).thenAnswer(invocation -> invocation.getArgument(0));
        stubLifecycleDtoLookups(workOrder);

        WorkOrderDto response = service.close(workOrderId, new CloseWorkOrderRequest("closed", "notes"));

        assertThat(linkedDefect.getStatus()).isEqualTo(DefectStatus.CLOSED);
        assertThat(repairRequest.getStatus()).isEqualTo(RequestStatus.OPEN);
        assertThat(response.repairRequest()).isNotNull();
        assertThat(response.repairRequest().status()).isEqualTo(RequestStatus.OPEN);
        verify(repairRequestRepository, never()).save(any(RepairRequest.class));
    }

    @Test
    void closeClosesRepairRequestWhenAllWorkOrdersTerminalAndAllDefectsResolvedOrClosed() {
        UUID workOrderId = UUID.randomUUID();
        UUID repairRequestId = UUID.randomUUID();
        UUID linkedDefectId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.COMPLETED, null, null);
        workOrder.setRepairRequestId(repairRequestId);
        workOrder.setDefectId(linkedDefectId);
        WorkOrder completedSibling = lifecycleWorkOrder(UUID.randomUUID(), WorkType.REPAIR, WorkOrderStatus.COMPLETED, null, null);
        completedSibling.setRepairRequestId(repairRequestId);
        WorkOrder cancelledSibling = lifecycleWorkOrder(UUID.randomUUID(), WorkType.REPAIR, WorkOrderStatus.CANCELLED, null, null);
        cancelledSibling.setRepairRequestId(repairRequestId);
        RepairRequest repairRequest = repairRequest(repairRequestId, RequestStatus.IN_PROGRESS);
        Defect linkedDefect = defect(linkedDefectId, repairRequestId, DefectStatus.RESOLVED);
        Defect alreadyClosedDefect = defect(UUID.randomUUID(), repairRequestId, DefectStatus.CLOSED);

        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(repository.save(any(WorkOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.findAllByDefectIdAndIsDeletedFalseOrderByUpdatedAtDesc(linkedDefectId))
                .thenReturn(java.util.List.of(workOrder));
        when(repository.findAllByRepairRequestIdAndIsDeletedFalseOrderByUpdatedAtDesc(repairRequestId))
                .thenReturn(java.util.List.of(workOrder, completedSibling, cancelledSibling));
        when(repairRequestRepository.findByIdAndIsDeletedFalse(repairRequestId)).thenReturn(Optional.of(repairRequest));
        when(repairRequestRepository.save(any(RepairRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(defectRepository.findByIdAndIsDeletedFalse(linkedDefectId)).thenReturn(Optional.of(linkedDefect));
        when(defectRepository.findAllByRepairRequestIdAndIsDeletedFalseOrderByUpdatedAtDesc(repairRequestId))
                .thenReturn(java.util.List.of(linkedDefect, alreadyClosedDefect));
        when(defectRepository.save(any(Defect.class))).thenAnswer(invocation -> invocation.getArgument(0));
        stubLifecycleDtoLookups(workOrder);

        WorkOrderDto response = service.close(workOrderId, new CloseWorkOrderRequest("closed", "notes"));

        assertThat(linkedDefect.getStatus()).isEqualTo(DefectStatus.CLOSED);
        assertThat(repairRequest.getStatus()).isEqualTo(RequestStatus.CLOSED);
        assertThat(repairRequest.getCloseResult()).isEqualTo("closed");
        assertThat(repairRequest.getActualCompletionAt()).isNotNull();
        assertThat(response.repairRequest()).isNotNull();
        assertThat(response.repairRequest().status()).isEqualTo(RequestStatus.CLOSED);
        verify(repairRequestRepository).save(repairRequest);
    }

    @Test
    void workOrderWithoutLinksStillWorks() {
        UUID workOrderId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.APPROVED, null, null);

        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(repository.save(any(WorkOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        stubLifecycleDtoLookups(workOrder);

        WorkOrderDto response = service.start(workOrderId);

        assertThat(response.status()).isEqualTo(WorkOrderStatus.IN_PROGRESS);
        assertThat(response.repairRequest()).isNull();
        assertThat(response.defect()).isNull();
    }

    @Test
    void nonReplacementLifecycleShouldNotChangeWarehouseEquipmentItemStatus() {
        UUID workOrderId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.DRAFT, null, null);

        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(repository.save(any(WorkOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        stubLifecycleDtoLookups(workOrder);

        service.approve(workOrderId, UUID.randomUUID());

        verifyNoInteractions(warehouseEquipmentItemRepository);
    }

    @Test
    void startReplacementWorkOrderWithInvalidWarehouseEquipmentStateShouldFail() {
        UUID workOrderId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID replacementEquipmentId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPLACEMENT, WorkOrderStatus.APPROVED, warehouseId, replacementEquipmentId);
        WarehouseEquipmentItem item = warehouseItem(warehouseId, replacementEquipmentId, WarehouseEquipmentStatus.INSTALLED);

        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(warehouseEquipmentItemRepository.findByWarehouseIdAndEquipmentIdAndActiveTrueAndIsDeletedFalse(warehouseId, replacementEquipmentId))
                .thenReturn(Optional.of(item));

        assertThatThrownBy(() -> service.start(workOrderId))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("must be AVAILABLE or RESERVED");
    }

    @Test
    void startReplacementWorkOrderWithMissingWarehouseEquipmentItemShouldFail() {
        UUID workOrderId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID replacementEquipmentId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPLACEMENT, WorkOrderStatus.APPROVED, warehouseId, replacementEquipmentId);

        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(warehouseEquipmentItemRepository.findByWarehouseIdAndEquipmentIdAndActiveTrueAndIsDeletedFalse(warehouseId, replacementEquipmentId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.start(workOrderId))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Replacement equipment item not found in selected warehouse");
    }

    private WorkOrderCountProjection countProjection(UUID workOrderId, long count) {
        return new WorkOrderCountProjection() {
            @Override
            public UUID getWorkOrderId() {
                return workOrderId;
            }

            @Override
            public long getCount() {
                return count;
            }
        };
    }

    private PprPlan pprPlan(UUID id, PlanStatus status) {
        PprPlan plan = new PprPlan();
        plan.setId(id);
        plan.setCode("PPR-2026-0001");
        plan.setName("Monthly PPR plan");
        plan.setStartDate(java.time.LocalDate.of(2026, 5, 1));
        plan.setEndDate(java.time.LocalDate.of(2026, 5, 31));
        plan.setStatus(status);
        plan.setCreatedById(UUID.randomUUID());
        return plan;
    }

    private PprTask pprTask(UUID id, PprPlan plan, com.toir.enums.PprTaskStatus status) {
        PprTask task = new PprTask();
        task.setId(id);
        task.setCode("PT-" + id.toString().substring(0, 8));
        task.setPlan(plan);
        task.setStatus(status);
        task.setRegulationId(UUID.randomUUID());
        task.setEquipmentId(UUID.randomUUID());
        task.setTitle("PPR task");
        task.setScheduledStart(java.time.LocalDateTime.now().minusHours(1));
        task.setScheduledEnd(java.time.LocalDateTime.now().plusHours(1));
        task.setDueDate(java.time.LocalDateTime.now().plusDays(1));
        task.setPlannedLaborHours(2.0);
        return task;
    }

    private WorkOrderRequest request(WorkOrderType type, WorkType workType, UUID warehouseId, UUID replacementEquipmentId) {
        return new WorkOrderRequest(
                "WO-2026-REPL-1",
                "Replacement job",
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                null,
                null,
                null,
                type,
                workType,
                warehouseId,
                replacementEquipmentId,
                PriorityLevel.MEDIUM,
                null,
                null,
                UUID.randomUUID(),
                "summary"
        );
    }

    private WorkOrderRequest requestWithLinks(UUID repairRequestId, UUID defectId) {
        WorkOrderRequest base = request(WorkOrderType.PLANNED, WorkType.REPAIR, null, null);
        return new WorkOrderRequest(
                base.number(),
                base.title(),
                base.equipmentId(),
                base.departmentId(),
                repairRequestId,
                defectId,
                base.pprTaskId(),
                base.contractorId(),
                base.type(),
                base.workType(),
                base.warehouseId(),
                base.replacementEquipmentId(),
                base.priority(),
                base.startPlannedAt(),
                base.endPlannedAt(),
                base.createdById(),
                base.summary()
        );
    }

    private WorkOrderRequest requestWithLinks(UUID repairRequestId, UUID defectId, UUID equipmentId) {
        WorkOrderRequest base = requestWithLinks(repairRequestId, defectId);
        return new WorkOrderRequest(
                base.number(),
                base.title(),
                equipmentId,
                base.departmentId(),
                base.repairRequestId(),
                base.defectId(),
                base.pprTaskId(),
                base.contractorId(),
                base.type(),
                base.workType(),
                base.warehouseId(),
                base.replacementEquipmentId(),
                base.priority(),
                base.startPlannedAt(),
                base.endPlannedAt(),
                base.createdById(),
                base.summary()
        );
    }

    private WorkOrderRequest requestWithPprTask(UUID pprTaskId) {
        WorkOrderRequest base = request(WorkOrderType.PLANNED, WorkType.REPAIR, null, null);
        return new WorkOrderRequest(
                base.number(),
                base.title(),
                base.equipmentId(),
                base.departmentId(),
                base.repairRequestId(),
                base.defectId(),
                pprTaskId,
                base.contractorId(),
                base.type(),
                base.workType(),
                base.warehouseId(),
                base.replacementEquipmentId(),
                base.priority(),
                base.startPlannedAt(),
                base.endPlannedAt(),
                base.createdById(),
                base.summary()
        );
    }

    private WorkOrderRequest requestWithNode(UUID equipmentId, UUID equipmentNodeId) {
        WorkOrderRequest base = request(WorkOrderType.PLANNED, WorkType.REPAIR, null, null);
        return new WorkOrderRequest(
                base.number(),
                base.title(),
                equipmentId,
                equipmentNodeId,
                base.departmentId(),
                base.repairRequestId(),
                base.defectId(),
                base.pprTaskId(),
                base.contractorId(),
                base.type(),
                base.workType(),
                base.warehouseId(),
                base.replacementEquipmentId(),
                base.priority(),
                base.startPlannedAt(),
                base.endPlannedAt(),
                base.createdById(),
                base.summary()
        );
    }

    private RepairRequest repairRequest(UUID id, RequestStatus status) {
        return repairRequest(id, status, null);
    }

    private RepairRequest repairRequest(UUID id, RequestStatus status, UUID equipmentId) {
        RepairRequest repairRequest = new RepairRequest();
        repairRequest.setId(id);
        repairRequest.setNumber("RR-2026-1001");
        repairRequest.setEquipmentId(equipmentId);
        repairRequest.setPriority(PriorityLevel.MEDIUM);
        repairRequest.setTitle("Repair request");
        repairRequest.setDescription("Short description");
        repairRequest.setStatus(status);
        return repairRequest;
    }

    private Defect defect(UUID id, UUID repairRequestId) {
        return defect(id, repairRequestId, DefectStatus.OPEN);
    }

    private Defect defect(UUID id, UUID repairRequestId, UUID equipmentId) {
        Defect defect = defect(id, repairRequestId, DefectStatus.OPEN);
        defect.setEquipmentId(equipmentId);
        return defect;
    }

    private Defect defect(UUID id, UUID repairRequestId, DefectStatus status) {
        Defect defect = new Defect();
        defect.setId(id);
        defect.setCode("DEF-2026-1001");
        defect.setTitle("Leak");
        defect.setStatus(status);
        defect.setSeverity("HIGH");
        defect.setRepairRequestId(repairRequestId);
        return defect;
    }

    private EquipmentNode equipmentNode(UUID id, UUID equipmentId, String code, String name) {
        EquipmentNode node = new EquipmentNode();
        node.setId(id);
        node.setEquipmentId(equipmentId);
        node.setCode(code);
        node.setName(name);
        node.setNodeType(EquipmentNodeType.COMPONENT);
        return node;
    }

    private void mockSuccessfulCreateDependencies(WorkOrderRequest request) {
        Equipment sourceEquipment = new Equipment();
        sourceEquipment.setId(request.equipmentId());
        sourceEquipment.setName("Source Equipment");

        Department department = new Department();
        department.setId(request.departmentId());
        department.setName("Maintenance");

        when(repository.existsByNumberAndIsDeletedFalse(request.number())).thenReturn(false);
        when(equipmentRepository.findById(eq(request.equipmentId()))).thenReturn(Optional.of(sourceEquipment));
        when(departmentRepository.findById(eq(request.departmentId()))).thenReturn(Optional.of(department));
    }

    private WorkOrder lifecycleWorkOrder(UUID id,
                                         WorkType workType,
                                         WorkOrderStatus status,
                                         UUID warehouseId,
                                         UUID replacementEquipmentId) {
        WorkOrder workOrder = new WorkOrder();
        ReflectionTestUtils.setField(workOrder, "id", id);
        workOrder.setNumber("WO-LIFE-" + id);
        workOrder.setTitle("Lifecycle test");
        workOrder.setEquipmentId(UUID.randomUUID());
        workOrder.setDepartmentId(UUID.randomUUID());
        workOrder.setType(WorkOrderType.PLANNED);
        workOrder.setWorkType(workType);
        workOrder.setStatus(status);
        workOrder.setCreatedById(UUID.randomUUID());
        workOrder.setWarehouseId(warehouseId);
        workOrder.setReplacementEquipmentId(replacementEquipmentId);
        return workOrder;
    }

    private MaintenanceDueEvent dueEvent(UUID id, UUID equipmentId, UUID regulationId, UUID ruleId) {
        MaintenanceDueEvent event = new MaintenanceDueEvent();
        ReflectionTestUtils.setField(event, "id", id);
        event.setEquipmentId(equipmentId);
        event.setRegulationId(regulationId);
        event.setEquipmentMaintenanceRuleId(ruleId);
        event.setStatus(MaintenanceDueEventStatus.WORK_ORDER_CREATED);
        event.setDueStatus(MaintenanceDueStatus.DUE);
        event.setTriggerSource(MaintenanceTriggerSource.METER_READING);
        event.setCycleKey("EQ:RULE:METER:ENGINE_HOURS:500");
        event.setDueAt(java.time.Instant.parse("2026-06-03T10:00:00Z"));
        event.setDetectedAt(java.time.Instant.parse("2026-06-03T09:55:00Z"));
        event.setMeterType(MeterType.ENGINE_HOURS);
        event.setMeterAnchorValue(0.0);
        event.setMeterInterval(500.0);
        event.setMeterCurrentValue(520.0);
        event.setMeterRemaining(0.0);
        return event;
    }

    private WarehouseEquipmentItem warehouseItem(UUID warehouseId, UUID equipmentId, WarehouseEquipmentStatus status) {
        WarehouseEquipmentItem item = new WarehouseEquipmentItem();
        item.setWarehouseId(warehouseId);
        item.setEquipmentId(equipmentId);
        item.setStatus(status);
        item.setActive(true);
        item.setDeleted(false);
        return item;
    }

    private WorkOrderTask workOrderTask(WorkOrder workOrder, String title, TaskExecutionStatus status) {
        WorkOrderTask task = new WorkOrderTask();
        ReflectionTestUtils.setField(task, "id", UUID.randomUUID());
        task.setWorkOrder(workOrder);
        task.setTitle(title);
        task.setStatus(status);
        return task;
    }

    private SafetyPermit safetyPermit(UUID workOrderId, SafetyPermitStatus status) {
        SafetyPermit permit = new SafetyPermit();
        permit.setId(UUID.randomUUID());
        permit.setWorkOrderId(workOrderId);
        permit.setPermitNumber("SP-" + workOrderId.toString().substring(0, 8));
        permit.setStatus(status);
        return permit;
    }

    private CompletionAct completionAct(UUID workOrderId, boolean signed) {
        CompletionAct act = new CompletionAct();
        act.setId(UUID.randomUUID());
        act.setWorkOrderId(workOrderId);
        act.setActNumber("CA-" + workOrderId.toString().substring(0, 8));
        if (signed) {
            act.setSignedById(UUID.randomUUID());
            act.setSignedAt(java.time.Instant.now());
        }
        return act;
    }

    private void stubLifecycleDtoLookups(WorkOrder workOrder) {
        when(equipmentRepository.findById(workOrder.getEquipmentId())).thenReturn(Optional.empty());
        when(departmentRepository.findById(workOrder.getDepartmentId())).thenReturn(Optional.empty());
        if (workOrder.getReplacementEquipmentId() != null) {
            when(equipmentRepository.findById(workOrder.getReplacementEquipmentId())).thenReturn(Optional.empty());
        }
    }
}
