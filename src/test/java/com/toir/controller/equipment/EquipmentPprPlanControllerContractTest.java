package com.toir.controller.equipment;

import com.toir.dto.pprplanning.EquipmentLinkedPprPlanDto;
import com.toir.dto.pprplanning.EquipmentPprPlannedWorkDto;
import com.toir.dto.pprplanning.EquipmentPprPlannedWorksResponse;
import com.toir.dto.pprplanning.EquipmentPprPlansResponse;
import com.toir.dto.pprplanning.PprPlanDto;
import com.toir.enums.PlanStatus;
import com.toir.service.PprPlanService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class EquipmentPprPlanControllerContractTest {

    @Mock
    private PprPlanService pprPlanService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new EquipmentPprPlanController(pprPlanService))
                .build();
    }

    @Test
    void listReturnsEquipmentScopedPlans() throws Exception {
        UUID equipmentId = UUID.randomUUID();
        UUID typeId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        PprPlanDto plan = new PprPlanDto(
                planId,
                "PPR-2026-001",
                "Annual pump plan",
                PlanStatus.APPROVED,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                2L,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                null,
                null,
                null,
                null,
                null,
                List.of(),
                null,
                null,
                null
        );
        when(pprPlanService.findLinkedToEquipment(equipmentId, false))
                .thenReturn(new EquipmentPprPlansResponse(
                        equipmentId,
                        typeId,
                        1,
                        List.of(new EquipmentLinkedPprPlanDto(
                                plan,
                                List.of(EquipmentLinkedPprPlanDto.REASON_EQUIPMENT_TARGET)
                        ))
                ));

        mockMvc.perform(get("/api/v1/equipment/{equipmentId}/ppr-plans", equipmentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.equipmentId").value(equipmentId.toString()))
                .andExpect(jsonPath("$.equipmentTypeId").value(typeId.toString()))
                .andExpect(jsonPath("$.planCount").value(1))
                .andExpect(jsonPath("$.plans[0].plan.id").value(planId.toString()))
                .andExpect(jsonPath("$.plans[0].plan.code").value("PPR-2026-001"))
                .andExpect(jsonPath("$.plans[0].linkReasons[0]").value("EQUIPMENT_TARGET"));

        verify(pprPlanService).findLinkedToEquipment(equipmentId, false);
    }

    @Test
    void listPassesIncludeTasksFlag() throws Exception {
        UUID equipmentId = UUID.randomUUID();
        when(pprPlanService.findLinkedToEquipment(equipmentId, true))
                .thenReturn(new EquipmentPprPlansResponse(equipmentId, null, 0, List.of()));

        mockMvc.perform(get("/api/v1/equipment/{equipmentId}/ppr-plans", equipmentId)
                        .param("includeTasks", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planCount").value(0));

        verify(pprPlanService).findLinkedToEquipment(equipmentId, true);
    }

    @Test
    void listDirectReturnsOnlyPlannedWorks() throws Exception {
        UUID equipmentId = UUID.randomUUID();
        UUID typeId = UUID.randomUUID();
        EquipmentPprPlannedWorkDto work = new EquipmentPprPlannedWorkDto(
                "RULE:" + UUID.randomUUID(),
                "Oil change",
                null,
                null,
                null,
                null,
                UUID.randomUUID(),
                "OIL-CHANGE",
                "Oil change",
                null,
                2,
                null,
                null,
                1,
                null,
                null,
                null
        );
        when(pprPlanService.findDirectPlannedWorks(equipmentId))
                .thenReturn(new EquipmentPprPlannedWorksResponse(
                        equipmentId,
                        typeId,
                        1,
                        List.of(work)
                ));

        mockMvc.perform(get("/api/v1/equipment/{equipmentId}/ppr-plans/direct", equipmentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.equipmentId").value(equipmentId.toString()))
                .andExpect(jsonPath("$.plannedWorkCount").value(1))
                .andExpect(jsonPath("$.plannedWorks[0].title").value("Oil change"))
                .andExpect(jsonPath("$.plans").doesNotExist());

        verify(pprPlanService).findDirectPlannedWorks(equipmentId);
    }
}
