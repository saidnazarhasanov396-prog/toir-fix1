package com.toir.service;

import com.toir.dto.warehouse.WarehouseDto;
import com.toir.entity.warehouse.Warehouse;
import com.toir.repository.LocationRepository;
import com.toir.repository.WarehouseRepository;
import com.toir.repository.WarehouseStockRepository;
import com.toir.repository.department.DepartmentRepository;
import com.toir.repository.users.EmployeeRepository;
import com.toir.util.AuditBuilderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WarehouseServiceTest {

    @Mock
    WarehouseRepository repository;

    @Mock
    WarehouseStockRepository stockRepository;

    @Mock
    DepartmentRepository departmentRepository;

    @Mock
    LocationRepository locationRepository;

    @Mock
    EmployeeRepository employeeRepository;

    @Mock
    AuditBuilderService auditBuilderService;

    @InjectMocks
    WarehouseService service;

    @Test
    void findAllAppliesSearchAndFilters() {
        UUID departmentId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        UUID responsibleId = UUID.randomUUID();
        Warehouse warehouse = new Warehouse();
        warehouse.setId(UUID.randomUUID());
        warehouse.setCode("WH-MAIN");
        warehouse.setName("Central warehouse");
        warehouse.setDepartmentId(departmentId);
        warehouse.setLocationId(locationId);
        warehouse.setResponsibleId(responsibleId);
        warehouse.setActive(true);

        when(repository.search(
                eq("main"),
                eq(departmentId),
                eq(locationId),
                eq(responsibleId),
                eq(true)
        )).thenReturn(List.of(warehouse));
        when(stockRepository.findAllByWarehouseIdAndIsDeletedFalse(warehouse.getId())).thenReturn(List.of());

        List<WarehouseDto> result = service.findAll(" main ", departmentId, locationId, responsibleId, true);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().responsibleId()).isEqualTo(responsibleId);
    }
}
