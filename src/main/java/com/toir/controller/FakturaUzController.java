package com.toir.controller;

import com.toir.dto.faktura.FakturaUzCredentialCheckRequest;
import com.toir.dto.faktura.FakturaUzCredentialCheckResponse;
import com.toir.dto.faktura.FakturaUzDocumentContentDto;
import com.toir.dto.faktura.FakturaUzDocumentDto;
import com.toir.dto.faktura.FakturaUzImportHistoryDto;
import com.toir.dto.faktura.FakturaUzImportRequest;
import com.toir.dto.faktura.FakturaUzImportResponse;
import com.toir.security.RequiresAdmin;
import com.toir.service.faktura.FakturaUzService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/integrations/faktura-uz")
@Tag(name = "faktura-uz")
@RequiresAdmin
@RequiredArgsConstructor
public class FakturaUzController {
    private final FakturaUzService service;

    @PostMapping("/credentials/check")
    public ResponseEntity<FakturaUzCredentialCheckResponse> checkCredentials(
            @Valid @RequestBody FakturaUzCredentialCheckRequest request) {
        return ResponseEntity.ok(service.checkCredentials(request));
    }

    @PostMapping("/import")
    public ResponseEntity<FakturaUzImportResponse> importDocuments(
            @Valid @RequestBody FakturaUzImportRequest request) {
        return ResponseEntity.ok(service.importDocuments(request));
    }

    @GetMapping("/documents")
    public ResponseEntity<Page<FakturaUzDocumentDto>> documents(
            @RequestParam(required = false) UUID endpointId,
            @RequestParam(required = false) LocalDate fromDate,
            @RequestParam(required = false) LocalDate toDate,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Integer type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(service.getDocuments(
                endpointId,
                fromDate,
                toDate,
                query,
                type,
                PageRequest.of(page, size)
        ));
    }

    @GetMapping("/documents/{type}/{uniqueId}/content")
    public ResponseEntity<FakturaUzDocumentContentDto> documentContent(
            @PathVariable Integer type,
            @PathVariable String uniqueId) {
        return ResponseEntity.ok(service.getDocumentContent(type, uniqueId));
    }

    @GetMapping("/import-history")
    public ResponseEntity<Page<FakturaUzImportHistoryDto>> importHistory(
            @RequestParam(required = false) UUID endpointId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(service.getImportHistory(
                endpointId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "importedAt"))
        ));
    }
}
