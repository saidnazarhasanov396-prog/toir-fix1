package com.toir.service.defects;

import com.toir.dto.defect.DefectRequest;
import com.toir.dto.defect.DefectResponse;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.defects.Defect;
import com.toir.entity.repair.RepairRequest;
import com.toir.enums.DefectStatus;
import com.toir.enums.PriorityLevel;
import com.toir.enums.RequestStatus;
import com.toir.enums.WorkOrderStatus;
import com.toir.enums.WorkOrderType;
import com.toir.enums.WorkType;
import com.toir.exception.RestException;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.defects.DefectRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.repair.RepairRequestRepository;
import com.toir.util.AuditBuilderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefectServiceTest {

    @Mock
    DefectRepository repository;

    @Mock
    EquipmentRepository equipmentRepository;

    @Mock
    RepairRequestRepository repairRequestRepository;

    @Mock
    WorkOrderRepository workOrderRepository;

    @Mock
    AuditBuilderService auditBuilderService;

    @InjectMocks
    DefectService service;

    @Test
    void findAllFiltersByRepairRequestId() {
        UUID repairRequestId = UUID.randomUUID();
        PageRequest pageRequest = PageRequest.of(0, 20);
        when(repository.searchPaginated(null, repairRequestId, null, pageRequest))
                .thenReturn(new PageImpl<>(List.of(), pageRequest, 0));

        var result = service.search(null, repairRequestId, 0, 20, null);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).isEmpty();
        verify(repository).searchPaginated(eq(null), eq(repairRequestId), eq(null), eq(pageRequest));
    }

    @Test
    void findAllWithoutRepairRequestIdKeepsExistingBehavior() {
        PageRequest pageRequest = PageRequest.of(0, 20);
        when(repository.searchPaginated(null, null, null, pageRequest))
                .thenReturn(new PageImpl<>(List.of(), pageRequest, 0));

        var result = service.search(null, null, 0, 20, null);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).isEmpty();
        verify(repository).searchPaginated(eq(null), eq(null), eq(null), eq(pageRequest));
    }

    @Test
    void findAllCombinesRepairRequestIdAndSearchIfSearchExists() {
        UUID repairRequestId = UUID.randomUUID();
        String search = "leak";
        PageRequest pageRequest = PageRequest.of(0, 20);
        when(repository.searchPaginated(null, repairRequestId, search, pageRequest))
                .thenReturn(new PageImpl<>(List.of(), pageRequest, 0));

        var result = service.search(null, repairRequestId, 0, 20, search);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).isEmpty();
        verify(repository).searchPaginated(eq(null), eq(repairRequestId), eq(search), eq(pageRequest));
    }

    @Test
    void createWithValidRepairRequestSucceeds() {
        UUID equipmentId = UUID.randomUUID();
        UUID repairRequestId = UUID.randomUUID();
        when(repository.maxSequenceByCodePrefix(anyString())).thenReturn(0L);
        when(repository.existsByCode(anyString())).thenReturn(false);
        when(repairRequestRepository.findByIdAndIsDeletedFalse(repairRequestId))
                .thenReturn(Optional.of(repairRequest(repairRequestId, RequestStatus.OPEN)));
        when(repository.save(any(Defect.class))).thenAnswer(invocation -> {
            Defect defect = invocation.getArgument(0);
            ReflectionTestUtils.setField(defect, "id", UUID.randomUUID());
            return defect;
        });
        when(equipmentRepository.findAllByIdInAndIsDeletedFalse(List.of(equipmentId))).thenReturn(List.of());
        when(repairRequestRepository.findAllByIdInAndIsDeletedFalse(List.of(repairRequestId)))
                .thenReturn(List.of(repairRequest(repairRequestId, RequestStatus.OPEN)));
        when(workOrderRepository.findAllByDefectIdInAndIsDeletedFalseOrderByUpdatedAtDesc(any()))
                .thenReturn(List.of());

        DefectResponse response = service.create(request(equipmentId, repairRequestId));

        ArgumentCaptor<Defect> defectCaptor = ArgumentCaptor.forClass(Defect.class);
        verify(repository).save(defectCaptor.capture());
        assertThat(defectCaptor.getValue().getRepairRequestId()).isEqualTo(repairRequestId);
        assertThat(response.repairRequestId()).isEqualTo(repairRequestId);
        assertThat(response.requestId()).isEqualTo(repairRequestId);
    }

    @Test
    void createWithUnknownRepairRequestReturns404() {
        UUID equipmentId = UUID.randomUUID();
        UUID repairRequestId = UUID.randomUUID();
        when(repository.maxSequenceByCodePrefix(anyString())).thenReturn(0L);
        when(repository.existsByCode(anyString())).thenReturn(false);
        when(repairRequestRepository.findByIdAndIsDeletedFalse(repairRequestId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(request(equipmentId, repairRequestId)))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(ex.getMessage()).contains("Repair request not found");
                });
        verify(repository, never()).save(any(Defect.class));
    }

    @Test
    void createWithClosedRepairRequestReturns400() {
        UUID equipmentId = UUID.randomUUID();
        UUID repairRequestId = UUID.randomUUID();
        when(repository.maxSequenceByCodePrefix(anyString())).thenReturn(0L);
        when(repository.existsByCode(anyString())).thenReturn(false);
        when(repairRequestRepository.findByIdAndIsDeletedFalse(repairRequestId))
                .thenReturn(Optional.of(repairRequest(repairRequestId, RequestStatus.CLOSED)));

        assertThatThrownBy(() -> service.create(request(equipmentId, repairRequestId)))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("Cannot link defect to repair request");
                });
        verify(repository, never()).save(any(Defect.class));
    }

    @Test
    void createWithoutRepairRequestStillWorks() {
        UUID equipmentId = UUID.randomUUID();
        when(repository.maxSequenceByCodePrefix(anyString())).thenReturn(0L);
        when(repository.existsByCode(anyString())).thenReturn(false);
        when(repository.save(any(Defect.class))).thenAnswer(invocation -> {
            Defect defect = invocation.getArgument(0);
            ReflectionTestUtils.setField(defect, "id", UUID.randomUUID());
            return defect;
        });
        when(equipmentRepository.findAllByIdInAndIsDeletedFalse(List.of(equipmentId))).thenReturn(List.of());
        when(workOrderRepository.findAllByDefectIdInAndIsDeletedFalseOrderByUpdatedAtDesc(any()))
                .thenReturn(List.of());

        DefectResponse response = service.create(request(equipmentId, null));

        ArgumentCaptor<Defect> defectCaptor = ArgumentCaptor.forClass(Defect.class);
        verify(repository).save(defectCaptor.capture());
        assertThat(defectCaptor.getValue().getRepairRequestId()).isNull();
        assertThat(response.repairRequestId()).isNull();
        assertThat(response.requestId()).isNull();
    }

    @Test
    void updateWithUnknownRepairRequestReturns404() {
        UUID defectId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        UUID unknownRepairRequestId = UUID.randomUUID();
        Defect defect = Defect.builder()
                .code("DEF-2026-0001")
                .title("Existing defect")
                .description("Existing description")
                .equipmentId(equipmentId)
                .build();
        defect.setId(defectId);
        when(repository.findByIdAndIsDeletedFalse(defectId)).thenReturn(Optional.of(defect));
        when(repairRequestRepository.findByIdAndIsDeletedFalse(unknownRepairRequestId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(defectId, request(equipmentId, unknownRepairRequestId)))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(ex.getMessage()).contains("Repair request not found");
                });
        verify(repository, never()).save(any(Defect.class));
    }

    @Test
    void responseIncludesRepairRequestObject() {
        UUID defectId = UUID.randomUUID();
        UUID repairRequestId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        Defect defect = Defect.builder()
                .code("DEF-2026-0002")
                .title("Leak")
                .description("Oil leak detected")
                .equipmentId(equipmentId)
                .repairRequestId(repairRequestId)
                .status(DefectStatus.OPEN)
                .severity("HIGH")
                .build();
        defect.setId(defectId);
        when(repository.findByIdAndIsDeletedFalse(defectId)).thenReturn(Optional.of(defect));
        when(equipmentRepository.findAllByIdInAndIsDeletedFalse(List.of(equipmentId))).thenReturn(List.of());
        when(repairRequestRepository.findAllByIdInAndIsDeletedFalse(List.of(repairRequestId)))
                .thenReturn(List.of(repairRequest(repairRequestId, RequestStatus.OPEN)));
        when(workOrderRepository.findAllByDefectIdInAndIsDeletedFalseOrderByUpdatedAtDesc(List.of(defectId)))
                .thenReturn(List.of());

        DefectResponse response = service.findById(defectId);

        assertThat(response.repairRequest()).isNotNull();
        assertThat(response.repairRequest().id()).isEqualTo(repairRequestId);
        assertThat(response.repairRequest().status()).isEqualTo(RequestStatus.OPEN);
    }

    @Test
    void responseIncludesLinkedWorkOrders() {
        UUID defectId = UUID.randomUUID();
        UUID repairRequestId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        Defect defect = Defect.builder()
                .code("DEF-2026-0003")
                .title("Bearing")
                .description("Overheat")
                .equipmentId(equipmentId)
                .repairRequestId(repairRequestId)
                .status(DefectStatus.OPEN)
                .severity("MEDIUM")
                .build();
        defect.setId(defectId);
        WorkOrder linkedWorkOrder = workOrder(defectId, repairRequestId);
        when(repository.findByIdAndIsDeletedFalse(defectId)).thenReturn(Optional.of(defect));
        when(equipmentRepository.findAllByIdInAndIsDeletedFalse(List.of(equipmentId))).thenReturn(List.of());
        when(repairRequestRepository.findAllByIdInAndIsDeletedFalse(List.of(repairRequestId)))
                .thenReturn(List.of(repairRequest(repairRequestId, RequestStatus.OPEN)));
        when(workOrderRepository.findAllByDefectIdInAndIsDeletedFalseOrderByUpdatedAtDesc(List.of(defectId)))
                .thenReturn(List.of(linkedWorkOrder));

        DefectResponse response = service.findById(defectId);

        assertThat(response.linkedWorkOrders()).hasSize(1);
        assertThat(response.linkedWorkOrders().getFirst().id()).isEqualTo(linkedWorkOrder.getId());
        assertThat(response.linkedWorkOrders().getFirst().number()).isEqualTo(linkedWorkOrder.getNumber());
    }

    @Test
    void responseWithNoRepairRequestReturnsNullAndEmptyList() {
        UUID defectId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        Defect defect = Defect.builder()
                .code("DEF-2026-0004")
                .title("Vibration")
                .description("Unstable behavior")
                .equipmentId(equipmentId)
                .repairRequestId(null)
                .status(DefectStatus.OPEN)
                .severity("LOW")
                .build();
        defect.setId(defectId);
        when(repository.findByIdAndIsDeletedFalse(defectId)).thenReturn(Optional.of(defect));
        when(equipmentRepository.findAllByIdInAndIsDeletedFalse(List.of(equipmentId))).thenReturn(List.of());
        when(workOrderRepository.findAllByDefectIdInAndIsDeletedFalseOrderByUpdatedAtDesc(List.of(defectId)))
                .thenReturn(List.of());

        DefectResponse response = service.findById(defectId);

        assertThat(response.repairRequest()).isNull();
        assertThat(response.linkedWorkOrders()).isEmpty();
    }

    private DefectRequest request(UUID equipmentId, UUID repairRequestId) {
        return new DefectRequest(
                null,
                "Bearing overheating",
                "Temperature threshold exceeded",
                equipmentId,
                repairRequestId,
                "MECHANICAL",
                "HIGH",
                "Wear",
                "Insufficient lubrication"
        );
    }

    private RepairRequest repairRequest(UUID id, RequestStatus status) {
        RepairRequest repairRequest = new RepairRequest();
        repairRequest.setId(id);
        repairRequest.setNumber("RR-2026-1001");
        repairRequest.setPriority(PriorityLevel.MEDIUM);
        repairRequest.setTitle("Repair request");
        repairRequest.setDescription("Short description");
        repairRequest.setStatus(status);
        return repairRequest;
    }

    private WorkOrder workOrder(UUID defectId, UUID repairRequestId) {
        WorkOrder workOrder = new WorkOrder();
        workOrder.setId(UUID.randomUUID());
        workOrder.setNumber("WO-2026-1001");
        workOrder.setTitle("Linked work");
        workOrder.setEquipmentId(UUID.randomUUID());
        workOrder.setDepartmentId(UUID.randomUUID());
        workOrder.setCreatedById(UUID.randomUUID());
        workOrder.setType(WorkOrderType.PLANNED);
        workOrder.setWorkType(WorkType.REPAIR);
        workOrder.setStatus(WorkOrderStatus.APPROVED);
        workOrder.setPriority(PriorityLevel.MEDIUM);
        workOrder.setRepairRequestId(repairRequestId);
        workOrder.setDefectId(defectId);
        return workOrder;
    }
}
