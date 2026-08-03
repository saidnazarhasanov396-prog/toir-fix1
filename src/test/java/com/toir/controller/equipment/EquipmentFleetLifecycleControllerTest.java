package com.toir.controller.equipment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.toir.service.equipmentfleetlifecycle.EquipmentFleetLifecycleBatchLoader.Batch;
import com.toir.service.equipmentfleetlifecycle.EquipmentFleetLifecycleJsonlWriter;
import com.toir.exception.GlobalExceptionHandler;
import com.toir.service.equipmentfleetlifecycle.EquipmentFleetLifecycleStreamService;
import com.toir.service.equipmentfleetlifecycle.EquipmentFleetLifecycleStreamService.PreparedFleetStream;
import com.toir.util.RequestContext;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

class EquipmentFleetLifecycleControllerTest {

    private static final Instant GENERATED_AT = Instant.parse("2026-08-03T08:15:30Z");
    private static final String CORRELATION_ID = "fleet-request-42";

    @Test
    void preparesTheFleetBeforeReturningAnNdjsonStreamingResponse() throws Exception {
        EquipmentFleetLifecycleStreamService streamService = mock(EquipmentFleetLifecycleStreamService.class);
        EquipmentFleetLifecycleJsonlWriter writer = mock(EquipmentFleetLifecycleJsonlWriter.class);
        PreparedFleetStream prepared = new PreparedFleetStream(
                null, false, GENERATED_AT, CORRELATION_ID, new Batch(List.of(), null, false));
        RequestContext requestContext = mock(RequestContext.class);
        AtomicReference<Thread> captureThread = new AtomicReference<>();
        when(requestContext.getCorrelationId()).thenAnswer(invocation -> {
            captureThread.set(Thread.currentThread());
            return "  " + CORRELATION_ID + "  ";
        });
        when(streamService.prepare(GENERATED_AT, CORRELATION_ID)).thenReturn(prepared);
        EquipmentFleetLifecycleController controller = new EquipmentFleetLifecycleController(
                streamService, writer, Clock.fixed(GENERATED_AT, ZoneOffset.UTC), requestContext);

        ResponseEntity<StreamingResponseBody> response = controller.get();

        verify(streamService).prepare(GENERATED_AT, CORRELATION_ID);
        assertThat(captureThread.get()).isSameAs(Thread.currentThread());
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getHeaders().getContentType().toString()).isEqualTo("application/x-ndjson");
        assertThat(response.getHeaders().getContentDisposition().getType()).isEqualTo("inline");
        assertThat(response.getHeaders().getContentDisposition().getFilename())
                .isEqualTo("equipment-fleet-lifecycle-v1.jsonl");
        assertThat(response.getHeaders().getCacheControl())
                .contains("no-store", "private", "no-transform");
        assertThat(response.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        response.getBody().writeTo(output);

        verify(writer).write(output, prepared);
    }

    @Test
    void asyncFailureAfterACompleteLineDoesNotAppendTheGlobalErrorContract() throws Exception {
        EquipmentFleetLifecycleStreamService streamService = mock(EquipmentFleetLifecycleStreamService.class);
        EquipmentFleetLifecycleJsonlWriter writer = mock(EquipmentFleetLifecycleJsonlWriter.class);
        RequestContext requestContext = mock(RequestContext.class);
        when(requestContext.getCorrelationId()).thenReturn(CORRELATION_ID);
        PreparedFleetStream prepared = new PreparedFleetStream(
                null, false, GENERATED_AT, CORRELATION_ID, new Batch(List.of(), null, false));
        when(streamService.prepare(GENERATED_AT, CORRELATION_ID)).thenReturn(prepared);
        org.mockito.Mockito.doAnswer(invocation -> {
            OutputStream output = invocation.getArgument(0);
            output.write("{\"schemaVersion\":\"1.0\"}\n".getBytes(StandardCharsets.UTF_8));
            throw new IOException("async stream abort");
        }).when(writer).write(org.mockito.ArgumentMatchers.any(OutputStream.class), eq(prepared));
        EquipmentFleetLifecycleController controller = new EquipmentFleetLifecycleController(
                streamService, writer, Clock.fixed(GENERATED_AT, ZoneOffset.UTC), requestContext);
        org.springframework.test.web.servlet.MockMvc mockMvc =
                org.springframework.test.web.servlet.setup.MockMvcBuilders
                        .standaloneSetup(controller)
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .build();

        org.springframework.test.web.servlet.MvcResult initial = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .get("/api/v1/equipment/lifecycle-context"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.request().asyncStarted())
                .andReturn();

        assertThat(initial.getAsyncResult(5_000)).isInstanceOf(IOException.class);
        assertThat(initial.getResponse().getContentAsString())
                .isEqualTo("{\"schemaVersion\":\"1.0\"}\n");
        assertThatThrownBy(() -> mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch(initial)))
                .isInstanceOf(IOException.class)
                .hasMessage("async stream abort");
        assertThat(initial.getResponse().getContentAsString()).isEmpty();
        assertThat(initial.getResponse().getContentAsString())
                .doesNotContain("INTERNAL_SERVER_ERROR", "errorCode", "summary");
    }

    @Test
    void generatesASafeCorrelationIdWhenTheRequestDoesNotProvideOne() {
        EquipmentFleetLifecycleStreamService streamService = mock(EquipmentFleetLifecycleStreamService.class);
        EquipmentFleetLifecycleJsonlWriter writer = mock(EquipmentFleetLifecycleJsonlWriter.class);
        RequestContext requestContext = mock(RequestContext.class);
        when(requestContext.getCorrelationId()).thenReturn("   ");
        PreparedFleetStream prepared = new PreparedFleetStream(
                null, false, GENERATED_AT, CORRELATION_ID, new Batch(List.of(), null, false));
        when(streamService.prepare(eq(GENERATED_AT), anyString())).thenReturn(prepared);
        EquipmentFleetLifecycleController controller = new EquipmentFleetLifecycleController(
                streamService, writer, Clock.fixed(GENERATED_AT, ZoneOffset.UTC), requestContext);

        controller.get();

        ArgumentCaptor<String> correlationId = ArgumentCaptor.forClass(String.class);
        verify(streamService).prepare(eq(GENERATED_AT), correlationId.capture());
        assertThat(correlationId.getValue())
                .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    @Test
    void publishesTheExactParameterlessFleetRouteWithReadAuthorities() throws Exception {
        RequestMapping requestMapping = EquipmentFleetLifecycleController.class.getAnnotation(RequestMapping.class);
        Method get = EquipmentFleetLifecycleController.class.getMethod("get");
        GetMapping getMapping = get.getAnnotation(GetMapping.class);
        PreAuthorize authorization = EquipmentFleetLifecycleController.class.getAnnotation(PreAuthorize.class);

        assertThat(requestMapping.value()).containsExactly("/api/v1/equipment/lifecycle-context");
        assertThat(get.getParameterCount()).isZero();
        assertThat(getMapping.value()).isEmpty();
        assertThat(getMapping.path()).isEmpty();
        assertThat(authorization.value())
                .contains("hasAuthority('SYSTEM_ADMIN')", "hasAuthority('*')", "hasAuthority('EQUIPMENT_READ')");
    }
}
