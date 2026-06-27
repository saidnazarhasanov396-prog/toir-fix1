package com.toir.service;

import com.toir.dto.supplier.SupplierDto;
import com.toir.dto.supplier.SupplierPerformanceDto;
import com.toir.dto.supplier.SupplierRequest;
import com.toir.entity.PurchaseOrder;
import com.toir.entity.Supplier;
import com.toir.enums.PurchaseOrderStatus;
import com.toir.enums.SupplierType;
import com.toir.exception.RestException;
import com.toir.repository.PurchaseOrderRepository;
import com.toir.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SupplierService {

    private final SupplierRepository supplierRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;

    @Transactional(readOnly = true)
    public List<SupplierDto> findAll(String search, Boolean active) {
        return findAll(search, active, null);
    }

    @Transactional(readOnly = true)
    public List<SupplierDto> findAll(String search, Boolean active, SupplierType supplierType) {
        String normalizedSearch = trimToNull(search);
        List<Supplier> suppliers = normalizedSearch == null
                ? supplierRepository.findAllFiltered(active, supplierType)
                : supplierRepository.search(normalizedSearch, active, supplierType);
        return suppliers.stream()
                .map(SupplierDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public SupplierDto findById(UUID id) {
        return SupplierDto.from(load(id));
    }

    @Transactional
    public SupplierDto create(SupplierRequest request) {
        Supplier supplier = new Supplier();
        supplier.setCode(resolveCode(request.code()));
        apply(supplier, request);
        return SupplierDto.from(supplierRepository.save(supplier));
    }

    @Transactional
    public SupplierDto update(UUID id, SupplierRequest request) {
        Supplier supplier = load(id);
        String requestedCode = trimToNull(request.code());
        if (requestedCode != null && !requestedCode.equals(supplier.getCode())) {
            if (supplierRepository.existsByCodeAndIsDeletedFalse(requestedCode)) {
                throw RestException.conflict("Supplier code already exists: " + requestedCode);
            }
            supplier.setCode(requestedCode);
        }
        apply(supplier, request);
        return SupplierDto.from(supplierRepository.save(supplier));
    }

    @Transactional
    public void delete(UUID id) {
        Supplier supplier = load(id);
        supplier.setActive(false);
        supplierRepository.save(supplier);
    }

    @Transactional(readOnly = true)
    public SupplierPerformanceDto performance(UUID id) {
        Supplier supplier = load(id);
        List<PurchaseOrder> orders = purchaseOrderRepository.findAllBySupplierIdAndIsDeletedFalse(supplier.getId());
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
        return new SupplierPerformanceDto(supplier.getId(), orders.size(), delivered.size(), onTime, late, averageDays, totalSpend);
    }

    Supplier loadActive(UUID id) {
        return loadActiveForType(id, null, "new purchase orders");
    }

    public Supplier loadActiveForType(UUID id, SupplierType requiredType, String context) {
        Supplier supplier = load(id);
        if (!Boolean.TRUE.equals(supplier.getActive())) {
            throw RestException.badRequest("Inactive suppliers cannot be selected for " + context);
        }
        SupplierType actualType = supplier.getSupplierType() == null ? SupplierType.BOTH : supplier.getSupplierType();
        if (!actualType.supports(requiredType)) {
            throw RestException.badRequest("Supplier must support " + requiredType + " for " + context);
        }
        return supplier;
    }

    Supplier load(UUID id) {
        return supplierRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Supplier not found: " + id));
    }

    private void apply(Supplier supplier, SupplierRequest request) {
        supplier.setName(request.name());
        supplier.setContactPerson(trimToNull(request.contactPerson()));
        supplier.setPhone(trimToNull(request.phone()));
        supplier.setEmail(trimToNull(request.email()));
        supplier.setAddress(trimToNull(request.address()));
        supplier.setTaxNumber(trimToNull(request.taxNumber()));
        supplier.setBaseInn(resolveBaseInn(
                trimToNull(request.taxNumber()),
                trimToNull(request.baseInn())
        ));
        supplier.setDirectorName(trimToNull(request.directorName()));
        supplier.setBankName(trimToNull(request.bankName()));
        supplier.setBankAccount(trimToNull(request.bankAccount()));
        supplier.setMfo(trimToNull(request.mfo()));
        supplier.setSupplierType(request.supplierType() != null ? request.supplierType() : SupplierType.BOTH);
        if (request.active() != null) {
            supplier.setActive(request.active());
        } else if (supplier.getActive() == null) {
            supplier.setActive(Boolean.TRUE);
        }
    }

    private String resolveCode(String requestedCode) {
        String code = trimToNull(requestedCode);
        if (code != null) {
            if (supplierRepository.existsByCodeAndIsDeletedFalse(code)) {
                throw RestException.conflict("Supplier code already exists: " + code);
            }
            return code;
        }
        long next = supplierRepository.count() + 1;
        do {
            code = "SUP-" + String.format("%05d", next++);
        } while (supplierRepository.existsByCodeAndIsDeletedFalse(code));
        return code;
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
