package com.toir.service.approval;

import com.toir.service.repair.RepairRequestService;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RepairRequestApprovalTransactionContractTest {

    @Test
    void approvalFinalizersUseTheOuterApprovalTransaction() throws Exception {
        assertThat(RepairRequestService.class
                .getMethod("finalizeApprovalFromApprovalRequest", UUID.class)
                .isAnnotationPresent(Transactional.class))
                .isFalse();
        assertThat(RepairRequestService.class
                .getMethod("finalizeRejectionFromApprovalRequest", UUID.class, String.class)
                .isAnnotationPresent(Transactional.class))
                .isFalse();
    }
}
