package com.toir.controller.equipment;

import com.toir.dto.equipmentnode.EquipmentNodeDto;
import com.toir.dto.equipmentnode.EquipmentNodeLifecycleDto;
import com.toir.enums.EquipmentNodeType;
import com.toir.exception.GlobalExceptionHandler;
import com.toir.exception.RestException;
import com.toir.service.equipment.EquipmentNodeLifecycleService;
import com.toir.service.equipment.EquipmentNodeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class EquipmentNodeControllerContractTest {

    @Mock
    EquipmentNodeService service;

    @Mock
    EquipmentNodeLifecycleService lifecycleService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new EquipmentNodeController(service, lifecycleService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listNodes_returnsParentId() throws Exception {
        UUID equipmentId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        UUID nodeId = UUID.randomUUID();
        when(service.findByEquipment(equipmentId)).thenReturn(List.of(
                node(nodeId, equipmentId, parentId, "MOTOR", "Motor", "SN-1")
        ));

        mockMvc.perform(get("/api/v1/equipment/{equipmentId}/nodes", equipmentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(nodeId.toString()))
                .andExpect(jsonPath("$.content[0].equipmentId").value(equipmentId.toString()))
                .andExpect(jsonPath("$.content[0].parentId").value(parentId.toString()))
                .andExpect(jsonPath("$.content[0].code").value("MOTOR"))
                .andExpect(jsonPath("$.content[0].serialNumber").value("SN-1"));
    }

    @Test
    void createNode_acceptsParentId() throws Exception {
        UUID equipmentId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        UUID nodeId = UUID.randomUUID();
        when(service.create(eq(equipmentId), any()))
                .thenReturn(node(nodeId, equipmentId, parentId, "MOTOR", "Motor", null));

        mockMvc.perform(post("/api/v1/equipment/{equipmentId}/nodes", equipmentId)
                        .contentType("application/json")
                        .content("""
                                {
                                  "parentId": "%s",
                                  "code": "MOTOR",
                                  "name": "Motor",
                                  "nodeType": "COMPONENT"
                                }
                                """.formatted(parentId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parentId").value(parentId.toString()));

        ArgumentCaptor<EquipmentNodeDto> captor = ArgumentCaptor.forClass(EquipmentNodeDto.class);
        verify(service).create(eq(equipmentId), captor.capture());
        assertThat(captor.getValue().parentId()).isEqualTo(parentId);
    }

    @Test
    void createNode_acceptsParentNodeIdAlias() throws Exception {
        UUID equipmentId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        UUID nodeId = UUID.randomUUID();
        when(service.create(eq(equipmentId), any()))
                .thenReturn(node(nodeId, equipmentId, parentId, "MOTOR", "Motor", null));

        mockMvc.perform(post("/api/v1/equipment/{equipmentId}/nodes", equipmentId)
                        .contentType("application/json")
                        .content("""
                                {
                                  "parentNodeId": "%s",
                                  "code": "MOTOR",
                                  "name": "Motor",
                                  "nodeType": "COMPONENT"
                                }
                                """.formatted(parentId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parentId").value(parentId.toString()));

        ArgumentCaptor<EquipmentNodeDto> captor = ArgumentCaptor.forClass(EquipmentNodeDto.class);
        verify(service).create(eq(equipmentId), captor.capture());
        assertThat(captor.getValue().parentId()).isEqualTo(parentId);
    }

    @Test
    void updateNode_rejectsCircularParent() throws Exception {
        UUID nodeId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        when(service.update(eq(nodeId), any())).thenThrow(RestException.badRequest("Circular equipment node hierarchy"));

        mockMvc.perform(put("/api/v1/equipment-nodes/{id}", nodeId)
                        .contentType("application/json")
                        .content("""
                                {
                                  "parentId": "%s",
                                  "code": "MOTOR",
                                  "name": "Motor",
                                  "nodeType": "COMPONENT"
                                }
                                """.formatted(parentId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Circular equipment node hierarchy"));
    }

    @Test
    void getNodeLifecycle_returnsExpectedShape() throws Exception {
        UUID nodeId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        UUID defectId = UUID.randomUUID();
        UUID workOrderId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        EquipmentNodeLifecycleDto response = new EquipmentNodeLifecycleDto(
                new EquipmentNodeLifecycleDto.NodeSummary(
                        nodeId,
                        equipmentId,
                        null,
                        "BRG-01",
                        "Bearing",
                        EquipmentNodeType.COMPONENT,
                        "SN-1"
                ),
                new EquipmentNodeLifecycleDto.Counts(1, 1, 1),
                List.of(new EquipmentNodeLifecycleDto.DefectItem(
                        defectId,
                        "DEF-1",
                        "Bearing overheating",
                        "OPEN",
                        "HIGH",
                        Instant.parse("2026-05-23T10:00:00Z"),
                        Instant.parse("2026-05-23T11:00:00Z")
                )),
                List.of(new EquipmentNodeLifecycleDto.WorkOrderItem(
                        workOrderId,
                        "WO-1",
                        "Replace bearing",
                        "IN_PROGRESS",
                        "DEFECT",
                        "REPAIR",
                        "HIGH",
                        Instant.parse("2026-05-23T12:00:00Z"),
                        Instant.parse("2026-05-23T13:00:00Z")
                )),
                List.of(new EquipmentNodeLifecycleDto.DocumentItem(
                        documentId,
                        "Bearing drawing",
                        "DRAWING",
                        "R1",
                        java.time.LocalDate.of(2026, 5, 23),
                        null,
                        Instant.parse("2026-05-23T14:00:00Z"),
                        Instant.parse("2026-05-23T15:00:00Z")
                )),
                List.of(new EquipmentNodeLifecycleDto.TimelineItem(
                        "WORK_ORDER",
                        workOrderId,
                        "Replace bearing",
                        "IN_PROGRESS",
                        "DEFECT",
                        Instant.parse("2026-05-23T13:00:00Z"),
                        Instant.parse("2026-05-23T12:00:00Z")
                ))
        );
        when(lifecycleService.getLifecycle(nodeId, true, 50)).thenReturn(response);

        mockMvc.perform(get("/api/v1/equipment-nodes/{nodeId}/lifecycle", nodeId)
                        .param("includeTimeline", "true")
                        .param("limit", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.node.id").value(nodeId.toString()))
                .andExpect(jsonPath("$.node.code").value("BRG-01"))
                .andExpect(jsonPath("$.counts.defects").value(1))
                .andExpect(jsonPath("$.defects[0].code").value("DEF-1"))
                .andExpect(jsonPath("$.workOrders[0].number").value("WO-1"))
                .andExpect(jsonPath("$.documents[0].title").value("Bearing drawing"))
                .andExpect(jsonPath("$.timeline[0].type").value("WORK_ORDER"));

        verify(lifecycleService).getLifecycle(nodeId, true, 50);
    }

    private EquipmentNodeDto node(UUID id,
                                  UUID equipmentId,
                                  UUID parentId,
                                  String code,
                                  String name,
                                  String serialNumber) {
        return new EquipmentNodeDto(
                id,
                equipmentId,
                parentId,
                code,
                name,
                EquipmentNodeType.COMPONENT,
                serialNumber,
                "desc"
        );
    }
}
