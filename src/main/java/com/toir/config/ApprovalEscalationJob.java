package com.toir.config;

import com.toir.service.approval.ApprovalGovernanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ApprovalEscalationJob {

    private final ApprovalGovernanceService approvalGovernanceService;

    @Scheduled(fixedDelayString = "${toir.approvals.scheduler.fixed-delay-ms:300000}")
    public void run() {
        try {
            int expired = approvalGovernanceService.expireOverdue();
            int escalated = approvalGovernanceService.escalateOverdue();
            if (expired > 0 || escalated > 0) {
                log.info("Approval governance job completed: expired={}, escalated={}", expired, escalated);
            }
        } catch (Exception ex) {
            log.error("Approval governance job failed", ex);
        }
    }
}
