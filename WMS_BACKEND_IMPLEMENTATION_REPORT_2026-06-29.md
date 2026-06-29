# TOIR WMS Backend Implementation Report

Report sanasi: 2026-06-29
Branch: `wms-backend-implementation-20260627`
Worktree: `/Users/tenzorsoft/.config/superpowers/worktrees/toir-backend/wms-backend-implementation-20260627`
Asosiy reja: `/Users/tenzorsoft/Desktop/Work/toir/docs/superpowers/plans/2026-06-27-toir-wms-backend-implementation.md`

## Qisqa xulosa

TOIR warehouse moduli oddiy stock accounting holatidan operatsion WMS modeliga kengaytirildi. Asosiy o'zgarishlar: bin/adresli saqlash, lot/serial/expiry/status bo'yicha stock identity, receipt/putaway/pick/issue/return/count/quality/writeoff workflowlari, WMS tasklar, QR/barcode label va scan validation API, status-aware reporting va regression smoke testlar.

Core qoida saqlandi: fizik va reservation stock miqdorini o'zgartirish uchun asosiy writer `ToirStockService` bo'lib qoldi. Yangi WMS service/controllerlar shu core atrofida workflow, validation, API va integratsiya qatlamlarini beradi.

## Bajarilgan tasklar

| Task | Holat | Qisqa mazmun |
| --- | --- | --- |
| Task 1: WMS Foundation Migration and Enums | Bajarildi | WMS migration, yangi enumlar, permission va document/audit targetlar qo'shildi. |
| Task 2: Stock Identity, Core Service, and Coordinate Validation | Bajarildi | Stock identity warehouse/sparePart/bin/lot/serial/expiry/status bo'yicha kengaydi, `ToirStockService` coordinate-aware qilindi. |
| Task 3: Warehouse Bin Management API | Bajarildi | Warehouse bin CRUD, block/unblock, freeze/unfreeze, bin stock balans endpointlari qo'shildi. |
| Task 4: Atomic Bin Move API | Bajarildi | Binlar orasida atomik stock move API va service yaratildi. |
| Task 5: Manual Inventory and Stock Movement Coordinate Propagation | Bajarildi | Inventory/stock movement DTO, entity va service qatlamlariga bin/lot/serial/expiry/status koordinatalari o'tkazildi. |
| Task 6: WMS Document Policy Service | Bajarildi | WMS operatsiyalarida majburiy hujjat/attachment policy validation qo'shildi. |
| Task 7: Procurement and Purchase Order Receiving With Bins and Documents | Bajarildi | Procurement/Purchase Order receipt oqimlari bin va document policy bilan integratsiya qilindi. |
| Task 8: Warehouse Task Management | Bajarildi | Warehouse task/task line modeli, assign/start/scan/complete/cancel workflowlari qo'shildi. |
| Task 9: Work Order Reserve, Pick List, and Issue Workflow | Bajarildi | Work order uchun reserve, pick list va issue workflowlari WMS tasklar bilan bog'landi. |
| Task 10: Work Order Material Returns | Bajarildi | Ishlatilmagan materiallarni warehousega qaytarish modeli va API qo'shildi. |
| Task 11: Inventory Count Sessions | Bajarildi | Count session, count line, review, approve, post adjustments va cancel workflowlari qo'shildi. |
| Task 12: Quality Status, Quarantine, and Writeoff | Bajarildi | AVAILABLE/QUARANTINE/etc statuslar, quality transfer va writeoff flow qo'shildi. |
| Task 13: QR/Barcode Label and Scan Validation API | Bajarildi | Bin, spare part, equipment label payloadlari va scan validation endpointi qo'shildi. |
| Task 14: Query DTOs, Analytics Read Models, and Reconciliation | Bajarildi | Status-aware balance/ledger/reconciliation DTO va query filterlari qo'shildi. |
| Task 15: Backend End-to-End Smoke and Regression Suite | Bajarildi | WMS end-to-end smoke test va targeted regression suite qo'shildi/yurgizildi. |

## Migration va model o'zgarishlari

Yangi WMS foundation migration yaratildi:

- `src/main/resources/db/migration/V20260627_9__wms_backend_foundation.sql`

Migration quyidagi yo'nalishlarni qamrab oladi:

- `warehouse_bins` uchun quality zone, temperature zone, hazard class, barcode, QR payload, mixed lot/spare part flags.
- `warehouse_stock_balances` uchun `stock_status`, quality hold reason, quality check metadata.
- `warehouse_stock_ledgers` va `warehouse_reservation_ledgers` uchun `expiry_date` va `stock_status`.
- `stock_movements`, `inventory_transactions`, `reservations`, `repair_material_usages` uchun WMS koordinatalari.
- `warehouse_equipment_items` uchun bin koordinatasi.
- Yangi WMS jadvallar: `warehouse_tasks`, `warehouse_task_lines`, `inventory_count_sessions`, `inventory_count_lines`, `repair_material_returns`, `warehouse_writeoff_requests`.
- Attachment target va permission setlari WMS operatsiyalariga mos kengaytirildi.

Yangi enumlar qo'shildi:

- `WarehouseStockStatus`
- `WarehouseQualityZoneType`
- `WarehouseTaskType`
- `WarehouseTaskStatus`
- `WarehouseTaskPriority`
- `WarehouseTaskSourceType`
- `WarehouseTaskLineStatus`
- `InventoryCountSessionStatus`
- `InventoryCountScopeType`
- `InventoryCountLineStatus`
- `WmsDocumentOperationType`
- `RepairMaterialReturnStatus`
- `WarehouseWriteoffStatus`

## Core stock logic

`WarehouseStockBalance.identityKey` endi quyidagi dimensionlardan tuziladi:

```text
warehouseId | sparePartId | binId | lotNumber | serialNumber | expiryDate | stockStatus
```

Core behavior:

- `AVAILABLE` bo'lmagan statuslarda `availableQty` doim `0`.
- `qtyReserved > qtyOnHand` holati saqlashdan oldin rad etiladi.
- `stockStatus` null bo'lsa default `AVAILABLE`.
- Ledger va reservation ledger yozuvlarida expiry/status saqlanadi.
- `ToirStockService` receipt, issue, increase, decrease, reserve, release va fulfill operatsiyalarida bin/lot/serial/expiry/status koordinatalarini qabul qiladi.
- Idempotency keylar coordinate-aware oqimlarda saqlanadi.

## Qo'shilgan API va service qatlamlari

### Warehouse bin API

Controller: `WarehouseBinController`
Service: `WarehouseBinService`

Endpointlar:

- `GET /api/v1/warehouses/{warehouseId}/bins`
- `GET /api/v1/warehouses/{warehouseId}/bins/{binId}`
- `POST /api/v1/warehouses/{warehouseId}/bins`
- `PUT /api/v1/warehouses/{warehouseId}/bins/{binId}`
- `POST /api/v1/warehouses/{warehouseId}/bins/{binId}/block`
- `POST /api/v1/warehouses/{warehouseId}/bins/{binId}/unblock`
- `POST /api/v1/warehouses/{warehouseId}/bins/{binId}/freeze`
- `POST /api/v1/warehouses/{warehouseId}/bins/{binId}/unfreeze`
- `GET /api/v1/warehouses/{warehouseId}/bins/{binId}/stock-balances`

### Atomic stock move API

Controller: `WarehouseStockMoveController`
Service: `WarehouseStockMoveService`

Endpoint:

- `POST /api/v1/warehouse/stock-moves`

Maqsad: bitta warehouse ichida stockni bir bin/status koordinatasidan boshqa bin/status koordinatasiga ledger bilan atomik ko'chirish.

### Warehouse task API

Controller: `WarehouseTaskController`
Service: `WarehouseTaskService`

Endpointlar:

- `GET /api/v1/warehouse/tasks`
- `POST /api/v1/warehouse/tasks`
- `POST /api/v1/warehouse/tasks/{id}/assign`
- `POST /api/v1/warehouse/tasks/{id}/start`
- `POST /api/v1/warehouse/tasks/{id}/lines/{lineId}/scan-confirm`
- `POST /api/v1/warehouse/tasks/{id}/complete`
- `POST /api/v1/warehouse/tasks/{id}/cancel`

Qo'llab-quvvatlangan operatsiyalar: receive/putaway/pick/move/count/quality/writeoff kabi WMS task turlari.

### Work order WMS API

Controller: `WorkOrderWmsController`
Service: `WorkOrderWmsService`

Endpointlar:

- `POST /api/v1/work-orders/{workOrderId}/wms-reservations`
- `POST /api/v1/work-orders/{workOrderId}/pick-list`
- `POST /api/v1/work-orders/{workOrderId}/pick-list/{pickListId}/confirm`
- `POST /api/v1/work-orders/{workOrderId}/material-returns`

Maqsad: repair/work order material oqimini WMS reservation, pick, issue va return bilan bog'lash.

### Inventory count API

Controller: `InventoryCountSessionController`
Service: `InventoryCountSessionService`

Endpointlar:

- `POST /api/v1/inventory/count-sessions`
- `GET /api/v1/inventory/count-sessions`
- `GET /api/v1/inventory/count-sessions/{id}`
- `POST /api/v1/inventory/count-sessions/{id}/open`
- `POST /api/v1/inventory/count-sessions/{id}/lines/{lineId}/count`
- `POST /api/v1/inventory/count-sessions/{id}/review`
- `POST /api/v1/inventory/count-sessions/{id}/approve`
- `POST /api/v1/inventory/count-sessions/{id}/post-adjustments`
- `POST /api/v1/inventory/count-sessions/{id}/cancel`

Maqsad: warehouse/bin/spare part scope bo'yicha sanash, variance ko'rish, approve qilish va stock adjustment ledgerlarini post qilish.

### Quality va writeoff API

Controller: `WarehouseQualityController`
Service: `WarehouseQualityService`

Endpointlar:

- `POST /api/v1/warehouse/quality/status-transfer`
- `POST /api/v1/warehouse/writeoffs`
- `POST /api/v1/warehouse/writeoffs/{id}/submit`
- `POST /api/v1/warehouse/writeoffs/{id}/approve`
- `POST /api/v1/warehouse/writeoffs/{id}/post`
- `POST /api/v1/warehouse/writeoffs/{id}/reject`

Maqsad: stockni quarantine/available/writeoff kabi statuslar orasida nazoratli o'tkazish va writeoff approval workflowini yuritish.

### QR/barcode label va scan validation API

Controller: `WmsLabelController`
Service: `WmsLabelService`

Endpointlar:

- `GET /api/v1/warehouse/labels/bins/{binId}`
- `GET /api/v1/warehouse/labels/spare-parts/{sparePartId}`
- `GET /api/v1/warehouse/labels/equipment/{equipmentId}`
- `POST /api/v1/warehouse/scan/validate`

Label payload formatlari:

```text
TOIR-WMS|type=BIN|id=<uuid>|warehouseId=<uuid>|code=<code>
TOIR-WMS|type=SPARE_PART|id=<uuid>|code=<code>
TOIR-WMS|type=EQUIPMENT|id=<uuid>|inventoryNumber=<number>
```

Scan validation:

- `BIN`, `SPARE_PART`, `EQUIPMENT` turlarini tekshiradi.
- Manual fallback faqat bin uchun ishlaydi.
- `taskLineId` berilgan bo'lsa expected type/id task line bo'yicha solishtiriladi.
- Noto'g'ri type/id/task kombinatsiyalari `400 Bad Request` bilan rad etiladi.

## Existing flow integratsiyalari

Quyidagi mavjud oqimlar WMS koordinatalari bilan kengaytirildi:

- Procurement receipt
- Purchase order receipt
- Manual inventory receipt/issue/transfer/return/adjustment
- Stock movement receipt/issue
- Reservation create/release/fulfill
- Repair material usage
- Repair material return
- Warehouse equipment item placement
- Attachment group access checks

DTO/entity/service darajasida quyidagi koordinatalar tarqatildi:

- `warehouseId`
- `binId`, `fromBinId`, `toBinId`
- `lotNumber`
- `serialNumber`
- `expiryDate`
- `stockStatus`

## Reporting va query o'zgarishlari

`WarehouseStockBalanceDto` kengaytirildi:

- `stockStatus`
- `qualityHoldReason`
- `qualityCheckedAt`
- `qualityCheckedById`
- `expiryDate` JSON format: `yyyy-MM-dd`

`WarehouseStockLedgerDto` kengaytirildi:

- `expiryDate`
- `stockStatus`

`GET /api/v1/warehouses/{id}/stock-balances` endpointiga filterlar qo'shildi:

- `binId`
- `sparePartId`
- `stockStatus`
- `lotNumber`
- `serialNumber`

Reconciliation query status-aware qilindi:

- group by: `warehouse_id`, `spare_part_id`, `stock_status`
- projection: `stockStatus`, `legacyBinId`, `legacyBinless`
- legacy stock fallback `AVAILABLE` statusga map qilinadi.

## Security va permissions

Yangi WMS permissionlar `PermissionConstants` va default role mappinglarga qo'shildi. Controller contract/security testlar orqali quyidagi yo'nalishlar tekshirildi:

- Warehouse bin read/write/status operations
- Warehouse task read/execute
- Work order WMS operations
- Inventory count create/read/approve/post
- Warehouse quality/writeoff operations
- Label generation va scan validation

Label controllerdagi asosiy access rules:

- Bin label: `WAREHOUSE_BIN_READ` yoki `STOCK_READ`
- Spare part label: `SPARE_PART_READ` yoki `STOCK_READ`
- Equipment label: `EQUIPMENT_READ` yoki `WAREHOUSE_EQUIPMENT_READ`
- Scan validate: `WAREHOUSE_TASK_EXECUTE`
- Admin/wildcard bypass saqlangan.

## Testlar va verifikatsiya

### O'tgan targeted testlar

```bash
./mvnw -Dtest=WmsLabelServiceTest,WmsLabelControllerContractTest test
```

Natija: PASS, 7 test.

```bash
./mvnw -Dtest=WarehouseControllerContractTest,ToirWarehouseQueryServiceTest,WarehouseAnalyticsControllerContractTest test
```

Natija: PASS, 9 test.

```bash
./mvnw -Dtest=WmsEndToEndSmokeTest test
```

Natija: PASS, 1 test.

```bash
./mvnw -Dtest=WarehouseStockBalanceTest test
```

Natija: PASS, 4 test.

```bash
./mvnw -Dtest='*Wms*,*WarehouseBin*,*WarehouseTask*,*InventoryCount*,*WarehouseQuality*,WarehouseStockBalanceTest,ToirStockServiceTest,ProcurementRequestServiceTest,PurchaseOrderServiceTest,InventoryTransactionServiceTest,ReservationServiceTest,RepairMaterialUsageServiceTest' test
```

Natija: PASS, 184 test.

```bash
./mvnw -DskipTests compile
```

Natija: PASS.

### End-to-end smoke test qamrovi

`WmsEndToEndSmokeTest` quyidagi flowlarni bitta scenario ichida tekshiradi:

1. Warehouse, bin, spare part, work order va requirement yaratish.
2. Purchase receipt orqali `AVAILABLE` qty qabul qilish.
3. `QUARANTINE` qty qabul qilish va available qty nol bo'lishini tekshirish.
4. Work order requirement uchun reservation yaratish.
5. Pick task line scan confirmation qilish.
6. Reservation release qilish.
7. Work order material issue qilish.
8. Repair material return qilish.
9. Inventory count session va variance yaratish.
10. Adjustment decrease post qilish.
11. Ledger sumlari va final balance consistency tekshirish.

Asosiy invariantlar:

- Ledger entrylar soni expected bo'lishi.
- Reservation ledgerda `RESERVE` va `RELEASE` borligi.
- Final `AVAILABLE qtyOnHand = 6`, `qtyReserved = 0`, `availableQty = 6`.
- Final `QUARANTINE qtyOnHand = 2`, `availableQty = 0`.
- Hech bir balance negative emas.
- `qtyReserved <= qtyOnHand`.
- Ledger sumlari final balance bilan mos.

## Full suite holati

Full backend suite ham ishga tushirildi:

```bash
./mvnw test
```

Natija: BUILD FAILURE.

Summary:

```text
Tests run: 3222, Failures: 2, Errors: 63, Skipped: 12
```

WMS bilan bog'liq bitta failure (`WarehouseStockBalanceTest`) tuzatildi va alohida qayta o'tkazildi. Qolgan blockerlar WMS implementationdan tashqarida:

1. `application-test.yml` lokal Postgres `jdbc:postgresql://localhost:5433/toir_demo` ga ulanadi. Bu muhitda `5433` portida Postgres ishlamayapti va `docker` command mavjud emas. Shu sababli ko'p `@DataJpaTest` repository testlari `ApplicationContext` load qilolmaydi.
2. `UniversalAuditBulkWriteBypassContractTest` mavjud faktura repositorylaridagi bulk delete methodlarni ushlayapti:
   - `src/main/java/com/toir/repository/faktura/FakturaUzDocumentType32ServiceRepository.java`
   - `src/main/java/com/toir/repository/faktura/FakturaUzDocumentType32PartRepository.java`
3. `ContractorSupplierLegalDetailsMigrationContractTest` mavjud bo'lmagan eski fayl nomini kutyapti:
   - expected: `V20260627_4__contractor_supplier_legal_details.sql`
   - repo'dagi real fayl: `V20260627_7__contractor_supplier_legal_details.sql`

## Muhim fayllar

Yangi controllerlar:

- `src/main/java/com/toir/controller/WarehouseBinController.java`
- `src/main/java/com/toir/controller/WarehouseStockMoveController.java`
- `src/main/java/com/toir/controller/WarehouseTaskController.java`
- `src/main/java/com/toir/controller/WorkOrderWmsController.java`
- `src/main/java/com/toir/controller/InventoryCountSessionController.java`
- `src/main/java/com/toir/controller/WarehouseQualityController.java`
- `src/main/java/com/toir/controller/WmsLabelController.java`

Yangi service qatlamlari:

- `src/main/java/com/toir/service/warehouse/WarehouseBinService.java`
- `src/main/java/com/toir/service/warehouse/WarehouseStockMoveService.java`
- `src/main/java/com/toir/service/warehouse/WarehouseTaskService.java`
- `src/main/java/com/toir/service/warehouse/WorkOrderWmsService.java`
- `src/main/java/com/toir/service/warehouse/InventoryCountSessionService.java`
- `src/main/java/com/toir/service/warehouse/WarehouseQualityService.java`
- `src/main/java/com/toir/service/warehouse/WmsDocumentPolicyService.java`
- `src/main/java/com/toir/service/warehouse/WmsLabelService.java`
- `src/main/java/com/toir/service/warehouse/WmsStockCoordinateValidator.java`

Yangi entitylar:

- `src/main/java/com/toir/entity/warehouse/WarehouseTask.java`
- `src/main/java/com/toir/entity/warehouse/WarehouseTaskLine.java`
- `src/main/java/com/toir/entity/warehouse/InventoryCountSession.java`
- `src/main/java/com/toir/entity/warehouse/InventoryCountLine.java`
- `src/main/java/com/toir/entity/warehouse/RepairMaterialReturn.java`
- `src/main/java/com/toir/entity/warehouse/WarehouseWriteoffRequest.java`

Asosiy o'zgartirilgan core fayllar:

- `src/main/java/com/toir/service/warehouse/ToirStockService.java`
- `src/main/java/com/toir/entity/warehouse/WarehouseStockBalance.java`
- `src/main/java/com/toir/entity/warehouse/WarehouseStockLedger.java`
- `src/main/java/com/toir/entity/warehouse/WarehouseReservationLedger.java`
- `src/main/java/com/toir/repository/WarehouseStockBalanceRepository.java`
- `src/main/java/com/toir/service/InventoryTransactionService.java`
- `src/main/java/com/toir/service/ProcurementRequestService.java`
- `src/main/java/com/toir/service/PurchaseOrderService.java`
- `src/main/java/com/toir/service/ReservationService.java`
- `src/main/java/com/toir/service/StockMovementService.java`

Yangi WMS smoke/regression testlar:

- `src/test/java/com/toir/wms/WmsEndToEndSmokeTest.java`
- `src/test/java/com/toir/service/warehouse/WmsLabelServiceTest.java`
- `src/test/java/com/toir/controller/WmsLabelControllerContractTest.java`
- `src/test/java/com/toir/service/warehouse/ToirWarehouseQueryServiceTest.java`
- `src/test/java/com/toir/migration/WmsBackendFoundationMigrationContractTest.java`

## Yakuniy holat

WMS backend implementation bo'yicha targeted regression, smoke test va compile verifikatsiyadan o'tdi. Full backend suite esa hozirgi lokal muhit va WMSdan tashqari mavjud contract muammolari sabab to'liq pass bo'lmadi. WMS qamrovidagi aniqlangan regression tuzatildi va qayta tekshirildi.
