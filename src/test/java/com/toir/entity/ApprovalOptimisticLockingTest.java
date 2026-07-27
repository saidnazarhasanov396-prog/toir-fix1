package com.toir.entity;

import jakarta.persistence.Version;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApprovalOptimisticLockingTest {

    @Test
    void approvalRequestAndStepHaveVersionFields() throws Exception {
        assertThat(ApprovalRequest.class.getDeclaredField("version").isAnnotationPresent(Version.class)).isTrue();
        assertThat(ApprovalStep.class.getDeclaredField("version").isAnnotationPresent(Version.class)).isTrue();
        assertThat(ApprovalTemplate.class.getDeclaredField("version").isAnnotationPresent(Version.class)).isTrue();
    }
}
