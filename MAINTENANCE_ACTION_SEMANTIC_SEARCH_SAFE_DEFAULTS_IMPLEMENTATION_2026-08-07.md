# TOIR Maintenance Action Semantic Search - Safe Defaults Implementation

**Date:** 2026-08-07

**Branch:** `bek_bobo`

**Source implementation commit:** `04974e93` (`fix: restore semantic search safe defaults`)

**Source push:** successful to `origin/bek_bobo`

## Result

Maintenance Action semantic search is fail-closed again. An ordinary backend configuration can load the real packaged `application.yml` without an AI revision, URL, endpoint, credentials, timeouts, or input limits. All runtime features and all external confirmation gates default false.

No TEST/production deployment, database access, AI request, canary, backfill, or runtime feature activation was performed.

## Files changed

Source/configuration/test commit `04974e93` contains only:

- `src/main/java/com/toir/config/MaintenanceActionSemanticSearchProperties.java`;
- `src/main/java/com/toir/config/MaintenanceActionSemanticSearchConfiguration.java`;
- `src/main/resources/application.yml`;
- `src/test/java/com/toir/config/MaintenanceActionSemanticSearchPropertiesTest.java`;
- `src/test/java/com/toir/config/MaintenanceActionSemanticSearchSafeDefaultsContextTest.java`.

This implementation report is committed separately after recording the source commit and push result. The prior recovery/audit report was preserved without modification and was not included in the scoped source commit.

No migration, controller, API contract, RBAC, queue, vector, business entity, seed, or unrelated source was changed.

## Before and after

Before the fix, Java and/or packaged configuration activated the master flag, confirmation gates, client concurrency, semantic-search API, and a production AI URL fallback. With an empty immutable model revision, ordinary startup failed at `BLOCKED_BY_MODEL_DIMENSION_CONTRACT`.

After the fix, a new properties instance and packaged configuration resolve to:

```text
enabled = false
generationWorkerEnabled = false
semanticSearchEnabled = false
backfillEnabled = false
aiServiceContractConfirmed = false
modelDimensionContractConfirmed = false
pgvectorPrerequisiteConfirmed = false
clientConcurrency = 0
```

The locked model and dimension remain unchanged:

```text
ibm-granite/granite-embedding-311m-multilingual-r2
768
```

Model revision, AI URL/path, authentication, timeouts, modes, and input limits remain unset unless runtime configuration supplies them. The production AI URL fallback was removed.

## External AI bean activation

The HTTP `RestClient` and `MaintenanceActionEmbeddingClient` previously used `ai-service-contract-confirmed` as their bean-enable condition. That acknowledgement could instantiate and validate the external adapter while every operational AI consumer was disabled.

Both beans now share a condition equivalent to:

```text
enabled
AND
(generation-worker-enabled OR semantic-search-enabled)
```

Consequences:

- fully disabled mode creates no external AI beans;
- enqueue-only mode creates no external AI beans, even if AI-contract confirmation is accidentally true;
- worker/search activation still runs `validateExternalGates()` and retains the AI-contract, pgvector, HTTP, immutable model/revision/dimension, input-limit, batch-size, authentication, timeout, and worker-specific safety gates;
- backfill remains independent and off by default.

No safety validation was loosened.

## Environment variable support

Direct canonical Spring environment variables are supported and tested, including:

- `TOIR_AI_MAINTENANCE_ACTION_SEMANTIC_SEARCH_ENABLED`;
- `TOIR_AI_MAINTENANCE_ACTION_SEMANTIC_SEARCH_GENERATION_WORKER_ENABLED`;
- `TOIR_AI_MAINTENANCE_ACTION_SEMANTIC_SEARCH_SEMANTIC_SEARCH_ENABLED`;
- `TOIR_AI_MAINTENANCE_ACTION_SEMANTIC_SEARCH_BACKFILL_ENABLED`;
- `TOIR_AI_MAINTENANCE_ACTION_SEMANTIC_SEARCH_AI_SERVICE_CONTRACT_CONFIRMED`;
- `TOIR_AI_MAINTENANCE_ACTION_SEMANTIC_SEARCH_MODEL_DIMENSION_CONTRACT_CONFIRMED`;
- `TOIR_AI_MAINTENANCE_ACTION_SEMANTIC_SEARCH_PGVECTOR_PREREQUISITE_CONFIRMED`.

The packaged YAML deliberately retains these existing compatibility aliases, all with safe false defaults:

- `TOIR_MAINTENANCE_ACTION_SEMANTIC_SEARCH_ENABLED`;
- `TOIR_MAINTENANCE_ACTION_EMBEDDING_WORKER_ENABLED`;
- `TOIR_MAINTENANCE_ACTION_SEMANTIC_SEARCH_API_ENABLED`;
- `TOIR_MAINTENANCE_ACTION_EMBEDDING_BACKFILL_ENABLED`;
- `TOIR_MAINTENANCE_ACTION_AI_CONTRACT_CONFIRMED`;
- `TOIR_MAINTENANCE_ACTION_MODEL_DIMENSION_CONFIRMED`;
- `TOIR_MAINTENANCE_ACTION_PGVECTOR_CONFIRMED`.

Binding tests prove both the canonical field-derived names and retained aliases map to the exact Java properties.

## Tests and commands

Maven used JDK 21 and the repository's Maven 3.9.11 distribution extracted under `%TEMP%`.

New safe-default tests:

```powershell
& $mvn '-Dtest=MaintenanceActionSemanticSearchPropertiesTest,MaintenanceActionSemanticSearchSafeDefaultsContextTest' test
```

Final result: **12 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS**.

The tests prove:

- real packaged configuration loads in fully disabled mode with missing AI configuration;
- all feature and confirmation defaults are false;
- no external AI beans exist in disabled mode;
- no external AI beans exist in enqueue-only mode, including accidental acknowledgement true;
- canonical environment binding works;
- retained aliases work;
- subordinate/master, enqueue identity/input limits, worker, search, HTTP, pgvector, batch, and auth gates remain fail-closed;
- no packaged production AI URL fallback exists.

Full relevant semantic suite:

```powershell
$tests='MaintenanceActionEmbeddingTextBuilderTest,EmbeddingVectorValidatorTest,HttpMaintenanceActionEmbeddingClientTest,MaintenanceActionSemanticSearchPropertiesTest,MaintenanceActionSemanticSearchSafeDefaultsContextTest,MaintenanceActionServiceTest,DurableMaintenanceActionEmbeddingLifecycleTest,JdbcMaintenanceActionEmbeddingJobStoreTest,MaintenanceActionEmbeddingWorkerTest,MaintenanceActionEmbeddingBackfillServiceTest,MaintenanceActionEmbeddingPrePgvectorSchemaContractTest,MaintenanceActionEmbeddingPgvectorSchemaContractTest,JdbcMaintenanceTemplateSemanticSearchRepositoryTest,MaintenanceTemplateSemanticSearchServiceTest,MaintenanceTemplateSemanticSearchControllerContractTest,FlywayMigrationVersionContractTest,FlywayMigrationFilenameContractTest'
& $mvn "-Dtest=$tests" test
```

Final result: **61 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS**. All 1,965 main sources and 717 test sources compiled during verification.

Explicit test compilation under constrained local memory:

```powershell
$env:MAVEN_OPTS='-Xms128m -Xmx512m -XX:MaxMetaspaceSize=256m'
& $mvn -DskipTests test-compile
```

Result: **BUILD SUCCESS**.

### pgvector/Testcontainers result

The integration suite was attempted with:

```powershell
$env:DOCKER_HOST='npipe:////./pipe/dockerDesktopLinuxEngine'
& $mvn '-Dtest=MaintenanceActionSemanticSearchPgvectorIntegrationTest' test
```

Docker Desktop was initially unavailable, so the disabled-without-Docker test was skipped. After starting Docker Engine 29.2.1, Windows failed JVM allocation because the paging file was too small. Test sources were then compiled with Docker stopped, Docker was restarted, and Surefire was run without an additional fork:

```powershell
& $mvn '-DforkCount=0' '-Dtest=MaintenanceActionSemanticSearchPgvectorIntegrationTest' surefire:test
```

The pgvector PostgreSQL 17 container started and Flyway began applying the validated 244-resource migration chain, but the database connection ended with SQLSTATE `08006` / EOF during migration under the same local memory pressure. This is classified as a local Docker/Windows paging-file infrastructure blocker, not a semantic-search assertion failure. The integration test itself passed in Gate 1 before these configuration-only changes; no migration, pgvector persistence, or query code changed in this task. The test was not weakened or replaced.

## Static and Git verification

- `git diff HEAD --check`: passed before commit.
- Separate trailing-whitespace checks passed for tracked and newly added scoped files.
- Source commit: `04974e93`.
- Source push: successful normal push to `origin/bek_bobo`; no force push.
- No reset, checkout rollback, rebase, merge, or migration change was performed.

## Deployment status

TEST deployment was intentionally not attempted. Production was not accessed. A future TEST deployment should first start with no semantic configuration and confirm all features/confirmation gates are effectively false before any explicitly configured rollout mode is considered.

## Final verdict

`SAFE_DEFAULTS_IMPLEMENTED_READY_FOR_TEST_DEPLOYMENT`
