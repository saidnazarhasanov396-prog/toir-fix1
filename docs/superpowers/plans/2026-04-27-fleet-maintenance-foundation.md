# Fleet Maintenance Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the fleet foundation: standardized page responses for the new work, `Equipment.category`, `VehicleDetails`, `/api/v1/vehicles` CRUD, and the first frontend Fleet registry/card routes.

**Architecture:** Vehicles remain normal `Equipment` rows with `category = VEHICLE`; vehicle-only passport fields live in `VehicleDetails`. Backend exposes repository-style `/api/v1/vehicles` endpoints returning the shared `{ items, meta }` shape, and frontend fleet code lives under `src/features/fleet` so it does not enlarge the existing `src/lib/api.ts` monolith.

**Tech Stack:** Spring Boot 3.3, Java 21, JPA/Hibernate, Flyway, JUnit 5, Mockito, React 19, Vite, TypeScript, TanStack Query, TanStack Table.

---

## Scope Boundary

This plan implements Phase 0 and Phase 1 from the approved design:

- Shared page response utility for new and touched endpoints.
- Equipment category support.
- VehicleDetails persistence.
- Vehicle CRUD API.
- Fleet registry and vehicle card shell in the frontend.

These approved design areas are intentionally assigned to separate future plans:

- Universal meter correction workflow.
- Counter-based preventive maintenance generation.
- RepairRequestVehicleContext and WorkOrderVehicleContext status automation.
- Vehicle inspections and document expiry notifications.
- Tires, fuel, and fleet dashboard metrics.

## Repositories

The workspace contains two git repositories:

- Backend: `/Users/tenzorsoft/Desktop/Work/toir/toir-backend`
- Frontend: `/Users/tenzorsoft/Desktop/Work/toir/toir-front`

Commit backend and frontend changes separately.

## File Structure

Backend files:

- Create: `src/main/java/com/toir/config/PaginatedResponse.java` - shared API response wrapper.
- Create: `src/main/java/com/toir/enums/EquipmentCategory.java` - equipment category enum.
- Create: `src/main/java/com/toir/enums/VehicleType.java` - vehicle type enum.
- Modify: `src/main/java/com/toir/enums/EquipmentStatus.java` - add `OUT_OF_SERVICE`.
- Modify: `src/main/java/com/toir/entity/Equipment.java` - add `category`.
- Modify: `src/main/java/com/toir/dto/equipment/EquipmentDto.java` - expose `category`.
- Modify: `src/main/java/com/toir/dto/equipment/EquipmentRequest.java` - accept `category`.
- Modify: `src/main/java/com/toir/repository/EquipmentRepository.java` - filter search by category.
- Modify: `src/main/java/com/toir/service/EquipmentService.java` - apply category and one-based page conversion at API edge.
- Modify: `src/main/java/com/toir/controller/EquipmentController.java` - return `PaginatedResponse`.
- Create: `src/main/java/com/toir/entity/VehicleDetails.java` - vehicle-specific fields.
- Create: `src/main/java/com/toir/repository/VehicleDetailsRepository.java` - vehicle details persistence.
- Create: `src/main/java/com/toir/dto/vehicle/VehicleSummaryDto.java` - registry row DTO.
- Create: `src/main/java/com/toir/dto/vehicle/VehicleDetailDto.java` - vehicle card DTO.
- Create: `src/main/java/com/toir/dto/vehicle/VehicleRequest.java` - create/update request.
- Create: `src/main/java/com/toir/service/VehicleService.java` - transactional vehicle CRUD.
- Create: `src/main/java/com/toir/controller/VehicleController.java` - `/api/v1/vehicles`.
- Create: `src/main/resources/db/migration/V20260427_1__fleet_vehicle_foundation.sql` - schema migration.
- Create: `src/test/java/com/toir/config/PaginatedResponseTest.java` - page wrapper tests.
- Create: `src/test/java/com/toir/dto/equipment/EquipmentDtoTest.java` - category exposure test.
- Create: `src/test/java/com/toir/service/VehicleServiceTest.java` - service unit tests.

Frontend files:

- Modify: `package.json` - add Vitest test scripts and dependencies.
- Create: `src/test/setup.ts` - test setup.
- Create: `src/features/fleet/types.ts` - fleet frontend types.
- Create: `src/features/fleet/api.ts` - fleet endpoint client.
- Create: `src/features/fleet/query-keys.ts` - fleet query keys.
- Create: `src/features/fleet/api.test.ts` - API URL and payload tests.
- Create: `src/features/fleet/components/vehicle-status-badge.tsx` - vehicle status visual.
- Create: `src/features/fleet/components/vehicle-registry-table.tsx` - table component.
- Create: `src/features/fleet/pages/fleet-registry-page.tsx` - `/vehicles`.
- Create: `src/features/fleet/pages/vehicle-card-page.tsx` - `/vehicles/:equipmentId`.
- Modify: `src/app/routes.tsx` - add fleet routes.
- Modify: `src/components/layout/app-shell.tsx` - add Fleet nav item.
- Modify: `src/types/api.ts` - add vehicle category/detail types only if shared types are needed outside `features/fleet`.

---

### Task 1: Backend Shared Pagination Contract

**Files:**
- Create: `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/config/PaginatedResponse.java`
- Create: `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/test/java/com/toir/config/PaginatedResponseTest.java`
- Modify: `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/controller/EquipmentController.java`
- Modify: `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/service/EquipmentService.java`

- [ ] **Step 1: Write the failing pagination utility test**

Create `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/test/java/com/toir/config/PaginatedResponseTest.java`:

```java
package com.toir.config;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PaginatedResponseTest {

    @Test
    void fromSpringPageKeepsPublicOneBasedPageNumber() {
        PageImpl<String> page = new PageImpl<>(
                List.of("A", "B"),
                PageRequest.of(1, 2),
                5
        );

        PaginatedResponse<String> response = PaginatedResponse.from(page, 2, 2);

        assertThat(response.items()).containsExactly("A", "B");
        assertThat(response.meta().page()).isEqualTo(2);
        assertThat(response.meta().pageSize()).isEqualTo(2);
        assertThat(response.meta().total()).isEqualTo(5);
    }

    @Test
    void ofListReturnsSinglePageResponse() {
        PaginatedResponse<String> response = PaginatedResponse.of(List.of("A"));

        assertThat(response.items()).containsExactly("A");
        assertThat(response.meta().page()).isEqualTo(1);
        assertThat(response.meta().pageSize()).isEqualTo(1);
        assertThat(response.meta().total()).isEqualTo(1);
    }
}
```

- [ ] **Step 2: Run the failing test**

Run:

```bash
cd /Users/tenzorsoft/Desktop/Work/toir/toir-backend
./mvnw test -Dtest=PaginatedResponseTest
```

Expected: compilation fails because `com.toir.config.PaginatedResponse` does not exist.

- [ ] **Step 3: Add the shared response wrapper**

Create `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/config/PaginatedResponse.java`:

```java
package com.toir.config;

import org.springframework.data.domain.Page;

import java.util.List;

public record PaginatedResponse<T>(List<T> items, Meta meta) {

    public record Meta(int page, int pageSize, long total) {
    }

    public static <T> PaginatedResponse<T> from(Page<T> page, int publicPage, int requestedPageSize) {
        int safePage = Math.max(publicPage, 1);
        int safePageSize = Math.max(requestedPageSize, 1);
        return new PaginatedResponse<>(
                page.getContent(),
                new Meta(safePage, safePageSize, page.getTotalElements())
        );
    }

    public static <T> PaginatedResponse<T> of(List<T> items) {
        List<T> safeItems = items == null ? List.of() : items;
        int size = safeItems.size();
        return new PaginatedResponse<>(safeItems, new Meta(1, size, size));
    }
}
```

- [ ] **Step 4: Convert equipment list to the shared response**

Modify `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/controller/EquipmentController.java`:

```java
import com.toir.config.PaginatedResponse;
```

Replace the list method with:

```java
@GetMapping
public PaginatedResponse<EquipmentDto> list(
        @RequestParam(required = false) UUID departmentId,
        @RequestParam(required = false) UUID equipmentTypeId,
        @RequestParam(required = false) EquipmentStatus status,
        @RequestParam(required = false) String search,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int pageSize
) {
    int safePage = Math.max(page, 1);
    int safePageSize = Math.max(pageSize, 1);
    Page<EquipmentDto> result = service.search(
            securityScope.enforceDepartmentScope(departmentId),
            equipmentTypeId,
            status,
            search,
            safePage - 1,
            safePageSize
    );
    return PaginatedResponse.from(result, safePage, safePageSize);
}
```

- [ ] **Step 5: Run pagination tests**

Run:

```bash
cd /Users/tenzorsoft/Desktop/Work/toir/toir-backend
./mvnw test -Dtest=PaginatedResponseTest
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 6: Commit backend pagination foundation**

Run:

```bash
cd /Users/tenzorsoft/Desktop/Work/toir/toir-backend
git add src/main/java/com/toir/config/PaginatedResponse.java \
  src/main/java/com/toir/controller/EquipmentController.java \
  src/test/java/com/toir/config/PaginatedResponseTest.java
git commit -m "feat: add shared paginated response"
```

---

### Task 2: Backend Equipment Category Schema

**Files:**
- Create: `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/enums/EquipmentCategory.java`
- Create: `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/enums/VehicleType.java`
- Modify: `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/enums/EquipmentStatus.java`
- Modify: `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/entity/Equipment.java`
- Modify: `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/dto/equipment/EquipmentDto.java`
- Modify: `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/dto/equipment/EquipmentRequest.java`
- Modify: `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/repository/EquipmentRepository.java`
- Modify: `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/service/EquipmentService.java`
- Create: `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/resources/db/migration/V20260427_1__fleet_vehicle_foundation.sql`
- Create: `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/test/java/com/toir/dto/equipment/EquipmentDtoTest.java`

- [ ] **Step 1: Write the failing category DTO test**

Create `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/test/java/com/toir/dto/equipment/EquipmentDtoTest.java`:

```java
package com.toir.dto.equipment;

import com.toir.entity.equipment.Equipment;
import com.toir.enums.EquipmentCategory;
import com.toir.enums.EquipmentStatus;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EquipmentDtoTest {

    @Test
    void fromIncludesEquipmentCategory() {
        Equipment equipment = new com.toir.entity.equipment.Equipment();
        equipment.setId(UUID.randomUUID());
        equipment.setCode("VH-001");
        equipment.setName("Fleet Truck 001");
        equipment.setInventoryNumber("INV-VH-001");
        equipment.setEquipmentTypeId(UUID.randomUUID());
        equipment.setDepartmentId(UUID.randomUUID());
        equipment.setStatus(EquipmentStatus.ACTIVE);
        equipment.setCategory(EquipmentCategory.VEHICLE);

        EquipmentDto dto = EquipmentDto.from(equipment);

        assertThat(dto.category()).isEqualTo(EquipmentCategory.VEHICLE);
    }
}
```

- [ ] **Step 2: Run the failing category test**

Run:

```bash
cd /Users/tenzorsoft/Desktop/Work/toir/toir-backend
./mvnw test -Dtest=EquipmentDtoTest
```

Expected: compilation fails because `EquipmentCategory` and `Equipment.category` do not exist.

- [ ] **Step 3: Add equipment and vehicle enums**

Create `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/enums/EquipmentCategory.java`:

```java
package com.toir.enums;

public enum EquipmentCategory {
    PRODUCTION_EQUIPMENT,
    VEHICLE,
    ENERGY_EQUIPMENT,
    INSTRUMENTATION,
    BUILDING_INFRASTRUCTURE,
    OTHER
}
```

Create `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/enums/VehicleType.java`:

```java
package com.toir.enums;

public enum VehicleType {
    PASSENGER_CAR,
    TRUCK,
    BUS,
    SPECIAL_EQUIPMENT,
    FORKLIFT,
    TRAILER,
    OTHER
}
```

Modify `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/enums/EquipmentStatus.java` so it contains:

```java
package com.toir.enums;

public enum EquipmentStatus {
    ACTIVE,
    STANDBY,
    IN_REPAIR,
    OUT_OF_SERVICE,
    CONSERVATION,
    DECOMMISSIONED
}
```

- [ ] **Step 4: Add category to Equipment**

Modify `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/entity/Equipment.java`:

```java
import com.toir.enums.EquipmentCategory;
```

Add this field after `status`:

```java
@Enumerated(EnumType.STRING)
@Column(nullable = false)
private EquipmentCategory category = EquipmentCategory.PRODUCTION_EQUIPMENT;
```

- [ ] **Step 5: Expose category in equipment DTO/request**

Modify `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/dto/equipment/EquipmentDto.java`:

```java
import com.toir.enums.EquipmentCategory;
```

Add `EquipmentCategory category` after `EquipmentStatus status` in the record constructor, and pass `e.getCategory()` in `from(...)`:

```java
EquipmentStatus status,
EquipmentCategory category,
LocalDate commissionedAt,
```

```java
e.getManufacturer(), e.getStatus(), e.getCategory(),
e.getCommissionedAt(), e.getWarrantyUntil(), e.getDescription(),
```

Modify `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/dto/equipment/EquipmentRequest.java`:

```java
import com.toir.enums.EquipmentCategory;
```

Add the request field after `EquipmentStatus status`:

```java
EquipmentCategory category,
```

- [ ] **Step 6: Apply category in EquipmentService**

Modify `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/service/EquipmentService.java`:

```java
import com.toir.enums.EquipmentCategory;
```

In `apply(...)`, after status assignment:

```java
entity.setCategory(request.category() != null
        ? request.category()
        : EquipmentCategory.PRODUCTION_EQUIPMENT);
```

- [ ] **Step 7: Add category search filter**

Modify `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/repository/EquipmentRepository.java`:

```java
import com.toir.enums.EquipmentCategory;
```

Add this condition to the `search(...)` query after the status condition:

```java
"(:category is null or e.category = :category) and " +
```

Add the method parameter before `String search`:

```java
@Param("category") EquipmentCategory category,
```

Modify `EquipmentService.search(...)` signature to accept `EquipmentCategory category`:

```java
public Page<EquipmentDto> search(UUID departmentId, UUID equipmentTypeId, EquipmentStatus status,
                                 EquipmentCategory category, String search, int page, int pageSize) {
    Page<Equipment> items = repository.search(departmentId, equipmentTypeId, status, category, search, PageRequest.of(page, pageSize));
```

Modify `EquipmentController.list(...)` to accept and pass `category`:

```java
@RequestParam(required = false) EquipmentCategory category,
```

```java
category,
search,
```

- [ ] **Step 8: Add Flyway migration**

Create `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/resources/db/migration/V20260427_1__fleet_vehicle_foundation.sql`:

```sql
ALTER TABLE equipment
    ADD COLUMN IF NOT EXISTS category varchar(64) NOT NULL DEFAULT 'PRODUCTION_EQUIPMENT';

UPDATE equipment
SET category = 'PRODUCTION_EQUIPMENT'
WHERE category IS NULL;

CREATE INDEX IF NOT EXISTS idx_equipment_category
    ON equipment (category)
    WHERE is_deleted = false;
```

- [ ] **Step 9: Run category tests**

Run:

```bash
cd /Users/tenzorsoft/Desktop/Work/toir/toir-backend
./mvnw test -Dtest=EquipmentDtoTest
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 10: Run backend compile**

Run:

```bash
cd /Users/tenzorsoft/Desktop/Work/toir/toir-backend
./mvnw test -DskipTests
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 11: Commit backend category support**

Run:

```bash
cd /Users/tenzorsoft/Desktop/Work/toir/toir-backend
git add src/main/java/com/toir/enums/EquipmentCategory.java \
  src/main/java/com/toir/enums/VehicleType.java \
  src/main/java/com/toir/enums/EquipmentStatus.java \
  src/main/java/com/toir/entity/Equipment.java \
  src/main/java/com/toir/dto/equipment/EquipmentDto.java \
  src/main/java/com/toir/dto/equipment/EquipmentRequest.java \
  src/main/java/com/toir/repository/EquipmentRepository.java \
  src/main/java/com/toir/service/EquipmentService.java \
  src/main/java/com/toir/controller/EquipmentController.java \
  src/main/resources/db/migration/V20260427_1__fleet_vehicle_foundation.sql \
  src/test/java/com/toir/dto/equipment/EquipmentDtoTest.java
git commit -m "feat: add equipment category support"
```

---

### Task 3: Backend VehicleDetails Model and Vehicle API

**Files:**
- Create: `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/entity/VehicleDetails.java`
- Create: `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/repository/VehicleDetailsRepository.java`
- Create: `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/dto/vehicle/VehicleRequest.java`
- Create: `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/dto/vehicle/VehicleSummaryDto.java`
- Create: `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/dto/vehicle/VehicleDetailDto.java`
- Create: `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/service/VehicleService.java`
- Create: `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/controller/VehicleController.java`
- Modify: `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/resources/db/migration/V20260427_1__fleet_vehicle_foundation.sql`
- Create: `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/test/java/com/toir/service/VehicleServiceTest.java`

- [ ] **Step 1: Write the failing vehicle service test**

Create `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/test/java/com/toir/service/VehicleServiceTest.java`:

```java
package com.toir.service;

import com.toir.dto.vehicle.VehicleRequest;
import com.toir.dto.vehicle.VehicleDetailDto;
import com.toir.entity.equipment.Equipment;
import com.toir.enums.EquipmentCategory;
import com.toir.enums.EquipmentStatus;
import com.toir.enums.VehicleType;
import com.toir.exception.RestException;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.VehicleDetailsRepository;
import com.toir.service.equipment.EquipmentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VehicleServiceTest {

    @Mock
    EquipmentRepository equipmentRepository;

    @Mock
    VehicleDetailsRepository vehicleDetailsRepository;

    @Mock
    EquipmentService equipmentService;

    @InjectMocks
    VehicleService service;

    @Test
    void createVehicleCreatesEquipmentWithVehicleCategoryAndDetails() {
        UUID equipmentTypeId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        VehicleRequest request = new VehicleRequest(
                "VH-001",
                "Truck 001",
                "INV-VH-001",
                "TN-VH-001",
                "SER-VH-001",
                equipmentTypeId,
                departmentId,
                null,
                EquipmentStatus.ACTIVE,
                "01A123AA",
                "VIN123456789",
                "MAN",
                "TGS",
                2022,
                VehicleType.TRUCK,
                null,
                null,
                null,
                "DIESEL",
                400.0,
                12000.0,
                2,
                null,
                1000.0,
                25.0,
                "REG-001",
                "INS-001",
                LocalDate.of(2026, 12, 31),
                LocalDate.of(2026, 10, 31),
                "GPS-001"
        );

        when(equipmentRepository.existsByCodeAndIsDeletedFalse("VH-001")).thenReturn(false);
        when(equipmentRepository.existsByInventoryNumberAndIsDeletedFalse("INV-VH-001")).thenReturn(false);
        when(vehicleDetailsRepository.existsByPlateNumberAndIsDeletedFalse("01A123AA")).thenReturn(false);
        when(vehicleDetailsRepository.existsByVinAndIsDeletedFalse("VIN123456789")).thenReturn(false);
        when(equipmentRepository.save(any(Equipment.class))).thenAnswer(invocation -> {
            Equipment equipment = invocation.getArgument(0);
            equipment.setId(UUID.randomUUID());
            return equipment;
        });
        when(vehicleDetailsRepository.save(any(com.toir.entity.equipment.VehicleDetails.class))).thenAnswer(invocation -> {
            com.toir.entity.equipment.VehicleDetails details = invocation.getArgument(0);
            details.setId(UUID.randomUUID());
            return details;
        });

        VehicleDetailDto result = service.create(request);

        assertThat(result.equipment().category()).isEqualTo(EquipmentCategory.VEHICLE);
        assertThat(result.vehicleDetails().plateNumber()).isEqualTo("01A123AA");
    }

    @Test
    void createVehicleRejectsDuplicatePlateNumber() {
        VehicleRequest request = VehicleRequest.minimal(
                "VH-002",
                "Truck 002",
                "INV-VH-002",
                UUID.randomUUID(),
                UUID.randomUUID(),
                "01A999AA",
                VehicleType.TRUCK
        );

        when(equipmentRepository.existsByCodeAndIsDeletedFalse("VH-002")).thenReturn(false);
        when(equipmentRepository.existsByInventoryNumberAndIsDeletedFalse("INV-VH-002")).thenReturn(false);
        when(vehicleDetailsRepository.existsByPlateNumberAndIsDeletedFalse("01A999AA")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Vehicle plate number already exists");
    }
}
```

- [ ] **Step 2: Run the failing vehicle test**

Run:

```bash
cd /Users/tenzorsoft/Desktop/Work/toir/toir-backend
./mvnw test -Dtest=VehicleServiceTest
```

Expected: compilation fails because vehicle classes do not exist.

- [ ] **Step 3: Add VehicleDetails entity and repository**

Create `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/entity/VehicleDetails.java`:

```java
package com.toir.entity;

import com.toir.enums.VehicleType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "vehicle_details", indexes = {
        @Index(name = "idx_vehicle_details_equipment", columnList = "equipment_id"),
        @Index(name = "idx_vehicle_details_plate", columnList = "plate_number"),
        @Index(name = "idx_vehicle_details_vin", columnList = "vin")
})
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class VehicleDetails extends BaseEntity {

    @Column(name = "equipment_id", nullable = false, unique = true)
    private UUID equipmentId;

    @Column(name = "plate_number", nullable = false, unique = true)
    private String plateNumber;

    @Column(unique = true)
    private String vin;

    private String brand;
    private String model;

    @Column(name = "manufacture_year")
    private Integer manufactureYear;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", nullable = false)
    private VehicleType vehicleType;

    @Column(name = "body_number")
    private String bodyNumber;

    @Column(name = "chassis_number")
    private String chassisNumber;

    @Column(name = "engine_number")
    private String engineNumber;

    @Column(name = "fuel_type")
    private String fuelType;

    @Column(name = "fuel_tank_capacity")
    private Double fuelTankCapacity;

    @Column(name = "carrying_capacity")
    private Double carryingCapacity;

    @Column(name = "seat_count")
    private Integer seatCount;

    @Column(name = "assigned_driver_id")
    private UUID assignedDriverId;

    @Column(name = "current_odometer_km", nullable = false)
    private double currentOdometerKm;

    @Column(name = "current_engine_hours", nullable = false)
    private double currentEngineHours;

    @Column(name = "registration_certificate_number")
    private String registrationCertificateNumber;

    @Column(name = "insurance_policy_number")
    private String insurancePolicyNumber;

    @Column(name = "insurance_expiry_date")
    private LocalDate insuranceExpiryDate;

    @Column(name = "technical_inspection_expiry_date")
    private LocalDate technicalInspectionExpiryDate;

    @Column(name = "gps_device_id")
    private String gpsDeviceId;
}
```

Create `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/repository/VehicleDetailsRepository.java`:

```java
package com.toir.repository;

import com.toir.entity.equipment.VehicleDetails;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VehicleDetailsRepository extends JpaRepository<com.toir.entity.equipment.VehicleDetails, UUID> {
    Optional<VehicleDetails> findByEquipmentIdAndIsDeletedFalse(UUID equipmentId);

    List<com.toir.entity.equipment.VehicleDetails> findAllByEquipmentIdInAndIsDeletedFalse(Collection<UUID> equipmentIds);

    boolean existsByPlateNumberAndIsDeletedFalse(String plateNumber);

    boolean existsByVinAndIsDeletedFalse(String vin);
}
```

- [ ] **Step 4: Extend the migration with vehicle_details**

Append to `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/resources/db/migration/V20260427_1__fleet_vehicle_foundation.sql`:

```sql
CREATE TABLE IF NOT EXISTS vehicle_details (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    is_deleted boolean NOT NULL DEFAULT false,
    equipment_id uuid NOT NULL UNIQUE,
    plate_number varchar(64) NOT NULL UNIQUE,
    vin varchar(128) UNIQUE,
    brand varchar(128),
    model varchar(128),
    manufacture_year integer,
    vehicle_type varchar(64) NOT NULL,
    body_number varchar(128),
    chassis_number varchar(128),
    engine_number varchar(128),
    fuel_type varchar(64),
    fuel_tank_capacity double precision,
    carrying_capacity double precision,
    seat_count integer,
    assigned_driver_id uuid,
    current_odometer_km double precision NOT NULL DEFAULT 0,
    current_engine_hours double precision NOT NULL DEFAULT 0,
    registration_certificate_number varchar(128),
    insurance_policy_number varchar(128),
    insurance_expiry_date date,
    technical_inspection_expiry_date date,
    gps_device_id varchar(128),
    CONSTRAINT fk_vehicle_details_equipment
        FOREIGN KEY (equipment_id) REFERENCES equipment(id)
);

CREATE INDEX IF NOT EXISTS idx_vehicle_details_equipment
    ON vehicle_details (equipment_id)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_vehicle_details_plate
    ON vehicle_details (plate_number)
    WHERE is_deleted = false;
```

- [ ] **Step 5: Add vehicle DTOs**

Create `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/dto/vehicle/VehicleRequest.java`:

```java
package com.toir.dto.vehicle;

import com.toir.enums.EquipmentStatus;
import com.toir.enums.VehicleType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record VehicleRequest(
        @NotBlank String code,
        @NotBlank String name,
        @NotBlank String inventoryNumber,
        String technicalNumber,
        String serialNumber,
        @NotNull UUID equipmentTypeId,
        @NotNull UUID departmentId,
        UUID locationId,
        EquipmentStatus status,
        @NotBlank String plateNumber,
        String vin,
        String brand,
        String model,
        Integer manufactureYear,
        @NotNull VehicleType vehicleType,
        String bodyNumber,
        String chassisNumber,
        String engineNumber,
        String fuelType,
        Double fuelTankCapacity,
        Double carryingCapacity,
        Integer seatCount,
        UUID assignedDriverId,
        Double currentOdometerKm,
        Double currentEngineHours,
        String registrationCertificateNumber,
        String insurancePolicyNumber,
        LocalDate insuranceExpiryDate,
        LocalDate technicalInspectionExpiryDate,
        String gpsDeviceId
) {
    public static VehicleRequest minimal(String code, String name, String inventoryNumber,
                                         UUID equipmentTypeId, UUID departmentId,
                                         String plateNumber, VehicleType vehicleType) {
        return new VehicleRequest(
                code, name, inventoryNumber, null, null, equipmentTypeId, departmentId,
                null, EquipmentStatus.ACTIVE, plateNumber, null, null, null, null,
                vehicleType, null, null, null, null, null, null, null, null,
                0.0, 0.0, null, null, null, null, null
        );
    }
}
```

Create `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/dto/vehicle/VehicleSummaryDto.java`:

```java
package com.toir.dto.vehicle;

import com.toir.dto.equipment.EquipmentDto;
import com.toir.enums.EquipmentStatus;
import com.toir.enums.VehicleType;

import java.time.LocalDate;
import java.util.UUID;

public record VehicleSummaryDto(
        UUID equipmentId,
        String code,
        String name,
        String inventoryNumber,
        EquipmentStatus status,
        EquipmentDto.Ref department,
        EquipmentDto.Ref location,
        String plateNumber,
        String vin,
        String brand,
        String model,
        VehicleType vehicleType,
        UUID assignedDriverId,
        double currentOdometerKm,
        double currentEngineHours,
        LocalDate insuranceExpiryDate,
        LocalDate technicalInspectionExpiryDate
) {
    public static VehicleSummaryDto from(EquipmentDto equipment, com.toir.entity.equipment.VehicleDetails details) {
        return new VehicleSummaryDto(
                equipment.id(),
                equipment.code(),
                equipment.name(),
                equipment.inventoryNumber(),
                equipment.status(),
                equipment.department(),
                equipment.location(),
                details.getPlateNumber(),
                details.getVin(),
                details.getBrand(),
                details.getModel(),
                details.getVehicleType(),
                details.getAssignedDriverId(),
                details.getCurrentOdometerKm(),
                details.getCurrentEngineHours(),
                details.getInsuranceExpiryDate(),
                details.getTechnicalInspectionExpiryDate()
        );
    }
}
```

Create `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/dto/vehicle/VehicleDetailDto.java`:

```java
package com.toir.dto.vehicle;

import com.toir.dto.equipment.EquipmentDto;
import com.toir.enums.VehicleType;

import java.time.LocalDate;
import java.util.UUID;

public record VehicleDetailDto(
        EquipmentDto equipment,
        Details vehicleDetails
) {
    public record Details(
            UUID id,
            String plateNumber,
            String vin,
            String brand,
            String model,
            Integer manufactureYear,
            VehicleType vehicleType,
            String bodyNumber,
            String chassisNumber,
            String engineNumber,
            String fuelType,
            Double fuelTankCapacity,
            Double carryingCapacity,
            Integer seatCount,
            UUID assignedDriverId,
            double currentOdometerKm,
            double currentEngineHours,
            String registrationCertificateNumber,
            String insurancePolicyNumber,
            LocalDate insuranceExpiryDate,
            LocalDate technicalInspectionExpiryDate,
            String gpsDeviceId
    ) {
    }

    public static VehicleDetailDto from(EquipmentDto equipment, com.toir.entity.equipment.VehicleDetails details) {
        return new VehicleDetailDto(
                equipment,
                new Details(
                        details.getId(),
                        details.getPlateNumber(),
                        details.getVin(),
                        details.getBrand(),
                        details.getModel(),
                        details.getManufactureYear(),
                        details.getVehicleType(),
                        details.getBodyNumber(),
                        details.getChassisNumber(),
                        details.getEngineNumber(),
                        details.getFuelType(),
                        details.getFuelTankCapacity(),
                        details.getCarryingCapacity(),
                        details.getSeatCount(),
                        details.getAssignedDriverId(),
                        details.getCurrentOdometerKm(),
                        details.getCurrentEngineHours(),
                        details.getRegistrationCertificateNumber(),
                        details.getInsurancePolicyNumber(),
                        details.getInsuranceExpiryDate(),
                        details.getTechnicalInspectionExpiryDate(),
                        details.getGpsDeviceId()
                )
        );
    }
}
```

- [ ] **Step 6: Implement VehicleService**

Create `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/service/VehicleService.java`:

```java
package com.toir.service;

import com.toir.config.PaginatedResponse;
import com.toir.dto.equipment.EquipmentDto;
import com.toir.dto.vehicle.VehicleDetailDto;
import com.toir.dto.vehicle.VehicleRequest;
import com.toir.dto.vehicle.VehicleSummaryDto;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.equipment.VehicleDetails;
import com.toir.enums.EquipmentCategory;
import com.toir.enums.EquipmentStatus;
import com.toir.exception.RestException;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.VehicleDetailsRepository;
import com.toir.service.equipment.EquipmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VehicleService {

    private final EquipmentRepository equipmentRepository;
    private final VehicleDetailsRepository vehicleDetailsRepository;
    private final EquipmentService equipmentService;

    @Transactional(readOnly = true)
    public PaginatedResponse<VehicleSummaryDto> list(UUID departmentId, EquipmentStatus status, String search, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(pageSize, 1);
        Page<EquipmentDto> equipmentPage = equipmentService.search(
                departmentId,
                null,
                status,
                EquipmentCategory.VEHICLE,
                search,
                safePage - 1,
                safePageSize
        );
        Map<UUID, VehicleDetails> detailsByEquipment = vehicleDetailsRepository
                .findAllByEquipmentIdInAndIsDeletedFalse(equipmentPage.getContent().stream().map(EquipmentDto::id).toList())
                .stream()
                .collect(Collectors.toMap(VehicleDetails::getEquipmentId, Function.identity(), (a, b) -> a));
        Page<VehicleSummaryDto> mapped = equipmentPage.map(equipment -> {
            VehicleDetails details = detailsByEquipment.get(equipment.id());
            if (details == null) {
                throw RestException.conflict("Vehicle details are missing for equipment: " + equipment.id());
            }
            return VehicleSummaryDto.from(equipment, details);
        });
        return PaginatedResponse.from(mapped, safePage, safePageSize);
    }

    @Transactional(readOnly = true)
    public VehicleDetailDto findByEquipmentId(UUID equipmentId) {
        EquipmentDto equipment = equipmentService.findById(equipmentId);
        if (equipment.category() != EquipmentCategory.VEHICLE) {
            throw RestException.badRequest("Equipment is not a vehicle: " + equipmentId);
        }
        VehicleDetails details = vehicleDetailsRepository.findByEquipmentIdAndIsDeletedFalse(equipmentId)
                .orElseThrow(() -> RestException.notFound("Vehicle details not found: " + equipmentId));
        return VehicleDetailDto.from(equipment, details);
    }

    @Transactional
    public VehicleDetailDto create(VehicleRequest request) {
        validateUniqueCreate(request);
        Equipment equipment = new Equipment();
        applyEquipment(equipment, request);
        Equipment savedEquipment = equipmentRepository.save(equipment);

        VehicleDetails details = new com.toir.entity.equipment.VehicleDetails();
        details.setEquipmentId(savedEquipment.getId());
        applyDetails(details, request);
        VehicleDetails savedDetails = vehicleDetailsRepository.save(details);

        return VehicleDetailDto.from(EquipmentDto.from(savedEquipment), savedDetails);
    }

    @Transactional
    public VehicleDetailDto update(UUID equipmentId, VehicleRequest request) {
        Equipment equipment = equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)
                .orElseThrow(() -> RestException.notFound("Equipment not found: " + equipmentId));
        if (equipment.getCategory() != EquipmentCategory.VEHICLE) {
            throw RestException.badRequest("Equipment is not a vehicle: " + equipmentId);
        }
        VehicleDetails details = vehicleDetailsRepository.findByEquipmentIdAndIsDeletedFalse(equipmentId)
                .orElseThrow(() -> RestException.notFound("Vehicle details not found: " + equipmentId));

        applyEquipment(equipment, request);
        applyDetails(details, request);
        return VehicleDetailDto.from(EquipmentDto.from(equipment), details);
    }

    @Transactional
    public void delete(UUID equipmentId) {
        Equipment equipment = equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)
                .orElseThrow(() -> RestException.notFound("Equipment not found: " + equipmentId));
        VehicleDetails details = vehicleDetailsRepository.findByEquipmentIdAndIsDeletedFalse(equipmentId)
                .orElseThrow(() -> RestException.notFound("Vehicle details not found: " + equipmentId));
        details.setDeleted(true);
        equipment.setDeleted(true);
    }

    private void validateUniqueCreate(VehicleRequest request) {
        if (equipmentRepository.existsByCodeAndIsDeletedFalse(request.code())) {
            throw RestException.conflict("Equipment code already exists: " + request.code());
        }
        if (equipmentRepository.existsByInventoryNumberAndIsDeletedFalse(request.inventoryNumber())) {
            throw RestException.conflict("Inventory number already exists: " + request.inventoryNumber());
        }
        if (vehicleDetailsRepository.existsByPlateNumberAndIsDeletedFalse(request.plateNumber())) {
            throw RestException.conflict("Vehicle plate number already exists: " + request.plateNumber());
        }
        if (request.vin() != null && !request.vin().isBlank()
                && vehicleDetailsRepository.existsByVinAndIsDeletedFalse(request.vin())) {
            throw RestException.conflict("Vehicle VIN already exists: " + request.vin());
        }
    }

    private void applyEquipment(Equipment equipment, VehicleRequest request) {
        equipment.setCode(request.code());
        equipment.setName(request.name());
        equipment.setInventoryNumber(request.inventoryNumber());
        equipment.setTechnicalNumber(request.technicalNumber());
        equipment.setSerialNumber(request.serialNumber());
        equipment.setModel(request.model());
        equipment.setEquipmentTypeId(request.equipmentTypeId());
        equipment.setDepartmentId(request.departmentId());
        equipment.setLocationId(request.locationId());
        equipment.setStatus(request.status() != null ? request.status() : EquipmentStatus.ACTIVE);
        equipment.setCategory(EquipmentCategory.VEHICLE);
        equipment.setManufacturer(request.brand());
    }

    private void applyDetails(VehicleDetails details, VehicleRequest request) {
        if (request.currentOdometerKm() != null && request.currentOdometerKm() < 0) {
            throw RestException.badRequest("Current odometer cannot be negative");
        }
        if (request.currentEngineHours() != null && request.currentEngineHours() < 0) {
            throw RestException.badRequest("Current engine hours cannot be negative");
        }
        details.setPlateNumber(request.plateNumber());
        details.setVin(request.vin());
        details.setBrand(request.brand());
        details.setModel(request.model());
        details.setManufactureYear(request.manufactureYear());
        details.setVehicleType(request.vehicleType());
        details.setBodyNumber(request.bodyNumber());
        details.setChassisNumber(request.chassisNumber());
        details.setEngineNumber(request.engineNumber());
        details.setFuelType(request.fuelType());
        details.setFuelTankCapacity(request.fuelTankCapacity());
        details.setCarryingCapacity(request.carryingCapacity());
        details.setSeatCount(request.seatCount());
        details.setAssignedDriverId(request.assignedDriverId());
        details.setCurrentOdometerKm(request.currentOdometerKm() != null ? request.currentOdometerKm() : 0);
        details.setCurrentEngineHours(request.currentEngineHours() != null ? request.currentEngineHours() : 0);
        details.setRegistrationCertificateNumber(request.registrationCertificateNumber());
        details.setInsurancePolicyNumber(request.insurancePolicyNumber());
        details.setInsuranceExpiryDate(request.insuranceExpiryDate());
        details.setTechnicalInspectionExpiryDate(request.technicalInspectionExpiryDate());
        details.setGpsDeviceId(request.gpsDeviceId());
    }
}
```

- [ ] **Step 7: Add vehicle controller**

Create `/Users/tenzorsoft/Desktop/Work/toir/toir-backend/src/main/java/com/toir/controller/VehicleController.java`:

```java
package com.toir.controller;

import com.toir.config.PaginatedResponse;
import com.toir.dto.vehicle.VehicleDetailDto;
import com.toir.dto.vehicle.VehicleRequest;
import com.toir.dto.vehicle.VehicleSummaryDto;
import com.toir.enums.EquipmentStatus;
import com.toir.security.SecurityScope;
import com.toir.service.VehicleService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/vehicles")
@Tag(name = "vehicles")
public class VehicleController {

    private final VehicleService service;
    private final SecurityScope securityScope;

    public VehicleController(VehicleService service, SecurityScope securityScope) {
        this.service = service;
        this.securityScope = securityScope;
    }

    @GetMapping
    public PaginatedResponse<VehicleSummaryDto> list(
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) EquipmentStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        return service.list(
                securityScope.enforceDepartmentScope(departmentId),
                status,
                search,
                page,
                pageSize
        );
    }

    @GetMapping("/{equipmentId}")
    public VehicleDetailDto get(@PathVariable UUID equipmentId) {
        return service.findByEquipmentId(equipmentId);
    }

    @PostMapping
    public ResponseEntity<VehicleDetailDto> create(@Valid @RequestBody VehicleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{equipmentId}")
    public VehicleDetailDto update(@PathVariable UUID equipmentId, @Valid @RequestBody VehicleRequest request) {
        return service.update(equipmentId, request);
    }

    @DeleteMapping("/{equipmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID equipmentId) {
        service.delete(equipmentId);
    }
}
```

- [ ] **Step 8: Run vehicle tests and backend compile**

Run:

```bash
cd /Users/tenzorsoft/Desktop/Work/toir/toir-backend
./mvnw test -Dtest=VehicleServiceTest,EquipmentDtoTest,PaginatedResponseTest
./mvnw test -DskipTests
```

Expected: both commands finish with `BUILD SUCCESS`.

- [ ] **Step 9: Commit backend vehicle foundation**

Run:

```bash
cd /Users/tenzorsoft/Desktop/Work/toir/toir-backend
git add src/main/java/com/toir/entity/VehicleDetails.java \
  src/main/java/com/toir/repository/VehicleDetailsRepository.java \
  src/main/java/com/toir/dto/vehicle \
  src/main/java/com/toir/service/VehicleService.java \
  src/main/java/com/toir/controller/VehicleController.java \
  src/main/resources/db/migration/V20260427_1__fleet_vehicle_foundation.sql \
  src/test/java/com/toir/service/VehicleServiceTest.java
git commit -m "feat: add vehicle foundation api"
```

---

### Task 4: Frontend Fleet API Layer and Tests

**Files:**
- Modify: `/Users/tenzorsoft/Desktop/Work/toir/toir-front/package.json`
- Create: `/Users/tenzorsoft/Desktop/Work/toir/toir-front/src/test/setup.ts`
- Create: `/Users/tenzorsoft/Desktop/Work/toir/toir-front/src/features/fleet/types.ts`
- Create: `/Users/tenzorsoft/Desktop/Work/toir/toir-front/src/features/fleet/query-keys.ts`
- Create: `/Users/tenzorsoft/Desktop/Work/toir/toir-front/src/features/fleet/api.ts`
- Create: `/Users/tenzorsoft/Desktop/Work/toir/toir-front/src/features/fleet/api.test.ts`

- [ ] **Step 1: Add Vitest test dependencies and scripts**

Modify `/Users/tenzorsoft/Desktop/Work/toir/toir-front/package.json`:

```json
"scripts": {
  "dev": "vite --host 0.0.0.0",
  "build": "tsc -b && vite build",
  "lint": "eslint .",
  "preview": "vite preview",
  "format": "prettier --write \"**/*.{ts,tsx,js,jsx,css,md}\"",
  "test": "vitest run",
  "test:watch": "vitest"
}
```

Add dev dependencies:

```json
"@testing-library/jest-dom": "^6.9.1",
"@testing-library/react": "^16.3.0",
"jsdom": "^27.1.0",
"vitest": "^4.0.15"
```

Install:

```bash
cd /Users/tenzorsoft/Desktop/Work/toir/toir-front
npm install
```

Expected: `package-lock.json` updates and `npm` exits with code 0.

- [ ] **Step 2: Add test setup**

Create `/Users/tenzorsoft/Desktop/Work/toir/toir-front/src/test/setup.ts`:

```ts
import "@testing-library/jest-dom/vitest";
```

Modify `/Users/tenzorsoft/Desktop/Work/toir/toir-front/vite.config.ts` so `defineConfig` comes from `vitest/config`:

```ts
import { defineConfig } from "vitest/config";
```

Keep the existing React and Tailwind plugin imports, then add the `test` block:

```ts
export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      "@": fileURLToPath(new URL("./src", import.meta.url)),
    },
  },
  test: {
    environment: "jsdom",
    setupFiles: "./src/test/setup.ts",
  },
});
```

- [ ] **Step 3: Write the failing fleet API test**

Create `/Users/tenzorsoft/Desktop/Work/toir/toir-front/src/features/fleet/api.test.ts`:

```ts
import { afterEach, describe, expect, it, vi } from "vitest";
import { fleetApi } from "./api";

describe("fleetApi", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("loads vehicles from repo-style endpoint", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          items: [],
          meta: { page: 1, pageSize: 20, total: 0 },
        }),
        { status: 200, headers: { "Content-Type": "application/json" } },
      ),
    );

    await fleetApi.getVehicles("token", { page: 1, pageSize: 20 });

    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringContaining("/vehicles?page=1&pageSize=20"),
      expect.objectContaining({
        headers: expect.objectContaining({ Authorization: "Bearer token" }),
      }),
    );
  });

  it("creates a vehicle with VEHICLE equipment category handled by backend", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          equipment: { id: "eq-1", code: "VH-001", category: "VEHICLE" },
          vehicleDetails: { plateNumber: "01A123AA", vehicleType: "TRUCK" },
        }),
        { status: 201, headers: { "Content-Type": "application/json" } },
      ),
    );

    await fleetApi.createVehicle("token", {
      code: "VH-001",
      name: "Truck 001",
      inventoryNumber: "INV-VH-001",
      equipmentTypeId: "type-1",
      departmentId: "dept-1",
      plateNumber: "01A123AA",
      vehicleType: "TRUCK",
    });

    const [, init] = fetchMock.mock.calls[0];
    expect(init?.method).toBe("POST");
    expect(JSON.parse(init?.body as string)).toMatchObject({
      code: "VH-001",
      plateNumber: "01A123AA",
      vehicleType: "TRUCK",
    });
  });
});
```

- [ ] **Step 4: Run the failing frontend test**

Run:

```bash
cd /Users/tenzorsoft/Desktop/Work/toir/toir-front
npm run test -- src/features/fleet/api.test.ts
```

Expected: fails because `src/features/fleet/api.ts` does not exist.

- [ ] **Step 5: Add fleet types and query keys**

Create `/Users/tenzorsoft/Desktop/Work/toir/toir-front/src/features/fleet/types.ts`:

```ts
import type { PaginatedResponse } from "@/types/api";

export type EquipmentCategory =
  | "PRODUCTION_EQUIPMENT"
  | "VEHICLE"
  | "ENERGY_EQUIPMENT"
  | "INSTRUMENTATION"
  | "BUILDING_INFRASTRUCTURE"
  | "OTHER";

export type VehicleType =
  | "PASSENGER_CAR"
  | "TRUCK"
  | "BUS"
  | "SPECIAL_EQUIPMENT"
  | "FORKLIFT"
  | "TRAILER"
  | "OTHER";

export type VehicleStatus =
  | "ACTIVE"
  | "STANDBY"
  | "IN_REPAIR"
  | "OUT_OF_SERVICE"
  | "CONSERVATION"
  | "DECOMMISSIONED";

export interface VehicleSummary {
  equipmentId: string;
  code: string;
  name: string;
  inventoryNumber: string;
  status: VehicleStatus;
  department?: { id: string; code: string; name: string } | null;
  location?: { id: string; code: string; name: string } | null;
  plateNumber: string;
  vin?: string | null;
  brand?: string | null;
  model?: string | null;
  vehicleType: VehicleType;
  assignedDriverId?: string | null;
  currentOdometerKm: number;
  currentEngineHours: number;
  insuranceExpiryDate?: string | null;
  technicalInspectionExpiryDate?: string | null;
}

export interface VehicleDetail {
  equipment: {
    id: string;
    code: string;
    name: string;
    inventoryNumber: string;
    status: VehicleStatus;
    category: EquipmentCategory;
  };
  vehicleDetails: {
    id: string;
    plateNumber: string;
    vin?: string | null;
    brand?: string | null;
    model?: string | null;
    manufactureYear?: number | null;
    vehicleType: VehicleType;
    currentOdometerKm: number;
    currentEngineHours: number;
    insurancePolicyNumber?: string | null;
    insuranceExpiryDate?: string | null;
    technicalInspectionExpiryDate?: string | null;
    gpsDeviceId?: string | null;
  };
}

export interface VehiclePayload {
  code: string;
  name: string;
  inventoryNumber: string;
  technicalNumber?: string | null;
  serialNumber?: string | null;
  equipmentTypeId: string;
  departmentId: string;
  locationId?: string | null;
  status?: VehicleStatus;
  plateNumber: string;
  vin?: string | null;
  brand?: string | null;
  model?: string | null;
  manufactureYear?: number | null;
  vehicleType: VehicleType;
  currentOdometerKm?: number;
  currentEngineHours?: number;
  insurancePolicyNumber?: string | null;
  insuranceExpiryDate?: string | null;
  technicalInspectionExpiryDate?: string | null;
  gpsDeviceId?: string | null;
}

export type VehicleListResponse = PaginatedResponse<VehicleSummary>;
```

Create `/Users/tenzorsoft/Desktop/Work/toir/toir-front/src/features/fleet/query-keys.ts`:

```ts
export const fleetQueryKeys = {
  all: ["fleet"] as const,
  vehicles: (params: Record<string, string | number | undefined>) =>
    [...fleetQueryKeys.all, "vehicles", params] as const,
  vehicle: (equipmentId: string) =>
    [...fleetQueryKeys.all, "vehicle", equipmentId] as const,
};
```

- [ ] **Step 6: Add fleet API client**

Create `/Users/tenzorsoft/Desktop/Work/toir/toir-front/src/features/fleet/api.ts`:

```ts
import type { VehicleDetail, VehicleListResponse, VehiclePayload } from "./types";

const API_URL = import.meta.env.VITE_API_URL ?? "http://localhost:8080/api/v1";

type RequestOptions = RequestInit & {
  token?: string | null;
};

async function request<T>(path: string, options: RequestOptions = {}) {
  const response = await fetch(`${API_URL}${path}`, {
    ...options,
    headers: {
      "Content-Type": "application/json",
      ...(options.token ? { Authorization: `Bearer ${options.token}` } : {}),
      ...options.headers,
    },
  });

  if (!response.ok) {
    const payload = await response.json().catch(() => ({ message: "Request failed" }));
    throw new Error(payload.message ?? "Request failed");
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return (await response.json()) as T;
}

export const fleetApi = {
  getVehicles: (
    token: string,
    params: {
      page?: number;
      pageSize?: number;
      search?: string;
      status?: string;
      departmentId?: string;
    } = {},
  ) => {
    const query = new URLSearchParams();
    query.set("page", String(params.page ?? 1));
    query.set("pageSize", String(params.pageSize ?? 20));
    if (params.search) query.set("search", params.search);
    if (params.status) query.set("status", params.status);
    if (params.departmentId) query.set("departmentId", params.departmentId);
    return request<VehicleListResponse>(`/vehicles?${query.toString()}`, { token });
  },

  getVehicle: (token: string, equipmentId: string) =>
    request<VehicleDetail>(`/vehicles/${equipmentId}`, { token }),

  createVehicle: (token: string, payload: VehiclePayload) =>
    request<VehicleDetail>("/vehicles", {
      method: "POST",
      token,
      body: JSON.stringify(payload),
    }),

  updateVehicle: (token: string, equipmentId: string, payload: VehiclePayload) =>
    request<VehicleDetail>(`/vehicles/${equipmentId}`, {
      method: "PUT",
      token,
      body: JSON.stringify(payload),
    }),

  deleteVehicle: (token: string, equipmentId: string) =>
    request<void>(`/vehicles/${equipmentId}`, {
      method: "DELETE",
      token,
    }),
};
```

- [ ] **Step 7: Run fleet API tests**

Run:

```bash
cd /Users/tenzorsoft/Desktop/Work/toir/toir-front
npm run test -- src/features/fleet/api.test.ts
```

Expected: tests pass.

- [ ] **Step 8: Commit frontend fleet API layer**

Run:

```bash
cd /Users/tenzorsoft/Desktop/Work/toir/toir-front
git add package.json package-lock.json vite.config.ts \
  src/test/setup.ts \
  src/features/fleet/types.ts \
  src/features/fleet/query-keys.ts \
  src/features/fleet/api.ts \
  src/features/fleet/api.test.ts
git commit -m "feat: add fleet api client"
```

---

### Task 5: Frontend Fleet Registry and Vehicle Card Shell

**Files:**
- Create: `/Users/tenzorsoft/Desktop/Work/toir/toir-front/src/features/fleet/components/vehicle-status-badge.tsx`
- Create: `/Users/tenzorsoft/Desktop/Work/toir/toir-front/src/features/fleet/components/vehicle-registry-table.tsx`
- Create: `/Users/tenzorsoft/Desktop/Work/toir/toir-front/src/features/fleet/pages/fleet-registry-page.tsx`
- Create: `/Users/tenzorsoft/Desktop/Work/toir/toir-front/src/features/fleet/pages/vehicle-card-page.tsx`
- Modify: `/Users/tenzorsoft/Desktop/Work/toir/toir-front/src/app/routes.tsx`
- Modify: `/Users/tenzorsoft/Desktop/Work/toir/toir-front/src/components/layout/app-shell.tsx`

- [ ] **Step 1: Add status badge**

Create `/Users/tenzorsoft/Desktop/Work/toir/toir-front/src/features/fleet/components/vehicle-status-badge.tsx`:

```tsx
import { Badge } from "@/components/ui/badge";
import type { VehicleStatus } from "../types";

const toneByStatus: Record<VehicleStatus, "success" | "warning" | "danger" | "neutral"> = {
  ACTIVE: "success",
  STANDBY: "neutral",
  IN_REPAIR: "warning",
  OUT_OF_SERVICE: "danger",
  CONSERVATION: "neutral",
  DECOMMISSIONED: "neutral",
};

export function VehicleStatusBadge({ status }: { status: VehicleStatus }) {
  return <Badge tone={toneByStatus[status]}>{status.replaceAll("_", " ")}</Badge>;
}
```

- [ ] **Step 2: Add vehicle registry table**

Create `/Users/tenzorsoft/Desktop/Work/toir/toir-front/src/features/fleet/components/vehicle-registry-table.tsx`:

```tsx
import { useMemo } from "react";
import { Link } from "react-router-dom";
import { createColumnHelper } from "@tanstack/react-table";
import { DataTable } from "@/components/data-display/data-table";
import { formatDate, formatNumber } from "@/lib/utils";
import type { VehicleSummary } from "../types";
import { VehicleStatusBadge } from "./vehicle-status-badge";

const columnHelper = createColumnHelper<VehicleSummary>();

export function VehicleRegistryTable({
  data,
  page,
  pageSize,
  total,
  onPreviousPage,
  onNextPage,
}: {
  data: VehicleSummary[];
  page: number;
  pageSize: number;
  total: number;
  onPreviousPage: () => void;
  onNextPage: () => void;
}) {
  const columns = useMemo(
    () => [
      columnHelper.accessor("plateNumber", {
        header: "Plate",
        cell: (info) => (
          <Link
            className="font-semibold text-[var(--primary)] hover:underline"
            to={`/vehicles/${info.row.original.equipmentId}`}
          >
            {info.getValue()}
          </Link>
        ),
      }),
      columnHelper.accessor("name", {
        header: "Vehicle",
        cell: (info) => (
          <div>
            <p className="font-medium">{info.getValue()}</p>
            <p className="text-xs text-[var(--muted)]">
              {[info.row.original.brand, info.row.original.model].filter(Boolean).join(" ") || info.row.original.code}
            </p>
          </div>
        ),
      }),
      columnHelper.accessor("vehicleType", {
        header: "Type",
      }),
      columnHelper.accessor("department", {
        header: "Department",
        cell: (info) => info.getValue()?.name ?? "—",
      }),
      columnHelper.accessor("currentOdometerKm", {
        header: "Odometer",
        cell: (info) => `${formatNumber(info.getValue())} km`,
      }),
      columnHelper.accessor("currentEngineHours", {
        header: "Engine Hours",
        cell: (info) => `${formatNumber(info.getValue())} h`,
      }),
      columnHelper.accessor("status", {
        header: "Status",
        cell: (info) => <VehicleStatusBadge status={info.getValue()} />,
      }),
      columnHelper.accessor("insuranceExpiryDate", {
        header: "Insurance",
        cell: (info) => (info.getValue() ? formatDate(info.getValue()!) : "—"),
      }),
      columnHelper.accessor("technicalInspectionExpiryDate", {
        header: "Inspection",
        cell: (info) => (info.getValue() ? formatDate(info.getValue()!) : "—"),
      }),
    ],
    [],
  );

  return (
    <DataTable
      columns={columns}
      data={data}
      page={page}
      pageSize={pageSize}
      total={total}
      onPreviousPage={onPreviousPage}
      onNextPage={onNextPage}
      searchPlaceholder="Filter current page by plate, model, status, or department"
    />
  );
}
```

- [ ] **Step 3: Add fleet registry page**

Create `/Users/tenzorsoft/Desktop/Work/toir/toir-front/src/features/fleet/pages/fleet-registry-page.tsx`:

```tsx
import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { EmptyState } from "@/components/ui/empty-state";
import { Input } from "@/components/ui/input";
import { PageToolbar } from "@/components/ui/page-toolbar";
import { useAuth } from "@/app/auth-provider";
import { fleetApi } from "../api";
import { fleetQueryKeys } from "../query-keys";
import { VehicleRegistryTable } from "../components/vehicle-registry-table";

export function FleetRegistryPage() {
  const { token } = useAuth();
  const [page, setPage] = useState(1);
  const [search, setSearch] = useState("");

  const vehiclesQuery = useQuery({
    queryKey: fleetQueryKeys.vehicles({ page, pageSize: 20, search }),
    queryFn: () => fleetApi.getVehicles(token!, { page, pageSize: 20, search }),
    enabled: Boolean(token),
  });

  if (vehiclesQuery.isError) {
    return (
      <EmptyState
        title="Failed to load fleet"
        description={vehiclesQuery.error.message}
      />
    );
  }

  return (
    <div className="space-y-5">
      <PageToolbar
        title="Fleet"
        description="Vehicle assets managed through the common TOIR equipment model."
        filters={
          <Input
            className="min-w-[320px]"
            placeholder="Search by plate, vehicle name, inventory number, or model"
            value={search}
            onChange={(event) => {
              setSearch(event.target.value);
              setPage(1);
            }}
          />
        }
      />

      {vehiclesQuery.data ? (
        <VehicleRegistryTable
          data={vehiclesQuery.data.items}
          page={vehiclesQuery.data.meta.page}
          pageSize={vehiclesQuery.data.meta.pageSize}
          total={vehiclesQuery.data.meta.total}
          onPreviousPage={() => setPage((current) => Math.max(1, current - 1))}
          onNextPage={() => setPage((current) => current + 1)}
        />
      ) : null}
    </div>
  );
}
```

- [ ] **Step 4: Add vehicle card shell page**

Create `/Users/tenzorsoft/Desktop/Work/toir/toir-front/src/features/fleet/pages/vehicle-card-page.tsx`:

```tsx
import { useQuery } from "@tanstack/react-query";
import { useParams } from "react-router-dom";
import { Card, CardContent, CardHeader } from "@/components/ui/card";
import { EmptyState } from "@/components/ui/empty-state";
import { useAuth } from "@/app/auth-provider";
import { formatDate, formatNumber } from "@/lib/utils";
import { fleetApi } from "../api";
import { fleetQueryKeys } from "../query-keys";
import { VehicleStatusBadge } from "../components/vehicle-status-badge";

export function VehicleCardPage() {
  const { token } = useAuth();
  const { equipmentId } = useParams();

  const vehicleQuery = useQuery({
    queryKey: fleetQueryKeys.vehicle(equipmentId ?? ""),
    queryFn: () => fleetApi.getVehicle(token!, equipmentId!),
    enabled: Boolean(token) && Boolean(equipmentId),
  });

  if (vehicleQuery.isError) {
    return (
      <EmptyState
        title="Failed to load vehicle"
        description={vehicleQuery.error.message}
      />
    );
  }

  const vehicle = vehicleQuery.data;

  if (!vehicle) {
    return null;
  }

  return (
    <div className="space-y-5">
      <header className="flex flex-col gap-3 md:flex-row md:items-start md:justify-between">
        <div>
          <p className="text-sm text-[var(--muted)]">{vehicle.vehicleDetails.plateNumber}</p>
          <h1 className="text-2xl font-semibold text-[var(--foreground)]">
            {vehicle.equipment.name}
          </h1>
          <p className="mt-1 text-sm text-[var(--muted)]">
            {[vehicle.vehicleDetails.brand, vehicle.vehicleDetails.model].filter(Boolean).join(" ") || vehicle.equipment.code}
          </p>
        </div>
        <VehicleStatusBadge status={vehicle.equipment.status} />
      </header>

      <div className="grid gap-4 lg:grid-cols-3">
        <Card>
          <CardHeader>
            <h2 className="text-base font-semibold text-[var(--foreground)]">Passport</h2>
          </CardHeader>
          <CardContent className="space-y-2 text-sm">
            <Info label="VIN" value={vehicle.vehicleDetails.vin ?? "—"} />
            <Info label="Type" value={vehicle.vehicleDetails.vehicleType} />
            <Info label="Year" value={vehicle.vehicleDetails.manufactureYear?.toString() ?? "—"} />
            <Info label="GPS" value={vehicle.vehicleDetails.gpsDeviceId ?? "—"} />
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <h2 className="text-base font-semibold text-[var(--foreground)]">Meters</h2>
          </CardHeader>
          <CardContent className="space-y-2 text-sm">
            <Info label="Odometer" value={`${formatNumber(vehicle.vehicleDetails.currentOdometerKm)} km`} />
            <Info label="Engine hours" value={`${formatNumber(vehicle.vehicleDetails.currentEngineHours)} h`} />
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <h2 className="text-base font-semibold text-[var(--foreground)]">Documents</h2>
          </CardHeader>
          <CardContent className="space-y-2 text-sm">
            <Info label="Insurance policy" value={vehicle.vehicleDetails.insurancePolicyNumber ?? "—"} />
            <Info
              label="Insurance expiry"
              value={vehicle.vehicleDetails.insuranceExpiryDate ? formatDate(vehicle.vehicleDetails.insuranceExpiryDate) : "—"}
            />
            <Info
              label="Technical inspection"
              value={vehicle.vehicleDetails.technicalInspectionExpiryDate ? formatDate(vehicle.vehicleDetails.technicalInspectionExpiryDate) : "—"}
            />
          </CardContent>
        </Card>
      </div>
    </div>
  );
}

function Info({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between gap-4">
      <span className="text-[var(--muted)]">{label}</span>
      <span className="font-medium text-[var(--foreground)]">{value}</span>
    </div>
  );
}
```

- [ ] **Step 5: Add routes**

Modify `/Users/tenzorsoft/Desktop/Work/toir/toir-front/src/app/routes.tsx`.

Add lazy imports near the other page imports:

```tsx
const FleetRegistryPage = lazy(() =>
  import("@/features/fleet/pages/fleet-registry-page").then((module) => ({
    default: module.FleetRegistryPage,
  })),
);

const VehicleCardPage = lazy(() =>
  import("@/features/fleet/pages/vehicle-card-page").then((module) => ({
    default: module.VehicleCardPage,
  })),
);
```

Add protected routes inside `<Route element={<ProtectedLayout />}>`:

```tsx
<Route path="/vehicles" element={<FleetRegistryPage />} />
<Route path="/vehicles/:equipmentId" element={<VehicleCardPage />} />
```

- [ ] **Step 6: Add Fleet nav item**

Modify `/Users/tenzorsoft/Desktop/Work/toir/toir-front/src/components/layout/app-shell.tsx`.

Inside the `equipment` nav group, add:

```tsx
{ to: "/vehicles", labelKey: "nav.fleet", icon: HardDrive },
```

Modify locale files:

- `/Users/tenzorsoft/Desktop/Work/toir/toir-front/src/i18n/locales/en.json`
- `/Users/tenzorsoft/Desktop/Work/toir/toir-front/src/i18n/locales/ru.json`
- `/Users/tenzorsoft/Desktop/Work/toir/toir-front/src/i18n/locales/uz.json`

Add `nav.fleet` with these values:

```json
"fleet": "Fleet"
```

```json
"fleet": "Автопарк"
```

```json
"fleet": "Avtopark"
```

- [ ] **Step 7: Run frontend tests and build**

Run:

```bash
cd /Users/tenzorsoft/Desktop/Work/toir/toir-front
npm run test -- src/features/fleet/api.test.ts
npm run lint
npm run build
```

Expected: tests pass, lint completes, build completes.

- [ ] **Step 8: Commit frontend fleet pages**

Run:

```bash
cd /Users/tenzorsoft/Desktop/Work/toir/toir-front
git add src/features/fleet/components \
  src/features/fleet/pages \
  src/app/routes.tsx \
  src/components/layout/app-shell.tsx \
  src/i18n/locales/en.json \
  src/i18n/locales/ru.json \
  src/i18n/locales/uz.json
git commit -m "feat: add fleet registry pages"
```

---

### Task 6: End-to-End Verification

**Files:**
- No code files created.
- Verify backend and frontend repositories are clean after commits.

- [ ] **Step 1: Run backend verification**

Run:

```bash
cd /Users/tenzorsoft/Desktop/Work/toir/toir-backend
./mvnw test
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 2: Run frontend verification**

Run:

```bash
cd /Users/tenzorsoft/Desktop/Work/toir/toir-front
npm run test
npm run lint
npm run build
```

Expected: all commands exit with code 0.

- [ ] **Step 3: Start local backend**

Run:

```bash
cd /Users/tenzorsoft/Desktop/Work/toir/toir-backend
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
```

Expected: backend starts on `http://localhost:8080`.

- [ ] **Step 4: Start local frontend**

In a second terminal, run:

```bash
cd /Users/tenzorsoft/Desktop/Work/toir/toir-front
VITE_API_URL=http://localhost:8080/api/v1 npm run dev
```

Expected: Vite prints a local URL, normally `http://localhost:5173`.

- [ ] **Step 5: Manual smoke test**

Use an authenticated session and verify:

1. `/vehicles` loads without a route error.
2. Fleet appears in the Equipment nav group.
3. `/vehicles` calls `GET /api/v1/vehicles?page=1&pageSize=20`.
4. Empty fleet data renders a table shell or empty state without a crash.
5. A created vehicle through the backend appears in `/vehicles`.
6. Opening `/vehicles/{equipmentId}` shows Passport, Meters, and Documents cards.

- [ ] **Step 6: Confirm git status**

Run:

```bash
cd /Users/tenzorsoft/Desktop/Work/toir/toir-backend
git status --short
cd /Users/tenzorsoft/Desktop/Work/toir/toir-front
git status --short
```

Expected: both commands print no tracked or untracked changes.

## Follow-Up Plans

After this foundation lands, create separate plans in this order:

1. Universal meter correction workflow and vehicle meter history.
2. Counter-based preventive maintenance and `VehicleMaintenanceDueService`.
3. Repair request and work order vehicle contexts.
4. Vehicle inspections and document expiry notifications.
5. Tire management, fuel records, and fleet dashboard.
