package com.toir.service.equipment;

import com.toir.entity.FileAsset;
import com.toir.entity.TechnicalDocument;
import com.toir.entity.defects.Defect;
import com.toir.entity.equipment.EquipmentNode;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.enums.DefectStatus;
import com.toir.enums.DocumentType;
import com.toir.enums.EquipmentNodeType;
import com.toir.enums.PriorityLevel;
import com.toir.enums.WorkOrderStatus;
import com.toir.enums.WorkOrderType;
import com.toir.enums.WorkType;
import com.toir.exception.RestException;
import com.toir.repository.FileAssetRepository;
import com.toir.repository.TechnicalDocumentRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.defects.DefectRepository;
import com.toir.repository.equipment.EquipmentNodeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EquipmentNodeLifecycleServiceTest {

    @Mock
    EquipmentNodeRepository equipmentNodeRepository;

    @Mock
    DefectRepository defectRepository;

    @Mock
    WorkOrderRepository workOrderRepository;

    @Mock
    TechnicalDocumentRepository technicalDocumentRepository;

    @Mock
    FileAssetRepository fileAssetRepository;

    @InjectMocks
    EquipmentNodeLifecycleService service;

    @Test
    void getLifecycle_returnsNodeSummaryAndCounts() {
        UUID equipmentId = UUID.randomUUID();
        UUID nodeId = UUID.randomUUID();
        EquipmentNode node = node(nodeId, equipmentId);
        when(equipmentNodeRepository.findByIdAndIsDeletedFalse(nodeId)).thenReturn(Optional.of(node));
        when(defectRepository.findAllByEquipmentNodeIdAndIsDeletedFalseOrderByUpdatedAtDesc(nodeId))
                .thenReturn(List.of(defect(UUID.randomUUID(), equipmentId, nodeId, "DEF-1", Instant.parse("2026-05-23T10:00:00Z"))));
        when(workOrderRepository.findAllByEquipmentNodeIdAndIsDeletedFalseOrderByUpdatedAtDesc(nodeId))
                .thenReturn(List.of(workOrder(UUID.randomUUID(), equipmentId, nodeId, "WO-1", Instant.parse("2026-05-23T11:00:00Z"))));
        when(technicalDocumentRepository.findAllByEquipmentNodeIdAndIsDeletedFalse(nodeId))
                .thenReturn(List.of(document(UUID.randomUUID(), equipmentId, nodeId, "Drawing", Instant.parse("2026-05-23T12:00:00Z"))));

        var result = service.getLifecycle(nodeId, true, 50);

        assertThat(result.node().id()).isEqualTo(nodeId);
        assertThat(result.node().equipmentId()).isEqualTo(equipmentId);
        assertThat(result.node().code()).isEqualTo("BRG-01");
        assertThat(result.counts().defects()).isEqualTo(1);
        assertThat(result.counts().workOrders()).isEqualTo(1);
        assertThat(result.counts().documents()).isEqualTo(1);
    }

    @Test
    void getLifecycle_includesDefectsWorkOrdersAndDocuments() {
        UUID equipmentId = UUID.randomUUID();
        UUID nodeId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        EquipmentNode node = node(nodeId, equipmentId);
        TechnicalDocument document = document(UUID.randomUUID(), equipmentId, nodeId, "Certificate", Instant.parse("2026-05-23T12:00:00Z"));
        document.setFileId(fileId);
        FileAsset fileAsset = fileAsset(fileId);
        when(equipmentNodeRepository.findByIdAndIsDeletedFalse(nodeId)).thenReturn(Optional.of(node));
        when(defectRepository.findAllByEquipmentNodeIdAndIsDeletedFalseOrderByUpdatedAtDesc(nodeId))
                .thenReturn(List.of(defect(UUID.randomUUID(), equipmentId, nodeId, "DEF-1", Instant.parse("2026-05-23T10:00:00Z"))));
        when(workOrderRepository.findAllByEquipmentNodeIdAndIsDeletedFalseOrderByUpdatedAtDesc(nodeId))
                .thenReturn(List.of(workOrder(UUID.randomUUID(), equipmentId, nodeId, "WO-1", Instant.parse("2026-05-23T11:00:00Z"))));
        when(technicalDocumentRepository.findAllByEquipmentNodeIdAndIsDeletedFalse(nodeId))
                .thenReturn(List.of(document));
        when(fileAssetRepository.findAllByIdInAndIsDeletedFalse(List.of(fileId))).thenReturn(List.of(fileAsset));

        var result = service.getLifecycle(nodeId, true, 50);

        assertThat(result.defects()).hasSize(1);
        assertThat(result.defects().getFirst().code()).isEqualTo("DEF-1");
        assertThat(result.workOrders()).hasSize(1);
        assertThat(result.workOrders().getFirst().number()).isEqualTo("WO-1");
        assertThat(result.documents()).hasSize(1);
        assertThat(result.documents().getFirst().title()).isEqualTo("Certificate");
        assertThat(result.documents().getFirst().file()).isNotNull();
        assertThat(result.documents().getFirst().file().downloadUrl())
                .isEqualTo("/api/v1/files/assets/%s/download".formatted(fileId));
    }

    @Test
    void getLifecycle_returnsTimelineSortedDescending() {
        UUID equipmentId = UUID.randomUUID();
        UUID nodeId = UUID.randomUUID();
        when(equipmentNodeRepository.findByIdAndIsDeletedFalse(nodeId)).thenReturn(Optional.of(node(nodeId, equipmentId)));
        when(defectRepository.findAllByEquipmentNodeIdAndIsDeletedFalseOrderByUpdatedAtDesc(nodeId))
                .thenReturn(List.of(defect(UUID.randomUUID(), equipmentId, nodeId, "DEF-1", Instant.parse("2026-05-23T10:00:00Z"))));
        when(workOrderRepository.findAllByEquipmentNodeIdAndIsDeletedFalseOrderByUpdatedAtDesc(nodeId))
                .thenReturn(List.of(workOrder(UUID.randomUUID(), equipmentId, nodeId, "WO-1", Instant.parse("2026-05-23T12:00:00Z"))));
        when(technicalDocumentRepository.findAllByEquipmentNodeIdAndIsDeletedFalse(nodeId))
                .thenReturn(List.of(document(UUID.randomUUID(), equipmentId, nodeId, "Manual", Instant.parse("2026-05-23T11:00:00Z"))));

        var result = service.getLifecycle(nodeId, true, 50);

        assertThat(result.timeline()).extracting("type")
                .containsExactly("WORK_ORDER", "DOCUMENT", "DEFECT");
        assertThat(result.timeline()).extracting("occurredAt")
                .containsExactly(
                        Instant.parse("2026-05-23T12:00:00Z"),
                        Instant.parse("2026-05-23T11:00:00Z"),
                        Instant.parse("2026-05-23T10:00:00Z")
                );
    }

    @Test
    void getLifecycle_nodeNotFound_returnsNotFound() {
        UUID nodeId = UUID.randomUUID();
        when(equipmentNodeRepository.findByIdAndIsDeletedFalse(nodeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getLifecycle(nodeId, true, 50))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(ex.getMessage()).contains("Equipment node not found");
                });
    }

    @Test
    void getLifecycle_excludesDeletedRecords() {
        UUID equipmentId = UUID.randomUUID();
        UUID nodeId = UUID.randomUUID();
        Defect activeDefect = defect(UUID.randomUUID(), equipmentId, nodeId, "DEF-A", Instant.parse("2026-05-23T10:00:00Z"));
        Defect deletedDefect = defect(UUID.randomUUID(), equipmentId, nodeId, "DEF-D", Instant.parse("2026-05-23T11:00:00Z"));
        deletedDefect.setDeleted(true);
        WorkOrder activeWorkOrder = workOrder(UUID.randomUUID(), equipmentId, nodeId, "WO-A", Instant.parse("2026-05-23T12:00:00Z"));
        WorkOrder deletedWorkOrder = workOrder(UUID.randomUUID(), equipmentId, nodeId, "WO-D", Instant.parse("2026-05-23T13:00:00Z"));
        deletedWorkOrder.setDeleted(true);
        TechnicalDocument activeDocument = document(UUID.randomUUID(), equipmentId, nodeId, "Active doc", Instant.parse("2026-05-23T14:00:00Z"));
        TechnicalDocument deletedDocument = document(UUID.randomUUID(), equipmentId, nodeId, "Deleted doc", Instant.parse("2026-05-23T15:00:00Z"));
        deletedDocument.setDeleted(true);
        when(equipmentNodeRepository.findByIdAndIsDeletedFalse(nodeId)).thenReturn(Optional.of(node(nodeId, equipmentId)));
        when(defectRepository.findAllByEquipmentNodeIdAndIsDeletedFalseOrderByUpdatedAtDesc(nodeId))
                .thenReturn(List.of(deletedDefect, activeDefect));
        when(workOrderRepository.findAllByEquipmentNodeIdAndIsDeletedFalseOrderByUpdatedAtDesc(nodeId))
                .thenReturn(List.of(deletedWorkOrder, activeWorkOrder));
        when(technicalDocumentRepository.findAllByEquipmentNodeIdAndIsDeletedFalse(nodeId))
                .thenReturn(List.of(deletedDocument, activeDocument));

        var result = service.getLifecycle(nodeId, true, 50);

        assertThat(result.defects()).extracting("code").containsExactly("DEF-A");
        assertThat(result.workOrders()).extracting("number").containsExactly("WO-A");
        assertThat(result.documents()).extracting("title").containsExactly("Active doc");
        assertThat(result.counts().defects()).isEqualTo(1);
        assertThat(result.counts().workOrders()).isEqualTo(1);
        assertThat(result.counts().documents()).isEqualTo(1);
    }

    private EquipmentNode node(UUID id, UUID equipmentId) {
        EquipmentNode node = new EquipmentNode();
        node.setId(id);
        node.setEquipmentId(equipmentId);
        node.setParentId(UUID.randomUUID());
        node.setCode("BRG-01");
        node.setName("Bearing");
        node.setNodeType(EquipmentNodeType.COMPONENT);
        node.setSerialNumber("SN-1");
        return node;
    }

    private Defect defect(UUID id, UUID equipmentId, UUID nodeId, String code, Instant updatedAt) {
        Defect defect = new Defect();
        defect.setId(id);
        defect.setEquipmentId(equipmentId);
        defect.setEquipmentNodeId(nodeId);
        defect.setCode(code);
        defect.setTitle("Bearing overheating");
        defect.setDescription("Temperature is above normal");
        defect.setSeverity("HIGH");
        defect.setStatus(DefectStatus.OPEN);
        defect.setCreatedAt(updatedAt.minusSeconds(60));
        defect.setUpdatedAt(updatedAt);
        return defect;
    }

    private WorkOrder workOrder(UUID id, UUID equipmentId, UUID nodeId, String number, Instant updatedAt) {
        WorkOrder workOrder = new WorkOrder();
        workOrder.setId(id);
        workOrder.setEquipmentId(equipmentId);
        workOrder.setEquipmentNodeId(nodeId);
        workOrder.setDepartmentId(UUID.randomUUID());
        workOrder.setNumber(number);
        workOrder.setTitle("Replace bearing");
        workOrder.setStatus(WorkOrderStatus.IN_PROGRESS);
        workOrder.setType(WorkOrderType.DEFECT);
        workOrder.setWorkType(WorkType.REPAIR);
        workOrder.setPriority(PriorityLevel.HIGH);
        workOrder.setCreatedById(UUID.randomUUID());
        workOrder.setCreatedAt(updatedAt.minusSeconds(60));
        workOrder.setUpdatedAt(updatedAt);
        return workOrder;
    }

    private TechnicalDocument document(UUID id, UUID equipmentId, UUID nodeId, String title, Instant updatedAt) {
        TechnicalDocument document = new TechnicalDocument();
        document.setId(id);
        document.setEquipmentId(equipmentId);
        document.setEquipmentNodeId(nodeId);
        document.setTitle(title);
        document.setType(DocumentType.DRAWING);
        document.setRevision("R1");
        document.setDocumentDate(LocalDate.of(2026, 5, 23));
        document.setCreatedAt(updatedAt.minusSeconds(60));
        document.setUpdatedAt(updatedAt);
        return document;
    }

    private FileAsset fileAsset(UUID id) {
        FileAsset fileAsset = new FileAsset();
        fileAsset.setId(id);
        fileAsset.setFileName("drawing.pdf");
        fileAsset.setOriginalName("Drawing.pdf");
        fileAsset.setMimeType("application/pdf");
        fileAsset.setSizeBytes(1024);
        return fileAsset;
    }
}
