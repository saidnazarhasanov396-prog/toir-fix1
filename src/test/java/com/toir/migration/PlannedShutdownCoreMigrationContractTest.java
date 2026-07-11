package com.toir.migration;

import jakarta.persistence.Column;
import jakarta.persistence.Enumerated;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlannedShutdownCoreMigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V20260711_4__planned_shutdown_core.sql");
    private static final Path SCOPE_VERSION_MIGRATION = Path.of(
            "src/main/resources/db/migration/V20260711_5__planned_shutdown_scope_version.sql");
    private static final Path WORK_ITEM_EQUIPMENT_MIGRATION = Path.of(
            "src/main/resources/db/migration/V20260711_6__planned_shutdown_work_item_equipment_required.sql");
    private static final Path APPROVAL_ROUTE_MIGRATION = Path.of(
            "src/main/resources/db/migration/V20260711_7__planned_shutdown_approval_route.sql");

    @Test
    void migrationCreatesTheCompleteShutdownAggregateWithExplicitForeignKeys() throws Exception {
        assertThat(Files.exists(MIGRATION)).isTrue();
        String sql = Files.readString(MIGRATION).toLowerCase();

        for (String table : List.of(
                "planned_shutdown_assets",
                "planned_shutdown_work_items",
                "planned_shutdown_readiness_items",
                "planned_shutdown_isolation_points",
                "planned_shutdown_status_history",
                "planned_shutdown_startup_tests",
                "planned_shutdown_closure_snapshots")) {
            assertThat(sql).contains("create table " + table);
            assertThat(sql).contains("constraint fk_" + table + "_shutdown");
            assertThat(sql).contains("foreign key (planned_shutdown_id) references planned_shutdowns(id)");
        }

        assertThat(sql)
                .contains("foreign key (equipment_id) references equipment(id)")
                .contains("foreign key (responsible_employee_id) references hr_employees(id)")
                .contains("foreign key (permit_id) references safety_permits(id)")
                .contains("foreign key (actor_id) references users(id)");
    }

    @Test
    void migrationDefinesLifecycleAndEveryChildStatusConstraint() throws Exception {
        String sql = Files.readString(MIGRATION);

        for (String status : List.of(
                "DRAFT", "SCOPE_FORMATION", "READINESS_CHECK", "PENDING_APPROVAL", "APPROVED",
                "PREPARATION", "SHUTDOWN_STARTED", "SAFE_STATE", "REPAIR_IN_PROGRESS", "TESTING",
                "STARTUP", "COMPLETED", "CLOSED", "CANCELLED", "RESCHEDULED", "EMERGENCY_EXTENDED")) {
            assertThat(sql).contains("'" + status + "'");
        }
        assertThat(sql)
                .contains("chk_planned_shutdown_assets_disposition")
                .contains("chk_planned_shutdown_work_items_source_type")
                .contains("chk_planned_shutdown_work_items_status")
                .contains("chk_planned_shutdown_readiness_severity")
                .contains("chk_planned_shutdown_readiness_status")
                .contains("chk_planned_shutdown_isolation_status")
                .contains("chk_planned_shutdown_startup_tests_status")
                .contains("chk_planned_shutdown_window")
                .contains("chk_planned_shutdown_approved_window");
    }

    @Test
    void migrationDefinesActiveRowUniquenessAndWorkOrderLinks() throws Exception {
        String sql = Files.readString(MIGRATION).toLowerCase().replaceAll("\\s+", " ");

        for (String index : List.of(
                "uq_planned_shutdowns_active_code",
                "uq_planned_shutdown_assets_active_equipment",
                "uq_planned_shutdown_work_items_active_source",
                "uq_planned_shutdown_work_items_active_order",
                "uq_planned_shutdown_readiness_active_key",
                "uq_planned_shutdown_isolation_active_lock_tag",
                "uq_planned_shutdown_startup_tests_active_code",
                "uq_planned_shutdown_closure_active_shutdown")) {
            assertThat(sql).contains("create unique index " + index);
        }
        assertThat(sql).contains("where is_deleted = false");

        assertThat(sql)
                .contains("add column planned_shutdown_id uuid")
                .contains("add column shutdown_work_item_id uuid")
                .contains("constraint uq_planned_shutdown_work_items_id_shutdown unique (id, planned_shutdown_id)")
                .contains("fk_work_orders_planned_shutdown")
                .contains("foreign key (planned_shutdown_id) references planned_shutdowns(id)")
                .contains("fk_work_orders_shutdown_work_item_owner")
                .contains("foreign key (shutdown_work_item_id, planned_shutdown_id) references planned_shutdown_work_items(id, planned_shutdown_id)")
                .contains("shutdown_work_item_id is null or planned_shutdown_id is not null")
                .contains("idx_work_orders_planned_shutdown_id")
                .contains("idx_work_orders_shutdown_work_item_id");
    }

    @Test
    void migrationAddsRootMetadataAndConservativelyBackfillsLegacyRows() throws Exception {
        String sql = Files.readString(MIGRATION).toLowerCase();

        for (String column : List.of(
                "code varchar(64)", "shutdown_type varchar(64)", "responsible_employee_id uuid",
                "objective text", "notes text", "risk_level varchar(32)", "risk_score numeric(9,4)",
                "approval_scope_version bigint", "approval_scope_hash varchar(128)",
                "planned_start_at timestamptz", "planned_end_at timestamptz",
                "approved_start_at timestamptz", "approved_end_at timestamptz",
                "effective_extension_end_at timestamptz", "actual_shutdown_at timestamptz",
                "actual_safe_state_at timestamptz", "actual_repair_start_at timestamptz",
                "actual_testing_start_at timestamptz", "actual_startup_at timestamptz",
                "actual_completed_at timestamptz", "reschedule_reason text", "extension_reason text",
                "closure_version bigint")) {
            assertThat(sql).contains(column);
        }

        assertThat(sql)
                .contains("planned_start_at = start_at")
                .contains("planned_end_at = end_at")
                .contains("chk_planned_shutdown_window check (planned_end_at > planned_start_at) not valid")
                .contains("when 'generated' then 'scope_formation'")
                .contains("when 'approved' then 'pending_approval'")
                .contains("when 'in_progress' then 'readiness_check'")
                .doesNotContain("when 'approved' then 'approved'")
                .doesNotContain("when 'in_progress' then 'repair_in_progress'");

        String backfill = sql.substring(sql.indexOf("update planned_shutdowns"),
                sql.indexOf("alter table planned_shutdowns", sql.indexOf("update planned_shutdowns")));
        assertThat(backfill).doesNotContain("actual_shutdown_at =")
                .doesNotContain("actual_safe_state_at =")
                .doesNotContain("actual_completed_at =")
                .doesNotContain("approved_start_at =")
                .doesNotContain("approved_end_at =");
    }

    @Test
    void dedicatedEnumsAndAggregateRootMappingMatchTheSchema() throws Exception {
        assertEnumValues("com.toir.enums.PlannedShutdownStatus",
                "DRAFT", "SCOPE_FORMATION", "READINESS_CHECK", "PENDING_APPROVAL", "APPROVED",
                "PREPARATION", "SHUTDOWN_STARTED", "SAFE_STATE", "REPAIR_IN_PROGRESS", "TESTING",
                "STARTUP", "COMPLETED", "CLOSED", "CANCELLED", "RESCHEDULED", "EMERGENCY_EXTENDED");
        assertEnumValues("com.toir.enums.PlannedShutdownAssetDisposition", "STOPPED", "RESERVE", "RUNNING");
        assertEnumValues("com.toir.enums.PlannedShutdownWorkItemSourceType",
                "MANUAL", "DEFECT", "PPR", "REPAIR_REQUEST", "INSPECTION_ROUND", "WORK_ORDER", "REPAIR_CAMPAIGN");
        assertEnumValues("com.toir.enums.PlannedShutdownReadinessSeverity", "CRITICAL", "WARNING");
        assertEnumValues("com.toir.enums.PlannedShutdownItemStatus",
                "PENDING", "IN_PROGRESS", "PASSED", "FAILED", "WAIVED");

        Class<?> aggregate = Class.forName("com.toir.entity.PlannedShutdown");
        Field status = aggregate.getDeclaredField("status");
        assertThat(status.getType().getName()).isEqualTo("com.toir.enums.PlannedShutdownStatus");
        assertThat(status.getAnnotation(Enumerated.class)).isNotNull();

        for (String fieldName : List.of(
                "code", "shutdownType", "responsibleEmployeeId", "objective", "notes", "riskLevel",
                "riskScore", "scopeVersion", "approvalScopeVersion", "approvalScopeHash", "plannedStartAt", "plannedEndAt",
                "approvedStartAt", "approvedEndAt", "effectiveExtensionEndAt", "actualShutdownAt",
                "actualSafeStateAt", "actualRepairStartAt", "actualTestingStartAt", "actualStartupAt",
                "actualCompletedAt", "rescheduleReason", "extensionReason", "closureVersion")) {
            Field field = aggregate.getDeclaredField(fieldName);
            assertThat(field.getAnnotation(Column.class)).as(fieldName).isNotNull();
        }
    }

    @Test
    void scopeVersionIsAddedForwardOnlyAndCannotBecomeNegative() throws Exception {
        assertThat(SCOPE_VERSION_MIGRATION).exists();
        String sql = Files.readString(SCOPE_VERSION_MIGRATION).toLowerCase().replaceAll("\\s+", " ");
        assertThat(sql)
                .contains("add column scope_version bigint not null default 0")
                .contains("check (scope_version >= 0)");
    }

    @Test
    void activeCodeConstraintKeepsTheStableNameUsedByConflictClassification() throws Exception {
        String sql = Files.readString(MIGRATION).toLowerCase().replaceAll("\\s+", " ");
        assertThat(sql).contains(
                "create unique index uq_planned_shutdowns_active_code on planned_shutdowns (code) where is_deleted = false");
    }

    @Test
    void workItemEquipmentBecomesRequiredWithAFailSafePrecondition() throws Exception {
        assertThat(WORK_ITEM_EQUIPMENT_MIGRATION).exists();
        String sql = Files.readString(WORK_ITEM_EQUIPMENT_MIGRATION).toLowerCase().replaceAll("\\s+", " ");
        assertThat(sql)
                .contains("where equipment_id is null and is_deleted = false")
                .contains("raise exception")
                .contains("alter column equipment_id set not null");
        Field equipmentId = Class.forName("com.toir.entity.plannedshutdown.PlannedShutdownWorkItem")
                .getDeclaredField("equipmentId");
        assertThat(equipmentId.getAnnotation(Column.class).nullable()).isFalse();
    }

    @Test
    void approvalRouteSeedsExactIndependentProductionAndHseRoles() throws Exception {
        String sql = Files.readString(APPROVAL_ROUTE_MIGRATION);
        assertThat(sql).contains("'PLANNED_SHUTDOWN_APPROVAL'")
                .contains("(1, 'PLANNED_SHUTDOWN_PRODUCTION_APPROVER')")
                .contains("(2, 'PLANNED_SHUTDOWN_HSE_APPROVER')")
                .doesNotContain("PRODUCTION_MANAGER")
                .doesNotContain("HSE_MANAGER");
    }

    private static void assertEnumValues(String className, String... expected) throws Exception {
        Class<?> enumType = Class.forName(className);
        assertThat(enumType.isEnum()).isTrue();
        assertThat(Arrays.stream(enumType.getEnumConstants()).map(Object::toString))
                .containsExactly(expected);
    }
}
