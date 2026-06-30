package com.toir.controller;

import com.toir.controller.counteragent.CounteragentWorkController;
import com.toir.dto.counteragent.CounteragentWorkDto;
import com.toir.enums.ContractorWorkStatus;
import com.toir.service.counteragent.CounteragentWorkService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CounteragentWorkControllerContractTest {

    @Mock
    CounteragentWorkService service;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new CounteragentWorkController(service)).build();
    }

    @Test
    void shouldReturnAllCounteragentWorksWhenCounteragentIdNotProvided() throws Exception {
        CounteragentWorkDto first = dto(UUID.randomUUID(), UUID.randomUUID());
        CounteragentWorkDto second = dto(UUID.randomUUID(), UUID.randomUUID());
        when(service.findByCounteragent(null)).thenReturn(List.of(first, second));

        mockMvc.perform(get("/api/v1/counteragent-works"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].id").value(first.id().toString()))
                .andExpect(jsonPath("$.content[1].id").value(second.id().toString()));

        verify(service).findByCounteragent(null);
    }

    @Test
    void shouldFilterByCounteragentWhenCounteragentIdProvided() throws Exception {
        UUID counteragentId = UUID.randomUUID();
        CounteragentWorkDto work = dto(UUID.randomUUID(), counteragentId);
        when(service.findByCounteragent(counteragentId)).thenReturn(List.of(work));

        mockMvc.perform(get("/api/v1/counteragent-works")
                        .param("counteragentId", counteragentId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].counteragentId").value(counteragentId.toString()));

        verify(service).findByCounteragent(counteragentId);
    }

    @Test
    void verifyPaginationStillWorksWithoutCounteragentId() throws Exception {
        CounteragentWorkDto first = dto(UUID.randomUUID(), UUID.randomUUID());
        CounteragentWorkDto second = dto(UUID.randomUUID(), UUID.randomUUID());
        CounteragentWorkDto third = dto(UUID.randomUUID(), UUID.randomUUID());
        when(service.findByCounteragent(null)).thenReturn(List.of(first, second, third));

        mockMvc.perform(get("/api/v1/counteragent-works")
                        .param("page", "1")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(second.id().toString()))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.number").value(1))
                .andExpect(jsonPath("$.size").value(1));
    }

    private CounteragentWorkDto dto(UUID id, UUID counteragentId) {
        return new CounteragentWorkDto(
                id,
                counteragentId,
                UUID.randomUUID(),
                "Counteragent maintenance work",
                ContractorWorkStatus.DRAFT,
                Instant.parse("2026-05-01T00:00:00Z"),
                null,
                null,
                null,
                null,
                UUID.randomUUID(),
                null,
                null
        );
    }
}
