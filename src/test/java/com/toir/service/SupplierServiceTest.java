package com.toir.service;

import com.toir.dto.supplier.SupplierDto;
import com.toir.dto.supplier.SupplierRequest;
import com.toir.entity.Supplier;
import com.toir.enums.SupplierType;
import com.toir.exception.RestException;
import com.toir.repository.PurchaseOrderRepository;
import com.toir.repository.SupplierRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupplierServiceTest {

    @Mock
    SupplierRepository supplierRepository;

    @Mock
    PurchaseOrderRepository purchaseOrderRepository;

    SupplierService service;

    @BeforeEach
    void setUp() {
        service = new SupplierService(supplierRepository, purchaseOrderRepository);
    }

    @Test
    void createDefaultsSupplierTypeToBothWhenOmitted() {
        when(supplierRepository.count()).thenReturn(0L);
        when(supplierRepository.existsByCodeAndIsDeletedFalse("SUP-00001")).thenReturn(false);
        when(supplierRepository.save(any(Supplier.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.create(new SupplierRequest(
                null,
                "Universal Vendor",
                null,
                null,
                null,
                null,
                null,
                true,
                null
        ));

        ArgumentCaptor<Supplier> captor = ArgumentCaptor.forClass(Supplier.class);
        verify(supplierRepository).save(captor.capture());
        assertThat(captor.getValue().getSupplierType()).isEqualTo(SupplierType.BOTH);
    }

    @Test
    void findAllFiltersBySupplierTypeIncludingBothForEquipment() {
        Supplier equipmentOnly = supplier(UUID.randomUUID(), "Equipment Vendor", SupplierType.EQUIPMENT);
        Supplier both = supplier(UUID.randomUUID(), "Universal Vendor", SupplierType.BOTH);
        when(supplierRepository.findAllFiltered(true, SupplierType.EQUIPMENT)).thenReturn(List.of(equipmentOnly, both));

        List<SupplierDto> result = service.findAll(null, true, SupplierType.EQUIPMENT);

        assertThat(result).extracting(SupplierDto::supplierType)
                .containsExactly(SupplierType.EQUIPMENT, SupplierType.BOTH);
        verify(supplierRepository).findAllFiltered(true, SupplierType.EQUIPMENT);
        verify(supplierRepository, never()).search(any(), any(), any());
    }

    @Test
    void findAllUsesTextSearchOnlyWhenSearchIsProvided() {
        Supplier supplier = supplier(UUID.randomUUID(), "Universal Vendor", SupplierType.BOTH);
        when(supplierRepository.search("universal", null, null)).thenReturn(List.of(supplier));

        List<SupplierDto> result = service.findAll(" universal ", null, null);

        assertThat(result).extracting(SupplierDto::name).containsExactly("Universal Vendor");
        verify(supplierRepository).search("universal", null, null);
        verify(supplierRepository, never()).findAllFiltered(any(), any());
    }

    @Test
    void loadActiveForTypeRejectsSupplierWithWrongScope() {
        UUID supplierId = UUID.randomUUID();
        Supplier supplier = supplier(supplierId, "Equipment Vendor", SupplierType.EQUIPMENT);
        supplier.setActive(true);
        when(supplierRepository.findByIdAndIsDeletedFalse(supplierId)).thenReturn(Optional.of(supplier));

        assertThatThrownBy(() -> service.loadActiveForType(supplierId, SupplierType.SPARE_PART, "purchase orders"))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("purchase orders");
                    assertThat(ex.getMessage()).contains("SPARE_PART");
                });
    }

    private Supplier supplier(UUID id, String name, SupplierType supplierType) {
        Supplier supplier = new Supplier();
        supplier.setId(id);
        supplier.setCode("SUP-" + id.toString().substring(0, 8));
        supplier.setName(name);
        supplier.setSupplierType(supplierType);
        supplier.setActive(true);
        return supplier;
    }
}
