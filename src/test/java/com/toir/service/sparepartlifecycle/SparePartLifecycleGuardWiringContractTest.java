package com.toir.service.sparepartlifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SparePartLifecycleGuardWiringContractTest {

    @Test
    void guardIsWiredToStartCompleteCloseMaterialIssueAndInstallationOnly() throws IOException {
        String workOrders = source("src/main/java/com/toir/service/WorkOrderService.java");
        String materials = source("src/main/java/com/toir/service/repair/RepairMaterialUsageService.java");
        String installations = source(
                "src/main/java/com/toir/service/sparepartlifecycle/SparePartLifecycleService.java");

        assertThat(workOrders).contains(
                "SparePartLifecycleOperation.WORK_ORDER_START",
                "SparePartLifecycleOperation.WORK_ORDER_COMPLETE",
                "SparePartLifecycleOperation.WORK_ORDER_CLOSE");
        assertThat(materials).contains("SparePartLifecycleOperation.MATERIAL_ISSUE");
        assertThat(installations).contains("SparePartLifecycleOperation.SPARE_PART_INSTALL");
        assertThat(source("src/main/java/com/toir/service/MeterService.java"))
                .doesNotContain("SparePartLifecycleOperationGuard");
        assertThat(source("src/main/java/com/toir/service/defects/DefectService.java"))
                .doesNotContain("SparePartLifecycleOperationGuard");
        assertThat(source("src/main/java/com/toir/service/repair/RepairRequestService.java"))
                .doesNotContain("SparePartLifecycleOperationGuard");
    }

    @Test
    void workOrderCompletionGuardRunsBeforeMaterialAndLifecycleSideEffects() throws IOException {
        String workOrders = source("src/main/java/com/toir/service/WorkOrderService.java");
        int completeMethod = workOrders.indexOf("public WorkOrderDto complete(UUID id");
        int guard = workOrders.indexOf("SparePartLifecycleOperation.WORK_ORDER_COMPLETE", completeMethod);
        int materialIssue = workOrders.indexOf("issueCompletionMaterials(entity, request)", completeMethod);
        int lifecycleOperations = workOrders.indexOf(
                "executeSparePartLifecycleOperations(entity, request)", completeMethod);

        assertThat(completeMethod).isGreaterThanOrEqualTo(0);
        assertThat(guard).isGreaterThan(completeMethod);
        assertThat(guard).isLessThan(materialIssue);
        assertThat(guard).isLessThan(lifecycleOperations);
    }

    private String source(String path) throws IOException {
        return Files.readString(Path.of(path));
    }
}
