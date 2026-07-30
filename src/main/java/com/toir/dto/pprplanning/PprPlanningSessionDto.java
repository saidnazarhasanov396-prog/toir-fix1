package com.toir.dto.pprplanning;

import com.toir.entity.planning.PprPlanningSession;
import com.toir.entity.planning.PprPlanningVariant;
import com.toir.enums.planning.PprPlanningSessionStatus;
import com.toir.enums.planning.PprPlanningVariantStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record PprPlanningSessionDto(
        UUID id,
        String name,
        int year,
        UUID departmentId,
        LocalDate startDate,
        LocalDate endDate,
        String notes,
        PprPlanningSessionStatus status,
        UUID selectedVariantId,
        Long selectedVariantRevision,
        String selectedVariantHash,
        Integer selectedVariantHashVersion,
        UUID approvalRequestId,
        UUID approvedPlanId,
        long version,
        List<Variant> variants,
        Set<String> availableActions
) {
    public static PprPlanningSessionDto from(
            PprPlanningSession session,
            List<PprPlanningVariant> variants) {
        return new PprPlanningSessionDto(
                session.getId(),
                session.getName(),
                session.getYear(),
                session.getDepartmentId(),
                session.getStartDate(),
                session.getEndDate(),
                session.getNotes(),
                session.getStatus(),
                session.getSelectedVariantId(),
                session.getSelectedVariantRevision(),
                session.getSelectedVariantHash(),
                session.getSelectedVariantHashVersion(),
                session.getApprovalRequestId(),
                session.getApprovedPlanId(),
                session.getVersion(),
                variants == null ? List.of() : variants.stream().map(Variant::from).toList(),
                actions(session));
    }

    private static Set<String> actions(PprPlanningSession session) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (session.getStatus() == PprPlanningSessionStatus.DRAFT
                || session.getStatus() == PprPlanningSessionStatus.READY_FOR_SELECTION
                || session.getStatus() == PprPlanningSessionStatus.SELECTED) {
            result.add("CREATE_VARIANT");
            result.add("CALCULATE_VARIANT");
            result.add("SELECT_VARIANT");
        }
        if (session.getStatus() == PprPlanningSessionStatus.SELECTED) {
            result.add("SUBMIT_FOR_APPROVAL");
        }
        return Set.copyOf(result);
    }

    public record Variant(
            UUID id,
            String name,
            long revision,
            String contentHash,
            int hashVersion,
            PprPlanningVariantStatus status,
            Integer taskCount,
            BigDecimal totalLaborHours,
            Long totalDowntimeMinutes,
            LocalDate firstPlannedDate,
            LocalDate lastPlannedDate,
            long version
    ) {
        public static Variant from(PprPlanningVariant variant) {
            return new Variant(
                    variant.getId(),
                    variant.getName(),
                    variant.getRevision(),
                    variant.getContentHash(),
                    variant.getHashVersion(),
                    variant.getStatus(),
                    variant.getTaskCount(),
                    variant.getTotalLaborHours(),
                    variant.getTotalDowntimeMinutes(),
                    variant.getFirstPlannedDate(),
                    variant.getLastPlannedDate(),
                    variant.getVersion());
        }
    }
}
