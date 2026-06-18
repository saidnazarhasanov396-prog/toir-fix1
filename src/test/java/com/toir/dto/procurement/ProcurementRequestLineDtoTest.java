package com.toir.dto.procurement;

import com.toir.entity.SparePart;
import com.toir.entity.equipment.ProcurementRequestLine;
import com.toir.entity.projects.ProcurementRequest;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProcurementRequestLineDtoTest {

    @Test
    void fromWithNonNullSparePartPopulatesCodeAndName() {
        UUID sparePartId = UUID.randomUUID();
        ProcurementRequestLine line = line(sparePartId, 3.0, 12.5);
        SparePart sparePart = sparePart(sparePartId, "SP-001", "Bearing 6205");

        ProcurementRequestLineDto dto = ProcurementRequestLineDto.from(line, sparePart);

        assertThat(dto.sparePartId()).isEqualTo(sparePartId);
        assertThat(dto.sparePartCode()).isEqualTo("SP-001");
        assertThat(dto.sparePartName()).isEqualTo("Bearing 6205");
        assertThat(dto.quantity()).isEqualTo(3.0);
        assertThat(dto.unitPrice()).isEqualTo(12.5);
    }

    @Test
    void fromWithNullSparePartYieldsNullCodeAndName() {
        UUID sparePartId = UUID.randomUUID();
        ProcurementRequestLine line = line(sparePartId, 2.0, null);

        ProcurementRequestLineDto dto = ProcurementRequestLineDto.from(line, null);

        assertThat(dto.sparePartId()).isEqualTo(sparePartId);
        assertThat(dto.sparePartCode()).isNull();
        assertThat(dto.sparePartName()).isNull();
    }

    @Test
    void fromProcurementRequestDtoPopulatesLinesFromMap() {
        UUID sparePartId = UUID.randomUUID();
        ProcurementRequest request = new ProcurementRequest();
        request.setId(UUID.randomUUID());
        request.setNumber("PR-2026-00001");
        request.setStatus(com.toir.enums.ProcurementRequestStatus.DRAFT);
        ProcurementRequestLine line = line(sparePartId, 5.0, 10.0);
        line.setRequest(request);
        request.getLines().add(line);

        SparePart sparePart = sparePart(sparePartId, "SP-999", "Bolt M10");
        Map<UUID, SparePart> sparePartsById = Map.of(sparePartId, sparePart);

        ProcurementRequestDto dto = ProcurementRequestDto.from(request, sparePartsById);

        assertThat(dto.lines()).hasSize(1);
        assertThat(dto.lines().getFirst().sparePartCode()).isEqualTo("SP-999");
        assertThat(dto.lines().getFirst().sparePartName()).isEqualTo("Bolt M10");
    }

    @Test
    void fromProcurementRequestDtoWithMissingSparePartInMapYieldsNullFields() {
        UUID knownId = UUID.randomUUID();
        UUID unknownId = UUID.randomUUID();
        ProcurementRequest request = new ProcurementRequest();
        request.setId(UUID.randomUUID());
        request.setNumber("PR-2026-00002");
        request.setStatus(com.toir.enums.ProcurementRequestStatus.DRAFT);
        ProcurementRequestLine knownLine = line(knownId, 1.0, null);
        knownLine.setRequest(request);
        ProcurementRequestLine unknownLine = line(unknownId, 2.0, null);
        unknownLine.setRequest(request);
        request.getLines().add(knownLine);
        request.getLines().add(unknownLine);

        Map<UUID, SparePart> sparePartsById = Map.of(knownId, sparePart(knownId, "SP-A", "Part A"));

        ProcurementRequestDto dto = ProcurementRequestDto.from(request, sparePartsById);

        assertThat(dto.lines()).hasSize(2);
        ProcurementRequestLineDto known = dto.lines().stream()
                .filter(l -> knownId.equals(l.sparePartId())).findFirst().orElseThrow();
        ProcurementRequestLineDto unknown = dto.lines().stream()
                .filter(l -> unknownId.equals(l.sparePartId())).findFirst().orElseThrow();

        assertThat(known.sparePartCode()).isEqualTo("SP-A");
        assertThat(known.sparePartName()).isEqualTo("Part A");
        assertThat(unknown.sparePartCode()).isNull();
        assertThat(unknown.sparePartName()).isNull();
    }

    private ProcurementRequestLine line(UUID sparePartId, double quantity, Double unitPrice) {
        ProcurementRequestLine line = new ProcurementRequestLine();
        line.setId(UUID.randomUUID());
        line.setSparePartId(sparePartId);
        line.setQuantity(quantity);
        line.setUnit("pcs");
        line.setUnitPrice(unitPrice);
        line.setEstimatedCost(unitPrice == null ? 0 : unitPrice * quantity);
        return line;
    }

    private SparePart sparePart(UUID id, String code, String name) {
        SparePart sp = new SparePart();
        sp.setId(id);
        sp.setCode(code);
        sp.setName(name);
        return sp;
    }
}
