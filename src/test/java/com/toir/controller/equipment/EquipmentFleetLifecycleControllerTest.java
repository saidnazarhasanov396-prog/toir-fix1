package com.toir.controller.equipment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.toir.service.equipmentfleetlifecycle.EquipmentFleetLifecycleBatchLoader.Batch;
import com.toir.service.equipmentfleetlifecycle.EquipmentFleetLifecycleJsonlWriter;
import com.toir.service.equipmentfleetlifecycle.EquipmentFleetLifecycleStreamService;
import com.toir.service.equipmentfleetlifecycle.EquipmentFleetLifecycleStreamService.PreparedFleetStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

class EquipmentFleetLifecycleControllerTest {

    private static final Instant GENERATED_AT = Instant.parse("2026-08-03T08:15:30Z");

    @Test
    void preparesTheFleetBeforeReturningAnNdjsonStreamingResponse() throws Exception {
        EquipmentFleetLifecycleStreamService streamService = mock(EquipmentFleetLifecycleStreamService.class);
        EquipmentFleetLifecycleJsonlWriter writer = mock(EquipmentFleetLifecycleJsonlWriter.class);
        PreparedFleetStream prepared = new PreparedFleetStream(null, false, GENERATED_AT, new Batch(List.of(), null, false));
        when(streamService.prepare(GENERATED_AT)).thenReturn(prepared);
        EquipmentFleetLifecycleController controller = new EquipmentFleetLifecycleController(
                streamService, writer, Clock.fixed(GENERATED_AT, ZoneOffset.UTC));

        ResponseEntity<StreamingResponseBody> response = controller.get();

        verify(streamService).prepare(GENERATED_AT);
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
