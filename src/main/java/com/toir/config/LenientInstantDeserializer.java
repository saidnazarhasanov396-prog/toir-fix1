package com.toir.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;

import static java.time.temporal.ChronoField.HOUR_OF_DAY;
import static java.time.temporal.ChronoField.MINUTE_OF_HOUR;
import static java.time.temporal.ChronoField.NANO_OF_SECOND;
import static java.time.temporal.ChronoField.SECOND_OF_MINUTE;

/**
 * Accepts both strict ISO-8601 instants (with timezone) and local date-times (without timezone).
 * <p>
 * Examples accepted:
 * - 2026-04-24T17:46:00Z
 * - 2026-04-24T17:46:00+05:00
 * - 2026-04-24T17:46
 * - 2026-04-24 17:46
 */
public class LenientInstantDeserializer extends JsonDeserializer<Instant> {

    private static final DateTimeFormatter LOCAL_T_FORMAT = new DateTimeFormatterBuilder()
            .append(DateTimeFormatter.ISO_LOCAL_DATE)
            .appendLiteral('T')
            .appendValue(HOUR_OF_DAY, 2)
            .appendLiteral(':')
            .appendValue(MINUTE_OF_HOUR, 2)
            .optionalStart()
            .appendLiteral(':')
            .appendValue(SECOND_OF_MINUTE, 2)
            .optionalStart()
            .appendFraction(NANO_OF_SECOND, 0, 9, true)
            .optionalEnd()
            .optionalEnd()
            .toFormatter();

    private static final DateTimeFormatter LOCAL_SPACE_FORMAT = new DateTimeFormatterBuilder()
            .append(DateTimeFormatter.ISO_LOCAL_DATE)
            .appendLiteral(' ')
            .appendValue(HOUR_OF_DAY, 2)
            .appendLiteral(':')
            .appendValue(MINUTE_OF_HOUR, 2)
            .optionalStart()
            .appendLiteral(':')
            .appendValue(SECOND_OF_MINUTE, 2)
            .optionalStart()
            .appendFraction(NANO_OF_SECOND, 0, 9, true)
            .optionalEnd()
            .optionalEnd()
            .toFormatter();

    private final ZoneId defaultZone;

    public LenientInstantDeserializer(ZoneId defaultZone) {
        this.defaultZone = defaultZone;
    }

    @Override
    public Instant deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String raw = p.getValueAsString();
        if (raw == null) {
            return null;
        }
        String text = raw.trim();
        if (text.isEmpty()) {
            return null;
        }

        // 1) Strict instant
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException ignored) {
            // continue
        }

        // 2) Offset date-time (e.g. +05:00)
        try {
            return OffsetDateTime.parse(text).toInstant();
        } catch (DateTimeParseException ignored) {
            // continue
        }

        // 3) Local date-time without zone -> interpret with default zone
        LocalDateTime local = tryParseLocal(text);
        if (local != null) {
            return local.atZone(defaultZone).toInstant();
        }

        throw InvalidFormatException.from(p,
                "Invalid timestamp: " + text + ". Use ISO-8601, e.g. 2026-04-24T17:46:00Z, 2026-04-24T17:46:00+05:00, or 2026-04-24T17:46.",
                text,
                Instant.class);
    }

    private static LocalDateTime tryParseLocal(String text) {
        try {
            return LocalDateTime.parse(text, LOCAL_T_FORMAT);
        } catch (DateTimeParseException ignored) {
            // continue
        }
        try {
            return LocalDateTime.parse(text, LOCAL_SPACE_FORMAT);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }
}

