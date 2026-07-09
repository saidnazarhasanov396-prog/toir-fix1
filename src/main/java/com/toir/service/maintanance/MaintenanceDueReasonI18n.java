package com.toir.service.maintanance;

import com.toir.enums.MaintenanceDueReasonCode;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Server-side translations for {@link MaintenanceDueReasonCode}, mirroring the frontend
 * {@code maintenanceDue.reasons.*} / {@code maintenanceDue.baseSourceSuffix.*} locale keys 1:1.
 * Lets API consumers get a pre-translated {@code explanation} by passing {@code ?lang=ru|uz|en}
 * (or an {@code Accept-Language} header) instead of requiring the frontend i18n bundle - useful
 * for consumers that don't have it (mobile apps, exports, notifications).
 */
public final class MaintenanceDueReasonI18n {

    private MaintenanceDueReasonI18n() {
    }

    /** Parses a {@code lang} query param or {@code Accept-Language} header value; null if unsupported/absent. */
    public static String normalizeLang(String lang) {
        if (lang == null || lang.isBlank()) {
            return null;
        }
        for (String part : lang.split(",")) {
            String token = part.trim();
            int weightIndex = token.indexOf(';');
            if (weightIndex >= 0) {
                token = token.substring(0, weightIndex).trim();
            }
            String normalized = token.toLowerCase(Locale.ROOT);
            int regionIndex = normalized.indexOf('-');
            if (regionIndex >= 0) {
                normalized = normalized.substring(0, regionIndex);
            }
            if ("ru".equals(normalized) || "uz".equals(normalized) || "en".equals(normalized)) {
                return normalized;
            }
        }
        return null;
    }

    public static String render(MaintenanceDueReasonCode code, Map<String, Object> params, String lang) {
        String template = TEMPLATES.getOrDefault(lang, TEMPLATES.get("ru")).get(code);
        String text = interpolate(template, params);
        Object baseSource = params == null ? null : params.get("baseSource");
        if (baseSource instanceof String baseSourceName
                && ("REGULATION_CREATED".equals(baseSourceName) || "OPERATION_START".equals(baseSourceName))) {
            String suffix = BASE_SOURCE_SUFFIX.getOrDefault(lang, BASE_SOURCE_SUFFIX.get("ru")).get(baseSourceName);
            if (suffix != null && !suffix.isBlank()) {
                text = text + " " + suffix;
            }
        }
        return text;
    }

    public static String renderFull(MaintenanceDueReasonCode primaryCode,
                                    Map<String, Object> primaryParams,
                                    List<MaintenanceDueReasonCode> supportingCodes,
                                    List<Map<String, Object>> supportingParams,
                                    String lang) {
        StringBuilder sb = new StringBuilder(render(primaryCode, primaryParams, lang));
        for (int i = 0; i < supportingCodes.size(); i++) {
            Map<String, Object> params = i < supportingParams.size() ? supportingParams.get(i) : Map.of();
            sb.append("; ").append(render(supportingCodes.get(i), params, lang));
        }
        return sb.toString();
    }

    /** Same as {@link #renderFull} but takes raw {@code reasonCode.name()} strings (persisted-event path). */
    public static String renderFullByName(String primaryCodeName,
                                          Map<String, Object> primaryParams,
                                          List<String> supportingCodeNames,
                                          List<Map<String, Object>> supportingParams,
                                          String lang) {
        MaintenanceDueReasonCode primaryCode = parseCode(primaryCodeName);
        if (primaryCode == null) {
            return null;
        }
        List<MaintenanceDueReasonCode> supportingCodes = supportingCodeNames.stream()
                .map(MaintenanceDueReasonI18n::parseCode)
                .filter(java.util.Objects::nonNull)
                .toList();
        return renderFull(primaryCode, primaryParams, supportingCodes, supportingParams, lang);
    }

    private static MaintenanceDueReasonCode parseCode(String name) {
        if (name == null) {
            return null;
        }
        try {
            return MaintenanceDueReasonCode.valueOf(name);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String interpolate(String template, Map<String, Object> params) {
        if (template == null) {
            return "";
        }
        if (params == null || params.isEmpty()) {
            return template;
        }
        String result = template;
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", String.valueOf(entry.getValue()));
        }
        return result;
    }

    private static final Map<MaintenanceDueReasonCode, String> RU = Map.ofEntries(
            Map.entry(MaintenanceDueReasonCode.MANUAL_TRIGGER_POLICY, "Ручной запуск по регламенту"),
            Map.entry(MaintenanceDueReasonCode.NO_TRIGGER_CONFIGURED, "Триггер ТО не настроен"),
            Map.entry(MaintenanceDueReasonCode.NO_CALENDAR_ANCHOR, "Нет даты отсчёта по календарю"),
            Map.entry(MaintenanceDueReasonCode.CALENDAR_OVERDUE, "Календарный триггер просрочен"),
            Map.entry(MaintenanceDueReasonCode.CALENDAR_DUE, "Наступил срок по календарю"),
            Map.entry(MaintenanceDueReasonCode.CALENDAR_UPCOMING, "Календарный срок приближается"),
            Map.entry(MaintenanceDueReasonCode.CALENDAR_NOT_DUE, "Календарный срок ещё не наступил"),
            Map.entry(MaintenanceDueReasonCode.REQUIRE_INITIAL_ANCHOR,
                    "Для этого календарного регламента требуется первичная точка отсчёта (акт выполнения)"),
            Map.entry(MaintenanceDueReasonCode.NO_COMPLETION_ANCHOR, "Нет точки отсчёта для календарного триггера"),
            Map.entry(MaintenanceDueReasonCode.MISSING_ACTIVE_METER, "Требуется активный счётчик: {{meterType}}"),
            Map.entry(MaintenanceDueReasonCode.METER_OVERDUE, "Триггер по счётчику просрочен"),
            Map.entry(MaintenanceDueReasonCode.METER_DUE, "Наступил срок по счётчику"),
            Map.entry(MaintenanceDueReasonCode.METER_UPCOMING, "Срок по счётчику приближается"),
            Map.entry(MaintenanceDueReasonCode.METER_NOT_DUE, "Срок по счётчику ещё не наступил"),
            Map.entry(MaintenanceDueReasonCode.WAITING_ALL_UPCOMING, "Ожидание всех триггеров ТО — срок приближается"),
            Map.entry(MaintenanceDueReasonCode.WAITING_ALL_NONE_DUE, "Ожидание всех триггеров ТО")
    );

    private static final Map<MaintenanceDueReasonCode, String> UZ = Map.ofEntries(
            Map.entry(MaintenanceDueReasonCode.MANUAL_TRIGGER_POLICY, "Reglament bo'yicha qo'lda ishga tushirish"),
            Map.entry(MaintenanceDueReasonCode.NO_TRIGGER_CONFIGURED, "TX trigeri sozlanmagan"),
            Map.entry(MaintenanceDueReasonCode.NO_CALENDAR_ANCHOR, "Kalendar bo'yicha boshlang'ich sana yo'q"),
            Map.entry(MaintenanceDueReasonCode.CALENDAR_OVERDUE, "Kalendar trigeri muddati o'tgan"),
            Map.entry(MaintenanceDueReasonCode.CALENDAR_DUE, "Kalendar bo'yicha muddat keldi"),
            Map.entry(MaintenanceDueReasonCode.CALENDAR_UPCOMING, "Kalendar muddati yaqinlashmoqda"),
            Map.entry(MaintenanceDueReasonCode.CALENDAR_NOT_DUE, "Kalendar muddati hali kelmagan"),
            Map.entry(MaintenanceDueReasonCode.REQUIRE_INITIAL_ANCHOR,
                    "Ushbu kalendar reglamenti uchun boshlang'ich nuqta (bajarilish akti) talab qilinadi"),
            Map.entry(MaintenanceDueReasonCode.NO_COMPLETION_ANCHOR, "Kalendar trigeri uchun boshlang'ich nuqta yo'q"),
            Map.entry(MaintenanceDueReasonCode.MISSING_ACTIVE_METER, "Faol hisoblagich talab qilinadi: {{meterType}}"),
            Map.entry(MaintenanceDueReasonCode.METER_OVERDUE, "Hisoblagich trigeri muddati o'tgan"),
            Map.entry(MaintenanceDueReasonCode.METER_DUE, "Hisoblagich bo'yicha muddat keldi"),
            Map.entry(MaintenanceDueReasonCode.METER_UPCOMING, "Hisoblagich muddati yaqinlashmoqda"),
            Map.entry(MaintenanceDueReasonCode.METER_NOT_DUE, "Hisoblagich muddati hali kelmagan"),
            Map.entry(MaintenanceDueReasonCode.WAITING_ALL_UPCOMING,
                    "Barcha TX trigerlari kutilmoqda — muddat yaqinlashmoqda"),
            Map.entry(MaintenanceDueReasonCode.WAITING_ALL_NONE_DUE, "Barcha TX trigerlari kutilmoqda")
    );

    private static final Map<MaintenanceDueReasonCode, String> EN = Map.ofEntries(
            Map.entry(MaintenanceDueReasonCode.MANUAL_TRIGGER_POLICY, "Manual trigger policy"),
            Map.entry(MaintenanceDueReasonCode.NO_TRIGGER_CONFIGURED, "No maintenance trigger configured"),
            Map.entry(MaintenanceDueReasonCode.NO_CALENDAR_ANCHOR, "No calendar anchor date"),
            Map.entry(MaintenanceDueReasonCode.CALENDAR_OVERDUE, "Calendar trigger overdue"),
            Map.entry(MaintenanceDueReasonCode.CALENDAR_DUE, "Calendar trigger due"),
            Map.entry(MaintenanceDueReasonCode.CALENDAR_UPCOMING, "Calendar trigger upcoming"),
            Map.entry(MaintenanceDueReasonCode.CALENDAR_NOT_DUE, "Calendar trigger not due"),
            Map.entry(MaintenanceDueReasonCode.REQUIRE_INITIAL_ANCHOR,
                    "Initial completion anchor is required for this calendar regulation"),
            Map.entry(MaintenanceDueReasonCode.NO_COMPLETION_ANCHOR, "No completion anchor for calendar trigger"),
            Map.entry(MaintenanceDueReasonCode.MISSING_ACTIVE_METER, "Required active meter is missing: {{meterType}}"),
            Map.entry(MaintenanceDueReasonCode.METER_OVERDUE, "Meter trigger overdue"),
            Map.entry(MaintenanceDueReasonCode.METER_DUE, "Meter trigger due"),
            Map.entry(MaintenanceDueReasonCode.METER_UPCOMING, "Meter trigger upcoming"),
            Map.entry(MaintenanceDueReasonCode.METER_NOT_DUE, "Meter trigger not due"),
            Map.entry(MaintenanceDueReasonCode.WAITING_ALL_UPCOMING, "Waiting for all maintenance triggers to become due"),
            Map.entry(MaintenanceDueReasonCode.WAITING_ALL_NONE_DUE, "Waiting for all maintenance triggers")
    );

    private static final Map<String, Map<MaintenanceDueReasonCode, String>> TEMPLATES = Map.of(
            "ru", RU,
            "uz", UZ,
            "en", EN
    );

    private static final Map<String, String> BASE_SOURCE_SUFFIX_RU = Map.of(
            "REGULATION_CREATED", "(от даты создания регламента)",
            "OPERATION_START", "(от даты ввода в эксплуатацию)"
    );

    private static final Map<String, String> BASE_SOURCE_SUFFIX_UZ = Map.of(
            "REGULATION_CREATED", "(reglament yaratilgan sanadan)",
            "OPERATION_START", "(ekspluatatsiyaga kiritilgan sanadan)"
    );

    private static final Map<String, String> BASE_SOURCE_SUFFIX_EN = Map.of(
            "REGULATION_CREATED", "(from regulation created date)",
            "OPERATION_START", "(from operation start date)"
    );

    private static final Map<String, Map<String, String>> BASE_SOURCE_SUFFIX = Map.of(
            "ru", BASE_SOURCE_SUFFIX_RU,
            "uz", BASE_SOURCE_SUFFIX_UZ,
            "en", BASE_SOURCE_SUFFIX_EN
    );
}
