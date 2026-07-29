package com.toir.service.maintanance;

import com.toir.enums.MaintenanceScheduleContentHashVersion;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class MaintenanceScheduleCanonicalContentSerializerV1
        implements MaintenanceScheduleCanonicalContentSerializer {

    private static final int DATABASE_TIMESTAMP_PRECISION_NANOS = 1_000;

    @Override
    public MaintenanceScheduleContentHashVersion version() {
        return MaintenanceScheduleContentHashVersion.V1;
    }

    @Override
    public String serialize(MaintenanceScheduleCalculationContent content) {
        Objects.requireNonNull(content, "content");
        CanonicalWriter writer = new CanonicalWriter();
        writer.field("hashVersion", integer(version().persistedValue()));
        writer.field("planName", text(content.planName()));
        writer.field("notes", text(content.notes()));
        writer.field("periodStart", date(content.periodStart()));
        writer.field("periodEnd", date(content.periodEnd()));
        writer.field("selectionScopeType", enumName(content.selectionScopeType()));
        writer.field("planScopeType", enumName(content.planScopeType()));
        writer.field("departmentId", uuid(content.departmentId()));
        writer.field("anchorMode", enumName(content.anchorMode()));
        writer.field("recurrenceAnchor", enumName(content.recurrenceAnchor()));
        writer.field(
                "shiftFromExcludedWeekdays",
                Boolean.toString(content.shiftFromExcludedWeekdays())
        );
        writer.field(
                "calculationRevision",
                Long.toString(content.calculationRevision())
        );
        writeIds(writer, "equipmentIds", content.equipmentIds());
        writeIds(writer, "equipmentTypeIds", content.equipmentTypeIds());
        writeIds(writer, "regulationIds", content.regulationIds());
        writeWeekdays(writer, content.excludedWeekdays().stream()
                .sorted(Comparator.comparingInt(DayOfWeek::getValue))
                .toList());
        writeItems(writer, content.snapshotItems().stream()
                .sorted(Comparator.comparing(
                        MaintenanceScheduleCalculationItemContent::sourceItemKey,
                        Comparator.nullsFirst(Comparator.naturalOrder())
                ))
                .toList());
        return writer.toString();
    }

    private static void writeIds(
            CanonicalWriter writer,
            String fieldName,
            List<UUID> values) {
        List<UUID> sorted = values.stream()
                .sorted(Comparator.comparing(UUID::toString))
                .toList();
        writer.field(fieldName + ".size", integer(sorted.size()));
        for (int index = 0; index < sorted.size(); index++) {
            writer.field(fieldName + "[" + index + "]", uuid(sorted.get(index)));
        }
    }

    private static void writeWeekdays(
            CanonicalWriter writer,
            List<DayOfWeek> weekdays) {
        writer.field("excludedWeekdays.size", integer(weekdays.size()));
        for (int index = 0; index < weekdays.size(); index++) {
            writer.field(
                    "excludedWeekdays[" + index + "]",
                    enumName(weekdays.get(index))
            );
        }
    }

    private static void writeItems(
            CanonicalWriter writer,
            List<MaintenanceScheduleCalculationItemContent> items) {
        writer.field("snapshotItems.size", integer(items.size()));
        for (int index = 0; index < items.size(); index++) {
            String prefix = "snapshotItems[" + index + "].";
            MaintenanceScheduleCalculationItemContent item = items.get(index);
            writer.field(prefix + "sourceItemKey", text(item.sourceItemKey()));
            writer.field(
                    prefix + "sourceItemKeyVersion",
                    integer(item.sourceItemKeyVersion())
            );
            writer.field(prefix + "equipmentId", uuid(item.equipmentId()));
            writer.field(prefix + "regulationId", uuid(item.regulationId()));
            writer.field(
                    prefix + "maintenanceRuleId",
                    uuid(item.maintenanceRuleId())
            );
            writer.field(prefix + "templateId", uuid(item.templateId()));
            writer.field(
                    prefix + "maintenanceType",
                    enumName(item.maintenanceType())
            );
            writer.field(prefix + "triggerType", enumName(item.triggerType()));
            writer.field(
                    prefix + "triggerDiscriminator",
                    text(item.triggerDiscriminator())
            );
            writer.field(prefix + "cycleOrdinal", Long.toString(item.cycleOrdinal()));
            writer.field(prefix + "plannedDate", date(item.plannedDate()));
            writer.field(
                    prefix + "scheduledStart",
                    dateTime(item.scheduledStart())
            );
            writer.field(prefix + "scheduledEnd", dateTime(item.scheduledEnd()));
            writer.field(prefix + "dueDate", dateTime(item.dueDate()));
            writer.field(
                    prefix + "normativeLaborHours",
                    decimal(item.normativeLaborHours())
            );
            writer.field(prefix + "priority", enumName(item.priority()));
            writer.field(prefix + "departmentId", uuid(item.departmentId()));
            writer.field(
                    prefix + "taskTitleSnapshot",
                    text(item.taskTitleSnapshot())
            );
        }
    }

    private static String text(String value) {
        return value == null
                ? null
                : Normalizer.normalize(value.trim(), Normalizer.Form.NFC);
    }

    private static String uuid(UUID value) {
        return value == null
                ? null
                : value.toString().toLowerCase(Locale.ROOT);
    }

    private static String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private static String date(LocalDate value) {
        return value == null ? null : DateTimeFormatter.ISO_LOCAL_DATE.format(value);
    }

    private static String dateTime(LocalDateTime value) {
        if (value == null) {
            return null;
        }
        LocalDateTime microsecondPrecision = value.withNano(
                value.getNano() / DATABASE_TIMESTAMP_PRECISION_NANOS
                        * DATABASE_TIMESTAMP_PRECISION_NANOS
        );
        return DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(microsecondPrecision);
    }

    private static String integer(int value) {
        return Integer.toString(value);
    }

    private static String decimal(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.signum() == 0
                ? "0"
                : value.stripTrailingZeros().toPlainString();
    }

    private static final class CanonicalWriter {

        private final StringBuilder value = new StringBuilder();

        void field(String name, String fieldValue) {
            value.append(name).append('=');
            if (fieldValue == null) {
                value.append('N');
            } else {
                value.append('V')
                        .append(fieldValue.getBytes(StandardCharsets.UTF_8).length)
                        .append(':')
                        .append(fieldValue);
            }
            value.append('\n');
        }

        @Override
        public String toString() {
            return value.toString();
        }
    }
}
