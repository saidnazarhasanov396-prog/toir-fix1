package com.toir.service.maintenanceworkspace;

import com.toir.dto.maintenanceworkspace.MaintenanceDispatcherActionRequest;
import com.toir.dto.maintenanceworkspace.MaintenanceDispatcherActionResponse;
import com.toir.dto.maintenanceworkspace.MaintenanceDispatcherQueues;
import com.toir.dto.maintenanceworkspace.MaintenanceWorkspaceFilter;
import com.toir.entity.defects.Defect;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.repair.RepairRequest;
import com.toir.entity.users.BrigadeMember;
import com.toir.enums.*;
import com.toir.exception.RestException;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.defects.DefectRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.projects.BrigadeMemberRepository;
import com.toir.repository.repair.RepairRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MaintenanceDispatcherServiceTest {

    @Mock
    RepairRequestRepository repairRequestRepository;

    @Mock
    DefectRepository defectRepository;

    @Mock
    WorkOrderRepository workOrderRepository;

    @Mock
    BrigadeMemberRepository brigadeMemberRepository;

    @Mock
    EquipmentRepository equipmentRepository;

    @InjectMocks
    MaintenanceDispatcherService service;


    @BeforeEach
    void setUp() {
        lenient().when(repairRequestRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc())
                .thenReturn(List.of());

        lenient().when(defectRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc())
                .thenReturn(List.of());

        lenient().when(workOrderRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc())
                .thenReturn(List.of());

        lenient().when(equipmentRepository.findAllByIdInAndIsDeletedFalse(anyCollection()))
                .thenReturn(List.of());
    }

    @Test
    void queuesFiltersItemsAndRecomputesSummary() {
        UUID departmentId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        RepairRequest matchingEmergency = repairRequest(
                departmentId,
                equipmentId,
                "RR-1",
                "Pump seal leak",
                PriorityLevel.CRITICAL,
                RequestStatus.OPEN,
                Instant.parse("2026-07-10T09:00:00Z"),
                "line stopped"
        );
        RepairRequest otherDepartment = repairRequest(
                UUID.randomUUID(),
                equipmentId,
                "RR-2",
                "Pump seal leak",
                PriorityLevel.CRITICAL,
                RequestStatus.OPEN,
                Instant.parse("2026-07-10T09:00:00Z"),
                "line stopped"
        );
        Defect matchingTextButWrongType = defect(equipmentId, "DF-1", "Pump seal leak", "HIGH");
        WorkOrder matchingTextButWrongTypeWorkOrder = blockedWorkOrder(
                departmentId,
                equipmentId,
                "WO-1",
                "Pump seal leak",
                PriorityLevel.CRITICAL,
                WorkOrderStatus.SUSPENDED,
                Instant.parse("2026-07-10T09:00:00Z")
        );
        when(repairRequestRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc())
                .thenReturn(List.of(matchingEmergency, otherDepartment));
        when(defectRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc())
                .thenReturn(List.of(matchingTextButWrongType));
        when(workOrderRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc())
                .thenReturn(List.of(matchingTextButWrongTypeWorkOrder));

        MaintenanceDispatcherService service = new MaintenanceDispatcherService(
                repairRequestRepository,
                defectRepository,
                workOrderRepository,
                brigadeMemberRepository,
                equipmentRepository
        );
        MaintenanceWorkspaceFilter filter = new MaintenanceWorkspaceFilter(
                "pump",
                departmentId,
                equipmentId,
                "CRITICAL",
                "OPEN",
                "REPAIR_REQUEST",
                Instant.parse("2026-07-01T00:00:00Z"),
                Instant.parse("2026-07-31T23:59:59Z"),
                null,
                null,
                null
        );

        MaintenanceDispatcherQueues queues = service.queues(filter);

        assertThat(queues.summary().total()).isEqualTo(1);
        assertThat(queues.summary().emergencyRepairRequests()).isEqualTo(1);
        assertThat(queues.emergencyRepairRequests()).extracting("id").containsExactly(matchingEmergency.getId());
        assertThat(queues.newDefects()).isEmpty();
        assertThat(queues.blockedWorkOrders()).isEmpty();
    }


    @Test
    void assignAppliesRepairRequestOwner() {
        UUID ownerId = UUID.randomUUID();
        RepairRequest request = repairRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "RR-ASSIGN",
                "Assign me",
                PriorityLevel.HIGH,
                RequestStatus.OPEN,
                Instant.parse("2026-07-10T09:00:00Z"),
                null
        );
        request.setAssignedToId(null);
        when(repairRequestRepository.findByIdAndIsDeletedFalse(request.getId())).thenReturn(Optional.of(request));
        when(repairRequestRepository.save(request)).thenReturn(request);
        MaintenanceDispatcherService service = service();

        MaintenanceDispatcherActionResponse response = service.assign(new MaintenanceDispatcherActionRequest(
                "REPAIR_REQUEST",
                request.getId(),
                ownerId,
                "Assign to shift master"
        ));

        assertThat(request.getAssignedToId()).isEqualTo(ownerId);
        assertThat(response.status()).isEqualTo("APPLIED");
        assertThat(response.objectId()).isEqualTo(request.getId());
        verify(repairRequestRepository).save(request);
    }

    @Test
    void assignAppliesWorkOrderPerformerByUserId() {
        UUID ownerId = UUID.randomUUID();
        WorkOrder workOrder = blockedWorkOrder(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "WO-ASSIGN",
                "Assign performer",
                PriorityLevel.HIGH,
                WorkOrderStatus.APPROVED,
                Instant.parse("2026-07-10T09:00:00Z")
        );
        BrigadeMember member = new BrigadeMember();
        member.setId(UUID.randomUUID());
        member.setUserId(ownerId);
        when(workOrderRepository.findByIdAndIsDeletedFalse(workOrder.getId())).thenReturn(Optional.of(workOrder));
        when(brigadeMemberRepository.findAllByUserIdAndIsDeletedFalse(ownerId)).thenReturn(List.of(member));
        when(workOrderRepository.save(workOrder)).thenReturn(workOrder);
        MaintenanceDispatcherService service = service();

        MaintenanceDispatcherActionResponse response = service.assign(new MaintenanceDispatcherActionRequest(
                "WORK_ORDER",
                workOrder.getId(),
                ownerId,
                "Assign performer"
        ));

        assertThat(workOrder.getPerformer()).isSameAs(member);
        assertThat(response.status()).isEqualTo("APPLIED");
        verify(workOrderRepository).save(workOrder);
    }

    @Test
    void escalateRaisesRepairRequestPriorityAndReason() {
        RepairRequest request = repairRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "RR-ESC",
                "Escalate me",
                PriorityLevel.MEDIUM,
                RequestStatus.OPEN,
                Instant.parse("2026-07-10T09:00:00Z"),
                null
        );
        when(repairRequestRepository.findByIdAndIsDeletedFalse(request.getId())).thenReturn(Optional.of(request));
        when(repairRequestRepository.save(request)).thenReturn(request);
        MaintenanceDispatcherService service = service();

        MaintenanceDispatcherActionResponse response = service.escalate(new MaintenanceDispatcherActionRequest(
                "REPAIR_REQUEST",
                request.getId(),
                null,
                "SLA breach"
        ));

        assertThat(request.getPriority()).isEqualTo(PriorityLevel.EMERGENCY);
        assertThat(request.getEmergencyReason()).isEqualTo("SLA breach");
        assertThat(response.status()).isEqualTo("APPLIED");
        verify(repairRequestRepository).save(request);
    }

    @Test
    void assignRejectsDefectsBecauseTheyHaveNoOwnerField() {
        MaintenanceDispatcherService service = service();

        assertThatThrownBy(() -> service.assign(new MaintenanceDispatcherActionRequest(
                "DEFECT",
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Assign defect"
        ))).isInstanceOf(RestException.class)
                .hasMessageContaining("Defects do not support owner assignment");
    }

    @Test
    void queuesShouldAttachEquipmentNamesToAllDispatcherItemTypes() {
        UUID repairEquipmentId = UUID.randomUUID();
        UUID defectEquipmentId = UUID.randomUUID();
        UUID workOrderEquipmentId = UUID.randomUUID();

        RepairRequest repairRequest = repairRequest(UUID.randomUUID(), repairEquipmentId);
        repairRequest.setEmergencyReason("Emergency reason");
        repairRequest.setAssignedToId(UUID.randomUUID());
        repairRequest.setTargetCompletionAt(Instant.now().plus(1, ChronoUnit.DAYS));

        Defect defect = defect(UUID.randomUUID(), defectEquipmentId, "DEF-TEST-1", "Open defect");

        WorkOrder workOrder = suspendedWorkOrder(UUID.randomUUID(), workOrderEquipmentId);

        when(repairRequestRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc())
                .thenReturn(List.of(repairRequest));
        when(defectRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc())
                .thenReturn(List.of(defect));
        when(workOrderRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc())
                .thenReturn(List.of(workOrder));
        when(equipmentRepository.findAllByIdInAndIsDeletedFalse(anyCollection()))
                .thenReturn(List.of(
                        equipment(repairEquipmentId, "Repair equipment"),
                        equipment(defectEquipmentId, "Defect equipment"),
                        equipment(workOrderEquipmentId, "Work order equipment")
                ));

        MaintenanceDispatcherQueues result = service.queues(MaintenanceWorkspaceFilter.empty());

        assertThat(result.emergencyRepairRequests())
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.objectType()).isEqualTo("REPAIR_REQUEST");
                    assertThat(item.equipmentId()).isEqualTo(repairEquipmentId);
                    assertThat(item.equipmentName()).isEqualTo("Repair equipment");
                });

        assertThat(result.newDefects())
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.objectType()).isEqualTo("DEFECT");
                    assertThat(item.equipmentId()).isEqualTo(defectEquipmentId);
                    assertThat(item.equipmentName()).isEqualTo("Defect equipment");
                });

        assertThat(result.blockedWorkOrders())
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.objectType()).isEqualTo("WORK_ORDER");
                    assertThat(item.equipmentId()).isEqualTo(workOrderEquipmentId);
                    assertThat(item.equipmentName()).isEqualTo("Work order equipment");
                });

        assertThat(result.summary().total()).isEqualTo(3);
        assertThat(result.summary().emergencyRepairRequests()).isEqualTo(1);
        assertThat(result.summary().newDefects()).isEqualTo(1);
        assertThat(result.summary().blockedWorkOrders()).isEqualTo(1);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<UUID>> idsCaptor = ArgumentCaptor.forClass(Collection.class);

        verify(equipmentRepository).findAllByIdInAndIsDeletedFalse(idsCaptor.capture());
        assertThat(idsCaptor.getValue())
                .containsExactlyInAnyOrder(repairEquipmentId, defectEquipmentId, workOrderEquipmentId);
    }

    @Test
    void queuesShouldNotFailWhenEquipmentIsMissing() {
        UUID equipmentId = UUID.randomUUID();
        Defect defect = defect(UUID.randomUUID(), equipmentId, "DEF-MISSING-EQ", "Defect without equipment row");

        when(defectRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc())
                .thenReturn(List.of(defect));
        when(equipmentRepository.findAllByIdInAndIsDeletedFalse(anyCollection()))
                .thenReturn(List.of());

        MaintenanceDispatcherQueues result = service.queues(MaintenanceWorkspaceFilter.empty());

        assertThat(result.newDefects())
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.equipmentId()).isEqualTo(equipmentId);
                    assertThat(item.equipmentName()).isNull();
                });

        assertThat(result.summary().total()).isEqualTo(1);
        assertThat(result.summary().newDefects()).isEqualTo(1);
    }

    @Test
    void queuesShouldFilterByEquipmentIdAndRecalculateSummary() {
        UUID equipmentA = UUID.randomUUID();
        UUID equipmentB = UUID.randomUUID();

        Defect first = defect(UUID.randomUUID(), equipmentA, "DEF-A", "First defect");
        Defect second = defect(UUID.randomUUID(), equipmentB, "DEF-B", "Second defect");

        when(defectRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc())
                .thenReturn(List.of(first, second));
        when(equipmentRepository.findAllByIdInAndIsDeletedFalse(anyCollection()))
                .thenReturn(List.of(
                        equipment(equipmentA, "Equipment A"),
                        equipment(equipmentB, "Equipment B")
                ));

        MaintenanceWorkspaceFilter filter = new MaintenanceWorkspaceFilter(
                null,
                null,
                equipmentA,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        MaintenanceDispatcherQueues result = service.queues(filter);

        assertThat(result.newDefects())
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.code()).isEqualTo("DEF-A");
                    assertThat(item.equipmentId()).isEqualTo(equipmentA);
                    assertThat(item.equipmentName()).isEqualTo("Equipment A");
                });

        assertThat(result.summary().total()).isEqualTo(1);
        assertThat(result.summary().newDefects()).isEqualTo(1);
    }

    @Test
    void queuesShouldSearchByEquipmentName() {
        UUID pumpId = UUID.randomUUID();
        UUID compressorId = UUID.randomUUID();

        Defect pumpDefect = defect(UUID.randomUUID(), pumpId, "DEF-PUMP", "Bearing issue");
        Defect compressorDefect = defect(UUID.randomUUID(), compressorId, "DEF-COMP", "Oil issue");

        when(defectRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc())
                .thenReturn(List.of(pumpDefect, compressorDefect));
        when(equipmentRepository.findAllByIdInAndIsDeletedFalse(anyCollection()))
                .thenReturn(List.of(
                        equipment(pumpId, "Main Pump 001"),
                        equipment(compressorId, "Air Compressor 002")
                ));

        MaintenanceWorkspaceFilter filter = new MaintenanceWorkspaceFilter(
                "main pump",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        MaintenanceDispatcherQueues result = service.queues(filter);

        assertThat(result.newDefects())
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.code()).isEqualTo("DEF-PUMP");
                    assertThat(item.equipmentName()).isEqualTo("Main Pump 001");
                });

        assertThat(result.summary().total()).isEqualTo(1);
    }

    @Test
    void assignShouldUpdateRepairRequestOwner() {
        UUID requestId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        RepairRequest repairRequest = repairRequest(requestId, UUID.randomUUID());
        repairRequest.setAssignedToId(null);

        when(repairRequestRepository.findByIdAndIsDeletedFalse(requestId))
                .thenReturn(Optional.of(repairRequest));
        when(repairRequestRepository.save(repairRequest))
                .thenReturn(repairRequest);

        MaintenanceDispatcherActionRequest request =
                new MaintenanceDispatcherActionRequest("repair_request", requestId, ownerId, "assign owner");

        MaintenanceDispatcherActionResponse response = service.assign(request);

        assertThat(repairRequest.getAssignedToId()).isEqualTo(ownerId);
        assertThat(response.action()).isEqualTo("ASSIGN");
        assertThat(response.objectType()).isEqualTo("REPAIR_REQUEST");
        assertThat(response.objectId()).isEqualTo(requestId);
        assertThat(response.ownerId()).isEqualTo(ownerId);
    }

    @Test
    void assignShouldRejectDefectOwnerAssignment() {
        MaintenanceDispatcherActionRequest request =
                new MaintenanceDispatcherActionRequest("DEFECT", UUID.randomUUID(), UUID.randomUUID(), "assign defect");

        assertThatThrownBy(() -> service.assign(request))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Defects do not support owner assignment");

        verifyNoInteractions(brigadeMemberRepository);
    }

    @Test
    void escalateShouldSetDefectSeverityToCritical() {
        UUID defectId = UUID.randomUUID();
        Defect defect = defect(defectId, UUID.randomUUID(), "DEF-ESC", "Escalated defect");
        defect.setSeverity("Очень серьезно");

        when(defectRepository.findByIdAndIsDeletedFalse(defectId))
                .thenReturn(Optional.of(defect));
        when(defectRepository.save(defect))
                .thenReturn(defect);

        MaintenanceDispatcherActionRequest request =
                new MaintenanceDispatcherActionRequest("defect", defectId, null, "critical issue");

        MaintenanceDispatcherActionResponse response = service.escalate(request);

        assertThat(defect.getSeverity()).isEqualTo("CRITICAL");
        assertThat(response.action()).isEqualTo("ESCALATE");
        assertThat(response.objectType()).isEqualTo("DEFECT");
        assertThat(response.objectId()).isEqualTo(defectId);
    }

    @Test
    void escalateShouldSetRepairRequestPriorityAndEmergencyReason() {
        UUID requestId = UUID.randomUUID();

        RepairRequest repairRequest = repairRequest(requestId, UUID.randomUUID());
        repairRequest.setPriority(PriorityLevel.MEDIUM);
        repairRequest.setEmergencyReason(null);

        when(repairRequestRepository.findByIdAndIsDeletedFalse(requestId))
                .thenReturn(Optional.of(repairRequest));
        when(repairRequestRepository.save(repairRequest))
                .thenReturn(repairRequest);

        MaintenanceDispatcherActionRequest request =
                new MaintenanceDispatcherActionRequest("repair_request", requestId, null, "Need urgent repair");

        MaintenanceDispatcherActionResponse response = service.escalate(request);

        assertThat(repairRequest.getPriority()).isEqualTo(PriorityLevel.EMERGENCY);
        assertThat(repairRequest.getEmergencyReason()).isEqualTo("Need urgent repair");
        assertThat(response.action()).isEqualTo("ESCALATE");
        assertThat(response.objectType()).isEqualTo("REPAIR_REQUEST");
    }

    private RepairRequest repairRequest(UUID id, UUID equipmentId) {
        RepairRequest request = new RepairRequest();
        setBaseFields(request, id);

        request.setNumber("RR-" + id.toString().substring(0, 8));
        request.setTitle("Repair request");
        request.setDescription("Repair request description");
        request.setEquipmentId(equipmentId);
        request.setDepartmentId(UUID.randomUUID());
        request.setLocationId(UUID.randomUUID());
        request.setReporterId(UUID.randomUUID());
        request.setAssignedToId(UUID.randomUUID());
        request.setPriority(PriorityLevel.MEDIUM);
        request.setCriticality(CriticalityLevel.MEDIUM);
        request.setStatus(RequestStatus.OPEN);
        request.setDetectedAt(Instant.now().minus(2, ChronoUnit.HOURS));
        request.setTargetCompletionAt(Instant.now().plus(1, ChronoUnit.DAYS));

        return request;
    }

    private Defect defect(UUID id, UUID equipmentId, String code, String title) {
        Defect defect = new Defect();
        setBaseFields(defect, id);

        defect.setCode(code);
        defect.setTitle(title);
        defect.setDescription(title + " description");
        defect.setEquipmentId(equipmentId);
        defect.setSeverity("Очень серьезно");
        defect.setStatus(DefectStatus.OPEN);
        defect.setDetectedAt(Instant.now().minus(3, ChronoUnit.HOURS));

        return defect;
    }

    private WorkOrder suspendedWorkOrder(UUID id, UUID equipmentId) {
        WorkOrder workOrder = new WorkOrder();
        setBaseFields(workOrder, id);

        workOrder.setNumber("WO-" + id.toString().substring(0, 8));
        workOrder.setTitle("Suspended work order");
        workOrder.setEquipmentId(equipmentId);
        workOrder.setDepartmentId(UUID.randomUUID());
        workOrder.setLocationId(UUID.randomUUID());
        workOrder.setPriority(PriorityLevel.MEDIUM);
        workOrder.setStatus(WorkOrderStatus.SUSPENDED);
        workOrder.setRepairActRequired(false);
        workOrder.setStoppageActRequired(false);

        return workOrder;
    }

    private Equipment equipment(UUID id, String name) {
        Equipment equipment = new Equipment();
        ReflectionTestUtils.setField(equipment, "id", id);
        ReflectionTestUtils.setField(equipment, "name", name);
        return equipment;
    }

    private void setBaseFields(Object entity, UUID id) {
        ReflectionTestUtils.setField(entity, "id", id);
        ReflectionTestUtils.setField(entity, "createdAt", Instant.now().minus(4, ChronoUnit.HOURS));
        ReflectionTestUtils.setField(entity, "updatedAt", Instant.now().minus(1, ChronoUnit.HOURS));
        ReflectionTestUtils.setField(entity, "isDeleted", false);
    }

    private MaintenanceDispatcherService service() {
        return new MaintenanceDispatcherService(
                repairRequestRepository,
                defectRepository,
                workOrderRepository,
                brigadeMemberRepository,
                equipmentRepository
        );
    }

    private RepairRequest repairRequest(
            UUID departmentId,
            UUID equipmentId,
            String number,
            String title,
            PriorityLevel priority,
            RequestStatus status,
            Instant targetCompletionAt,
            String emergencyReason
    ) {
        RepairRequest request = new RepairRequest();
        request.setId(UUID.randomUUID());
        request.setNumber(number);
        request.setTitle(title);
        request.setDescription(title);
        request.setDepartmentId(departmentId);
        request.setEquipmentId(equipmentId);
        request.setReporterId(UUID.randomUUID());
        request.setAssignedToId(UUID.randomUUID());
        request.setPriority(priority);
        request.setStatus(status);
        request.setDetectedAt(targetCompletionAt.minusSeconds(3600));
        request.setTargetCompletionAt(targetCompletionAt);
        request.setEmergencyReason(emergencyReason);
        return request;
    }

    private Defect defect(UUID equipmentId, String code, String title, String severity) {
        Defect defect = new Defect();
        defect.setId(UUID.randomUUID());
        defect.setCode(code);
        defect.setTitle(title);
        defect.setDescription(title);
        defect.setEquipmentId(equipmentId);
        defect.setSeverity(severity);
        defect.setStatus(DefectStatus.OPEN);
        defect.setDetectedAt(Instant.parse("2026-07-10T08:00:00Z"));
        return defect;
    }

    private WorkOrder blockedWorkOrder(
            UUID departmentId,
            UUID equipmentId,
            String number,
            String title,
            PriorityLevel priority,
            WorkOrderStatus status,
            Instant dueAt
    ) {
        WorkOrder workOrder = new WorkOrder();
        workOrder.setId(UUID.randomUUID());
        workOrder.setNumber(number);
        workOrder.setTitle(title);
        workOrder.setDepartmentId(departmentId);
        workOrder.setEquipmentId(equipmentId);
        workOrder.setPriority(priority);
        workOrder.setStatus(status);
        workOrder.setCreatedAt(dueAt.minusSeconds(7200));
        workOrder.setEndPlannedAt(dueAt);
        return workOrder;
    }
}
