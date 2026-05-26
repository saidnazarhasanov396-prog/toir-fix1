package com.toir.controller;

import com.toir.controller.contractor.ContractorWorkController;
import com.toir.dto.contractorwork.ContractorWorkDto;
import com.toir.enums.ContractorWorkStatus;
import com.toir.service.contactor.ContractorWorkService;
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
class ContractorWorkControllerContractTest {

    @Mock
    ContractorWorkService service;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ContractorWorkController(service)).build();
    }

    @Test
    void shouldReturnAllContractorWorksWhenContractorIdNotProvided() throws Exception {
        ContractorWorkDto first = dto(UUID.randomUUID(), UUID.randomUUID());
        ContractorWorkDto second = dto(UUID.randomUUID(), UUID.randomUUID());
        when(service.findByContractor(null)).thenReturn(List.of(first, second));

        mockMvc.perform(get("/api/v1/contractor-works"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].id").value(first.id().toString()))
                .andExpect(jsonPath("$.content[1].id").value(second.id().toString()));

        verify(service).findByContractor(null);
    }

    @Test
    void shouldFilterByContractorWhenContractorIdProvided() throws Exception {
        UUID contractorId = UUID.randomUUID();
        ContractorWorkDto work = dto(UUID.randomUUID(), contractorId);
        when(service.findByContractor(contractorId)).thenReturn(List.of(work));

        mockMvc.perform(get("/api/v1/contractor-works")
                        .param("contractorId", contractorId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].contractorId").value(contractorId.toString()));

        verify(service).findByContractor(contractorId);
    }

    @Test
    void verifyPaginationStillWorksWithoutContractorId() throws Exception {
        ContractorWorkDto first = dto(UUID.randomUUID(), UUID.randomUUID());
        ContractorWorkDto second = dto(UUID.randomUUID(), UUID.randomUUID());
        ContractorWorkDto third = dto(UUID.randomUUID(), UUID.randomUUID());
        when(service.findByContractor(null)).thenReturn(List.of(first, second, third));

        mockMvc.perform(get("/api/v1/contractor-works")
                        .param("page", "1")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(second.id().toString()))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.number").value(1))
                .andExpect(jsonPath("$.size").value(1));
    }

    private ContractorWorkDto dto(UUID id, UUID contractorId) {
        return new ContractorWorkDto(
                id,
                contractorId,
                UUID.randomUUID(),
                "Contractor maintenance work",
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
