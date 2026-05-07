package com.toir.service;

import com.toir.dto.procurement.ProcurementLineRequest;
import com.toir.dto.procurement.ProcurementRequestDto;
import com.toir.dto.procurement.ProcurementRequestRequest;
import com.toir.entity.SparePart;
import com.toir.entity.equipment.ProcurementRequestLine;
import com.toir.entity.projects.ProcurementRequest;
import com.toir.entity.warehouse.WarehouseStock;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.enums.ProcurementRequestStatus;
import com.toir.exception.RestException;
import com.toir.repository.ProcurementRequestRepository;
import com.toir.repository.SparePartRepository;
import com.toir.repository.WarehouseStockRepository;
import com.toir.util.AuditBuilderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ProcurementRequestService {

    private final ProcurementRequestRepository repo;
    private final SparePartRepository sparePartRepository;
    private final WarehouseStockRepository stockRepository;
    private final AuditBuilderService auditBuilderService;



    @Transactional(readOnly = true)
    public List<ProcurementRequestDto> findAll(ProcurementRequestStatus status, UUID departmentId) {
        List<ProcurementRequest> list;
        if (status != null) list = repo.findAllByStatusAndIsDeletedFalseOrderByUpdatedAtDesc(status);
        else if (departmentId != null) list = repo.findAllByDepartmentIdAndIsDeletedFalseOrderByUpdatedAtDesc(departmentId);
        else list = repo.findAllByIsDeletedFalseOrderByUpdatedAtDesc();
        return list.stream().map(ProcurementRequestDto::from).toList();
    }

    @Transactional(readOnly = true)
    public ProcurementRequestDto findById(UUID id) {
        return ProcurementRequestDto.from(load(id));
    }

    @Transactional
    public ProcurementRequestDto create(ProcurementRequestRequest r) {
        ProcurementRequest p = new ProcurementRequest();
        p.setNumber(nextNumber());
        p.setTitle(r.title());
        p.setDescription(r.description());
        p.setDepartmentId(r.departmentId());
        p.setWarehouseId(r.warehouseId());
        p.setRequiredBy(r.requiredBy());
        p.setStatus(ProcurementRequestStatus.DRAFT);
        p.setSource("MANUAL");
        if (r.lines() != null) {
            for (ProcurementLineRequest line : r.lines()) {
                p.getLines().add(buildLine(p, line));
            }
        }
        recalcTotal(p);
        ProcurementRequest saved = repo.save(p);

        auditBuilderService.log(
                "procurement_request",
                saved.getId().toString(),
                AuditAction.CREATE,
                AuditModule.PROCUREMENT_REQUEST,
                "Заявка на закупку создана",
                null,
                saved
        );

        return ProcurementRequestDto.from(saved);
    }

    @Transactional
    public ProcurementRequestDto addLine(UUID id, ProcurementLineRequest line) {
        ProcurementRequest p = load(id);
        if (p.getStatus() != ProcurementRequestStatus.DRAFT) {
            throw RestException.badRequest("Can only add lines to DRAFT requests");
        }
        p.getLines().add(buildLine(p, line));
        recalcTotal(p);

        ProcurementRequest saved = repo.save(p);
        auditBuilderService.log(
                "procurement_request",
                saved.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.PROCUREMENT_REQUEST,
                "Заявка на закупку обновлена",
                p,
                saved
        );


        return ProcurementRequestDto.from(p);
    }

    @Transactional
    public ProcurementRequestDto submit(UUID id) {
        ProcurementRequest p = load(id);
        if (p.getStatus() != ProcurementRequestStatus.DRAFT) {
            throw RestException.badRequest("Only DRAFT can be submitted");
        }
        if (p.getLines().isEmpty()) {
            throw RestException.badRequest("Cannot submit procurement request with no lines");
        }
        p.setStatus(ProcurementRequestStatus.SUBMITTED);
        p.setSubmittedAt(Instant.now());

        ProcurementRequest saved = repo.save(p);
        auditBuilderService.log(
                "procurement_request",
                saved.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.PROCUREMENT_REQUEST,
                "Заявка на закупку обновлена",
                p,
                saved
        );

        return ProcurementRequestDto.from(p);
    }

    @Transactional
    public ProcurementRequestDto approve(UUID id) {
        ProcurementRequest p = load(id);
        if (p.getStatus() != ProcurementRequestStatus.SUBMITTED) {
            throw RestException.badRequest("Only SUBMITTED can be approved");
        }
        p.setStatus(ProcurementRequestStatus.APPROVED);
        p.setApprovedAt(Instant.now());
        ProcurementRequest saved = repo.save(p);
        auditBuilderService.log(
                "procurement_request",
                saved.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.PROCUREMENT_REQUEST,
                "Заявка на закупку обновлена",
                p,
                saved
        );
        return ProcurementRequestDto.from(p);
    }

    @Transactional
    public ProcurementRequestDto reject(UUID id, String reason) {
        ProcurementRequest p = load(id);
        if (p.getStatus() == ProcurementRequestStatus.RECEIVED
                || p.getStatus() == ProcurementRequestStatus.CANCELLED) {
            throw RestException.badRequest("Cannot reject completed procurement request");
        }
        p.setStatus(ProcurementRequestStatus.REJECTED);
        p.setRejectionReason(reason);

        ProcurementRequest saved = repo.save(p);
        auditBuilderService.log(
                "procurement_request",
                saved.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.PROCUREMENT_REQUEST,
                "Заявка на закупку обновлена",
                p,
                saved
        );
        return ProcurementRequestDto.from(p);
    }

    @Transactional
    public ProcurementRequestDto markOrdered(UUID id) {
        ProcurementRequest p = load(id);
        if (p.getStatus() != ProcurementRequestStatus.APPROVED) {
            throw RestException.badRequest("Only APPROVED can be marked ORDERED");
        }
        p.setStatus(ProcurementRequestStatus.ORDERED);
        p.setOrderedAt(Instant.now());
        ProcurementRequest saved = repo.save(p);
        auditBuilderService.log(
                "procurement_request",
                saved.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.PROCUREMENT_REQUEST,
                "Заявка на закупку обновлена",
                p,
                saved
        );
        return ProcurementRequestDto.from(p);
    }

    @Transactional
    public ProcurementRequestDto markReceived(UUID id) {
        ProcurementRequest p = load(id);
        if (p.getStatus() != ProcurementRequestStatus.ORDERED) {
            throw RestException.badRequest("Only ORDERED can be marked RECEIVED");
        }
        p.setStatus(ProcurementRequestStatus.RECEIVED);
        p.setReceivedAt(Instant.now());
        ProcurementRequest saved = repo.save(p);
        auditBuilderService.log(
                "procurement_request",
                saved.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.PROCUREMENT_REQUEST,
                "Заявка на закупку обновлена",
                p,
                saved
        );
        return ProcurementRequestDto.from(p);
    }

    @Transactional
    public ProcurementRequestDto cancel(UUID id) {
        ProcurementRequest p = load(id);
        if (p.getStatus() == ProcurementRequestStatus.RECEIVED) {
            throw RestException.badRequest("Cannot cancel received procurement request");
        }
        p.setStatus(ProcurementRequestStatus.CANCELLED);
        ProcurementRequest saved = repo.save(p);
        auditBuilderService.log(
                "procurement_request",
                saved.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.PROCUREMENT_REQUEST,
                "Заявка на закупку обновлена",
                p,
                saved
        );
        return ProcurementRequestDto.from(p);
    }

    /** Сгенерировать заявку(и) на закупку из low-stock позиций (по складу). */
    @Transactional
    public List<ProcurementRequestDto> generateFromLowStock(UUID warehouseId) {
        List<WarehouseStock> stocks = stockRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .filter(s -> warehouseId == null || s.getWarehouseId().equals(warehouseId))
                .filter(s -> s.getAvailable() < s.getMinQty())
                .toList();
        if (stocks.isEmpty()) return List.of();

        Map<UUID, ProcurementRequest> byWarehouse = new HashMap<>();
        for (WarehouseStock s : stocks) {
            ProcurementRequest p = byWarehouse.computeIfAbsent(s.getWarehouseId(), wh -> {
                ProcurementRequest pr = new ProcurementRequest();
                pr.setNumber(nextNumber());
                pr.setTitle("Auto low-stock replenishment");
                pr.setDescription("Автозаявка: пополнение запасов ниже минимального уровня");
                pr.setWarehouseId(wh);
                pr.setStatus(ProcurementRequestStatus.DRAFT);
                pr.setSource("AUTO");
                pr.setRequiredBy(LocalDate.now(ZoneOffset.UTC).plusDays(14));
                return pr;
            });
            SparePart sp = sparePartRepository.findByIdAndIsDeletedFalse(s.getSparePartId()).orElse(null);
            if (sp == null) continue;
            double target = s.getMaxQty() != null ? s.getMaxQty() : s.getMinQty() * 2;
            double needed = Math.max(0, target - s.getAvailable());
            if (needed <= 0) continue;
            ProcurementRequestLine line = new ProcurementRequestLine();
            line.setRequest(p);
            line.setSparePartId(sp.getId());
            line.setQuantity(needed);
            line.setUnit(sp.getUnit());
            line.setEstimatedCost(0.0);
            line.setNotes("Автогенерация: available=" + s.getAvailable() + ", min=" + s.getMinQty());
            p.getLines().add(line);
        }

        List<ProcurementRequestDto> result = new ArrayList<>();
        for (ProcurementRequest p : byWarehouse.values()) {
            if (p.getLines().isEmpty()) continue;
            recalcTotal(p);
            ProcurementRequest saved = repo.save(p);

            auditBuilderService.log(
                    "procurement_request",
                    saved.getId().toString(),
                    AuditAction.CREATE,
                    AuditModule.PROCUREMENT_REQUEST,
                    "Заявка на закупку создана",
                    null,
                    saved
            );

            result.add(ProcurementRequestDto.from(saved));
        }
        return result;
    }

    private ProcurementRequestLine buildLine(ProcurementRequest p, ProcurementLineRequest r) {
        SparePart sp = sparePartRepository.findByIdAndIsDeletedFalse(r.sparePartId())
                .orElseThrow(() -> RestException.notFound("Spare part not found: " + r.sparePartId()));
        ProcurementRequestLine line = new ProcurementRequestLine();
        line.setRequest(p);
        line.setSparePartId(sp.getId());
        line.setQuantity(r.quantity());
        line.setUnit(r.unit() != null ? r.unit() : sp.getUnit());
        line.setUnitPrice(r.unitPrice());
        line.setEstimatedCost(r.unitPrice() != null ? r.unitPrice() * r.quantity() : 0.0);
        line.setNotes(r.notes());
        return line;
    }

    private void recalcTotal(ProcurementRequest p) {
        double total = p.getLines().stream().mapToDouble(ProcurementRequestLine::getEstimatedCost).sum();
        p.setTotalEstimatedCost(total);
    }

    private ProcurementRequest load(UUID id) {
        return repo.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Procurement request not found: " + id));
    }

    private String nextNumber() {
        String base = "PR-" + LocalDate.now(ZoneOffset.UTC).getYear() + "-";
        long count = repo.countByIsDeletedFalse() + 1;
        String number;
        do {
            number = base + String.format("%05d", count);
            count++;
        } while (repo.existsByNumberAndIsDeletedFalse(number));
        return number;
    }
}
