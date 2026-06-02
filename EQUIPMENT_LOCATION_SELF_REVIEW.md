# Equipment Location Self Review

## 1. Qisqa xulosa

Equipment Location o'zgarishlari grep va diff asosida qayta tekshirildi. Asosiy model, DTO, migration, repository query, PBAC scope va audit history yo'nalishlari ko'rib chiqildi. Backend biznes logikasiga yangi funksiya qo'shilmadi.

Review davomida ikkita kichik va xavfsiz test kontrakt tuzatishi qilindi:

- `EquipmentControllerContractTest` ro'yxat testlaridagi eski `service.search(...)` chaqiriqlari location-aware yangi signature bilan moslashtirildi.
- Warehouse placement contract testida `locationId` warehouse id deb kutilgan eski assertion physical warehouse location id ga o'zgartirildi.

## 2. Tekshirilgan fayllar

- `src/main/java/com/toir/entity/equipment/Equipment.java`
- `src/main/java/com/toir/entity/equipment/EquipmentLocationHistory.java`
- `src/main/java/com/toir/dto/equipment/EquipmentCreateRequest.java`
- `src/main/java/com/toir/dto/equipment/EquipmentUpdateRequest.java`
- `src/main/java/com/toir/dto/equipment/EquipmentDto.java`
- `src/main/java/com/toir/dto/equipment/EquipmentLocationRequest.java`
- `src/main/java/com/toir/dto/equipment/EquipmentTransferRequest.java`
- `src/main/java/com/toir/service/equipment/EquipmentService.java`
- `src/main/java/com/toir/service/equipment/EquipmentLocationValidator.java`
- `src/main/java/com/toir/repository/equipment/EquipmentRepository.java`
- `src/main/java/com/toir/repository/equipment/EquipmentLocationHistoryRepository.java`
- `src/main/java/com/toir/security/ScopeAccessService.java`
- `src/main/java/com/toir/service/AnalyticsService.java`
- `src/main/java/com/toir/controller/equipment/EquipmentController.java`
- `src/main/java/com/toir/controller/VehicleController.java`
- `src/main/resources/db/migration/V20260602_3__equipment_current_location.sql`
- `src/test/java/com/toir/controller/EquipmentControllerContractTest.java`
- `src/test/java/com/toir/repository/equipment/EquipmentRepositoryQueryContractTest.java`
- `src/test/java/com/toir/security/EquipmentPbacScopeTest.java`
- `src/test/java/com/toir/security/ScopeAccessServiceTest.java`
- `src/test/java/com/toir/security/VehiclePbacScopeTest.java`
- `src/test/java/com/toir/service/WarehouseEquipmentItemServiceTest.java`
- `src/test/java/com/toir/service/equipment/EquipmentServiceTest.java`
- `src/test/java/com/toir/service/equipment/EquipmentLocationValidatorTest.java`

## 3. Topilgan va tuzatilgan muammolar

- `EquipmentControllerContractTest` list testlari eski 9-parametrli `EquipmentService.search(...)` overloadini stub qilgan edi. Controller esa yangi location-aware 13-parametrli overloadni chaqiradi. Testlar yangi signaturega moslashtirildi.
- `patchPlacementWarehouseSuccess` testida `locationId` uchun warehouse id kutilgan edi. Yangi qoida bo'yicha `locationId` physical location bo'lishi kerak, warehouse id emas. Assertion warehouse physical location id ga o'zgartirildi.

## 4. Tuzatilmagan risklar

- Build va testlar lokal muhitda to'liq ishlatilmadi: `./mvnw`, `mvn`, `./gradlew`, `gradle` mavjud emas.
- Quyidagi equipment accessory/label/attribute controllerlarda hali ham `equipment.getDepartmentId() == null` orqali access guard bor. Agar bu endpointlar warehouse yoki outside equipment uchun responsible department orqali ishlashi kerak bo'lsa, keyingi bosqichda alohida tuzatish kerak:
  - `EquipmentManualAttributeController`
  - `EquipmentAttributeController`
  - `EquipmentScanCompatibilityController`
  - `EquipmentLabelController`
- `EquipmentRepository` querylaridagi enum literal va overdue filter semantikasi Maven compile/integration test bilan tasdiqlanishi kerak.
- Dashboard/reporting yo'nalishlarida `department_id` ga bog'langan eski visibility patternlar bo'lishi mumkin. Ushbu review faqat Equipment Location implementatsiyasining aniq scope bo'yicha tekshirildi.

## 5. Migration/entity alignment natijasi

`V20260602_3__equipment_current_location.sql` yangi `Equipment` fieldlari bilan mos:

- `current_location_type`
- `current_warehouse_id`
- `responsible_department_id`
- `outside_reason`
- `outside_taken_by`
- `outside_recipient_user_id`
- `outside_started_date`
- `outside_expected_return_date`
- `outside_destination`
- `outside_reason_note`

`EquipmentLocationHistory` entitysi `equipment_location_history` table ustunlari bilan mos ko'rindi. Enum check constraint qiymatlari `EquipmentLocationType` va `EquipmentOutsideReason` enumlari bilan mos. FK va indexlar asosiy lookup/query yo'llari uchun qo'shilgan.

## 6. DTO/API compatibility natijasi

Old flat fieldlar saqlangan:

- `departmentId`
- `warehouseId` create requestda
- `locationId`

Yangi `location` object qo'shilgan. Old constructor overloadlari saqlangan, shuning uchun mavjud test va Java call sitelar uchun binary/source compatibility yo'nalishi e'tiborga olingan.

## 7. PBAC/query natijasi

Asosiy Equipment list/detail/service yo'llari responsible department fallback bilan ishlashga moslashtirilgan ko'rindi. `ScopeAccessService` va asosiy equipment PBAC testlari yangilangan.

Repository search signaturelari location filterlar bilan kengaytirilgan:

- `warehouseId`
- `locationType`
- `outsideReason`
- `overdueOnly`
- `availableForReplacement`

Old compatibility overloadlar mavjudligi service testlarida eski chaqiriqlarni saqlab qolishga yordam beradi.

## 8. History/audit natijasi

`EquipmentLocationHistory` yozish yo'li `EquipmentService` ichida create, update va placement update paytlarida chaqiriladi. History entity from/to location fields, responsible department, changed by, changed at va note maydonlarini saqlaydi.

## 9. Warehouse locationId misuse tekshiruvi

Grep natijalariga ko'ra production kodda warehouse id ni to'g'ridan-to'g'ri `locationId` sifatida yozish topilmadi. `EquipmentService` warehouse placementda `equipment.setLocationId(warehouse.getLocationId())` ishlatadi, bu physical warehouse location qoidasiga mos.

Migrationdagi eski noto'g'ri holatlarni normallashtirish bloki ham ko'rildi:

- `WHEN e.location_id = wei.warehouse_id THEN w.location_id`

Testlarda eski setup qoldiqlari bor, lekin ular legacy setup sifatida ko'rindi. Bir stale contract assertion tuzatildi.

## 10. Verification natijasi

- `git diff --check` - muvaffaqiyatli.
- `./mvnw -q -DskipTests compile` - bajarilmadi, `./mvnw` topilmadi.
- `mvn -q -DskipTests compile` - bajarilmadi, `mvn` topilmadi.
- `./gradlew test` - bajarilmadi, `./gradlew` topilmadi.
- `gradle test` - bajarilmadi, `gradle` topilmadi.

## 11. Keyingi qadamlar

- Maven yoki Gradle mavjud muhitda compile va testlarni ishga tushirish.
- Accessory/label/attribute endpointlaridagi direct `departmentId` guardlarini responsible department qoidasi bo'yicha alohida tekshirish.
- Repository JPQL querylarini compile/integration test orqali tasdiqlash.
- Audit bosqichiga o'tishdan oldin yuqoridagi verification blockerlarni yopish.

## 12. Access guard follow-up

- Risky guardlar topildi:
  - `EquipmentManualAttributeController` ichida `equipment.getDepartmentId() == null` va `assertCanAccessDepartment(equipment.getDepartmentId())`.
  - `EquipmentAttributeController` ichida `equipment.getDepartmentId() == null` va `assertCanAccessDepartment(equipment.getDepartmentId())`.
  - `EquipmentScanCompatibilityController` ichida `equipment.getDepartmentId() == null` va `assertCanAccessDepartment(equipment.getDepartmentId())`.
  - `EquipmentLabelController` ichida `equipment.getDepartmentId() == null` va `assertCanAccessDepartment(equipment.getDepartmentId())`.
  - `AnalyticsService.rcaEquipment(...)` ichida `assertCanAccessDepartment(equipment.getDepartmentId())`.
  - `WarehouseEquipmentItemService.assign(...)` ichida `canAccessDepartment(equipment.getDepartmentId())`.
- Tuzatilgan fayllar:
  - `src/main/java/com/toir/controller/equipment/EquipmentManualAttributeController.java`
  - `src/main/java/com/toir/controller/equipment/EquipmentAttributeController.java`
  - `src/main/java/com/toir/controller/equipment/EquipmentScanCompatibilityController.java`
  - `src/main/java/com/toir/controller/equipment/EquipmentLabelController.java`
  - `src/main/java/com/toir/service/AnalyticsService.java`
  - `src/main/java/com/toir/service/WarehouseEquipmentItemService.java`
- Hamma yuqoridagi equipment access guardlar `scopeAccessService.assertCanAccessEquipmentScope(equipment.getResponsibleDepartmentId(), equipment.getDepartmentId())` helperiga o'tkazildi.
- Qolgan grep matchlar equipment current-location access guard emas:
  - PPR, maintenance budget, repair, finance, HR va warehouse-owned resource access checks.
  - Analytics/Dashboard/Reports/Pareto filter guardlari alohida reporting visibility masalasi bo'lib qoladi.
- Testlar yangilandi yoki qo'shildi:
  - Label endpoint warehouse equipment uchun `responsibleDepartmentId` orqali ruxsat beradi.
  - Scan endpoint outside/null physical department holatida `responsibleDepartmentId` orqali ruxsat beradi.
  - Scan endpoint responsible va physical department ikkalasi ham null bo'lsa non-admin uchun deny qiladi.
  - Attribute endpoint null physical department va `responsibleDepartmentId` bilan ruxsat beradi, ikkala scope null bo'lsa deny qiladi.
  - Manual attribute read endpoint null physical department va `responsibleDepartmentId` bilan ruxsat beradi.
  - `AnalyticsService.rcaEquipment(...)` responsible department helperini chaqirishi tekshirildi.
  - `WarehouseEquipmentItemService.assign(...)` responsible department helperini chaqirishi tekshirildi.
- Verification:
  - `git diff --check` - muvaffaqiyatli.
  - `./mvnw -q -DskipTests compile` - bajarilmadi, `./mvnw` topilmadi.
  - `mvn -q -DskipTests compile` - bajarilmadi, `mvn` topilmadi.
  - `./gradlew test` - bajarilmadi, `./gradlew` topilmadi.
  - `gradle test` - bajarilmadi, `gradle` topilmadi.
