package com.toir.service.sparepartlifecycle;

import com.toir.dto.sparepartlifecycle.SparePartLifeLimitRequest;
import com.toir.dto.sparepartlifecycle.SparePartLifeRuleRequest;
import com.toir.enums.sparepartlifecycle.SparePartLifeCombinationMode;
import com.toir.enums.sparepartlifecycle.SparePartLifeLimitKind;
import com.toir.exception.RestException;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class SparePartLifeRuleValidator {

    public void validate(SparePartLifeRuleRequest request) {
        if (request == null || request.sparePartId() == null || request.combinationMode() == null
                || request.dueAction() == null) {
            throw RestException.badRequest("RULE_INVALID: part, combination mode and due action are required");
        }
        if (request.effectiveFrom() != null && request.effectiveTo() != null
                && !request.effectiveTo().isAfter(request.effectiveFrom())) {
            throw RestException.badRequest("RULE_EFFECTIVE_RANGE_INVALID: effectiveTo must be after effectiveFrom");
        }
        List<SparePartLifeLimitRequest> limits = request.limits() == null ? List.of() : request.limits();
        if (request.combinationMode() != SparePartLifeCombinationMode.MANUAL && limits.isEmpty()) {
            throw RestException.badRequest("RULE_LIMIT_REQUIRED: non-manual rules require at least one limit");
        }
        Set<Integer> sequences = new HashSet<>();
        for (SparePartLifeLimitRequest limit : limits) {
            validateLimit(limit);
            if (!sequences.add(limit.sequence())) {
                throw RestException.badRequest("RULE_LIMIT_SEQUENCE_DUPLICATE: limit sequence must be unique");
            }
        }
    }

    private void validateLimit(SparePartLifeLimitRequest limit) {
        if (limit == null || limit.limitKind() == null || limit.limitValue() == null
                || limit.limitValue().compareTo(BigDecimal.ZERO) <= 0 || limit.sequence() < 0) {
            throw RestException.badRequest("RULE_LIMIT_INVALID: kind, positive value and non-negative sequence are required");
        }
        boolean validShape = switch (limit.limitKind()) {
            case CALENDAR -> limit.calendarUnit() != null
                    && limit.meterType() == null
                    && limit.explicitEquipmentMeterId() == null;
            case METER -> limit.calendarUnit() == null && limit.meterType() != null;
        };
        if (!validShape) {
            throw RestException.badRequest("RULE_LIMIT_INVALID: calendar and meter fields do not match the limit kind");
        }
        if (limit.warningBeforeValue() != null
                && (limit.warningBeforeValue().compareTo(BigDecimal.ZERO) < 0
                || limit.warningBeforeValue().compareTo(limit.limitValue()) > 0)) {
            throw RestException.badRequest("RULE_WARNING_INVALID: warning value must be between zero and the limit");
        }
    }
}
