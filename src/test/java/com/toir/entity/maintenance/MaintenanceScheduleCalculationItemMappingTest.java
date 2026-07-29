package com.toir.entity.maintenance;

import static org.assertj.core.api.Assertions.assertThat;

import com.toir.dto.pprplanning.PprTaskDto;
import com.toir.entity.BaseEntity;
import com.toir.entity.PprTask;
import jakarta.persistence.Column;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.junit.jupiter.api.Test;

class MaintenanceScheduleCalculationItemMappingTest {

    @Test
    void snapshotEntityUsesImmutableBaseEntityAndCanonicalTable() {
        Class<MaintenanceScheduleCalculationItem> type =
                MaintenanceScheduleCalculationItem.class;

        assertThat(BaseEntity.class).isAssignableFrom(type);
        assertThat(type.getAnnotation(Immutable.class)).isNotNull();
        assertThat(type.getAnnotation(Table.class).name())
                .isEqualTo("maintenance_schedule_calculation_items");
    }

    @Test
    void planIsARequiredNonCascadingInsertOnlyRelationship() throws Exception {
        Field plan = field(MaintenanceScheduleCalculationItem.class, "plan");
        ManyToOne relationship = plan.getAnnotation(ManyToOne.class);
        JoinColumn joinColumn = plan.getAnnotation(JoinColumn.class);

        assertThat(relationship).isNotNull();
        assertThat(relationship.optional()).isFalse();
        assertThat(relationship.cascade()).isEmpty();
        assertThat(joinColumn.name()).isEqualTo("plan_id");
        assertThat(joinColumn.nullable()).isFalse();
        assertThat(joinColumn.updatable()).isFalse();
    }

    @Test
    void snapshotMapsIdentityScheduleAndHistoricalDisplayFieldsAsInsertOnly()
            throws Exception {
        assertInsertOnlyColumn("calculationRevision", "calculation_revision");
        assertInsertOnlyColumn("sourceItemKey", "source_item_key");
        assertInsertOnlyColumn("sourceItemKeyVersion", "source_item_key_version");
        assertInsertOnlyColumn("equipmentId", "equipment_id");
        assertInsertOnlyColumn("regulationId", "regulation_id");
        assertInsertOnlyColumn("maintenanceRuleId", "maintenance_rule_id");
        assertInsertOnlyColumn("templateId", "template_id");
        assertInsertOnlyColumn("maintenanceType", "maintenance_type");
        assertInsertOnlyColumn("triggerType", "trigger_type");
        assertInsertOnlyColumn("triggerDiscriminator", "trigger_discriminator");
        assertInsertOnlyColumn("cycleOrdinal", "cycle_ordinal");
        assertInsertOnlyColumn("plannedDate", "planned_date");
        assertInsertOnlyColumn("scheduledStart", "scheduled_start");
        assertInsertOnlyColumn("scheduledEnd", "scheduled_end");
        assertInsertOnlyColumn("dueDate", "due_date");
        assertInsertOnlyColumn("normativeLaborHours", "normative_labor_hours");
        assertInsertOnlyColumn("priority", "priority");
        assertInsertOnlyColumn("departmentId", "department_id");
        assertInsertOnlyColumn("equipmentCodeSnapshot", "equipment_code_snapshot");
        assertInsertOnlyColumn("equipmentNameSnapshot", "equipment_name_snapshot");
        assertInsertOnlyColumn("regulationNameSnapshot", "regulation_name_snapshot");
        assertInsertOnlyColumn("maintenanceRuleNameSnapshot", "maintenance_rule_name_snapshot");
        assertInsertOnlyColumn("templateNameSnapshot", "template_name_snapshot");
        assertInsertOnlyColumn("taskTitleSnapshot", "task_title_snapshot");
    }

    @Test
    void pprTaskTraceabilityIsNullableInsertOnlyAndNotExposedByDto() throws Exception {
        Field sourceItemId = field(PprTask.class, "sourceCalculationItemId");
        Column column = sourceItemId.getAnnotation(Column.class);

        assertThat(sourceItemId.getType()).isEqualTo(UUID.class);
        assertThat(column.name()).isEqualTo("source_calculation_item_id");
        assertThat(column.nullable()).isTrue();
        assertThat(column.updatable()).isFalse();
        assertThat(new PprTask().getSourceCalculationItemId()).isNull();
        assertThat(Arrays.stream(PprTaskDto.class.getRecordComponents())
                .map(component -> component.getName())
                .toList())
                .doesNotContain("sourceCalculationItemId", "sourceCalculationItem");
    }

    private static void assertInsertOnlyColumn(String fieldName, String columnName)
            throws Exception {
        Column column = field(MaintenanceScheduleCalculationItem.class, fieldName)
                .getAnnotation(Column.class);
        assertThat(column).isNotNull();
        assertThat(column.name()).isEqualTo(columnName);
        assertThat(column.updatable()).isFalse();
    }

    private static Field field(Class<?> owner, String fieldName)
            throws NoSuchFieldException {
        return owner.getDeclaredField(fieldName);
    }
}
