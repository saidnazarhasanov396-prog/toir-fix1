package com.toir.controller;

import com.toir.dto.integration.EmployeeClearanceReceiptRequest;
import com.toir.dto.integration.EmployeeClearanceReceiptV2Request;
import com.toir.service.integration.ErpEmployeeClearanceOutboxService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Manual, reviewed assertion that TOIR has removed an employee's operational assignments. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/integrations/offboarding")
public class EmployeeClearanceIntegrationController {
    private final ErpEmployeeClearanceOutboxService service;

    @PostMapping("/employee-clearances")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*')")
    public void submit(@Valid @RequestBody EmployeeClearanceReceiptRequest request) {
        service.queue(request);
    }

    /** Receives a reviewed clearance tied to the authoritative ERP offboarding case. */
    @PostMapping("/employee-clearances/v2")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*')")
    public void submitV2(@Valid @RequestBody EmployeeClearanceReceiptV2Request request) {
        service.queueV2(request);
    }
}
