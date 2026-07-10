package com.toir.entity.sparepartlifecycle;

import com.toir.enums.sparepartlifecycle.SparePartCalendarUnit;
import com.toir.enums.sparepartlifecycle.SparePartDueAction;
import com.toir.enums.sparepartlifecycle.SparePartDueEventState;
import com.toir.enums.sparepartlifecycle.SparePartInstallationStatus;
import com.toir.enums.sparepartlifecycle.SparePartLifeCombinationMode;
import com.toir.enums.sparepartlifecycle.SparePartLifeLimitKind;
import com.toir.enums.sparepartlifecycle.SparePartLifeRuleScope;
import com.toir.enums.sparepartlifecycle.SparePartLifecycleCommandStatus;
import com.toir.enums.sparepartlifecycle.SparePartLifecycleCommandType;
import com.toir.enums.sparepartlifecycle.SparePartLifecycleEvaluationState;
import com.toir.enums.sparepartlifecycle.SparePartOperationalReadiness;
import com.toir.enums.sparepartlifecycle.SparePartRemovalDisposition;
import jakarta.persistence.Column;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.util.Set;
import org.hibernate.annotations.JdbcTypeCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SparePartLifecycleEntityContractTest {

    @Test
    void entitiesMapToDedicatedAdditiveTables() {
        assertTable(SparePartLifeRule.class, "spare_part_life_rules");
        assertTable(SparePartLifeLimit.class, "spare_part_life_limits");
        assertTable(SparePartInstallation.class, "spare_part_installations");
        assertTable(SparePartInstallationMeterBaseline.class, "spare_part_installation_meter_baselines");
        assertTable(SparePartInstallationMaterialAllocation.class, "spare_part_installation_material_allocations");
        assertTable(SparePartLifecycleCommand.class, "spare_part_lifecycle_commands");
        assertTable(SparePartDueEvent.class, "spare_part_due_events");
    }

    @Test
    void auditableResourceFieldsUseBigDecimal() throws Exception {
        assertThat(SparePartLifeLimit.class.getDeclaredField("limitValue").getType()).isEqualTo(BigDecimal.class);
        assertThat(SparePartLifeLimit.class.getDeclaredField("warningBeforeValue").getType()).isEqualTo(BigDecimal.class);
        assertThat(SparePartInstallation.class.getDeclaredField("quantity").getType()).isEqualTo(BigDecimal.class);
        assertThat(SparePartInstallationMeterBaseline.class.getDeclaredField("baselineValue").getType()).isEqualTo(BigDecimal.class);
        assertThat(SparePartInstallationMaterialAllocation.class.getDeclaredField("allocatedQuantity").getType()).isEqualTo(BigDecimal.class);
        assertThat(SparePartDueEvent.class.getDeclaredField("dueMeterValue").getType()).isEqualTo(BigDecimal.class);
    }

    @Test
    void mutableAggregatesUseOptimisticVersionsAndJsonbSnapshots() throws Exception {
        assertThat(SparePartLifeRule.class.getDeclaredField("version").getAnnotation(Version.class)).isNotNull();
        assertThat(SparePartInstallation.class.getDeclaredField("version").getAnnotation(Version.class)).isNotNull();
        assertThat(SparePartDueEvent.class.getDeclaredField("version").getAnnotation(Version.class)).isNotNull();
        assertThat(SparePartInstallation.class.getDeclaredField("appliedRuleSnapshot").getAnnotation(JdbcTypeCode.class)).isNotNull();
        assertThat(SparePartInstallation.class.getDeclaredField("evaluationDetails").getAnnotation(JdbcTypeCode.class)).isNotNull();
        assertThat(SparePartDueEvent.class.getDeclaredField("reasons").getAnnotation(JdbcTypeCode.class)).isNotNull();
    }

    @Test
    void installationPositionIsImmutableAtJpaLevel() throws Exception {
        Column column = SparePartInstallation.class.getDeclaredField("positionKey").getAnnotation(Column.class);

        assertThat(column).isNotNull();
        assertThat(column.nullable()).isFalse();
        assertThat(column.updatable()).isFalse();
    }

    @Test
    void lifecycleEnumsExposeTheRequiredClosedVocabulary() {
        assertThat(Set.of(SparePartLifeRuleScope.values())).containsExactlyInAnyOrder(
                SparePartLifeRuleScope.CATALOG,
                SparePartLifeRuleScope.EQUIPMENT,
                SparePartLifeRuleScope.NODE,
                SparePartLifeRuleScope.NODE_SLOT
        );
        assertThat(Set.of(SparePartLifeCombinationMode.values())).containsExactlyInAnyOrder(
                SparePartLifeCombinationMode.ANY,
                SparePartLifeCombinationMode.ALL,
                SparePartLifeCombinationMode.MANUAL
        );
        assertThat(Set.of(SparePartLifeLimitKind.values())).containsExactlyInAnyOrder(
                SparePartLifeLimitKind.CALENDAR,
                SparePartLifeLimitKind.METER
        );
        assertThat(Set.of(SparePartCalendarUnit.values())).containsExactlyInAnyOrder(
                SparePartCalendarUnit.DAY,
                SparePartCalendarUnit.MONTH,
                SparePartCalendarUnit.YEAR
        );
        assertThat(Set.of(SparePartDueAction.values())).containsExactlyInAnyOrder(
                SparePartDueAction.WARNING_ONLY,
                SparePartDueAction.MAINTENANCE_REQUIRED,
                SparePartDueAction.BLOCK_OPERATION
        );
        assertThat(Set.of(SparePartInstallationStatus.values())).containsExactlyInAnyOrder(
                SparePartInstallationStatus.ACTIVE,
                SparePartInstallationStatus.REPLACED,
                SparePartInstallationStatus.REMOVED
        );
        assertThat(Set.of(SparePartLifecycleEvaluationState.values())).containsExactlyInAnyOrder(
                SparePartLifecycleEvaluationState.OK,
                SparePartLifecycleEvaluationState.WARNING,
                SparePartLifecycleEvaluationState.DUE,
                SparePartLifecycleEvaluationState.OVERDUE,
                SparePartLifecycleEvaluationState.ERROR
        );
        assertThat(Set.of(SparePartRemovalDisposition.values())).containsExactlyInAnyOrder(
                SparePartRemovalDisposition.SCRAP,
                SparePartRemovalDisposition.REPAIR_HOLD,
                SparePartRemovalDisposition.RETURN_TO_STOCK,
                SparePartRemovalDisposition.UNKNOWN
        );
        assertThat(Set.of(SparePartLifecycleCommandType.values())).containsExactlyInAnyOrder(
                SparePartLifecycleCommandType.INSTALL,
                SparePartLifecycleCommandType.REMOVE,
                SparePartLifecycleCommandType.REPLACE
        );
        assertThat(Set.of(SparePartLifecycleCommandStatus.values())).containsExactlyInAnyOrder(
                SparePartLifecycleCommandStatus.IN_PROGRESS,
                SparePartLifecycleCommandStatus.SUCCEEDED
        );
        assertThat(Set.of(SparePartDueEventState.values())).containsExactlyInAnyOrder(
                SparePartDueEventState.UPCOMING,
                SparePartDueEventState.WARNING,
                SparePartDueEventState.DUE,
                SparePartDueEventState.OVERDUE,
                SparePartDueEventState.RESOLVED
        );
        assertThat(Set.of(SparePartOperationalReadiness.values())).containsExactlyInAnyOrder(
                SparePartOperationalReadiness.READY,
                SparePartOperationalReadiness.WARNING,
                SparePartOperationalReadiness.MAINTENANCE_REQUIRED,
                SparePartOperationalReadiness.BLOCKED,
                SparePartOperationalReadiness.EVALUATION_ERROR
        );
    }

    private static void assertTable(Class<?> entityType, String tableName) {
        Table table = entityType.getAnnotation(Table.class);
        assertThat(table).isNotNull();
        assertThat(table.name()).isEqualTo(tableName);
    }
}
