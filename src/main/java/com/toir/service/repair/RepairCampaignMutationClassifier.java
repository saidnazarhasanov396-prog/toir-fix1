package com.toir.service.repair;

import com.toir.entity.repair.RepairCampaign;
import com.toir.enums.RepairCampaignMutationType;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public final class RepairCampaignMutationClassifier {
    private RepairCampaignMutationClassifier() {}

    public static RepairCampaignMutationType classify(
            RepairCampaign current, UUID maintenanceBudgetId, BigDecimal totalBudget, String currencyCode) {
        if (!Objects.equals(current.getCurrencyCode(), currencyCode)) return RepairCampaignMutationType.FX;
        if (!Objects.equals(current.getMaintenanceBudgetId(), maintenanceBudgetId)
                || decimalChanged(current.getTotalBudget(), totalBudget)) return RepairCampaignMutationType.BUDGET;
        return RepairCampaignMutationType.METADATA;
    }

    private static boolean decimalChanged(BigDecimal left, BigDecimal right) {
        return left == null ? right != null : right == null || left.compareTo(right) != 0;
    }
}
