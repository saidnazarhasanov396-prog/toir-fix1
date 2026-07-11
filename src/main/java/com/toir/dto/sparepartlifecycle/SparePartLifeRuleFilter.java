package com.toir.dto.sparepartlifecycle;

import com.toir.enums.sparepartlifecycle.SparePartLifeRuleScope;
import java.time.Instant;
import java.util.UUID;

/**
 * Spare-part life rule ro'yxatini ma'lumotlar bazasi darajasida filtrlash uchun
 * parametrlar. {@code effectiveAt} yarim-ochiq interval semantikasi bilan
 * qo'llaniladi: effectiveFrom &lt;= at &lt; effectiveTo.
 */
public record SparePartLifeRuleFilter(
        UUID sparePartId,
        UUID equipmentId,
        UUID equipmentNodeId,
        SparePartLifeRuleScope scopeType,
        Boolean active,
        Instant effectiveAt
) {
}
