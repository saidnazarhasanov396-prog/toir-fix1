# Finance Backend Implementation Report

Sana: 2026-06-29

Branch: `finance-branch`

Repo: `toir-backend`

## Qisqa xulosa

Finance backend bo'yicha budget lifecycle, actual cost allocation/correction, finance dashboard/reporting, export permissionlari, audit timeline va Firebase secret hardening ishlari qilindi. O'zgarishlar backend API contract, DB migration, RBAC/PBAC nazorati va test coverage bilan birga tayyorlandi.

## Qilingan ishlar

### 1. Budget lifecycle kengaytirildi

- `BudgetStatus` endi quyidagi holatlarni qo'llaydi: `DRAFT`, `SUBMITTED`, `APPROVED`, `LOCKED`, `CLOSED`, `REJECTED`.
- `MaintenanceBudgetController` ga yangi command endpointlar qo'shildi:
  - `POST /api/v1/budgets/{id}/submit`
  - `POST /api/v1/budgets/{id}/approve`
  - `POST /api/v1/budgets/{id}/reject`
  - `POST /api/v1/budgets/{id}/lock`
  - `POST /api/v1/budgets/{id}/close`
  - `POST /api/v1/budgets/{id}/reopen`
  - `POST /api/v1/budgets/{id}/transfer`
  - `POST /api/v1/budgets/{id}/revise`
- `MaintenanceBudgetService` da status transition qoidalari qo'shildi:
  - faqat `DRAFT` yoki `REJECTED` budget submit qilinadi;
  - faqat `SUBMITTED` budget approve/reject qilinadi;
  - faqat `APPROVED` budget lock qilinadi;
  - faqat `APPROVED` yoki `LOCKED` budget close qilinadi;
  - faqat `CLOSED` budget reopen qilinadi;
  - reject va reopen uchun comment majburiy.
- Budget line transfer va revise flowlari qo'shildi. Planned amount approved actual amountdan pastga tushmasligi tekshiriladi.
- `MaintenanceBudgetApprovalHandler` eski `DRAFT -> APPROVED` finalization o'rniga `SUBMITTED -> APPROVED` lifecycle bilan moslashtirildi.

### 2. Budget audit timeline qo'shildi

- Yangi `BudgetEvent` entity va `BudgetEventRepository` qo'shildi.
- Budget status, transfer va revision amallari `budget_events` jadvaliga event sifatida yoziladi.
- Eventlarda `budgetId`, `budgetLineId`, `eventType`, `oldValues`, `newValues`, `actorUserId`, `comment`, `occurredAt` saqlanadi.

### 3. Actual cost allocation va correction flowlari qo'shildi

- `ActualCost` entity va `ActualCostDto` quyidagi allocation/correction maydonlari bilan kengaytirildi:
  - `correctionReason`
  - `allocationComment`
  - `allocatedById`
  - `allocatedAt`
- Yangi request DTOlar qo'shildi:
  - `ActualCostAllocationRequest`
  - `ActualCostCorrectionRequest`
- `ActualCostController` ga yangi endpointlar qo'shildi:
  - `POST /api/v1/actual-costs/{id}/allocate-budget-line`
  - `POST /api/v1/actual-costs/{id}/request-correction`
- `ActualCostService` allocation vaqtida:
  - comment majburiyligini tekshiradi;
  - faqat `PENDING` yoki `REJECTED` actual costlarni allocate qilishga ruxsat beradi;
  - budget line statusi, cost category va department mosligini tekshiradi;
  - allocation metadata va timeline event yozadi.
- Correction request actual costni `REJECTED` holatiga o'tkazadi, correction reason va review event yozadi.
- Actual cost reject endpointi endi conflict qaytarmaydi; comment bilan `service.review(..., false, ...)` orqali reject qiladi.

### 4. Actual cost allocation audit qo'shildi

- Yangi `ActualCostAllocationEvent` entity va `ActualCostAllocationEventRepository` qo'shildi.
- Allocation paytida eski va yangi budget line, actor, comment va vaqt alohida event sifatida saqlanadi.
- Review event logiga `ALLOCATION/ALLOCATED` va `REVIEW/CORRECTION_REQUESTED` eventlari yoziladi.

### 5. Finance dashboard va report API qo'shildi

- Yangi `FinanceReportController` va `FinanceReportService` qo'shildi.
- Yangi response DTOlar qo'shildi:
  - `FinanceDashboardResponse`
  - `FinanceReportRow`
- API endpointlar:
  - `GET /api/v1/budgets/summary/dashboard`
  - `GET /api/v1/budgets/reports/plan-vs-actual-by-department`
  - `GET /api/v1/budgets/reports/plan-vs-actual-by-category`
  - `GET /api/v1/budgets/reports/plan-vs-actual-by-department/export`
  - `GET /api/v1/budgets/reports/plan-vs-actual-by-category/export`
- Dashboard va reportlarda quyidagi metriclar hisoblanadi:
  - planned amount;
  - approved, pending va rejected actual amount;
  - remaining budget;
  - forecast remaining;
  - variance;
  - burn rate;
  - risk amount;
  - unallocated amount;
  - actual cost count.
- Reportlar department va category bo'yicha group qilinadi, `year`, `month`, `departmentId` filterlari qo'llab-quvvatlanadi.

### 6. Review queue/register allocation filterlari qo'shildi

- `ActualCostReviewItem` allocation metadata bilan boyitildi:
  - `allocationStatus`
  - `unallocated`
  - `allocationComment`
  - `allocatedAt`
  - `allocatedById`
- `BudgetSummaryController` actual cost register va review queue endpointlariga `allocationStatus` filteri qo'shildi.
- `ActualCostReviewActionController` export endpointlariga `allocationStatus` filteri qo'shildi.
- CSV export ustunlariga allocation status, budget line, allocated at/by maydonlari qo'shildi.

### 7. Permission va role seed yangilandi

- `PermissionConstants` ga yangi permissionlar qo'shildi:
  - `BUDGET_CLOSE`
  - `BUDGET_REOPEN`
  - `BUDGET_TRANSFER`
  - `BUDGET_REVISE`
  - `ACTUAL_COST_ALLOCATE`
  - `ACTUAL_COST_REQUEST_CORRECTION`
  - `FINANCE_REPORT_EXPORT`
- `V20260519_3__seed_granular_role_permissions.sql` da `ECONOMIST` va `FINANCE_MANAGER` rollari finance flow uchun kerakli permissionlar bilan kengaytirildi.
- Review/export endpointlari `FINANCE_REPORT_EXPORT` bilan himoyalandi.
- Mavjud RBAC/PBAC testlari yangi permission contract bilan moslashtirildi.

### 8. DB migration qo'shildi

- `V20260627_10__finance_backend_controls.sql` migration qo'shildi.
- Migration quyidagilarni bajaradi:
  - `maintenance_budgets.status` check constraintini yangi lifecycle statuslari bilan yangilaydi;
  - `actual_costs` jadvaliga correction/allocation metadata ustunlarini qo'shadi;
  - `budget_events` jadvalini yaratadi;
  - `actual_cost_allocation_events` jadvalini yaratadi;
  - event lookup uchun indexlar qo'shadi.

### 9. Repair campaign close precheck kuchaytirildi

- `RepairCampaignService` close flowida approved yoki pending actual costlar budget linega allocate qilinmagan bo'lsa campaign close qilish bloklanadi.
- Pending actual costlar uchun alohida close blokirovkasi saqlab qolindi.

### 10. Firebase secret hardening qilindi

- `firebase-service-account.json` resourcedan olib tashlandi va repo ichiga secret commit qilmaslik qoidasi READMEda yozildi.
- Firebase konfiguratsiyasi runtime env orqali boshqariladigan qilindi:
  - `APP_FIREBASE_ENABLED`
  - `APP_FIREBASE_PROJECT_ID`
  - `APP_FIREBASE_SERVICE_ACCOUNT_FILE`
  - `APP_FIREBASE_SERVICE_ACCOUNT_JSON`
  - `APP_FIREBASE_SERVICE_ACCOUNT_BASE64`
- `FirebaseConfig` credentialsni file, raw JSON yoki base64 orqali o'qiydi.
- `FirebaseStartupStatusLogger` startup diagnostics loglarini kengaytiradi.
- GitLab deploy job Firebase envlarni containerga uzatadi va Firebase yoqilgan bo'lsa credentials mavjudligini tekshiradi.

## Test coverage

Quyidagi testlar qo'shildi yoki yangilandi:

- `MaintenanceBudgetLifecycleServiceTest` - budget submit/approve/lock/close/reopen, transfer va revise qoidalari.
- `ActualCostServiceTest` - allocation, correction, category/department validation va event yozilishi.
- `FinanceReportServiceTest` - dashboard, department/category report va CSV metriclari.
- `RepairCampaignServiceTest` - unallocated actual cost bilan campaign close bloklanishi.
- `ActualCostControllerContractTest` - allocation va correction endpoint contractlari.
- `BudgetSummaryControllerContractTest` - allocationStatus filter contracti.
- `FinanceBackendControlsMigrationContractTest` - finance migration SQL contracti.
- `FinanceEventRepositoryContractTest` - budget/allocation event repository lookup contractlari.
- `RbacActualCostSecurityTest`, `RbacBudgetSecurityTest`, `RolePermissionMatrixTest` - yangi permissionlar va access control.
- `ActualCostPbacScopeTest`, `ApprovalPbacScopeTest`, `BudgetPbacScopeTest` - PBAC/scope compatibility.
- `FirebaseConfigTest`, `ProductionConfigSecretsTest` - Firebase secret runtime configuration va deploy env contractlari.

## Frontend uchun contract eslatmalari

- Budget status lifecycle endi `SUBMITTED` va `REJECTED` holatlarini ham ko'rsatishi kerak.
- Budget action buttonlar backend permissionlarga mos bo'lishi kerak: submit, approve, reject, lock, close, reopen, transfer, revise.
- Actual cost review/register ekranlarida `allocationStatus` filteri va allocation metadata ko'rsatilishi mumkin.
- Actual cost detail/actions uchun allocate budget line va request correction flowlari backendda tayyor.
- Finance dashboard va plan-vs-actual reportlar department/category kesimida JSON va CSV export bilan tayyor.

## Deploy eslatmalari

- Firebase push default holatda o'chirilgan: `APP_FIREBASE_ENABLED=false`.
- Productionda push kerak bo'lsa Firebase project ID va service account runtime secret sifatida berilishi kerak.
- Repo ichida Firebase service-account JSON saqlanmaydi.
