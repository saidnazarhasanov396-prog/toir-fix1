package com.toir.controller;
import com.toir.dto.rcm.EquipmentRiskScore;
import com.toir.dto.rcm.RcmSnapshotCaptureResponse;
import com.toir.entity.RcmSnapshot;
import com.toir.service.RcmAutoPlannerService;
import com.toir.service.RcmAutoPlanPreviewService;
import com.toir.dto.rcm.autoplan.RcmAutoPlanConfirmRequest;
import com.toir.dto.rcm.autoplan.RcmAutoPlanConfirmResult;
import com.toir.dto.rcm.autoplan.RcmAutoPlanPreviewResponse;
import jakarta.validation.Valid;
import com.toir.service.RcmService;
import com.toir.util.PaginationUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/rcm")
@Tag(name = "rcm")
@RequiredArgsConstructor
public class RcmController {

    private final RcmService service;
    private final RcmAutoPlannerService autoPlannerService;
    private final RcmAutoPlanPreviewService previewService;

    @GetMapping("/risk-scores")
    public ResponseEntity<Page<EquipmentRiskScore>> list(@RequestParam(defaultValue = "0") int top,
                                                         @RequestParam(defaultValue = "0") int page,
                                                         @RequestParam(defaultValue = "20") int size,
                                                         @RequestParam(defaultValue = "riskScore") String sortBy,
                                                         @RequestParam(defaultValue = "desc") String sortDir,
                                                         @RequestParam(required = false) String lang,
                                                         @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) {
        String responseLang = requestedLanguage(lang, acceptLanguage);
        List<EquipmentRiskScore> scores = responseLang == null
                ? (top > 0 ? service.topN(top, sortBy, sortDir) : service.computeAll(sortBy, sortDir))
                : (top > 0
                ? service.topN(top, sortBy, sortDir, responseLang)
                : service.computeAll(sortBy, sortDir, responseLang));
        return ResponseEntity.ok(PaginationUtils.page(scores, page, size));
    }

    @PostMapping("/snapshot")
    public ResponseEntity<RcmSnapshotCaptureResponse> capture() {
        List<RcmSnapshot> items = service.captureSnapshot();
        return ResponseEntity.ok(new RcmSnapshotCaptureResponse(items.size(), items));
    }

    @GetMapping("/snapshot/{equipmentId}")
    public ResponseEntity<List<RcmSnapshot>> history(@PathVariable UUID equipmentId) {
        return ResponseEntity.ok(service.historyFor(equipmentId));
    }

    @PostMapping("/auto-plan/preview")
    @PreAuthorize("hasAnyAuthority('PPR_TASK_CREATE', 'SYSTEM_ADMIN', '*' )")
    public ResponseEntity<RcmAutoPlanPreviewResponse> previewAutoPlan(
            @RequestParam(defaultValue = "30") int riskThreshold,
            @RequestParam(required = false) UUID planId) {
        return ResponseEntity.ok(previewService.preview(riskThreshold, planId));
    }

    @PostMapping("/auto-plan/confirm")
    @PreAuthorize("hasAnyAuthority('PPR_TASK_CREATE', 'SYSTEM_ADMIN', '*' )")
    public ResponseEntity<RcmAutoPlanConfirmResult> confirmAutoPlan(
            @Valid @RequestBody RcmAutoPlanConfirmRequest request) {
        return ResponseEntity.ok(autoPlannerService.confirm(request));
    }

    @Deprecated
    @PostMapping("/auto-plan")
    public ResponseEntity<RcmAutoPlannerService.AutoPlanResult> autoPlan(@RequestParam(defaultValue = "30") int riskThreshold,
                                                         @RequestParam(required = false) UUID planId) {
        return ResponseEntity.ok(autoPlannerService.generate(riskThreshold, planId));
    }

    private String requestedLanguage(String lang, String acceptLanguage) {
        if (lang != null && !lang.isBlank()) {
            return lang;
        }
        if (acceptLanguage != null && !acceptLanguage.isBlank()) {
            return acceptLanguage;
        }
        return null;
    }
}
