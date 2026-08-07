# TOIR Maintenance Action Semantic Search - TEST Rollout Gate 3 Canary Worker

**Date:** 2026-08-07

**Repository branch / HEAD:** `bek_bobo` / `28af01960dfdc8edc1de64525f87eb48ad61fc52`

## Outcome

Gate 3 stopped before the non-canary queue safety gate because this workspace does not contain or expose an unambiguous TEST runtime target, a read-only TEST database connection, TEST application credentials, or TEST runtime configuration control. Enabling a worker without first listing every claimable job would violate the mandatory safety gate.

The public `api-toir.tenzorsoft.uz` endpoint is identified by repository production configuration and was not treated as TEST. No guessed host, credential, or deployment target was used.

## Runtime contract discovered

The worker requires both the master and worker flags:

- `TOIR_MAINTENANCE_ACTION_SEMANTIC_SEARCH_ENABLED`;
- `TOIR_MAINTENANCE_ACTION_EMBEDDING_WORKER_ENABLED`.

Gate 3 must keep these disabled paths explicitly false:

- `TOIR_MAINTENANCE_ACTION_EMBEDDING_BACKFILL_ENABLED=false`;
- `TOIR_MAINTENANCE_ACTION_SEMANTIC_SEARCH_API_ENABLED=false`.

The remaining worker contract is:

- `TOIR_MAINTENANCE_ACTION_AI_CONTRACT_CONFIRMED=true`;
- `TOIR_MAINTENANCE_ACTION_MODEL_DIMENSION_CONFIRMED=true`;
- `TOIR_MAINTENANCE_ACTION_PGVECTOR_CONFIRMED=true`;
- `TOIR_MAINTENANCE_ACTION_EMBEDDING_BASE_URL`;
- `TOIR_MAINTENANCE_ACTION_EMBEDDING_ENDPOINT_PATH`;
- optional paired `TOIR_MAINTENANCE_ACTION_EMBEDDING_AUTH_HEADER` and `TOIR_MAINTENANCE_ACTION_EMBEDDING_AUTH_SECRET`;
- immutable `TOIR_MAINTENANCE_ACTION_EMBEDDING_MODEL_REVISION`;
- `TOIR_MAINTENANCE_ACTION_EMBEDDING_DOCUMENT_MODE` and query mode configuration;
- positive connect/read timeouts;
- `TOIR_MAINTENANCE_ACTION_EMBEDDING_CLIENT_CONCURRENCY=1` for the canary;
- `TOIR_MAINTENANCE_ACTION_EMBEDDING_BATCH_SIZE=1`;
- positive polling interval and lease duration;
- bounded retry count, base/max backoff, and jitter in `[0,1)`;
- positive per-field/composed input character and UTF-8 byte limits.

The fixed model contract is `ibm-granite/granite-embedding-311m-multilingual-r2`, dimension `768`, and source schema version `maintenance-action-text-v1`. Secrets were neither retrieved nor recorded.

## Deployment/access discovery

- Current working tree was clean at entry.
- The checked-out semantic-search commit is present on `origin/bek_bobo`.
- The tracked GitLab pipeline is limited to the default branch and uses externally supplied SSH/server variables. It defines no TEST environment or TEST URL in the repository.
- The GitLab project API was reachable with the locally configured Git credential, but exposed no project CI variables or registered project environments. Group-variable listing was denied, so no TEST target could be established through that route.
- No Kubernetes context, TEST-specific environment variables, SSH host configuration, read-only PostgreSQL DSN, or TEST runtime-control tool was available locally.
- The repository's only explicit public backend URL is marked as production. It was not contacted for this task.

## Runtime flags actually used

No TEST runtime configuration was changed.

The prompt states that master, worker, backfill, and semantic-search API flags were OFF after Gate 2. This could not be independently verified from the unavailable TEST runtime. No secret values were inspected or printed.

## Queue preflight

The production schema was identified:

- jobs: `maintenance_action_embedding_jobs`;
- vectors: `maintenance_action_embeddings`.

The worker can claim current jobs with remaining attempts when they are:

- `PENDING`;
- eligible `RETRY_WAIT` (`next_attempt_at <= now()`);
- expired `PROCESSING` (`lease_until <= now()`).

The required read-only query could not be executed because no TEST database connection was available. Therefore current job IDs, Action IDs, statuses, and canary classification are unknown. This is the exact point where execution stopped.

## Canary and real-AI activity

- Canary Maintenance Template created: **none**.
- Canary Maintenance Actions created: **none**.
- Canary IDs: **none**.
- Real embedding-service attempts: **0**.
- Lifecycle observations: **not started**.
- Vector/model/hash compatibility checks: **not started**.
- Requeue-after-content-change check: **not started**.
- Source files/default flags changed: **none**.

No non-canary job was processed by this task because the worker was never enabled. No raw vector, token, credential, or AI response was emitted.

## Controlled shutdown state

No worker activation occurred, so no shutdown mutation was required. This task made no change to worker, backfill, or semantic-search API configuration. The supplied Gate 2 state says all remain OFF, but independent runtime confirmation requires TEST access.

## Exact root cause

`GATE_3_BLOCKED_BY_RUNTIME_CONFIGURATION`: the mandatory queue safety gate requires read-only evidence from the actual TEST database before any worker activation, while canary creation and worker toggling require an authenticated, explicitly identified TEST API/UI and runtime-control path. None of those TEST-specific access inputs are available in this workspace, and substituting the repository's production-labelled public host is prohibited.

## Next safe step

Provide or configure all of the following for the explicitly identified TEST environment:

1. TEST API base URL and an authorized TEST canary account/session;
2. read-only TEST PostgreSQL connection capable of querying the two embedding tables and the linked Maintenance Actions/Templates;
3. TEST runtime configuration/restart mechanism (for example, an SSH target, deployment environment, or operator command) with current non-secret variable names visible;
4. TEST log read access;
5. confirmed immutable embedding revision, TEST AI endpoint/path, document mode, limits/timeouts/retry values, and the location of the authentication secret without placing the secret in chat or the report.

Once these are available, resume at the non-canary queue query. Do not create canaries or enable the worker before that query proves no non-canary job is claimable.

## Final verdict

`GATE_3_BLOCKED_BY_RUNTIME_CONFIGURATION`
