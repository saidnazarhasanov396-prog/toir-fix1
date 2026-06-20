package com.toir.service;

import com.toir.dto.operationalissue.OperationalIssueTextI18n;
import com.toir.entity.OperationalIssue;
import com.toir.enums.OperationalIssueType;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class OperationalIssueI18nService {

    private static final String TITLE_PREFIX = "operationalIssues.titles.";
    private static final String MESSAGE_PREFIX = "operationalIssues.messages.";

    public OperationalIssueTextI18n build(OperationalIssue issue) {
        if (issue == null || issue.getType() == null) {
            return OperationalIssueTextI18n.empty();
        }
        Map<String, Object> params = new LinkedHashMap<>();
        copyMetadata(params, issue.getMetadata());
        enrichFromLegacyText(params, issue);
        putIfPresent(params, "sourceType", issue.getSourceType());
        putIfPresent(params, "sourceId", issue.getSourceId() == null ? null : issue.getSourceId().toString());

        Map<String, Object> safeParams = Collections.unmodifiableMap(new LinkedHashMap<>(params));
        String typeName = issue.getType().name();
        return new OperationalIssueTextI18n(
                TITLE_PREFIX + typeName,
                safeParams,
                MESSAGE_PREFIX + typeName,
                safeParams
        );
    }

    private void copyMetadata(Map<String, Object> params, Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return;
        }
        metadata.forEach((key, value) -> {
            if (key != null && value != null) {
                params.put(key, value);
            }
        });
    }

    private void enrichFromLegacyText(Map<String, Object> params, OperationalIssue issue) {
        OperationalIssueType type = issue.getType();
        String title = issue.getTitle();
        switch (type) {
            case OVERDUE_WORK_ORDER -> putIfMissing(params, "workOrderNumber", afterPrefix(title, "Overdue work order "));
            case OVERDUE_PPR_TASK -> putIfMissing(params, "pprTaskCode", afterPrefix(title, "Overdue PPR task "));
            case OVERDUE_REPAIR_REQUEST -> putIfMissing(params, "requestNumber", afterPrefix(title, "Overdue repair request "));
            case OVERDUE_CALIBRATION -> putIfMissing(params, "certificateNumber", afterPrefix(title, "Overdue calibration "));
            case EQUIPMENT_LIFETIME_WARNING -> putIfMissing(params, "equipmentCode", afterPrefix(title, "Equipment lifetime expiring soon: "));
            case EQUIPMENT_LIFETIME_EXPIRED -> putIfMissing(params, "equipmentCode", afterPrefix(title, "Equipment lifetime expired: "));
            case EQUIPMENT_LIFECYCLE -> putIfMissing(params, "equipmentCode", afterPrefix(title, "Equipment lifecycle risk: "));
            case MAINTENANCE_DUE, MAINTENANCE_OVERDUE, MISSING_METERS ->
                    putIfMissing(params, "cycleKey", afterPrefix(title, "Maintenance due: "));
            case LOW_STOCK -> putIfMissing(params, "sparePartName", afterPrefix(title, "Low stock: "));
            case SPARE_PART_SHORTAGE_FORECAST ->
                    putIfMissing(params, "sparePartName", afterPrefix(title, "Spare part shortage forecast: "));
            case BUDGET_REVIEW_ISSUE -> putIfMissing(params, "year", afterPrefix(title, "Budget review issue "));
            case APPROVAL_ESCALATION -> {
                String approvalTitle = afterPrefix(title, "Approval escalation: ");
                if (approvalTitle == null) {
                    approvalTitle = afterPrefix(title, "Approval SLA exceeded: ");
                }
                putIfMissing(params, "approvalTitle", approvalTitle);
            }
            case INSPECTION_DEFECT -> putIfMissing(params, "defectCode", afterPrefix(title, "Inspection defect "));
            case CONTRACTOR_WORK_DELAY, AUTOMATION_FAILURE, BLOCKED_PPR_GENERATION -> {
                // These types either use generic titles or already provide useful metadata.
            }
        }
    }

    private String afterPrefix(String value, String prefix) {
        if (value == null || prefix == null || !value.startsWith(prefix)) {
            return null;
        }
        String result = value.substring(prefix.length()).trim();
        return result.isBlank() ? null : result;
    }

    private void putIfMissing(Map<String, Object> params, String key, Object value) {
        if (!params.containsKey(key)) {
            putIfPresent(params, key, value);
        }
    }

    private void putIfPresent(Map<String, Object> params, String key, Object value) {
        if (key != null && value != null) {
            params.put(key, value);
        }
    }
}
