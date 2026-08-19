package com.toir.ai.repair;

import com.fasterxml.jackson.databind.JsonNode;
import com.toir.enums.CriticalityLevel;
import com.toir.enums.PriorityLevel;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class AiRepairConclusionParser {

    public AiRepairConclusion parse(AiRepairKind kind, JsonNode payload) {
        JsonNode root = payload;
        JsonNode unwrapped = AiJson.unwrap(root);
        JsonNode report = AiJson.firstObject(root, "inspection_report", "inspectionReport");
        if (report == null) {
            report = AiJson.findDeepObject(root, "inspection_report", "inspectionReport");
        }
        JsonNode source = report != null ? report : unwrapped;

        boolean insufficient = Boolean.FALSE.equals(AiJson.bool(
                source,
                "sufficient_for_inspection",
                "sufficientForInspection",
                "sufficient"
        ));

        List<AiRepairDefect> defects = extractDefects(root, source);
        List<String> recommendations = extractGuidance(source, root);
        String cause = firstText(source, root,
                "cause", "root_cause", "rootCause", "failure_cause", "failureCause", "probable_cause");
        String repair = firstText(source, root,
                "repair_action", "repairAction", "recommended_repair", "recommendedRepair",
                "corrective_action", "correctiveAction");
        String summary = firstText(source, root,
                "summary_uz", "summary", "description_uz", "description", "analysis_summary", "message");
        String title = firstText(source, root,
                "title_uz", "title", "asset_type_uz", "asset_type", "component_uz", "component", "failure_type",
                "failureType", "category_name", "category");
        String highestSeverity = firstText(source, root,
                "highest_severity", "highestSeverity", "severity", "priority", "criticality");
        Integer totalDefects = AiJson.integer(source, "total_defects", "totalDefects");
        String status = firstText(source, root, "inspection_status", "inspectionStatus", "status", "result_status");

        boolean hasRepairGuidance = !recommendations.isEmpty()
                || StringUtils.hasText(cause)
                || StringUtils.hasText(repair);
        boolean hasDefectSignal = !defects.isEmpty()
                || (totalDefects != null && totalDefects > 0)
                || isBrokenStatus(status)
                || isBrokenSeverity(highestSeverity);

        boolean broken = switch (kind) {
            case VISUAL_INSPECTION -> hasDefectSignal;
            case WORK_ORDER_DRAFT -> hasDefectSignal || (hasRepairGuidance && !isMatchOnly(source, root));
            case CAUSE_REPAIR, FAILURE_EVIDENCE -> hasDefectSignal || hasRepairGuidance;
        };

        if (insufficient && kind == AiRepairKind.VISUAL_INSPECTION && !hasDefectSignal) {
            return new AiRepairConclusion(false, true, null, null, null, null, null, List.of());
        }

        String problemKey = buildProblemKey(defects, title, cause, summary);
        String resolvedTitle = resolveTitle(title, summary, defects, kind);
        String description = buildDescription(summary, cause, repair, recommendations, defects);
        PriorityLevel priority = mapPriority(highestSeverity, defects);
        CriticalityLevel criticality = mapCriticality(priority);

        return new AiRepairConclusion(
                broken,
                insufficient && kind == AiRepairKind.VISUAL_INSPECTION,
                problemKey,
                resolvedTitle,
                description,
                priority,
                criticality,
                defects
        );
    }

    private static boolean isMatchOnly(JsonNode source, JsonNode root) {
        boolean hasMatches = !AiJson.array(source, "matching_work_orders", "matched_work_orders", "work_orders").isEmpty()
                || AiJson.firstObject(source, "matched_work_order", "matchedWorkOrder") != null
                || !AiJson.array(root, "matching_work_orders", "matched_work_orders").isEmpty();
        boolean hasWork = !AiJson.array(source, "tasks", "operations", "work_steps").isEmpty()
                || !AiJson.stringList(source, "recommendations", "recommendations_uz").isEmpty();
        return hasMatches && !hasWork;
    }

    private static List<AiRepairDefect> extractDefects(JsonNode root, JsonNode source) {
        List<JsonNode> records = new ArrayList<>();
        records.addAll(AiJson.array(source, "defects", "detected_defects", "detectedDefects", "findings", "failures"));
        records.addAll(AiJson.array(root, "defects", "detected_defects", "detectedDefects"));
        JsonNode report = AiJson.findDeepObject(root, "inspection_report", "inspectionReport");
        if (report != null) {
            records.addAll(AiJson.array(report, "defects", "detected_defects", "detectedDefects"));
        }
        List<AiRepairDefect> defects = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (JsonNode record : records) {
            if (!record.isObject()) {
                continue;
            }
            String type = firstText(record, null, "type", "defect_type", "defectType", "category", "name", "name_uz");
            String title = firstText(record, null, "title_uz", "title", "name_uz", "name", "type", "defect_type");
            String description = firstText(record, null,
                    "description_uz", "description", "summary_uz", "summary", "visual_evidence_uz", "visual_evidence",
                    "evidence", "recommendation");
            if (title == null) {
                title = type;
            }
            if (title == null && description == null) {
                continue;
            }
            if (title == null) {
                title = description.length() > 80 ? description.substring(0, 80) : description;
            }
            if (description == null) {
                description = title;
            }
            String key = AiJson.normalizeKey(type != null ? type : title);
            if (key != null && !seen.add(key)) {
                continue;
            }
            defects.add(new AiRepairDefect(
                    type,
                    title,
                    description,
                    firstText(record, null, "category", "defect_type", "defectType"),
                    firstText(record, null, "severity", "highest_severity"),
                    firstText(record, null, "failure_reason", "failureReason", "cause"),
                    firstText(record, null, "root_cause", "rootCause")
            ));
        }
        return defects;
    }

    private static List<String> extractGuidance(JsonNode source, JsonNode root) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        values.addAll(AiJson.stringList(source, "recommendations_uz", "recommendations", "repair_actions",
                "repairActions", "instructions", "tasks", "operations", "work_steps"));
        values.addAll(AiJson.stringList(root, "recommendations_uz", "recommendations", "repair_actions"));
        return List.copyOf(values);
    }

    private static String firstText(JsonNode primary, JsonNode secondary, String... keys) {
        String value = AiJson.text(primary, keys);
        if (value != null) {
            return value;
        }
        return secondary == null ? null : AiJson.text(secondary, keys);
    }

    static String buildProblemKey(List<AiRepairDefect> defects, String title, String cause, String summary) {
        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        for (AiRepairDefect defect : defects) {
            String token = AiJson.normalizeKey(defect.type() != null ? defect.type() : defect.title());
            if (token != null) {
                tokens.add(token);
            }
        }
        if (tokens.isEmpty()) {
            String fallback = AiJson.normalizeKey(firstNonBlank(cause, title, summary));
            if (fallback != null) {
                tokens.add(fallback);
            }
        }
        if (tokens.isEmpty()) {
            return "general";
        }
        return String.join("+", tokens);
    }

    private static String resolveTitle(String title, String summary, List<AiRepairDefect> defects, AiRepairKind kind) {
        if (StringUtils.hasText(title)) {
            return truncate(title, 255);
        }
        if (!defects.isEmpty() && StringUtils.hasText(defects.getFirst().title())) {
            return truncate("AI: " + defects.getFirst().title(), 255);
        }
        if (StringUtils.hasText(summary)) {
            return truncate(summary, 255);
        }
        return switch (kind) {
            case VISUAL_INSPECTION -> "AI visual inspection";
            case WORK_ORDER_DRAFT -> "AI repair conclusion";
            case CAUSE_REPAIR -> "AI cause-repair analysis";
            case FAILURE_EVIDENCE -> "AI failure evidence";
        };
    }

    private static String buildDescription(
            String summary,
            String cause,
            String repair,
            List<String> recommendations,
            List<AiRepairDefect> defects
    ) {
        StringBuilder builder = new StringBuilder();
        appendSection(builder, "Xulosa", summary);
        appendSection(builder, "Sabab", cause);
        appendSection(builder, "Tuzatish", repair);
        if (!recommendations.isEmpty()) {
            appendSection(builder, "Yo'l-yo'riq", String.join("\n- ", recommendations));
        }
        if (!defects.isEmpty()) {
            StringBuilder defectText = new StringBuilder();
            for (AiRepairDefect defect : defects) {
                defectText.append("\n- ").append(defect.title());
                if (StringUtils.hasText(defect.description()) && !defect.description().equals(defect.title())) {
                    defectText.append(": ").append(defect.description());
                }
            }
            appendSection(builder, "Nuqsonlar", defectText.toString().trim());
        }
        String text = builder.toString().trim();
        return text.isEmpty() ? "AI tizimi ta'mirlash xulosasi." : text;
    }

    private static void appendSection(StringBuilder builder, String heading, String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        if (!builder.isEmpty()) {
            builder.append("\n\n");
        }
        builder.append(heading).append(":\n").append(value.trim());
    }

    private static PriorityLevel mapPriority(String severity, List<AiRepairDefect> defects) {
        PriorityLevel fromHeader = fromSeverity(severity);
        PriorityLevel highest = fromHeader != null ? fromHeader : PriorityLevel.MEDIUM;
        for (AiRepairDefect defect : defects) {
            PriorityLevel mapped = fromSeverity(defect.severity());
            if (mapped != null && mapped.ordinal() > highest.ordinal()) {
                highest = mapped;
            }
        }
        return highest;
    }

    private static PriorityLevel fromSeverity(String severity) {
        String key = AiJson.normalizeKey(severity);
        if (key == null) {
            return null;
        }
        return switch (key) {
            case "emergency", "urgent" -> PriorityLevel.EMERGENCY;
            case "critical", "alarm", "failed", "danger" -> PriorityLevel.CRITICAL;
            case "high", "major" -> PriorityLevel.HIGH;
            case "medium", "moderate", "warning", "attention" -> PriorityLevel.MEDIUM;
            case "low", "minor", "normal", "none", "ok", "info" -> PriorityLevel.LOW;
            default -> null;
        };
    }

    private static CriticalityLevel mapCriticality(PriorityLevel priority) {
        if (priority == null) {
            return CriticalityLevel.MEDIUM;
        }
        return switch (priority) {
            case EMERGENCY, CRITICAL -> CriticalityLevel.CRITICAL;
            case HIGH -> CriticalityLevel.HIGH;
            case LOW -> CriticalityLevel.LOW;
            case MEDIUM -> CriticalityLevel.MEDIUM;
        };
    }

    private static boolean isBrokenStatus(String status) {
        String key = AiJson.normalizeKey(status);
        if (key == null) {
            return false;
        }
        return key.contains("fail") || key.contains("defect") || key.contains("critical")
                || key.contains("alarm") || "broken".equals(key) || "damaged".equals(key);
    }

    private static boolean isBrokenSeverity(String severity) {
        PriorityLevel mapped = fromSeverity(severity);
        return mapped != null && mapped.ordinal() >= PriorityLevel.LOW.ordinal()
                && !"none".equals(AiJson.normalizeKey(severity))
                && !"ok".equals(AiJson.normalizeKey(severity))
                && !"normal".equals(AiJson.normalizeKey(severity));
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() <= max) {
            return trimmed;
        }
        return trimmed.substring(0, max);
    }
}
