# Equipment Location Implementation Plan

## 1. Qisqa qaror

Tanlangan model: `Option C` - joriy location holati `Equipment` entity/table ichida saqlanadi, location o'zgarishlari esa alohida `EquipmentLocationHistory` jadvalida audit/history sifatida yoziladi.

Asosiy qarorlar:

- `Equipment`ga joriy location maydonlari qo'shiladi: `currentLocationType`, `currentWarehouseId`, outside fields.
- PBAC ownership/scope uchun `responsibleDepartmentId` qo'shiladi.
- Mavjud `departmentId` saqlanadi, lekin uning semantikasi "physical current department location" sifatida aniqlashtiriladi.
- Mavjud `locationId` saqlanadi, lekin warehouse id sifatida ishlatilishi to'xtatiladi; u faqat fizik `locations.id` uchun ishlatilishi kerak.
- Warehouse inventory metadata uchun `WarehouseEquipmentItem` qoladi, lekin current physical warehouse source of truth `Equipment.currentWarehouseId` bo'ladi.
- `EquipmentLocationHistory` qo'shiladi va create/update/transfer paytida from/to snapshot saqlaydi.
- `PATCH /api/v1/equipment/{id}/placement` backward-compatible kengaytiriladi va `OUTSIDE_FACILITY`ni qabul qiladi.
- Eski flat request fieldlar (`departmentId`, `warehouseId`, `locationId`) bir transition davrida qo'llab-quvvatlanadi.

`responsibleDepartmentId` nomi va behavioriga qo'shilaman. Hozirgi kodda PBAC qoidalari `departmentId`ga qattiq bog'langan, lekin `departmentId` physical location sifatida null bo'lishi mumkin. Shu sabab `responsibleDepartmentId` aniq va yetarlicha domain-neutral nom: u joylashuv emas, ownership/scope maydoni.

## 2. Tanlangan domain model

### Equipment current fields

`/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/entity/equipment/Equipment.java` quyidagi maydonlar bilan kengaytiriladi:

```java
@Enumerated(EnumType.STRING)
@Column(name = "current_location_type")
private EquipmentLocationType currentLocationType;

// Existing field. Keep as physical department location.
@Column(name = "department_id")
private UUID departmentId;

// Existing field. Keep as physical plant/location reference only.
@Column(name = "location_id")
private UUID locationId;

@Column(name = "current_warehouse_id")
private UUID currentWarehouseId;

@Column(name = "responsible_department_id")
private UUID responsibleDepartmentId;

@Enumerated(EnumType.STRING)
@Column(name = "outside_reason")
private EquipmentOutsideReason outsideReason;

@Column(name = "outside_taken_by")
private String outsideTakenBy;

@Column(name = "outside_recipient_user_id")
private UUID outsideRecipientUserId;

@Column(name = "outside_started_date")
private LocalDate outsideStartedDate;

@Column(name = "outside_expected_return_date")
private LocalDate outsideExpectedReturnDate;

@Column(name = "outside_destination")
private String outsideDestination;

@Column(name = "outside_reason_note")
private String outsideReasonNote;
```

### `departmentId` bo'yicha qaror

`departmentId` rename qilinmaydi. Sabablar:

- U ko'p controller/service/repository/testlarda ishlatiladi.
- Rename katta migration va API compatibility risk tug'diradi.
- Yangi semantika plan va JavaDoc/commentlarda aniq yoziladi: `departmentId` = physical current department.

Keyingi implementatsiyada `Equipment.java`da qisqa comment qo'yish kerak:

```java
// Physical department where equipment is currently installed.
// PBAC ownership is responsibleDepartmentId.
@Column(name = "department_id")
private UUID departmentId;
```

### `locationId` bo'yicha qaror

`locationId` saqlanadi, lekin warehouse id sifatida ishlatilmasligi kerak. Auditda topilgan risk: hozir `moveToWarehouse(...)` `equipment.setLocationId(request.warehouseId())` qiladi. Bu to'xtatiladi.

Yangi qoida:

- `locationId` faqat `locations.id` physical sub-location uchun.
- `WAREHOUSE` uchun `locationId` null yoki warehouse'ning `locationId` qiymati bo'lishi mumkin, lekin `warehouseId`ning o'zi bo'lmasligi kerak.
- `currentWarehouseId` warehouse identity uchun ishlatiladi.
- Migration vaqtida `location_id` active warehouse idga teng bo'lgan qatorlar aniqlanib `current_warehouse_id`ga ko'chiriladi; keyin `location_id` null qilinadi yoki warehouse.location_id bilan to'ldiriladi.

### `currentWarehouseId` bo'yicha qaror

`currentWarehouseId` qo'shiladi. `WarehouseEquipmentItem`ni current location source of truth sifatida davom ettirish tavsiya qilinmaydi, chunki:

- `Equipment` list/detail hozir ham asosiy aggregate sifatida ishlaydi.
- PBAC va filterlar uchun current warehouse to'g'ridan-to'g'ri query qilinishi kerak.
- Active `WarehouseEquipmentItem` inventory status (`AVAILABLE`, `RESERVED`, `OUT_OF_SERVICE`) va warehouse metadata uchun qoladi.

### Final source of truth

Joriy physical location source of truth:

- `Equipment.currentLocationType`
- `Equipment.departmentId` agar `currentLocationType = DEPARTMENT`
- `Equipment.currentWarehouseId` agar `currentLocationType = WAREHOUSE`
- Outside fields agar `currentLocationType = OUTSIDE_FACILITY`

PBAC source of truth:

- `Equipment.responsibleDepartmentId`

Warehouse inventory metadata:

- `WarehouseEquipmentItem` active row, `status`, `active`

History/audit source:

- `EquipmentLocationHistory`
- `AuditLog` detailed snapshot/diff

## 3. Database migration rejasi

### Migration nomi

Mavjud oxirgi migrationlar:

- `V20260602_1__vehicle_document_names.sql`
- `V20260602_2__equipment_documents.sql`

Shu sabab keyingi safe nom:

```txt
V20260602_3__equipment_current_location.sql
```

### Add columns

Migration quyidagilarni qo'shadi:

```sql
ALTER TABLE equipment
    ADD COLUMN IF NOT EXISTS current_location_type varchar(64),
    ADD COLUMN IF NOT EXISTS current_warehouse_id uuid,
    ADD COLUMN IF NOT EXISTS responsible_department_id uuid,
    ADD COLUMN IF NOT EXISTS outside_reason varchar(64),
    ADD COLUMN IF NOT EXISTS outside_taken_by varchar(255),
    ADD COLUMN IF NOT EXISTS outside_recipient_user_id uuid,
    ADD COLUMN IF NOT EXISTS outside_started_date date,
    ADD COLUMN IF NOT EXISTS outside_expected_return_date date,
    ADD COLUMN IF NOT EXISTS outside_destination varchar(255),
    ADD COLUMN IF NOT EXISTS outside_reason_note text;
```

### Check constraints

Initial migrationda `current_location_type` nullable qoladi, chunki null/null eski data manual review talab qilishi mumkin. Enum constraint qo'shiladi:

```sql
ALTER TABLE equipment
    ADD CONSTRAINT chk_equipment_current_location_type
    CHECK (
        current_location_type IS NULL
        OR current_location_type IN ('DEPARTMENT', 'WAREHOUSE', 'OUTSIDE_FACILITY')
    );
```

Outside reason constraint:

```sql
ALTER TABLE equipment
    ADD CONSTRAINT chk_equipment_outside_reason
    CHECK (
        outside_reason IS NULL
        OR outside_reason IN (
            'BUSINESS_TRIP',
            'SERVICE',
            'RENTED_OUT',
            'ON_ROAD',
            'TEMPORARY_USE',
            'EXTERNAL_ORGANIZATION',
            'INSTALLATION',
            'CALIBRATION',
            'INSPECTION',
            'OTHER'
        )
    );
```

Date constraint:

```sql
ALTER TABLE equipment
    ADD CONSTRAINT chk_equipment_outside_expected_return_date
    CHECK (
        outside_expected_return_date IS NULL
        OR outside_started_date IS NULL
        OR outside_expected_return_date >= outside_started_date
    );
```

Full exactly-one DB constraintni birinchi migrationda juda agressiv qilmaslik kerak, chunki existing data drift bor. Service validation birinchi himoya bo'ladi. Keyingi hardening migration data tozalangandan keyin qo'shiladi.

### FKlar

Department FK:

```sql
ALTER TABLE equipment
    ADD CONSTRAINT fk_equipment_responsible_department
    FOREIGN KEY (responsible_department_id) REFERENCES departments (id);
```

Warehouse FK:

```sql
ALTER TABLE equipment
    ADD CONSTRAINT fk_equipment_current_warehouse
    FOREIGN KEY (current_warehouse_id) REFERENCES warehouses (id);
```

Outside recipient user FK safe, chunki `users` table mavjud:

```sql
ALTER TABLE equipment
    ADD CONSTRAINT fk_equipment_outside_recipient_user
    FOREIGN KEY (outside_recipient_user_id) REFERENCES users (id);
```

Eslatma: agar soft-deleted users bilan historical relation kerak bo'lsa, FK baribir foydali; soft-delete rowni o'chirmaydi.

### Indexlar

```sql
CREATE INDEX IF NOT EXISTS idx_equipment_current_location_type
    ON equipment (current_location_type)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_equipment_responsible_department_id
    ON equipment (responsible_department_id)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_equipment_current_warehouse_id
    ON equipment (current_warehouse_id)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_equipment_outside_expected_return_date
    ON equipment (outside_expected_return_date)
    WHERE is_deleted = false AND outside_expected_return_date IS NOT NULL;
```

### History table

```sql
CREATE TABLE IF NOT EXISTS equipment_location_history (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    is_deleted boolean NOT NULL DEFAULT false,

    equipment_id uuid NOT NULL,

    from_location_type varchar(64),
    from_department_id uuid,
    from_warehouse_id uuid,
    from_outside_reason varchar(64),
    from_outside_taken_by varchar(255),
    from_outside_recipient_user_id uuid,
    from_outside_started_date date,
    from_outside_expected_return_date date,
    from_outside_destination varchar(255),
    from_outside_reason_note text,

    to_location_type varchar(64) NOT NULL,
    to_department_id uuid,
    to_warehouse_id uuid,
    to_outside_reason varchar(64),
    to_outside_taken_by varchar(255),
    to_outside_recipient_user_id uuid,
    to_outside_started_date date,
    to_outside_expected_return_date date,
    to_outside_destination varchar(255),
    to_outside_reason_note text,

    responsible_department_id uuid,
    changed_by uuid,
    changed_at timestamptz NOT NULL,
    note text,

    CONSTRAINT fk_equipment_location_history_equipment
        FOREIGN KEY (equipment_id) REFERENCES equipment (id),
    CONSTRAINT fk_equipment_location_history_responsible_department
        FOREIGN KEY (responsible_department_id) REFERENCES departments (id),
    CONSTRAINT fk_equipment_location_history_changed_by
        FOREIGN KEY (changed_by) REFERENCES users (id)
);
```

History indexlar:

```sql
CREATE INDEX IF NOT EXISTS idx_equipment_location_history_equipment_id
    ON equipment_location_history (equipment_id)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_equipment_location_history_changed_at
    ON equipment_location_history (changed_at)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_equipment_location_history_changed_by
    ON equipment_location_history (changed_by)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_equipment_location_history_to_location_type
    ON equipment_location_history (to_location_type)
    WHERE is_deleted = false;
```

### Backfill strategy

1. Department physical location:

```sql
UPDATE equipment
SET current_location_type = 'DEPARTMENT',
    responsible_department_id = COALESCE(responsible_department_id, department_id)
WHERE is_deleted = false
  AND department_id IS NOT NULL;
```

2. Warehouse physical location, department null:

```sql
UPDATE equipment e
SET current_location_type = 'WAREHOUSE',
    current_warehouse_id = wei.warehouse_id,
    responsible_department_id = COALESCE(
        e.responsible_department_id,
        w.department_id
    ),
    location_id = CASE
        WHEN e.location_id = wei.warehouse_id THEN w.location_id
        ELSE e.location_id
    END
FROM warehouse_equipment_items wei
JOIN warehouses w ON w.id = wei.warehouse_id AND w.is_deleted = false
WHERE e.id = wei.equipment_id
  AND e.is_deleted = false
  AND wei.active = true
  AND wei.is_deleted = false
  AND e.department_id IS NULL;
```

3. Null/null review:

`current_location_type` null qolgan qatorlar manual data auditga chiqariladi:

```sql
-- Manual verification query, migration ichida comment sifatida qoldirish mumkin:
SELECT id, code, name, inventory_number, department_id, location_id
FROM equipment
WHERE is_deleted = false
  AND current_location_type IS NULL;
```

### UNKNOWN qarori

`UNKNOWN` DB enum qiymati qo'shilmaydi. Sabab:

- `UNKNOWN` biznes holat emas, eski data drift signali.
- DBda `UNKNOWN` qoldirilsa keyin real workflowga kirib ketadi.
- Transition davrida `current_location_type` nullable bo'lishi mumkin; response fallback `PlacementType.UNKNOWN` saqlanadi.
- Data tozalangandan keyin hardening migration bilan `current_location_type NOT NULL` qo'yiladi.

## 4. Entity o'zgarishlari

### Modify: Equipment

File: `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/entity/equipment/Equipment.java`

Main changes:

- Import `EquipmentLocationType`, `EquipmentOutsideReason`.
- Add fields listed in section 2.
- Keep `departmentId` and `locationId`.
- Add comments clarifying:
  - `departmentId` physical department
  - `responsibleDepartmentId` PBAC ownership
  - `locationId` physical `locations` reference, not warehouse id

### Add: EquipmentLocationHistory

File: `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/entity/equipment/EquipmentLocationHistory.java`

Recommended entity:

```java
@Entity
@Table(name = "equipment_location_history")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class EquipmentLocationHistory extends BaseEntity {

    @Column(name = "equipment_id", nullable = false)
    private UUID equipmentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_location_type")
    private EquipmentLocationType fromLocationType;

    @Column(name = "from_department_id")
    private UUID fromDepartmentId;

    @Column(name = "from_warehouse_id")
    private UUID fromWarehouseId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_outside_reason")
    private EquipmentOutsideReason fromOutsideReason;

    @Column(name = "from_outside_taken_by")
    private String fromOutsideTakenBy;

    @Column(name = "from_outside_recipient_user_id")
    private UUID fromOutsideRecipientUserId;

    @Column(name = "from_outside_started_date")
    private LocalDate fromOutsideStartedDate;

    @Column(name = "from_outside_expected_return_date")
    private LocalDate fromOutsideExpectedReturnDate;

    @Column(name = "from_outside_destination")
    private String fromOutsideDestination;

    @Column(name = "from_outside_reason_note", columnDefinition = "text")
    private String fromOutsideReasonNote;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_location_type", nullable = false)
    private EquipmentLocationType toLocationType;

    @Column(name = "to_department_id")
    private UUID toDepartmentId;

    @Column(name = "to_warehouse_id")
    private UUID toWarehouseId;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_outside_reason")
    private EquipmentOutsideReason toOutsideReason;

    @Column(name = "to_outside_taken_by")
    private String toOutsideTakenBy;

    @Column(name = "to_outside_recipient_user_id")
    private UUID toOutsideRecipientUserId;

    @Column(name = "to_outside_started_date")
    private LocalDate toOutsideStartedDate;

    @Column(name = "to_outside_expected_return_date")
    private LocalDate toOutsideExpectedReturnDate;

    @Column(name = "to_outside_destination")
    private String toOutsideDestination;

    @Column(name = "to_outside_reason_note", columnDefinition = "text")
    private String toOutsideReasonNote;

    @Column(name = "responsible_department_id")
    private UUID responsibleDepartmentId;

    @Column(name = "changed_by")
    private UUID changedBy;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    @Column(columnDefinition = "text")
    private String note;
}
```

Full outside snapshot saqlansin. Bu og'ir emas, chunki history row transfer paytida yoziladi, list read pathga kirmaydi, lekin auditda from/to ni to'liq tiklash imkonini beradi.

## 5. Enum o'zgarishlari

### Add: EquipmentLocationType

File: `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/enums/EquipmentLocationType.java`

```java
package com.toir.enums;

public enum EquipmentLocationType {
    DEPARTMENT,
    WAREHOUSE,
    OUTSIDE_FACILITY
}
```

### Add: EquipmentOutsideReason

File: `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/enums/EquipmentOutsideReason.java`

```java
package com.toir.enums;

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

### PlacementTargetType

File: `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/enums/PlacementTargetType.java`

Backward compatibility uchun `PlacementTargetType`ga `OUTSIDE_FACILITY` qo'shish mumkin:

```java
public enum PlacementTargetType {
    WAREHOUSE,
    DEPARTMENT,
    OUTSIDE_FACILITY
}
```

Yangi code pathda `EquipmentLocationType` ishlatiladi. `PlacementTargetType` faqat eski `targetType` payloadni qabul qilish uchun adapter bo'lib qoladi.

### LOST / DECOMMISSIONED / SERVICE status qarori

- `DECOMMISSIONED` `EquipmentStatus`da qoladi. U location reason emas.
- `LOST` location reason bo'lmasin. Product owner tasdiqlasa `EquipmentStatus.LOST` sifatida alohida migration/enum change bilan qo'shiladi.
- `SERVICE` outside reason avtomatik `EquipmentStatus.IN_REPAIR`ga o'zgartirmasin. Hozir status lifecycle alohida `EquipmentStatusLifecycleService` orqali boshqariladi; avtomatik status change mavjud behaviorni buzishi mumkin.

## 6. DTO / API contract

### Add: EquipmentLocationRequest

File: `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/dto/equipment/EquipmentLocationRequest.java`

Fields:

```java
public record EquipmentLocationRequest(
        EquipmentLocationType locationType,
        UUID departmentId,
        UUID warehouseId,
        UUID locationId,
        UUID responsibleDepartmentId,
        EquipmentOutsideReason outsideReason,
        String outsideTakenBy,
        UUID outsideRecipientUserId,
        LocalDate outsideStartedDate,
        LocalDate outsideExpectedReturnDate,
        String outsideDestination,
        String outsideReasonNote,
        WarehouseEquipmentStatus warehouseStatus
) {}
```

`warehouseStatus` faqat placement/create warehouse assignment metadata uchun kerak. Response ichida warehouse item statusdan keladi.

### Add: EquipmentTransferRequest

File: `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/dto/equipment/EquipmentTransferRequest.java`

```java
public record EquipmentTransferRequest(
        @NotNull EquipmentLocationRequest targetLocation,
        String note
) {}
```

### Add: EquipmentLocationDto

File: `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/dto/equipment/EquipmentLocationDto.java`

Fields:

- `EquipmentLocationType type`
- `EquipmentDto.Ref department`
- `EquipmentDto.Ref warehouse`
- `WarehouseEquipmentStatus warehouseStatus`
- `EquipmentDto.Ref location`
- `UUID responsibleDepartmentId`
- `EquipmentOutsideReason outsideReason`
- `String outsideTakenBy`
- `UUID outsideRecipientUserId`
- `LocalDate outsideStartedDate`
- `LocalDate outsideExpectedReturnDate`
- `String outsideDestination`
- `String outsideReasonNote`
- `boolean overdue`

### Modify: EquipmentCreateRequest

File: `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/dto/equipment/EquipmentCreateRequest.java`

Add:

```java
EquipmentLocationRequest location
```

Keep existing flat:

- `UUID departmentId`
- `UUID warehouseId`
- `UUID locationId`

Create resolution order:

1. If `location != null`, use nested location.
2. Else infer from flat fields:
   - `warehouseId != null` and `departmentId == null` -> `WAREHOUSE`
   - `departmentId != null` and `warehouseId == null` -> `DEPARTMENT`
   - both present -> reject in new validation
   - both missing -> reject

### Modify: EquipmentUpdateRequest

File: `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/dto/equipment/EquipmentUpdateRequest.java`

Add:

```java
EquipmentLocationRequest location
```

Update semantics:

- `location == null`: old behavior, non-location fields update; old flat `departmentId/locationId` are still accepted for transition.
- `location != null`: explicit location update. Nulls inside location are interpreted according to `locationType`, not "no-op".

### Modify: EquipmentPlacementRequest

File: `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/dto/equipment/EquipmentPlacementRequest.java`

Keep old fields:

- `targetType`
- `warehouseId`
- `departmentId`
- `warehouseStatus`

Add:

```java
EquipmentLocationRequest targetLocation,
String note
```

Resolution order:

1. If `targetLocation != null`, use it.
2. Else adapt old fields into `EquipmentLocationRequest`.

### Modify: EquipmentDto

File: `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/dto/equipment/EquipmentDto.java`

Keep existing top-level fields for compatibility:

- `departmentId`
- `locationId`

Add top-level fields only if needed for compatibility:

- `currentLocationType`
- `currentWarehouseId`
- `responsibleDepartmentId`

Preferred response change:

- Extend existing `PlacementRef` rather than adding a second nested `location` initially.
- Later frontend can migrate to `placement` as canonical current location object.

Extended `PlacementRef`:

```java
public record PlacementRef(
        EquipmentLocationType type,
        Ref department,
        Ref warehouse,
        WarehouseEquipmentStatus warehouseStatus,
        Ref location,
        UUID responsibleDepartmentId,
        EquipmentOutsideReason outsideReason,
        String outsideTakenBy,
        UUID outsideRecipientUserId,
        LocalDate outsideStartedDate,
        LocalDate outsideExpectedReturnDate,
        String outsideDestination,
        String outsideReasonNote,
        boolean overdue
) {}
```

Eslatma: eski `PlacementType` enum `UNKNOWN` fallback uchun vaqtincha qolishi mumkin, lekin yangi response `EquipmentLocationType`ga o'tishi tavsiya qilinadi. Agar frontend breaking change xavfli bo'lsa, eski `PlacementType`ni `OUTSIDE_FACILITY` bilan kengaytirib ishlatish ham mumkin.

### API examples

Old create payload still works:

```json
{
  "name": "Pump",
  "inventoryNumber": "INV-1",
  "equipmentTypeId": "00000000-0000-0000-0000-000000000001",
  "departmentId": "00000000-0000-0000-0000-00000000d002",
  "averageOperatingLifeHours": 10000
}
```

New create outside:

```json
{
  "name": "Portable compressor",
  "inventoryNumber": "INV-OUT-1",
  "equipmentTypeId": "00000000-0000-0000-0000-000000000001",
  "averageOperatingLifeHours": 10000,
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
    "outsideReasonNote": null,
    "responsibleDepartmentId": "00000000-0000-0000-0000-00000000d002"
  }
}
```

Transfer outside:

```json
{
  "targetLocation": {
    "locationType": "OUTSIDE_FACILITY",
    "outsideReason": "BUSINESS_TRIP",
    "outsideTakenBy": "Toshmat",
    "outsideStartedDate": "2026-06-02",
    "outsideExpectedReturnDate": "2026-06-10",
    "outsideDestination": "Toshkent",
    "responsibleDepartmentId": "00000000-0000-0000-0000-00000000d002"
  },
  "note": "Vaqtincha berildi"
}
```

Response placement:

```json
{
  "type": "OUTSIDE_FACILITY",
  "department": null,
  "warehouse": null,
  "warehouseStatus": null,
  "location": null,
  "responsibleDepartmentId": "00000000-0000-0000-0000-00000000d002",
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

## 7. Service validation qoidalari

Validationni bitta helper/servicega ajratish tavsiya qilinadi:

File: `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/service/equipment/EquipmentLocationValidator.java`

### General rules

- `locationType` required when explicit `location`/`targetLocation` exists.
- Exactly one physical target active:
  - `DEPARTMENT`: `departmentId` required, `warehouseId/currentWarehouseId` null, outside fields null.
  - `WAREHOUSE`: `warehouseId` required, `departmentId` null, outside fields null.
  - `OUTSIDE_FACILITY`: department/warehouse physical target null, outside rules apply.
- Empty strings are trimmed.
- Blank strings become null before validation.
- Invalid enum values return 400 via existing `GlobalExceptionHandler`.
- Null must not clear update location unless explicit `location` object exists.
- `responsibleDepartmentId` must be non-null after default/inference.

### DEPARTMENT

- `departmentId` required.
- `warehouseId` / `currentWarehouseId` must be null.
- Outside fields must be null:
  - `outsideReason`
  - `outsideTakenBy`
  - `outsideRecipientUserId`
  - `outsideStartedDate`
  - `outsideExpectedReturnDate`
  - `outsideDestination`
  - `outsideReasonNote`
- Department must exist via `DepartmentRepository.findByIdAndIsDeletedFalse`.
- User must have access to department.
- `responsibleDepartmentId` defaults to `departmentId` if missing.
- If `responsibleDepartmentId` provided and differs from `departmentId`, allow only scope admin unless product owner approves split ownership.

### WAREHOUSE

- `warehouseId` required.
- Physical `departmentId` must be null.
- Outside fields must be null.
- Warehouse must exist via `WarehouseRepository.findByIdAndIsDeletedFalse`.
- User must have access to warehouse using existing warehouse scope logic.
- `warehouseStatus` remains limited to `AVAILABLE` or `OUT_OF_SERVICE` for placement into warehouse.
- `responsibleDepartmentId` resolution:
  1. provided value if user can access it,
  2. existing equipment `responsibleDepartmentId`,
  3. warehouse.departmentId if non-null,
  4. current user department if non-admin and non-null,
  5. reject.

### OUTSIDE_FACILITY

- `outsideReason` required.
- `outsideStartedDate` required; if omitted on create/transfer, default to `LocalDate.now()` in service.
- `outsideExpectedReturnDate` optional, but if present must be `>= outsideStartedDate`.
- `outsideReasonNote` required when `outsideReason == OTHER`.
- `outsideDestination` required for:
  - `SERVICE`
  - `RENTED_OUT`
  - `EXTERNAL_ORGANIZATION`
  - `INSTALLATION`
  - `CALIBRATION`
  - `INSPECTION`
  - `BUSINESS_TRIP`
- `outsideTakenBy` and `outsideRecipientUserId` rule:
  - allow either one,
  - reject both if both are provided, to avoid ambiguous authoritative recipient,
  - for `TEMPORARY_USE`, require one of them.
- If `outsideRecipientUserId` provided, user must exist via `UserRepository.findByIdAndIsDeletedFalse`.
- `responsibleDepartmentId` resolution:
  1. provided value if user can access it,
  2. existing equipment `responsibleDepartmentId`,
  3. existing physical `departmentId`,
  4. current user department if non-admin,
  5. reject.
- User must have access to final `responsibleDepartmentId`.

## 8. Transfer logic rejasi

All transfer logic should pass through one method:

```java
EquipmentDto updatePlacement(UUID id, EquipmentPlacementRequest request)
```

Internally:

1. Resolve `EquipmentLocationRequest target`.
2. Snapshot current location.
3. Validate source access and target access.
4. Apply field clearing/setting.
5. Sync warehouse item state.
6. Save equipment.
7. Write `EquipmentLocationHistory`.
8. Write audit log.
9. Return enriched DTO.

### Department -> Warehouse

Set:

- `currentLocationType = WAREHOUSE`
- `departmentId = null`
- `currentWarehouseId = target.warehouseId`
- `responsibleDepartmentId = target.responsibleDepartmentId` or previous responsible/department
- `locationId = warehouse.locationId` if available, else null

Clear:

- all outside fields

Warehouse item:

- Use `WarehouseEquipmentItemService.transferEquipmentToWarehouse(...)`.
- It should no longer set `locationId = warehouseId`; it should update only inventory assignment/status and optionally current warehouse through caller-owned logic.

History:

- from `DEPARTMENT`, fromDepartmentId old `departmentId`
- to `WAREHOUSE`, toWarehouseId target

Audit:

- `AuditAction.UPDATE`, `AuditModule.EQUIPMENT`, message: `Equipment location changed: DEPARTMENT -> WAREHOUSE`

### Warehouse -> Department

Set:

- `currentLocationType = DEPARTMENT`
- `departmentId = target.departmentId`
- `currentWarehouseId = null`
- `responsibleDepartmentId = target.responsibleDepartmentId` or target department
- `locationId = target.locationId` if provided

Clear:

- outside fields

Warehouse item decision:

- Current project behavior sets active warehouse item status to `INSTALLED`.
- Recommended cleanup: for a true physical move out of warehouse, close active warehouse item (`active=false`, `isDeleted=true`) or create a new status meaning "ISSUED/INSTALLED" only if warehouse inventory needs installed metadata.
- For safest compatibility in first implementation: keep existing `INSTALLED` behavior for warehouse -> department, but ensure `Equipment.currentWarehouseId = null`; treat active `INSTALLED` item as historical/metadata, not current location.
- Add follow-up cleanup task if product confirms active item should close.

History:

- from `WAREHOUSE`, fromWarehouseId old `currentWarehouseId`
- to `DEPARTMENT`, toDepartmentId target

### Department -> Outside

Set:

- `currentLocationType = OUTSIDE_FACILITY`
- `departmentId = null`
- `currentWarehouseId = null`
- outside fields from target
- `responsibleDepartmentId = target.responsibleDepartmentId` or old `departmentId`
- `locationId = null`

Clear:

- warehouse physical fields

Warehouse item:

- none expected. If stray active warehouse item exists, reject or close only with explicit cleanup decision. Recommended first implementation: reject drift unless active item status is `INSTALLED`.

History:

- from `DEPARTMENT`
- to `OUTSIDE_FACILITY` with full outside snapshot

### Warehouse -> Outside

Set:

- `currentLocationType = OUTSIDE_FACILITY`
- `departmentId = null`
- `currentWarehouseId = null`
- outside fields from target
- `responsibleDepartmentId` from target or existing responsible/warehouse department
- `locationId = null`

Warehouse item:

- Recommended: close active `WarehouseEquipmentItem` (`active=false`, `isDeleted=true`) because equipment physically left warehouse.
- If product wants warehouse custody retained, keep active item with a new status is needed; current enum does not have `ISSUED_OUTSIDE`, so do not overload `AVAILABLE`.

History:

- from `WAREHOUSE`
- to `OUTSIDE_FACILITY`

### Outside -> Department

Set:

- `currentLocationType = DEPARTMENT`
- `departmentId = target.departmentId`
- `currentWarehouseId = null`
- `responsibleDepartmentId = target.responsibleDepartmentId` or target department
- `locationId = target.locationId` if provided

Clear:

- all outside fields

Warehouse item:

- no active item should exist; if it exists, close or reject drift based on validation. Recommended: reject unless item is inactive.

History:

- full outside snapshot in from fields
- to department

### Outside -> Warehouse

Set:

- `currentLocationType = WAREHOUSE`
- `departmentId = null`
- `currentWarehouseId = target.warehouseId`
- `responsibleDepartmentId = target.responsibleDepartmentId` or previous responsible
- `locationId = warehouse.locationId` if available

Clear:

- outside fields

Warehouse item:

- create/activate assignment via `WarehouseEquipmentItemService.transferEquipmentToWarehouse(...)`.

History:

- from outside snapshot
- to warehouse

### Outside -> Outside

Set:

- `currentLocationType = OUTSIDE_FACILITY`
- replace outside fields with target
- keep or change `responsibleDepartmentId` only if target allowed

Clear:

- physical department/warehouse fields

Warehouse item:

- none

History:

- from outside snapshot
- to outside snapshot

### Create directly outside

Set same fields as Department -> Outside but from snapshot is null. History row is still created with `fromLocationType = null`, `toLocationType = OUTSIDE_FACILITY`.

## 9. PBAC / RBAC rejasi

### ScopeAccessService

File: `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/security/ScopeAccessService.java`

Add helper:

```java
public boolean canAccessEquipmentScope(UUID responsibleDepartmentId, UUID physicalDepartmentId) {
    UUID scopeDepartment = responsibleDepartmentId != null ? responsibleDepartmentId : physicalDepartmentId;
    return canAccessDepartment(scopeDepartment);
}
```

Add assert helper:

```java
public void assertCanAccessEquipmentScope(UUID responsibleDepartmentId, UUID physicalDepartmentId) {
    if (!canAccessEquipmentScope(responsibleDepartmentId, physicalDepartmentId)) {
        throwAccessDenied();
    }
}
```

### EquipmentController

File: `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/controller/equipment/EquipmentController.java`

Change `assertCanAccessEquipment(...)`:

- If scope admin: allow.
- Else use `responsibleDepartmentId` first.
- Fallback to `departmentId` for old rows.
- If both null: deny.

### VehicleController

File: `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/controller/VehicleController.java`

Same access behavior as equipment. Vehicles can be outside/on road, so `departmentId == null` must not automatically deny if `responsibleDepartmentId` is accessible.

### AnalyticsService

File: `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/service/AnalyticsService.java`

Change analytics filtering:

- Use `responsibleDepartmentId` when present.
- Fallback to `departmentId`.
- Non-admin with no accessible responsible/physical department is denied.

### Equipment list/search scope

Controller should pass `responsibleDepartmentId` filter for non-admin. Service/repository should filter by:

```sql
coalesce(e.responsibleDepartmentId, e.departmentId) = :scopedDepartmentId
```

### Warehouse transfer scope

Rules:

- Source equipment access: current responsible/physical department.
- Target department access: `departmentId`.
- Target warehouse access: existing warehouse scope behavior.
- Target responsible department access: required if provided/different.

### RBAC

Existing `EQUIPMENT_TRANSFER` remains sufficient for normal location transfer. Additional product decision may add:

- `EQUIPMENT_LOCATION_HISTORY_READ`
- `EQUIPMENT_MARK_LOST`

Do not introduce new permissions in first pass unless product confirms.

## 10. Repository / query o'zgarishlari

### EquipmentRepository.search

File: `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/repository/equipment/EquipmentRepository.java`

Add params:

- `UUID responsibleDepartmentId`
- `EquipmentLocationType locationType`
- `UUID warehouseId`
- `EquipmentOutsideReason outsideReason`
- `Boolean overdueOnly`
- `LocalDate today`

Query behavior:

- PBAC scope:
  - `(:responsibleDepartmentId is null or coalesce(e.responsibleDepartmentId, e.departmentId) = :responsibleDepartmentId)`
- Location type:
  - `(:locationType is null or e.currentLocationType = :locationType)`
- Warehouse:
  - `(:warehouseId is null or e.currentWarehouseId = :warehouseId)`
- Outside reason:
  - `(:outsideReason is null or e.outsideReason = :outsideReason)`
- Overdue:
  - `(:overdueOnly = false or (e.currentLocationType = OUTSIDE_FACILITY and e.outsideExpectedReturnDate < :today))`

### EquipmentController.list query params

Add:

- `EquipmentLocationType locationType`
- `EquipmentOutsideReason outsideReason`
- `Boolean overdueOnly`

Keep existing:

- `departmentId`
- `warehouseId`
- `availableForReplacement`

`warehouseId` must work as general location filter when `availableForReplacement=false`.

### Stats

`getEquipmentStats(...)` should use responsible department filter:

```java
coalesce(e.responsibleDepartmentId, e.departmentId)
```

Stats should include outside equipment under its responsible department.

### Analytics

Analytics service/repository flows that load equipment by department should use responsible department fallback.

### Replacement query

`searchAvailableForReplacement(...)` must include only `currentLocationType = WAREHOUSE` and matching `currentWarehouseId` unless product explicitly allows outside equipment as replacement.

## 11. Audit log va history rejasi

### History write triggers

History must be written on:

- Equipment create
- Explicit location update in `PUT /equipment/{id}`
- Placement transfer in `PATCH /equipment/{id}/placement`
- Return from outside to department/warehouse
- Warehouse assignment if it changes current location
- Department assignment if it changes current location

### History snapshot

Add helper in `EquipmentService` or new `EquipmentLocationHistoryService`:

```java
EquipmentLocationSnapshot snapshot(Equipment equipment)
```

Snapshot fields:

- `locationType`
- `departmentId`
- `warehouseId`
- all outside fields
- `responsibleDepartmentId`

History row uses:

- from snapshot before mutation
- to snapshot after mutation
- `changedBy = scopeAccessService.currentUserIdOrNull()`
- `changedAt = Instant.now()`
- `note = request.note()`

### AuditBuilderService usage

For create:

- action `CREATE`
- module `EQUIPMENT`
- message: `Equipment created with location: <type>`

For transfer/update:

- action `UPDATE`
- module `EQUIPMENT`
- message: `Equipment location changed: <from> -> <to>`
- old snapshot and new snapshot should be passed so `diff_json` captures changed location fields.

### History repository

File: `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/repository/equipment/EquipmentLocationHistoryRepository.java`

Methods:

```java
Page<EquipmentLocationHistory> findAllByEquipmentIdAndIsDeletedFalseOrderByChangedAtDesc(UUID equipmentId, Pageable pageable);
```

Optional endpoint can be added later:

- `GET /api/v1/equipment/{id}/location-history`

If added in first implementation, protect with `EQUIPMENT_READ` and source equipment PBAC.

## 12. Backward compatibility rejasi

### Old create payload

Still accepted:

- `departmentId`
- `warehouseId`
- `locationId`

Rules:

- `departmentId` only -> `DEPARTMENT`
- `warehouseId` only -> `WAREHOUSE`
- both -> reject with clear 400
- neither -> reject unless nested location exists

### Old update payload

Still accepted:

- `departmentId`
- `locationId`

But recommend deprecation warning in API docs. For old update:

- If `departmentId` provided, interpret as move/update to physical department.
- If `location` nested provided, nested wins.

### Old placement endpoint

Existing payload keeps working:

```json
{
  "targetType": "DEPARTMENT",
  "departmentId": "..."
}
```

```json
{
  "targetType": "WAREHOUSE",
  "warehouseId": "...",
  "warehouseStatus": "AVAILABLE"
}
```

New payload:

```json
{
  "targetLocation": {
    "locationType": "OUTSIDE_FACILITY",
    "outsideReason": "SERVICE",
    "outsideStartedDate": "2026-06-02",
    "outsideDestination": "Servis markazi",
    "responsibleDepartmentId": "..."
  },
  "note": "Servisga yuborildi"
}
```

### Unknown outside fields

After implementation, frontend should not send outside fields as unknown flat fields. Backend should either:

- accept them only inside `location` / `targetLocation`, or
- explicitly add `@JsonAlias` for temporary flat compatibility if current frontend already sends flat outside fields.

Contract tests must confirm outside fields are not silently lost.

### Deprecation path

1. Release supports both old flat fields and new nested location.
2. OpenAPI/docs mark old flat location fields deprecated.
3. Frontend migrates to nested `location` and `targetLocation`.
4. Later backend removes flat location write support only after product approval.

## 13. Frontend integration notes

Frontend should eventually send:

- Create equipment: `location.locationType`.
- Update equipment: `location` object only when location is intentionally changed.
- Transfer equipment: `targetLocation` + `note`.
- Outside detail/table: show outside reason, destination, takenBy/recipient, started date, expected return date, overdue.
- Filtering: use `locationType`, `warehouseId`, `outsideReason`, `overdueOnly`.
- zod validation should mirror backend:
  - exactly one location type
  - `OTHER` requires note
  - expected return >= started
  - destination required for service/rent/business trip/etc.
  - either `outsideTakenBy` or `outsideRecipientUserId`, not both
- Do not send flat `outsideReason`, `outsideTakenBy`, etc. before backend support exists; old backend ignores unknown fields.

## 14. Test rejasi

### Service tests

File: `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/test/java/com/toir/service/equipment/EquipmentServiceTest.java`

Add tests:

- `createWithDepartmentSetsCurrentLocationDepartmentAndResponsibleDepartment`
- `createWithWarehouseSetsCurrentLocationWarehouseAndCurrentWarehouseId`
- `createWithOutsideFacilitySetsOutsideFieldsAndResponsibleDepartment`
- `transferDepartmentToWarehouseClearsDepartmentAndOutsideFields`
- `transferWarehouseToDepartmentClearsCurrentWarehouseAndOutsideFields`
- `transferDepartmentToOutsideClearsPhysicalLocationAndWritesOutsideFields`
- `transferWarehouseToOutsideClosesWarehouseItemAndWritesOutsideFields`
- `transferOutsideToDepartmentClearsOutsideFields`
- `transferOutsideToWarehouseClearsOutsideFieldsAndCreatesWarehouseAssignment`
- `transferOutsideToOutsideReplacesOutsideSnapshot`
- `outsideOtherRequiresReasonNote`
- `outsideExpectedReturnBeforeStartedRejected`
- `departmentAndWarehouseTogetherRejected`
- `responsibleDepartmentDefaultsToDepartmentOnDepartmentCreate`
- `responsibleDepartmentInferredFromExistingOnOutsideTransfer`
- `locationHistoryWrittenOnCreate`
- `locationHistoryWrittenOnTransfer`

### Controller tests

File: `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/test/java/com/toir/controller/EquipmentControllerContractTest.java`

Add tests:

- `oldCreateDepartmentPayloadStillWorks`
- `oldCreateWarehousePayloadStillWorks`
- `newNestedLocationCreateOutsideWorks`
- `oldPlacementDepartmentPayloadStillWorks`
- `oldPlacementWarehousePayloadStillWorks`
- `placementOutsidePayloadWorks`
- `invalidLocationTypeReturns400`
- `responsePlacementIncludesOutsideFields`
- `flatOutsideFieldsAreRejectedOrMappedAccordingToCompatibilityDecision`

### Security/PBAC tests

File: `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/test/java/com/toir/security/EquipmentPbacScopeTest.java`

Add tests:

- `outsideEquipmentVisibleToResponsibleDepartment`
- `outsideEquipmentHiddenFromUnrelatedDepartment`
- `warehouseEquipmentVisibleByResponsibleDepartmentWhenPhysicalDepartmentNull`
- `cannotTransferToUnauthorizedDepartment`
- `cannotTransferToUnauthorizedWarehouse`
- `cannotAssignUnauthorizedResponsibleDepartment`

File: `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/test/java/com/toir/security/VehiclePbacScopeTest.java`

Add:

- `vehicleOutsideVisibleByResponsibleDepartment`
- `vehicleOutsideHiddenFromUnrelatedDepartment`

### RBAC tests

File: `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/test/java/com/toir/security/RbacEquipmentSecurityTest.java`

Add:

- `equipmentTransferCanTransferOutside`
- `equipmentReadCannotTransferOutside`

### Repository tests

File: `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/test/java/com/toir/repository/equipment/EquipmentRepositoryQueryContractTest.java`

Add:

- `searchFiltersByLocationType`
- `searchFiltersByCurrentWarehouseId`
- `searchFiltersByOutsideReason`
- `searchFiltersByOverdueOnly`
- `statsIncludeOutsideEquipmentByResponsibleDepartment`
- `availableReplacementQueryRequiresWarehouseLocation`

### Migration tests

File: `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/test/java/com/toir/migration/EquipmentCurrentLocationMigrationContractTest.java`

Add if migration contract pattern is accepted:

- backfill department rows
- backfill warehouse rows from active `warehouse_equipment_items`
- null/null rows remain nullable for manual review
- constraints include enum values
- indexes exist

## 15. Fayllar bo'yicha aniq o'zgarishlar ro'yxati

### `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/resources/db/migration/V20260602_3__equipment_current_location.sql`

- Change type: ADD
- Purpose: Current location fields, PBAC ownership field, outside fields, history table, indexes, backfill.
- Main changes: add columns to `equipment`, add FK/index/check constraints, create `equipment_location_history`, backfill current location.
- Risk: migration backfill ambiguity for null/null data and `location_id` warehouse-id drift.

### `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/entity/equipment/Equipment.java`

- Change type: MODIFY
- Purpose: Store current location and PBAC ownership directly on Equipment.
- Main changes: add `currentLocationType`, `currentWarehouseId`, `responsibleDepartmentId`, outside fields.
- Risk: existing mappings/tests need updates; `departmentId` semantic must be kept clear.

### `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/entity/equipment/EquipmentLocationHistory.java`

- Change type: ADD
- Purpose: Immutable location movement/history rows.
- Main changes: from/to location snapshot, responsibleDepartmentId, changedBy, changedAt, note.
- Risk: missing history writes if not centralized.

### `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/enums/EquipmentLocationType.java`

- Change type: ADD
- Purpose: Current location type enum.
- Main changes: `DEPARTMENT`, `WAREHOUSE`, `OUTSIDE_FACILITY`.
- Risk: response compatibility if replacing old `PlacementType` too aggressively.

### `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/enums/EquipmentOutsideReason.java`

- Change type: ADD
- Purpose: Outside facility reason enum.
- Main changes: business/service/rent/on-road/etc. reasons.
- Risk: product may request `LOST`; keep it out until status decision.

### `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/enums/PlacementTargetType.java`

- Change type: MODIFY
- Purpose: Backward-compatible outside target support for existing placement endpoint.
- Main changes: add `OUTSIDE_FACILITY`.
- Risk: duplicate semantics with `EquipmentLocationType`; keep adapter logic explicit.

### `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/dto/equipment/EquipmentLocationRequest.java`

- Change type: ADD
- Purpose: Shared create/update/transfer location payload.
- Main changes: locationType, physical target IDs, responsibleDepartmentId, outside fields, warehouseStatus.
- Risk: null semantics must be documented and tested.

### `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/dto/equipment/EquipmentTransferRequest.java`

- Change type: ADD
- Purpose: Explicit transfer request if current placement request becomes too overloaded.
- Main changes: targetLocation, note.
- Risk: May be optional if extending `EquipmentPlacementRequest` is enough.

### `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/dto/equipment/EquipmentLocationDto.java`

- Change type: ADD
- Purpose: Response shape for current location.
- Main changes: type, refs, outside fields, overdue.
- Risk: duplication with `EquipmentDto.PlacementRef`; decide one canonical response object.

### `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/dto/equipment/EquipmentCreateRequest.java`

- Change type: MODIFY
- Purpose: Accept nested location while preserving old flat fields.
- Main changes: add `EquipmentLocationRequest location`.
- Risk: constructor overloads need careful update to avoid breaking tests.

### `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/dto/equipment/EquipmentUpdateRequest.java`

- Change type: MODIFY
- Purpose: Explicit location update support.
- Main changes: add `EquipmentLocationRequest location`.
- Risk: old null-as-no-op semantics must remain when `location == null`.

### `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/dto/equipment/EquipmentPlacementRequest.java`

- Change type: MODIFY
- Purpose: Support outside transfer while old targetType payload keeps working.
- Main changes: add `targetLocation`, `note`; adapt old fields.
- Risk: ambiguous payload if both old and new fields are provided; nested wins or reject mixed payloads.

### `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/dto/equipment/EquipmentDto.java`

- Change type: MODIFY
- Purpose: Return outside/current location details.
- Main changes: extend `PlacementRef` or add `EquipmentLocationDto`.
- Risk: frontend response contract; keep old fields.

### `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/controller/equipment/EquipmentController.java`

- Change type: MODIFY
- Purpose: Accept new filters and PBAC based on responsible department.
- Main changes: list params, create/update/placement request handling, `assertCanAccessEquipment`.
- Risk: non-admin visibility behavior changes; security tests required.

### `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/service/equipment/EquipmentService.java`

- Change type: MODIFY
- Purpose: Main implementation of create/update/transfer location logic.
- Main changes: resolve location payload, apply current fields, clear stale fields, call validator, write history/audit, enrich response.
- Risk: large file; keep helper methods focused or extract `EquipmentLocationService`.

### `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/service/equipment/EquipmentLocationValidator.java`

- Change type: ADD
- Purpose: Centralize location validation/defaulting.
- Main changes: validate department/warehouse/outside, normalize strings, infer responsibleDepartmentId.
- Risk: Must not duplicate controller PBAC in contradictory ways.

### `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/service/equipment/EquipmentLocationHistoryService.java`

- Change type: ADD
- Purpose: Centralize history snapshot/write logic.
- Main changes: snapshot from equipment, write history row.
- Risk: optional; can be private methods in `EquipmentService` if scope stays small.

### `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/repository/equipment/EquipmentLocationHistoryRepository.java`

- Change type: ADD
- Purpose: Persist/query location history.
- Main changes: JpaRepository and find by equipmentId order changedAt desc.
- Risk: none beyond schema/entity alignment.

### `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/repository/equipment/EquipmentRepository.java`

- Change type: MODIFY
- Purpose: Add responsible department and location filters.
- Main changes: search/list/stats query params and predicates.
- Risk: JPQL enum/date predicates and pagination tests.

### `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/service/WarehouseEquipmentItemService.java`

- Change type: MODIFY
- Purpose: Stop treating `locationId = warehouseId` as source of truth; coordinate warehouse assignment with equipment current location.
- Main changes: remove/update `equipment.setLocationId(targetWarehouseId)`, expose close active assignment helper if moving outside.
- Risk: existing warehouse tests need update.

### `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/security/ScopeAccessService.java`

- Change type: MODIFY
- Purpose: Equipment PBAC helper using responsible department.
- Main changes: add equipment scope helper methods.
- Risk: scope behavior for old rows with both fields null.

### `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/controller/VehicleController.java`

- Change type: MODIFY
- Purpose: Vehicle outside/on-road visibility through responsible department.
- Main changes: access helper uses responsibleDepartmentId fallback.
- Risk: vehicle tests need updates if VehicleRequest remains department-required.

### `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/service/AnalyticsService.java`

- Change type: MODIFY
- Purpose: Include outside equipment under responsible department.
- Main changes: filtering and access checks use responsibleDepartmentId fallback.
- Risk: analytics counts may change for warehouse/outside equipment.

### Tests

- Change type: MODIFY / ADD
- Purpose: Cover service, controller, PBAC/RBAC, repository, migration.
- Main files:
  - `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/test/java/com/toir/service/equipment/EquipmentServiceTest.java`
  - `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/test/java/com/toir/controller/EquipmentControllerContractTest.java`
  - `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/test/java/com/toir/security/EquipmentPbacScopeTest.java`
  - `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/test/java/com/toir/security/RbacEquipmentSecurityTest.java`
  - `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/test/java/com/toir/repository/equipment/EquipmentRepositoryQueryContractTest.java`
  - `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/test/java/com/toir/migration/EquipmentCurrentLocationMigrationContractTest.java`
- Risk: tests are broad; implement phase by phase.

## 16. Implementation bosqichlari

### Phase 1: Schema and enums

- Add migration `V20260602_3__equipment_current_location.sql`.
- Add `EquipmentLocationType`.
- Add `EquipmentOutsideReason`.
- Modify `Equipment`.
- Add `EquipmentLocationHistory`.
- Add `EquipmentLocationHistoryRepository`.
- Verification:

```bash
./mvnw test -DskipTests=false -Dtest=EquipmentCurrentLocationMigrationContractTest
./mvnw test
```

Expected: compile succeeds; migration contract tests pass if implemented.

### Phase 2: DTO and validation

- Add `EquipmentLocationRequest`.
- Add `EquipmentTransferRequest` if chosen.
- Add/extend `EquipmentLocationDto` / `EquipmentDto.PlacementRef`.
- Modify `EquipmentCreateRequest`, `EquipmentUpdateRequest`, `EquipmentPlacementRequest`.
- Add `EquipmentLocationValidator`.
- Add service tests for validation first.
- Verification:

```bash
./mvnw test -Dtest=EquipmentServiceTest
./mvnw test -Dtest=EquipmentControllerContractTest
```

Expected: old payload tests and new validation tests pass.

### Phase 3: Service transfer logic

- Update `EquipmentService.create`.
- Update `EquipmentService.update`.
- Update `EquipmentService.updatePlacement`.
- Extract apply/clear/snapshot helper methods.
- Update `WarehouseEquipmentItemService` to avoid writing warehouse id into `locationId`.
- Write history rows.
- Write audit log entries.
- Verification:

```bash
./mvnw test -Dtest=EquipmentServiceTest,WarehouseEquipmentItemServiceTest
```

Expected: all department/warehouse/outside transfer tests pass.

### Phase 4: PBAC/query

- Update `ScopeAccessService`.
- Update `EquipmentController.assertCanAccessEquipment`.
- Update `VehicleController.assertCanAccessVehicleEquipment`.
- Update `AnalyticsService`.
- Update `EquipmentRepository` search/stats queries.
- Add list filters.
- Verification:

```bash
./mvnw test -Dtest=EquipmentPbacScopeTest,VehiclePbacScopeTest,RbacEquipmentSecurityTest,EquipmentRepositoryQueryContractTest,AnalyticsServiceTest
```

Expected: responsible department visibility works and unrelated department is denied.

### Phase 5: Full tests

- Run full backend test suite.
- Fix failures caused by DTO constructor changes and response shape changes.
- Verification:

```bash
./mvnw test
```

Expected: all tests pass.

## 17. Risklar va rollback rejasi

### Risk: migration backfill ambiguity

- Null/null location rows cannot be safely categorized.
- Rollback/prevention: migration keeps `current_location_type` nullable and reports rows for manual review; do not enforce NOT NULL until data audit is done.

### Risk: `locationId` semantic conflict

- Existing code sets `locationId = warehouseId`.
- Rollback/prevention: migration copies warehouse id to `current_warehouse_id` and normalizes `location_id`; service stops writing warehouse id into locationId.

### Risk: old warehouse assignment drift

- Current system can keep active warehouse item when equipment is in department.
- Rollback/prevention: first release preserves `INSTALLED` metadata behavior, but current location uses `Equipment.currentWarehouseId`; later cleanup can close installed active items if approved.

### Risk: PBAC behavior changes

- Non-admin users may newly see warehouse/outside equipment by responsible department.
- Rollback/prevention: add explicit security tests; if unexpected, gate new behavior behind responsibleDepartmentId only and fallback to old departmentId for old rows.

### Risk: frontend compatibility

- DTO response shape change can break frontend if `placement.type` enum changes.
- Rollback/prevention: keep old top-level fields; either keep `PlacementType` compatible or add outside field without removing old fields.

### Risk: data with null/null location

- These rows remain invisible to non-admin if responsibleDepartmentId also null.
- Rollback/prevention: backfill responsibleDepartmentId where safe; generate manual review query before hardening.

### Rollback before migration

- If only source files changed and not committed:

```bash
git status
git diff
```

Then revert only implementation files intentionally. Do not use `git reset --hard` unless explicitly approved.

### Rollback after migration in dev/staging

- Take DB backup before applying migration.
- If migration applied and must be reverted in dev/staging:
  - restore DB backup, or
  - create reverse migration that drops new FKs/indexes/table/columns only after confirming no production data depends on them.
- Do not drop `equipment_location_history` in production without export/backup.

## 18. Ochiq savollar

- Should `responsibleDepartmentId` be required for all equipment after backfill/hardening?
- Who owns equipment moved outside from warehouse if warehouse.departmentId is null?
- Should outside `SERVICE` change equipment status to `IN_REPAIR`, or should status remain independent?
- Should `LOST` be added as `EquipmentStatus`?
- Should active `WarehouseEquipmentItem` close when equipment moves outside?
- Should active `WarehouseEquipmentItem` close when equipment moves from warehouse to department, or remain `INSTALLED` metadata?
- Should `outsideRecipientUserId` be required for internal employee temporary use?
- Should `outsideExpectedReturnDate` be required for all outside reasons or only temporary ones?
- Should the API eventually use nested `location` only, or keep flat fields for multiple releases?
- Should `locationId` be required for department placement when the frontend knows exact shop/room/line?
- Should outside history endpoint be included in first implementation or deferred?
- Should `responsibleDepartmentId` be editable by ordinary `EQUIPMENT_UPDATE`, or only by `EQUIPMENT_TRANSFER`/admin?
