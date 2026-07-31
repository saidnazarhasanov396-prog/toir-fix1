# Analytics Period Context Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add one authoritative 7-day/30-day/all-time period to the downtime analytics page and show backend-supplied period, department, source, and update metadata beside every statistic, chart, and table.

**Architecture:** Resolve an immutable analytics range and access scope once per backend request, filter every contributing dataset before aggregation, and return a shared `AnalyticsContextDto` with each response. The React page owns one URL-backed period value, passes it to every query, and renders a reusable context badge/tooltip from response metadata.

**Tech Stack:** Java 21, Spring Boot 3.3, JUnit 5/Mockito/MockMvc, React 19, TypeScript 6, TanStack Query v5, React Router v7, Radix Tooltip, Vitest/Testing Library, i18next.

## Global Constraints

- Supported periods are exactly `LAST_7_DAYS`, `LAST_30_DAYS`, and `ALL_TIME`.
- First visit defaults to `LAST_30_DAYS`; the choice is stored in the `period` URL parameter.
- The backend, not the browser, filters and describes the data.
- Department visibility continues to come from existing PBAC scope checks.
- Downtime intervals are clipped to the selected range; empty ranges return zero/empty results.
- New UI strings must exist in `ru`, `uz`, and `en`.
- Use TDD: each production behavior must first be demonstrated by a correctly failing test.
- Preserve unrelated dirty-worktree changes in both repositories.

---

## File Structure

### Backend

- Create `src/main/java/com/toir/dto/analytics/AnalyticsPeriod.java`: allowlisted period enum.
- Create `src/main/java/com/toir/dto/analytics/AnalyticsRange.java`: resolved `from`, `to`, and overlap helpers.
- Create `src/main/java/com/toir/dto/analytics/AnalyticsContextDto.java`: response metadata contract.
- Create `src/main/java/com/toir/service/AnalyticsContextService.java`: clock/timezone/range/scope resolution.
- Modify `src/main/java/com/toir/dto/analytics/AnalyticsOverview.java`, `FailureParetoResponse.java`, and `RcaOverviewResponse.java`: add context.
- Create `src/main/java/com/toir/dto/analytics/AnalyticsPageResponse.java`: flat pagination fields plus context for reliability and events.
- Modify `src/main/java/com/toir/controller/AnalyticsController.java`: accept `period` consistently.
- Modify `src/main/java/com/toir/service/AnalyticsService.java` and `ReliabilityPassportService.java`: range-filter source records and attach metadata.
- Test in `src/test/java/com/toir/service/AnalyticsContextServiceTest.java`, `AnalyticsServiceTest.java`, `ReliabilityPassportServiceTest.java`, and `src/test/java/com/toir/controller/AnalyticsControllerContractTest.java`.

### Frontend

- Modify `src/types/api.ts`: analytics period/context and contextual response types.
- Create `src/modules/analytics/libs/downtime-analytics/analytics-period.ts`: URL parsing and API query helpers.
- Create `src/modules/analytics/libs/downtime-analytics/tests/analytics-period.test.ts`.
- Create `src/modules/analytics/components/downtime-analytics/analytics-context.tsx`: visible badge and tooltip.
- Create `src/modules/analytics/components/downtime-analytics/analytics-context.test.tsx`.
- Modify `src/modules/analytics/pages/downtime-analytics-page.tsx` and its section components to consume one period/context.
- Create `src/modules/analytics/pages/downtime-analytics-page.test.tsx`.
- Modify `src/i18n/locales/{ru,uz,en}.json`.

---

### Task 1: Resolve analytics periods deterministically

**Interfaces:**
- Produces: `AnalyticsPeriod`, `AnalyticsRange`, and `AnalyticsContextService.resolve(AnalyticsPeriod)`.
- Consumes: Spring `Clock`, configured business `ZoneId`, existing current-user department scope.

- [ ] **Step 1: Write failing range-resolution tests**

```java
@Test
void lastSevenDaysUsesOneClockReadingAndRollingBoundary() {
    Instant asOf = Instant.parse("2026-07-31T10:00:00Z");
    AnalyticsRange range = service.resolveRange(AnalyticsPeriod.LAST_7_DAYS);
    assertThat(range.from()).isEqualTo(asOf.minus(7, ChronoUnit.DAYS));
    assertThat(range.to()).isEqualTo(asOf);
}

@Test
void allTimeHasNoLowerBoundary() {
    assertThat(service.resolveRange(AnalyticsPeriod.ALL_TIME).from()).isNull();
}
```

- [ ] **Step 2: Run the tests and verify RED**

Run: `./mvnw -Dtest=AnalyticsContextServiceTest test`
Expected: FAIL because the period/range types do not exist.

- [ ] **Step 3: Add the minimal enum, range, and resolver**

Implement `AnalyticsPeriod` with the three values and `AnalyticsRange` with
`boolean contains(Instant)` and `Duration overlap(Instant start, Instant end)`.
Inject `Clock`; never call `Instant.now()` inside analytics calculation code.

- [ ] **Step 4: Add parsing/error and fixed-timezone cases**

Assert invalid enum input produces HTTP 400 through Spring binding and that local
date conversion uses `Asia/Tashkent` at both boundaries.

- [ ] **Step 5: Run the focused tests and verify GREEN**

Run: `./mvnw -Dtest=AnalyticsContextServiceTest test`
Expected: PASS with zero failures.

- [ ] **Step 6: Commit the backend value objects**

```bash
git add src/main/java/com/toir/dto/analytics/AnalyticsPeriod.java \
  src/main/java/com/toir/dto/analytics/AnalyticsRange.java \
  src/main/java/com/toir/service/AnalyticsContextService.java \
  src/test/java/com/toir/service/AnalyticsContextServiceTest.java
git commit -m "feat: resolve analytics reporting periods"
```

### Task 2: Apply the period to overview aggregation

**Interfaces:**
- Consumes: `AnalyticsRange` from Task 1.
- Produces: `AnalyticsService.overview(AnalyticsPeriod period)` returning an
  `AnalyticsOverview` whose values all come from the resolved range.

- [ ] **Step 1: Add failing service tests for source timestamp rules**

Use records immediately before, inside, and after the range. Assert request fallback
from `detectedAt` to `createdAt`, work-order completion by `actualCompletionAt`,
defect occurrence filtering, and PPR due/completion filtering.

- [ ] **Step 2: Add the failing downtime-overlap test**

```java
@Test
void overviewClipsDowntimeToSelectedRange() {
    // Event spans 2 hours before the boundary and 3 hours after it.
    // Only the 3 in-range hours contribute.
    assertThat(service.overview(LAST_7_DAYS).kpis().downtimeHoursTotal())
        .isEqualTo(3.0);
}
```

- [ ] **Step 3: Run the focused test and verify RED**

Run: `./mvnw -Dtest=AnalyticsServiceTest test`
Expected: FAIL because `overview(AnalyticsPeriod)` and range filtering are absent.

- [ ] **Step 4: Implement minimal range filtering before every aggregation**

Add named private predicates such as `requestOccurredIn`, `defectOccurredIn`,
`workOrderCreatedIn`, and `workOrderCompletedIn`. Clip downtime before calling
`ReliabilityDowntimeCalculator`; do not mutate JPA entities.

- [ ] **Step 5: Make current reliability calculations range-aware**

Pass the same `asOf` and filtered/clipped events, orders, and requests into the
calculator. Do not fall back to an all-time stored metric when it lies outside the
selected range.

- [ ] **Step 6: Verify overview tests GREEN**

Run: `./mvnw -Dtest=AnalyticsServiceTest test`
Expected: PASS, including empty-range zero totals.

- [ ] **Step 7: Commit overview aggregation**

```bash
git add src/main/java/com/toir/service/AnalyticsService.java \
  src/test/java/com/toir/service/AnalyticsServiceTest.java
git commit -m "feat: filter analytics overview by period"
```

### Task 3: Return context from every page analytics endpoint

**Interfaces:**
- Produces: `AnalyticsContextDto(period, from, to, timezone, scope, calculatedAt, sources)`.
- Produces: `AnalyticsPageResponse<T>` retaining `content`, `number`, `size`,
  `totalElements`, `totalPages`, and adding `analyticsContext`.

- [ ] **Step 1: Write failing MockMvc contract tests**

For `/overview`, `/pareto/failures`, `/rca/overview`, `/reliability`, and
`/downtime-events`, request `period=LAST_7_DAYS` and assert:

```java
.andExpect(jsonPath("$.analyticsContext.period").value("LAST_7_DAYS"))
.andExpect(jsonPath("$.analyticsContext.scope.type").value("DEPARTMENT"))
.andExpect(jsonPath("$.analyticsContext.sources").isArray());
```

- [ ] **Step 2: Run controller tests and verify RED**

Run: `./mvnw -Dtest=AnalyticsControllerContractTest test`
Expected: FAIL because endpoints do not accept or return the context.

- [ ] **Step 3: Add DTOs and controller parameters**

Add `@RequestParam(defaultValue = "LAST_30_DAYS") AnalyticsPeriod period` to all
five methods. Replace raw `Page` for events/reliability with
`AnalyticsPageResponse.from(page, context)` so existing pagination field names stay
flat and compatible.

- [ ] **Step 4: Return honest source lists and scope labels**

Use stable source codes per response. Resolve the authorized department's name from
the existing repository; use `ALL_AUTHORIZED` with null department fields for an
unrestricted user.

- [ ] **Step 5: Extend reliability filtering tests**

Verify that defects/downtime/orders/requests outside the range do not change a
passport row, numeric sorting still applies to filtered metrics, and the page total
still describes authorized equipment rather than source event count.

- [ ] **Step 6: Run backend analytics tests and verify GREEN**

Run: `./mvnw -Dtest=AnalyticsContextServiceTest,AnalyticsServiceTest,ReliabilityPassportServiceTest,AnalyticsControllerContractTest test`
Expected: PASS with zero failures.

- [ ] **Step 7: Commit contextual API contracts**

```bash
git add src/main/java/com/toir/dto/analytics \
  src/main/java/com/toir/controller/AnalyticsController.java \
  src/main/java/com/toir/service/ReliabilityPassportService.java \
  src/test/java/com/toir/controller/AnalyticsControllerContractTest.java \
  src/test/java/com/toir/service/ReliabilityPassportServiceTest.java
git commit -m "feat: expose analytics calculation context"
```

### Task 4: Add frontend period state and request plumbing

**Interfaces:**
- Produces: `AnalyticsPeriod = "LAST_7_DAYS" | "LAST_30_DAYS" | "ALL_TIME"`.
- Produces: `parseAnalyticsPeriod(value): AnalyticsPeriod` and
  `analyticsPeriodQuery(period): string`.

- [ ] **Step 1: Write failing URL/query helper tests**

```ts
expect(parseAnalyticsPeriod(null)).toBe("LAST_30_DAYS");
expect(parseAnalyticsPeriod("LAST_7_DAYS")).toBe("LAST_7_DAYS");
expect(parseAnalyticsPeriod("bad")).toBe("LAST_30_DAYS");
expect(analyticsPeriodQuery("ALL_TIME")).toBe("period=ALL_TIME");
```

- [ ] **Step 2: Run the helper test and verify RED**

Run: `yarn test src/modules/analytics/libs/downtime-analytics/tests/analytics-period.test.ts`
Expected: FAIL because the module is missing.

- [ ] **Step 3: Add types and minimal pure helpers**

Define `AnalyticsContext` in `src/types/api.ts` with nullable `from`, scope fields,
and stable source-code strings. Extend overview, pareto, RCA, and page responses.

- [ ] **Step 4: Wire the page's single URL-backed selection**

Read `period` from the existing `useSearchParams` pair, normalize it with the helper,
and include it in every TanStack query key and API query. On change update `period`
and clear `page` plus `eventsPage`.

- [ ] **Step 5: Add a failing page test for synchronized requests**

Mock all analytics API methods, select `LAST_7_DAYS`, and assert every call contains
`period=LAST_7_DAYS` and both paginations reset to zero.

- [ ] **Step 6: Run focused frontend tests and verify GREEN**

Run: `yarn test analytics-period downtime-analytics-page`
Expected: PASS with zero failures.

- [ ] **Step 7: Commit frontend request plumbing**

```bash
git add src/types/api.ts src/modules/analytics/libs/downtime-analytics/analytics-period.ts \
  src/modules/analytics/libs/downtime-analytics/tests/analytics-period.test.ts \
  src/modules/analytics/pages/downtime-analytics-page.tsx \
  src/modules/analytics/pages/downtime-analytics-page.test.tsx
git commit -m "feat: synchronize analytics period queries"
```

### Task 5: Build the reusable context badge and tooltip

**Interfaces:**
- Produces: `<AnalyticsContext context sourcesOverride? compact? />`.
- Consumes: exact backend `AnalyticsContext` and i18n keys only.

- [ ] **Step 1: Write failing component tests**

Render a 30-day department context and assert the visible text contains `30 дней ·
Цех № 1`; open the info tooltip and assert exact dates, translated sources, timezone,
and update time. Render `undefined` and assert `Контекст недоступен`.

- [ ] **Step 2: Run component test and verify RED**

Run: `yarn test analytics-context`
Expected: FAIL because the component and translations are absent.

- [ ] **Step 3: Implement the badge with the existing Radix tooltip wrapper**

Use `formatDate()` for timestamps, semantic button labeling for the info icon, and
wrapping source chips. Do not calculate `from`/`to` in the browser.

- [ ] **Step 4: Add exact keys to all locales**

Add period labels, `updatedAt`, `department`, `allAuthorized`, `sources`, source-code
labels, `contextUnavailable`, and the info-button accessible label under
`downtimeAnalytics.context` in `ru.json`, `uz.json`, and `en.json`.

- [ ] **Step 5: Verify component and locale parity GREEN**

Run: `yarn test analytics-context && yarn i18n:check`
Expected: both commands exit 0.

- [ ] **Step 6: Commit the shared UI**

```bash
git add src/modules/analytics/components/downtime-analytics/analytics-context.tsx \
  src/modules/analytics/components/downtime-analytics/analytics-context.test.tsx \
  src/i18n/locales/ru.json src/i18n/locales/uz.json src/i18n/locales/en.json
git commit -m "feat: show analytics calculation context"
```

### Task 6: Put selector and context on every analytics block

**Interfaces:**
- Consumes: the period state from Task 4 and `AnalyticsContext` from Task 5.
- Produces: one page selector and context annotations for toolbar stats, overview
  tiles, charts, RCA/predictive content, and both table groups.

- [ ] **Step 1: Extend the page test with coverage for every section**

Assert the period selector has three options, all four toolbar stats share the
overview context, both chart headers show their endpoint context, RCA shows the RCA
context, and table headers show reliability/events context.

- [ ] **Step 2: Run the page test and verify RED**

Run: `yarn test downtime-analytics-page`
Expected: FAIL because sections do not accept/render context.

- [ ] **Step 3: Add the selector to `PageToolbar.filters`**

Use the existing `Select` component with explicit label. Preserve the current tabs
and department filter state while updating only period/pagination URL keys.

- [ ] **Step 4: Thread context through focused section props**

Update `OverviewInsights`, `ChartsSection`, `RcaSection`, and `TablesSection` with
typed context props. Place one context component at each visual block header; avoid
duplicating the tooltip markup.

- [ ] **Step 5: Add loading and missing-context behavior**

Reserve badge space while each endpoint loads. If one endpoint fails, preserve the
existing page error behavior; if only metadata is absent, render the explicit
unavailable label.

- [ ] **Step 6: Run the analytics frontend tests and verify GREEN**

Run: `yarn test src/modules/analytics`
Expected: PASS with zero failures.

- [ ] **Step 7: Commit page integration**

```bash
git add src/modules/analytics/pages/downtime-analytics-page.tsx \
  src/modules/analytics/pages/downtime-analytics-page.test.tsx \
  src/modules/analytics/components/downtime-analytics
git commit -m "feat: apply period context across analytics"
```

### Task 7: Cross-repository verification

- [ ] **Step 1: Run focused backend verification**

Run: `./mvnw -Dtest=AnalyticsContextServiceTest,AnalyticsServiceTest,ReliabilityPassportServiceTest,AnalyticsControllerContractTest,AnalyticsPbacScopeTest test`
Expected: exit 0 and zero failures.

- [ ] **Step 2: Run the full frontend quality gate**

Run: `yarn test && yarn lint && yarn i18n:check && yarn build`
Expected: every command exits 0 with no test, lint, locale, type, or build failures.

- [ ] **Step 3: Inspect only intended diffs**

Run in each repository: `git status --short`, `git diff --check`, and
`git diff --stat`. Confirm unrelated pre-existing files remain untouched and no
generated output is staged.

- [ ] **Step 4: Record manual acceptance evidence**

Open `/analytics/downtime`, switch among all three periods, reload/back/forward, and
verify every card/chart/table shows the same selected period, the authorized
department, correct sources, and a calculation timestamp.
