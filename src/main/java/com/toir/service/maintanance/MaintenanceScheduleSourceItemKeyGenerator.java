package com.toir.service.maintanance;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class MaintenanceScheduleSourceItemKeyGenerator {

    public static final int VERSION = 1;

    public int version() {
        return VERSION;
    }

    public String generate(MaintenanceScheduleSourceItemCoordinates coordinates) {
        Objects.requireNonNull(coordinates, "coordinates");
        StringBuilder canonical = new StringBuilder();
        append(canonical, "version", "v" + VERSION);
        append(canonical, "equipmentId", uuid(coordinates.equipmentId()));
        append(canonical, "regulationId", uuid(coordinates.regulationId()));
        append(canonical, "maintenanceRuleId", uuid(coordinates.maintenanceRuleId()));
        append(canonical, "templateId", uuid(coordinates.templateId()));
        append(canonical, "triggerType", enumName(coordinates.triggerType()));
        append(canonical, "triggerDiscriminator", text(coordinates.triggerDiscriminator()));
        append(canonical, "maintenanceType", enumName(coordinates.maintenanceType()));
        append(canonical, "plannedDate", nullable(coordinates.plannedDate().toString()));
        append(
                canonical,
                "scheduledStart",
                coordinates.scheduledStart() == null
                        ? nullable(null)
                        : nullable(DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(
                                coordinates.scheduledStart()))
        );
        append(canonical, "cycleOrdinal", Long.toString(coordinates.cycleOrdinal()));
        return sha256(canonical.toString());
    }

    private static void append(StringBuilder target, String name, String value) {
        target.append(name)
                .append('=')
                .append(value.length())
                .append(':')
                .append(value)
                .append('\n');
    }

    private static String uuid(UUID value) {
        return nullable(value == null
                ? null
                : value.toString().toLowerCase(Locale.ROOT));
    }

    private static String enumName(Enum<?> value) {
        return nullable(value == null ? null : value.name());
    }

    private static String text(String value) {
        return nullable(value == null ? null : value.trim());
    }

    private static String nullable(String value) {
        return value == null
                ? "N"
                : "V" + value.length() + ":" + value;
    }

    private static String sha256(String canonical) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
