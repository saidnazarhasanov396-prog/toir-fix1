package com.toir.security;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class ToirBusinessFlowRolePermissionsTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V20260711_2__toir_business_flow_permissions.sql"
    );
    private static final Path DATA_BOOTSTRAP = Path.of(
            "src/main/java/com/toir/config/DataBootstrap.java"
    );

    private static final Set<String> FULL_BUSINESS_FLOW_PERMISSIONS = Set.of(
            PermissionConstants.PLANNED_SHUTDOWN_READ,
            PermissionConstants.PLANNED_SHUTDOWN_CREATE,
            PermissionConstants.PLANNED_SHUTDOWN_UPDATE,
            PermissionConstants.PLANNED_SHUTDOWN_APPROVE,
            PermissionConstants.PLANNED_SHUTDOWN_PREPARE,
            PermissionConstants.PLANNED_SHUTDOWN_CONFIRM_SAFE_STATE,
            PermissionConstants.PLANNED_SHUTDOWN_START_REPAIR,
            PermissionConstants.PLANNED_SHUTDOWN_TEST,
            PermissionConstants.PLANNED_SHUTDOWN_STARTUP,
            PermissionConstants.PLANNED_SHUTDOWN_CLOSE,
            PermissionConstants.PLANNED_SHUTDOWN_CANCEL,
            PermissionConstants.PLANNED_SHUTDOWN_RESCHEDULE,
            PermissionConstants.PLANNED_SHUTDOWN_EXTEND,
            PermissionConstants.REPAIR_CAMPAIGN_READ,
            PermissionConstants.REPAIR_CAMPAIGN_CREATE,
            PermissionConstants.REPAIR_CAMPAIGN_UPDATE,
            PermissionConstants.REPAIR_CAMPAIGN_APPROVE,
            PermissionConstants.REPAIR_CAMPAIGN_START,
            PermissionConstants.REPAIR_CAMPAIGN_SUSPEND,
            PermissionConstants.REPAIR_CAMPAIGN_COMPLETE,
            PermissionConstants.REPAIR_CAMPAIGN_CLOSE,
            PermissionConstants.REPAIR_CAMPAIGN_CANCEL,
            PermissionConstants.REPAIR_CAMPAIGN_GENERATE_WORK_ORDERS
    );

    @Test
    void defaultsGrantFullBusinessFlowPermissionsToEstablishedApproverRoles() {
        assertThat(RolePermissionDefaults.forRole("TECHNICAL_DIRECTOR"))
                .containsAll(FULL_BUSINESS_FLOW_PERMISSIONS);
        assertThat(RolePermissionDefaults.forRole("CHIEF_MECHANIC"))
                .containsAll(FULL_BUSINESS_FLOW_PERMISSIONS);
    }

    @Test
    void defaultsGrantConservativeBusinessFlowSubsetsWithoutApproval() {
        assertThat(RolePermissionDefaults.forRole("PPR_ENGINEER")).contains(
                PermissionConstants.PLANNED_SHUTDOWN_READ,
                PermissionConstants.PLANNED_SHUTDOWN_CREATE,
                PermissionConstants.PLANNED_SHUTDOWN_UPDATE,
                PermissionConstants.REPAIR_CAMPAIGN_READ,
                PermissionConstants.REPAIR_CAMPAIGN_CREATE,
                PermissionConstants.REPAIR_CAMPAIGN_UPDATE,
                PermissionConstants.REPAIR_CAMPAIGN_GENERATE_WORK_ORDERS
        ).doesNotContain(
                PermissionConstants.PLANNED_SHUTDOWN_APPROVE,
                PermissionConstants.REPAIR_CAMPAIGN_APPROVE
        );

        assertThat(RolePermissionDefaults.forRole("RELIABILITY_ENGINEER")).contains(
                PermissionConstants.PLANNED_SHUTDOWN_READ,
                PermissionConstants.REPAIR_CAMPAIGN_READ,
                PermissionConstants.REPAIR_CAMPAIGN_UPDATE
        ).doesNotContain(
                PermissionConstants.PLANNED_SHUTDOWN_APPROVE,
                PermissionConstants.REPAIR_CAMPAIGN_APPROVE
        );
    }

    @Test
    void migrationMatchesBootstrapDefaultsForBusinessFlowRoles() throws Exception {
        Map<String, Set<String>> matrix = migrationMatrix();

        assertThat(matrix.get("SYSTEM_ADMIN")).containsAll(FULL_BUSINESS_FLOW_PERMISSIONS);
        assertThat(matrix.get("TECHNICAL_DIRECTOR")).containsAll(FULL_BUSINESS_FLOW_PERMISSIONS);
        assertThat(matrix.get("CHIEF_MECHANIC")).containsAll(FULL_BUSINESS_FLOW_PERMISSIONS);
        assertThat(matrix.get("PPR_ENGINEER")).containsExactlyInAnyOrder(
                PermissionConstants.PLANNED_SHUTDOWN_READ,
                PermissionConstants.PLANNED_SHUTDOWN_CREATE,
                PermissionConstants.PLANNED_SHUTDOWN_UPDATE,
                PermissionConstants.REPAIR_CAMPAIGN_READ,
                PermissionConstants.REPAIR_CAMPAIGN_CREATE,
                PermissionConstants.REPAIR_CAMPAIGN_UPDATE,
                PermissionConstants.REPAIR_CAMPAIGN_GENERATE_WORK_ORDERS
        );
        assertThat(matrix.get("RELIABILITY_ENGINEER")).containsExactlyInAnyOrder(
                PermissionConstants.PLANNED_SHUTDOWN_READ,
                PermissionConstants.REPAIR_CAMPAIGN_READ,
                PermissionConstants.REPAIR_CAMPAIGN_UPDATE
        );
        assertThat(matrix).doesNotContainKeys("MAINTENANCE_MANAGER", "MAINTENANCE_ENGINEER");
    }

    @Test
    void migrationReferencesOnlyBootstrapSeededRolesAndReportsIfOneIsMissing() throws Exception {
        Set<String> bootstrapRoles = bootstrapRoleCodes();
        Map<String, Set<String>> matrix = migrationMatrix();
        String sql = Files.readString(MIGRATION).toLowerCase();

        assertThat(bootstrapRoles).containsAll(matrix.keySet());
        assertThat(sql).contains("if not found then", "raise notice");
    }

    private Set<String> bootstrapRoleCodes() throws Exception {
        String source = Files.readString(DATA_BOOTSTRAP);
        Matcher matcher = Pattern.compile("\\{\\\"([A-Z_]+)\\\",.*?}").matcher(source);
        Set<String> roles = new LinkedHashSet<>();
        roles.add("SYSTEM_ADMIN");
        while (matcher.find()) {
            roles.add(matcher.group(1));
        }
        return roles;
    }

    private Map<String, Set<String>> migrationMatrix() throws Exception {
        String sql = Files.readString(MIGRATION);
        Matcher matcher = Pattern.compile(
                "append_role_permissions\\('([^']+)',\\s*ARRAY\\[(.*?)]\\)",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        ).matcher(sql);
        Map<String, Set<String>> matrix = new LinkedHashMap<>();
        while (matcher.find()) {
            Set<String> permissions = matrix.computeIfAbsent(matcher.group(1), ignored -> new LinkedHashSet<>());
            Arrays.stream(matcher.group(2).split(","))
                    .map(String::trim)
                    .map(value -> value.replace("'", ""))
                    .filter(value -> !value.isBlank())
                    .forEach(permissions::add);
        }
        return matrix;
    }
}
