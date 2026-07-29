package com.toir.service.maintanance;

import com.toir.enums.MaintenanceScheduleContentHashVersion;
import com.toir.exception.MaintenanceScheduleCalculationConflictException;
import com.toir.exception.MaintenanceScheduleCalculationConflictException.Reason;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MaintenanceScheduleRevisionService {

    private static final long INITIAL_REVISION = 1L;
    private static final MaintenanceScheduleContentHashVersion HASH_VERSION =
            MaintenanceScheduleContentHashVersion.V1;

    private final MaintenanceScheduleContentHasher contentHasher;

    public MaintenanceScheduleRevisionTransition initial(
            MaintenanceScheduleCalculationContent content) {
        Objects.requireNonNull(content, "content");
        if (content.calculationRevision() != INITIAL_REVISION) {
            throw invalidTransition();
        }
        return transition(null, content);
    }

    public MaintenanceScheduleRevisionTransition next(
            Long currentRevision,
            MaintenanceScheduleCalculationContent nextContent) {
        Objects.requireNonNull(nextContent, "nextContent");
        if (currentRevision == null || currentRevision < INITIAL_REVISION) {
            throw invalidTransition();
        }
        long expectedNext;
        try {
            expectedNext = Math.addExact(currentRevision, 1L);
        } catch (ArithmeticException exception) {
            throw invalidTransition();
        }
        if (nextContent.calculationRevision() != expectedNext) {
            throw invalidTransition();
        }
        return transition(currentRevision, nextContent);
    }

    private MaintenanceScheduleRevisionTransition transition(
            Long previousRevision,
            MaintenanceScheduleCalculationContent content) {
        String calculatedHash = contentHasher.compute(HASH_VERSION, content);
        return new MaintenanceScheduleRevisionTransition(
                previousRevision,
                content.calculationRevision(),
                HASH_VERSION,
                calculatedHash
        );
    }

    private static MaintenanceScheduleCalculationConflictException
            invalidTransition() {
        return new MaintenanceScheduleCalculationConflictException(
                Reason.INVALID_REVISION_TRANSITION);
    }
}
