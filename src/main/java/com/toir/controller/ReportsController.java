package com.toir.controller;

import com.toir.service.ReportsService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports")
@Tag(name = "reports")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('ANALYTICS_EXPORT')")
public class ReportsController {

    private final ReportsService reportsService;

    @GetMapping(value = "/rcm-risk.csv", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<String> rcmRiskCsv() {
        return csvResponse(reportsService.rcmRiskCsv());
    }

    @GetMapping(value = "/calibration-records.csv", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<String> calibrationRecordsCsv() {
        return csvResponse(reportsService.calibrationRecordsCsv());
    }

    @GetMapping(value = "/user-certifications.csv", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<String> userCertificationsCsv() {
        return csvResponse(reportsService.userCertificationsCsv());
    }

    @GetMapping(value = "/equipment.csv", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<String> equipmentCsv() {
        return csvResponse(reportsService.equipmentCsv());
    }

    @GetMapping(value = "/repair-requests.csv", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<String> repairRequestsCsv() {
        return csvResponse(reportsService.repairRequestsCsv());
    }

    @GetMapping(value = "/defects.csv", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<String> defectsCsv() {
        return csvResponse(reportsService.defectsCsv());
    }

    @GetMapping(value = "/work-orders.csv", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<String> workOrdersCsv() {
        return csvResponse(reportsService.workOrdersCsv());
    }

    @GetMapping(value = "/downtimes.csv", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<String> downtimesCsv() {
        return csvResponse(reportsService.downtimesCsv());
    }

    @GetMapping(value = "/actual-costs.csv", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<String> actualCostsCsv() {
        return csvResponse(reportsService.actualCostsCsv());
    }

    private ResponseEntity<String> csvResponse(ReportsService.CsvFile file) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.filename() + "\"")
                .body(file.content());
    }
}
