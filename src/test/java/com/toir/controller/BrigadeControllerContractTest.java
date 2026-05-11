package com.toir.controller;

import com.toir.dto.brigade.BrigadeDto;
import com.toir.exception.GlobalExceptionHandler;
import com.toir.service.BrigadeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class BrigadeControllerContractTest {

    @Mock
    BrigadeService service;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new BrigadeController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listWithoutFiltersReturns200() throws Exception {
        when(service.findAll(null, null, null)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/brigades")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void listWithSearchEmptyAndTextReturns200() throws Exception {
        BrigadeDto dto = new BrigadeDto(
                UUID.randomUUID(),
                "BR-01",
                "Mechanic Team",
                UUID.randomUUID(),
                UUID.randomUUID(),
                "mechanics",
                true,
                List.of()
        );
        when(service.findAll(any(), any(), eq(""))).thenReturn(List.of(dto));
        when(service.findAll(any(), any(), eq("mechanic"))).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/v1/brigades")
                        .param("search", "")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());

        mockMvc.perform(get("/api/v1/brigades")
                        .param("search", "mechanic")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Mechanic Team"));

        verify(service).findAll(any(), any(), eq(""));
        verify(service).findAll(any(), any(), eq("mechanic"));
    }
}
