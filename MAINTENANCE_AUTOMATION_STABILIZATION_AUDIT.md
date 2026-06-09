# MAINTENANCE_AUTOMATION_STABILIZATION_AUDIT

## 1. PM uchun qisqa summary

Maintenance automation stabilization backend branchida quyidagi production risklar yopildi:

- Department-scoped user boshqa department due eventlarini approve/cancel/work-order qila olmasligi enforce qilindi.
- Preview/impact attribute condition natijasi real automation resolver logiciga yaqinlashtirildi.
- Approvaldan keyingi natija `approvalResultAction` orqali task yoki work order sifatida boshqariladi.
- Default-equivalent automation fields oddiy regulation create/update paytida `MAINTENANCE_AUTOMATION_CONFIGURE` talab qilmaydi.
- Meter readingdan keyingi automation exception silent qolmaydi, warn log yoziladi.
- `UPCOMING` auto-create emas, event-only sifatida qoldi.
- `ALL` trigger policy combined meter + calendar due calculationda `UPCOMING` va real `DUE/OVERDUE` holatlari ajratildi.

Oldin fail bo'lgan targeted test:

- `MaintenanceDueCalculationServiceTest.combinedAllBecomesDueWhenMeterAndCalendarAreBothDue`
- Fail: actual `NOT_DUE`, expected `DUE` yoki `OVERDUE`.

Qolgan risk:

- Shu lokal Codex shell muhitida Maven executable yo'q: `mvn: command not found`, repo ichida `./mvnw` ham yo'q. Shuning uchun testlarni bu muhitda qayta yurgizib pass deb tasdiqlab bo'lmadi.

## 2. Backend developer uchun o'zgarishlar

Asosiy fayllar:

- `src/main/java/com/toir/service/maintanance/MaintenanceDueCalculationService.java`
- `src/test/java/com/toir/service/maintanance/MaintenanceDueCalculationServiceTest.java`
- `src/main/java/com/toir/enums/ApprovalResultAction.java`
- `src/main/resources/db/migration/V20260604_2__maintenance_approval_result_action.sql`
- `src/main/java/com/toir/entity/maintenance/MaintenanceRegulation.java`
- `src/main/java/com/toir/dto/maintenanceregulation/MaintenanceRegulationRequest.java`
- `src/main/java/com/toir/dto/maintenanceregulation/MaintenanceRegulationDto.java`
- `src/main/java/com/toir/service/maintanance/MaintenanceRegulationApplicabilityService.java`
- `src/main/java/com/toir/service/maintanance/MaintenanceAutomationService.java`
- `src/main/java/com/toir/service/maintanance/MaintenanceDueEventService.java`
- `src/main/java/com/toir/service/maintanance/MaintenanceImpactService.java`
- `src/main/java/com/toir/service/maintanance/MaintenanceRegulationService.java`
- `src/main/java/com/toir/service/MeterService.java`

Root cause:

- `MaintenanceDueCalculationService.combine(...)` ichida `ALL` policy uchun `isActive(...)` ishlatilgan.
- `isActive(...)` `UPCOMING`, `DUE`, `OVERDUE` holatlarini bir xil active deb hisoblagan.
- Business rule bo'yicha `ALL` final `DUE/OVERDUE` bo'lishi uchun barcha configured triggerlar real due yoki overdue bo'lishi kerak; `UPCOMING` hali auto-create eligible due emas.
- Failing test fixture ham fixed test clock (`2026-06-02T00:00:00Z`) o'rniga `Instant.now().minusSeconds(...)` ishlatgani uchun calendar due holati wall-clockga bog'liq bo'lib qolgan.

Fix:

- `MaintenanceDueCalculationService.combine(...)`da `ALL` policy uchun:
  - `BLOCKED` avvalgi kabi dominant block bo'lib qoladi.
  - Barcha signal `DUE` yoki `OVERDUE` bo'lsa final dominant due status qaytadi.
  - Barcha signal kamida `UPCOMING` bo'lsa, lekin hammasi due/overdue bo'lmasa final `UPCOMING` qaytadi.
  - Aks holda final `NOT_DUE` va waiting explanation qaytadi.
- `isDueOrOverdue(...)` helper qo'shildi.
- `combinedAllBecomesDueWhenMeterAndCalendarAreBothDue` test setupi fixed clockga mos deterministic anchor bilan barqarorlandi: `2026-06-01T00:00:00Z`.

Department scope:

- `MaintenanceDueEventService.assertCanAccessEvent(...)` event equipmentini topib, departmentni `coalesce(responsibleDepartmentId, departmentId)` orqali tekshiradi.
- `cancel`, `approveDueEvent`, `createWorkOrderFromEvent` mutating actionlari shu assertiondan o'tadi.

Approval result action:

- `approvalResultAction=CREATE_TASK | CREATE_WORK_ORDER`.
- Null/default backward compatibility: `CREATE_TASK`.
- `REQUIRE_APPROVAL` flow approve paytida shu fieldga qarab task yoki work order yaratadi.

Permission contract:

- Create/update paytida default-equivalent automation fields privileged config hisoblanmaydi.
- Non-default automation config uchun `MAINTENANCE_AUTOMATION_CONFIGURE` talab qilinadi.

## 3. Frontend uchun API/request/response o'zgarishlari

Frontend o'zgarishlari backend commitga qo'shilmadi.

Backend contract bo'yicha:

- Regulation request/response `approvalResultAction` fieldini qo'llaydi.
- `UPCOMING` due calculation status sifatida qoladi, lekin auto-create behaviorga eligible emas.
- `DUE` va `OVERDUE` direct task/work-order creation policy bo'yicha downstream action yaratishi mumkin.

Oldingi frontend audit natijalari:

- Maintenance due status API-facing mappingda `NORMAL` yuborilmaydi; normal label `NOT_DUE`ga bog'langan.
- Work order button `api.createWorkOrderFromMaintenanceDueEvent(token, event.id)` chaqiradi.
- `canConfigureAutomation=false` bo'lsa automation fields payloaddan omit qilinadi.
- Permission bor bo'lsa non-default `approvalResultAction` payloadga kiritiladi.

## 4. Test/build/manual verification

So'ralgan test commands:

```bash
mvn -q -Dtest=MaintenanceDueCalculationServiceTest#combinedAllBecomesDueWhenMeterAndCalendarAreBothDue test
mvn -q -Dtest=MaintenanceDueCalculationServiceTest test
mvn -q -Dtest=MaintenanceAutomationServiceTest,MaintenanceDueCalculationServiceTest,MaintenanceDueEventServiceTest,MaintenanceRegulationServiceTest,MaintenanceImpactServiceTest,RbacMaintenanceAutomationSecurityTest test
git diff --check
```

Lokal natijalar:

- `mvn -q -Dtest=MaintenanceDueCalculationServiceTest#combinedAllBecomesDueWhenMeterAndCalendarAreBothDue test`: blocked, `zsh: command not found: mvn`.
- `mvn -q -Dtest=MaintenanceDueCalculationServiceTest test`: blocked, `mvn` PATH'da emas.
- Full targeted backend suite: blocked, `mvn` PATH'da emas.
- Repo ichida `./mvnw` topilmadi.
- `java -version`: OpenJDK 24.0.1 mavjud.
- `git diff --check`: pass.

Manual E2E expected status:

1. EX-001 meter 900 dan 1000 ga chiqsa meter trigger due bo'ladi.
2. Calendar trigger ham due/overdue bo'lsa va policy `ALL` bo'lsa final status `DUE/OVERDUE`.
3. Calendar hali `UPCOMING` yoki `NOT_DUE` bo'lsa `ALL` final auto-create eligible due bo'lmaydi.
4. `UPCOMING` event-only; `DUE/OVERDUE` policyga qarab task/work order yaratishi mumkin.
5. Duplicate policy one item per cycle ikkinchi open work order yaratishni to'xtatadi.
