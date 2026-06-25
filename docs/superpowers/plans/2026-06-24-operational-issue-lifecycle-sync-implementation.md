# Operational Issue Lifecycle Sync Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Resolve Problems page operational issues immediately when their source defect or repair request reaches a terminal lifecycle state.

**Architecture:** Add a small lifecycle mirroring service that delegates to `OperationalIssueService.resolveOpen(...)` and never mutates source statuses. Wire it into existing successful lifecycle points in `DefectService`, `WorkOrderService`, and `RepairRequestService`, keeping the scanner as fallback.

**Tech Stack:** Java 21, Spring Boot services, Spring transactions, Mockito/JUnit 5, Maven.

---

## File Structure

- Create: `src/main/java/com/toir/service/OperationalIssueLifecycleSyncService.java`
  - Owns operational issue mirroring for terminal defects and repair request final sweep.
- Create: `src/test/java/com/toir/service/OperationalIssueLifecycleSyncServiceTest.java`
  - Verifies service behavior directly and idempotent no-op behavior.
- Modify: `src/main/java/com/toir/service/defects/DefectService.java`
  - Calls lifecycle sync after direct defect resolve saves `RESOLVED`.
- Modify: `src/test/java/com/toir/service/defects/DefectServiceTest.java`
  - Adds mock dependency and verifies direct resolve sync.
- Modify: `src/main/java/com/toir/service/WorkOrderService.java`
  - Calls lifecycle sync after linked defect resolve/close and after linked repair request completion.
- Modify: `src/test/java/com/toir/service/WorkOrderServiceTest.java`
  - Adds mock dependency and verifies sync hooks for complete/close/final sweep.
- Modify: `src/main/java/com/toir/service/repair/RepairRequestService.java`
  - Calls lifecycle sync after repair request close saves `CLOSED`.
- Modify: `src/test/java/com/toir/service/repair/RepairRequestServiceTest.java`
  - Adds mock dependency and verifies close/admin override final sweep.

## Task 1: Lifecycle Sync Service

**Files:**
- Create: `src/test/java/com/toir/service/OperationalIssueLifecycleSyncServiceTest.java`
- Create: `src/main/java/com/toir/service/OperationalIssueLifecycleSyncService.java`

- [ ] **Step 1: Write failing service tests**

Create `src/test/java/com/toir/service/OperationalIssueLifecycleSyncServiceTest.java` with these tests:

```java
package com.toir.service;

import com.toir.entity.defects.Defect;
import com.toir.enums.DefectStatus;
import com.toir.repository.defects.DefectRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OperationalIssueLifecycleSyncServiceTest {

    @Mock
    OperationalIssueService operationalIssueService;

    @Mock
    DefectRepository defectRepository;

    @InjectMocks
    OperationalIssueLifecycleSyncService service;

    @Test
    void resolveDefectIssueIfTerminalResolvesResolvedDefectIssue() {
        UUID defectId = UUID.randomUUID();
        Defect defect = defect(defectId, DefectStatus.RESOLVED);

        service.resolveDefectIssueIfTerminal(defect, "Defect resolved.");

        verify(operationalIssueService).resolveOpen("Defect", defectId, "Defect resolved.");
    }

    @Test
    void resolveDefectIssueIfTerminalSkipsOpenDefectIssue() {
        UUID defectId = UUID.randomUUID();
        Defect defect = defect(defectId, DefectStatus.OPEN);

        service.resolveDefectIssueIfTerminal(defect, "Defect resolved.");

        verify(operationalIssueService, never()).resolveOpen("Defect", defectId, "Defect resolved.");
    }

    @Test
    void sweepRepairRequestResolvesOnlyTerminalLinkedDefectsAndRepairRequestIssue() {
        UUID repairRequestId = UUID.randomUUID();
        UUID resolvedDefectId = UUID.randomUUID();
        UUID closedDefectId = UUID.randomUUID();
        UUID openDefectId = UUID.randomUUID();
        Defect resolved = defect(resolvedDefectId, DefectStatus.RESOLVED);
        Defect closed = defect(closedDefectId, DefectStatus.CLOSED);
        Defect open = defect(openDefectId, DefectStatus.OPEN);
        when(defectRepository.findAllByRepairRequestIdAndIsDeletedFalseOrderByUpdatedAtDesc(repairRequestId))
                .thenReturn(List.of(resolved, closed, open));

        service.sweepRepairRequest(repairRequestId, "Repair request completed.");

        verify(operationalIssueService).resolveOpen("Defect", resolvedDefectId, "Repair request completed.");
        verify(operationalIssueService).resolveOpen("Defect", closedDefectId, "Repair request completed.");
        verify(operationalIssueService, never()).resolveOpen("Defect", openDefectId, "Repair request completed.");
        verify(operationalIssueService).resolveOpen("RepairRequest", repairRequestId, "Repair request completed.");
    }

    @Test
    void sweepRepairRequestSkipsNullRepairRequestId() {
        service.sweepRepairRequest(null, "Repair request completed.");

        verify(defectRepository, never()).findAllByRepairRequestIdAndIsDeletedFalseOrderByUpdatedAtDesc(null);
        verify(operationalIssueService, never()).resolveOpen("RepairRequest", null, "Repair request completed.");
    }

    private static Defect defect(UUID id, DefectStatus status) {
        Defect defect = new Defect();
        defect.setId(id);
        defect.setStatus(status);
        return defect;
    }
}
```

- [ ] **Step 2: Run service test and verify RED**

Run:

```bash
./mvnw -Dtest=OperationalIssueLifecycleSyncServiceTest test
```

Expected: FAIL because `OperationalIssueLifecycleSyncService` does not exist.

- [ ] **Step 3: Implement minimal lifecycle sync service**

Create `src/main/java/com/toir/service/OperationalIssueLifecycleSyncService.java`:

```java
package com.toir.service;

import com.toir.entity.defects.Defect;
import com.toir.enums.DefectStatus;
import com.toir.repository.defects.DefectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OperationalIssueLifecycleSyncService {

    private static final Set<DefectStatus> TERMINAL_DEFECT_STATUSES = EnumSet.of(
            DefectStatus.RESOLVED,
            DefectStatus.CLOSED,
            DefectStatus.CANCELLED
    );

    private final OperationalIssueService operationalIssueService;
    private final DefectRepository defectRepository;

    @Transactional
    public void resolveDefectIssueIfTerminal(Defect defect, String resolutionMessage) {
        if (defect == null || defect.getId() == null || !TERMINAL_DEFECT_STATUSES.contains(defect.getStatus())) {
            return;
        }
        operationalIssueService.resolveOpen("Defect", defect.getId(), resolutionMessage);
    }

    @Transactional
    public void resolveRepairRequestIssue(UUID repairRequestId, String resolutionMessage) {
        if (repairRequestId == null) {
            return;
        }
        operationalIssueService.resolveOpen("RepairRequest", repairRequestId, resolutionMessage);
    }

    @Transactional
    public void sweepRepairRequest(UUID repairRequestId, String resolutionMessage) {
        if (repairRequestId == null) {
            return;
        }
        defectRepository.findAllByRepairRequestIdAndIsDeletedFalseOrderByUpdatedAtDesc(repairRequestId)
                .forEach(defect -> resolveDefectIssueIfTerminal(defect, resolutionMessage));
        resolveRepairRequestIssue(repairRequestId, resolutionMessage);
    }
}
```

- [ ] **Step 4: Run service test and verify GREEN**

Run:

```bash
./mvnw -Dtest=OperationalIssueLifecycleSyncServiceTest test
```

Expected: PASS.

## Task 2: Defect Direct Resolve Hook

**Files:**
- Modify: `src/test/java/com/toir/service/defects/DefectServiceTest.java`
- Modify: `src/main/java/com/toir/service/defects/DefectService.java`

- [ ] **Step 1: Write failing direct resolve test**

In `DefectServiceTest`, import `com.toir.service.OperationalIssueLifecycleSyncService`, add this mock near other service mocks:

```java
@Mock
OperationalIssueLifecycleSyncService operationalIssueLifecycleSyncService;
```

Add this test near the existing defect resolve tests:

```java
@Test
void resolveSyncsOperationalIssueAfterSavingResolvedDefect() {
    UUID defectId = UUID.randomUUID();
    UUID equipmentId = UUID.randomUUID();
    Defect defect = defect(defectId, equipmentId);
    defect.setStatus(DefectStatus.OPEN);
    when(repository.findByIdAndIsDeletedFalse(defectId)).thenReturn(Optional.of(defect));
    when(repository.save(any(Defect.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(equipmentRepository.findAllByIdInAndIsDeletedFalse(List.of(equipmentId))).thenReturn(List.of());
    when(repairRequestRepository.findAllByIdInAndIsDeletedFalse(List.of())).thenReturn(List.of());
    when(workOrderRepository.findAllByDefectIdInAndIsDeletedFalseOrderByUpdatedAtDesc(any()))
            .thenReturn(List.of());
    when(knowledgeRepository.findDefectIdsWithLesson(any(), eq("LESSON_LEARNED")))
            .thenReturn(List.of());

    service.resolve(defectId);

    verify(operationalIssueLifecycleSyncService)
            .resolveDefectIssueIfTerminal(defect, "Defect resolved directly.");
}
```

- [ ] **Step 2: Run direct resolve test and verify RED**

Run:

```bash
./mvnw -Dtest=DefectServiceTest#resolveSyncsOperationalIssueAfterSavingResolvedDefect test
```

Expected: FAIL because `DefectService.resolve(...)` does not call the lifecycle sync service.

- [ ] **Step 3: Wire lifecycle sync into `DefectService`**

Add import:

```java
import com.toir.service.OperationalIssueLifecycleSyncService;
```

Add final dependency:

```java
private final OperationalIssueLifecycleSyncService operationalIssueLifecycleSyncService;
```

After `Defect saved = repository.save(entity);`, call:

```java
operationalIssueLifecycleSyncService.resolveDefectIssueIfTerminal(saved, "Defect resolved directly.");
```

- [ ] **Step 4: Run direct resolve test and verify GREEN**

Run:

```bash
./mvnw -Dtest=DefectServiceTest#resolveSyncsOperationalIssueAfterSavingResolvedDefect test
```

Expected: PASS.

## Task 3: Work Order Lifecycle Hooks

**Files:**
- Modify: `src/test/java/com/toir/service/WorkOrderServiceTest.java`
- Modify: `src/main/java/com/toir/service/WorkOrderService.java`

- [ ] **Step 1: Write failing work order tests**

In `WorkOrderServiceTest`, add this mock near other service mocks:

```java
@Mock
OperationalIssueLifecycleSyncService operationalIssueLifecycleSyncService;
```

In `completeResolvesDefectWhenNoActiveLinkedWorkOrdersRemain`, add:

```java
verify(operationalIssueLifecycleSyncService)
        .resolveDefectIssueIfTerminal(defect, "Defect resolved from linked work order completion.");
```

In `completeDoesNotResolveDefectWhenAnotherActiveLinkedWorkOrderExists`, add:

```java
verify(operationalIssueLifecycleSyncService, never())
        .resolveDefectIssueIfTerminal(any(Defect.class), any());
```

In `completeMovesRepairRequestToCompletedWhenAllWorkOrdersDoneAndDefectsResolved`, add:

```java
verify(operationalIssueLifecycleSyncService)
        .sweepRepairRequest(repairRequestId,
                "Repair request completed after linked work orders and defects reached terminal state.");
```

In `closeClosesDefectWhenAllLinkedWorkOrdersTerminalAndDefectResolved`, add:

```java
verify(operationalIssueLifecycleSyncService)
        .resolveDefectIssueIfTerminal(defect,
                "Defect closed after linked work orders reached terminal state.");
```

- [ ] **Step 2: Run work order tests and verify RED**

Run:

```bash
./mvnw -Dtest=WorkOrderServiceTest#completeResolvesDefectWhenNoActiveLinkedWorkOrdersRemain,WorkOrderServiceTest#completeDoesNotResolveDefectWhenAnotherActiveLinkedWorkOrderExists,WorkOrderServiceTest#completeMovesRepairRequestToCompletedWhenAllWorkOrdersDoneAndDefectsResolved,WorkOrderServiceTest#closeClosesDefectWhenAllLinkedWorkOrdersTerminalAndDefectResolved test
```

Expected: FAIL because `WorkOrderService` does not call lifecycle sync hooks.

- [ ] **Step 3: Wire lifecycle sync into `WorkOrderService`**

Add final dependency:

```java
private final OperationalIssueLifecycleSyncService operationalIssueLifecycleSyncService;
```

In `syncDefectOnComplete`, after `Defect saved = defectRepository.save(defect);`, call:

```java
operationalIssueLifecycleSyncService.resolveDefectIssueIfTerminal(
        saved,
        "Defect resolved from linked work order completion.");
```

In `syncRepairRequestOnComplete`, after `RepairRequest saved = repairRequestRepository.save(request);`, call:

```java
operationalIssueLifecycleSyncService.sweepRepairRequest(
        saved.getId(),
        "Repair request completed after linked work orders and defects reached terminal state.");
```

In `syncDefectOnClose`, after `Defect saved = defectRepository.save(defect);`, call:

```java
operationalIssueLifecycleSyncService.resolveDefectIssueIfTerminal(
        saved,
        "Defect closed after linked work orders reached terminal state.");
```

- [ ] **Step 4: Run work order tests and verify GREEN**

Run:

```bash
./mvnw -Dtest=WorkOrderServiceTest#completeResolvesDefectWhenNoActiveLinkedWorkOrdersRemain,WorkOrderServiceTest#completeDoesNotResolveDefectWhenAnotherActiveLinkedWorkOrderExists,WorkOrderServiceTest#completeMovesRepairRequestToCompletedWhenAllWorkOrdersDoneAndDefectsResolved,WorkOrderServiceTest#closeClosesDefectWhenAllLinkedWorkOrdersTerminalAndDefectResolved test
```

Expected: PASS.

## Task 4: Repair Request Close Final Sweep

**Files:**
- Modify: `src/test/java/com/toir/service/repair/RepairRequestServiceTest.java`
- Modify: `src/main/java/com/toir/service/repair/RepairRequestService.java`

- [ ] **Step 1: Write failing repair request close tests**

In `RepairRequestServiceTest`, import `com.toir.service.OperationalIssueLifecycleSyncService`, add this mock near other service mocks:

```java
@Mock
OperationalIssueLifecycleSyncService operationalIssueLifecycleSyncService;
```

In `closeAllowsTerminalWorkOrdersAndResolvedDefects`, add:

```java
verify(operationalIssueLifecycleSyncService)
        .sweepRepairRequest(id, "Repair request closed.");
```

Add this admin override test near other close tests:

```java
@Test
void closeWithAdminOverrideRunsFinalSweepWithoutResolvingOpenDefectStatuses() {
    UUID id = UUID.randomUUID();
    RepairRequest entity = repairRequest(id);
    entity.setStatus(RequestStatus.OPEN);
    Defect openDefect = defect(id);
    openDefect.setStatus(DefectStatus.OPEN);
    when(scopeAccessService.isScopeAdmin()).thenReturn(true);
    when(repository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(entity));
    when(repository.save(any(RepairRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(defectRepository.findAllByRepairRequestIdAndIsDeletedFalseOrderByUpdatedAtDesc(id))
            .thenReturn(List.of(openDefect));
    stubNameLookups(entity);

    service.close(id, new CloseRequestRequest("Admin accepted risk"));

    assertThat(openDefect.getStatus()).isEqualTo(DefectStatus.OPEN);
    verify(operationalIssueLifecycleSyncService)
            .sweepRepairRequest(id, "Repair request closed.");
}
```

- [ ] **Step 2: Run repair request tests and verify RED**

Run:

```bash
./mvnw -Dtest=RepairRequestServiceTest#closeAllowsTerminalWorkOrdersAndResolvedDefects,RepairRequestServiceTest#closeWithAdminOverrideRunsFinalSweepWithoutResolvingOpenDefectStatuses test
```

Expected: FAIL because `RepairRequestService.close(...)` does not call the lifecycle sync service.

- [ ] **Step 3: Wire lifecycle sync into `RepairRequestService`**

Add import:

```java
import com.toir.service.OperationalIssueLifecycleSyncService;
```

Add final dependency:

```java
private final OperationalIssueLifecycleSyncService operationalIssueLifecycleSyncService;
```

After `RepairRequest save = repository.save(entity);`, call:

```java
operationalIssueLifecycleSyncService.sweepRepairRequest(save.getId(), "Repair request closed.");
```

- [ ] **Step 4: Run repair request tests and verify GREEN**

Run:

```bash
./mvnw -Dtest=RepairRequestServiceTest#closeAllowsTerminalWorkOrdersAndResolvedDefects,RepairRequestServiceTest#closeWithAdminOverrideRunsFinalSweepWithoutResolvingOpenDefectStatuses test
```

Expected: PASS.

## Task 5: Regression Verification and Commit

**Files:**
- Verify all changed Java files and plan/spec docs.

- [ ] **Step 1: Run focused lifecycle regression suite**

Run:

```bash
./mvnw -Dtest=OperationalIssueLifecycleSyncServiceTest,DefectServiceTest#resolveSyncsOperationalIssueAfterSavingResolvedDefect,WorkOrderServiceTest#completeResolvesDefectWhenNoActiveLinkedWorkOrdersRemain,WorkOrderServiceTest#completeDoesNotResolveDefectWhenAnotherActiveLinkedWorkOrderExists,WorkOrderServiceTest#completeMovesRepairRequestToCompletedWhenAllWorkOrdersDoneAndDefectsResolved,WorkOrderServiceTest#closeClosesDefectWhenAllLinkedWorkOrdersTerminalAndDefectResolved,RepairRequestServiceTest#closeAllowsTerminalWorkOrdersAndResolvedDefects,RepairRequestServiceTest#closeWithAdminOverrideRunsFinalSweepWithoutResolvingOpenDefectStatuses test
```

Expected: PASS.

- [ ] **Step 2: Run existing affected service tests**

Run:

```bash
./mvnw -Dtest=OperationalIssueLifecycleSyncServiceTest,DefectServiceTest,WorkOrderServiceTest,RepairRequestServiceTest test
```

Expected: PASS.

- [ ] **Step 3: Run diff hygiene check**

Run:

```bash
git diff --check
```

Expected: no output and exit code 0.

- [ ] **Step 4: Commit implementation**

Run:

```bash
git status --short
git add -f docs/superpowers/plans/2026-06-24-operational-issue-lifecycle-sync-implementation.md
git add src/main/java/com/toir/service/OperationalIssueLifecycleSyncService.java \
        src/test/java/com/toir/service/OperationalIssueLifecycleSyncServiceTest.java \
        src/main/java/com/toir/service/defects/DefectService.java \
        src/test/java/com/toir/service/defects/DefectServiceTest.java \
        src/main/java/com/toir/service/WorkOrderService.java \
        src/test/java/com/toir/service/WorkOrderServiceTest.java \
        src/main/java/com/toir/service/repair/RepairRequestService.java \
        src/test/java/com/toir/service/repair/RepairRequestServiceTest.java
git commit -m "feat: sync operational issues with repair lifecycle"
```

Expected: commit created on `Codex_org`.

- [ ] **Step 5: Push branch**

Run:

```bash
git push origin Codex_org
```

Expected: push succeeds and remote `Codex_org` includes the design doc commit plus implementation commit.

## Self-Review

- Spec coverage: terminal defect sync, repair request final sweep, admin override behavior, and scanner fallback preservation are covered by Tasks 1-4.
- Placeholder scan: no `TBD`, `TODO`, or unspecified implementation steps remain.
- Type consistency: all new hooks use `OperationalIssueLifecycleSyncService`; source types remain `"Defect"` and `"RepairRequest"` to match existing `OperationalIssueService.resolveOpen(...)` contracts.
