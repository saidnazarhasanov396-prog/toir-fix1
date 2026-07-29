package com.toir.service.maintanance;

import static com.toir.service.maintanance.MaintenanceScheduleContentTestFixtures.canonicalContent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.toir.enums.MaintenanceScheduleContentHashVersion;
import com.toir.exception.MaintenanceScheduleCalculationConflictException;
import com.toir.exception.MaintenanceScheduleCalculationConflictException.Reason;
import org.junit.jupiter.api.Test;

class MaintenanceScheduleRevisionServiceTest {

    private final MaintenanceScheduleRevisionService revisionService =
            new MaintenanceScheduleRevisionService(
                    new MaintenanceScheduleContentHasher(
                            new MaintenanceScheduleCanonicalContentSerializerV1()));

    @Test
    void initialTransitionIsRevisionOneAndComputesHashExactlyOnce() {
        MaintenanceScheduleRevisionTransition transition =
                revisionService.initial(canonicalContent());

        assertThat(transition.previousRevision()).isNull();
        assertThat(transition.nextRevision()).isEqualTo(1L);
        assertThat(transition.hashVersion())
                .isEqualTo(MaintenanceScheduleContentHashVersion.V1);
        assertThat(transition.calculatedHash()).matches("[0-9a-f]{64}");
    }

    @Test
    void nextTransitionIsExactlyCurrentPlusOne() {
        MaintenanceScheduleCalculationContent revisionTwo =
                withRevision(canonicalContent(), 2L);

        MaintenanceScheduleRevisionTransition transition =
                revisionService.next(1L, revisionTwo);

        assertThat(transition.previousRevision()).isEqualTo(1L);
        assertThat(transition.nextRevision()).isEqualTo(2L);
        assertThat(transition.calculatedHash()).matches("[0-9a-f]{64}");
    }

    @Test
    void invalidInitialCurrentAndSkippedRevisionsAreRejected() {
        assertInvalid(() -> revisionService.initial(
                withRevision(canonicalContent(), 0L)));
        assertInvalid(() -> revisionService.next(null, withRevision(canonicalContent(), 1L)));
        assertInvalid(() -> revisionService.next(0L, withRevision(canonicalContent(), 1L)));
        assertInvalid(() -> revisionService.next(-1L, withRevision(canonicalContent(), 1L)));
        assertInvalid(() -> revisionService.next(1L, withRevision(canonicalContent(), 3L)));
        assertInvalid(() -> revisionService.next(
                Long.MAX_VALUE,
                withRevision(canonicalContent(), Long.MAX_VALUE)));
    }

    private static void assertInvalid(Runnable operation) {
        assertThatThrownBy(operation::run)
                .isInstanceOf(MaintenanceScheduleCalculationConflictException.class)
                .satisfies(error -> {
                    MaintenanceScheduleCalculationConflictException conflict =
                            (MaintenanceScheduleCalculationConflictException) error;
                    assertThat(conflict.getReason())
                            .isEqualTo(Reason.INVALID_REVISION_TRANSITION);
                    assertThat(conflict.getErrorCode())
                            .isEqualTo("PPR_CALCULATION_INVALID_REVISION_TRANSITION");
                });
    }

    private static MaintenanceScheduleCalculationContent withRevision(
            MaintenanceScheduleCalculationContent source,
            long revision) {
        return new MaintenanceScheduleCalculationContent(
                source.planName(),
                source.notes(),
                source.periodStart(),
                source.periodEnd(),
                source.selectionScopeType(),
                source.planScopeType(),
                source.departmentId(),
                source.equipmentIds(),
                source.equipmentTypeIds(),
                source.regulationIds(),
                source.anchorMode(),
                source.recurrenceAnchor(),
                source.shiftFromExcludedWeekdays(),
                source.excludedWeekdays(),
                revision,
                source.snapshotItems()
        );
    }
}
