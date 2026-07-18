package com.toir.service.approval;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LifecycleApprovalFixedAssumptionSourceTest {

    private static final Path MAIN_JAVA = Path.of("src/main/java");

    @Test
    void activeLifecycleApprovalSourceContainsNoFixedRouteValidatorsOrAssumptions() throws IOException {
        assertThat(MAIN_JAVA.resolve(
                "com/toir/service/repair/RepairCampaignApprovalRouteValidator.java"))
                .doesNotExist();
        assertThat(MAIN_JAVA.resolve(
                "com/toir/service/plannedshutdown/PlannedShutdownApprovalRouteValidator.java"))
                .doesNotExist();

        String productionSources = readSources(List.of(
                "com/toir/service/ApprovalService.java",
                "com/toir/service/approval/DefaultApprovalRouteResolver.java",
                "com/toir/service/repair/RepairCampaignApprovalPolicy.java",
                "com/toir/service/PlannedShutdownService.java",
                "com/toir/service/plannedshutdown/PlannedShutdownApprovalScopeHasher.java"));

        assertThat(productionSources).doesNotContain(
                "RepairCampaignApprovalRouteValidator",
                "PlannedShutdownApprovalRouteValidator",
                "REQUIRED_DISCIPLINE_ROLES",
                "REQUIRED_APPROVER_ROLES",
                "PRODUCTION_APPROVER_ROLE",
                "HSE_APPROVER_ROLE",
                "LEGACY_SYSTEM_ADMIN_ROUTE",
                "completedSevenDisciplineRoute",
                "APPROVAL_PRODUCTION_MISSING",
                "APPROVAL_HSE_MISSING");
    }

    private static String readSources(List<String> relativePaths) throws IOException {
        StringBuilder sources = new StringBuilder();
        for (String relativePath : relativePaths) {
            sources.append(Files.readString(MAIN_JAVA.resolve(relativePath))).append('\n');
        }
        return sources.toString();
    }
}
