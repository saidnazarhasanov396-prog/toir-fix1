package com.toir.service;

import com.toir.dto.counteragent.CounteragentDto;
import com.toir.dto.counteragent.CounteragentPerformanceDto;
import com.toir.dto.counteragent.CounteragentRequest;
import com.toir.entity.Counteragent;
import com.toir.entity.PurchaseOrder;
import com.toir.enums.CounteragentStatus;
import com.toir.enums.PurchaseOrderStatus;
import com.toir.exception.RestException;
import com.toir.repository.CounteragentRepository;
import com.toir.repository.PurchaseOrderRepository;
import com.toir.util.CodeGenerationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CounteragentService {

    private final CounteragentRepository counteragentRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;

    @Transactional(readOnly = true)
    public List<CounteragentDto> findAll(String search, CounteragentStatus status) {
        String normalizedSearch = trimToNull(search);
        List<Counteragent> counteragents = normalizedSearch == null
                ? counteragentRepository.findAllFiltered(status)
                : counteragentRepository.search(normalizedSearch, status);
        return counteragents.stream()
                .map(CounteragentDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public CounteragentDto findById(UUID id) {
        return CounteragentDto.from(load(id));
    }

    @Transactional
    public CounteragentDto create(CounteragentRequest request) {
        Counteragent counteragent = new Counteragent();
        counteragent.setCode(resolveCode(request.code()));
        apply(counteragent, request);
        return CounteragentDto.from(counteragentRepository.save(counteragent));
    }

    @Transactional
    public CounteragentDto update(UUID id, CounteragentRequest request) {
        Counteragent counteragent = load(id);
        String requestedCode = trimToNull(request.code());
        if (requestedCode != null && !requestedCode.equals(counteragent.getCode())) {
            if (counteragentRepository.existsByCodeAndIsDeletedFalse(requestedCode)) {
                throw RestException.conflict("Counteragent code already exists: " + requestedCode);
            }
            counteragent.setCode(requestedCode);
        }
        apply(counteragent, request);
        return CounteragentDto.from(counteragentRepository.save(counteragent));
    }

    @Transactional
    public void delete(UUID id) {
        Counteragent counteragent = load(id);
        counteragent.setDeleted(true);
        counteragentRepository.save(counteragent);
    }

    @Transactional(readOnly = true)
    public CounteragentPerformanceDto performance(UUID id) {
        Counteragent counteragent = load(id);
        List<PurchaseOrder> orders = purchaseOrderRepository.findAllByCounteragentIdAndIsDeletedFalse(counteragent.getId());
        List<PurchaseOrder> delivered = orders.stream()
                .filter(po -> po.getStatus() == PurchaseOrderStatus.RECEIVED && po.getReceivedDate() != null)
                .toList();
        long late = delivered.stream()
                .filter(po -> po.getExpectedDeliveryDate() != null && po.getReceivedDate().isAfter(po.getExpectedDeliveryDate()))
                .count();
        long onTime = delivered.stream()
                .filter(po -> po.getExpectedDeliveryDate() == null || !po.getReceivedDate().isAfter(po.getExpectedDeliveryDate()))
                .count();
        double averageDays = delivered.stream()
                .mapToLong(po -> ChronoUnit.DAYS.between(po.getOrderDate(), po.getReceivedDate()))
                .average()
                .orElse(0);
        BigDecimal totalSpend = orders.stream()
                .map(PurchaseOrder::getTotalAmount)
                .filter(value -> value != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CounteragentPerformanceDto(counteragent.getId(), orders.size(), delivered.size(), onTime, late, averageDays, totalSpend);
    }

    public Counteragent loadActive(UUID id, String context) {
        Counteragent counteragent = load(id);
        if (counteragent.getStatus() != CounteragentStatus.ACTIVE) {
            throw RestException.badRequest("Inactive counteragents cannot be selected for " + context);
        }
        return counteragent;
    }

    public Counteragent load(UUID id) {
        return counteragentRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Counteragent not found: " + id));
    }

    public List<Counteragent> load(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return counteragentRepository.findAllByIdInAndIsDeletedFalse(ids);
    }

    private void apply(Counteragent counteragent, CounteragentRequest request) {
        counteragent.setName(request.name());
        counteragent.setTaxNumber(trimToNull(request.taxNumber()));
        counteragent.setBaseInn(resolveBaseInn(
                trimToNull(request.taxNumber()),
                trimToNull(request.baseInn())
        ));
        counteragent.setContactPerson(trimToNull(request.contactPerson()));
        counteragent.setPhone(trimToNull(request.phone()));
        counteragent.setEmail(trimToNull(request.email()));
        counteragent.setAddress(trimToNull(request.address()));
        counteragent.setSpecialization(trimToNull(request.specialization()));
        counteragent.setDirectorName(trimToNull(request.directorName()));
        counteragent.setBankName(trimToNull(request.bankName()));
        counteragent.setBankAccount(trimToNull(request.bankAccount()));
        counteragent.setMfo(trimToNull(request.mfo()));
        counteragent.setStatus(request.status() != null ? request.status() : CounteragentStatus.ACTIVE);
    }

    private String resolveCode(String requestedCode) {
        String code = trimToNull(requestedCode);
        if (code != null) {
            if (counteragentRepository.existsByCodeAndIsDeletedFalse(code)) {
                throw RestException.conflict("Counteragent code already exists: " + code);
            }
            return code;
        }
        String prefix = "CA-" + java.time.Year.now().getValue() + "-";
        return CodeGenerationUtils.nextYearSequenceCode(
                "CA",
                () -> counteragentRepository.maxSequenceByCodePrefix(prefix),
                counteragentRepository::existsByCodeAndIsDeletedFalse
        );
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String resolveBaseInn(String taxNumber, String explicitBaseInn) {
        if (explicitBaseInn != null && !explicitBaseInn.isBlank()) {
            return explicitBaseInn.trim();
        }
        if (taxNumber == null || taxNumber.isBlank()) {
            return null;
        }
        String trimmed = taxNumber.trim();
        int underscoreIdx = trimmed.lastIndexOf('_');
        if (underscoreIdx > 0) {
            String suffix = trimmed.substring(underscoreIdx + 1);
            if (suffix.matches("\\d+")) {
                return trimmed.substring(0, underscoreIdx);
            }
        }
        return trimmed;
    }
}
