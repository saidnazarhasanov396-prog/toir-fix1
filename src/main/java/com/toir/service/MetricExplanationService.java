package com.toir.service;

import com.toir.dto.analytics.MetricExplanationDto;
import com.toir.dto.analytics.MetricExplanationStepDto;
import com.toir.dto.rcm.RiskExplanationDto;
import com.toir.dto.rcm.RiskReasonCategory;
import com.toir.dto.rcm.RiskReasonCode;
import com.toir.dto.rcm.RiskReasonDto;
import com.toir.dto.rcm.RiskSeverity;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
public class MetricExplanationService {

    private static final String DEFAULT_LOCALE = "en";

    public String normalizeLocale(String lang) {
        if (lang == null || lang.isBlank()) {
            return DEFAULT_LOCALE;
        }
        for (String part : lang.split(",")) {
            String token = part.trim();
            if (token.isBlank()) {
                continue;
            }
            int weightIndex = token.indexOf(';');
            if (weightIndex >= 0) {
                token = token.substring(0, weightIndex).trim();
            }
            String normalized = token.toLowerCase(Locale.ROOT);
            int regionIndex = normalized.indexOf('-');
            if (regionIndex >= 0) {
                normalized = normalized.substring(0, regionIndex);
            }
            if ("uz".equals(normalized) || "en".equals(normalized) || "ru".equals(normalized)) {
                return normalized;
            }
        }
        return DEFAULT_LOCALE;
    }

    public RiskExplanationDto rcmRisk(String lang, EquipmentRiskScoringResult result) {
        return rcmRisk(lang, result, null);
    }

    public RiskExplanationDto rcmRisk(String lang, EquipmentRiskScoringResult result, EquipmentRiskEvidence evidence) {
        String locale = normalizeLocale(lang);
        RcmText text = rcmText(locale);
        List<RiskReasonDto> reasons = result.reasons().stream()
                .map(reason -> riskReason(locale, reason))
                .toList();
        String primaryReason = result.primaryReason() == null
                ? reasonText(locale, new EquipmentRiskScoringReason(
                RiskReasonCode.BASELINE_PROBABILITY,
                RiskReasonCategory.PROBABILITY,
                0,
                0,
                1,
                RiskSeverity.LOW))
                : reasonText(locale, result.primaryReason());

        return new RiskExplanationDto(
                locale,
                text.formula(),
                text.summary(result.riskScore(), primaryReason),
                reasons,
                rcmSteps(locale, text, result, evidence)
        );
    }

    public MetricExplanationDto availability(String lang,
                                             double observedHours,
                                             double downtimeHours,
                                             double operatingHours,
                                             double availabilityPct) {
        String locale = normalizeLocale(lang);
        AvailabilityText text = availabilityText(locale);
        return new MetricExplanationDto(
                locale,
                text.formula(),
                text.summary(availabilityPct, observedHours, downtimeHours),
                List.of(
                        new MetricExplanationStepDto(
                                text.observedTime(),
                                round2(observedHours),
                                text.hoursUnit(),
                                durationHours(locale, observedHours)),
                        new MetricExplanationStepDto(
                                text.downtime(),
                                round2(downtimeHours),
                                text.hoursUnit(),
                                durationHours(locale, downtimeHours)),
                        new MetricExplanationStepDto(
                                text.operatingTime(),
                                round2(operatingHours),
                                text.hoursUnit(),
                                durationHours(locale, operatingHours)),
                        new MetricExplanationStepDto(text.availability(), round2(availabilityPct), "%")
                )
        );
    }

    private RcmText rcmText(String locale) {
        return switch (locale) {
            case "uz" -> new RcmText(
                    "min(100, oqibat × ehtimollik)",
                    "Oqibat",
                    "Ehtimollik",
                    "Yakuniy xavf",
                    (riskScore, primaryReason) ->
                            "Xavf %d/100, chunki %s".formatted(riskScore, primaryReason));
            case "ru" -> new RcmText(
                    "min(100, последствие × вероятность)",
                    "Последствие",
                    "Вероятность",
                    "Итоговый риск",
                    (riskScore, primaryReason) ->
                            "Риск %d/100, потому что %s".formatted(riskScore, primaryReason));
            default -> new RcmText(
                    "min(100, consequence × probability)",
                    "Consequence",
                    "Probability",
                    "Final risk",
                    (riskScore, primaryReason) ->
                            "Risk is %d/100 because %s".formatted(riskScore, primaryReason));
        };
    }

    private List<MetricExplanationStepDto> rcmSteps(
            String locale,
            RcmText text,
            EquipmentRiskScoringResult result,
            EquipmentRiskEvidence evidence
    ) {
        return List.of(
                new MetricExplanationStepDto(text.consequenceTotal(), result.consequenceScore()),
                new MetricExplanationStepDto(text.probability(), result.probabilityScore()),
                new MetricExplanationStepDto(text.finalRisk(), result.riskScore(), "/100")
        );
    }

    private RiskReasonDto riskReason(String locale, EquipmentRiskScoringReason reason) {
        return new RiskReasonDto(
                reason.code(),
                reason.category(),
                reasonLabel(locale, reason.code()),
                reason.value(),
                reasonEffect(locale, reason),
                reason.severity()
        );
    }

    private String reasonLabel(String locale, RiskReasonCode code) {
        return switch (code) {
            case OPEN_DEFECTS_CRITICAL, OPEN_DEFECTS_HIGH, OPEN_DEFECTS_MEDIUM ->
                    localized(locale, "Open defects", "Ochiq nuqsonlar", "Открытые дефекты");
            case LOW_MTBF_HIGH, LOW_MTBF_MEDIUM ->
                    localized(locale, "MTBF", "MTBF", "MTBF");
            case RECURRING_DEFECTS ->
                    localized(locale, "Recurring defects", "Takroriy nuqsonlar", "Повторяющиеся дефекты");
            case OVERDUE_MAINTENANCE ->
                    localized(locale, "Overdue maintenance", "Kechikkan texnik xizmat", "Просроченное обслуживание");
            case BASELINE_PROBABILITY ->
                    localized(locale, "Baseline probability", "Bazaviy ehtimollik", "Базовая вероятность");
            case CRITICAL_EQUIPMENT, HIGH_CRITICALITY, MEDIUM_CRITICALITY, LOW_CRITICALITY ->
                    localized(locale, "Criticality", "Kritiklik", "Критичность");
            case RECENT_DOWNTIME ->
                    localized(locale, "Recent downtime", "So'nggi to'xtash", "Недавний простой");
            case HIGH_MTTR ->
                    localized(locale, "MTTR", "MTTR", "MTTR");
            case OPEN_HIGH_REPAIR_REQUEST ->
                    localized(locale, "Open high repair request", "Ochiq yuqori ta'mir so'rovi", "Открытая срочная заявка на ремонт");
            case EQUIPMENT_UNAVAILABLE ->
                    localized(locale, "Equipment status", "Uskuna holati", "Статус оборудования");
            case INSPECTION_OR_CALIBRATION_ISSUE ->
                    localized(locale, "Inspection or calibration", "Tekshiruv yoki kalibrlash", "Осмотр или калибровка");
        };
    }

    private String reasonEffect(String locale, EquipmentRiskScoringReason reason) {
        if (reason.category() == RiskReasonCategory.PROBABILITY) {
            return switch (reason.code()) {
                case BASELINE_PROBABILITY -> localized(
                        locale,
                        "Probability set to 1",
                        "Ehtimollik 1 ga o'rnatildi",
                        "Вероятность установлена на 1");
                case RECURRING_DEFECTS, OVERDUE_MAINTENANCE -> localized(
                        locale,
                        "Probability increased by %d".formatted(reason.scoreImpact()),
                        "Ehtimollik %d ga oshdi".formatted(reason.scoreImpact()),
                        "Вероятность увеличена на %d".formatted(reason.scoreImpact()));
                case LOW_MTBF_HIGH, LOW_MTBF_MEDIUM -> localized(
                        locale,
                        "Probability floor is %d".formatted(reason.targetScore()),
                        "Ehtimollik kamida %d".formatted(reason.targetScore()),
                        "Минимальная вероятность %d".formatted(reason.targetScore()));
                default -> localized(
                        locale,
                        "Probability set to %d".formatted(reason.targetScore()),
                        "Ehtimollik %d ga o'rnatildi".formatted(reason.targetScore()),
                        "Вероятность установлена на %d".formatted(reason.targetScore()));
            };
        }
        return localized(
                locale,
                "Consequence increased by %d".formatted(reason.scoreImpact()),
                "Oqibat %d ga oshdi".formatted(reason.scoreImpact()),
                "Последствие увеличено на %d".formatted(reason.scoreImpact()));
    }

    private String reasonText(String locale, EquipmentRiskScoringReason reason) {
        String value = valueText(reason.value());
        String durationValue = durationValueText(locale, reason.value());
        return switch (reason.code()) {
            case OPEN_DEFECTS_CRITICAL -> localized(
                    locale,
                    "%s open defects were observed, so probability is very high.".formatted(value),
                    "%s ta ochiq nuqson aniqlandi, shuning uchun ehtimollik juda yuqori.".formatted(value),
                    "Обнаружено %s открытых дефектов, поэтому вероятность очень высокая.".formatted(value));
            case OPEN_DEFECTS_HIGH -> localized(
                    locale,
                    "%s open defects were observed, so probability is high.".formatted(value),
                    "%s ta ochiq nuqson aniqlandi, shuning uchun ehtimollik yuqori.".formatted(value),
                    "Обнаружено %s открытых дефекта, поэтому вероятность высокая.".formatted(value));
            case OPEN_DEFECTS_MEDIUM -> localized(
                    locale,
                    "%s open defects were observed, so probability is elevated.".formatted(value),
                    "%s ta ochiq nuqson aniqlandi, shuning uchun ehtimollik oshgan.".formatted(value),
                    "Обнаружено %s открытых дефекта, поэтому вероятность повышена.".formatted(value));
            case LOW_MTBF_HIGH -> localized(
                    locale,
                    "MTBF is %s, below the 2000 hour threshold.".formatted(durationValue),
                    "MTBF %s, bu 2000 soat chegarasidan past.".formatted(durationValue),
                    "MTBF составляет %s, что ниже порога 2000 ч.".formatted(durationValue));
            case LOW_MTBF_MEDIUM -> localized(
                    locale,
                    "MTBF is %s, below the 4000 hour threshold.".formatted(durationValue),
                    "MTBF %s, bu 4000 soat chegarasidan past.".formatted(durationValue),
                    "MTBF составляет %s, что ниже порога 4000 ч.".formatted(durationValue));
            case RECURRING_DEFECTS -> localized(
                    locale,
                    "%s recurring defects show a repeated issue pattern.".formatted(value),
                    "%s ta takroriy nuqson bir xil muammo qaytalanayotganini ko'rsatadi.".formatted(value),
                    "%s повторяющихся дефектов показывают повторяющуюся проблему.".formatted(value));
            case OVERDUE_MAINTENANCE -> localized(
                    locale,
                    "%s overdue maintenance tasks increase failure probability.".formatted(value),
                    "%s ta kechikkan texnik xizmat nosozlik ehtimolligini oshiradi.".formatted(value),
                    "%s просроченных задач обслуживания повышают вероятность отказа.".formatted(value));
            case BASELINE_PROBABILITY -> localized(
                    locale,
                    "no active defect or reliability signal was found, so baseline probability is used.",
                    "faol nuqson yoki ishonchlilik signali topilmadi, shuning uchun bazaviy ehtimollik ishlatiladi.",
                    "активные дефекты или сигналы надежности не найдены, поэтому используется базовая вероятность.");
            case CRITICAL_EQUIPMENT -> localized(
                    locale,
                    "the equipment is business-critical.",
                    "uskuna biznes uchun kritik darajada muhim.",
                    "оборудование является критически важным для бизнеса.");
            case HIGH_CRITICALITY -> localized(
                    locale,
                    "the equipment has high criticality, increasing consequence.",
                    "uskuna yuqori kritik darajaga ega, bu oqibatni oshiradi.",
                    "оборудование имеет высокую критичность, что повышает последствие.");
            case MEDIUM_CRITICALITY -> localized(
                    locale,
                    "the equipment has medium criticality.",
                    "uskuna o'rta kritik darajaga ega.",
                    "оборудование имеет среднюю критичность.");
            case LOW_CRITICALITY -> localized(
                    locale,
                    "the equipment has low criticality.",
                    "uskuna past kritik darajaga ega.",
                    "оборудование имеет низкую критичность.");
            case RECENT_DOWNTIME -> localized(
                    locale,
                    "recent downtime of %s increases consequence.".formatted(durationValue),
                    "so'nggi %s to'xtash oqibatni oshiradi.".formatted(durationValue),
                    "недавний простой %s повышает последствие.".formatted(durationValue));
            case HIGH_MTTR -> localized(
                    locale,
                    "MTTR is %s, so recovery is considered difficult.".formatted(durationValue),
                    "MTTR %s, shuning uchun tiklash murakkab deb baholanadi.".formatted(durationValue),
                    "MTTR составляет %s, поэтому восстановление считается сложным.".formatted(durationValue));
            case OPEN_HIGH_REPAIR_REQUEST -> localized(
                    locale,
                    "%s open high-priority repair requests increase consequence.".formatted(value),
                    "%s ta ochiq yuqori ustuvor ta'mir so'rovi oqibatni oshiradi.".formatted(value),
                    "%s открытых срочных заявок на ремонт повышают последствие.".formatted(value));
            case EQUIPMENT_UNAVAILABLE -> localized(
                    locale,
                    "equipment status is %s, so it is already unavailable or degraded.".formatted(value),
                    "uskuna holati %s, shuning uchun u allaqachon mavjud emas yoki cheklangan.".formatted(value),
                    "статус оборудования %s, поэтому оно уже недоступно или работает с ограничениями.".formatted(value));
            case INSPECTION_OR_CALIBRATION_ISSUE -> localized(
                    locale,
                    "inspection or calibration attention is required.",
                    "tekshiruv yoki kalibrlash e'tibor talab qiladi.",
                    "требуется внимание к осмотру или калибровке.");
        };
    }

    private String localized(String locale, String en, String uz, String ru) {
        return switch (locale) {
            case "uz" -> uz;
            case "ru" -> ru;
            default -> en;
        };
    }

    private AvailabilityText availabilityText(String locale) {
        return switch (locale) {
            case "uz" -> new AvailabilityText(
                    "(kuzatilgan vaqt - to'xtash vaqti) / kuzatilgan vaqt × 100",
                    "Kuzatilgan vaqt",
                    "To'xtash vaqti",
                    "Ishlagan vaqt",
                    "Mavjudlik",
                    "soat",
                    (availabilityPct, observedHours, downtimeHours) ->
                            "Mavjudlik %s%%, chunki kuzatilgan vaqt %s va to'xtash vaqti %s."
                                    .formatted(
                                            number(availabilityPct),
                                            durationHours(locale, observedHours),
                                            durationHours(locale, downtimeHours)));
            case "ru" -> new AvailabilityText(
                    "(наблюдаемое время - простой) / наблюдаемое время × 100",
                    "Наблюдаемое время",
                    "Простой",
                    "Рабочее время",
                    "Доступность",
                    "ч",
                    (availabilityPct, observedHours, downtimeHours) ->
                            "Доступность %s%%, потому что наблюдаемое время %s, простой %s."
                                    .formatted(
                                            number(availabilityPct),
                                            durationHours(locale, observedHours),
                                            durationHours(locale, downtimeHours)));
            default -> new AvailabilityText(
                    "(observed time - downtime) / observed time × 100",
                    "Observed time",
                    "Downtime",
                    "Operating time",
                    "Availability",
                    "hours",
                    (availabilityPct, observedHours, downtimeHours) ->
                            "Availability is %s%% because observed time was %s and downtime was %s."
                                    .formatted(
                                            number(availabilityPct),
                                            durationHours(locale, observedHours),
                                            durationHours(locale, downtimeHours)));
        };
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private String valueText(Object value) {
        if (value instanceof Number number) {
            return number(number.doubleValue());
        }
        return String.valueOf(value);
    }

    private String durationValueText(String locale, Object value) {
        if (value instanceof Number number) {
            return durationHours(locale, number.doubleValue());
        }
        return String.valueOf(value);
    }

    public String formatDurationHours(String lang, Double value) {
        if (value == null) {
            return null;
        }
        return durationHours(normalizeLocale(lang), value);
    }

    public String formatDurationMinutes(String lang, long value) {
        return durationHours(normalizeLocale(lang), value / 60.0);
    }

    private String durationHours(String locale, double value) {
        long totalMinutes = Math.max(0, Math.round(value * 60.0));
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;
        StringBuilder result = new StringBuilder();

        if (hours > 0) {
            result.append(durationPart(hours, hourUnit(locale, hours)));
        }
        if (minutes > 0 || result.isEmpty()) {
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(durationPart(minutes, minuteUnit(locale, minutes)));
        }

        return result.toString();
    }

    private String durationPart(long value, String unit) {
        return value + " " + unit;
    }

    private String hourUnit(String locale, long value) {
        return switch (locale) {
            case "uz" -> "soat";
            case "ru" -> "ч";
            default -> value == 1 ? "hour" : "hours";
        };
    }

    private String minuteUnit(String locale, long value) {
        return switch (locale) {
            case "uz" -> "daq";
            case "ru" -> "мин";
            default -> value == 1 ? "minute" : "minutes";
        };
    }

    private static String number(double value) {
        double rounded = Math.round(value * 100.0) / 100.0;
        if (Math.abs(rounded - Math.rint(rounded)) < 0.0000001) {
            return String.valueOf((long) Math.rint(rounded));
        }
        return String.format(Locale.US, "%.2f", rounded).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private record RcmText(String formula,
                           String consequenceTotal,
                           String probability,
                           String finalRisk,
                           RcmSummary summaryFormatter) {
        String summary(int riskScore, String primaryReason) {
            return summaryFormatter.summary(riskScore, primaryReason);
        }
    }

    private record AvailabilityText(String formula,
                                    String observedTime,
                                    String downtime,
                                    String operatingTime,
                                    String availability,
                                    String hoursUnit,
                                    AvailabilitySummary summaryFormatter) {
        String summary(double availabilityPct, double observedHours, double downtimeHours) {
            return summaryFormatter.summary(availabilityPct, observedHours, downtimeHours);
        }
    }

    @FunctionalInterface
    private interface RcmSummary {
        String summary(int riskScore, String primaryReason);
    }

    @FunctionalInterface
    private interface AvailabilitySummary {
        String summary(double availabilityPct, double observedHours, double downtimeHours);
    }
}
