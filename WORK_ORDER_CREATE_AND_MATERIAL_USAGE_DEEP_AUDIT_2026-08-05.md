# Work Order create va material usage deep audit — 2026-08-05

## Executive summary

Final verdict: **MULTIPLE_ROOT_CAUSES**.

Audit ikkita mustaqil contract regressiyasini isbotladi:

1. **Work Order performer contracti ajralib ketgan.** Frontend `/work-orders` create flow’i tanlangan brigade performerining `BrigadeMember.id` qiymatini legacy `performerId` sifatida yuboradi. Backend hozir `performerId`ni hamon `BrigadeMember.id` deb talqin qiladi, lekin 2026-07-31dan boshlab canonical contract `performerEmployeeId` va ixtiyoriy `performerBrigadeMemberId`dir. Frontend `Employee.id`ni `performerId`ga yuborsa backend uni brigade-member ID deb qidiradi. Bundan tashqari frontend “topshiriq beruvchi” (`issuerEmployeeId`)ni alohida request field sifatida umuman yubormaydi, faqat `summary` matniga qo‘shadi.
2. **Material quantity JSON tipi mos emas.** Frontend `quantity: Number(...)` qilib JSON number yuboradi. Backend `RepairMaterialUsageDto.quantity`ga `MoneyDecimalStringDeserializer` qo‘ygan va u faqat JSON string (`"1"`, `"1.2500"`) qabul qiladi. Shuning uchun screenshotdagi `quantity = 1` uchun real frontend body deserializatsiya qatlamidayoq `400 / INVALID_REQUEST_BODY` beradi; controller service’ga yetib bormaydi. Bo‘sh unit price bu xatoning sababi emas: frontend uni body’dan tashlab yuboradi, backend `unitCost`ni optional qabul qiladi va service null price bilan usage yaratishga ruxsat beradi.

Work Order screenshotidagi aynan `400 / BAD_REQUEST`ni faqat ko‘rsatilgan UI maydonlaridan bitta shartga qat’iy bog‘lash uchun evidence yetarli emas. Hozirgi kodda bunday generic 400ning kuchli nomzodi — foydalanuvchi tanlagan `locationId` equipmentning o‘z `locationId`idan farq qilishi: frontend departmentdagi boshqa locationni tanlashga ruxsat beradi, backend esa aynan equipment locationiga teng bo‘lishini talab qiladi. Performer contracti ham aniq nuqson, ammo oddiy `Employee.id` legacy `performerId`da kelsa hozirgi backend odatda `404 / WORK_ORDER_PERFORMER_MEMBER_NOT_FOUND` qaytaradi, screenshotdagi generic 400 emas. Exact runtime payload va backend exception log talab qilinadi.

## Audit scope va repository holati

Audit faqat statik/read-only tekshiruv va bitta Markdown report yaratish bilan cheklangan. Maven, npm, Vitest, Playwright va boshqa build/testlar ishga tushirilmadi. Branch, commit, push, production/test/migration/config o‘zgarishi qilinmadi.

| Repo | Path | Branch | HEAD | Upstream | Working tree | Deployment/ref evidence |
|---|---|---|---|---|---|---|
| Backend | `D:\Projects\toir-org\toir-backend` | `new_feature` | `00435f3f90cbe257f4bf0e49f94394c1f8eb644e` | branch upstream yo‘q; ayni commit `main`, `origin/main`, `origin/HEAD`da | avvaldan mavjud 2 untracked audit fayli; tegilmadi | `.gitlab-ci.yml:2-3` faqat default branch pipeline; local HEAD `origin/main` bilan ayni |
| Frontend | `D:\Projects\toir-front` | `behzod` | `4a1f4a06165943bf6c8c9a180e3ba20a574661a7` | upstream yo‘q | clean | `.gitlab-ci.yml:2-3` faqat default branch; audit branch default emas; real deployed SHA repo ichidan aniqlanmaydi |

Frontend local `main` `origin/main`dan 407 commit ortda deb ko‘rsatilgan; audit uchun user ishlatayotgan `behzod` HEAD tekshirildi. Backend va frontend HEADlari bir xil deployment/ref juftligi ekanini tasdiqlovchi manifest yoki runtime build SHA yo‘q. Shu sabab deployment mismatch ehtimoli ochiq qoladi.

## Screenshot/promptdan olinadigan faktlar

- Work Order: `POST /api/v1/work-orders` → `400`, `errorCode=BAD_REQUEST`, `params={}`, RU message “Некорректный запрос”.
- Material: `POST /api/v1/work-orders/{workOrderId}/material-usage` → `400`, `errorCode=INVALID_REQUEST_BODY`, `params={}`, RU message “Некорректное тело запроса”.
- Material dialogda warehouse, material va quantity `1` tanlangan; unit price bo‘sh; submit yoqilgan.
- Screenshot/prompt actual request body, response headers, selected entity UUIDlari, selected equipment/location relationi, Work Order statusi yoki backend stack trace’ni bermaydi.

## Work Order create — end-to-end trace

### Frontend

Asosiy `/work-orders` implementation `src/modules/repairs/pages/work-orders-page.tsx`da; deyarli ayni flow `components/work-order-create/work-order-create-dialog.tsx`da ham takrorlangan.

1. Initial state: `work-orders-page.tsx:113-135`. `issuerEmployeeId`, `performerEmployeeId`, dates, location va relation IDlar bo‘sh string; `type=PLANNED`, `workKind=REPAIR`, `hazardLevel=LOW`.
2. Wizard validation: `work-order-create-validation.ts:45-85`.
   - Step 1: title, equipment, department; EMERGENCY uchun repair request; DEFECT uchun defect; REPLACEMENT uchun warehouse va boshqa equipment.
   - Step 2: faqat issuer va start majburiy.
   - Step 3: HIGH hazard uchun participants.
   - Performer, end date, location/note va start-before-end tekshirilmaydi.
3. Issuer options `GET employees`dan keladi va option `value=e.id` (`work-orders-page.tsx:658-700, 2501-2518`). Issuer backendka alohida yuborilmaydi; `summary`ga inson o‘qiydigan matn bo‘lib kiradi (`984-999`).
4. Performer options `GET /brigades` javobidagi active `members`dan olinadi; option `value=member.id` (`702-729, 2538-2551`). Form field nomi `performerEmployeeId` chalg‘ituvchi: unda amalda `BrigadeMember.id` turadi. `member.userId` faqat labelga Employee topish uchun ishlatilgan; map esa `Employee.id` bilan keyed (`689`), shuning uchun `member.userId !== employee.id` bo‘lsa label ham UUIDga degradatsiya qiladi.
5. Location options department bo‘yicha `GET /locations?...&departmentId=...`dan keladi va istalgan option `location.id` yuboriladi (`766-800, 2595-2626`). Equipment selection o‘z locationini autofill qiladi, lekin UI foydalanuvchiga shu departmentdagi boshqa locationni tanlashga ruxsat beradi.
6. Submit builder `work-orders-page.tsx:955-1027`:
   - `performerId = form.performerEmployeeId` (aslida member ID);
   - `createdById=user.id` yuboriladi, lekin controller authenticated current userni ishlatadi;
   - `start/end` `datetimeLocalToInstant` orqali browser local vaqt zonasidan UTC ISO instantga aylanadi (`src/lib/utils.ts:32-44`);
   - bo‘sh optional qiymatlar `undefined`; `JSON.stringify` ularni body’dan olib tashlaydi;
   - bo‘sh note yuborilmaydi; array field yo‘q; acts faqat dialog variantida summaryga qo‘shiladi, canonical booleanlar bu create builderda yuborilmaydi.
7. API client `src/lib/api.ts:2494-2534`: `POST /work-orders`, JSON body. Shared request `api.ts:826-850` `Content-Type: application/json` va Bearer token qo‘yadi. Base URL `/api/v1` konfiguratsiya orqali prefiks qilinadi.

Tipik LOW body:

```json
{
  "number": "WO-...",
  "title": "...",
  "equipmentId": "<Equipment.id>",
  "departmentId": "<Department.id>",
  "type": "PLANNED",
  "workType": "REPAIR",
  "priority": "MEDIUM",
  "createdById": "<User.id>",
  "summary": "...",
  "performerId": "<BrigadeMember.id>",
  "locationId": "<Location.id>",
  "workLocationNote": "...",
  "startPlannedAt": "2026-08-05T05:00:00.000Z",
  "endPlannedAt": "2026-08-05T06:00:00.000Z"
}
```

### Backend

1. `WorkOrderController.create`, `src/main/java/com/toir/controller/WorkOrderController.java:450-458`: `@Valid @RequestBody WorkOrderRequest`, department scope, authenticated `currentUserId`, `201`.
2. Jackson maps UUID/enums/`Instant`. Invalid UUID, enum yoki non-ISO timestamp → `HttpMessageNotReadableException` → `400 INVALID_REQUEST_BODY`.
3. Bean Validation in `WorkOrderRequest`: only `title @NotBlank`, `equipmentId @NotNull`, `type @NotNull`. Canonical performer fields mavjud: legacy `performerId`, `performerEmployeeId`, `performerBrigadeMemberId`. Dates, department, location, issuer, performer not annotated.
4. `WorkOrderService.createPublic/createInternal`, `WorkOrderService.java:865-1006`:
   - server-owned field guard;
   - equipment operational lifecycle;
   - replacement and type relations;
   - number uniqueness;
   - repair request/defect/PPR relation checks;
   - equipment, department and location resolution;
   - performer policy;
   - persistence, template/checklist/audit/notification.
5. Department contract `WorkOrderService.java:2655-2672`: request department equipment responsible/physical departmentiga mos bo‘lishi shart; mismatch → generic `RestException.badRequest`.
6. Location contract `2681-2697`: request `locationId` equipmentning non-null locationidan farq qilsa generic 400; location boshqa departmentniki bo‘lsa ham generic 400.
7. Performer semantics `WorkOrderPerformerAssignmentPolicy.java:35-53`:
   - `performerId` = legacy **BrigadeMember.id**;
   - `performerEmployeeId` = canonical **Employee.id**;
   - `performerBrigadeMemberId` faqat employee ID bilan birga beriladi;
   - employee/member active, not deleted, department va user pairing tekshiriladi.
8. Entity `WorkOrder`: `performer` `brigade_member_id`ga, `performerEmployee` `performer_employee_id`ga saqlanadi; title/equipment/department/type/workType/status/priority DBda non-null.
9. Hozirgi service start/end tartibini, equal/past datesni validate qilmaydi. ISO parse bo‘lsa har qanday Instant saqlanadi. Bu UI kutgan biznes qoidasiga qarshi coverage gap.

### Frontend ↔ backend Work Order contract

| UI/form | Frontend payload | Real ID/type | Backend meaning | Holat |
|---|---|---|---|---|
| Issuer | faqat `summary` | `Employee.id` lookup | issuer field yo‘q | Data loss/audit gap; create error sababi emas |
| Performer (joriy picker) | `performerId` | `BrigadeMember.id` | legacy BrigadeMember.id | Ishlaydi, agar member va linked employee valid bo‘lsa |
| Oddiy employee (agar shu value berilsa) | `performerId` | `Employee.id` | BrigadeMember.id deb qidiriladi | Contract mismatch; 404 member-not-found kutiladi |
| Canonical employee | frontend type/builderda yo‘q | — | `performerEmployeeId: Employee.id` | Frontend ortda |
| Canonical member context | frontend type/builderda yo‘q | — | `performerBrigadeMemberId: BrigadeMember.id` + employeeId | Frontend ortda |
| Department | `departmentId` | UUID | equipment owner departmenti bilan teng | UI equipment filter/autofill yordam beradi, backend qat’iy |
| Work location | `locationId` | Location.id | equipment locationi bilan aynan teng yoki equipmentda null | UI departmentdagi boshqa locationni tanlatadi — mismatch |
| Equipment | `equipmentId` | Equipment.id | required, active/operational | Mos; runtime state ta’sir qiladi |
| Type/source | `type`, relation IDs | enum + UUID | EMERGENCY/DEFECT relation required | Front validation bor |
| Priority/status | derived priority; status yo‘q | enum | status server default DRAFT | Mos |
| Planned dates | ISO UTC string | Instant | optional Instant | Format mos; ordering validation yo‘q |
| Required acts | page builder yubormaydi; dialog summaryga ham qo‘shadi | text/undefined | boolean fields bor | Contract incomplete |
| Notes | `summary`, `workLocationNote` | string/omitted | optional | Bo‘sh string omitted |

### Work Order exact failure conditions

- **Isbotlangan generic 400 shartlari:** equipmentga mos bo‘lmagan department; equipmentga mos bo‘lmagan location; boshqa department location; disallowed repair-request status; relation/equipment mismatch; emergency/defect relation yo‘qligi; invalid replacement rules; lifecycle guard va ayrim server-owned fieldlar. Bular `RestException.badRequest(message)` ishlatadi va stable code bermaydi, shuning uchun RU locale’da `BAD_REQUEST`, “Некорректный запрос”, `params={}`ga aylanadi.
- **Isbotlangan performer mismatch:** `Employee.id`ni `performerId`da yuborish backendning legacy member lookupiga mos emas. Hozirgi policy bunda odatda 404 stable member-not-found beradi; screenshot 400 bilan aynan teng emas.
- **Screenshotga eng mos statik nomzod:** tanlangan `locationId != equipment.locationId`. UI buni hosil qila oladi va backend aynan generic 400 qaytaradi. Buni exact root cause deyish uchun payloaddagi ikki ID va DB equipment row kerak.

## Material usage — end-to-end trace

### Frontend

1. State `work-order-detail-page.tsx:58-63, 93-95`: barcha qiymatlar bo‘sh string.
2. Warehouse options `GET /warehouses`, `value=item.id` (`177-200`). Material options `GET /inventory-catalog`, `value=s.id` (`259-286`). Bu catalog ID backend kutgan `SparePart.id`; stock balance ID yuborilmaydi.
3. Dialog `materials-tab.tsx:111-215`: button faqat warehouse, sparePart va truthy quantityni tekshiradi. Unit cost optional. `"0"`, `"-1"` truthy bo‘lgani uchun submit enabled; explicit schema yo‘q.
4. Submit `work-order-detail-page.tsx:339-350`: quantity `Number`, unitCost bo‘lsa `Number`, bo‘lmasa `undefined`.
5. `MaterialUsageInput`, `api.ts:411-429`: quantity TypeScriptda `number`; serializer faqat `unitCost`ni two-decimal stringga aylantiradi. `addWorkOrderMaterial`, `2631-2636` JSON.stringify qiladi.

Screenshot case actual body kod bo‘yicha:

```json
{
  "warehouseId": "<Warehouse.id>",
  "sparePartId": "<InventoryCatalog/SparePart.id>",
  "quantity": 1
}
```

`unitCost: undefined` JSONdan tushadi. Agar narx kiritilsa bodyda `"unitCost":"12.50"` bo‘ladi; backend DTO esa `Double` bo‘lsa-da Jackson default coercion odatda numeric stringni qabul qiladi. Asosiy deterministik xato `quantity` tokenidir.

### Backend

1. `RepairMaterialUsageController.register`, `controller/repair/RepairMaterialUsageController.java:48-52`: UUID path, `@Valid @RequestBody RepairMaterialUsageDto`, `201`.
2. DTO `dto/materialusage/RepairMaterialUsageDto.java:14-39`:
   - warehouseId `@NotNull UUID`;
   - sparePartId `@NotNull UUID`;
   - quantity `BigDecimal`, `@Positive`, `@Digits(15,4)`, custom `MoneyDecimalStringDeserializer`;
   - unitCost `Double`, annotation yo‘q, optional.
3. `MoneyDecimalStringDeserializer.java:18-42`: non-string tokenni rad etadi; regex `-?(0|[1-9]\d*)(\.\d{1,4})?`; nullni rad etadi; absentni `BigDecimal.ZERO` qiladi.
4. Non-string quantity Jacksonda `HttpMessageNotReadableException` bo‘ladi. `GlobalExceptionHandler.handleUnreadable` → `400 INVALID_REQUEST_BODY`, empty params. Localizer RU generic message sabab ichki “canonical string” detali clientga berilmaydi.
5. Faqat deserializatsiyadan keyin Bean Validation ishlaydi. `"0"`/`"-1"` → `VALIDATION_FAILED`, `params.fields=["quantity"]`.
6. Service `RepairMaterialUsageService.register`, lines `187-269`:
   - Work Order exists/scope;
   - status faqat APPROVED yoki IN_PROGRESS;
   - equipment lifecycle;
   - warehouse exists/scope;
   - quantity qo‘shimcha validate;
   - legacy stock row warehouse+sparePart bo‘yicha exists;
   - StockMovement save;
   - WMS auto-allocation / available stock;
   - usage save va optional actual cost.
7. `postCoreStockIssue` lines `272-287` WMS `StockIssueCommand`ga ayni warehouse/sparePart/quantityni beradi. `ToirStockService.java:422-474`: balance yo‘q yoki insufficient available stock → generic 400. Transactional method sabab oldingi movement save rollback bo‘lishi kerak.
8. Unit cost null bo‘lsa usage saqlanadi; `syncMaterialActualCost` lines `383-386` cost yaratmaydi, response `costWarning` beradi. Bu unit price biznes bo‘yicha optional ekanini bevosita isbotlaydi.
9. Duplicate materialga unique constraint yoki service guard yo‘q; bir Work Orderga bir xil material qayta issue qilinishi mumkin, stock yetarli bo‘lsa yangi usage yaratiladi.

### Material field-by-field contract

| Form field | Front payload | Actual JSON | Backend DTO | Required/validation | Mismatch/effect |
|---|---|---|---|---|---|
| Warehouse | `warehouseId` | UUID string | `UUID warehouseId` | `@NotNull`, exists/scope | ID semantics mos; inventory relation service’da |
| Material | `sparePartId` | catalog `s.id` UUID | `UUID sparePartId` | `@NotNull`, stock row required | Catalog SparePart ID mos; balance ID emas |
| Quantity | `quantity` | **number** | custom string→BigDecimal | positive, digits(15,4) | **Deterministik INVALID_REQUEST_BODY** |
| Unit price | `unitCost` | omitted yoki decimal string | `Double unitCost` | optional | Bo‘sh narx valid; serializer/type naming inconsistent, lekin screenshot xatosi emas |
| Keep open | payloadga kirmaydi | — | yo‘q | UI-only | Mos |
| Notes/coordinates | UIda yo‘q | omitted | optional fields | optional | Mos, default stock status AVAILABLE |

## Error handling va observability

`GlobalExceptionHandler` quyidagicha ajratadi:

- `RestException` → uning status/code/params’i. Code berilmagan generic bad request `BackendErrorLocalizer.defaultCode(BAD_REQUEST)` orqali `BAD_REQUEST` bo‘ladi.
- Bean Validation → `VALIDATION_FAILED`, `params.fields` bor.
- Jackson unreadable → `INVALID_REQUEST_BODY`, `params={}`.
- UUID path/query conversion → `INVALID_PARAMETER_VALUE`.
- DB optimistic lock → `409 OPTIMISTIC_LOCK`.

`params={}` sababi screenshotlarda Bean Validation emasligidir. Work Orderda code’siz `RestException`; materialda Jackson handler ataylab empty map yuboradi. RU localizer unknown/custom code yoki generic code uchun ichki inglizcha sababni umumiy tarjima bilan almashtiradi. Natijada frontend qaysi field xato ekanini ko‘ra olmaydi. Material handler `buildUnreadableMessage` ichki detailni yasaydi, ammo localizer known `INVALID_REQUEST_BODY` tarjimasini tanlab, detailni RU clientga uzatmaydi.

## Reproduction matrix — Work Order create

`BASE` = valid title/equipment/department/type, ISO dates; payloadda ko‘rsatilmagan optional fieldlar omitted.

| Case | Front payload delta | Backend canonical expectation | Current result | Screenshot 400? | Confidence |
|---|---|---|---|---|---|
| Valid brigade performer | `performerId:<BrigadeMember.id>` | legacy member yoki canonical pair | 201, agar member+employee active/same dept | No | High |
| Valid employee canonical | `performerEmployeeId:<Employee.id>` | aynan shu | 201 | No | High |
| User.id yuborish | `performerId:<User.id>` | legacy member ID | 404 `WORK_ORDER_PERFORMER_MEMBER_NOT_FOUND` (UUID collision yo‘q deb) | No | High |
| Employee.id legacy fieldda | `performerId:<Employee.id>` | legacy member ID | 404 member-not-found | No | High |
| BrigadeMember.id | `performerId:<member.id>` | legacy member ID | 201 yoki member/employee policy error | Ba’zi policy mismatchda 400 | High |
| Inactive employee canonical | `performerEmployeeId:<id>` | active employee | 400 `WORK_ORDER_PERFORMER_EMPLOYEE_INACTIVE` | 400, lekin code boshqa | High |
| Boshqa department employee | canonical employee ID | same dept | 400 `...DEPARTMENT_MISMATCH` | 400, lekin code boshqa | High |
| Issuer yo‘q | UI request yubormaydi | backend issuer yo‘q | frontend step 2 bloklaydi; raw request create bo‘lishi mumkin | No HTTP | High |
| Invalid issuer ID | issuer faqat summary lookup | backend field yo‘q | submit bo‘lsa backend bilmaydi | No | High |
| start/end invalid format | raw non-ISO | `Instant` ISO | 400 `INVALID_REQUEST_BODY` | No (code boshqa) | High |
| start>end/equal/past | ISO instants | hozir qoida yo‘q | 201 (boshqa guard bo‘lmasa) | No | High |
| Location omitted | no `locationId` | equipment location fallback | 201; equipment location null bo‘lsa null | No | High |
| Location invalid UUID | malformed string | UUID | 400 `INVALID_REQUEST_BODY` | No | High |
| Location boshqa valid ID | `locationId != equipment.locationId` | exact equipment location | **400 BAD_REQUEST, params={}** | **Yes** | High condition; screenshot linkage Medium |
| Oldingi required field yo‘q | e.g. title/equipment/type omitted | Bean Validation | 400 `VALIDATION_FAILED`, fields params | No | High |

## Reproduction matrix — material usage

`UI` column hozirgi serializer natijasini; `raw canonical` backendka to‘g‘ri qo‘lda yuboriladigan qiymatni anglatadi.

| Case | Front/body | Backend expectation | Current result | Screenshot? | Confidence |
|---|---|---|---|---|---|
| Valid warehouse+material+qty+price via UI | `quantity:1, unitCost:"10.00"` | `quantity:"1"` | 400 `INVALID_REQUEST_BODY` | Yes | High |
| Price omitted | `quantity:1`, unitCost absent | price optional | quantity sabab same 400 | Yes | High |
| Price null | serializer omits it | optional | quantity sabab same 400 | Yes | High |
| Price empty string | UI converts to undefined | optional | quantity sabab same 400 | Yes | High |
| Quantity string raw | `quantity:"1"` | canonical decimal string | deserializes; then business checks | No, agar data valid | High |
| Quantity zero raw | `"0"` | positive | 400 `VALIDATION_FAILED`, fields quantity | No | High |
| Quantity negative raw | `"-1"` | positive | 400 `VALIDATION_FAILED` | No | High |
| Decimal comma UI | `Number("1,5")=NaN`; JSON `null` | `"1.5"` | 400 `INVALID_REQUEST_BODY` | Yes, same code | High |
| Insufficient stock raw canonical | `quantity:"N"` | available >= N | 400 `BAD_REQUEST`, empty params | No (code differs) | High |
| Material boshqa warehouse | valid IDs, no pair stock | pair stock/balance | 404 no legacy stock yoki 400 no WMS balance | No | High |
| Material o‘rniga balance ID | `sparePartId:<balance.id>` | SparePart.id | 404 no stock | No | High |
| Balance o‘rniga material ID | UIda balance field yo‘q | n/a | current UI aynan SparePart.id yuboradi | No mismatch | High |
| Closed/cancelled/completed WO raw | canonical body | APPROVED/IN_PROGRESS only | 400 `BAD_REQUEST` | No | High |
| Duplicate material | same valid request twice | guard yo‘q | ikki usage/issue, stock yetarli bo‘lsa 201 | No | High |
| Invalid WO UUID syntax | invalid path | UUID | 400 `INVALID_PARAMETER_VALUE` | No | High |
| Missing but valid WO UUID | random UUID | existing nondeleted WO | 404 `RESOURCE_NOT_FOUND` | No | High |

## Swagger/OpenAPI va TypeScript contract audit

- Controller endpoint va HTTP methods frontend bilan mos.
- `WorkOrderRequest` canonical performer fieldlarini beradi, frontend inline `createWorkOrder` type esa faqat `performerId`ni biladi. Bu aniq stale TypeScript contract.
- `RepairMaterialUsageDto` request va response sifatida bir DTOda ishlatilgan; response-only fieldlar request OpenAPI schema’ga ham chiqadi.
- `quantity`ga custom deserializer qo‘yilgan, lekin `@Schema(type="string", pattern=...)` yo‘q. Springdoc BigDecimalni odatda numeric schema sifatida ko‘rsatishi mumkin; runtime string-only talab controller/DTO annotationlaridan consumerga aniq ko‘rinmaydi.
- Frontend comment “Money tipi string kutadi” deydi, lekin serializer faqat `unitCost`ga qo‘llangan; backendda aynan custom string-only field `quantity`, `unitCost` esa `Double`. Bu comment va implementationning o‘zaro zidligini ko‘rsatadi.
- Eski/yangi Work Order performer contracti parallel qolgan: legacy `performerId` va canonical ikki field. Backend compatibility saqlagan, frontend canonicalga ko‘chmagan.

## Git history / regression evidence

- Frontend Work Order picker va `performerId` builder `3021d2e9` (2026-06-18)dan.
- Backend canonical employee performer policy `8bf8eb26` (2026-07-31, `fix(work-orders): make employee canonical performer`)da kirgan. Audit frontend HEAD 2026-07-30 bo‘lgani uchun u bu backend contract o‘zgarishidan oldingi snapshot.
- Backend material `quantity`ga strict deserializer `4828334d` (2026-07-12)da qo‘yilgan.
- Frontend `bbbb625a` (2026-07-13)da decimal serializer qo‘shgan, ammo faqat `unitCost`ni stringga aylantirgan; `quantity` `number` bo‘lib qolgan. Evidence regression commitni material flow uchun ishonch bilan ko‘rsatadi.
- Real production SHAlar yo‘q; shu commitlar productionga qachon deploy bo‘lganini repo historyning o‘zi isbotlamaydi.

## Test coverage gaps

Mavjud backend `Task5DecimalBoundaryContractTest` numeric quantity rad qilinishini ataylab testlaydi; service material tests null unit cost, stock va statuslarni qoplaydi. Ammo real frontend serializer bilan controller end-to-end contract testi yo‘q. Frontend `api.test.ts` material POST body’sining quantity string bo‘lishini tekshirmaydi.

Yetishmaydigan regression tests:

1. Frontend serializer: `{quantity:1}` → `{"quantity":"1"}` va empty/null price omission.
2. Cross-contract fixture: frontend-generated body backend `ObjectMapper`/MockMvc’da 201 bo‘lishi.
3. Work Order picker discriminated option `{kind:"EMPLOYEE"|"BRIGADE_MEMBER", employeeId, memberId}` mapping testi.
4. Canonical employee-only va employee+member controller integration tests.
5. UI location selection policy backendning exact-equipment-location qoidasiga mosligini tekshiruvchi test.
6. start < end va past/equal sanalar bo‘yicha kelishilgan contract testlari.
7. Error responsesda stable code/field params va frontend fieldga mapping testi.
8. Material duplicate policy va warehouse/catalog stock filtering testi.

## Findings

### P0

**WO-MAT-001 — material quantity transport type mismatch**

- Severity/flow: P0, material add; barcha joriy UI submitlar.
- Exact condition: frontend `quantity`ni JSON number yuboradi; backend string-only deserializer.
- Symptom: 400 `INVALID_REQUEST_BODY`, generic RU message, empty params.
- Evidence: frontend `work-order-detail-page.tsx:344-349`, `api.ts:411-430,2631-2636`; backend `RepairMaterialUsageDto.java:25-28`, `MoneyDecimalStringDeserializer.java:18-32`, exception handler.
- Ownership: frontend contract mapper birlamchi; backend/OpenAPI design ham contributing.
- Fix: quantityni canonical max-4-decimal string qilib serialize qilish va TS type’ni boundary type bilan moslash.
- Regression test: exact POST body + backend deserialization/controller test.

### P1

**WO-CREATE-001 — stale/ambiguous performer ID contract**

- Severity/flow: P1, Work Order create.
- Exact condition: ambiguous `performerEmployeeId` form variableda member ID saqlanadi va legacy `performerId` yuboriladi; canonical fields frontendda yo‘q. Employee ID shu fieldda yuborilsa member lookup bo‘ladi.
- Symptom: assignment failure (404/400/409 policy holatiga qarab) yoki frontend canonical employee assignment qila olmaydi.
- Evidence: frontend `work-orders-page.tsx:689-729,2538-2551,1010-1013`; backend `WorkOrderRequest`, `WorkOrderPerformerAssignmentPolicy.java:35-53`; commit `8bf8eb26`.
- Ownership: frontend migration, backend legacy deprecation/error clarity.
- Fix: canonical discriminated mapping va preferably backend `/work-orders/options/performers` endpointidan foydalanish.
- Regression test: employee-only, member-context, wrong ID-kind.

**WO-CREATE-002 — UI location freedom backend exact-equipment-location invariantiga zid**

- Severity/flow: P1, Work Order create.
- Exact condition: user departmentdagi locationni tanlaydi va u equipment locationidan farq qiladi.
- Symptom: screenshotga aynan mos 400 `BAD_REQUEST`, params empty.
- Evidence: frontend location query/options `766-800,2595-2626`; backend `WorkOrderService.java:2681-2697`.
- Ownership: contract decision frontend+backend.
- Fix: minimal — locationni equipmentdan read-only/autofill; canonical — backend biznesi boshqa execution locationga ruxsat berishi kerak bo‘lsa invariantni alohida `equipmentLocationId` va `workLocationId` semantikalariga ajratish.
- Regression test: same/different/null equipment location matrix.

### P2

**WO-OBS-001 — generic localization root cause’ni yashiradi**

- Severity: P2, ikkala flow.
- Condition: code’siz RestException yoki unreadable JSON RU/UZ locale’da.
- Symptom: `BAD_REQUEST`/`INVALID_REQUEST_BODY`, `{}`, field details yo‘q.
- Evidence: `GlobalExceptionHandler.java:41-78,121-124`; `BackendErrorLocalizer.java:21-30,60-90,107-129`.
- Ownership: backend; frontend field mapping ham kerak.
- Fix: har business invariantga stable code+params; unreadable exceptiondan field/path/reasonCode params; localized safe detail.
- Test: RU/UZ/EN response code+params contract.

**WO-CREATE-003 — issuer va required acts canonical persistence qilinmaydi**

- Severity: P2.
- Condition: UI issuer/acts tanlaydi, builder ularni faqat summaryga qo‘shadi yoki umuman yubormaydi.
- Symptom: structured audit/reporting yo‘q; issuer invalidligi backendda tekshirilmaydi.
- Evidence: frontend builder lines `979-1027`; backend requestda issuer field yo‘q, acts booleanlari mavjud.
- Ownership: product contract + both sides.
- Fix/test: issuer semanticsni aniqlab DTO/entity field; act booleansni yuborish va persist test.

**WO-MAT-002 — request/response DTO va OpenAPI schema noaniq**

- Severity: P2.
- Condition: response DTO request body sifatida; custom string deserializer schema bilan e’lon qilinmagan.
- Symptom: generated/manual clients number yuboradi va 400 oladi.
- Evidence: controller `@RequestBody RepairMaterialUsageDto`; DTO annotations.
- Ownership: backend API design.
- Fix: alohida `CreateRepairMaterialUsageRequest` va explicit OpenAPI string decimal schema.
- Test: generated OpenAPI schema assertion.

### P3

**WO-CREATE-004 — duplicate create implementations va misleading names**

- Severity: P3.
- Evidence: page va `work-order-create-dialog.tsx`da bir xil flow; `performerEmployeeId` amalda member ID.
- Risk: fix bir nusxada qolib ketadi.
- Fix/test: shared form model/payload mapper; mapper unit tests.

**WO-MAT-003 — weak client validation**

- Severity: P3.
- Evidence: button truthinessgina tekshiradi; `0`, negative, comma/NaNni oldindan rad qilmaydi.
- Fix/test: decimal parser/schema, field-level errors.

## Fix options

### Minimal safe fix

1. Frontend `serializeMaterialUsage` quantityni canonical decimal stringga aylantirsin; comma inputni normalize/rad qilsin; TS request boundaryda string bo‘lsin.
2. Frontend Work Order builder joriy brigade option uchun `performerEmployeeId` + `performerBrigadeMemberId`ni yuborishi uchun memberdan linked Employee.idni ishonchli resolve qilsin; legacy `performerId`ni yangi requestsda to‘xtatsin.
3. Work location equipmentnikidan farq qilmasligi kerak bo‘lsa selectni read-only/autofill qilsin.

### Canonical contract fix

- Backend alohida create request DTOlari va explicit OpenAPI schemasini joriy qilsin.
- Performer option endpoint unified discriminated response qaytarsin: `employeeId`, optional `brigadeMemberId`, `userId`, active/department metadata.
- Material request: `warehouseId`, `sparePartId`, `quantity` canonical decimal string, optional `unitCost`ning ham bitta kelishilgan decimal tipi.
- Business exceptions stable error codes va field params bilan qaytsin.

### Long-term cleanup

- Legacy `performerId`ni deprecate va usage telemetrydan keyin olib tashlash.
- Ikki Work Order create UI implementationni shared mapper/schema bilan birlashtirish.
- Issuer, execution location va actsni free-text summarydan structured domain fieldsga ko‘chirish.
- Contract fixture/generationni CI’da frontend va backend orasida tekshirish.

## Tavsiya etilgan implementation sequence

1. Avval runtime Network payload va backend log bilan Work Order exact failing invariantini tasdiqlash.
2. Material quantity serializer + cross-contract regression test (eng kichik, deterministik P0).
3. Work Order canonical performer mapper va option source; legacy fallbackni saqlab turish.
4. Location semantikasini product qarori bilan aniqlab, UI yoki backend invariantini moslashtirish.
5. Stable error codes/params va frontend field display.
6. Issuer/acts structured contract va duplicate UI cleanup.
7. OpenAPI/contract CI gate.

## Runtime’da hali tekshirilishi kerak

Work Order exact screenshot root cause uchun quyidagilar zarur:

- Browser Network’dan **raw POST JSON** (ayniqsa `equipmentId`, `departmentId`, `locationId`, barcha performer fieldlari, type/relation IDs, dates).
- Response headers (`Content-Language`) va deployed frontend/backend build SHA/image digest.
- Backend shu request correlation/time uchun exception class, `RestException.message/errorCode`, stack trace.
- DB/read API: equipment `location_id`, responsible/physical department; selected location department; selected BrigadeMember `id,user_id,active,brigade_id`; linked Employee `id,user_id,department_id,active,is_deleted`; brigade active/department.

Material uchun root cause code bilan yetarlicha isbotlangan, ammo runtime deploymentni tasdiqlash uchun raw bodyda `quantity` tokenining number ekanini va backend build SHA `4828334d`ni o‘z ichiga olishini tekshirish kerak.

## Final verdict

**MULTIPLE_ROOT_CAUSES**

- Material screenshotining isbotlangan sababi: frontend `quantity`ni number yuboradi, backend string-only BigDecimal kutadi. Unit price bo‘shligi sabab emas.
- Work Orderda isbotlangan contract qarama-qarshiliklari: legacy/canonical performer fields va execution-location invariant. Screenshotdagi aynan generic 400ga eng mos shart — selected location equipment locationidan farq qilishi; actual payload/DB/logsiz buni yakka root cause sifatida tasdiqlab bo‘lmaydi.
- Ikki xato bitta transport bugi emas. Ularning umumiy tizimli sababi — frontend/backend schema contractining generated/shared tekshiruvi yo‘qligi va error observabilityning yetarli emasligi.

Tests/build: **NOT RUN — audit-only and skipped by instruction.**

Production code: **UNCHANGED.**
