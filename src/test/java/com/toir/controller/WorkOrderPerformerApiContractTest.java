package com.toir.controller;

import com.toir.dto.workorder.WorkOrderDto;
import com.toir.dto.workorder.WorkOrderRequest;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class WorkOrderPerformerApiContractTest {

    @Test
    void requestAndResponseExposeCanonicalAndLegacyPerformerFields() {
        assertThat(componentNames(WorkOrderRequest.class))
                .contains("performerId", "performerEmployeeId", "performerBrigadeMemberId");
        assertThat(componentNames(WorkOrderDto.class))
                .contains(
                        "performerId", "performerName",
                        "performerEmployeeId", "performerFullName", "performerUserId",
                        "performerBrigadeMemberId", "performerBrigadeId", "performerBrigadeName"
                );
    }

    @Test
    void controllerPublishesEmployeeOptionsAndReassignmentRoutes() throws Exception {
        GetMapping options = WorkOrderController.class
                .getDeclaredMethod("employeePerformerOptions", java.util.UUID.class, String.class, int.class, int.class)
                .getAnnotation(GetMapping.class);
        PatchMapping reassign = WorkOrderController.class
                .getDeclaredMethod("reassignPerformer", java.util.UUID.class,
                        com.toir.dto.workorder.WorkOrderPerformerAssignmentRequest.class)
                .getAnnotation(PatchMapping.class);

        assertThat(options.value()).containsExactly("/performer-options");
        assertThat(reassign.value()).containsExactly("/{id}/performer");
    }

    private static java.util.List<String> componentNames(Class<?> recordType) {
        return Arrays.stream(recordType.getRecordComponents()).map(java.lang.reflect.RecordComponent::getName).toList();
    }
}
