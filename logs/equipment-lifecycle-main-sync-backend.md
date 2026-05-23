# Equipment Lifecycle Main Sync - Backend

Date: 2026-05-23
Refresh check: 2026-05-23 after a second `git fetch --all --prune`.

## Branches

- Starting branch: `cadex-toir-p1-12`
- Target branch: `codex/equipment-lifecycle-main-sync-backend`
- Updated base branch: `main` at `fc3a328`
- Preserved feature branch: `cadex-toir-p1-12` at `abe4ea0`
- Sync merge commit: `f9a588a`
- Report commit before refresh: `104eb98`

## Commands Executed

```bash
git status --short --branch
git fetch --all --prune
git branch --list main codex/equipment-lifecycle-main-sync-backend
git branch -r --list origin/main origin/cadex-toir-p1-12 origin/codex/equipment-lifecycle-main-sync-backend
git checkout main
git pull --ff-only origin main
git rev-list --left-right --count main...cadex-toir-p1-12
git diff --stat main..cadex-toir-p1-12
git checkout -b codex/equipment-lifecycle-main-sync-backend
git merge --no-ff cadex-toir-p1-12
mvn test
command -v mvn || true
command -v ./mvnw || true
command -v java || true
java -version
git fetch --all --prune
git checkout main
git pull --ff-only origin main
git checkout codex/equipment-lifecycle-main-sync-backend
git merge --no-ff main
mvn test
git diff --name-status main..HEAD
git status --short --branch
```

## Conflicts Found

None. The merge from `cadex-toir-p1-12` into the new branch completed with the `ort` strategy. The later refresh merge from updated `main` was a no-op because backend `main` was already current at `fc3a328`.

## Files Changed

Compared with updated `main`, the sync branch contains the preserved feature branch changes plus this report:

```text
A logs/equipment-lifecycle-main-sync-backend.md
A logs/p1-12-role-aware-routing-notification-sla-audit.md
A logs/p1-12-role-aware-routing-notification-sla-fix.md
A logs/s1-main-sync-audit-backend.md
A logs/s1-main-sync-result-backend.md
M pom.xml
M src/main/java/com/toir/controller/NotificationController.java
M src/main/java/com/toir/repository/NotificationRepository.java
M src/main/java/com/toir/service/ActualCostService.java
M src/main/java/com/toir/service/ApprovalService.java
M src/main/java/com/toir/service/DashboardService.java
M src/main/java/com/toir/service/InspectionService.java
M src/main/java/com/toir/service/NotificationService.java
M src/main/java/com/toir/service/repair/RepairRequestService.java
M src/test/java/com/toir/controller/NotificationControllerContractTest.java
M src/test/java/com/toir/security/ActualCostPbacScopeTest.java
M src/test/java/com/toir/security/ApprovalPbacScopeTest.java
M src/test/java/com/toir/service/ActualCostServiceTest.java
M src/test/java/com/toir/service/InspectionServiceTest.java
M src/test/java/com/toir/service/NotificationServiceTest.java
M src/test/java/com/toir/service/repair/RepairRequestServiceTest.java
```

## Conflict Resolution Summary

No conflict resolution was required. No business logic was changed beyond preserving the existing feature branch content in the new sync branch.

## Tests / Build Commands Executed

```bash
mvn test
```

## Test / Build Result

`mvn test` could not be executed because Maven is not installed in this environment:

```text
zsh:1: command not found: mvn
```

Java is available:

```text
/usr/bin/java
openjdk version "24.0.1" 2025-04-15
```

There is no `mvnw` Maven wrapper in the backend repository.

## Could Not Be Verified

- Backend compile and tests could not be verified locally because neither `mvn` nor `mvnw` is available.

## Safe For Next Audit Step

Partially. The git sync itself is clean and conflict-free, but backend audit should wait until `mvn test` or the project-approved backend verification command runs successfully in an environment with Maven.
