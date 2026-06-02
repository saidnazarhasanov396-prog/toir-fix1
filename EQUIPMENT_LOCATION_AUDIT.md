# Equipment Location Audit

## 1. Qisqa xulosa

Backend hozir uskunaning joriy joylashuvini asosan `equipment.department_id`, `equipment.location_id` va ombor uchun alohida `warehouse_equipment_items` jadvali orqali yuritadi. `OUTSIDE_FACILITY`, tashqi sabab, kim olib ketgani, qabul qiluvchi user, boshlanish/qaytish sanalari yoki tashqi manzil uchun model, enum, DTO, endpoint va migratsiya topilmadi.

Eng katta risk: `department_id = null` bo'lgan uskuna non-admin foydalanuvchilar uchun ko'p joyda ko'rinmaydi yoki 403 beradi. Omborda turgan uskuna ham `department_id = null` bo'ladi, scope qoidalari esa ko'p endpointlarda faqat `departmentId`ga qaraydi. `OUTSIDE_FACILITY` qo'shilsa, mas'ul department yoki ownership alohida saqlanmasa PBAC noaniq bo'ladi.

Tavsiya: bu loyiha uchun eng xavfsiz yo'l - `Equipment`da joriy joylashuv maydonlari + alohida `EquipmentLocationHistory` jadvali. Bu mavjud list/detail API'larini kamroq buzadi, joriy holatni tez o'qish imkonini beradi va transfer audit/history talabini qoplaydi.

## 2. Hozirgi backend holati

- Joriy branch audit oldidan tekshirildi: `audit/equipment-location`.
- Working tree audit oldidan toza edi.
- Oxirgi commit: `53a133c Merge branch 'Sardor' into 'main'`.
- Kod implementatsiya qilinmadi; faqat audit markdown fayli yaratildi.

Asosiy topilgan surface:

- `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/entity/equipment/Equipment.java`
- `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/dto/equipment/EquipmentCreateRequest.java`
- `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/dto/equipment/EquipmentUpdateRequest.java`
- `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/dto/equipment/EquipmentDto.java`
- `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/dto/equipment/EquipmentPlacementRequest.java`
- `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/controller/equipment/EquipmentController.java`
- `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/service/equipment/EquipmentService.java`
- `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/entity/warehouse/WarehouseEquipmentItem.java`
- `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/service/WarehouseEquipmentItemService.java`
- `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/repository/equipment/EquipmentRepository.java`
- `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/repository/WarehouseEquipmentItemRepository.java`
- `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/security/ScopeAccessService.java`
- `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/util/AuditBuilderService.java`
- `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/resources/db/migration/B20260523_7__schema_baseline.sql`
- `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/resources/db/migration/V20260507_1__warehouse_equipment_items.sql`
- `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/resources/db/migration/V20260513_1__equipment_department_nullable.sql`

## 3. Current data model audit

### Finding: Equipment faqat department/location maydonlarini saqlaydi

- File path: `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/entity/equipment/Equipment.java`
- Current behavior: `Equipment` entityda `departmentId` va `locationId` bor. `warehouseId`, `currentLocationType`, `outsideReason`, `outsideTakenBy`, `outsideRecipientUserId`, `outsideStartedDate`, `outsideExpectedReturnDate`, `outsideDestination`, `outsideReasonNote` topilmadi.
- Problem/risk: Joriy joylashuv turi aniq emas. `locationId` ba'zan haqiqiy `locations.id`, ba'zan `warehouses.id` sifatida ishlatiladi. `OUTSIDE_FACILITY`ni xavfsiz qo'shish uchun semantik joy yetarli emas.
- Recommended fix: `EquipmentLocationType` va joriy joylashuv maydonlarini aniq modelga ajratish; `locationId`ni faqat fizik location uchun qoldirish yoki migratsiya bilan ma'nosini aniqlashtirish.
- Priority: HIGH

### Finding: Warehouse joriy holati Equipment ichida emas, alohida active assignmentda

- File path: `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/entity/warehouse/WarehouseEquipmentItem.java`
- Current behavior: `warehouse_equipment_items` jadvalida `warehouseId`, `equipmentId`, `status`, `active` saqlanadi. `EquipmentDto.PlacementRef` shu active assignmentdan `WAREHOUSE` placementni chiqaradi.
- Problem/risk: `Equipment`da warehouse FK yo'q. `departmentId = null` + active warehouse item kombinatsiyasi orqali ombor holati aniqlanadi. Tashqi placement uchun shunga o'xshash active state jadvali yo'q.
- Recommended fix: Joriy location state bitta authoritative modelga keltirilsin; warehouse assignment bilan `Equipment.currentLocationType` yoki history jadvali transactional bog'lansin.
- Priority: HIGH

### Finding: Department va warehouse bir vaqtda mavjud bo'lishi mumkin bo'lgan drift bor

- File path: `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/service/equipment/EquipmentService.java`
- Current behavior: `placementRef(...)` `departmentId != null` bo'lsa `PlacementType.DEPARTMENT` qaytaradi, lekin active `WarehouseEquipmentItem` bo'lsa warehouse metadata ham qo'shadi. Testlarda `equipmentWithDepartmentAndActiveNonInstalledWarehouseItemDoesNot500` bunday driftni barqaror ko'rsatadi.
- Problem/risk: "exactly one active location" invariant yo'q. Uskuna departmentda ham, active warehouse itemda ham ko'rinishi mumkin.
- Recommended fix: DB check/partial unique invariant va service validation qo'shish: `DEPARTMENT` bo'lsa active warehouse item bo'lmasin yoki status faqat `INSTALLED` bo'lsin; `WAREHOUSE` bo'lsa `departmentId` null bo'lsin.
- Priority: HIGH

### Finding: Ikkala location null holati mavjud va UNKNOWN sifatida qaytadi

- File path: `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/service/equipment/EquipmentService.java`
- Current behavior: `placementRef(...)` `departmentId == null` va active warehouse item topilmasa `PlacementType.UNKNOWN` qaytaradi.
- Problem/risk: `UNKNOWN` real biznes holat emas. `OUTSIDE_FACILITY` qo'shilganda null/null qatorlar tashqi joylashuvmi, eski data driftmi yoki xato ekanini ajratib bo'lmaydi.
- Recommended fix: Migratsiya/backfill strategiyasi bilan mavjud null/null qatorlar alohida aniqlansin; yangi API uchun `locationType` required bo'lsin.
- Priority: HIGH

### Finding: Transfer/movement history jadvali topilmadi

- File path: `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/resources/db/migration`
- Current behavior: `equipment_status_history` bor, lekin `equipment_location_history`, `equipment_transfer_history`, `equipment_movements` topilmadi. `warehouse_equipment_items` eski assignmentni soft-delete qiladi, lekin from/to, changedBy, reason/note bilan to'liq movement history emas.
- Problem/risk: Oldingi joylashuvlar audit uchun ishonchli saqlanmaydi; soft-deleted warehouse row transfer sababini, actorni va tashqi ma'lumotlarni bermaydi.
- Recommended fix: `EquipmentLocationHistory` jadvali qo'shish va barcha create/update/placement transferlarda yozish.
- Priority: HIGH

### Finding: Audit log bor, lekin placement transferida ishlatilmaydi

- File path: `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/service/equipment/EquipmentService.java`
- Current behavior: `create`, `update`, `delete` audit log yozadi. `updatePlacement`, `moveToWarehouse`, `moveToDepartment` ichida `auditBuilderService.log(...)` topilmadi.
- Problem/risk: Location change actor va diff audit logda yo'qoladi yoki faqat ombor service side-effectlari orqali bilvosita qoladi.
- Recommended fix: Placement update atomic transaction ichida audit log va history yozsin.
- Priority: HIGH

## 4. API endpoint audit

### Endpoint: Create equipment

- HTTP method: `POST`
- URL/path: `/api/v1/equipment`
- Controller method: `EquipmentController.create(...)`
- Request DTO: `EquipmentCreateRequest`
- Response DTO: `EquipmentDto`
- Location fields accepted: `departmentId`, `warehouseId`, `locationId`.
- Location fields returned: `departmentId`, `locationId`, `department`, `location`, `placement.type`, `placement.department`, `placement.warehouse`, `placement.warehouseStatus`, `placement.location`.
- Validation behavior: `name`, `inventoryNumber`, `equipmentTypeId`, `averageOperatingLifeHours` bean validation; service `departmentId or warehouseId is required`; department/warehouse existence checked; code clientdan berilsa reject; inventory number unique.
- Outside facility fields: DTOda yo'q. Global `FAIL_ON_UNKNOWN_PROPERTIES` konfiguratsiyasi topilmadi; amaldagi Spring/Jackson default bo'yicha unknown JSON fields ignore bo'lishi ehtimol. Demak frontend yuborgan `outsideReason`, `outsideTakenBy` va boshqalar saqlanmaydi va responsega qaytmaydi.

### Finding: Create equipment `departmentId` va `warehouseId`ni birga yuborishni taqiqlamaydi

- File path: `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/service/equipment/EquipmentService.java`
- Current behavior: `validateCreatePlacement(...)` faqat ikkalasi ham null bo'lsa xato beradi. Ikkalasi to'ldirilgan holat reject qilinmaydi.
- Problem/risk: Equipment `departmentId` bilan saqlanadi va shu bilan birga `warehouseEquipmentItemService.assign(...)` chaqiriladi; assignment `departmentId`ni null qiladi, lekin create audit snapshot va response enrichmentda noaniq/drift risk bor.
- Recommended fix: Create uchun `locationType` required bo'lsin va aynan bitta target qoidasini validate qilsin.
- Priority: HIGH

### Endpoint: Update equipment

- HTTP method: `PUT`
- URL/path: `/api/v1/equipment/{id}`
- Controller method: `EquipmentController.update(...)`
- Request DTO: `EquipmentUpdateRequest`
- Response DTO: `EquipmentDto`
- Location fields accepted: `departmentId`, `locationId`. `warehouseId` qabul qilinmaydi.
- Location fields returned: `EquipmentDto`dagi joriy location/placement maydonlari.
- Validation behavior: mavjud equipment department scope tekshiriladi; target `departmentId` bo'lsa scope va existence tekshiriladi; statusni direct o'zgartirish reject qilinadi; null update fieldlar ko'p joyda "o'zgartirmaslik" sifatida ishlaydi.
- Outside facility fields: DTOda yo'q; qabul qilinsa saqlanmaydi.

### Finding: Update orqali locationni nullga clear qilib bo'lmaydi

- File path: `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/service/equipment/EquipmentService.java`
- Current behavior: `applyForUpdate(...)` `departmentId` va `locationId` null bo'lsa eski qiymatni qoldiradi.
- Problem/risk: Return/move/outside kabi holatlarda "oldingi locationni tozalash" aniq ifodalanmaydi. Null `no-op`mi yoki clear requestmi, farqlanmaydi.
- Recommended fix: Dedicated location payload ishlatish; clear semantics faqat `locationType` asosida service tomonidan bajarilsin.
- Priority: HIGH

### Endpoint: Get equipment detail

- HTTP method: `GET`
- URL/path: `/api/v1/equipment/{id}`
- Controller method: `EquipmentController.get(...)`
- Request DTO: topilmadi
- Response DTO: `EquipmentDetailDto`
- Location fields accepted: topilmadi
- Location fields returned: nested `EquipmentDto` orqali `departmentId`, `locationId`, `placement`.
- Validation behavior: `assertCanAccessEquipment(...)`; `departmentId == null` bo'lsa non-admin 403.
- Outside facility fields: response payloadda topilmadi.

### Endpoint: List equipment

- HTTP method: `GET`
- URL/path: `/api/v1/equipment`
- Controller method: `EquipmentController.list(...)`
- Request DTO: query params
- Response DTO: `Page<EquipmentDto>`
- Location fields accepted: `departmentId`, `warehouseId` faqat `availableForReplacement=true` queryda ishlatiladi.
- Location fields returned: `EquipmentDto` placement.
- Validation behavior: non-admin uchun department scope clamp qilinadi; current department yo'q bo'lsa 403.
- Outside facility fields: topilmadi.

### Finding: Listda `warehouseId` umumiy warehouse filter emas

- File path: `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/service/equipment/EquipmentService.java`
- Current behavior: `warehouseId` faqat `availableForReplacement=true` bo'lganda ishlatiladi. Oddiy list `repository.search(...)`da warehouse filter yo'q.
- Problem/risk: Ombordagi yoki kelajakdagi outside equipmentni filterlash uchun mavjud API yetarli emas; frontend `warehouseId` yuborsa, `availableForReplacement=false` holatda e'tiborsiz qoladi.
- Recommended fix: Location filter contractni qayta belgilash: `locationType`, `departmentId`, `warehouseId`, `outsideReason`, `overdueOnly`.
- Priority: MEDIUM

### Endpoint: Transfer/update placement

- HTTP method: `PATCH`
- URL/path: `/api/v1/equipment/{id}/placement`
- Controller method: `EquipmentController.updatePlacement(...)`
- Request DTO: `EquipmentPlacementRequest`
- Response DTO: `EquipmentDto`
- Location fields accepted: `targetType`, `warehouseId`, `departmentId`, `warehouseStatus`.
- Location fields returned: `EquipmentDto` placement.
- Validation behavior: `targetType` required; `WAREHOUSE` uchun `warehouseId` required va `warehouseStatus` faqat `AVAILABLE` yoki `OUT_OF_SERVICE`; `DEPARTMENT` uchun `departmentId` required; `warehouseId` va `departmentId` birga reject qilinadi.
- Outside facility fields: `PlacementTargetType` faqat `WAREHOUSE`, `DEPARTMENT`; `OUTSIDE_FACILITY` reject bo'ladi.

### Endpoint: Warehouse equipment list/unassign

- HTTP method: `GET`
- URL/path: `/api/v1/warehouses/{warehouseId}/equipment`
- Controller method: `WarehouseController.listEquipment(...)`
- Request DTO: query params
- Response DTO: `Page<WarehouseEquipmentItemDto>`
- Location fields accepted: `warehouseId`, optional `status`.
- Location fields returned: `warehouseId`, `equipmentId`, `status`, `active`.
- Outside facility fields: topilmadi.

- HTTP method: `DELETE`
- URL/path: `/api/v1/warehouses/{warehouseId}/equipment/{equipmentId}`
- Controller method: `WarehouseController.unassignEquipment(...)`
- Request DTO: topilmadi
- Response DTO: empty
- Location fields accepted: path `warehouseId`, `equipmentId`.
- Validation behavior: item soft-deleted/inactive qilinadi; `Equipment.locationId`/`departmentId` sync qilinmaydi.
- Outside facility fields: topilmadi.

### Endpoint: Vehicle create/update/list/detail

- HTTP method: `POST`, `PUT`, `GET`
- URL/path: `/api/v1/vehicles`, `/api/v1/vehicles/{equipmentId}`
- Controller method: `VehicleController.create(...)`, `VehicleController.update(...)`, `VehicleController.list(...)`, `VehicleController.get(...)`
- Request DTO: `VehicleRequest`
- Response DTO: `VehicleDetailDto`, `VehicleSummaryDto`
- Location fields accepted: `departmentId`, `locationId`; `VehicleRequest.departmentId` `@NotNull`.
- Location fields returned: nested `EquipmentDto`.
- Validation behavior: vehicle creation requires department; warehouse/outside create not supported.
- Outside facility fields: topilmadi.

### Endpoint: Label/scan

- HTTP method: `GET`
- URL/path: `/api/v1/equipment/{id}/label`, `/api/v1/equipment/by-code/{code}`, `/api/v1/equipment/resolve-scan`, `/api/v1/equipment/{id}/label.svg`, `/api/v1/scan/equipment/{id}`
- Controller method: `EquipmentLabelController`, `EquipmentScanCompatibilityController`
- Request DTO: topilmadi
- Response DTO: `EquipmentLabelResponse`
- Location fields returned: `departmentId`, `locationId`.
- Outside facility fields: topilmadi.

### Import/export audit

- Equipment import endpoint: topilmadi.
- Equipment location export/report endpoint: topilmadi.
- Equipment document download bor, lekin location import/export emas.

## 5. Service logic audit

### Finding: Create department yoki warehouse talab qiladi, outside yo'q

- File path: `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/service/equipment/EquipmentService.java`
- Current behavior: `validateCreatePlacement(departmentId, warehouseId)` kamida bittasini talab qiladi.
- Problem/risk: Uskunani bevosita `OUTSIDE_FACILITY` sifatida yaratib bo'lmaydi.
- Recommended fix: `locationType` asosida create validation: `DEPARTMENT`, `WAREHOUSE`, `OUTSIDE_FACILITY`.
- Priority: HIGH

### Finding: Department -> Warehouse transfer transactional, lekin history/audit yo'q

- File path: `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/service/equipment/EquipmentService.java`
- Current behavior: `updatePlacement(...)` `@Transactional`; `moveToWarehouse(...)` warehouse service orqali active item yaratadi/ko'chiradi, `equipment.departmentId = null`, `locationId = warehouseId`.
- Problem/risk: Atomic update bor, lekin `changedBy`, `from`, `to`, reason/note saqlanmaydi.
- Recommended fix: Shu transaction ichida `EquipmentLocationHistory` va audit log yozish.
- Priority: HIGH

### Finding: Warehouse -> Department transfer active warehouse assignmentni o'chirmaydi

- File path: `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/service/equipment/EquipmentService.java`
- Current behavior: `moveToDepartment(...)` active warehouse itemni `INSTALLED` statusga o'zgartiradi, equipment `departmentId`ni set qiladi, `locationId` warehouseIdga teng bo'lsa null qiladi.
- Problem/risk: Active warehouse item qoladi. Bu "ombordan install qilingan" metadata sifatida ishlatilishi mumkin, lekin "exactly one active location" uchun noaniq.
- Recommended fix: Agar warehouse item ownership/history sifatida qolishi kerak bo'lsa, nom/model aniq ajratilsin; aks holda transfer historyga ko'chirilib active item yopilsin.
- Priority: MEDIUM

### Finding: Warehouse assignment service Equipmentni ham o'zgartiradi

- File path: `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/service/WarehouseEquipmentItemService.java`
- Current behavior: `assign(...)` va `transferEquipmentToWarehouse(...)` `Equipment.departmentId`ni null qiladi; `transferEquipmentToWarehouse(...)` `locationId = targetWarehouseId` qiladi.
- Problem/risk: Joylashuv invariantlari bir nechta serviceda tarqalgan. Kelajakda outside location qo'shilsa side-effectlar ko'payadi.
- Recommended fix: Location transition uchun bitta domain service yaratilishi yoki `EquipmentService` authoritative bo'lishi kerak.
- Priority: MEDIUM

## 6. Repository/query audit

### Finding: Equipment search faqat department filterga tayanadi

- File path: `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/repository/equipment/EquipmentRepository.java`
- Current behavior: `search(...)` va `getEquipmentStats(...)` `(:departmentId is null or e.departmentId = :departmentId)` sharti bilan ishlaydi.
- Problem/risk: `OUTSIDE_FACILITY`da `departmentId` null bo'lsa non-admin list/statlarda yo'qoladi. Agar `responsibleDepartmentId` qo'shilmasa scope buziladi.
- Recommended fix: Querylar `currentLocationType` va `responsibleDepartmentId`/`owningDepartmentId`ni inobatga olsin.
- Priority: HIGH

### Finding: Warehouse replacement query department va warehouse scope aralashmasiga tayanadi

- File path: `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/repository/equipment/EquipmentRepository.java`
- Current behavior: `searchAvailableForReplacement(...)` active `WarehouseEquipmentItem` existence orqali warehouse filter qiladi, lekin qo'shimcha department filter optional.
- Problem/risk: Outside equipment uchun replacement eligibility aniq emas; null department yoki outside states queryga noto'g'ri kirishi/chiqishi mumkin.
- Recommended fix: Replacement queryga explicit locationType va eligible statuses qo'shilsin.
- Priority: MEDIUM

### Finding: WarehouseEquipmentItemRepository faqat active assignmentni oladi

- File path: `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/repository/WarehouseEquipmentItemRepository.java`
- Current behavior: `findActiveByEquipmentId(...)`, `findActiveByEquipmentIds(...)` faqat `active=true AND is_deleted=false`.
- Problem/risk: Old warehouse assignmentlar history sifatida ishonchli query qilinmaydi. Soft-delete qilingan qatorlar domain history emas.
- Recommended fix: Active assignment alohida, immutable history alohida jadval bo'lsin.
- Priority: MEDIUM

## 7. Validation muammolari

### Finding: Exactly one active location validation to'liq emas

- File path: `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/service/equipment/EquipmentService.java`
- Current behavior: Placement endpoint department/warehouse targetni birga yuborishni reject qiladi, lekin create endpoint buni to'liq reject qilmaydi; DB check constraint topilmadi.
- Problem/risk: Noto'g'ri kombinatsiyalar saqlanishi mumkin.
- Recommended fix: Service va DB darajasida `locationType`ga mos exactly-one validation.
- Priority: HIGH

### Finding: Outside facility validationlari topilmadi

- File path: `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/dto/equipment`
- Current behavior: `outsideReason`, `outsideDestination`, `outsideStartedDate`, `outsideExpectedReturnDate`, `outsideReasonNote`, `outsideRecipientUserId` DTOlarda topilmadi.
- Problem/risk: Frontend yuboradigan outside payload backendda yo'qoladi.
- Recommended fix: Dedicated nested `EquipmentLocationRequest` yoki typed fields qo'shish; `OTHER` uchun note, expectedReturn >= startedDate, destination required kabi validationlar.
- Priority: HIGH

### Finding: Department/warehouse permission validation notekis

- File path: `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/controller/equipment/EquipmentController.java`
- Current behavior: Create faqat `departmentId` bo'lsa scope tekshiradi; warehouse-only create controllerda warehouse scope tekshirmaydi. Service `WarehouseEquipmentItemService.assign(...)` orqali warehouse scope tekshiradi.
- Problem/risk: Scope enforcement controller/service orasida notekis; future outside placementda qaysi scope ishlashi noaniq.
- Recommended fix: Location transition service ichida source va target scope bir joyda tekshirilsin.
- Priority: MEDIUM

### Finding: Empty string normalization outside payload uchun yo'q

- File path: `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/dto/equipment`
- Current behavior: Outside string fields yo'q. Mavjud equipment string fieldlar uchun umumiy trim/blank normalization ko'rinmadi.
- Problem/risk: `outsideTakenBy=""`, `outsideDestination=" "` kabi qiymatlar noto'g'ri saqlanishi mumkin.
- Recommended fix: Request normalizer yoki service validationda blank stringlarni null yoki bad request qilish.
- Priority: MEDIUM

## 8. Security / RBAC / PBAC audit

### Finding: Equipment visibility departmentId null bo'lsa non-admin uchun yopiq

- File path: `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/controller/equipment/EquipmentController.java`
- Current behavior: `assertCanAccessEquipment(...)` `departmentId == null` bo'lsa faqat scope adminni o'tkazadi.
- Problem/risk: Ombordagi yoki kelajakda outside turgan uskuna oddiy department foydalanuvchisiga ko'rinmaydi, hatto u uning mas'ul departmentiga tegishli bo'lsa ham.
- Recommended fix: `responsibleDepartmentId`/`owningDepartmentId`ni saqlash va PBACni current physical locationdan ajratish.
- Priority: HIGH

### Finding: Vehicle PBAC ham departmentId nullda non-admin uchun yopiq

- File path: `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/controller/VehicleController.java`
- Current behavior: `assertCanAccessVehicleEquipment(...)` `departmentId == null` bo'lsa non-admin 403.
- Problem/risk: Business trip, on road, service kabi vehicle outside holatlari departmentId null bo'lsa ko'rinmaydi.
- Recommended fix: Vehicle ham umumiy equipment location/ownership modelidan foydalansin.
- Priority: HIGH

### Finding: Analytics scope outside equipmentni yashiradi

- File path: `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/service/AnalyticsService.java`
- Current behavior: `assertCanAccessEquipmentAnalytics(...)` `departmentId == null` bo'lsa non-admin 403; list/filterlar `isEquipmentInDepartment(...)`ga tayanadi.
- Problem/risk: Outside facility equipment analytics non-admin uchun yo'qoladi.
- Recommended fix: Analytics scope `owningDepartmentId` yoki `responsibleDepartmentId`ga qarasin.
- Priority: MEDIUM

### Finding: RBAC permission bor, lekin outside transfer uchun alohida permission yo'q

- File path: `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/security/PermissionConstants.java`
- Current behavior: `EQUIPMENT_TRANSFER` mavjud. `EQUIPMENT_OUTSIDE_TRANSFER`, `EQUIPMENT_RETURN`, `EQUIPMENT_LOCATION_HISTORY_READ` topilmadi.
- Problem/risk: Tashqi tashkilotga berish, lost/decommissioned kabi yuqori riskli harakatlar oddiy transfer bilan bir xil permissionda qoladi.
- Recommended fix: Minimal bosqichda `EQUIPMENT_TRANSFER` bilan boshlash mumkin, lekin lost/decommissioned/status uchun alohida permission yoki status lifecycle permission ko'rib chiqilsin.
- Priority: MEDIUM

### Finding: Actor logging placementda yo'q

- File path: `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/util/AuditBuilderService.java`
- Current behavior: AuditBuilder current userni oladi, lekin placement transfer service undan foydalanmaydi.
- Problem/risk: Kim qachon joylashuvni o'zgartirgani auditdan topilmaydi.
- Recommended fix: `updatePlacement` va yangi outside transfer endpointlarida actor bilan audit/history yozish.
- Priority: HIGH

## 9. Database / migration audit

### Finding: Equipment tableda location type va outside ustunlari yo'q

- File path: `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/resources/db/migration/B20260523_7__schema_baseline.sql`
- Current behavior: `equipment` jadvalida `department_id`, `location_id`, `equipment_type_id`, status/category va asosiy identity ustunlari bor. `warehouse_id`, `current_location_type`, outside fields topilmadi.
- Problem/risk: Tashqi joylashuvni mavjud schema bilan yo'qotmasdan saqlab bo'lmaydi.
- Recommended fix: Flyway migration bilan enum/check, current fields va history jadvali qo'shish.
- Priority: HIGH

### Finding: equipment.department_id nullable qilingan

- File path: `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/resources/db/migration/V20260513_1__equipment_department_nullable.sql`
- Current behavior: `department_id DROP NOT NULL`.
- Problem/risk: Warehouse-only uchun kerak bo'lgan o'zgarish outside uchun ham texnik imkon beradi, lekin semantic ownership yo'q.
- Recommended fix: Nullable physical departmentdan tashqari `responsible_department_id` yoki `owning_department_id` qo'shish.
- Priority: HIGH

### Finding: Warehouse equipment active uniqueness bor

- File path: `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/resources/db/migration/V20260507_1__warehouse_equipment_items.sql`
- Current behavior: `uq_warehouse_equipment_items_active_equipment` bitta active equipment assignmentni majbur qiladi.
- Problem/risk: Bu faqat warehouse assignmentga tegishli; department/outside bilan cross-table invariantni himoya qilmaydi.
- Recommended fix: Joriy location bitta jadval/ustunlarda saqlansa, DB check bilan cross-location invariantni yengilroq himoya qilish mumkin.
- Priority: MEDIUM

### Migration style

- Flyway ishlatiladi: `application.yml`da `spring.flyway.enabled=true`, `locations=classpath:db/migration`.
- Naming style: `VYYYYMMDD_N__description.sql` va baseline `B20260523_7__schema_baseline.sql`.
- Existing rows migration: `department_id != null` bo'lganlar `DEPARTMENT`; active `warehouse_equipment_items` va `department_id is null` bo'lganlar `WAREHOUSE`; qolgan null/null qatorlar `UNKNOWN` audit/backfill ro'yxatiga chiqarilishi kerak.
- Backward compatibility: eski clientlar `departmentId`/`warehouseId` yuborishda davom etishi mumkin, lekin yangi `locationType` transitional davrda optional qabul qilinib server infer qilishi kerak.

## 10. Current tests audit

### Finding: Equipment placement unit tests bor, outside yo'q

- File path: `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/test/java/com/toir/service/equipment/EquipmentServiceTest.java`
- Current behavior: Department placement, warehouse placement, `UNKNOWN`, department->warehouse, warehouse->department, invalid targetType testlari bor.
- Problem/risk: Outside facility create/update/transfer, return, overdue, history testlari yo'q.
- Recommended fix: Yangi location model bilan barcha outside cases uchun service tests qo'shish.
- Priority: HIGH

### Finding: Controller contract placement unknownni tekshiradi

- File path: `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/test/java/com/toir/controller/EquipmentControllerContractTest.java`
- Current behavior: `listResponsePlacementUnknownIsStable` `placement.type=UNKNOWN` response shape saqlanishini tekshiradi.
- Problem/risk: `OUTSIDE_FACILITY` qo'shilganda response contract yangilanmasa frontend unknown ko'rishda davom etadi.
- Recommended fix: Department, warehouse, outside response contract testlari alohida yozilsin.
- Priority: MEDIUM

### Finding: PBAC tests department va warehouse bilan cheklangan

- File path: `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/test/java/com/toir/security/EquipmentPbacScopeTest.java`
- Current behavior: List department scope, detail access, create/update department scope, placement cross-department denial bor.
- Problem/risk: Outside equipment visibility, responsible department, recipient user, target warehouse/department authorization cases yo'q.
- Recommended fix: Outside visibility va transfer scope testlari qo'shilsin.
- Priority: HIGH

### Finding: Warehouse equipment PBAC testlari bor

- File path: `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/test/java/com/toir/security/WarehouseEquipmentPbacScopeTest.java`
- Current behavior: Forbidden warehouse list/assign/update/remove va equipment source department denial tekshirilgan.
- Problem/risk: Warehouse -> outside yoki outside -> warehouse uchun scope test yo'q.
- Recommended fix: Location transition service testlari orqali source and target scope coverage.
- Priority: MEDIUM

### Finding: Repository replacement query contract bor

- File path: `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/test/java/com/toir/repository/equipment/EquipmentRepositoryQueryContractTest.java`
- Current behavior: `searchAvailableForReplacement` active non-deleted warehouse item talabini tekshiradi.
- Problem/risk: Outside equipment list/filter behavior repository darajasida qoplanmagan.
- Recommended fix: `OUTSIDE_FACILITY` list inclusion/exclusion, overdue filter va department scope query tests.
- Priority: MEDIUM

## 11. Outside facility gap analysis

| Business case | Currently supported | Why | Required backend change | Priority |
| --- | --- | --- | --- | --- |
| Equipment departmentda | YES | `equipment.departmentId` va `PlacementType.DEPARTMENT` bor | Invariantni mustahkamlash | MEDIUM |
| Equipment warehouse'da | PARTIAL | Active `warehouse_equipment_items` + `departmentId=null` | Unified location model | HIGH |
| Equipment outside facility | NO | DTO/entity/enum yo'q | `OUTSIDE_FACILITY` model, migration, API | HIGH |
| Vaqtincha employee/user olgan | NO | `outsideTakenBy`, `outsideRecipientUserId` yo'q | Outside fields va user FK validation | HIGH |
| Service/repairga yuborilgan | NO | `outsideReason=SERVICE` yo'q | Outside reason enum + status mapping | HIGH |
| Rented out | NO | Reason/topshirilgan tomon yo'q | Reason enum + destination/recipient | HIGH |
| On road | NO | Vehicle uchun faqat department/location | Vehicle location model integration | HIGH |
| Expected return date | NO | Sana fieldlari yo'q | started/expectedReturn validation | HIGH |
| Return overdue | NO | Expected return yo'q | query/filter/status computed field | MEDIUM |
| Outside -> department return | NO | PlacementTargetType faqat dept/warehouse, outside source yo'q | Transfer model/history | HIGH |
| Outside -> warehouse return | NO | Outside source yo'q | Transfer model/history | HIGH |
| Outside state -> outside state | NO | Outside state yo'q | Update/transfer outside endpoint | MEDIUM |
| Missing/lost | PARTIAL | `EquipmentStatus`da `OUT_OF_SERVICE`, `DECOMMISSIONED`; `LOST` yo'q | LOST status yoki outside reason qarori | HIGH |
| Written off/decommissioned | PARTIAL | `EquipmentStatus.DECOMMISSIONED` bor | Location reason emas, status lifecycleda qolishi kerak | MEDIUM |
| Created directly outside | NO | Create department/warehouse talab qiladi | Create locationType outside support | HIGH |
| Created then transferred outside | NO | Transfer enumda outside yo'q | PlacementTargetType kengaytirish yoki yangi DTO | HIGH |

## 12. Tavsiya qilingan domain model

### Option A: Outside fieldlarni to'g'ridan-to'g'ri Equipmentga qo'shish

Example: `currentLocationType`, `departmentId`, `warehouseId`, `outsideReason`, `outsideTakenBy`, `outsideRecipientUserId`, `outsideStartedDate`, `outsideExpectedReturnDate`, `outsideDestination`, `outsideReasonNote`.

- Pros: Eng tez implementatsiya; list/detail querylar oson; DTO mapping sodda.
- Cons: History yo'q; `Equipment` entity kattalashadi; eski `warehouse_equipment_items` bilan ikki xil source paydo bo'ladi; transfer audit talabi yopilmaydi.
- Migration impact: `equipment`ga ko'p nullable ustun, check constraints, backfill kerak.
- API impact: Create/update DTOlar tez kengayadi.
- PBAC impact: `responsibleDepartmentId` qo'shilmasa null department risk saqlanadi.
- Test impact: Service/controller validation testlari ko'p qo'shiladi; history testlari bo'lmaydi.

### Option B: Alohida EquipmentLocation table

Example: active row bilan `EquipmentLocation { id, equipmentId, locationType, departmentId, warehouseId, outsideReason, outsideTakenBy, outsideRecipientUserId, outsideStartedDate, outsideExpectedReturnDate, outsideDestination, outsideReasonNote, active, createdAt, createdBy }`.

- Pros: Location domaini ajraladi; history active=false qatorlarda qisman saqlanishi mumkin; `Equipment` yengil qoladi.
- Cons: Har bir list/detail query join talab qiladi; hozirgi `EquipmentDto.from` va repository searchlar ko'p o'zgaradi; active=false history immutable bo'lmasa audit sust.
- Migration impact: Yangi jadval va backfill; existing `departmentId/locationId/warehouse_equipment_items` bilan sinxron strategiya kerak.
- API impact: Contract aniq bo'ladi, lekin response enrichment qayta yoziladi.
- PBAC impact: Scope querylari join orqali murakkablashadi.
- Test impact: Repository integration testlar ko'payadi.

### Option C: Current location Equipmentda + EquipmentLocationHistory table

Example: `Equipment`da `currentLocationType`, `currentDepartmentId` yoki mavjud `departmentId`, `currentWarehouseId`, outside fields; `EquipmentLocationHistory`da from/to snapshot, `changedBy`, `changedAt`, `note`.

- Pros: Joriy holatni tez o'qish; mavjud `EquipmentDto` va list flowga mos; history/audit talabi yopiladi; migration bosqichma-bosqich.
- Cons: `Equipment`da bir nechta location ustun paydo bo'ladi; history yozishni transactionlarda unutmaslik kerak; `warehouse_equipment_items` bilan integratsiya qarori kerak.
- Migration impact: `equipment`ga current fields + yangi history table; backfill aniq qilinadi.
- API impact: Create/update/transfer payloadlari aniq kengayadi.
- PBAC impact: `owningDepartmentId`/`responsibleDepartmentId`ni current modelga kiritish oson.
- Test impact: Service validation + history persistence tests kerak.

Tavsiya: Option C. Sabab: mavjud backend `Equipment`ni markaziy aggregate sifatida ishlatadi, list/detail response `EquipmentDto` orqali boyitiladi, placement endpoint allaqachon `EquipmentService`da. Current state Equipmentda bo'lsa frontend va repositorylar kamroq buziladi; history jadvali esa audit va transfer traceabilityni yopadi.

## 13. Tavsiya qilingan enumlar

### Finding: Existing placement enum outside uchun yetarli emas

- File path: `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/enums/PlacementTargetType.java`
- Current behavior: `WAREHOUSE`, `DEPARTMENT`.
- Problem/risk: `OUTSIDE_FACILITY` request enumda yo'q; yuborilsa bad JSON/enum error bo'ladi.
- Recommended fix: `EquipmentLocationType` alohida enum sifatida qo'shilsin: `DEPARTMENT`, `WAREHOUSE`, `OUTSIDE_FACILITY`. `PlacementTargetType`ni shu enum bilan almashtirish yoki `OUTSIDE_FACILITY` qo'shish.
- Priority: HIGH

Tavsiya qilingan enumlar:

```java
public enum EquipmentLocationType {
    DEPARTMENT,
    WAREHOUSE,
    OUTSIDE_FACILITY
}
```

```java
public enum EquipmentOutsideReason {
    BUSINESS_TRIP,
    SERVICE,
    RENTED_OUT,
    ON_ROAD,
    TEMPORARY_USE,
    EXTERNAL_ORGANIZATION,
    INSTALLATION,
    CALIBRATION,
    INSPECTION,
    OTHER
}
```

`LOST` va `DECOMMISSIONED` bo'yicha tavsiya:

- `DECOMMISSIONED` allaqachon `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/enums/EquipmentStatus.java`da status sifatida bor; location reason bo'lmasligi kerak.
- `LOST` fizik placement emas, asset lifecycle/security status. Uni `EquipmentStatus`ga alohida status sifatida qo'shish yoki `OUT_OF_SERVICE` ostida reason sifatida yuritish kerak. Agar biznes "yo'qolgan uskuna joylashuvi noma'lum" deb ko'rsa, outside reasonga qo'shish mumkin, lekin audit uchun status sifatida afzal.

## 14. Tavsiya qilingan API contract

Create/update uchun tavsiya qilingan nested payload:

```json
{
  "location": {
    "locationType": "OUTSIDE_FACILITY",
    "departmentId": null,
    "warehouseId": null,
    "outsideReason": "BUSINESS_TRIP",
    "outsideTakenBy": "Toshmat",
    "outsideRecipientUserId": null,
    "outsideStartedDate": "2026-06-02",
    "outsideExpectedReturnDate": "2026-06-10",
    "outsideDestination": "Toshkent",
    "outsideReasonNote": null
  }
}
```

Department location:

```json
{
  "location": {
    "locationType": "DEPARTMENT",
    "departmentId": "00000000-0000-0000-0000-00000000d002",
    "warehouseId": null,
    "outsideReason": null,
    "outsideDestination": null
  }
}
```

Warehouse location:

```json
{
  "location": {
    "locationType": "WAREHOUSE",
    "departmentId": null,
    "warehouseId": "00000000-0000-0000-0000-00000000w001",
    "warehouseStatus": "AVAILABLE",
    "outsideReason": null
  }
}
```

Outside facility location:

```json
{
  "location": {
    "locationType": "OUTSIDE_FACILITY",
    "departmentId": null,
    "warehouseId": null,
    "outsideReason": "SERVICE",
    "outsideTakenBy": null,
    "outsideRecipientUserId": null,
    "outsideStartedDate": "2026-06-02",
    "outsideExpectedReturnDate": "2026-06-12",
    "outsideDestination": "Servis markazi",
    "outsideReasonNote": null
  }
}
```

Transfer to outside:

```json
{
  "targetLocation": {
    "locationType": "OUTSIDE_FACILITY",
    "outsideReason": "BUSINESS_TRIP",
    "outsideTakenBy": "Toshmat",
    "outsideStartedDate": "2026-06-02",
    "outsideExpectedReturnDate": "2026-06-10",
    "outsideDestination": "Toshkent"
  },
  "note": "Audit uchun vaqtincha berildi"
}
```

Return outside -> department:

```json
{
  "targetLocation": {
    "locationType": "DEPARTMENT",
    "departmentId": "00000000-0000-0000-0000-00000000d002"
  },
  "note": "Safardan qaytdi"
}
```

Return outside -> warehouse:

```json
{
  "targetLocation": {
    "locationType": "WAREHOUSE",
    "warehouseId": "00000000-0000-0000-0000-00000000w001",
    "warehouseStatus": "AVAILABLE"
  },
  "note": "Omborga qaytarildi"
}
```

Response `EquipmentDto.placement` kengaytirilgan ko'rinish:

```json
{
  "type": "OUTSIDE_FACILITY",
  "department": null,
  "warehouse": null,
  "warehouseStatus": null,
  "location": null,
  "outsideReason": "SERVICE",
  "outsideTakenBy": null,
  "outsideRecipientUserId": null,
  "outsideStartedDate": "2026-06-02",
  "outsideExpectedReturnDate": "2026-06-12",
  "outsideDestination": "Servis markazi",
  "outsideReasonNote": null,
  "overdue": false
}
```

## 15. Backend integration rejasi

1. Domain model qarori: Option Cni tasdiqlash; `currentLocationType` va ownership/scope fieldini aniqlash.
2. Migration changes: `equipment`ga current location/outside fields, `responsible_department_id` yoki `owning_department_id`, `equipment_location_history` jadvali, index/check constraints.
3. Enum changes: `EquipmentLocationType`, `EquipmentOutsideReason`; `LOST` status qarorini alohida qilish.
4. Entity changes: `Equipment` current fields; yangi `EquipmentLocationHistory` entity/repository.
5. Request DTO changes: `EquipmentLocationRequest`, `EquipmentTransferRequest`; create/updatega backward-compatible mapping.
6. Response DTO changes: `EquipmentDto.PlacementRef` outside fields va overdue flag bilan kengaytiriladi.
7. Mapper changes: `EquipmentService.enrich(...)` location mappingni current fieldsdan oladi; warehouse fallback transitional saqlanadi.
8. Service validation changes: exactly-one target, date validation, OTHER note, destination, target existence, blank normalization.
9. Transfer API changes: `PATCH /api/v1/equipment/{id}/placement`ni kengaytirish yoki `POST /api/v1/equipment/{id}/transfers` qo'shish.
10. PBAC/RBAC changes: source equipment access, target department/warehouse access, outside recipient/responsible department access; lost/decommissioned uchun alohida permission qarori.
11. Repository/specification changes: list/search/stats filters `locationType`, `warehouseId`, `outsideReason`, `overdueOnly`, `responsibleDepartmentId` bilan kengayadi.
12. Audit/history logging changes: har create/update/transferda `EquipmentLocationHistory` va `AuditLog` yoziladi.
13. Existing data migration/backfill: department rows -> `DEPARTMENT`; active warehouse rows -> `WAREHOUSE`; null/null rows -> manual review yoki `UNKNOWN` transitional report.
14. Backend tests: quyidagi 16-bo'limdagi service/controller/security/repository tests.
15. Frontend integration notes: eski `departmentId`/`warehouseId` fieldlarini transitional davrda qo'llab, yangi `location` payloadga o'tish; unknown outside fields silently lost bo'lmasligi uchun backend release bilan birga contract yangilash.

## 16. Backend test rejasi

- Create equipment in department.
- Create equipment in warehouse.
- Create equipment outside facility.
- Update equipment department fields.
- Update equipment warehouse fields.
- Update equipment outside fields.
- Transfer department -> warehouse.
- Transfer warehouse -> department.
- Transfer department -> outside.
- Transfer warehouse -> outside.
- Transfer outside -> department.
- Transfer outside -> warehouse.
- Outside reason OTHER requires note.
- Expected return date validation.
- Department/warehouse cannot both be filled.
- Location type required.
- Target department not found.
- Target warehouse not found.
- PBAC cannot transfer to unauthorized department.
- PBAC cannot transfer to unauthorized warehouse.
- PBAC outside equipment visible by owning/responsible department.
- PBAC outside equipment hidden from unrelated department.
- Response returns correct current location.
- History/audit is saved correctly.
- List/filter behavior includes outside equipment correctly.
- Overdue outside equipment filter works.
- Vehicle create/list/detail handles outside-capable location consistently.
- Unknown old outside fields are either rejected clearly or mapped, depending on final compatibility decision.

## 17. Risklar

### Finding: Unknown outside fields silently lost bo'lishi mumkin

- File path: `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/config/JacksonConfig.java`
- Current behavior: Jackson config faqat Instant parsingni custom qiladi; unknown property reject sozlamasi topilmadi.
- Problem/risk: Frontend outside fields yuborsa, backend ularni saqlamaydi, lekin request muvaffaqiyatli ko'rinishi mumkin.
- Recommended fix: Yangi contract chiqmaguncha frontendga bu fieldlarni yubormaslik yoki backendda explicit DTO qo'shilganda contract test bilan tekshirish.
- Priority: HIGH

### Finding: DB FKlar location semanticsni himoya qilmaydi

- File path: `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/resources/db/manual/audit_fk_orphans_20260520.sql`
- Current behavior: Manual orphan audit `equipment.location_id -> locations.id` deb tekshiradi, lekin service warehouse transferda `locationId = warehouseId` qiladi.
- Problem/risk: `location_id` ba'zan warehouse id sifatida ishlatilsa FK qo'shish yoki orphan audit noto'g'ri natija beradi.
- Recommended fix: `warehouseId`ni alohida current fieldga ko'chirish; `locationId` faqat `locations` uchun ishlatilsin.
- Priority: HIGH

### Finding: Existing clients backward compatibility risk

- File path: `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/dto/equipment/EquipmentCreateRequest.java`
- Current behavior: Eski clients `departmentId`, `warehouseId`, `locationId` yuboradi.
- Problem/risk: `locationType required`ni birdan majburiy qilish eski clientlarni buzadi.
- Recommended fix: Transitional infer logic: agar `locationType` null bo'lsa `warehouseId != null` -> `WAREHOUSE`, `departmentId != null` -> `DEPARTMENT`; keyin deprecation.
- Priority: MEDIUM

## 18. Ochiq savollar

- `OUTSIDE_FACILITY`dagi uskuna qaysi department scope ostida qoladi: oldingi department, responsible department, owner department yoki recipient user department?
- Ombordan tashqariga berilgan uskuna `warehouse_equipment_items` active rowini yopadimi yoki warehouse ownership sifatida qoldiradimi?
- `LOST` statusmi yoki outside reasonmi? Audit tavsiyasi: status.
- `DECOMMISSIONED` joylashuv emas, status sifatida qolishi tasdiqlanadimi?
- Outside recipient user HR employee bo'lishi shartmi yoki erkin text ham yetarlimi?
- `outsideTakenBy` va `outsideRecipientUserId` birga yuborilsa qaysi biri authoritative?
- `outsideExpectedReturnDate` majburiymi yoki ayrim reasonlarda optionalmi?
- Service/repair holatida `EquipmentStatus.IN_REPAIR` avtomatik o'zgaradimi yoki location/status alohida yuradimi?
- Existing `locationId` warehouse id sifatida ishlatilgan qatorlar bor-yo'qligi DBda alohida data audit qilinadimi?
- Frontend yangi payloadni nested `location` sifatida yuboradimi yoki flat fields backward-compatible saqlanadimi?
