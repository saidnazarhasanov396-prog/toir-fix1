package com.toir.service.equipment;

import com.toir.dto.equipmentnode.EquipmentNodeDto;
import com.toir.entity.equipment.EquipmentNode;
import com.toir.enums.EquipmentNodeType;
import com.toir.exception.RestException;
import com.toir.repository.TechnicalDocumentRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.defects.DefectRepository;
import com.toir.repository.equipment.EquipmentNodeRepository;
import com.toir.util.AuditBuilderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EquipmentNodeServiceTest {

    @Mock
    EquipmentNodeRepository repository;

    @Mock
    DefectRepository defectRepository;

    @Mock
    TechnicalDocumentRepository technicalDocumentRepository;

    @Mock
    WorkOrderRepository workOrderRepository;

    @Mock
    AuditBuilderService auditBuilderService;

    @InjectMocks
    EquipmentNodeService service;

    @BeforeEach
    void setUp() {
        lenient().when(repository.maxSequenceByEquipmentIdAndCodePrefix(any(UUID.class), anyString())).thenReturn(0L);
        lenient().when(repository.existsByEquipmentIdAndCodeAndIsDeletedFalse(any(UUID.class), anyString())).thenReturn(false);
    }

    @Test
    void createNode_withParentId_buildsHierarchy() {
        UUID equipmentId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        EquipmentNode parent = node(parentId, equipmentId, null, "PUMP", "Pump", null);
        lenient().when(repository.existsByEquipmentIdAndCodeAndIsDeletedFalse(equipmentId, "MOTOR")).thenReturn(false);
        when(repository.findByIdAndIsDeletedFalse(parentId)).thenReturn(Optional.of(parent));
        when(repository.save(any(EquipmentNode.class))).thenAnswer(invocation -> {
            EquipmentNode saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
            return saved;
        });

        EquipmentNodeDto result = service.create(equipmentId, request(null, equipmentId, parentId, "MOTOR", "Motor", "SN-1"));

        assertThat(result.parentId()).isEqualTo(parentId);
        ArgumentCaptor<EquipmentNode> captor = ArgumentCaptor.forClass(EquipmentNode.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getParentId()).isEqualTo(parentId);
        assertThat(captor.getValue().getEquipmentId()).isEqualTo(equipmentId);
    }

    @Test
    void createNode_parentFromDifferentEquipment_returnsBadRequest() {
        UUID equipmentId = UUID.randomUUID();
        UUID otherEquipmentId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        lenient().when(repository.existsByEquipmentIdAndCodeAndIsDeletedFalse(equipmentId, "MOTOR")).thenReturn(false);
        when(repository.findByIdAndIsDeletedFalse(parentId))
                .thenReturn(Optional.of(node(parentId, otherEquipmentId, null, "OTHER", "Other", null)));

        assertThatThrownBy(() -> service.create(equipmentId, request(null, equipmentId, parentId, "MOTOR", "Motor", null)))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("same equipment");
                });
        verify(repository, never()).save(any());
    }

    @Test
    void updateNode_parentSelf_returnsBadRequest() {
        UUID equipmentId = UUID.randomUUID();
        UUID nodeId = UUID.randomUUID();
        EquipmentNode current = node(nodeId, equipmentId, null, "MOTOR", "Motor", null);
        when(repository.findByIdAndIsDeletedFalse(nodeId)).thenReturn(Optional.of(current));

        assertThatThrownBy(() -> service.update(nodeId, request(nodeId, equipmentId, nodeId, "MOTOR", "Motor", null)))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("parent itself");
                });
        verify(repository, never()).save(any());
    }

    @Test
    void updateNode_circularParent_returnsBadRequest() {
        UUID equipmentId = UUID.randomUUID();
        UUID motorId = UUID.randomUUID();
        UUID bearingId = UUID.randomUUID();
        EquipmentNode motor = node(motorId, equipmentId, null, "MOTOR", "Motor", null);
        EquipmentNode bearing = node(bearingId, equipmentId, motorId, "BEARING", "Bearing", null);
        when(repository.findByIdAndIsDeletedFalse(motorId)).thenReturn(Optional.of(motor));
        when(repository.findByIdAndIsDeletedFalse(bearingId)).thenReturn(Optional.of(bearing));

        assertThatThrownBy(() -> service.update(motorId, request(motorId, equipmentId, bearingId, "MOTOR", "Motor", null)))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("Circular");
                });
        verify(repository, never()).save(any());
    }

    @Test
    void deleteNode_withChildren_returnsConflict() {
        UUID equipmentId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        when(repository.findAllByParentIdAndIsDeletedFalse(parentId))
                .thenReturn(List.of(node(UUID.randomUUID(), equipmentId, parentId, "CHILD", "Child", null)));

        assertThatThrownBy(() -> service.delete(parentId))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getMessage()).contains("children");
                });
        verify(repository, never()).save(any());
    }

    @Test
    void deleteNode_withReferencedDefect_returnsConflict() {
        UUID equipmentId = UUID.randomUUID();
        UUID nodeId = UUID.randomUUID();
        when(repository.findAllByParentIdAndIsDeletedFalse(nodeId)).thenReturn(List.of());
        when(defectRepository.existsByEquipmentNodeIdAndIsDeletedFalse(nodeId)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(nodeId))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getMessage()).contains("referenced by active defects");
                });
        verify(repository, never()).save(any());
    }

    @Test
    void deleteNode_withReferencedTechnicalDocument_returnsConflict() {
        UUID nodeId = UUID.randomUUID();
        when(repository.findAllByParentIdAndIsDeletedFalse(nodeId)).thenReturn(List.of());
        when(defectRepository.existsByEquipmentNodeIdAndIsDeletedFalse(nodeId)).thenReturn(false);
        when(technicalDocumentRepository.existsByEquipmentNodeIdAndIsDeletedFalse(nodeId)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(nodeId))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getMessage()).contains("technical documents");
                });
        verify(repository, never()).save(any());
    }

    @Test
    void deleteNode_withReferencedWorkOrder_returnsConflict() {
        UUID nodeId = UUID.randomUUID();
        when(repository.findAllByParentIdAndIsDeletedFalse(nodeId)).thenReturn(List.of());
        when(defectRepository.existsByEquipmentNodeIdAndIsDeletedFalse(nodeId)).thenReturn(false);
        when(technicalDocumentRepository.existsByEquipmentNodeIdAndIsDeletedFalse(nodeId)).thenReturn(false);
        when(workOrderRepository.existsByEquipmentNodeIdAndIsDeletedFalse(nodeId)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(nodeId))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getMessage()).contains("work orders");
                });
        verify(repository, never()).save(any());
    }

    @Test
    void createNode_rejectsClientProvidedCode() {
        UUID equipmentId = UUID.randomUUID();

        assertThatThrownBy(() -> service.create(equipmentId,
                withClientCode(request(null, equipmentId, null, "MOTOR", "Motor", null), "MOTOR")))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("code is generated by backend and must not be provided");
                });
        verify(repository, never()).save(any());
    }

    @Test
    void createNode_sameCodeDifferentEquipment_allowed() {
        UUID equipmentId = UUID.randomUUID();
        lenient().when(repository.existsByEquipmentIdAndCodeAndIsDeletedFalse(equipmentId, "MOTOR")).thenReturn(false);
        when(repository.save(any(EquipmentNode.class))).thenAnswer(invocation -> {
            EquipmentNode saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
            return saved;
        });

        EquipmentNodeDto result = service.create(equipmentId, request(null, equipmentId, null, "MOTOR", "Motor", null));

        assertThat(result.code()).isEqualTo(generatedNodeCode());
        verify(repository).save(any(EquipmentNode.class));
    }

    @Test
    void createNode_duplicateSerialWithinEquipment_returnsConflict() {
        UUID equipmentId = UUID.randomUUID();
        lenient().when(repository.existsByEquipmentIdAndCodeAndIsDeletedFalse(equipmentId, "MOTOR")).thenReturn(false);
        when(repository.existsByEquipmentIdAndSerialNumberAndIsDeletedFalse(equipmentId, "SN-1")).thenReturn(true);

        assertThatThrownBy(() -> service.create(equipmentId, request(null, equipmentId, null, "MOTOR", "Motor", "SN-1")))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getMessage()).contains("serial number");
                });
        verify(repository, never()).save(any());
    }

    private EquipmentNodeDto request(UUID id, UUID equipmentId, UUID parentId, String code, String name, String serialNumber) {
        return new EquipmentNodeDto(id, equipmentId, parentId, null, name, EquipmentNodeType.COMPONENT, serialNumber, "desc");
    }

    private EquipmentNodeDto withClientCode(EquipmentNodeDto request, String code) {
        return new EquipmentNodeDto(
                request.id(),
                request.equipmentId(),
                request.parentId(),
                code,
                request.name(),
                request.nodeType(),
                request.serialNumber(),
                request.description()
        );
    }

    private String generatedNodeCode() {
        return "NODE-" + java.time.Year.now().getValue() + "-0001";
    }

    private EquipmentNode node(UUID id, UUID equipmentId, UUID parentId, String code, String name, String serialNumber) {
        EquipmentNode node = new EquipmentNode();
        ReflectionTestUtils.setField(node, "id", id);
        node.setEquipmentId(equipmentId);
        node.setParentId(parentId);
        node.setCode(code);
        node.setName(name);
        node.setNodeType(EquipmentNodeType.COMPONENT);
        node.setSerialNumber(serialNumber);
        return node;
    }
}
