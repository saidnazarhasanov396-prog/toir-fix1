package com.toir.controller.equipment;
import com.toir.dto.equipmentlabel.EquipmentLabelResponse;
import com.toir.entity.equipment.Equipment;
import com.toir.exception.RestException;
import com.toir.repository.equipment.EquipmentRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/equipment")
@Tag(name = "equipment-label")
@RequiredArgsConstructor
public class EquipmentLabelController {

    private final EquipmentRepository repository;

    @GetMapping("/{id}/label")
    public ResponseEntity<EquipmentLabelResponse> label(@PathVariable UUID id) {
        Equipment eq = repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Equipment not found: " + id));
        return ResponseEntity.ok(buildPayload(eq));
    }

    @GetMapping("/by-code/{code}")
    public ResponseEntity<EquipmentLabelResponse> resolveByCode(@PathVariable String code) {
        Equipment eq = repository.findByCodeAndIsDeletedFalse(code)
                .or(() -> repository.findByInventoryNumberAndIsDeletedFalse(code))
                .orElseThrow(() -> RestException.notFound("Equipment not found by code/inventory: " + code));
        return ResponseEntity.ok(buildPayload(eq));
    }

    @GetMapping("/resolve-scan")
    public ResponseEntity<EquipmentLabelResponse> resolveScan(@RequestParam String payload) {
        String value = payload;
        if (payload != null && payload.startsWith("toir://equipment/")) {
            String rest = payload.substring("toir://equipment/".length());
            int qm = rest.indexOf('?');
            value = qm >= 0 ? rest.substring(0, qm) : rest;
        }
        if (value == null || value.isBlank()) {
            throw RestException.badRequest("Empty scan payload");
        }
        try {
            UUID id = UUID.fromString(value);
            Equipment eq = repository.findByIdAndIsDeletedFalse(id)
                    .orElseThrow(() -> RestException.notFound("Equipment not found: " + id));
            return ResponseEntity.ok(buildPayload(eq));
        } catch (IllegalArgumentException ignored) {
            return resolveByCode(value);
        }
    }

    @GetMapping(value = "/{id}/label.svg", produces = "image/svg+xml")
    public ResponseEntity<byte[]> labelSvg(@PathVariable UUID id) {
        Equipment eq = repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Equipment not found: " + id));
        String payload = String.format("toir://equipment/%s?code=%s&inv=%s",
                eq.getId(), eq.getCode(), eq.getInventoryNumber());
        String svg = "<svg xmlns='http://www.w3.org/2000/svg' width='320' height='180' viewBox='0 0 320 180'>" +
                "<rect width='100%' height='100%' fill='white' stroke='black'/>" +
                "<text x='12' y='30' font-family='monospace' font-size='16' font-weight='bold'>" + escape(eq.getCode()) + "</text>" +
                "<text x='12' y='56' font-family='monospace' font-size='13'>" + escape(eq.getName()) + "</text>" +
                "<text x='12' y='78' font-family='monospace' font-size='12'>INV: " + escape(eq.getInventoryNumber()) + "</text>" +
                "<text x='12' y='100' font-family='monospace' font-size='10'>" + escape(payload) + "</text>" +
                "<text x='12' y='170' font-family='monospace' font-size='9' fill='#666'>Issued " + Instant.now() + "</text>" +
                "</svg>";
        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("image/svg+xml"))
                .body(svg.getBytes(StandardCharsets.UTF_8));
    }

    private EquipmentLabelResponse buildPayload(Equipment eq) {
        String qrPayload = String.format("toir://equipment/%s?code=%s&inv=%s",
                eq.getId(), eq.getCode(), eq.getInventoryNumber());
        return new EquipmentLabelResponse(
                eq,
                eq.getId(),
                eq.getCode(),
                eq.getInventoryNumber(),
                eq.getName(),
                qrPayload,
                Instant.now().toString()
        );
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
