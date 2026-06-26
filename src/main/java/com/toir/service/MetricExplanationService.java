package com.toir.service;

import com.toir.dto.analytics.MetricExplanationDto;
import com.toir.dto.analytics.MetricExplanationStepDto;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
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

    public MetricExplanationDto rcmRisk(String lang,
                                        int safetyImpact,
                                        int productionImpact,
                                        int ecologicalImpact,
                                        int energyImpact,
                                        int consequence,
                                        int probability,
                                        int riskScore,
                                        long openDefects,
                                        double mtbfHours,
                                        RcmProbabilityBasis probabilityBasis) {
        String locale = normalizeLocale(lang);
        RcmText text = rcmText(locale);
        RcmProbabilityBasis resolvedProbabilityBasis = probabilityBasis == null
                ? RcmProbabilityBasis.BASELINE
                : probabilityBasis;
        List<MetricExplanationStepDto> steps = new ArrayList<>();
        steps.add(new MetricExplanationStepDto(text.safetyImpact(), safetyImpact));
        steps.add(new MetricExplanationStepDto(text.productionImpact(), productionImpact));
        steps.add(new MetricExplanationStepDto(text.ecologicalImpact(), ecologicalImpact));
        steps.add(new MetricExplanationStepDto(text.energyImpact(), energyImpact));
        steps.add(new MetricExplanationStepDto(text.consequenceTotal(), consequence));
        steps.add(new MetricExplanationStepDto(text.openDefects(), openDefects));
        if (resolvedProbabilityBasis == RcmProbabilityBasis.MTBF) {
            steps.add(new MetricExplanationStepDto(text.mtbf(), round2(mtbfHours), text.hoursUnit()));
        }
        steps.add(new MetricExplanationStepDto(text.probability(resolvedProbabilityBasis), probability));
        steps.add(new MetricExplanationStepDto(text.finalRisk(), riskScore, "/100"));
        return new MetricExplanationDto(
                locale,
                text.formula(),
                text.summary(riskScore, consequence, probability),
                steps
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
                        new MetricExplanationStepDto(text.observedTime(), round2(observedHours), text.hoursUnit()),
                        new MetricExplanationStepDto(text.downtime(), round2(downtimeHours), text.hoursUnit()),
                        new MetricExplanationStepDto(text.operatingTime(), round2(operatingHours), text.hoursUnit()),
                        new MetricExplanationStepDto(text.availability(), round2(availabilityPct), "%")
                )
        );
    }

    private RcmText rcmText(String locale) {
        return switch (locale) {
            case "uz" -> new RcmText(
                    "min(100, (xavfsizlik + ishlab chiqarish + ekologiya + energiya) × ehtimollik)",
                    "Xavfsizlik ta'siri",
                    "Ishlab chiqarish ta'siri",
                    "Ekologik ta'sir",
                    "Energiya ta'siri",
                    "Oqibat jami",
                    "Ochiq nuqsonlar",
                    "Ehtimollik (ochiq nuqsonlar bo'yicha)",
                    "Ehtimollik (MTBF bo'yicha)",
                    "Ehtimollik (bazaviy)",
                    "MTBF",
                    "Yakuniy xavf",
                    "soat",
                    (riskScore, consequence, probability) ->
                            "Xavf %d/100, chunki oqibat %d va ehtimollik %d."
                                    .formatted(riskScore, consequence, probability));
            case "ru" -> new RcmText(
                    "min(100, (безопасность + производство + экология + энергия) × вероятность)",
                    "Влияние на безопасность",
                    "Влияние на производство",
                    "Экологическое влияние",
                    "Влияние на энергопотребление",
                    "Итого последствие",
                    "Открытые дефекты",
                    "Вероятность (по открытым дефектам)",
                    "Вероятность (по MTBF)",
                    "Вероятность (базовая)",
                    "MTBF",
                    "Итоговый риск",
                    "часы",
                    (riskScore, consequence, probability) ->
                            "Риск %d/100, потому что последствие равно %d, а вероятность %d."
                                    .formatted(riskScore, consequence, probability));
            default -> new RcmText(
                    "min(100, (safety + production + ecological + energy) × probability)",
                    "Safety impact",
                    "Production impact",
                    "Ecological impact",
                    "Energy impact",
                    "Consequence total",
                    "Open defects",
                    "Probability (from open defects)",
                    "Probability (from MTBF)",
                    "Probability (baseline)",
                    "MTBF",
                    "Final risk",
                    "hours",
                    (riskScore, consequence, probability) ->
                            "Risk is %d/100 because consequence is %d and probability is %d."
                                    .formatted(riskScore, consequence, probability));
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
                            "Mavjudlik %s%%, chunki kuzatilgan vaqt %s soat va to'xtash vaqti %s soat."
                                    .formatted(number(availabilityPct), number(observedHours), number(downtimeHours)));
            case "ru" -> new AvailabilityText(
                    "(наблюдаемое время - простой) / наблюдаемое время × 100",
                    "Наблюдаемое время",
                    "Простой",
                    "Рабочее время",
                    "Доступность",
                    "часы",
                    (availabilityPct, observedHours, downtimeHours) ->
                            "Доступность %s%%, потому что наблюдаемое время %s ч, простой %s ч."
                                    .formatted(number(availabilityPct), number(observedHours), number(downtimeHours)));
            default -> new AvailabilityText(
                    "(observed time - downtime) / observed time × 100",
                    "Observed time",
                    "Downtime",
                    "Operating time",
                    "Availability",
                    "hours",
                    (availabilityPct, observedHours, downtimeHours) ->
                            "Availability is %s%% because observed time was %s hours and downtime was %s hours."
                                    .formatted(number(availabilityPct), number(observedHours), number(downtimeHours)));
        };
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static String number(double value) {
        double rounded = Math.round(value * 100.0) / 100.0;
        if (Math.abs(rounded - Math.rint(rounded)) < 0.0000001) {
            return String.valueOf((long) Math.rint(rounded));
        }
        return String.format(Locale.US, "%.2f", rounded).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private record RcmText(String formula,
                           String safetyImpact,
                           String productionImpact,
                           String ecologicalImpact,
                           String energyImpact,
                           String consequenceTotal,
                           String openDefects,
                           String openDefectsProbability,
                           String mtbfProbability,
                           String baselineProbability,
                           String mtbf,
                           String finalRisk,
                           String hoursUnit,
                           RcmSummary summaryFormatter) {
        String summary(int riskScore, int consequence, int probability) {
            return summaryFormatter.summary(riskScore, consequence, probability);
        }

        String probability(RcmProbabilityBasis basis) {
            return switch (basis) {
                case OPEN_DEFECTS -> openDefectsProbability;
                case MTBF -> mtbfProbability;
                case BASELINE -> baselineProbability;
            };
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
        String summary(int riskScore, int consequence, int probability);
    }

    public enum RcmProbabilityBasis {
        OPEN_DEFECTS,
        MTBF,
        BASELINE
    }

    @FunctionalInterface
    private interface AvailabilitySummary {
        String summary(double availabilityPct, double observedHours, double downtimeHours);
    }
}
