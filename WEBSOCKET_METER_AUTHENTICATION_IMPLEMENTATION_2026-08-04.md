# TOIR Meter WebSocket Authentication — Evidence-Gated Implementation Report

**Date:** 2026-08-04 (Asia/Tashkent)  
**Mode:** evidence-gated incident closure  
**Outcome:** evidence gate not satisfied; no production or test source changed

## 1. Executive summary

The evidence gate does not authorize an application, frontend, or proxy patch. The checked-out backend already contains commit `7f26e11710ba1bbcc2427e02135d8adfc13bfa66`, and the current source preserves the intended scoped query-token contract: Bearer first, then `access_token` only for `GET /api/v1/ws/meters` with `Upgrade: websocket`; all other requests remain header-only. The endpoint is authenticated, the ordinary `JwtService` is used, and the handler requires `METER_READ`, `SYSTEM_ADMIN`, or `*`.

No immutable production image digest/commit, fresh-token REST result, fresh-token query/header WebSocket comparison, direct-backend result, original request timestamp, Nginx `upstream_status`, sanitized token expiry/authority/environment, frontend repository, or active proxy configuration is available. Therefore none of the decision-matrix roots can be confirmed. Reimplementing the existing backend flow would be speculative and unsafe.

No production/test code, configuration, migration, dependency, branch, or existing dirty file was modified. This report is the only file created by this evidence-gated task.

**Final verdict: `EVIDENCE_REQUIRED`**

## 2. Audit baseline

The authoritative baseline was read in full from `WEBSOCKET_METER_AUTHENTICATION_DEEP_AUDIT_2026-08-04.md`.

Baseline facts reconfirmed:

- audited/current backend HEAD: `00435f3f90cbe257f4bf0e49f94394c1f8eb644e`;
- query-token feature commit: `7f26e11710ba1bbcc2427e02135d8adfc13bfa66`;
- query-token scope: GET + exact `/api/v1/ws/meters` + WebSocket Upgrade;
- Bearer header precedence;
- endpoint is not `permitAll()`;
- missing/invalid authentication fails with HTTP 401 before upgrade;
- insufficient meter authority is a post-upgrade 1008 close, not HTTP 401;
- prior audit verdict: `INSUFFICIENT_EVIDENCE`.

The earlier safe public probe used only an intentionally invalid literal placeholder represented in records as `<REDACTED>`. It received Spring's `AUTHENTICATION_REQUIRED` JSON through `nginx/1.20.1`. This identifies the reproduced invalid-credential response source but cannot validate a real token, query forwarding, Upgrade forwarding, or deployed source version.

## 3. Repository/branch/HEAD/worktree state

### Backend

| Field | Evidence |
|---|---|
| Repository path | `D:\Projects\toir-org\toir-backend` |
| Branch | `bek_bobo` |
| HEAD | `00435f3f90cbe257f4bf0e49f94394c1f8eb644e` |
| Configured upstream for branch | none (`@{u}` is not configured) |
| Locally known remote default | `origin/main` / `origin/HEAD` pointed to this HEAD during the baseline audit |
| Preflight `git status --short` | `?? WEBSOCKET_METER_AUTHENTICATION_DEEP_AUDIT_2026-08-04.md` |
| Dirty/untracked files before this report | prior audit report only |
| `7f26e117` ancestor check | **YES**; `git merge-base --is-ancestor ... HEAD` returned exit code 0 |

No branch switch, merge, fetch, cherry-pick, commit, or push was performed. The pre-existing untracked audit report was preserved.

### Frontend

No frontend repository was present in the supplied workspace or under `D:\Projects`. Recursive exact-name search found no `meter-realtime-client.ts`; its real path, branch, HEAD, upstream, and worktree state are therefore **UNKNOWN**.

### Proxy/deployment

The backend repository contains `Dockerfile`, `docker-compose.yml`, and `.gitlab-ci.yml`. It contains no active Nginx, Ingress, Kubernetes Deployment, Helm, or Kustomize configuration. External proxy configuration cannot be patched or verified from this repository.

## 4. Production version evidence

```text
Production commit/image digest: UNKNOWN
Contains 7f26e117 or later: UNKNOWN
```

Repository evidence shows that current HEAD contains the feature commit, but runtime identity is not exposed:

- `.gitlab-ci.yml` builds and deploys mutable `${CI_REGISTRY_IMAGE}:latest`;
- the Docker image is not labeled with `CI_COMMIT_SHA` in the tracked pipeline/Dockerfile;
- no immutable digest is recorded in a tracked deployment manifest;
- unauthenticated `GET /actuator/info` returned Spring 401 and exposed no build data;
- unauthenticated `GET /actuator/health` returned backend-style 404 and exposed no build data.

These responses prove the public host reaches a TOIR/Spring-style application through Nginx, but they do not identify its code version. Deployment/version mismatch remains possible, not confirmed.

Required DevOps evidence is either the running container image ID plus immutable registry digest mapped to a pipeline commit, or a trusted non-secret build label/attestation. Deploying current main is not authorized by this task and was not attempted.

## 5. Runtime evidence matrix

```text
Production commit/image digest: UNKNOWN
Contains 7f26e117 or later: UNKNOWN

REST + Bearer: UNKNOWN — no fresh valid token result supplied
Public WS + query token: UNKNOWN — only an intentionally invalid placeholder produced Spring 401
Public WS + Bearer header: UNKNOWN — no fresh valid token result supplied
Direct backend WS + query token: UNKNOWN — backend access/result unavailable
Browser WS: reported HTTP authentication failure; exact timestamp/status/response body unavailable
Nginx status: UNKNOWN for original request; safe invalid-placeholder probe returned 401
Nginx upstream_status: UNKNOWN
```

Sanitized `exp`, effective authority, and issuing environment are also unavailable. No real credential was requested, printed, stored, decoded, or simulated. Runtime tests requiring valid production authentication were not performed.

## 6. Confirmed root cause

No underlying root cause is confirmed.

The failure stage remains supported: an HTTP authentication failure occurs before the WebSocket handler, when Spring has no accepted `Authentication` by authorization time. That does not distinguish among:

1. an old production image without `7f26e117`;
2. missing Upgrade/query forwarding;
3. a missing, stale, expired, malformed, or wrong-environment token;
4. frontend reconnect retaining an old token;
5. a production signing-key/environment mismatch;
6. a currently unseen request-matching/runtime integration defect.

Permission failure is not compatible with a pre-upgrade HTTP 401 under the current implementation; it would produce a 101 followed by 1008.

## 7. Chosen fix layer

**Chosen layer: none — evidence gate.**

Decision-matrix branches A–F each require runtime or repository evidence that is absent. Current backend extraction must not be rewritten because it already satisfies the contract. The frontend cannot be traced because its repository is unavailable. The proxy cannot be changed because its active configuration and direct-vs-public comparison are unavailable.

The incident is paused at `EVIDENCE_REQUIRED`, not closed with a speculative fix.

## 8. Files changed

Created by this task:

- `WEBSOCKET_METER_AUTHENTICATION_IMPLEMENTATION_2026-08-04.md` — this evidence-gated report.

Pre-existing untracked file preserved without modification during implementation:

- `WEBSOCKET_METER_AUTHENTICATION_DEEP_AUDIT_2026-08-04.md`.

Production source changed: none.  
Test source changed: none.  
Frontend changed: none.  
Proxy/deployment changed: none.

## 9. Detailed implementation

No functional implementation was authorized. The completed work consists of:

1. reading the authoritative audit completely;
2. recording repository path, branch, HEAD, upstream, and dirty state;
3. proving `7f26e117` is an ancestor of current HEAD;
4. searching for the frontend and proxy/Ingress configuration;
5. rereading the security filter, validator, filter chain, entry point, WebSocket registration/handler, and focused tests;
6. checking public non-credential endpoints for build identity without finding one;
7. applying the decision matrix and stopping before source modification.

### Current source contract

| Required invariant | Current evidence | Result |
|---|---|---|
| Bearer works on requests processed by the filter | `JwtAuthenticationFilter.java:104-108` extracts a nonblank `Authorization: Bearer ...` before any query logic | **PRESENT** |
| Query-token only for exact meter Upgrade | `JwtAuthenticationFilter.java:109-119` checks GET, exact request URI, and `Upgrade: websocket` | **PRESENT** |
| Header wins over query | extraction returns header token first; `JwtAuthenticationFilterWebSocketTest.java:48-61` verifies query is not parsed | **PRESENT** |
| Ordinary HTTP query does not authenticate | exact Upgrade predicate plus negative assertion at `JwtAuthenticationFilterWebSocketTest.java:39-45` | **PRESENT** |
| Valid authentication becomes principal | filter creates `AuthenticatedUser`/`UsernamePasswordAuthenticationToken` and sets `SecurityContextHolder` at lines 56-71; focused test asserts authentication is non-null | **PRESENT in source/unit contract**; runtime handshake not evidenced |
| Handler requires meter-read permission | `MeterRealtimeWebSocketHandler.java:28-29,38-45,100-105`; handler test verifies unauthorized principal closes with 1008 | **PRESENT** |
| Ordinary validator is reused | filter calls `jwtService.parse(token)` at line 46; `JwtService.java:61-66` verifies signed claims | **PRESENT** |
| Endpoint remains authenticated | `/api/v1/ws/meters` is absent from public paths; `SecurityConfig.java:60-68` applies `.anyRequest().authenticated()` | **PRESENT** |

The feature-related files have no diff between `7f26e117..HEAD` in the scoped comparison. No second validator, handshake interceptor, `permitAll`, or broader query-token matcher was added.

## 10. Security invariants preserved

Because no functional code changed, all current safeguards remain intact:

- exact endpoint scope;
- GET + WebSocket Upgrade requirement;
- Bearer header precedence;
- ordinary `JwtService` signature/temporal validation;
- fail-closed HTTP 401 for absent/rejected authentication;
- authenticated Spring principal creation;
- handler-side `METER_READ` / `SYSTEM_ADMIN` / `*` check;
- ordinary REST query-token negative behavior;
- no anonymous `permitAll` opening;
- no real token/full authenticated WebSocket URL in this report or terminal requests;
- no unrelated issuer/audience, lifetime, revocation, or ticket-architecture change.

## 11. Frontend/backend/proxy responsibilities

### Backend

- Provide the already implemented exact-path query-token and Bearer contracts.
- Validate through the existing `JwtService` and attach the Spring principal.
- Return 401 for missing/invalid authentication and preserve post-upgrade permission enforcement.
- Provide an auditable build identity through deployment metadata in a separately authorized DevOps change.

### Frontend

- Evidence owner must supply the actual repository/path and sanitized behavior around initial connect, refresh, reconnect, logout, and cleanup.
- Each connection attempt must obtain the current raw access JWT, reject blank/`undefined`/`null`, encode once, omit `Bearer ` in the query value, avoid stale closures, and avoid token/full-URL logging.
- No frontend conclusion or patch is possible until `meter-realtime-client.ts` and its auth conventions are available.

### Proxy/DevOps

- Prove running image identity and map it to a commit containing or lacking `7f26e117`.
- Capture original `status` and `upstream_status` without logging `$request_uri`, `$args`, token, cookie, or Authorization value.
- Compare direct backend and public proxy with the same newly issued token.
- Preserve HTTP/1.1, `Upgrade`, `Connection`, query arguments, `Origin`, and (for diagnostic clients) Authorization on the exact location.
- Redact query token/full request target from Nginx, load balancer, APM, and Sentry telemetry.

## 12. Tests added or updated

None. No defect and no changed functional layer was confirmed, so adding tests would not accompany an authorized patch.

Existing focused tests read during the gate:

- `JwtAuthenticationFilterWebSocketTest` — valid scoped query extraction, ordinary REST negative case, header precedence;
- `JwtAuthenticationFilterRejectionTest` — missing/wrong scheme/malformed Bearer remains anonymous;
- `MeterRealtimeWebSocketHandlerTest` — allowed broadcast, insufficient authority 1008, client-write rejection;
- authority-mapping tests elsewhere confirm `AuthenticatedUser` principal creation.

The existing suite does not provide a full deployed servlet-to-WebSocket integration proof and does not replace runtime evidence.

## 13. Verification status

| Verification | Status |
|---|---|
| Authoritative audit read | completed |
| Git preflight | completed, read-only |
| `7f26e117` ancestor check | completed: YES |
| Current source contract inspection | completed, read-only |
| Frontend discovery | completed: not found |
| Proxy config discovery | completed: not found |
| Public build identity discovery | completed: identity unavailable |
| Valid-token production comparisons | not performed: credential/evidence unavailable |
| Tests | **NOT RUN — skipped by instruction** |
| Build/typecheck/lint | **NOT RUN — skipped by instruction** |

## 14. Manual production verification

Run these steps only by authorized operators. Use one newly issued token, never paste it into a ticket/chat, never use verbose HTTP output that echoes Authorization, and sanitize every artifact as `<REDACTED>`.

### A. Prove deployed version

On the authorized production host:

```powershell
docker inspect --format '{{.Image}}' toir-backend
docker image inspect --format '{{json .RepoDigests}} {{json .Config.Labels}}' <IMAGE_ID>
```

Map the image ID/digest to the registry pipeline commit. Record only digest/commit, not registry credentials. Evaluate whether that commit contains `7f26e117` using `git merge-base --is-ancestor` in a trusted checkout.

### B. Four-way same-token matrix

Obtain a new token through the normal UI/auth process and keep it only in a protected local variable. Record statuses only.

```text
1. REST GET /api/v1/meters with Authorization: Bearer <REDACTED>
2. Public wss /api/v1/ws/meters?access_token=<REDACTED>
3. Public wss /api/v1/ws/meters with Authorization: Bearer <REDACTED>
4. Direct backend ws /api/v1/ws/meters?access_token=<REDACTED>
```

Use Postman, an authorized host with `websocat`, or `wscat`. The native browser API cannot set Authorization, so step 3 needs a diagnostic client. `wscat`/`websocat` were not installed in the audit shell and were not installed because dependency changes are prohibited.

Interpretation:

- production older than `7f26e117` → deploy existing current main/feature commit by immutable digest; do not rewrite extraction;
- REST 401 → signing environment, expiration, malformed credential, and clocks; do not change WebSocket code;
- REST succeeds + direct query succeeds + public query fails → proxy layer;
- REST succeeds + public Bearer succeeds + query fails → deployed version, Upgrade visibility, or query visibility;
- public/direct work + browser fails → frontend token lifecycle;
- 101 then 1008 → permission/authority policy, not HTTP authentication.

### C. Safe Nginx evidence

Capture only timestamp, `$uri` (not `$request_uri`), `$status`, `$upstream_status`, selected upstream, `$http_upgrade` presence, and timing. Never log `$args`, raw `$request`, Authorization, Cookie, or the full WebSocket URL. Verify the exact active location includes the equivalent of:

```nginx
proxy_http_version 1.1;
proxy_set_header Upgrade $http_upgrade;
proxy_set_header Connection $connection_upgrade;
proxy_set_header Origin $http_origin;
proxy_set_header Authorization $http_authorization;
```

Confirm the `proxy_pass`/rewrite form preserves original query arguments. Do not apply this generic snippet blindly; patch the active configuration only after direct-vs-public evidence confirms the proxy branch, then run the organization's Nginx config validation and controlled reload process.

### D. Sanitized token facts

Record only:

```text
exp: <UTC timestamp, no token>
effective authority: METER_READ / SYSTEM_ADMIN / * / absent
issuing environment: <environment name, no secret>
server UTC time: <timestamp>
```

Offline payload decoding is not signature validation. Do not disclose token segments or signing material.

## 15. Remaining blockers

1. Production container commit or immutable digest mapped to source.
2. Fresh-token REST Bearer status.
3. Same-token public query and public header WebSocket statuses.
4. Same-token direct-backend query WebSocket status, if network policy allows it.
5. Original browser request timestamp, HTTP status, and sanitized response body/headers.
6. Matching Nginx `status` and `upstream_status` with safe URI-only logging.
7. Sanitized `exp`, effective authority, issuing environment, and server time.
8. Frontend repository and actual `meter-realtime-client.ts` path/HEAD/worktree state.
9. Active Nginx/Ingress configuration repository/path.

Any one evidence set may select a decision branch, but no source patch should begin until the comparison is internally consistent and the affected repository is available.

## 16. Final verdict

`EVIDENCE_REQUIRED`

The current backend implementation is correct with respect to the incident's required authentication contract, and `7f26e117` is present in current HEAD. Runtime evidence is insufficient to confirm deployment, proxy, token configuration, frontend lifecycle, backend integration, or permission as the root cause. Per the safety gate, work stops with no production/test source changes. The next authorized action is evidence collection using the sanitized version and four-way runtime matrix above.
