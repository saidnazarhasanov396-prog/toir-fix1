package com.toir.service.plannedshutdown;

import com.toir.dto.plannedshutdown.PlannedShutdownProductionReturnRequest;
import com.toir.dto.plannedshutdown.PlannedShutdownStartupTestRequest;
import com.toir.dto.plannedshutdown.PlannedShutdownStartupTestResultRequest;
import com.toir.entity.plannedshutdown.PlannedShutdownProductionReturn;
import com.toir.entity.plannedshutdown.PlannedShutdownStartupTest;
import com.toir.enums.PlannedShutdownItemStatus;
import com.toir.enums.PlannedShutdownStatus;
import com.toir.repository.plannedshutdown.PlannedShutdownProductionReturnRepository;
import com.toir.repository.plannedshutdown.PlannedShutdownStartupTestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import jakarta.validation.Validation;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlannedShutdownEvidenceServiceTest {
    @Mock PlannedShutdownStartupTestRepository testRepository;
    @Mock PlannedShutdownProductionReturnRepository productionReturnRepository;
    PlannedShutdownEvidenceService service;
    UUID shutdownId;
    UUID actor;

    @BeforeEach
    void setUp() {
        service = new PlannedShutdownEvidenceService(testRepository, productionReturnRepository);
        shutdownId = UUID.randomUUID();
        actor = UUID.randomUUID();
        lenient().when(testRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(productionReturnRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void mandatoryMissingOrFailedStartupTestsBlockStartup() {
        when(testRepository.findAllByPlannedShutdownIdAndIsDeletedFalseOrderByOrderNumberAsc(shutdownId))
                .thenReturn(List.of());
        assertThatThrownBy(() -> service.requireStartupReady(shutdownId))
                .hasMessageContaining("STARTUP_TESTS_MISSING");

        PlannedShutdownStartupTest failed = startupTest(true, PlannedShutdownItemStatus.FAILED);
        when(testRepository.findAllByPlannedShutdownIdAndIsDeletedFalseOrderByOrderNumberAsc(shutdownId))
                .thenReturn(List.of(failed));
        assertThatThrownBy(() -> service.requireStartupReady(shutdownId))
                .hasMessageContaining("STARTUP_TEST_FAILED");
    }

    @Test
    void resultCapturesMeasuredEvidenceAndIndependentPerformerVerifier() {
        UUID testId = UUID.randomUUID();
        PlannedShutdownStartupTest test = startupTest(true, PlannedShutdownItemStatus.PENDING);
        test.setId(testId);
        when(testRepository.findByIdAndPlannedShutdownIdAndIsDeletedFalse(testId, shutdownId))
                .thenReturn(Optional.of(test));
        UUID performer = UUID.randomUUID();

        var result = service.recordResult(shutdownId, testId, PlannedShutdownStatus.TESTING,
                new PlannedShutdownStartupTestResultRequest(7L, "42.1250", "bar", true,
                        "gauge-photo:asset-1", performer), actor);

        assertThat(result.status()).isEqualTo(PlannedShutdownItemStatus.PASSED);
        assertThat(result.measuredValue()).isEqualByComparingTo(new BigDecimal("42.1250"));
        assertThat(result.performerId()).isEqualTo(performer);
        assertThat(result.verifierId()).isEqualTo(actor);
        assertThat(result.verifiedAt()).isNotNull();
        assertThatThrownBy(() -> service.recordResult(shutdownId, testId, PlannedShutdownStatus.TESTING,
                new PlannedShutdownStartupTestResultRequest(7L, "42", "bar", true, "evidence", actor), actor))
                .hasMessageContaining("STARTUP_TEST_SEPARATION_OF_DUTY_REQUIRED");
    }

    @Test
    void productionReturnIsSingleCanonicalServerActorSignoff() {
        when(productionReturnRepository.findByPlannedShutdownIdAndIsDeletedFalse(shutdownId))
                .thenReturn(Optional.empty());

        var signoff = service.approveProductionReturn(shutdownId, PlannedShutdownStatus.STARTUP,
                3L, 2L, new PlannedShutdownProductionReturnRequest(7L, "stable pressure and temperature"), actor);

        assertThat(signoff.approvedById()).isEqualTo(actor);
        assertThat(signoff.approvedAt()).isNotNull();
        assertThat(signoff.scopeVersion()).isEqualTo(3L);
        assertThat(signoff.windowVersion()).isEqualTo(2L);
        verify(productionReturnRepository).saveAndFlush(argThat(saved -> saved.getApprovedById().equals(actor)
                && saved.getEvidence().equals("stable pressure and temperature")));

        when(productionReturnRepository.findByPlannedShutdownIdAndIsDeletedFalse(shutdownId))
                .thenReturn(Optional.of(new PlannedShutdownProductionReturn()));
        assertThatThrownBy(() -> service.approveProductionReturn(shutdownId, PlannedShutdownStatus.STARTUP,
                3L, 2L, new PlannedShutdownProductionReturnRequest(7L, "repeat"), actor))
                .hasMessageContaining("PRODUCTION_RETURN_ALREADY_APPROVED");
    }

    @Test
    void staleProductionReturnCannotAuthorizeCompletion() {
        PlannedShutdownProductionReturn signoff = new PlannedShutdownProductionReturn();
        signoff.setScopeVersion(2L);
        signoff.setWindowVersion(4L);
        when(productionReturnRepository.findByPlannedShutdownIdAndIsDeletedFalse(shutdownId))
                .thenReturn(Optional.of(signoff));

        assertThatThrownBy(() -> service.productionReturn(shutdownId, 3L, 4L))
                .hasMessageContaining("PRODUCTION_RETURN_STALE");
    }

    @Test
    void definitionsAreMutableOnlyDuringTestingAndKeysAreUnique() {
        var request = new PlannedShutdownStartupTestRequest(7L, "PRESSURE", "Pressure stability", true,
                "40 <= bar <= 45", "bar", 10);
        when(testRepository.existsByPlannedShutdownIdAndTestKeyAndIsDeletedFalse(shutdownId, "PRESSURE"))
                .thenReturn(false);

        var created = service.createTest(shutdownId, PlannedShutdownStatus.TESTING, request);

        assertThat(created.testKey()).isEqualTo("PRESSURE");
        assertThatThrownBy(() -> service.createTest(shutdownId, PlannedShutdownStatus.STARTUP, request))
                .hasMessageContaining("STARTUP_TEST_MUTATION_NOT_ALLOWED");
        when(testRepository.existsByPlannedShutdownIdAndTestKeyAndIsDeletedFalse(shutdownId, "PRESSURE"))
                .thenReturn(true);
        assertThatThrownBy(() -> service.createTest(shutdownId, PlannedShutdownStatus.TESTING, request))
                .hasMessageContaining("STARTUP_TEST_KEY_EXISTS");
    }

    @Test
    void resultRequiresAnExplicitPassFailDecision() {
        var request = new PlannedShutdownStartupTestResultRequest(7L, "42", "bar", null,
                "evidence", UUID.randomUUID());
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var violations = factory.getValidator().validate(request);
            assertThat(violations).extracting(violation -> violation.getPropertyPath().toString())
                    .contains("passed");
        }
    }

    private PlannedShutdownStartupTest startupTest(boolean mandatory, PlannedShutdownItemStatus status) {
        PlannedShutdownStartupTest test = new PlannedShutdownStartupTest();
        test.setPlannedShutdownId(shutdownId);
        test.setTestKey("PRESSURE");
        test.setTitle("Pressure stability");
        test.setMandatory(mandatory);
        test.setAcceptanceCriteria("40 <= bar <= 45");
        test.setStatus(status);
        return test;
    }
}
