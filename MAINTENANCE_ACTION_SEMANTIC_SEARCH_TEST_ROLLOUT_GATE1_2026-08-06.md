# TOIR Maintenance Action Semantic Search - TEST Rollout Gate 1

**Date:** 2026-08-06

**Scope:** build and automated verification only

**Branch:** `bek_bobo`

**HEAD:** `3cb4ba5c3208859f6bf55d9a63460e481c0fdaec`

## Outcome

The semantic-search implementation compiles and its focused unit, contract, and real-pgvector integration tests pass. The integration test applied the complete production Flyway chain to an ephemeral PostgreSQL 17/pgvector 0.8.x container and exercised real `vector(768)` persistence and `<=>` cosine ranking.

No TEST/PROD database, deployment, real AI endpoint, real credential, or real backfill was used.

## Preflight

- Inspected the existing dirty working tree without reset, staging, commit, or push.
- Read the final implementation report, implementation/tests, Maven configuration, and database-test configuration.
- Inspected `V20260806_1__maintenance_action_embedding_pre_pgvector.sql` and `V20260806_2__maintenance_action_embedding_pgvector.sql`; neither migration was modified.
- Scanned all `src/main/resources/db/migration/V*.sql` filenames by parsed version: `NO_DUPLICATE_FLYWAY_VERSIONS`.
- Flyway filename and version contract tests both passed.
- `src/test/resources/application-test.yml` targets a persistent localhost database. A repository-wide `mvn test` was therefore not run blindly because it could violate the explicit prohibition on touching a real/local TEST database. Compilation of all 716 test sources plus all 54 relevant isolated tests and the dedicated ephemeral database integration test is the broadest safe verification for this gate.

## Environment and exact commands

The repository had no usable `mvnw.cmd`, Java was not on `PATH`, and the checked-out Maven extraction was incomplete. The existing Maven 3.9.11 ZIP was extracted only under `%TEMP%`; no repository wrapper files were changed.

Common shell setup:

```powershell
$env:JAVA_HOME='C:\Users\Asus\.jdks\corretto-21.0.10'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$mvn="$env:TEMP\codex-toir-maven-3.9.11\apache-maven-3.9.11\bin\mvn.cmd"
```

Compile and test-compile:

```powershell
& $mvn -DskipTests compile
& $mvn -DskipTests test-compile
```

Focused unit/contract suite:

```powershell
$tests='MaintenanceActionEmbeddingTextBuilderTest,EmbeddingVectorValidatorTest,HttpMaintenanceActionEmbeddingClientTest,MaintenanceActionSemanticSearchPropertiesTest,MaintenanceActionServiceTest,DurableMaintenanceActionEmbeddingLifecycleTest,JdbcMaintenanceActionEmbeddingJobStoreTest,MaintenanceActionEmbeddingWorkerTest,MaintenanceActionEmbeddingBackfillServiceTest,MaintenanceActionEmbeddingPrePgvectorSchemaContractTest,MaintenanceActionEmbeddingPgvectorSchemaContractTest,JdbcMaintenanceTemplateSemanticSearchRepositoryTest,MaintenanceTemplateSemanticSearchServiceTest,MaintenanceTemplateSemanticSearchControllerContractTest,FlywayMigrationVersionContractTest,FlywayMigrationFilenameContractTest'
& $mvn "-Dtest=$tests" test
```

Ephemeral pgvector integration:

```powershell
$env:DOCKER_HOST='npipe:////./pipe/dockerDesktopLinuxEngine'
& $mvn '-Dtest=MaintenanceActionSemanticSearchPgvectorIntegrationTest' test
```

Dependency and static checks:

```powershell
& $mvn '-Dincludes=org.testcontainers:*' dependency:tree
git diff HEAD --check
# PowerShell scan of every V*.sql parsed Flyway version
git branch --show-current
git rev-parse HEAD
git status --short
Select-String -Path src\test\java\**\*.java,src\test\resources\* -Pattern 'toir-ai\.tenzorsoft\.uz'
docker ps -a --filter ancestor=pgvector/pgvector:0.8.1-pg17
```

## Results

### Compilation

- Main compile: **BUILD SUCCESS**; 1,965 production source files compiled; 56.220 s on the explicit compile run.
- Test compile: **BUILD SUCCESS**; initially 715 and finally 716 test source files compiled after adding the integration test; 25.959 s on the explicit test-compile run.
- Existing Lombok/deprecation/unchecked warnings remain; no semantic-search compilation error remains.

### Focused unit and contract tests

Final result: **54 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS**.

Coverage includes:

- AI HTTP request/response mapping and mocked failure behavior;
- exact 768-dimension and finite-number validation, including NaN rejection;
- enqueue, same-hash deduplication, changed-hash stale/current lifecycle;
- retry, lease/fencing, stale-worker rejection, vector-before-READY SQL invariant;
- idempotent/restartable backfill behavior;
- exact semantic SQL contract, unique Template grouping/MAX-before-LIMIT;
- API payload/zero-result contract, RBAC guard, and controlled HTTP 503;
- Flyway migration filename/version collision contracts.

### Testcontainers and pgvector

Final result: **2 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS**.

- Testcontainers: `1.21.4`, consistently resolved for PostgreSQL, JDBC, database-commons, JUnit Jupiter, and core.
- Image: `pgvector/pgvector:0.8.1-pg17`.
- Database: ephemeral PostgreSQL 17.8 on a random local port.
- Test infrastructure alone executed `CREATE EXTENSION vector`; production Flyway/runtime code still does not create it.
- Flyway validated 244 migration resources and successfully applied 218 versioned migrations from the empty-schema baseline through `V20260806.2`.
- Confirmed the migrated column reports exactly `vector(768)`.
- Confirmed real vector persistence and atomic fenced `PROCESSING -> READY`; an incorrect lease token persisted no vector and published no READY state.
- Executed the real pgvector `<=>` cosine query.
- Confirmed stale/wrong-revision/unlinked rows are excluded.
- Confirmed grouping before limit, `MAX(actionSimilarityScore)` per Template, at most 10 unique Template IDs, deterministic score/UUID ordering, and empty results for an incompatible revision.
- Testcontainers/Ryuk removed the ephemeral pgvector container; the final image-filtered `docker ps -a` output was empty.

## Failures found and fixes

1. Docker Desktop was initially stopped. It was started locally; no remote environment was involved.
2. The repository inherited Testcontainers `1.19.8`, which could not negotiate successfully with Docker Engine 29.2.1 and caused the integration test to be skipped. `pom.xml` now pins Testcontainers `1.21.4` and removes the duplicate `junit-jupiter` declaration. The dependency tree is consistent and the test executes rather than skips.
3. The first executing integration run exposed a test-fixture `Instant` bind. The fixture was corrected to bind `OffsetDateTime`.
4. The next run exposed the same real production JDBC boundary defect in `JdbcMaintenanceActionEmbeddingJobStore`: PostgreSQL JDBC cannot infer a SQL type for `Instant` in generic named parameters. All store timestamp bind values are now converted to UTC `OffsetDateTime`. This covers enqueue/update, claim/lease, retry, terminal failure, vector completion, and stale transitions. The full focused suite and pgvector integration then passed.

Files changed specifically during Gate 1:

- `pom.xml`;
- `src/main/java/com/toir/service/maintenanceembedding/JdbcMaintenanceActionEmbeddingJobStore.java`;
- `src/test/java/com/toir/service/maintenanceembedding/MaintenanceActionSemanticSearchPgvectorIntegrationTest.java` (new);
- this report.

## Static verification and isolation

- `git diff HEAD --check`: exit 0; only line-ending conversion warnings, no whitespace error. A separate trailing-whitespace scan also passed for the untracked Gate 1 report and integration test.
- Working-tree inspection found the expected prior semantic-search implementation plus the Gate 1 files above; no unrelated file was intentionally changed during this gate.
- No test source/resource reference to `https://toir-ai.tenzorsoft.uz` was found.
- AI behavior remained mocked/stubbed; no network call to an AI service occurred.
- No live TEST/PROD database or credentials were used. Flyway ran only against the Testcontainers random-port database.
- No deployment, feature enablement, backfill, commit, push, or production access occurred.

## Remaining Gate 2 runtime configuration gates

- Back up TEST and re-scan the deployed migration set.
- Confirm the TEST application role can use the infrastructure-owned pgvector extension/type.
- Deploy with all semantic-search, worker, backfill, and API feature flags false; run migrations through the approved TEST pipeline.
- Verify TEST schema/startup before enabling any feature.
- Configure immutable model revision, AI URL/auth/timeouts/input limits, confirmation flags, lease/retry/concurrency controls, and batch size 1 initially.
- Enable and observe a canary worker lifecycle before a bounded backfill; then canary the secured search permission/API.
- Validate multilingual ranking, latency, 503 behavior, metrics, restart/idempotency, and READY coverage in TEST before expansion.

## Final verdict

`GATE_1_PASSED_READY_FOR_TEST_DEPLOY`
