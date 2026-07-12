package com.toir.service.plannedshutdown;

import com.toir.entity.PlannedShutdown;
import com.toir.entity.plannedshutdown.*;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Component
public class PlannedShutdownApprovalScopeHasher {
    public static final String PRODUCTION_APPROVER_ROLE = "PLANNED_SHUTDOWN_PRODUCTION_APPROVER";
    public static final String HSE_APPROVER_ROLE = "PLANNED_SHUTDOWN_HSE_APPROVER";

    public String hash(PlannedShutdown s, List<PlannedShutdownAsset> assets, List<PlannedShutdownWorkItem> work,
            List<PlannedShutdownReadinessItem> readiness, List<PlannedShutdownIsolationPoint> isolation) {
        List<String> facts = new ArrayList<>();
        facts.add(line("ROOT", s.getCode(), s.getName(), s.getShutdownType(), s.getDepartmentId(),
                s.getResponsibleEmployeeId(), s.getPlannedStartAt(), s.getPlannedEndAt(), s.getReason(),
                s.getObjective(), s.getNotes(), s.getRiskLevel(), s.getRiskScore(), s.getScopeVersion(),
                s.getApprovedStartAt(), s.getApprovedEndAt()));
        assets.stream().map(a -> line("ASSET", a.getEquipmentId(), a.getDisposition(), a.getInclusionReason()))
                .sorted().forEach(facts::add);
        work.stream().map(w -> line("WORK", w.getSourceType(), w.getSourceId(), w.getEquipmentId(), w.getTitle(),
                        w.getPriority(), w.isRequiresShutdown(), w.isRequiresIsolation(),
                        w.getPlannedDurationMinutes(), w.getCriticality()))
                .sorted().forEach(facts::add);
        readiness.stream().map(r -> line("READINESS", r.getReadinessKey(), r.getSourceType(), r.getSourceId(),
                        r.getTitle(), r.getSeverity(), r.getResponsibleEmployeeId(), r.getDueAt()))
                .sorted().forEach(facts::add);
        isolation.stream().map(i -> line("ISOLATION", i.getEquipmentId(), i.getLocationId(),
                        i.getIsolationMethod(), i.getLockTagIdentifier(), i.getResponsibleEmployeeId(), i.getPermitId()))
                .sorted().forEach(facts::add);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(String.join("\n", facts).getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static String line(Object... values) {
        return Arrays.stream(values).map(value -> value == null ? "∅" : escape(canonicalValue(value)))
                .collect(java.util.stream.Collectors.joining("|"));
    }

    private static String canonicalValue(Object value) {
        return value instanceof java.math.BigDecimal decimal
                ? decimal.stripTrailingZeros().toPlainString() : value.toString();
    }

    private static String escape(String value) { return value.replace("\\", "\\\\").replace("|", "\\|").trim(); }
}
