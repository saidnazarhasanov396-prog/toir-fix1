package com.toir.service.ppr;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.toir.entity.PprTask;
import com.toir.exception.RestException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class PprWorkOrderEarlyCreationPolicyTest {

    @Test
    void regularCreateIsBlockedBeforeLeadDateAndAllowedWhenDue() {
        PprTask task = new PprTask();
        task.setScheduledStart(LocalDateTime.of(2026, 8, 20, 9, 0));
        task.setWorkOrderLeadDays(7);

        assertThatThrownBy(() -> PprWorkOrderEarlyCreationPolicy.requireDue(
                task, LocalDateTime.of(2026, 8, 12, 9, 0)))
                .isInstanceOf(RestException.class);
        assertThatCode(() -> PprWorkOrderEarlyCreationPolicy.requireDue(
                task, LocalDateTime.of(2026, 8, 13, 9, 0)))
                .doesNotThrowAnyException();
    }
}
