package com.toir.service;

import com.toir.dto.warehouse.WarehouseStockPolicyDto;
import com.toir.dto.warehouse.WarehouseStockPolicyRequest;
import com.toir.entity.SparePart;
import com.toir.entity.warehouse.Warehouse;
import com.toir.entity.warehouse.WarehouseStockPolicy;
import com.toir.exception.RestException;
import com.toir.repository.SparePartRepository;
import com.toir.repository.WarehouseRepository;
import com.toir.repository.WarehouseStockPolicyRepository;
import com.toir.security.ScopeAccessService;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WarehouseStockPolicyServiceTest {

    @Mock
    WarehouseStockPolicyRepository policyRepository;

    @Mock
    WarehouseRepository warehouseRepository;

    @Mock
    SparePartRepository sparePartRepository;

    @Mock
    ScopeAccessService scopeAccessService;

    WarehouseStockPolicyService service;

    @BeforeEach
    void setUp() {
        service = new WarehouseStockPolicyService(
                policyRepository,
                warehouseRepository,
                sparePartRepository,
                scopeAccessService
        );
        lenient().when(scopeAccessService.isScopeAdmin()).thenReturn(true);
    }

    @Test
    void replaceForSparePartUpsertsListedPoliciesAndSoftDeletesRemovedPolicies() {
        UUID sparePartId = UUID.randomUUID();
        UUID keptWarehouseId = UUID.randomUUID();
        UUID removedWarehouseId = UUID.randomUUID();
        SparePart sparePart = sparePart(sparePartId, "Laptop Kamera", 5);
        Warehouse keptWarehouse = warehouse(keptWarehouseId, "Central Warehouse");
        WarehouseStockPolicy removedPolicy = policy(removedWarehouseId, sparePartId, 2);

        when(sparePartRepository.findByIdAndIsDeletedFalse(sparePartId)).thenReturn(Optional.of(sparePart));
        when(policyRepository.findAllBySparePartIdAndIsDeletedFalse(sparePartId)).thenReturn(List.of(removedPolicy));
        when(warehouseRepository.findByIdAndIsDeletedFalse(keptWarehouseId)).thenReturn(Optional.of(keptWarehouse));
        when(policyRepository.findByWarehouseIdAndSparePartId(keptWarehouseId, sparePartId)).thenReturn(Optional.empty());
        when(policyRepository.save(any(WarehouseStockPolicy.class))).thenAnswer(invocation -> {
            WarehouseStockPolicy saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(UUID.randomUUID());
            }
            return saved;
        });

        List<WarehouseStockPolicyDto> result = service.replaceForSparePart(
                sparePartId,
                List.of(new WarehouseStockPolicyRequest(keptWarehouseId, 5.0, 20.0, 5.0, 10.0, null))
        );

        assertThat(removedPolicy.isDeleted()).isTrue();
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().warehouseId()).isEqualTo(keptWarehouseId);
        assertThat(result.getFirst().minQty()).isEqualTo(5.0);
        assertThat(result.getFirst().reorderQty()).isEqualTo(10.0);
        verify(policyRepository).save(removedPolicy);
    }

    @Test
    void replaceForSparePartRejectsDuplicateWarehousePolicies() {
        UUID sparePartId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();

        when(sparePartRepository.findByIdAndIsDeletedFalse(sparePartId))
                .thenReturn(Optional.of(sparePart(sparePartId, "Termopasta", 2)));

        assertThatThrownBy(() -> service.replaceForSparePart(
                sparePartId,
                List.of(
                        new WarehouseStockPolicyRequest(warehouseId, 2.0, null, 2.0, 4.0, null),
                        new WarehouseStockPolicyRequest(warehouseId, 3.0, null, 3.0, 6.0, null)
                )
        ))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("Duplicate warehouse policy");
                });
    }

    @Test
    void upsertRevivesSoftDeletedPolicyAndDefaultsMinQtyFromSparePart() {
        UUID sparePartId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        SparePart sparePart = sparePart(sparePartId, "Hard Disk", 10);
        Warehouse warehouse = warehouse(warehouseId, "Main Warehouse");
        WarehouseStockPolicy existing = policy(warehouseId, sparePartId, 0);
        existing.setDeleted(true);

        when(warehouseRepository.findByIdAndIsDeletedFalse(warehouseId)).thenReturn(Optional.of(warehouse));
        when(sparePartRepository.findByIdAndIsDeletedFalse(sparePartId)).thenReturn(Optional.of(sparePart));
        when(policyRepository.findByWarehouseIdAndSparePartId(warehouseId, sparePartId)).thenReturn(Optional.of(existing));
        when(policyRepository.save(any(WarehouseStockPolicy.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.upsert(warehouseId, sparePartId,
                new WarehouseStockPolicyRequest(null, null, 0.0, 0.0, 0.0, 0.0));

        ArgumentCaptor<WarehouseStockPolicy> captor = ArgumentCaptor.forClass(WarehouseStockPolicy.class);
        verify(policyRepository).save(captor.capture());
        WarehouseStockPolicy saved = captor.getValue();
        assertThat(saved.isDeleted()).isFalse();
        assertThat(saved.getMinQty()).isEqualTo(10.0);
        assertThat(saved.getMaxQty()).isNull();
        assertThat(saved.getReorderPoint()).isNull();
        assertThat(saved.getReorderQty()).isNull();
    }

    private SparePart sparePart(UUID id, String name, double minStock) {
        SparePart sparePart = new SparePart();
        sparePart.setId(id);
        sparePart.setCode("SP-001");
        sparePart.setName(name);
        sparePart.setMinStock(minStock);
        return sparePart;
    }

    private Warehouse warehouse(UUID id, String name) {
        Warehouse warehouse = new Warehouse();
        warehouse.setId(id);
        warehouse.setName(name);
        return warehouse;
    }

    private WarehouseStockPolicy policy(UUID warehouseId, UUID sparePartId, double minQty) {
        WarehouseStockPolicy policy = new WarehouseStockPolicy();
        policy.setId(UUID.randomUUID());
        policy.setWarehouseId(warehouseId);
        policy.setSparePartId(sparePartId);
        policy.setMinQty(minQty);
        return policy;
    }
}
