# TOiR Stage 2 Planned Shutdown Verification

Date: 2026-07-12

Backend evidence revision: `4c90fd2fb913dc0e62c1899ee7cd9b33a8a7f6cd`

Frontend evidence revision: `71c6b4e3a7aef2e62ca42acca715320f95e4b5c0`

## Outcome

The Stage 2 Planned Shutdown implementation passes its isolated backend, database, frontend,
type, locale, and production-build gates. This does not mean that every item in the broader
original PS-01..PS-14 audit is complete. The implementation closes the Stage 2 plan, while the
audit still has explicit partial rows for source-family breadth, readiness percentage/category
coverage, the full ten-role approval route, detailed safety evidence, two start guards, derived
risk, automatic overrun escalation, and extended closure facts.

Audit disposition:

- Complete against the audited behavior: PS-01, PS-02, PS-03, PS-08, PS-10, PS-13.
- Partially complete with exact limitations below: PS-04, PS-05, PS-06, PS-07, PS-09, PS-11,
  PS-12, PS-14.

## Isolated provenance

All executable evidence was gathered outside the active checkouts:

- Backend detached worktree: `/private/tmp/toir-stage2-task10-backend` at `4c90fd2f`.
- Frontend detached worktree: `/private/tmp/toir-stage2-task10-frontend` at `71c6b4e3`.
- PostgreSQL data directory: `/private/tmp/toir-stage2-task10-pgdata`, PostgreSQL 17.6,
  bound only to `127.0.0.1:55442`, then stopped with fast shutdown.

This prevented Maven `target/`, Yarn install state, TypeScript build metadata, and Vite
`dist/` from being shared with concurrent work.

## Verification commands and results

### Backend

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 24)
./mvnw -DskipTests compile
./mvnw -Dtest='PlannedShutdown*Test,RbacPlannedShutdownReadinessSecurityTest,RbacToirBusinessFlowSecurityTest,ApprovalPbacScopeTest,ApprovalServiceTest,ApprovalActionHandlerTest,WorkOrderServiceTest,SafetyChecklistServiceTest' test
```

- Compile: PASS, 1,591 main sources compiled with Java 24 / release 21.
- Tests: PASS, 456/456 across 27 classes; 0 failures, 0 errors, 0 skipped.
- Included 162 `WorkOrderServiceTest` cases, all Planned Shutdown migrations, policies,
  services, controllers, security contracts, startup/closure/report tests, and approval PBAC.
- Existing Maven model warning: duplicate `org.testcontainers:junit-jupiter` declaration.
  It did not fail compilation or tests and is not introduced by Stage 2.

### PostgreSQL 17 / Flyway

The local cluster was initialized with:

```bash
/opt/homebrew/opt/postgresql@17/bin/initdb \
  -D /private/tmp/toir-stage2-task10-pgdata -A trust --no-locale -E UTF8
/opt/homebrew/opt/postgresql@17/bin/pg_ctl \
  -D /private/tmp/toir-stage2-task10-pgdata \
  -l /private/tmp/toir-stage2-task10-postgres.log \
  -o '-p 55442 -h 127.0.0.1' start
/opt/homebrew/opt/postgresql@17/bin/createdb \
  -h 127.0.0.1 -p 55442 -U tenzorsoft toir_stage2_verify
```

Flyway was invoked from the built project classpath using
`Flyway.configure().dataSource(...).locations("classpath:db/migration").load().migrate()`.
The exact result was:

- PostgreSQL 17.6 detected.
- 190 migration resources validated.
- 164 versioned migrations applied to an empty schema.
- Target `20260711.11`, `success=true`.
- Schema-history ranks 157-164 record `20260711.4` through `20260711.11` as successful.
- Live catalog checks found the generation uniqueness constraint, status/window/scope checks,
  absolute closure uniqueness, and `trg_planned_shutdown_closure_snapshot_immutable`.

Safe live probes:

- Two overlapping transactions inserted the same
  `(planned_shutdown_id, idempotency_key)`. The first committed; the second waited and failed
  on `uq_ps_generation_request_shutdown_key`; exactly one row survived.
- Updating a closure snapshot failed with
  `PLANNED_SHUTDOWN_CLOSURE_SNAPSHOT_IMMUTABLE`.
- Inserting a second snapshot for the same shutdown failed on
  `uq_planned_shutdown_closure_snapshots_shutdown`; the original JSON remained unchanged.

The live concurrency probe proves database arbitration, not an end-to-end pair of concurrent
HTTP requests. Transactional service behavior is covered by
`PlannedShutdownWorkOrderGenerationServiceTest.secondCreateFailureDoesNotRecordPartialCommand`
and the deterministic replay/conflict tests.

### Frontend

```bash
yarn install --immutable
yarn test src/modules/repairs/libs/planned-shutdowns/tests \
  src/modules/repairs/components/planned-shutdown-detail \
  src/modules/hr/libs/approvals/tests/approval-integration.test.ts \
  src/app/routes-rbac.test.ts src/app/role-route-visibility.test.ts
yarn eslint <all planned-shutdown modules, components, and pages>
yarn i18n:check
yarn tsc --noEmit
yarn build
```

- Focused Stage 2 tests: PASS, 111/111 across 11 files.
- Planned Shutdown scoped ESLint: PASS.
- Locale parity: PASS.
- TypeScript: PASS.
- Production build: PASS, 2,161 modules transformed. Existing large-chunk warnings remain.

The repository-wide frontend baseline is not green and is not reported as passed:

- Full `yarn test`: 1,388 passed, 8 failed, 1,396 total.
- Three failures are path/legacy-directory assumptions exposed by isolation:
  `deployment-config.test.mjs`, `equipment-i18n-coverage.test.mjs`, and
  `vehicle-i18n-coverage.test.mjs`.
- Five unrelated stale expectations are in dashboard layout, knowledge toolbar, downtime chart
  normalization, commissioning payload naming, and maintenance due-status localization.
- Full `yarn lint`: 674 errors and 48 warnings across the pre-existing repository baseline.
  Linting the Stage 2 diff also reaches 23 pre-existing errors in monolithic `src/lib/api.ts`
  and `src/app/routes.tsx`; the dedicated Planned Shutdown files themselves pass.

No failure in either global baseline references Planned Shutdown code. They remain release-level
quality debt and are not silently reclassified as green.

## PS-01..PS-14 evidence map

| ID | Disposition | Named evidence | Finding / limitation |
|---|---|---|---|
| PS-01 | Complete | `PlannedShutdownCoreMigrationContractTest.migrationCreatesTheCompleteShutdownAggregateWithExplicitForeignKeys`; `PlannedShutdownServiceTest.createValidatesOwnerWindowAndGeneratesUniqueCode`; `PlannedShutdownControllerContractTest.createUsesTypedLifecycleContractWithoutLegacyPlanStatus` | Dedicated aggregate, active unique code, metadata, owner, department, type and risk fields are separate from Work Order and Repair Campaign. |
| PS-02 | Complete | `PlannedShutdownServiceTest.createRejectsDuplicateCodeAndInvalidWindow`; `PlannedShutdownLifecycleServiceTest.lockedForwardTransitionWritesCanonicalHistoryAndServerTimestampOnce`; `PlannedShutdownReportServiceTest.snapshotDeduplicatesCanonicalWorkMaterialCostDefectAndSourceIds` | Instants carry timezone-safe planned/approved/actual milestones; actual completion is the semantic actual end; downtime is server-derived with `Duration`, not accepted as a client total. |
| PS-03 | Complete | `PlannedShutdownServiceTest.replaceScopeKeepsStableRowsAndSoftDeletesRemovedRows`; `replaceScopeRejectsDuplicatesMissingBoundaryAndForeignDepartmentEquipment`; live `uq_planned_shutdown_assets_active_equipment` catalog evidence | STOPPED/RESERVE/RUNNING boundary rows are stable, mutually exclusive per active asset, department-scoped, and versioned. |
| PS-04 | Partial | `PlannedShutdownWorkItemServiceTest.pprSourceMustExistAndMatchScopedEquipment`; `workOrderSourceMustExistAndMatchScopedEquipment`; `duplicateCanonicalSourceAndOrderAreRejectedBeforeDatabaseWrite` | Canonical MANUAL/DEFECT/PPR/WORK_ORDER records exist. Dedicated REPAIR_REQUEST, INSPECTION, DIAGNOSTICS, MODERNIZATION and HSE/PRESCRIPTION source types are absent; Campaign linking remains Stage 3. |
| PS-05 | Partial | `PlannedShutdownReadinessPolicyTest.returnsEveryFailClosedBlockerInDeterministicOrder`; `readyFactsProceedAndWarningsDoNotBlock`; `PlannedShutdownServiceTest.completeAndReopenReadinessCaptureServerActorAndEvidence` | Critical/warning readiness is deterministic and cannot be overwritten by a manual percentage. There is no persisted/calculated readiness percentage, and tools/documents/energy/finance/startup-plan categories are not all first-class adapters. |
| PS-06 | Partial | `ApprovalPbacScopeTest.plannedShutdownRequesterCannotApproveProductionOrHseRoleStep`; `plannedShutdownSameActorCannotApproveProductionAndHseSteps`; `PlannedShutdownLifecycleServiceTest.approvalFinalizationRequiresCurrentProductionAndHseStepsWithSeparationOfDuty` | Current-scope production/HSE approvals, history, timestamps, rejection/comment infrastructure, reapproval and separation of duty are enforced. Stage 2 does not create the original audit's full ten-role shutdown template. |
| PS-07 | Partial | `PlannedShutdownReadinessPolicyTest.safeStateRequiresEveryRequiredIsolationAppliedAndVerified`; `PlannedShutdownServiceTest.isolationApplyVerifyReleaseRequiresOrderedServerStampedActions`; `isolationRejectsUnrelatedNotIssuedFutureAndExpiredPermits` | Isolation points, lock/tag identity, permit, responsible actor and safe-state invariant are enforced. Pressure release, flushing, gas analysis, restricted-area/PPE and photo/file evidence are not separate typed shutdown facts. |
| PS-08 | Complete | `PlannedShutdownTransitionPolicyTest.allowsEveryCanonicalForwardEdge`; `rejectsSkippedBackwardAndTerminalTransitions`; `PlannedShutdownReadinessLifecyclePolicyTest.exposesExactLifecycleCapabilities`; `PlannedShutdownLifecyclePermissionContractTest` | All 16 dedicated statuses have bounded transitions, exact authorities, blockers, locked mutation, status history and audit evidence. |
| PS-09 | Partial | `PlannedShutdownReadinessPolicyTest.returnsEveryFailClosedBlockerInDeterministicOrder`; `PlannedShutdownWorkOrderStartPolicyTest.flaggedWorkWithoutShutdownFailsClosed`; `staleApprovalsIsolationAndPermitBlockersAreComposed`; `criticalMaterialDeficitBlocksAfterCurrentShutdownEvidence` | Backend blocks missing production/HSE approval, isolation/permit, critical material, performer/contractor, invalid window and cancelled/deleted shutdown. It checks canonical work, not explicitly “generated WO count”, and no distinct startup-plan guard exists. |
| PS-10 | Complete | `PlannedShutdownWorkOrderGenerationServiceTest.ordersItemsDeterministicallyAndReplaysCanonicalWorkOrder`; `invalidSelectionIsRejectedBeforeAnyWorkOrderMutation`; `secondCreateFailureDoesNotRecordPartialCommand`; live overlapping-transaction probe | Transactional bulk generation is linked, ordered, replay-safe and database-unique; partial success is not recorded. |
| PS-11 | Partial | `PlannedShutdownCoreMigrationContractTest.dedicatedEnumsAndAggregateRootMappingMatchTheSchema`; create/update DTO validation | `riskLevel` and nonnegative decimal `riskScore` are stored metadata supplied by the client. No derived/configurable risk formula consumes readiness, permit, contractor, start proximity or defects. |
| PS-12 | Partial | `PlannedShutdownLifecycleServiceTest.emergencyExtensionRecordsEventAndReturnsToOperationalStatus`; `startupExtensionRequiresCurrentReapprovalBeforeCompletion`; closure report extension facts | Emergency extension requires a new end and reason, advances window version, is audited and affects derived downtime. There is no scheduled overrun detector, reason taxonomy, notification or escalation specific to Planned Shutdown. |
| PS-13 | Complete | `PlannedShutdownEvidenceServiceTest.mandatoryMissingOrFailedStartupTestsBlockStartup`; `resultCapturesMeasuredEvidenceAndIndependentPerformerVerifier`; `productionReturnIsSingleCanonicalServerActorSignoff` | Mandatory measured tests, explicit pass/fail, performer/verifier separation, current-window production return and startup/completion gates are enforced. |
| PS-14 | Partial | `PlannedShutdownReportServiceTest.snapshotDeduplicatesCanonicalWorkMaterialCostDefectAndSourceIds`; `closureSnapshotIsSingleAndReadIsIdempotent`; `readRejectsStoredJsonWhoseHashDoesNotMatch`; live immutable-trigger probe | Immutable report includes planned/actual downtime, work/status/results, source/material/cost/defect IDs, tests, sign-off and extensions. It does not yet carry planned-vs-actual budget values, explicit remaining/repeated-defect/deviation fields, or required attachment/act identities. |

## Negative-scenario evidence

- Invalid window, duplicate code, missing/inactive/cross-department owner:
  `PlannedShutdownServiceTest.createRejectsDuplicateCodeAndInvalidWindow`,
  `createRejectsMissingAndInactiveResponsibleEmployees`,
  `createRejectsUnknownOrCrossDepartmentResponsibleEmployee`.
- Stale optimistic version and unauthorized department transfer:
  `updateUsesLockedAggregateAndRejectsStaleVersion`,
  `updateCannotTransferShutdownToUnauthorizedTargetDepartment`,
  `departmentPbacRejectsUnrelatedDepartmentReadAndMutation`.
- Duplicate/empty/foreign/immutable scope:
  `replaceScopeRejectsDuplicatesMissingBoundaryAndForeignDepartmentEquipment`,
  `replaceScopeRejectsMissingEquipment`,
  `replaceScopeRejectsImmutableLifecycleStatus`.
- Invalid work source, out-of-scope asset, duplicate source/order, active-WO removal, frozen identity:
  `PlannedShutdownWorkItemServiceTest.pprSourceMustExistAndMatchScopedEquipment`,
  `rejectsEquipmentOutsideShutdownScope`,
  `duplicateCanonicalSourceAndOrderAreRejectedBeforeDatabaseWrite`,
  `removalIsBlockedWhileActiveLinkedWorkOrderExists`,
  `pendingApprovalAlsoFreezesSourceIdentityDuringUpdate`.
- Missing readiness/safety facts and invalid permit/isolation order:
  `PlannedShutdownReadinessPolicyTest.returnsEveryFailClosedBlockerInDeterministicOrder`,
  `safeStateRequiresEveryRequiredIsolationAppliedAndVerified`,
  `PlannedShutdownServiceTest.isolationRejectsUnrelatedNotIssuedFutureAndExpiredPermits`.
- Illegal lifecycle jumps, terminal transitions, cancellation without safe conditions:
  `PlannedShutdownTransitionPolicyTest.rejectsSkippedBackwardAndTerminalTransitions`,
  `cancellationAndRescheduleAreExplicitlyBounded`,
  `PlannedShutdownLifecycleServiceTest.cancellationIsBlockedWhileLinkedWorkOrdersRemainActive`.
- Stale/self/cross-role/cross-department approval:
  `PlannedShutdownLifecycleServiceTest.approvalFinalizationRejectsStaleScopeAndRequesterSelfApproval`,
  `approvalRejectsSubstringRoleAndSameActorAcrossProductionAndHse`,
  `ApprovalPbacScopeTest.plannedShutdownApproverRoleCannotCrossDepartmentScope`.
- Unsafe Work Order start:
  every negative in `PlannedShutdownWorkOrderStartPolicyTest`, including missing shutdown,
  cancelled/deleted/ineligible shutdown, window, stale approvals, isolation, permit, checklist,
  material and performer/contractor blockers; ordinary Work Orders remain compatible.
- Generation replay poisoning, invalid selection, race classification and partial failure:
  `PlannedShutdownWorkOrderGenerationServiceTest.sameHeaderWithDifferentFingerprintIsConflictBeforeWorkOrderAccess`,
  `invalidSelectionIsRejectedBeforeAnyWorkOrderMutation`,
  `exactGenerationConstraintMapsConflictWithoutPoisonedTransactionRequery`,
  `secondCreateFailureDoesNotRecordPartialCommand`, plus the live uniqueness probe.
- Failed/missing/stale startup and production evidence:
  `PlannedShutdownEvidenceServiceTest.mandatoryMissingOrFailedStartupTestsBlockStartup`,
  `staleProductionReturnCannotAuthorizeCompletion`,
  `approveExtendCompletionStaleReapprovePreservesOldEvidenceAndAuthorizesCurrentWindow`.
- Active work, unreleased/never-applied isolation, repeat closure and corrupted snapshot:
  `PlannedShutdownLifecycleServiceTest.completionAndCloseReportActiveWorkAndUnreleasedIsolationBeforeTaskSevenEvidence`,
  `completionFailsClosedForDefinedIsolationThatWasNeverAppliedOrReleased`,
  `PlannedShutdownReportServiceTest.deletedSnapshotStillBlocksSecondSnapshot`,
  `readRejectsStoredJsonWhoseHashDoesNotMatch`, plus the live immutability probe.
- API/security negatives:
  `RbacToirBusinessFlowSecurityTest.unrelatedUserCannotReadOrUpdatePlannedShutdownDetailAndScope`,
  all 13 `RbacPlannedShutdownReadinessSecurityTest` authority-separation cases, and typed
  400/409 blocker tests in `PlannedShutdownControllerContractTest`.

## Commit provenance

Planned Shutdown Stage 2 backend work is the reviewed range `5e1c405d..4c90fd2f`.
Unrelated concurrent commits inside that history are recorded separately and are not claimed as
Stage 2 evidence:

- `ed3da64e`, `3bde81f8`: active spare-part rule design/plan.
- `772b63f8`: dashboard vehicle-driver resolution design.
- `d2f01672`: Repair Campaign Work Order creation boundary hardening.

Frontend Stage 2 work is `fb914d00..71c6b4e3`; all seven commits in that range are Planned
Shutdown plan, compatibility, permission, workspace, or corrective-review commits.

## Release decision

The Stage 2 Planned Shutdown plan is technically verified at the pinned revisions, including a
real PostgreSQL 17 migration chain. The broader PS audit is not fully closed: the partial rows
above must remain visible inputs to later planning. Repository-wide frontend test/lint debt must
also be resolved or explicitly waived by the release owner; this report does not waive it.
