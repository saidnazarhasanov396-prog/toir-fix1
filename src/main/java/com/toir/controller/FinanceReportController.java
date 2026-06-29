package com.toir.controller;

import com.toir.dto.budget.FinanceDashboardResponse;
import com.toir.dto.budget.FinanceReportRow;
import com.toir.security.RequiresSensitiveAccess;
import com.toir.service.FinanceReportService;
import com.toir.service.ReportsService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/budgets")
@Tag(name = "finance-reports")
@RequiresSensitiveAccess
@RequiredArgsConstructor
public class FinanceReportController {

    private static final String READ_AUTH =
            "hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('BUDGET_READ')";
    private static final String EXPORT_AUTH =
            "hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('FINANCE_REPORT_EXPORT')";

    private final FinanceReportService financeReportService;

    @GetMapping("/summary/dashboard")
    @PreAuthorize(READ_AUTH)
    public ResponseEntity<FinanceDashboardResponse> dashboard(@RequestParam(required = false) Integer year,
                                                              @RequestParam(required = false) Integer month,
                                                              @RequestParam(required = false) UUID departmentId) {
        return ResponseEntity.ok(financeReportService.dashboard(year, month, departmentId));
    }

    @GetMapping("/reports/plan-vs-actual-by-department")
    @PreAuthorize(READ_AUTH)
    public ResponseEntity<List<FinanceReportRow>> planVsActualByDepartment(@RequestParam(required = false) Integer year,
                                                                           @RequestParam(required = false) Integer month,
                                                                           @RequestParam(required = false) UUID departmentId) {
        return ResponseEntity.ok(financeReportService.planVsActualByDepartment(year, month, departmentId));
    }

    @GetMapping("/reports/plan-vs-actual-by-category")
    @PreAuthorize(READ_AUTH)
    public ResponseEntity<List<FinanceReportRow>> planVsActualByCategory(@RequestParam(required = false) Integer year,
                                                                         @RequestParam(required = false) Integer month,
                                                                         @RequestParam(required = false) UUID departmentId) {
        return ResponseEntity.ok(financeReportService.planVsActualByCategory(year, month, departmentId));
    }

    @GetMapping(value = "/reports/plan-vs-actual-by-department/export", produces = "text/csv;charset=UTF-8")
    @PreAuthorize(EXPORT_AUTH)
    public ResponseEntity<String> exportPlanVsActualByDepartment(@RequestParam(required = false) Integer year,
                                                                 @RequestParam(required = false) Integer month,
                                                                 @RequestParam(required = false) UUID departmentId) {
        return csv(financeReportService.planVsActualByDepartmentCsv(year, month, departmentId));
    }

    @GetMapping(value = "/reports/plan-vs-actual-by-category/export", produces = "text/csv;charset=UTF-8")
    @PreAuthorize(EXPORT_AUTH)
    public ResponseEntity<String> exportPlanVsActualByCategory(@RequestParam(required = false) Integer year,
                                                               @RequestParam(required = false) Integer month,
                                                               @RequestParam(required = false) UUID departmentId) {
        return csv(financeReportService.planVsActualByCategoryCsv(year, month, departmentId));
    }

    private ResponseEntity<String> csv(ReportsService.CsvFile file) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.filename() + "\"")
                .body(file.content());
    }
}
