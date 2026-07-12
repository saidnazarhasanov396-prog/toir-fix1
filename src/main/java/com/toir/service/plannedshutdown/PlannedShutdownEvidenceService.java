package com.toir.service.plannedshutdown;

import com.toir.dto.plannedshutdown.*;
import com.toir.entity.plannedshutdown.PlannedShutdownProductionReturn;
import com.toir.entity.plannedshutdown.PlannedShutdownStartupTest;
import com.toir.enums.PlannedShutdownItemStatus;
import com.toir.enums.PlannedShutdownStatus;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.exception.RestException;
import com.toir.repository.plannedshutdown.PlannedShutdownProductionReturnRepository;
import com.toir.repository.plannedshutdown.PlannedShutdownStartupTestRepository;
import com.toir.util.AuditBuilderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.Comparator;

@Service
@RequiredArgsConstructor
public class PlannedShutdownEvidenceService {
    private final PlannedShutdownStartupTestRepository testRepository;
    private final PlannedShutdownProductionReturnRepository productionReturnRepository;
    private final AuditBuilderService auditBuilderService;

    @Transactional(readOnly = true)
    public List<PlannedShutdownStartupTestResponse> tests(UUID shutdownId) {
        return currentTests(shutdownId).stream().map(PlannedShutdownStartupTestResponse::from).toList();
    }

    @Transactional
    public PlannedShutdownStartupTestResponse createTest(UUID shutdownId, PlannedShutdownStatus status,
            PlannedShutdownStartupTestRequest request) {
        requireTesting(status);
        String key = request.testKey().trim().toUpperCase(Locale.ROOT);
        if (testRepository.existsByPlannedShutdownIdAndTestKeyAndIsDeletedFalse(shutdownId, key)) {
            throw RestException.conflict("STARTUP_TEST_KEY_EXISTS:" + key);
        }
        PlannedShutdownStartupTest test = new PlannedShutdownStartupTest();
        test.setPlannedShutdownId(shutdownId);
        test.setTestKey(key);
        test.setTitle(request.title().trim());
        test.setMandatory(request.mandatory());
        test.setAcceptanceCriteria(request.acceptanceCriteria().trim());
        test.setUnit(normalize(request.unit()));
        test.setOrderNumber(request.orderNumber());
        test.setStatus(PlannedShutdownItemStatus.PENDING);
        return PlannedShutdownStartupTestResponse.from(testRepository.saveAndFlush(test));
    }

    @Transactional
    public PlannedShutdownStartupTestResponse recordResult(UUID shutdownId, UUID testId,
            PlannedShutdownStatus status, PlannedShutdownStartupTestResultRequest request, UUID verifier) {
        requireTesting(status);
        if (verifier.equals(request.performerId())) {
            throw RestException.conflict("STARTUP_TEST_SEPARATION_OF_DUTY_REQUIRED");
        }
        PlannedShutdownStartupTest test = testRepository
                .findByIdAndPlannedShutdownIdAndIsDeletedFalse(testId, shutdownId)
                .orElseThrow(() -> RestException.notFound("Startup test not found"));
        test.setMeasuredValue(decimal(request.measuredValue()));
        test.setResultUnit(request.unit().trim());
        test.setStatus(Boolean.TRUE.equals(request.passed())
                ? PlannedShutdownItemStatus.PASSED : PlannedShutdownItemStatus.FAILED);
        test.setEvidence(request.evidence().trim());
        test.setPerformerId(request.performerId());
        test.setVerifierId(verifier);
        test.setVerifiedAt(Instant.now());
        return PlannedShutdownStartupTestResponse.from(testRepository.saveAndFlush(test));
    }

    @Transactional(readOnly = true)
    public void requireStartupReady(UUID shutdownId) {
        List<PlannedShutdownBlocker> blockers = startupBlockers(shutdownId);
        if (!blockers.isEmpty()) {
            throw RestException.conflict(blockers.getFirst().code());
        }
    }

    @Transactional(readOnly = true)
    public List<PlannedShutdownBlocker> startupBlockers(UUID shutdownId) {
        List<PlannedShutdownStartupTest> tests = currentTests(shutdownId);
        if (tests.stream().noneMatch(PlannedShutdownStartupTest::isMandatory)) {
            return List.of(new PlannedShutdownBlocker("STARTUP_TESTS_MISSING",
                    "At least one mandatory startup test is required", "PLANNED_SHUTDOWN", shutdownId));
        }
        return tests.stream().filter(PlannedShutdownStartupTest::isMandatory)
                .filter(test -> test.getStatus() != PlannedShutdownItemStatus.PASSED)
                .map(test -> test.getStatus() == PlannedShutdownItemStatus.FAILED
                        ? new PlannedShutdownBlocker("STARTUP_TEST_FAILED", "Mandatory startup test failed",
                                "STARTUP_TEST", test.getId())
                        : new PlannedShutdownBlocker("STARTUP_TEST_INCOMPLETE",
                                "Mandatory startup test is incomplete", "STARTUP_TEST", test.getId()))
                .sorted(Comparator.comparing(PlannedShutdownBlocker::code)
                        .thenComparing(blocker -> blocker.entityId() == null ? "" : blocker.entityId().toString()))
                .toList();
    }

    @Transactional
    public PlannedShutdownProductionReturnResponse approveProductionReturn(UUID shutdownId,
            PlannedShutdownStatus status, Long scopeVersion, Long windowVersion,
            PlannedShutdownProductionReturnRequest request, UUID actor) {
        if (status != PlannedShutdownStatus.STARTUP) {
            throw RestException.conflict("PRODUCTION_RETURN_NOT_ALLOWED:" + status);
        }
        var active = productionReturnRepository.findByPlannedShutdownIdAndIsDeletedFalse(shutdownId);
        if (active.isPresent()) {
            PlannedShutdownProductionReturn previous = active.get();
            if (java.util.Objects.equals(previous.getScopeVersion(), scopeVersion)
                    && java.util.Objects.equals(previous.getWindowVersion(), windowVersion)) {
                throw RestException.conflict("PRODUCTION_RETURN_ALREADY_APPROVED");
            }
            ProductionReturnAuditSnapshot before = ProductionReturnAuditSnapshot.from(previous);
            previous.setDeleted(true);
            productionReturnRepository.saveAndFlush(previous);
            String evidenceId = previous.getId() == null ? shutdownId.toString() : previous.getId().toString();
            auditBuilderService.log("planned_shutdown_production_return", evidenceId, AuditAction.UPDATE,
                    AuditModule.PLANNED_SHUTDOWN, "Retired stale production return sign-off", before,
                    ProductionReturnAuditSnapshot.from(previous));
        }
        PlannedShutdownProductionReturn signoff = new PlannedShutdownProductionReturn();
        signoff.setPlannedShutdownId(shutdownId);
        signoff.setScopeVersion(scopeVersion);
        signoff.setWindowVersion(windowVersion);
        signoff.setApprovedById(actor);
        signoff.setApprovedAt(Instant.now());
        signoff.setEvidence(request.evidence().trim());
        return PlannedShutdownProductionReturnResponse.from(productionReturnRepository.saveAndFlush(signoff));
    }

    @Transactional(readOnly = true)
    public PlannedShutdownProductionReturnResponse productionReturn(UUID shutdownId,
            Long scopeVersion, Long windowVersion) {
        List<PlannedShutdownBlocker> blockers = productionReturnBlockers(shutdownId, scopeVersion, windowVersion);
        if (!blockers.isEmpty()) {
            throw RestException.conflict(blockers.getFirst().code());
        }
        PlannedShutdownProductionReturn signoff = productionReturnRepository
                .findByPlannedShutdownIdAndIsDeletedFalse(shutdownId)
                .orElseThrow();
        return PlannedShutdownProductionReturnResponse.from(signoff);
    }

    @Transactional(readOnly = true)
    public List<PlannedShutdownBlocker> productionReturnBlockers(UUID shutdownId,
            Long scopeVersion, Long windowVersion) {
        var active = productionReturnRepository.findByPlannedShutdownIdAndIsDeletedFalse(shutdownId);
        if (active.isEmpty()) {
            return List.of(new PlannedShutdownBlocker("PRODUCTION_RETURN_MISSING",
                    "Production return sign-off is missing", "PLANNED_SHUTDOWN", shutdownId));
        }
        PlannedShutdownProductionReturn signoff = active.get();
        if (!java.util.Objects.equals(signoff.getScopeVersion(), scopeVersion)
                || !java.util.Objects.equals(signoff.getWindowVersion(), windowVersion)) {
            return List.of(new PlannedShutdownBlocker("PRODUCTION_RETURN_STALE",
                    "Production return sign-off does not match current scope and window",
                    "PRODUCTION_RETURN", signoff.getId()));
        }
        return List.of();
    }

    private List<PlannedShutdownStartupTest> currentTests(UUID id) {
        return testRepository.findAllByPlannedShutdownIdAndIsDeletedFalseOrderByOrderNumberAsc(id);
    }

    private static void requireTesting(PlannedShutdownStatus status) {
        if (status != PlannedShutdownStatus.TESTING) {
            throw RestException.conflict("STARTUP_TEST_MUTATION_NOT_ALLOWED:" + status);
        }
    }

    private static BigDecimal decimal(String value) {
        try { return new BigDecimal(value.trim()).setScale(4, RoundingMode.HALF_UP); }
        catch (NumberFormatException ex) { throw RestException.badRequest("INVALID_MEASURED_VALUE"); }
    }

    private static String normalize(String value) { return value == null ? null : value.trim(); }

    private record ProductionReturnAuditSnapshot(UUID id, UUID shutdownId, Long scopeVersion,
            Long windowVersion, UUID approvedById, Instant approvedAt, String evidence, boolean deleted) {
        static ProductionReturnAuditSnapshot from(PlannedShutdownProductionReturn signoff) {
            return new ProductionReturnAuditSnapshot(signoff.getId(), signoff.getPlannedShutdownId(),
                    signoff.getScopeVersion(), signoff.getWindowVersion(), signoff.getApprovedById(),
                    signoff.getApprovedAt(), signoff.getEvidence(), signoff.isDeleted());
        }
    }
}
