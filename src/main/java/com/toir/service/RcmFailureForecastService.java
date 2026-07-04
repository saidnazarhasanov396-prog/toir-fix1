package com.toir.service;

import com.toir.dto.rcm.RcmFailureForecastDto;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

@Service
public class RcmFailureForecastService {

    public RcmFailureForecastDto forecast(EquipmentRiskEvidence evidence, EquipmentRiskScoringResult result, String lang) {
        String locale = normalizeLocale(lang);
        double mtbf = evidence.mtbfHours();
        Instant lastFailureAt = evidence.lastFailureAt();

        if (mtbf > 0 && lastFailureAt != null) {
            Instant expectedAt = lastFailureAt.plus(Duration.ofMinutes(Math.round(mtbf * 60)));
            double remainingHours = Duration.between(Instant.now(), expectedAt).toMinutes() / 60.0;
            String status = statusForRemainingHours(remainingHours);
            return new RcmFailureForecastDto(
                    status,
                    labelFor(status, locale),
                    expectedAt,
                    round2(remainingHours),
                    round2(mtbf),
                    lastFailureAt,
                    basisFor("MTBF_LAST_FAILURE", locale),
                    "HIGH"
            );
        }

        if (mtbf > 0) {
            return new RcmFailureForecastDto(
                    "MTBF_ONLY_NO_DATE",
                    labelFor("MTBF_ONLY_NO_DATE", locale),
                    null,
                    null,
                    round2(mtbf),
                    null,
                    basisFor("MTBF_ONLY", locale),
                    "MEDIUM"
            );
        }

        if (result.probabilityScore() >= 4 && evidence.openDefects() > 0) {
            return new RcmFailureForecastDto(
                    "DUE_NOW_FROM_OPEN_DEFECTS",
                    labelFor("DUE_NOW_FROM_OPEN_DEFECTS", locale),
                    null,
                    null,
                    null,
                    null,
                    basisFor("OPEN_DEFECTS", locale),
                    evidence.openDefects() >= 5 ? "MEDIUM" : "LOW"
            );
        }

        return new RcmFailureForecastDto(
                "INSUFFICIENT_DATA",
                labelFor("INSUFFICIENT_DATA", locale),
                null,
                null,
                null,
                null,
                basisFor("INSUFFICIENT_DATA", locale),
                "LOW"
        );
    }

    private String statusForRemainingHours(double remainingHours) {
        if (remainingHours < 0) {
            return "OVERDUE_BY_MTBF";
        }
        if (remainingHours <= 24 * 7) {
            return "WITHIN_7_DAYS";
        }
        if (remainingHours <= 24 * 30) {
            return "WITHIN_30_DAYS";
        }
        return "LATER";
    }

    private String labelFor(String status, String locale) {
        return switch (status) {
            case "DUE_NOW_FROM_OPEN_DEFECTS" -> localized(locale,
                    "Timing not calculated",
                    "Muddat hisoblanmadi",
                    "Срок не рассчитан");
            case "OVERDUE_BY_MTBF" -> localized(locale,
                    "Average interval has passed",
                    "O'rtacha interval o'tib ketgan",
                    "Средний интервал уже пройден");
            case "WITHIN_7_DAYS" -> localized(locale,
                    "Within 7 days",
                    "7 kun ichida",
                    "В ближайшие 7 дней");
            case "WITHIN_30_DAYS" -> localized(locale,
                    "Within 30 days",
                    "30 kun ichida",
                    "В ближайшие 30 дней");
            case "LATER" -> localized(locale,
                    "Later than 30 days",
                    "30 kundan keyin",
                    "Позже 30 дней");
            case "MTBF_ONLY_NO_DATE" -> localized(locale,
                    "MTBF exists, no date",
                    "MTBF bor, sana yo'q",
                    "Есть MTBF, нет даты");
            default -> localized(locale,
                    "Not enough data",
                    "Ma'lumot yetarli emas",
                    "Недостаточно данных");
        };
    }

    private String basisFor(String basis, String locale) {
        return switch (basis) {
            case "MTBF_LAST_FAILURE" -> localized(locale,
                    "MTBF + latest unplanned/emergency downtime",
                    "MTBF + so'nggi rejadan tashqari/avariya to'xtashi",
                    "MTBF + последняя внеплановая/аварийная остановка");
            case "MTBF_ONLY" -> localized(locale,
                    "MTBF is available, but no last failure event was found",
                    "MTBF bor, lekin so'nggi nosozlik hodisasi topilmadi",
                    "MTBF есть, но последняя дата отказа не найдена");
            case "OPEN_DEFECTS" -> localized(locale,
                    "Active open defects require review now",
                    "Faol ochiq nuqsonlar hozir ko'rib chiqishni talab qiladi",
                    "Активные открытые дефекты требуют разбора сейчас");
            default -> localized(locale,
                    "No MTBF or failure history for timing calculation",
                    "Muddatni hisoblash uchun MTBF yoki nosozlik tarixi yo'q",
                    "Нет MTBF или истории отказов для расчета срока");
        };
    }

    private String normalizeLocale(String lang) {
        if (lang == null || lang.isBlank()) {
            return "en";
        }
        String normalized = lang.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("ru")) {
            return "ru";
        }
        if (normalized.startsWith("uz")) {
            return "uz";
        }
        return "en";
    }

    private String localized(String locale, String en, String uz, String ru) {
        return switch (locale) {
            case "ru" -> ru;
            case "uz" -> uz;
            default -> en;
        };
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
