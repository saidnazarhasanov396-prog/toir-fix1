package com.toir.model;

import com.toir.dto.defect.DefectRequest;
import com.toir.dto.workorder.WorkOrderDto;
import com.toir.dto.workorder.WorkOrderRequest;
import com.toir.entity.defects.Defect;
import com.toir.entity.maintenance.WorkOrder;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class TriadSchemaModelContractTest {

    @Test
    void defectMustUseRepairRequestIdAndMustNotExposeLegacyWorkOrderOrRequestFields() {
        assertThat(Arrays.stream(Defect.class.getDeclaredFields()).map(f -> f.getName()))
                .contains("repairRequestId")
                .doesNotContain("requestId")
                .doesNotContain("workOrderId");
    }

    @Test
    void workOrderMustExposeRepairRequestIdAndDefectId() {
        assertThat(Arrays.stream(WorkOrder.class.getDeclaredFields()).map(f -> f.getName()))
                .contains("repairRequestId", "defectId");
    }

    @Test
    void workOrderRequestAndDtoMustExposeDefectId() {
        assertThat(Arrays.stream(WorkOrderRequest.class.getDeclaredMethods()).map(m -> m.getName()))
                .contains("defectId");
        assertThat(Arrays.stream(WorkOrderDto.class.getDeclaredMethods()).map(m -> m.getName()))
                .contains("defectId");
    }

    @Test
    void defectRequestMustExposeRepairRequestId() {
        assertThat(Arrays.stream(DefectRequest.class.getDeclaredMethods()).map(m -> m.getName()))
                .contains("repairRequestId");
    }
}
