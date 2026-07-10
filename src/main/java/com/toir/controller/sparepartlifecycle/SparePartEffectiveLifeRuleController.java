package com.toir.controller.sparepartlifecycle;

import com.toir.dto.sparepartlifecycle.SparePartLifeRuleDto;
import com.toir.repository.sparepartlifecycle.SparePartLifeLimitRepository;
import com.toir.security.RequiresSensitiveAccess;
import com.toir.service.sparepartlifecycle.SparePartLifeRuleResolver;
import com.toir.service.sparepartlifecycle.SparePartSlotNormalizer;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/equipment/{equipmentId}/spare-part-life-rules/effective")
@RequiresSensitiveAccess
@RequiredArgsConstructor
public class SparePartEffectiveLifeRuleController {

    private final SparePartLifeRuleResolver resolver;
    private final SparePartLifeLimitRepository limitRepository;
    private final SparePartSlotNormalizer slotNormalizer;

    @GetMapping
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('SPARE_PART_LIFE_RULE_READ')")
    public ResponseEntity<SparePartLifeRuleDto> get(
            @PathVariable UUID equipmentId,
            @RequestParam UUID sparePartId,
            @RequestParam(required = false) UUID equipmentNodeId,
            @RequestParam(required = false) String slotCode,
            @RequestParam(required = false) Instant at
    ) {
        return resolver.resolve(
                        sparePartId,
                        equipmentId,
                        equipmentNodeId,
                        slotNormalizer.normalizeNullable(slotCode),
                        at == null ? Instant.now() : at)
                .map(rule -> SparePartLifeRuleDto.from(
                        rule,
                        limitRepository.findAllByRuleIdAndIsDeletedFalseOrderBySequenceAsc(rule.getId())))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
