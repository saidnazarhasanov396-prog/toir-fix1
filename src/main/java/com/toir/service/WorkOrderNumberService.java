package com.toir.service;

import com.toir.exception.RestException;
import com.toir.repository.WorkOrderRepository;
import java.time.Year;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WorkOrderNumberService {

    private static final int MAX_GENERATION_ATTEMPTS = 5000;

    private final WorkOrderRepository repository;

    @Transactional(readOnly = true)
    public String nextAutoNumber() {
        return nextNumber("WO-AUTO-");
    }

    @Transactional(readOnly = true)
    public String nextPprNumber() {
        return nextNumber("WO-PPR-");
    }

    @Transactional(readOnly = true)
    public String nextPprNumber(Set<String> reservedNumbers) {
        return nextNumber("WO-PPR-", reservedNumbers == null ? Set.of() : reservedNumbers);
    }

    @Transactional(readOnly = true)
    public String nextManualNumber() {
        return nextNumber("WO-MANUAL-");
    }

    private String nextNumber(String familyPrefix) {
        return nextNumber(familyPrefix, Set.of());
    }

    private String nextNumber(String familyPrefix, Set<String> reservedNumbers) {
        String prefix = familyPrefix + Year.now().getValue() + "-";
        long sequence = repository.maxSequenceByNumberPrefix(prefix) + 1;
        for (int attempt = 0; attempt < MAX_GENERATION_ATTEMPTS; attempt++) {
            String number = prefix + "%04d".formatted(sequence + attempt);
            if (!reservedNumbers.contains(number) && !repository.existsByNumberAndIsDeletedFalse(number)) {
                return number;
            }
        }
        throw RestException.conflict("Could not generate unique work order number for prefix " + prefix);
    }
}
