package com.toir.service.equipment;

import com.toir.dto.equipment.WarrantyReportItem;
import com.toir.entity.Department;
import com.toir.entity.Supplier;
import com.toir.entity.equipment.Equipment;
import com.toir.repository.SupplierRepository;
import com.toir.repository.department.DepartmentRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.security.ScopeAccessService;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WarrantyReportServiceTest {

    @Mock
    EquipmentRepository equipmentRepository;

    @Mock
    DepartmentRepository departmentRepository;

    @Mock
    SupplierRepository supplierRepository;

    @Mock
    ScopeAccessService scopeAccessService;

    @InjectMocks
    WarrantyReportService service;

    @Test
    void reportFiltersByStatusAndEnrichesNames() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        UUID departmentId = UUID.randomUUID();
        UUID supplierId = UUID.randomUUID();

        Equipment active = warrantyEquipment("EQ-ACTIVE", today.plusDays(60), departmentId, supplierId);
        Equipment expiring7 = warrantyEquipment("EQ-7", today.plusDays(5), departmentId, supplierId);

        when(equipmentRepository.findAllActiveWithWarranty(null, null, null))
                .thenReturn(List.of(active, expiring7));
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(departmentRepository.findAllByIdInAndIsDeletedFalse(Set.of(departmentId)))
                .thenReturn(List.of(department(departmentId, "Maintenance")));
        when(supplierRepository.findAllByIdInAndIsDeletedFalse(Set.of(supplierId)))
                .thenReturn(List.of(supplier(supplierId, "KSB Service")));

        Page<WarrantyReportItem> result = service.report(null, "EXPIRING_7", null, null, 0, 20);

        assertThat(result.getTotalElements()).isEqualTo(1);
        WarrantyReportItem item = result.getContent().get(0);
        assertThat(item.equipmentCode()).isEqualTo("EQ-7");
        assertThat(item.warrantyStatus()).isEqualTo("EXPIRING_7");
        assertThat(item.departmentName()).isEqualTo("Maintenance");
        assertThat(item.warrantySupplierName()).isEqualTo("KSB Service");
    }

    @Test
    void reportAppliesDepartmentScopeForNonAdmin() {
        UUID currentDept = UUID.randomUUID();
        UUID otherDept = UUID.randomUUID();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        Equipment inScope = warrantyEquipment("EQ-IN", today.plusDays(20), currentDept, null);
        Equipment outOfScope = warrantyEquipment("EQ-OUT", today.plusDays(20), otherDept, null);

        when(equipmentRepository.findAllActiveWithWarranty(null, null, null))
                .thenReturn(List.of(inScope, outOfScope));
        when(scopeAccessService.isScopeAdmin()).thenReturn(false);
        when(scopeAccessService.currentDepartmentIdOrNull()).thenReturn(currentDept);
        when(departmentRepository.findAllByIdInAndIsDeletedFalse(any()))
                .thenReturn(List.of(department(currentDept, "Dept A")));

        Page<WarrantyReportItem> result = service.report(null, "ALL", null, null, 0, 20);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).equipmentCode()).isEqualTo("EQ-IN");
    }

    @Test
    void reportIncludesEquipmentMatchedByResponsibleDepartmentScope() {
        UUID currentDept = UUID.randomUUID();
        UUID owningDept = UUID.randomUUID();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        Equipment inScope = warrantyEquipment("EQ-RESP", today.plusDays(15), owningDept, null);
        inScope.setResponsibleDepartmentId(currentDept);

        when(equipmentRepository.findAllActiveWithWarranty(null, null, null))
                .thenReturn(List.of(inScope));
        when(scopeAccessService.isScopeAdmin()).thenReturn(false);
        when(scopeAccessService.currentDepartmentIdOrNull()).thenReturn(currentDept);
        when(departmentRepository.findAllByIdInAndIsDeletedFalse(Set.of(currentDept)))
                .thenReturn(List.of(department(currentDept, "Responsible Dept")));

        Page<WarrantyReportItem> result = service.report(null, "ALL", null, null, 0, 20);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).departmentName()).isEqualTo("Responsible Dept");
    }

    @ParameterizedTest
    @CsvSource({
            "ACTIVE, 60, ACTIVE",
            "EXPIRING_30, 20, EXPIRING_30",
            "EXPIRING_7, 5, EXPIRING_7",
            "EXPIRED, -3, EXPIRED"
    })
    void reportFiltersByEachWarrantyStatus(String filter, int daysOffset, String expectedStatus) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        UUID departmentId = UUID.randomUUID();
        Equipment equipment = warrantyEquipment("EQ-STATUS", today.plusDays(daysOffset), departmentId, null);

        when(equipmentRepository.findAllActiveWithWarranty(null, null, null))
                .thenReturn(List.of(equipment));
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(departmentRepository.findAllByIdInAndIsDeletedFalse(any()))
                .thenReturn(List.of(department(departmentId, "Dept")));

        Page<WarrantyReportItem> result = service.report(null, filter, null, null, 0, 20);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).warrantyStatus()).isEqualTo(expectedStatus);
    }

    @Test
    void reportPassesRepositoryFiltersAndPaginatesResults() {
        UUID departmentId = UUID.randomUUID();
        UUID supplierId = UUID.randomUUID();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        Equipment first = warrantyEquipment("EQ-1", today.plusDays(5), departmentId, supplierId);
        Equipment second = warrantyEquipment("EQ-2", today.plusDays(10), departmentId, supplierId);
        Equipment third = warrantyEquipment("EQ-3", today.plusDays(20), departmentId, supplierId);

        when(equipmentRepository.findAllActiveWithWarranty(departmentId, supplierId, "pump"))
                .thenReturn(List.of(third, second, first));
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(departmentRepository.findAllByIdInAndIsDeletedFalse(any()))
                .thenReturn(List.of(department(departmentId, "Dept")));
        when(supplierRepository.findAllByIdInAndIsDeletedFalse(any()))
                .thenReturn(List.of(supplier(supplierId, "Vendor")));

        Page<WarrantyReportItem> page0 = service.report(departmentId, "ALL", supplierId, "pump", 0, 2);

        assertThat(page0.getTotalElements()).isEqualTo(3);
        assertThat(page0.getContent()).extracting(WarrantyReportItem::equipmentCode)
                .containsExactly("EQ-1", "EQ-2");

        Page<WarrantyReportItem> page1 = service.report(departmentId, "ALL", supplierId, "pump", 1, 2);

        verify(equipmentRepository, times(2))
                .findAllActiveWithWarranty(departmentId, supplierId, "pump");
        assertThat(page1.getContent()).extracting(WarrantyReportItem::equipmentCode)
                .containsExactly("EQ-3");
    }

    @Test
    void reportUsesWarrantyUntilWhenEndDateMissing() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        UUID departmentId = UUID.randomUUID();
        LocalDate until = today.plusDays(12);

        Equipment equipment = warrantyEquipment("EQ-UNTIL", null, departmentId, null);
        equipment.setWarrantyUntil(until);

        when(equipmentRepository.findAllActiveWithWarranty(isNull(), isNull(), isNull()))
                .thenReturn(List.of(equipment));
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(departmentRepository.findAllByIdInAndIsDeletedFalse(any()))
                .thenReturn(List.of(department(departmentId, "Dept")));

        Page<WarrantyReportItem> result = service.report(null, "EXPIRING_30", null, null, 0, 20);

        assertThat(result.getTotalElements()).isEqualTo(1);
        WarrantyReportItem item = result.getContent().get(0);
        assertThat(item.warrantyEndDate()).isEqualTo(until);
        assertThat(item.warrantyStatus()).isEqualTo("EXPIRING_30");
        assertThat(item.daysUntilExpiry()).isEqualTo(12);
    }

    @Test
    void reportTreatsBlankStatusAsAll() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Equipment first = warrantyEquipment("EQ-A", today.plusDays(5), UUID.randomUUID(), null);
        Equipment second = warrantyEquipment("EQ-B", today.plusDays(60), UUID.randomUUID(), null);

        when(equipmentRepository.findAllActiveWithWarranty(null, null, null))
                .thenReturn(List.of(first, second));
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(departmentRepository.findAllByIdInAndIsDeletedFalse(any())).thenReturn(List.of());

        assertThat(service.report(null, null, null, null, 0, 20).getTotalElements()).isEqualTo(2);
        assertThat(service.report(null, "   ", null, null, 0, 20).getTotalElements()).isEqualTo(2);
        assertThat(service.report(null, "ALL", null, null, 0, 20).getTotalElements()).isEqualTo(2);
    }

    private Equipment warrantyEquipment(String code, LocalDate end, UUID departmentId, UUID supplierId) {
        Equipment equipment = new Equipment();
        ReflectionTestUtils.setField(equipment, "id", UUID.randomUUID());
        equipment.setCode(code);
        equipment.setName("Equipment " + code);
        equipment.setHasWarranty(true);
        equipment.setWarrantyStartDate(end != null ? end.minusYears(1) : LocalDate.now().minusYears(1));
        equipment.setWarrantyEndDate(end);
        equipment.setDepartmentId(departmentId);
        equipment.setWarrantySupplierId(supplierId);
        return equipment;
    }

    private Department department(UUID id, String name) {
        Department department = new Department();
        department.setId(id);
        department.setName(name);
        return department;
    }

    private Supplier supplier(UUID id, String name) {
        Supplier supplier = new Supplier();
        supplier.setId(id);
        supplier.setName(name);
        return supplier;
    }
}
